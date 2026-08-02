package com.zenithblue.nativecode.cliauth

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.zenithblue.nativecode.terminal.LinuxCommandBuilder

/**
 * Builds mailto intents for NativeCode AI report (C5) and vendor safety mail.
 * No network; does not log description to disk.
 */
object ReportMailHelper {

    private const val BODY_MAX = 3500

    fun openNativeCodeReport(
        ctx: Context,
        toolId: String?,
        category: String,
        description: String
    ): Boolean {
        val (versionName, versionCode) = resolveVersion(ctx)
        val method = LinuxCommandBuilder.currentMethod
        val toolLabel = toolId?.let { id ->
            CliToolCatalog.byId(id)?.displayName?.let { "$it ($id)" }
                ?: AiVendorSafetyCatalog.forTool(id)?.displayName?.let { "$it ($id)" }
                ?: id
        } ?: "general / all tools"

        val subject =
            "[NativeCode AI Report] ${toolId ?: "general"} · $versionName"

        val body = buildString {
            appendLine("App version: $versionName ($versionCode)")
            appendLine("Isolation: $method")
            appendLine("Tool: $toolLabel")
            appendLine("Category: $category")
            appendLine("Description:")
            appendLine(description.ifBlank { "(none provided)" })
            appendLine()
            appendLine(
                "Note: Outputs come from third-party CLI vendors. " +
                    "This report is to the app developer."
            )
            appendLine("Vendor report links: Settings → AI Safety & Report.")
        }.take(BODY_MAX)

        return openMailto(
            ctx,
            address = AiVendorSafetyCatalog.NATIVECODE_REPORT_EMAIL,
            subject = subject,
            body = body
        )
    }

    fun openVendorMailto(ctx: Context, address: String, toolLabel: String): Boolean {
        val subject = "[NativeCode] Safety report — $toolLabel"
        val body =
            "I am reporting a concern related to $toolLabel used via NativeCode " +
                "(Android AI CLI launcher).\n\nDescription:\n"
        return openMailto(ctx, address = address, subject = subject, body = body.take(BODY_MAX))
    }

    private fun openMailto(
        ctx: Context,
        address: String,
        subject: String,
        body: String
    ): Boolean {
        return try {
            val uri = Uri.parse(
                "mailto:${Uri.encode(address)}" +
                    "?subject=${Uri.encode(subject)}" +
                    "&body=${Uri.encode(body)}"
            )
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = uri
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(Intent.createChooser(intent, "Send report email"))
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun resolveVersion(ctx: Context): Pair<String, String> {
        return try {
            val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            val name = pi.versionName ?: "?"
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pi.longVersionCode.toString()
            } else {
                @Suppress("DEPRECATION")
                pi.versionCode.toString()
            }
            name to code
        } catch (_: Exception) {
            "?" to "?"
        }
    }
}
