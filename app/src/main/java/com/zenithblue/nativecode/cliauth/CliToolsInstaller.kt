package com.zenithblue.nativecode.cliauth

import android.content.Context
import android.util.Log
import com.zenithblue.nativecode.RootShell
import com.zenithblue.nativecode.terminal.HostCommandBuilder
import com.zenithblue.nativecode.terminal.TermuxHostPaths
import java.io.File
import java.io.FileOutputStream

/**
 * Single path for guest AI CLI suite install (setup_cli_tools.sh).
 * Used by onboarding step H, Settings install, and Scripts completion.
 */
object CliToolsInstaller {

    private const val TAG = "CliToolsInstaller"
    private const val ASSET = "scripts/setup_cli_tools.sh"
    private const val SCRIPT = "setup_cli_tools.sh"

    /**
     * Deploy + run in proot debian as root-ish login. Blocks until exit.
     * [onLine] may be called from worker thread.
     */
    fun runProotSync(ctx: Context, onLine: (String) -> Unit = {}): Int {
        return try {
            val usrTmp = File(ctx.filesDir, "usr/tmp").also { it.mkdirs() }
            val cliScript = File(usrTmp, SCRIPT)
            ctx.assets.open(ASSET).use { input ->
                FileOutputStream(cliScript).use { input.copyTo(it) }
            }
            cliScript.setExecutable(true)
            onLine("Deployed $SCRIPT — running in proot debian guest…\n")
            val cmd = arrayOf(
                TermuxHostPaths.BIN + "/python",
                TermuxHostPaths.PROOT_DISTRO,
                "login", "debian", "--shared-tmp", "--",
                "bash", "/tmp/$SCRIPT"
            )
            runProcess(ctx, cmd, onLine)
        } catch (e: Exception) {
            Log.e(TAG, "proot install failed", e)
            onLine("ERROR: ${e.message}\n")
            -1
        }
    }

    /**
     * Deploy into chroot /tmp and run as root. [onDone] on main via RootShell.
     */
    fun runChrootAsync(
        ctx: Context,
        onLine: (String) -> Unit = {},
        onDone: (Int) -> Unit
    ) {
        Thread {
            try {
                val stageDir = File(ctx.filesDir, "staged_scripts").also { it.mkdirs() }
                val staged = File(stageDir, SCRIPT)
                ctx.assets.open(ASSET).use { input ->
                    FileOutputStream(staged).use { input.copyTo(it) }
                }
                staged.setReadable(true, false)
                onLine("Staged $SCRIPT for chroot…\n")

                val chrootTmp = "/data/local/tmp/chrootDebian13/tmp"
                val copyCmd =
                    "mkdir -p $chrootTmp && cp ${staged.absolutePath} $chrootTmp/$SCRIPT && chmod +x $chrootTmp/$SCRIPT"
                val copyCode = RootShell.executeSync(copyCmd)
                if (copyCode != 0) {
                    onLine("ERROR: copy into chroot exit $copyCode\n")
                    android.os.Handler(android.os.Looper.getMainLooper()).post { onDone(-1) }
                    return@Thread
                }
                onLine("Running $SCRIPT inside chroot…\n")
                RootShell.executeInChroot(
                    cmd = "bash /tmp/$SCRIPT",
                    user = "root",
                    onLine = { line -> onLine(line + "\n") },
                    onDone = onDone,
                    context = ctx
                )
            } catch (e: Exception) {
                Log.e(TAG, "chroot install failed", e)
                onLine("ERROR: ${e.message}\n")
                android.os.Handler(android.os.Looper.getMainLooper()).post { onDone(-1) }
            }
        }.start()
    }

    /** Apply provision flag after install attempt. */
    fun finishProvision(
        ctx: Context,
        method: String,
        exitCode: Int,
        treatSoftFailAsPartialOk: Boolean = false
    ) {
        val ok = exitCode == 0 || treatSoftFailAsPartialOk
        AiCliProvisionState.markAiCliProvisioned(ctx, method, ok)
        if (ok) {
            CliAuthService.invalidateCache(method)
        }
    }

    private fun runProcess(ctx: Context, cmd: Array<String>, onLine: (String) -> Unit): Int {
        val adjusted = if (cmd.isNotEmpty() && cmd[0].startsWith("/data/data/"))
            arrayOf("/system/bin/linker64") + cmd else cmd
        val pb = ProcessBuilder(*adjusted)
        HostCommandBuilder.applyTo(ctx, pb, forceHostSetup = false)
        pb.redirectErrorStream(true)
        val proc = pb.start()
        val buf = ByteArray(1024)
        val stream = proc.inputStream
        var read: Int
        while (stream.read(buf).also { read = it } != -1) {
            onLine(String(buf, 0, read))
        }
        return proc.waitFor()
    }
}
