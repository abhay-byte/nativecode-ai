package com.ivarna.nativecode

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.util.concurrent.Executors

/**
 * RootShell — Singleton for executing commands as root via KernelSU / Magisk su.
 *
 * Requirements:
 *  - KernelSU or Magisk must be installed on device.
 *  - The app package must be granted superuser access in the KSU / Magisk manager.
 *
 * All callbacks ([onLine], [onDone]) are dispatched on the main thread.
 */
object RootShell {

    private const val TAG = "RootShell"
    private val executor = Executors.newCachedThreadPool()
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Temp dir where asset scripts are staged before execution. */
    private const val SCRIPTS_TMP = "/data/local/tmp/nativecode_scripts"

    /**
     * Working su argv prefix, e.g. `["/system/bin/su","-c"]` or `["/system/bin/sh","-c"]` with
     * embedded su. Discovered once via [resolveSuInvocation]; null if root unavailable.
     *
     * IMPORTANT: do NOT gate on File.exists() — KernelSU/Magisk often hide su from the app
     * mount namespace until exec; File.exists() false positives skip the only working path.
     */
    @Volatile
    private var cachedSuInvocation: List<String>? = null

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns true if a working root shell (su) is available.
     * Runs synchronously — always call from a background thread.
     */
    fun isRootAvailable(): Boolean = resolveSuInvocation() != null

    /**
     * Capture stdout of a root command (blocking). Empty string on failure.
     * Background thread only.
     */
    fun capture(cmd: String): String {
        val inv = resolveSuInvocation() ?: return ""
        return try {
            val args = buildSuArgs(inv, cmd)
            val pb = ProcessBuilder(args).redirectErrorStream(true).start()
            val out = pb.inputStream.bufferedReader().readText()
            pb.waitFor()
            out
        } catch (e: Exception) {
            Log.w(TAG, "capture failed: ${e.message}")
            ""
        }
    }

    /**
     * Execute a shell command as root.
     * [onLine] is called on the Main thread for each stdout/stderr line.
     * [onDone] is called on the Main thread with the exit code.
     */
    fun execute(
        cmd: String,
        onLine: (String) -> Unit = {},
        onDone: (Int) -> Unit = {}
    ) {
        executor.execute {
            val inv = resolveSuInvocation()
            val code = if (inv == null) {
                mainHandler.post { onLine("[RootShell] ERROR: no working su binary") }
                -1
            } else {
                runCommand(buildSuArgs(inv, cmd), onLine)
            }
            mainHandler.post { onDone(code) }
        }
    }

    /**
     * Execute a shell command as root synchronously (blocking).
     * Must be called from a background thread. Returns the exit code.
     */
    fun executeSync(cmd: String): Int {
        val inv = resolveSuInvocation() ?: return -1
        return runCommand(buildSuArgs(inv, cmd)) {}
    }

    /**
     * Execute a shell script file as root.
     * [scriptPath] must be an absolute path to a script on the device.
     */
    fun executeScript(
        scriptPath: String,
        onLine: (String) -> Unit = {},
        onDone: (Int) -> Unit = {}
    ) {
        execute("sh \"$scriptPath\"", onLine, onDone)
    }

    /**
     * Copy an asset to /data/local/tmp/nativecode_scripts/, make it executable,
     * then run it as root.
     *
     * [assetName] is relative to assets/,
     *   e.g. "scripts/chroot/setup_debian13_chroot.sh"
     */
    fun executeScriptAsset(
        context: Context,
        assetName: String,
        onLine: (String) -> Unit = {},
        onDone: (Int) -> Unit = {}
    ) {
        executor.execute {
            val stagedPath = stageAsset(context, assetName)
            if (stagedPath == null) {
                mainHandler.post {
                    onLine("[RootShell] ERROR: Failed to stage asset '$assetName'")
                    onDone(-1)
                }
                return@execute
            }
            val inv = resolveSuInvocation()
            val code = if (inv == null) {
                mainHandler.post { onLine("[RootShell] ERROR: no working su binary") }
                -1
            } else {
                runCommand(buildSuArgs(inv, "sh \"$stagedPath\""), onLine)
            }
            mainHandler.post { onDone(code) }
        }
    }

    /**
     * Discover a working su invocation. Tries absolute paths without File.exists(),
     * plus `sh -c` wrappers matching MainActivity script runners.
     * Caches the first success. Background thread only.
     */
    fun resolveSuInvocation(): List<String>? {
        cachedSuInvocation?.let { return it }

        // Each entry: argv that runs a shell command string as root via trailing -c style,
        // OR special "sh_wrap" forms handled in trySuProbe.
        val trials: List<List<String>> = listOf(
            // Direct (KernelSU / Magisk common)
            listOf("/system/bin/su", "-c"),
            listOf("/system/xbin/su", "-c"),
            listOf("/sbin/su", "-c"),
            listOf("/debug_ramdisk/su", "-c"),
            listOf("su", "-c"),
            // Magisk sometimes wants uid first: su 0 -c 'cmd'
            listOf("/system/bin/su", "0", "-c"),
            listOf("su", "0", "-c"),
            // Same pattern as chroot_host script runner: sh -c '/system/bin/su -c …'
            listOf("/system/bin/sh", "-c", "SU_WRAP:/system/bin/su"),
            listOf("/system/bin/sh", "-c", "SU_WRAP:su"),
            listOf("/system/bin/sh", "-c", "SU_WRAP:/debug_ramdisk/su")
        )

        for (trial in trials) {
            if (trySuProbe(trial)) {
                cachedSuInvocation = trial
                Log.i(TAG, "resolveSuInvocation OK: $trial")
                return trial
            }
        }
        Log.w(TAG, "resolveSuInvocation: no working su")
        return null
    }

    /** Clear cached su (e.g. after user grants root in manager). */
    fun clearSuCache() {
        cachedSuInvocation = null
    }

    /**
     * Run a command inside the Debian 13 chroot as [user].
     * Mount policy matches ChrootCommandBuilder / setup script:
     * sticky disk /tmp (no app-tmp bind onto /tmp), optional /mnt/termux-tmp bridge.
     */
    fun executeInChroot(
        cmd: String,
        user: String = "flux",
        chrootPath: String = "/data/local/tmp/chrootDebian13",
        onLine: (String) -> Unit = {},
        onDone: (Int) -> Unit = {}
    ) {
        // Ensure filesystems are mounted — without this /dev/null is inaccessible and apt fails.
        // Never bind host/app tmp onto chroot /tmp (SELinux + _apt mkstemp).
        val mounts = listOf(
            "busybox mount -o remount,dev,suid /data 2>/dev/null || true",
            "busybox mount --bind /dev $chrootPath/dev 2>/dev/null || true",
            "busybox mount --bind /sys $chrootPath/sys 2>/dev/null || true",
            "busybox mount -t proc proc $chrootPath/proc 2>/dev/null || true",
            "busybox mount -t devpts devpts $chrootPath/dev/pts 2>/dev/null || true",
            "mkdir -p $chrootPath/dev/shm && busybox mount -t tmpfs -o size=512M,mode=1777 tmpfs $chrootPath/dev/shm 2>/dev/null || true",
            "mkdir -p $chrootPath/tmp $chrootPath/mnt/termux-tmp $chrootPath/sdcard",
            "busybox umount $chrootPath/tmp 2>/dev/null || true",
            "chmod 1777 $chrootPath/tmp 2>/dev/null || true",
            "busybox mount --bind /sdcard $chrootPath/sdcard 2>/dev/null || true"
        ).joinToString("; ")
        val inner = "$mounts; busybox chroot $chrootPath /bin/su - $user -c \"$cmd\""
        execute(inner, onLine, onDone)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildSuArgs(invocation: List<String>, cmd: String): List<String> {
        // SU_WRAP form: ["/system/bin/sh","-c","SU_WRAP:/system/bin/su"] → sh -c '/system/bin/su -c <cmd>'
        if (invocation.size >= 3 && invocation[2].startsWith("SU_WRAP:")) {
            val suBin = invocation[2].removePrefix("SU_WRAP:")
            val escaped = cmd.replace("'", "'\\''")
            return listOf(invocation[0], invocation[1], "$suBin -c '$escaped'")
        }
        return invocation + cmd
    }

    private fun trySuProbe(invocation: List<String>): Boolean {
        return try {
            val args = buildSuArgs(invocation, "id")
            Log.d(TAG, "trySuProbe: $args")
            val pb = ProcessBuilder(args).redirectErrorStream(true).start()
            // Read first (small output) — avoids pipe deadlock; process exits on deny quickly
            val out = pb.inputStream.bufferedReader().readText()
            val code = pb.waitFor()
            Log.d(TAG, "trySuProbe exit=$code out=${out.trim().take(120)}")
            code == 0 && out.contains("uid=0")
        } catch (e: Exception) {
            Log.w(TAG, "trySuProbe fail: ${e.message}")
            false
        }
    }

    /** Streams stdout+stderr from a ProcessBuilder command, returns exit code. */
    private fun runCommand(
        args: List<String>,
        onLine: (String) -> Unit
    ): Int {
        Log.d(TAG, "runCommand: ${args.joinToString(" ")}")
        return try {
            val pb = ProcessBuilder(args)
                .redirectErrorStream(true)
                .start()

            val reader = BufferedReader(InputStreamReader(pb.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line ?: continue
                Log.d(TAG, l)
                mainHandler.post { onLine(l) }
            }
            reader.close()
            val code = pb.waitFor()
            Log.d(TAG, "runCommand exit=$code")
            code
        } catch (e: Exception) {
            Log.e(TAG, "runCommand exception: ${e.message}")
            mainHandler.post { onLine("[RootShell] Exception: ${e.message}") }
            -1
        }
    }

    /** Copies an asset to app files directory, sets executable, returns absolute path or null. */
    private fun stageAsset(context: Context, assetName: String): String? {
        return try {
            val dir = File(context.filesDir, "staged_scripts")
            dir.mkdirs()
            dir.setExecutable(true, false)
            dir.setReadable(true, false)

            val scriptFile = File(dir, File(assetName).name)
            context.assets.open(assetName).use { input ->
                FileOutputStream(scriptFile).use { output -> input.copyTo(output) }
            }
            scriptFile.setExecutable(true, false)
            scriptFile.setReadable(true, false)
            Log.d(TAG, "Staged asset $assetName → ${scriptFile.absolutePath}")
            scriptFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "stageAsset failed for $assetName: ${e.message}")
            null
        }
    }
}
