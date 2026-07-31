package com.ivarna.nativecode.cliauth

import android.content.Context
import com.ivarna.nativecode.terminal.LinuxCommandBuilder

/**
 * Host prefs SSOT for AI CLI suite install (C6).
 * Gate Terminal/Workspace Free+Paid launchers on [shouldShowAiToolLaunchers].
 *
 * **Per isolation method** — proot and chroot flags are independent.
 * Installing chroot suite must not hide tools on a proot project (and reverse).
 */
object AiCliProvisionState {

    private const val PREFS = "nativecode_prefs"

    const val KEY_ENABLE_AI = "enable_ai_cli_install"
    const val KEY_PLAN_ACCEPTED = "install_plan_accepted"
    /** @deprecated legacy single-slot; migrated into per-method keys on read. */
    const val KEY_PROVISIONED = "ai_cli_tools_provisioned"
    /** @deprecated legacy; see [KEY_PROVISIONED]. */
    const val KEY_PROVISIONED_METHOD = "ai_cli_tools_provisioned_method"
    const val KEY_ENABLE_CUSTOM = "enable_debian_customization"

    private const val KEY_PROVISIONED_PROOT = "ai_cli_tools_provisioned_proot"
    private const val KEY_PROVISIONED_CHROOT = "ai_cli_tools_provisioned_chroot"

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun methodKey(method: String): String =
        if (method == "chroot") KEY_PROVISIONED_CHROOT else KEY_PROVISIONED_PROOT

    private fun normalizeMethod(method: String): String =
        if (method == "chroot") "chroot" else "proot"

    /** One-shot migrate legacy single slot → per-method booleans. */
    private fun migrateLegacyIfNeeded(ctx: Context) {
        val p = prefs(ctx)
        if (p.contains(KEY_PROVISIONED_PROOT) || p.contains(KEY_PROVISIONED_CHROOT)) return
        if (!p.getBoolean(KEY_PROVISIONED, false)) return
        val pm = p.getString(KEY_PROVISIONED_METHOD, null) ?: return
        val ed = p.edit()
        when (normalizeMethod(pm)) {
            "chroot" -> ed.putBoolean(KEY_PROVISIONED_CHROOT, true)
            else -> ed.putBoolean(KEY_PROVISIONED_PROOT, true)
        }
        // keep legacy keys for older readers; new reads use per-method only after migrate
        ed.apply()
    }

    /**
     * True when suite marked installed for [method].
     * Pass project isolation in workspace; default = global currentMethod (settings / app terminal).
     */
    fun isAiCliSuiteAvailable(
        ctx: Context,
        method: String = LinuxCommandBuilder.currentMethod
    ): Boolean {
        migrateLegacyIfNeeded(ctx)
        val m = normalizeMethod(method)
        val p = prefs(ctx)
        if (p.getBoolean(methodKey(m), false)) return true
        // legacy fallback before migrate wrote keys (race / partial prefs)
        if (p.getBoolean(KEY_PROVISIONED, false)) {
            val pm = p.getString(KEY_PROVISIONED_METHOD, null) ?: return false
            return normalizeMethod(pm) == m
        }
        return false
    }

    fun shouldShowAiToolLaunchers(
        ctx: Context,
        method: String = LinuxCommandBuilder.currentMethod
    ): Boolean = isAiCliSuiteAvailable(ctx, method)

    /** Mark or clear suite for one method only — never wipes the other isolation. */
    fun markAiCliProvisioned(ctx: Context, method: String, ok: Boolean) {
        migrateLegacyIfNeeded(ctx)
        val m = normalizeMethod(method)
        val ed = prefs(ctx).edit()
            .putBoolean(methodKey(m), ok)
        // Keep legacy slot pointing at last success for old code paths / debug
        if (ok) {
            ed.putBoolean(KEY_PROVISIONED, true)
                .putString(KEY_PROVISIONED_METHOD, m)
        } else {
            val other = if (m == "chroot") "proot" else "chroot"
            val otherOk = prefs(ctx).getBoolean(methodKey(other), false)
            if (otherOk) {
                ed.putBoolean(KEY_PROVISIONED, true)
                    .putString(KEY_PROVISIONED_METHOD, other)
            } else {
                ed.putBoolean(KEY_PROVISIONED, false)
                    .remove(KEY_PROVISIONED_METHOD)
            }
        }
        ed.apply()
    }

    /**
     * @param method null → clear both isolation slots; else only that method.
     */
    fun clearAiCliProvisioned(ctx: Context, method: String? = null) {
        migrateLegacyIfNeeded(ctx)
        val p = prefs(ctx)
        val ed = p.edit()
        if (method == null) {
            ed.putBoolean(KEY_PROVISIONED_PROOT, false)
                .putBoolean(KEY_PROVISIONED_CHROOT, false)
                .putBoolean(KEY_PROVISIONED, false)
                .remove(KEY_PROVISIONED_METHOD)
        } else {
            val m = normalizeMethod(method)
            ed.putBoolean(methodKey(m), false)
            val other = if (m == "chroot") "proot" else "chroot"
            val otherOk = p.getBoolean(methodKey(other), false)
            if (otherOk) {
                ed.putBoolean(KEY_PROVISIONED, true)
                    .putString(KEY_PROVISIONED_METHOD, other)
            } else {
                ed.putBoolean(KEY_PROVISIONED, false)
                    .remove(KEY_PROVISIONED_METHOD)
            }
        }
        ed.apply()
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
