package com.zenithblue.nativecode

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.zenithblue.nativecode.terminal.ChrootCommandBuilder
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

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
     * Async root probe. Runs [isRootAvailable] off the UI thread; [onResult] always on main.
     * Use from onboarding method cards / settings hub gates — not for hot paths that need
     * the boolean in the same bg job (call [isRootAvailable] there directly).
     *
     * @param forceClearCache true = drop cached su first (user just granted root).
     */
    fun probeRootAvailable(forceClearCache: Boolean = false, onResult: (Boolean) -> Unit) {
        executor.execute {
            if (forceClearCache) clearSuCache()
            val ok = try {
                isRootAvailable()
            } catch (_: Exception) {
                false
            }
            mainHandler.post { onResult(ok) }
        }
    }

    /** Result of a blocking root capture (exit code + stdout). */
    data class CaptureResult(val exitCode: Int, val stdout: String)

    /**
     * Capture stdout of a root command (blocking). Empty string on failure.
     * Background thread only. [timeoutMs] > 0 aborts hung su (exit -2).
     */
    fun capture(cmd: String, timeoutMs: Long = 0L): String =
        captureResult(cmd, timeoutMs).stdout

    /**
     * Capture stdout + exit code of a root command (blocking).
     * Background thread only.
     *
     * @param timeoutMs 0 = wait forever; >0 kills process after timeout (exitCode -2).
     * @return [CaptureResult] with exitCode -1 if no su / exception, -2 on timeout.
     */
    fun captureResult(cmd: String, timeoutMs: Long = 0L): CaptureResult {
        val inv = resolveSuInvocation() ?: return CaptureResult(-1, "")
        return try {
            val args = buildSuArgs(inv, cmd)
            val pb = ProcessBuilder(args).redirectErrorStream(true).start()
            // Read on side thread so waitFor(timeout) cannot deadlock on full pipe.
            val outFuture = executor.submit<String> {
                pb.inputStream.bufferedReader().use { it.readText() }
            }
            val finished = if (timeoutMs > 0L) {
                pb.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            } else {
                pb.waitFor()
                true
            }
            if (!finished) {
                pb.destroyForcibly()
                val partial = try {
                    outFuture.get(500, TimeUnit.MILLISECONDS)
                } catch (_: Exception) {
                    ""
                }
                Log.w(TAG, "capture timeout after ${timeoutMs}ms cmd=${cmd.take(80)}")
                return CaptureResult(-2, partial)
            }
            val out = try {
                outFuture.get(5, TimeUnit.SECONDS)
            } catch (e: Exception) {
                Log.w(TAG, "capture read failed: ${e.message}")
                ""
            }
            val code = try {
                pb.exitValue()
            } catch (_: Exception) {
                -1
            }
            CaptureResult(code, out)
        } catch (e: Exception) {
            Log.w(TAG, "capture failed: ${e.message}")
            CaptureResult(-1, "")
        }
    }

    /**
     * Copy [hostSrc] into the Debian chroot at [guestAbsPath] (e.g. `/home/flux/attach_x.png`).
     * mkdir -p parent, cp -f, chown flux, chmod 644, verify file exists.
     * Background thread only.
     */
    fun copyIntoChroot(
        hostSrc: File,
        guestAbsPath: String,
        chrootPath: String = ChrootCommandBuilder.CHROOT_PATH,
        timeoutMs: Long = 30_000L
    ): CaptureResult {
        if (!hostSrc.isFile || hostSrc.length() <= 0L) {
            return CaptureResult(-1, "missing or empty source")
        }
        val rel = guestAbsPath.removePrefix("/").trim()
        if (rel.isEmpty() || rel.contains("..")) {
            return CaptureResult(-1, "invalid guest path")
        }
        val dest = "$chrootPath/$rel"
        fun shEsc(s: String): String = s.replace("'", "'\\''")
        val parent = dest.substringBeforeLast('/', missingDelimiterValue = chrootPath)
        val cmd = buildString {
            append("mkdir -p '").append(shEsc(parent)).append("' && ")
            append("cp -f '").append(shEsc(hostSrc.absolutePath)).append("' '").append(shEsc(dest)).append("' && ")
            append("(chown flux:flux '").append(shEsc(dest)).append("' 2>/dev/null || ")
            append("chown 1000:1000 '").append(shEsc(dest)).append("' 2>/dev/null || true) && ")
            append("chmod 644 '").append(shEsc(dest)).append("' && ")
            append("test -f '").append(shEsc(dest)).append("' && ")
            append("stat -c %s '").append(shEsc(dest)).append("' 2>/dev/null || ")
            append("wc -c < '").append(shEsc(dest)).append("'")
        }
        Log.i(TAG, "copyIntoChroot src=${hostSrc.absolutePath} dest=$dest")
        return captureResult(cmd, timeoutMs)
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
     * Single shell snippet that runs [cmd] as root — for TerminalSession / `sh -c` only.
     * Probes su once if cache empty (may block briefly — call off hot UI loops when possible).
     * Escapes [cmd] in single quotes so `;` `&&` `$` inside the guest chain stay intact.
     */
    fun shellRootCommand(cmd: String): String {
        val escaped = cmd.replace("'", "'\\''")
        val inv = cachedSuInvocation ?: resolveSuInvocation()
        return when {
            inv != null && inv.size >= 3 && inv[2].startsWith("SU_WRAP:") -> {
                val suBin = inv[2].removePrefix("SU_WRAP:")
                "$suBin -c '$escaped'"
            }
            inv != null && inv.isNotEmpty() && inv.last() == "-c" -> {
                // e.g. [/system/bin/su, -c] or [su, 0, -c]
                val prefix = inv.dropLast(1).joinToString(" ")
                "$prefix -c '$escaped'"
            }
            else -> "/system/bin/su -c '$escaped'"
        }
    }

    /**
     * Run a command inside the Debian 13 chroot as [user] via SSOT helper.
     * Mounts + single-layer guest entry live in nativecode_chroot.sh (no Kotlin mount clone).
     * [chrootPath] is accepted for API stability; helper uses NC_CHROOT when non-default needed.
     * Pass [context] so the helper is staged from assets when missing (onboarding / first run).
     */
    fun executeInChroot(
        cmd: String,
        user: String = "flux",
        chrootPath: String = ChrootCommandBuilder.CHROOT_PATH,
        onLine: (String) -> Unit = {},
        onDone: (Int) -> Unit = {},
        context: Context? = null
    ) {
        context?.let { ChrootCommandBuilder.ensureHelperScript(it) }
        val u = if (user == "root") "root" else "flux"
        val b64 = android.util.Base64.encodeToString(
            cmd.toByteArray(Charsets.UTF_8),
            android.util.Base64.NO_WRAP
        )
        execute(buildChrootHelperCmd(u, b64, chrootPath), onLine, onDone)
    }

    /**
     * Blocking capture inside chroot (bg thread only). Prefer for probe/status.
     * [timeoutMs] 0 = wait forever; >0 abort (exit -2).
     * Pass [context] to stage helper from assets when missing.
     */
    fun captureInChroot(
        cmd: String,
        user: String = "flux",
        chrootPath: String = ChrootCommandBuilder.CHROOT_PATH,
        timeoutMs: Long = 60_000L,
        context: Context? = null
    ): CaptureResult {
        context?.let { ChrootCommandBuilder.ensureHelperScript(it) }
        val u = if (user == "root") "root" else "flux"
        val b64 = android.util.Base64.encodeToString(
            cmd.toByteArray(Charsets.UTF_8),
            android.util.Base64.NO_WRAP
        )
        return captureResult(buildChrootHelperCmd(u, b64, chrootPath), timeoutMs)
    }

    /**
     * Self-heal: if helper missing under /data/local/tmp, copy from app home/staged
     * (deployScripts must place nativecode_chroot.sh there). Then `sh helper b64 …`.
     */
    private fun buildChrootHelperCmd(user: String, b64: String, chrootPath: String): String {
        val helper = ChrootCommandBuilder.CHROOT_HELPER
        val pkg = "com.zenithblue.nativecode"
        val envPrefix =
            if (chrootPath == ChrootCommandBuilder.CHROOT_PATH) ""
            else "NC_CHROOT='$chrootPath' "
        // Bootstrap before invoke — fixes onboarding/gh when no terminal session ran yet
        return envPrefix +
            "if [ ! -f $helper ]; then " +
            "for _s in " +
            "/data/data/$pkg/files/home/nativecode_chroot.sh " +
            "/data/data/$pkg/files/staged_scripts/nativecode_chroot.sh; do " +
            "[ -f \"\$_s\" ] && cp -f \"\$_s\" $helper && chmod 755 $helper && break; " +
            "done; fi; " +
            "if [ -f $helper ]; then sh $helper b64 --user $user -- $b64; " +
            "else echo '[RootShell] missing $helper — reinstall chroot or open a chroot session once' >&2; exit 127; fi"
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

    /**
     * Copy an asset to [filesDir]/staged_scripts/, make executable, return absolute path or null.
     * Public so callers (e.g. ChrootProcessManager) can stage helpers then [capture] them.
     * Background thread only when followed by root I/O.
     */
    fun stageAsset(context: Context, assetName: String): String? {
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
