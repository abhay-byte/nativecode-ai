package com.ivarna.nativecode.cliauth

/**
 * SSOT for Terminal + Workspace tool launcher cards (C6).
 * Prevents Free/Paid/Shell drift between surfaces.
 */
data class LauncherToolDef(
    val type: String,
    val label: String,
    val desc: String
)

object ToolLauncherCatalog {

    val SHELL: List<LauncherToolDef> = listOf(
        LauncherToolDef("shell", "Debian Shell", "User: flux"),
        LauncherToolDef("shell-root", "Debian Shell Rooted", "User: root")
    )

    val FREE: List<LauncherToolDef> = listOf(
        LauncherToolDef("opencode", "opencode", "Claude Agent")
    )

    val PAID: List<LauncherToolDef> = listOf(
        LauncherToolDef("codex", "codex", "OpenAI Codex"),
        LauncherToolDef("agy", "agy", "Antigravity"),
        LauncherToolDef("claude-code", "claude-code", "Claude Code"),
        LauncherToolDef("qwen-code", "qwen-code", "Qwen Code"),
        LauncherToolDef("grok", "grok", "Grok CLI"),
        LauncherToolDef("kiro", "kiro", "Kiro CLI")
    )

    fun freeTools(): List<LauncherToolDef> = FREE

    fun paidTools(method: String): List<LauncherToolDef> =
        if (method == "chroot") PAID.filter { it.type != "codex" } else PAID

    fun shellTools(): List<LauncherToolDef> = SHELL

    fun isAiToolType(type: String): Boolean =
        type != "shell" && type != "shell-root"

    /** Dropdown / open-with pairs. */
    fun openWithPairs(method: String, showAi: Boolean): List<Pair<String, String>> {
        val shells = listOf(
            "Debian Shell" to "shell",
            "Debian Shell Rooted" to "shell-root"
        )
        if (!showAi) return shells
        val ai = listOf(
            "opencode" to "opencode",
            "codex" to "codex",
            "agy" to "agy",
            "claude-code" to "claude-code",
            "qwen-code" to "qwen-code",
            "grok" to "grok",
            "kiro" to "kiro"
        ).let { list ->
            if (method == "chroot") list.filter { it.second != "codex" } else list
        }
        return shells + ai
    }
}
