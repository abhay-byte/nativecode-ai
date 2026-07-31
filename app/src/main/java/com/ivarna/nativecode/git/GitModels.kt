package com.ivarna.nativecode.git

/** Branch list for project settings (local + remote, display-only). */
data class BranchInfo(
    val isRepo: Boolean,
    val current: String?,
    /** Local branch short names. */
    val localBranches: List<String>,
    /** Remote-tracking names (e.g. origin/main). */
    val remoteBranches: List<String> = emptyList(),
    val detached: Boolean = false
) {
    /** Combined list for dropdown: locals first, then remotes not already listed. */
    val allBranches: List<String>
        get() {
            val out = ArrayList<String>(localBranches.size + remoteBranches.size)
            out.addAll(localBranches)
            for (r in remoteBranches) {
                val short = r.removePrefix("origin/").removePrefix("upstream/")
                if (r !in out && short !in out) out.add(r)
            }
            return out
        }

    /** Backward-compatible alias used by older call sites. */
    val branches: List<String> get() = allBranches
}

/** One porcelain status line, UI-ready. */
data class GitStatusEntry(
    val xy: String,
    val path: String,
    val statusChar: Char,
    val statusLabel: String
)

/** Aggregated working-tree summary for Git Diff page. */
data class DiffSummary(
    val isRepo: Boolean,
    val modified: Int,
    val added: Int,
    val deleted: Int,
    val untracked: Int,
    val renamed: Int,
    val linesAdded: Int,
    val linesDeleted: Int,
    val entries: List<GitStatusEntry>
)

sealed class DiffSummaryResult {
    data class Ok(val summary: DiffSummary) : DiffSummaryResult()
    data class NotGit(
        val message: String = "Project is not a git repository"
    ) : DiffSummaryResult()
    data class Error(val message: String) : DiffSummaryResult()
}
