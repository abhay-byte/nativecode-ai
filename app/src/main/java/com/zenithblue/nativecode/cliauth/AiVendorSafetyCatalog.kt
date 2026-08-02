package com.zenithblue.nativecode.cliauth

/**
 * SSOT for AI safety / report / ToS links (C5 / B16).
 * UI must not hardcode vendor URLs — change here only.
 */
data class VendorSafetyLinks(
    /** Matches [CliToolDef.id], or [NATIVECODE_TOOL_ID] for app-level row. */
    val toolId: String,
    val displayName: String,
    /** HTTPS content-report form when available. */
    val reportUrl: String? = null,
    /** Vendor safety / support mailto (e.g. support@x.ai). */
    val reportMailto: String? = null,
    val tosUrl: String? = null,
    val aupUrl: String? = null,
    /** Short mono subtitle for UI. */
    val notes: String = ""
)

object AiVendorSafetyCatalog {

    const val NATIVECODE_TOOL_ID = "nativecode"
    const val NATIVECODE_REPORT_EMAIL = "zenithblue.dev@gmail.com"

    val ALL: List<VendorSafetyLinks> = listOf(
        VendorSafetyLinks(
            toolId = NATIVECODE_TOOL_ID,
            displayName = "NativeCode",
            reportMailto = NATIVECODE_REPORT_EMAIL,
            notes = "App bugs · safety awareness · not model host"
        ),
        VendorSafetyLinks(
            toolId = "claude",
            displayName = "Claude Code",
            reportUrl = "https://claude.com/form/anthropic-content-reporting",
            reportMailto = "usersafety@anthropic.com",
            tosUrl = "https://www.anthropic.com/legal/consumer-terms",
            aupUrl = "https://www.anthropic.com/legal/aup",
            notes = "Anthropic content form · AUP"
        ),
        VendorSafetyLinks(
            toolId = "codex",
            displayName = "OpenAI Codex",
            reportUrl = "https://openai.com/form/report-content/",
            tosUrl = "https://openai.com/policies/terms-of-use",
            aupUrl = "https://openai.com/policies/usage-policies",
            notes = "OpenAI report form · usage policies"
        ),
        VendorSafetyLinks(
            toolId = "grok",
            displayName = "Grok Build",
            reportMailto = "support@x.ai",
            tosUrl = "https://x.ai/legal/terms-of-service",
            aupUrl = "https://x.ai/legal/acceptable-use-policy",
            notes = "xAI support · AUP (in-product Report Issue)"
        ),
        VendorSafetyLinks(
            toolId = "agy",
            displayName = "Antigravity",
            reportUrl = "https://reportcontent.google.com",
            tosUrl = "https://policies.google.com/terms/generative-ai",
            notes = "Google report content · gen-AI terms"
        ),
        VendorSafetyLinks(
            toolId = "qwen",
            displayName = "Qwen Code",
            reportUrl = "https://modelstudio.console.alibabacloud.com/?tab=coding-plan#/efm/coding-plan-index",
            notes = "Vendor console / account support · no public AI form"
        ),
        VendorSafetyLinks(
            toolId = "opencode",
            displayName = "OpenCode",
            reportUrl = "https://opencode.ai",
            notes = "Site / project issues · use NativeCode mailto too"
        ),
        VendorSafetyLinks(
            toolId = "kiro",
            displayName = "Kiro CLI",
            reportUrl = "https://app.kiro.dev",
            notes = "AWS / Kiro app support · no public gen-AI form"
        )
    )

    fun forTool(toolId: String): VendorSafetyLinks? =
        ALL.find { it.idEquals(toolId) }

    private fun VendorSafetyLinks.idEquals(id: String) =
        toolId.equals(id, ignoreCase = true)

    /**
     * Vendor rows for current isolation (hide tools not in [CliToolCatalog.forMethod]).
     * Does not include NativeCode app row — UI paints that separately.
     */
    fun forMethod(method: String): List<VendorSafetyLinks> {
        val ids = CliToolCatalog.forMethod(method).map { it.id }.toSet()
        return ALL.filter { it.toolId != NATIVECODE_TOOL_ID && it.toolId in ids }
    }

    fun nativeCode(): VendorSafetyLinks =
        ALL.first { it.toolId == NATIVECODE_TOOL_ID }
}
