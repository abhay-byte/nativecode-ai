package com.ivarna.nativecode.terminal

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast

/** Handles project creation: folder setup and git clone, unified for proot/chroot. */
object ProjectManager {

    private val mainHandler = Handler(Looper.getMainLooper())

    /** Clones a git repository and calls [onProgress] for each output line,
     *  then [onDone] with the exit code. */
    fun cloneRepo(
        ctx: Context,
        gitUrl: String,
        onProgress: (line: String) -> Unit,
        onDone: (exitCode: Int) -> Unit
    ) {
        val gitCmd = "mkdir -p ~/repos && cd ~/repos && git clone --progress $gitUrl 2>&1"
        val (args, envMap) = LinuxCommandBuilder.build(ctx, gitCmd)
        ShellCommandRunner.runStreamed(ctx, args, envMap, onLine = onProgress, onDone = onDone)
    }

    /** Creates a directory inside the Debian environment (proot or chroot).
     *  Fire-and-forget: swallows errors. */
    fun ensureDir(ctx: Context, path: String) {
        val (args, envMap) = LinuxCommandBuilder.build(ctx, "mkdir -p $path")
        try {
            ShellCommandRunner.run(ctx, args, envMap)
        } catch (_: Exception) {
            // non-fatal
        }
    }
}
