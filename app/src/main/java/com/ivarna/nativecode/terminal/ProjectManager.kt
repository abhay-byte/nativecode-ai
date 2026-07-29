package com.ivarna.nativecode.terminal

import android.content.Context
import java.io.File

/** Handles project creation: folder setup and git clone, unified for proot/chroot. */
object ProjectManager {

    /** Repo folder name from a git URL (strip trailing .git). */
    fun repoNameFromUrl(gitUrl: String): String =
        gitUrl.trim().trimEnd('/').substringAfterLast("/").removeSuffix(".git")
            .ifEmpty { "repo" }

    /** Clones a git repository into the isolation [method] rootfs.
     *  Calls [onProgress] for each output line, then [onDone] with exit code. */
    fun cloneRepo(
        ctx: Context,
        gitUrl: String,
        method: String = LinuxCommandBuilder.currentMethod,
        onProgress: (line: String) -> Unit,
        onDone: (exitCode: Int) -> Unit
    ) {
        val repoName = repoNameFromUrl(gitUrl)
        val dest = "/home/flux/repos/$repoName"
        // Absolute dest avoids ~ expand edge cases; rmdir empty shell if present
        val gitCmd =
            "mkdir -p /home/flux/repos && " +
                "if [ -d '$dest' ] && [ ! -d '$dest/.git' ]; then rmdir '$dest' 2>/dev/null || true; fi && " +
                "git clone --progress ${shellQuote(gitUrl)} '$dest' 2>&1"
        val (args, envMap) = LinuxCommandBuilder.build(ctx, gitCmd, method = method)
        ShellCommandRunner.runStreamed(ctx, args, envMap, onLine = onProgress, onDone = onDone)
    }

    /** Creates a directory inside the Debian environment for [method].
     *  Fire-and-forget: swallows errors. */
    fun ensureDir(
        ctx: Context,
        path: String,
        method: String = LinuxCommandBuilder.currentMethod
    ) {
        val (args, envMap) = LinuxCommandBuilder.build(ctx, "mkdir -p $path", method = method)
        try {
            ShellCommandRunner.run(ctx, args, envMap)
        } catch (_: Exception) {
            // non-fatal
        }
    }

    /** True if host path under [method] has a `.git` directory. */
    fun hasGitCheckout(ctx: Context, projectPath: String, method: String): Boolean {
        val host = ProjectPathResolver.resolve(ctx, projectPath, method)
        return File(host, ".git").isDirectory
    }

    /** Opposite isolation method. */
    fun oppositeMethod(method: String): String =
        if (method == "chroot") "proot" else "chroot"

    /**
     * If [method] host tree lacks `.git` but the opposite rootfs has it,
     * return the method where data lives; else null.
     */
    fun detectRepoMethod(
        ctx: Context,
        projectPath: String,
        preferredMethod: String
    ): String? {
        if (hasGitCheckout(ctx, projectPath, preferredMethod)) return preferredMethod
        val other = oppositeMethod(preferredMethod)
        if (hasGitCheckout(ctx, projectPath, other)) return other
        return null
    }

    private fun shellQuote(s: String): String =
        "'" + s.replace("'", "'\\''") + "'"
}
