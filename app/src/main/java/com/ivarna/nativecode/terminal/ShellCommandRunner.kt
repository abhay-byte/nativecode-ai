package com.ivarna.nativecode.terminal

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.File

/** Runs shell commands with proper environment, supporting both proot and chroot. */
object ShellCommandRunner {

    private val mainHandler = Handler(Looper.getMainLooper())

    /** Runs a command and returns its exit code. Output is consumed but not returned. */
    fun run(ctx: Context, cmd: Array<String>, envMap: Map<String, String>? = null): Int {
        val adjusted = if (cmd.isNotEmpty() && cmd[0].startsWith("/data/data/"))
            arrayOf("/system/bin/linker64") + cmd else cmd
        val pb = ProcessBuilder(*adjusted)
        applyEnvironment(ctx, pb, envMap)
        pb.redirectErrorStream(true)
        val proc = pb.start()
        val stream = proc.inputStream
        val buf = ByteArray(1024)
        while (stream.read(buf) != -1) { /* consume */ }
        return proc.waitFor()
    }

    /** Runs a command and streams each output line to [onLine] on the main thread. */
    fun runStreamed(
        ctx: Context,
        cmd: Array<String>,
        envMap: Map<String, String>? = null,
        onLine: (line: String) -> Unit,
        onDone: (exitCode: Int) -> Unit
    ) {
        val adjusted = if (cmd.isNotEmpty() && cmd[0].startsWith("/data/data/"))
            arrayOf("/system/bin/linker64") + cmd else cmd
        val pb = ProcessBuilder(*adjusted)
        applyEnvironment(ctx, pb, envMap)
        pb.redirectErrorStream(true)

        Thread {
            val proc = pb.start()
            val reader = proc.inputStream.bufferedReader(Charsets.UTF_8)
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line ?: continue
                mainHandler.post { onLine(l) }
            }
            val exit = proc.waitFor()
            mainHandler.post { onDone(exit) }
        }.start()
    }

    private fun applyEnvironment(ctx: Context, pb: ProcessBuilder, envMap: Map<String, String>?) {
        val env = pb.environment()
        val nld = ctx.applicationInfo.nativeLibraryDir

        // Base environment — proot defaults
        env["PATH"] = "$nld:/data/data/com.ivarna.nativecode/files/usr/bin:/system/bin"
        env["PD_PROOT_BIN"] = File(nld, "libproot.so").absolutePath
        env["PROOT_LOADER"] = File(nld, "libloader.so").absolutePath
        env["LD_LIBRARY_PATH"] = "/data/data/com.ivarna.nativecode/files/usr/lib:/data/data/com.ivarna.nativecode/files/usr/opt/virglrenderer-android/lib"
        val termuxExec = File(ctx.filesDir, "usr/lib/libtermux-exec.so")
        if (termuxExec.exists()) env["LD_PRELOAD"] = termuxExec.absolutePath
        env["PREFIX"] = "/data/data/com.ivarna.nativecode/files/usr"
        env["HOME"] = "/data/data/com.ivarna.nativecode/files/home"
        env["TMPDIR"] = "/data/data/com.ivarna.nativecode/files/usr/tmp"
        env["PROOT_TMP_DIR"] = "/data/data/com.ivarna.nativecode/files/usr/tmp"
        env["TERMUX_APP__PACKAGE_NAME"] = "com.ivarna.nativecode"
        env["TERMUX_X11_APK_PATH"] = ctx.applicationInfo.sourceDir
        env["TERMUX_X11_OVERRIDE_PACKAGE"] = "com.ivarna.nativecode"
        env["TERMUX__PREFIX"] = "/data/data/com.ivarna.nativecode/files/usr"
        env["TERMUX__HOME"] = "/data/data/com.ivarna.nativecode/files/home"
        env["SSL_CERT_FILE"] = "/data/data/com.ivarna.nativecode/files/usr/etc/tls/cert.pem"
        env["CURL_CA_BUNDLE"] = "/data/data/com.ivarna.nativecode/files/usr/etc/tls/cert.pem"
        env["GIT_TERMINAL_PROMPT"] = "0"

        // Override with envMap if provided (e.g. chroot-specific values)
        envMap?.forEach { (k, v) ->
            env[k] = v
        }
    }
}
