package com.zenithblue.nativecode.git

/**
 * Pure guest shell snippets for project git. No Android / UI.
 * Always run as flux via [com.zenithblue.nativecode.terminal.LinuxCommandBuilder].
 */
object GitGuestCommands {

    /** Non-interactive git env (merge into ProcessBuilder / ShellCommandRunner env). */
    fun gitEnv(): Map<String, String> = mapOf(
        "GIT_PAGER" to "cat",
        "GIT_TERMINAL_PROMPT" to "0",
        "TERM" to "dumb",
        "NO_COLOR" to "1"
    )

    fun shellQuote(s: String): String =
        "'" + s.replace("'", "'\\''") + "'"

    /** Single spawn: is-repo + current + local + remote-tracking branches. */
    fun branchesBundle(projectPath: String): String {
        val p = shellQuote(projectPath)
        // One line — safer inside proot/chroot -c quoting than multiline.
        return "cd $p || exit 2; " +
            "if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then echo '__NOGIT__'; exit 0; fi; " +
            "echo '__HEAD__'; " +
            "cur=\$(git branch --show-current 2>/dev/null); " +
            "[ -z \"\$cur\" ] && cur=\$(git rev-parse --abbrev-ref HEAD 2>/dev/null); " +
            "echo \"\$cur\"; " +
            "echo '__LOCAL__'; " +
            "git branch --format='%(refname:short)' 2>/dev/null; " +
            "echo '__REMOTE__'; " +
            "git branch -r --format='%(refname:short)' 2>/dev/null; " +
            "echo '__END__'"
    }

    /**
     * Single spawn: is-repo + porcelain status + numstat vs HEAD.
     * Untracked LOC omitted (count-only in parse).
     */
    fun statusSummaryBundle(projectPath: String): String {
        val p = shellQuote(projectPath)
        return """
            cd $p || exit 2
            if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
              echo '__NOGIT__'
              exit 0
            fi
            echo '__STATUS__'
            git status --porcelain -uall 2>/dev/null
            echo '__NUMSTAT__'
            git diff --numstat HEAD 2>/dev/null
        """.trimIndent().replace("\n", "; ")
    }

    fun isInsideWorkTree(projectPath: String): String =
        "cd ${shellQuote(projectPath)} && git rev-parse --is-inside-work-tree 2>/dev/null"

    fun diffFile(projectPath: String, filePath: String): String =
        "cd ${shellQuote(projectPath)} && git diff HEAD -- ${shellQuote(filePath)}"

    fun diffUntracked(projectPath: String, filePath: String): String =
        "cd ${shellQuote(projectPath)} && [ -f ${shellQuote(filePath)} ] && " +
            "git diff --no-index /dev/null ${shellQuote(filePath)}"
}
