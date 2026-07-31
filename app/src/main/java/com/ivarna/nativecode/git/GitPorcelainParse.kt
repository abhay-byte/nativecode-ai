package com.ivarna.nativecode.git

/** Pure parse helpers for guest git bundle output. */
object GitPorcelainParse {

    fun parseBranchesBundle(raw: String): BranchInfo {
        val lines = raw.lines().map { it.trimEnd() }
        if (lines.any { it.trim() == "__NOGIT__" }) {
            return BranchInfo(isRepo = false, current = null, localBranches = emptyList())
        }
        var section = ""
        var current: String? = null
        val local = ArrayList<String>()
        val remote = ArrayList<String>()
        for (line in lines) {
            val t = line.trim()
            when (t) {
                "__HEAD__" -> { section = "head"; continue }
                "__LOCAL__", "__BRANCHES__" -> { section = "local"; continue }
                "__REMOTE__" -> { section = "remote"; continue }
                "__END__" -> { section = ""; continue }
                "__NOGIT__" -> return BranchInfo(false, null, emptyList())
            }
            // strip noise from proot banners
            if (t.startsWith("__") && t.endsWith("__")) continue
            when (section) {
                "head" -> {
                    if (t.isNotEmpty() && current == null && !t.startsWith("fatal:")) {
                        current = t
                    }
                }
                "local" -> {
                    if (t.isNotEmpty() && !t.startsWith("fatal:") && t !in local) local.add(t)
                }
                "remote" -> {
                    // drop "origin/HEAD -> origin/main" style
                    val name = t.substringBefore(" -> ").trim()
                    if (name.isNotEmpty() && !name.endsWith("/HEAD") &&
                        !name.startsWith("fatal:") && name !in remote
                    ) {
                        remote.add(name)
                    }
                }
            }
        }
        // Ensure current appears in local list when present
        val cur = current?.takeIf { it.isNotEmpty() && it != "HEAD" }
        if (cur != null && cur !in local && !cur.contains("/")) local.add(0, cur)
        val detached = cur == null || current == "HEAD"
        return BranchInfo(
            isRepo = true,
            current = cur,
            localBranches = local,
            remoteBranches = remote,
            detached = detached
        )
    }

    fun parseStatusSummaryBundle(raw: String): DiffSummaryResult {
        val lines = raw.lines().map { it.trimEnd() }
        if (lines.any { it.trim() == "__NOGIT__" }) {
            return DiffSummaryResult.NotGit()
        }
        var section = ""
        val statusLines = ArrayList<String>()
        val numstatLines = ArrayList<String>()
        for (line in lines) {
            val t = line.trim()
            when (t) {
                "__STATUS__" -> {
                    section = "status"
                    continue
                }
                "__NUMSTAT__" -> {
                    section = "numstat"
                    continue
                }
                "__NOGIT__" -> return DiffSummaryResult.NotGit()
            }
            when (section) {
                "status" -> if (line.isNotEmpty()) statusLines.add(line)
                "numstat" -> if (line.isNotEmpty()) numstatLines.add(line)
            }
        }
        val entries = parsePorcelain(statusLines)
        val (locAdd, locDel) = parseNumstat(numstatLines)
        val counts = countBuckets(entries)
        return DiffSummaryResult.Ok(
            DiffSummary(
                isRepo = true,
                modified = counts.modified,
                added = counts.added,
                deleted = counts.deleted,
                untracked = counts.untracked,
                renamed = counts.renamed,
                linesAdded = locAdd,
                linesDeleted = locDel,
                entries = entries
            )
        )
    }

    fun parsePorcelain(lines: List<String>): List<GitStatusEntry> {
        val out = ArrayList<GitStatusEntry>()
        for (line in lines) {
            if (line.length <= 3 || line[2] != ' ') continue
            val x = line[0]
            val y = line[1]
            if (x !in XY && y !in XY) continue
            val xy = line.substring(0, 2)
            var path = line.substring(3).trim()
            // rename/copy: "old -> new"
            if (" -> " in path) {
                path = path.substringAfterLast(" -> ").trim()
            }
            // strip quotes if present
            if (path.startsWith("\"") && path.endsWith("\"") && path.length >= 2) {
                path = path.substring(1, path.length - 1)
            }
            if (path.isEmpty()) continue
            val statusChar = primaryStatusChar(x, y)
            out.add(
                GitStatusEntry(
                    xy = xy,
                    path = path,
                    statusChar = statusChar,
                    statusLabel = statusLabel(statusChar)
                )
            )
        }
        return out
    }

    fun parseNumstat(lines: List<String>): Pair<Int, Int> {
        var add = 0
        var del = 0
        for (line in lines) {
            val parts = line.trim().split(Regex("\\s+"), limit = 3)
            if (parts.size < 2) continue
            val a = parts[0]
            val d = parts[1]
            if (a != "-") add += a.toIntOrNull() ?: 0
            if (d != "-") del += d.toIntOrNull() ?: 0
        }
        return add to del
    }

    data class Counts(
        val modified: Int,
        val added: Int,
        val deleted: Int,
        val untracked: Int,
        val renamed: Int
    )

    fun countBuckets(entries: List<GitStatusEntry>): Counts {
        var modified = 0
        var added = 0
        var deleted = 0
        var untracked = 0
        var renamed = 0
        for (e in entries) {
            val x = e.xy.getOrElse(0) { ' ' }
            val y = e.xy.getOrElse(1) { ' ' }
            when {
                x == '?' || y == '?' -> untracked++
                x == 'R' || y == 'R' || x == 'C' || y == 'C' -> renamed++
                x == 'D' || y == 'D' -> deleted++
                x == 'A' || y == 'A' -> added++
                x == 'M' || y == 'M' -> modified++
                else -> modified++
            }
        }
        return Counts(modified, added, deleted, untracked, renamed)
    }

    fun statusLabel(c: Char): String = when (c) {
        'M' -> "MOD"
        'A' -> "ADD"
        'D' -> "DEL"
        'R' -> "REN"
        'C' -> "CPY"
        '?' -> "NEW"
        '!' -> "IGN"
        else -> "---"
    }

    /** Prefer worktree letter, then index; untracked wins. */
    fun primaryStatusChar(x: Char, y: Char): Char {
        if (x == '?' || y == '?') return '?'
        if (y != ' ' && y != '!') return y
        if (x != ' ' && x != '!') return x
        return y.takeIf { it != ' ' } ?: x
    }

    private val XY = listOf('M', 'A', 'D', 'R', 'C', 'U', '?', '!', ' ')
}
