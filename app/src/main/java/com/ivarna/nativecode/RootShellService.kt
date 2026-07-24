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

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns true if a working root shell (su) is available.
     * Runs synchronously — always call from a background thread.
     */
    fun isRootAvailable(): Boolean {
        return try {
            val pb = ProcessBuilder("su", "-c", "id")
                .redirectErrorStream(true)
                .start()
            val output = pb.inputStream.bufferedReader().readText()
            val exitCode = pb.waitFor()
            Log.d(TAG, "isRootAvailable: exitCode=$exitCode, output=${output.trim()}")
            exitCode == 0 && output.contains("uid=0")
        } catch (e: Exception) {
            Log.w(TAG, "isRootAvailable exception: ${e.message}")
            false
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
            val code = runCommand(listOf("su", "-c", cmd), onLine)
            mainHandler.post { onDone(code) }
        }
    }

    /**
     * Execute a shell command as root synchronously (blocking).
     * Must be called from a background thread. Returns the exit code.
     */
    fun executeSync(cmd: String): Int {
        return runCommand(listOf("su", "-c", cmd)) {}
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
        executor.execute {
            val code = runCommand(listOf("su", "-c", "sh \"$scriptPath\""), onLine)
            mainHandler.post { onDone(code) }
        }
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
            val code = runCommand(listOf("su", "-c", "sh \"$stagedPath\""), onLine)
            mainHandler.post { onDone(code) }
        }
    }

    /**
     * Run a command inside the Debian 13 chroot as [user].
     * Assumes chroot mounts are already set up.
     */
    fun executeInChroot(
        cmd: String,
        user: String = "flux",
        chrootPath: String = "/data/local/tmp/chrootDebian13",
        onLine: (String) -> Unit = {},
        onDone: (Int) -> Unit = {}
    ) {
        // Ensure filesystems are mounted — without this /dev/null is inaccessible and apt fails.
        val mounts = listOf(
            "busybox mount -o remount,dev,suid /data 2>/dev/null || true",
            "busybox mount --bind /dev $chrootPath/dev 2>/dev/null || true",
            "busybox mount --bind /sys $chrootPath/sys 2>/dev/null || true",
            "busybox mount -t proc proc $chrootPath/proc 2>/dev/null || true",
            "busybox mount -t devpts devpts $chrootPath/dev/pts 2>/dev/null || true",
            "mkdir -p $chrootPath/dev/shm && busybox mount -t tmpfs -o size=512M tmpfs $chrootPath/dev/shm 2>/dev/null || true"
        ).joinToString("; ")
        val inner = "$mounts; busybox chroot $chrootPath /bin/su - $user -c \"$cmd\""
        execute(inner, onLine, onDone)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────────────────────────────────────

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
