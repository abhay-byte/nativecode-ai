package com.ivarna.nativecode.cliauth

/**
 * AI CLI tools from setup_cli_tools.sh + login strategy per vendor docs.
 */
enum class CliLoginStrategy {
    /** Stream login cmd; parse OTP + URL (codex device-auth, kiro). */
    DEVICE_CODE,
    /** Stream login; parse https URL + optional token capture (claude setup-token). */
    STREAM_URL,
    /** Paste API key (+ optional open console URL). */
    API_KEY_FORM,
    /** Open terminal with login command (interactive TUI). */
    TERMINAL_GUIDED
}

data class CliToolDef(
    val id: String,
    val displayName: String,
    val bin: String,
    /** Alternate bins to probe (e.g. kiro vs kiro-cli). */
    val altBins: List<String> = emptyList(),
    val strategy: CliLoginStrategy,
    /** Terminal session type key used by MainActivity tool selector. */
    val terminalType: String,
    /** Hide on chroot (codex). */
    val hideOnChroot: Boolean = false,
    /** Console URL for API key / account signup. */
    val consoleUrl: String? = null,
    val envKey: String? = null,
    val subtitle: String = ""
)

object CliToolCatalog {

    val ALL: List<CliToolDef> = listOf(
        CliToolDef(
            id = "claude",
            displayName = "Claude Code",
            bin = "claude",
            strategy = CliLoginStrategy.STREAM_URL,
            terminalType = "claude-code",
            consoleUrl = "https://claude.ai/login",
            envKey = "CLAUDE_CODE_OAUTH_TOKEN",
            subtitle = "claude setup-token · browser OAuth"
        ),
        CliToolDef(
            id = "codex",
            displayName = "OpenAI Codex",
            bin = "codex",
            strategy = CliLoginStrategy.DEVICE_CODE,
            terminalType = "codex",
            hideOnChroot = true,
            consoleUrl = "https://auth.openai.com/codex/device",
            subtitle = "codex login --device-auth"
        ),
        CliToolDef(
            id = "qwen",
            displayName = "Qwen Code",
            bin = "qwen",
            strategy = CliLoginStrategy.API_KEY_FORM,
            terminalType = "qwen-code",
            consoleUrl = "https://modelstudio.console.alibabacloud.com/?tab=coding-plan#/efm/coding-plan-index",
            envKey = "BAILIAN_CODING_PLAN_API_KEY",
            subtitle = "API key · OAuth free tier discontinued"
        ),
        CliToolDef(
            id = "opencode",
            displayName = "OpenCode",
            bin = "opencode",
            strategy = CliLoginStrategy.TERMINAL_GUIDED,
            terminalType = "opencode",
            consoleUrl = "https://opencode.ai/auth",
            subtitle = "opencode auth login · providers"
        ),
        CliToolDef(
            id = "agy",
            displayName = "Antigravity",
            bin = "agy",
            strategy = CliLoginStrategy.TERMINAL_GUIDED,
            terminalType = "agy",
            consoleUrl = "https://accounts.google.com",
            subtitle = "Google OAuth · paste code in terminal"
        ),
        CliToolDef(
            id = "grok",
            displayName = "Grok Build",
            bin = "grok",
            strategy = CliLoginStrategy.API_KEY_FORM,
            terminalType = "grok",
            consoleUrl = "https://console.x.ai",
            envKey = "XAI_API_KEY",
            subtitle = "XAI_API_KEY or first-run browser"
        ),
        CliToolDef(
            id = "kiro",
            displayName = "Kiro CLI",
            bin = "kiro-cli",
            altBins = listOf("kiro"),
            strategy = CliLoginStrategy.DEVICE_CODE,
            terminalType = "kiro",
            consoleUrl = "https://app.kiro.dev",
            envKey = "KIRO_API_KEY",
            subtitle = "kiro-cli login · device / browser"
        )
    )

    fun byId(id: String): CliToolDef? = ALL.find { it.id == id }

    fun forMethod(method: String): List<CliToolDef> =
        if (method == "chroot") ALL.filter { !it.hideOnChroot } else ALL
}
