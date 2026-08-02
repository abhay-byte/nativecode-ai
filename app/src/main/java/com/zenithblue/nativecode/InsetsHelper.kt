package com.zenithblue.nativecode

import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Apply system bar insets to a root view so content never draws under the
 * status bar (top) or navigation bar (bottom).
 *
 * Call this AFTER setContentView() in every Activity:
 *   applySystemInsets(window.decorView.rootView)
 * OR pass the specific root view that should receive the padding.
 */
fun applySystemInsets(view: View) {
    ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
        val bars = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or
            WindowInsetsCompat.Type.displayCutout()
        )
        v.setPadding(
            v.paddingLeft,   // preserve existing horizontal padding
            bars.top,
            v.paddingRight,
            bars.bottom
        )
        insets
    }
    // Request a fresh insets pass now
    ViewCompat.requestApplyInsets(view)
}

/**
 * Variant that applies only top inset to a specific view (e.g. a top-bar),
 * and only bottom inset to another view (e.g. a bottom nav).
 *
 * This is the precise approach: pad the top-bar for status bar height,
 * pad the bottom nav for gesture/nav bar height, leave middle content alone.
 */
fun applyTopInset(topBar: View, bottomBar: View? = null) {
    val root = topBar.rootView ?: return
    ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
        val bars = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or
            WindowInsetsCompat.Type.displayCutout()
        )
        topBar.setPadding(
            topBar.paddingLeft,
            bars.top,
            topBar.paddingRight,
            topBar.paddingBottom
        )
        bottomBar?.setPadding(
            bottomBar.paddingLeft,
            bottomBar.paddingTop,
            bottomBar.paddingRight,
            bars.bottom
        )
        insets
    }
    ViewCompat.requestApplyInsets(root)
}
