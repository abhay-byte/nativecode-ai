package com.zenithblue.nativecode.terminal

import android.content.Context
import android.os.Handler
import android.os.Looper

/** Cancel handle for a streamed shell process. */
class ShellJob(private val process: Process) {
    @Volatile
    var cancelled: Boolean = false
        private set

    fun cancel() {
        cancelled = true
        try {
            process.destroyForcibly()
        } catch (_: Exception) {
        }
    }
}

/** Runs shell commands with proper environment, supporting both proot and chroot. */
object ShellCommandRunner {

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Host ET_DYN tools (libbash.so / libproot.so) live under /data/app or /data/data.
     * Prefix with system linker64 so Bionic resolves NEEDED libs via LD_LIBRARY_PATH / RUNPATH.
     */
    private fun adjustHostCmd(cmd: Array<String>): Array<String> {
        if (cmd.isEmpty()) return cmd
        val exe = cmd[0]
        if (exe == "/system/bin/linker64" || exe.startsWith("/system/")) return cmd
        val underAppData =
            exe.startsWith("/data/data/") ||
                exe.startsWith("/data/app/") ||
                exe.startsWith("/data/user/")
        return if (underAppData) arrayOf("/system/bin/linker64") + cmd else cmd
    }

    /** Runs a command and returns its exit code. Output is consumed but not returned. */
    fun run(ctx: Context, cmd: Array<String>, envMap: Map<String, String>? = null): Int {
        val pb = ProcessBuilder(*adjustHostCmd(cmd))
        applyEnvironment(ctx, pb, envMap)
        pb.redirectErrorStream(true)
        val proc = pb.start()
        val stream = proc.inputStream
        val buf = ByteArray(1024)
        while (stream.read(buf) != -1) { /* consume */ }
        return proc.waitFor()
    }

    /** Runs a command and returns combined stdout/stderr as a string (blocking). */
    fun runCapture(ctx: Context, cmd: Array<String>, envMap: Map<String, String>? = null): String {
        val pb = ProcessBuilder(*adjustHostCmd(cmd))
        applyEnvironment(ctx, pb, envMap)
        pb.redirectErrorStream(true)
        val proc = pb.start()
        val text = proc.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        proc.waitFor()
        return text
    }

    /** Blocking capture with exit code (call from bg thread). */
    fun runCaptureExit(
        ctx: Context,
        cmd: Array<String>,
        envMap: Map<String, String>? = null
    ): Pair<Int, String> {
        val pb = ProcessBuilder(*adjustHostCmd(cmd))
        applyEnvironment(ctx, pb, envMap)
        pb.redirectErrorStream(true)
        val proc = pb.start()
        val text = proc.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val exit = proc.waitFor()
        return exit to text
    }

    /** Runs a command and streams each output line to [onLine] on the main thread. */
    fun runStreamed(
        ctx: Context,
        cmd: Array<String>,
        envMap: Map<String, String>? = null,
        onLine: (line: String) -> Unit,
        onDone: (exitCode: Int) -> Unit
    ) {
        runStreamedCancelable(ctx, cmd, envMap, onLine, onDone)
    }

    /**
     * Streamed run with cancel handle. [onLine]/[onDone] post to main thread.
     * Process starts on a worker thread; cancel is safe before/after start.
     */
    fun runStreamedCancelable(
        ctx: Context,
        cmd: Array<String>,
        envMap: Map<String, String>? = null,
        onLine: (line: String) -> Unit,
        onDone: (exitCode: Int) -> Unit
    ): ShellJob {
        val pb = ProcessBuilder(*adjustHostCmd(cmd))
        applyEnvironment(ctx, pb, envMap)
        pb.redirectErrorStream(true)

        // Placeholder process; real Process attached once started
        val holder = arrayOfNulls<Process>(1)
        val job = ShellJob(object : Process() {
            override fun getOutputStream() = java.io.ByteArrayOutputStream()
            override fun getInputStream() = java.io.ByteArrayInputStream(ByteArray(0))
            override fun getErrorStream() = java.io.ByteArrayInputStream(ByteArray(0))
            override fun waitFor(): Int = -1
            override fun exitValue(): Int = -1
            override fun destroy() {
                holder[0]?.destroyForcibly()
            }
            override fun destroyForcibly(): Process {
                holder[0]?.destroyForcibly()
                return this
            }
            override fun isAlive(): Boolean = holder[0]?.isAlive == true
        })

        Thread {
            val proc = try {
                if (job.cancelled) {
                    mainHandler.post { onDone(-1) }
                    return@Thread
                }
                pb.start().also { holder[0] = it }
            } catch (_: Exception) {
                mainHandler.post { onDone(-1) }
                return@Thread
            }
            try {
                val reader = proc.inputStream.bufferedReader(Charsets.UTF_8)
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (job.cancelled) break
                    val l = line ?: continue
                    mainHandler.post { onLine(l) }
                }
            } catch (_: Exception) {
            }
            val exit = try {
                if (job.cancelled) {
                    try { proc.destroyForcibly() } catch (_: Exception) {}
                    -1
                } else {
                    proc.waitFor()
                }
            } catch (_: Exception) {
                -1
            }
            mainHandler.post { onDone(exit) }
        }.start()

        return job
    }

    private fun applyEnvironment(ctx: Context, pb: ProcessBuilder, envMap: Map<String, String>?) {
        val env = pb.environment()
        HostCommandBuilder.applyTo(ctx, pb, forceHostSetup = false)
        env["GIT_TERMINAL_PROMPT"] = "0"

        // Override with envMap if provided (e.g. chroot-specific values)
        envMap?.forEach { (k, v) ->
            env[k] = v
        }
    }
}
