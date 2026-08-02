package com.zenithblue.nativecode.git

import android.content.Context
import com.zenithblue.nativecode.terminal.LinuxCommandBuilder
import com.zenithblue.nativecode.terminal.ProjectManager
import com.zenithblue.nativecode.terminal.ShellCommandRunner

/**
 * Project-scoped git queries via guest shell (proot | chroot).
 * Always pass explicit [method] = project isolation, not only global currentMethod.
 */
object GitRepoService {

    fun isGitRepo(ctx: Context, projectPath: String, method: String): Boolean {
        if (projectPath.isBlank()) return false
        if (ProjectManager.hasGitCheckout(ctx, projectPath, method)) return true
        return try {
            val cmd = GitGuestCommands.isInsideWorkTree(projectPath)
            val (args, env) = LinuxCommandBuilder.build(ctx, cmd, method = method)
            val fullEnv = HashMap(env).apply { putAll(GitGuestCommands.gitEnv()) }
            val (exit, out) = ShellCommandRunner.runCaptureExit(ctx, args, fullEnv)
            exit == 0 && out.contains("true")
        } catch (_: Exception) {
            false
        }
    }

    /** Blocking — call from bg thread. */
    fun listBranches(ctx: Context, projectPath: String, method: String): BranchInfo {
        if (projectPath.isBlank()) {
            return BranchInfo(isRepo = false, current = null, localBranches = emptyList())
        }
        return try {
            val script = GitGuestCommands.branchesBundle(projectPath)
            val (args, env) = LinuxCommandBuilder.build(ctx, script, method = method)
            val fullEnv = HashMap(env).apply { putAll(GitGuestCommands.gitEnv()) }
            val (exit, out) = ShellCommandRunner.runCaptureExit(ctx, args, fullEnv)
            if (exit == 2) {
                return BranchInfo(isRepo = false, current = null, localBranches = emptyList())
            }
            GitPorcelainParse.parseBranchesBundle(out)
        } catch (e: Exception) {
            BranchInfo(isRepo = false, current = null, localBranches = emptyList())
        }
    }

    /** Blocking — call from bg thread. */
    fun loadDiffSummary(
        ctx: Context,
        projectPath: String,
        method: String
    ): DiffSummaryResult {
        if (projectPath.isBlank()) {
            return DiffSummaryResult.NotGit("No active project")
        }
        return try {
            val script = GitGuestCommands.statusSummaryBundle(projectPath)
            val (args, env) = LinuxCommandBuilder.build(ctx, script, method = method)
            val fullEnv = HashMap(env).apply { putAll(GitGuestCommands.gitEnv()) }
            val (exit, out) = ShellCommandRunner.runCaptureExit(ctx, args, fullEnv)
            if (exit == 2) {
                return DiffSummaryResult.NotGit("Project path not found")
            }
            // exit non-zero can still be valid porcelain (e.g. empty); parse markers first
            val parsed = GitPorcelainParse.parseStatusSummaryBundle(out)
            if (parsed is DiffSummaryResult.NotGit) return parsed
            if (parsed is DiffSummaryResult.Ok) return parsed
            if (out.contains("__NOGIT__")) return DiffSummaryResult.NotGit()
            DiffSummaryResult.Error("git status failed (exit $exit)")
        } catch (e: Exception) {
            DiffSummaryResult.Error(e.message ?: "git status error")
        }
    }
}
