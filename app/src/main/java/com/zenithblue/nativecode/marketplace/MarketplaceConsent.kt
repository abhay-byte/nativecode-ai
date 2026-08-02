package com.ivarna.nativecode.marketplace

import android.content.Context

/**
 * First-use consent for Marketplace (remote catalog + install scripts).
 * Once accepted, catalog/install flows work without re-prompt.
 */
object MarketplaceConsent {

    private const val PREFS = "nativecode_prefs"
    private const val KEY_ACCEPTED = "marketplace_consent_accepted"

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isAccepted(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_ACCEPTED, false)

    fun setAccepted(ctx: Context, accepted: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_ACCEPTED, accepted).apply()
    }
}
