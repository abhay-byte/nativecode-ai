package com.ivarna.nativecode.cliauth

import android.content.Context
import com.ivarna.nativecode.terminal.LinuxCommandBuilder

/**
 * Host prefs SSOT for AI CLI suite install (C6).
 * Gate Terminal/Workspace Free+Paid launchers on [shouldShowAiToolLaunchers].
 */
object AiCliProvisionState {

    private const val PREFS = "nativecode_prefs"

    const val KEY_ENABLE_AI = "enable_ai_cli_install"
    const val KEY_PLAN_ACCEPTED = "install_plan_accepted"
    const val KEY_PROVISIONED = "ai_cli_tools_provisioned"
    const val KEY_PROVISIONED_METHOD = "ai_cli_tools_provisioned_method"
    const val KEY_ENABLE_CUSTOM = "enable_debian_customization"

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** True when suite marked installed for the *current* isolation method. */
    fun isAiCliSuiteAvailable(ctx: Context): Boolean {
        val p = prefs(ctx)
        if (!p.getBoolean(KEY_PROVISIONED, false)) return false
        val method = LinuxCommandBuilder.currentMethod
        val pm = p.getString(KEY_PROVISIONED_METHOD, null) ?: return false
        return pm == method
    }

    fun shouldShowAiToolLaunchers(ctx: Context): Boolean =
        isAiCliSuiteAvailable(ctx)

    fun markAiCliProvisioned(ctx: Context, method: String, ok: Boolean) {
        val ed = prefs(ctx).edit()
        if (ok) {
            ed.putBoolean(KEY_PROVISIONED, true)
                .putString(KEY_PROVISIONED_METHOD, method)
                .apply()
        } else {
            val cur = prefs(ctx).getString(KEY_PROVISIONED_METHOD, null)
            if (cur == null || cur == method) {
                ed.putBoolean(KEY_PROVISIONED, false)
                    .remove(KEY_PROVISIONED_METHOD)
                    .apply()
            }
        }
    }

    fun clearAiCliProvisioned(ctx: Context, method: String? = null) {
        val p = prefs(ctx)
        if (method == null) {
            p.edit()
                .putBoolean(KEY_PROVISIONED, false)
                .remove(KEY_PROVISIONED_METHOD)
                .apply()
            return
        }
        val cur = p.getString(KEY_PROVISIONED_METHOD, null)
        if (cur == null || cur == method) {
            p.edit()
                .putBoolean(KEY_PROVISIONED, false)
                .remove(KEY_PROVISIONED_METHOD)
                .apply()
        }
    }

    fun setEnableAiCliInstall(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_ENABLE_AI, enabled).apply()
    }

    fun isEnableAiCliInstall(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_ENABLE_AI, false)

    fun setPlanAccepted(ctx: Context, accepted: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_PLAN_ACCEPTED, accepted).apply()
    }

    fun setEnableCustomization(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_ENABLE_CUSTOM, enabled).apply()
    }

    fun isEnableCustomization(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_ENABLE_CUSTOM, true)
}
