package com.ivarna.nativecode

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Page 1: Architecture Selection – PROOT vs CHROOT onboarding
 * HTML prototype: stitch_nativecode_ai_developer_environment/architecture_selection/code.html
 */
class ArchitectureSelectionActivity : AppCompatActivity() {

    private var selectedCard: LinearLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = NC.BG
        window.navigationBarColor = NC.BG

        val root = ScrollView(this).apply {
            setBackgroundColor(NC.BG)
            layoutParams = ViewGroup.LayoutParams(MATCH, MATCH)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(48), dp(24), dp(48))
        }

        // Header icon + brand
        val iconBox = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = roundedBg(NC.SURFACE_VAR, NC.BORDER_VAR, dp(12))
            setPadding(dp(16), dp(16), dp(16), dp(16))
            layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).apply { bottomMargin = dp(16) }
        }
        val memIcon = TextView(this).apply { text = "\uD83D\uDCBB"; textSize = 28f }
        iconBox.addView(memIcon)
        container.addView(iconBox)

        val title = TextView(this).apply {
            text = "NativeCode AI Dev Environment"
            textSize = 22f
            setTextColor(NC.ON_SURFACE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(8))
        }
        val subtitle = TextView(this).apply {
            text = "Choose your isolation method to configure your workspace."
            textSize = 14f
            setTextColor(NC.ON_SURF_VAR)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(32))
        }
        container.addView(title)
        container.addView(subtitle)

        // Cards row (vertical on mobile)
        val prootCard = buildArchCard(
            icon = "\uD83D\uDEE1\uFE0F", label = "PROOT",
            badge = "RECOMMENDED", badgeColor = NC.PRIMARY_CON,
            desc = "No root required. Bypasses W^X via JNI. Ideal for most standard development tasks and AI model testing.",
            footerLabel = "User Space", isRecommended = true
        )
        val chrootCard = buildArchCard(
            icon = "\uD83D\uDD13", label = "CHROOT",
            badge = null, badgeColor = 0,
            desc = "Requires Root access. Delivers native performance and full hardware acceleration for heavy workloads.",
            footerLabel = "Kernel Space", isRecommended = false
        )

        selectedCard = prootCard
        highlightCard(prootCard, true)

        prootCard.setOnClickListener { selectCard(prootCard, chrootCard) }
        chrootCard.setOnClickListener { selectCard(chrootCard, prootCard) }

        container.addView(prootCard)
        container.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(MATCH, dp(12)) })
        container.addView(chrootCard)
        container.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(MATCH, dp(32)) })

        // Action buttons
        val initBtn = TextView(this).apply {
            text = "Initialize Environment"
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = roundedBg(NC.PRIMARY_CON, NC.PRIMARY_CON, dp(24))
            setPadding(dp(24), dp(14), dp(24), dp(14))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(12) }
            setOnClickListener { finish() }
        }
        val advBtn = TextView(this).apply {
            text = "Advanced Settings"
            textSize = 14f
            setTextColor(NC.ON_SURF_VAR)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        container.addView(initBtn)
        container.addView(advBtn)

        root.addView(container)

        // Respect system bars
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            root.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
        setContentView(root)
    }

    private fun buildArchCard(
        icon: String, label: String, badge: String?,
        badgeColor: Int, desc: String, footerLabel: String, isRecommended: Boolean
    ): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(NC.SURFACE, NC.BORDER, dp(12))
            setPadding(dp(20), dp(20), dp(20), dp(20))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(12))
        }
        val iconBox = LinearLayout(this).apply {
            background = roundedBg(NC.SURFACE_HIGH, NC.BORDER, dp(8))
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        val iconTv = TextView(this).apply { text = icon; textSize = 20f }
        iconBox.addView(iconTv)
        topRow.addView(iconBox)
        topRow.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })
        if (badge != null) {
            val badgeTv = textBadge(badge, Color.argb(51, 124, 58, 237), NC.PRIMARY)
            topRow.addView(badgeTv)
        }
        card.addView(topRow)

        val labelTv = TextView(this).apply {
            text = label
            textSize = 18f
            setTextColor(NC.ON_SURFACE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(8))
        }
        val descTv = TextView(this).apply {
            text = desc
            textSize = 13f
            setTextColor(NC.ON_SURF_VAR)
            setPadding(0, 0, 0, dp(16))
        }
        card.addView(labelTv)
        card.addView(descTv)

        // Divider
        val divider = View(this).apply {
            setBackgroundColor(NC.BORDER_VAR)
            layoutParams = LinearLayout.LayoutParams(MATCH, dp(1)).apply { bottomMargin = dp(12) }
        }
        card.addView(divider)

        val footerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val footerTv = TextView(this).apply {
            text = footerLabel
            textSize = 11f
            setTextColor(NC.ON_SURF_VAR)
            typeface = Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        val checkTv = TextView(this).apply {
            text = if (isRecommended) "\u2713" else "\u26A1"
            textSize = 16f
            setTextColor(NC.ON_SURF_VAR)
        }
        footerRow.addView(footerTv)
        footerRow.addView(checkTv)
        card.addView(footerRow)

        return card
    }

    private fun selectCard(selected: LinearLayout, other: LinearLayout) {
        highlightCard(selected, true)
        highlightCard(other, false)
    }

    private fun highlightCard(card: LinearLayout, active: Boolean) {
        card.background = if (active)
            roundedBg(NC.SURFACE, NC.PRIMARY, dp(12))
        else
            roundedBg(NC.SURFACE, NC.BORDER, dp(12))
    }

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()
    private fun roundedBg(fill: Int, stroke: Int, r: Int) = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; setColor(fill); setStroke(dp(1), stroke); cornerRadius = r.toFloat() }
    private fun textBadge(text: String, bg: Int, fg: Int) = TextView(this).apply { this.text = text; textSize = 10f; setTextColor(fg); typeface = Typeface.MONOSPACE; background = roundedBg(bg, fg, dp(4)); setPadding(dp(8), dp(4), dp(8), dp(4)) }
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
}
