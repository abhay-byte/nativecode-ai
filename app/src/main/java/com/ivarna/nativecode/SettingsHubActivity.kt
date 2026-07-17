package com.ivarna.nativecode

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Page 10: Settings Hub
 * HTML prototype: settings_hub/code.html
 */
class SettingsHubActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = NC.BG
        window.navigationBarColor = NC.BG

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(NC.BG)
            layoutParams = ViewGroup.LayoutParams(MATCH, MATCH)
        }

        // Top bar
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#15121b")); setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        val appIcon = TextView(this).apply { text = "\uD83D\uDCBB"; textSize = 20f }
        val appTitle = TextView(this).apply { text = "NativeCode"; textSize = 18f; setTextColor(NC.PRIMARY); typeface = Typeface.DEFAULT_BOLD; setPadding(dp(8), 0, 0, 0); layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f) }
        val profileBtn = TextView(this).apply { text = "\uD83D\uDC64"; textSize = 22f }
        topBar.addView(appIcon); topBar.addView(appTitle); topBar.addView(profileBtn)
        root.addView(topBar)

        val scroll = ScrollView(this).apply { layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f) }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(20), dp(16), dp(32))
        }

        // Page title
        val pageTitle = TextView(this).apply { text = "Settings Hub"; textSize = 22f; setTextColor(NC.ON_SURFACE); typeface = Typeface.DEFAULT_BOLD; setPadding(0, 0, 0, dp(4)) }
        val pageSub = TextView(this).apply { text = "Configure your development environment and preferences."; textSize = 14f; setTextColor(NC.ON_SURF_VAR); setPadding(0, 0, 0, dp(24)) }
        content.addView(pageTitle); content.addView(pageSub)

        // Environment card
        content.addView(buildEnvironmentCard()); content.addView(spacer(12))
        // AI Tools card
        content.addView(buildAIToolsCard()); content.addView(spacer(12))
        // Appearance card
        content.addView(buildAppearanceCard()); content.addView(spacer(12))
        // Account card
        content.addView(buildAccountCard())

        scroll.addView(content)
        root.addView(scroll)

        // Bottom nav (settings tab active)
        root.addView(buildBottomBar())


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

    private fun buildEnvironmentCard(): LinearLayout {
        val card = glassCard()
        val header = sectionHeader("\uD83D\uDDA5\uFE0F", "Environment", NC.SECONDARY)
        card.addView(header)

        for ((label, value) in listOf("Container Method" to "PRoot", "OS Version" to "Debian 12")) {
            card.addView(infoRow(label, value))
            card.addView(spacer(8))
        }
        card.addView(spacer(8))

        val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val resetBtn = outlineBtn("Reset Environment")
        val destroyBtn = dangerBtn("Destroy Container")
        resetBtn.layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).apply { rightMargin = dp(8) }
        destroyBtn.layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        btnRow.addView(resetBtn); btnRow.addView(destroyBtn)
        card.addView(btnRow)
        return card
    }

    private fun buildAIToolsCard(): LinearLayout {
        val card = glassCard()
        card.addView(sectionHeader("\uD83E\uDD16", "AI Tools", NC.PRIMARY))
        val sub = TextView(this).apply { text = "Installed Models:"; textSize = 13f; setTextColor(NC.ON_SURF_VAR); setPadding(0, 0, 0, dp(10)) }
        card.addView(sub)
        val tagRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 0, 0, dp(16)) }
        for (tool in listOf("Claude", "Aider", "Cline")) {
            val tag = textBadge(tool, Color.argb(26, 76, 215, 246), NC.SECONDARY)
            tag.layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).apply { rightMargin = dp(8) }
            tagRow.addView(tag)
        }
        card.addView(tagRow)
        val manageBtn = primaryBtn("Manage CLI Tools")
        card.addView(manageBtn)
        return card
    }

    private fun buildAppearanceCard(): LinearLayout {
        val card = glassCard()
        card.addView(sectionHeader("\uD83C\uDFA8", "Appearance", NC.TERTIARY))

        val themeLbl = TextView(this).apply { text = "Theme"; textSize = 12f; setTextColor(NC.ON_SURF_VAR); setPadding(0, 0, 0, dp(4)) }
        card.addView(themeLbl)

        val spinner = Spinner(this).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; setColor(NC.SURFACE_HIGH); setStroke(dp(1), NC.BORDER_VAR); cornerRadius = dp(6).toFloat() }
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(16) }
        }
        val themes = arrayOf("Midnight Aurora (Dark)", "Abyss (Deep Dark)", "Solarized (Light)")
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, themes).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        card.addView(spinner)

        val fontLbl = TextView(this).apply { text = "Editor Font Size"; textSize = 12f; setTextColor(NC.ON_SURF_VAR); setPadding(0, 0, 0, dp(4)) }
        card.addView(fontLbl)
        val sliderRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val slider = SeekBar(this).apply { max = 14; progress = 4; layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f) }
        val sliderVal = TextView(this).apply { text = "14px"; textSize = 13f; setTextColor(NC.PRIMARY); typeface = Typeface.MONOSPACE; setPadding(dp(12), 0, 0, 0) }
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, u: Boolean) { sliderVal.text = "${p + 10}px" }
            override fun onStartTrackingTouch(s: SeekBar) {}
            override fun onStopTrackingTouch(s: SeekBar) {}
        })
        sliderRow.addView(slider); sliderRow.addView(sliderVal)
        card.addView(sliderRow)
        return card
    }

    private fun buildAccountCard(): LinearLayout {
        val card = glassCard()
        card.addView(sectionHeader("\uD83D\uDC64", "Account Connections", NC.SECONDARY))

        val ghRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = roundedBg(NC.SURFACE, NC.BORDER_VAR, dp(8)); setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(16) }
        }
        val ghIcon = TextView(this).apply { text = "\uD83D\uDD17"; textSize = 20f; setPadding(0, 0, dp(12), 0) }
        val ghDetails = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f) }
        val ghName = TextView(this).apply { text = "GitHub"; textSize = 14f; setTextColor(NC.ON_SURFACE) }
        val ghStatus = TextView(this).apply { text = "Connected (dev_ninja)"; textSize = 12f; setTextColor(NC.SECONDARY); typeface = Typeface.MONOSPACE }
        ghDetails.addView(ghName); ghDetails.addView(ghStatus)
        val checkTv = TextView(this).apply { text = "✓"; textSize = 18f; setTextColor(NC.SECONDARY) }
        ghRow.addView(ghIcon); ghRow.addView(ghDetails); ghRow.addView(checkTv)
        card.addView(ghRow)

        val disconnectBtn = outlineBtn("Disconnect GitHub")
        disconnectBtn.layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        card.addView(disconnectBtn)
        return card
    }

    private fun buildBottomBar(): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            setBackgroundColor(NC.SURFACE_VAR); setPadding(dp(16), dp(10), dp(16), dp(16))
        }
        for ((icon, active) in listOf("\uD83C\uDFE0" to false, "\uD83D\uDCC2" to false, "\uD83D\uDCBB" to false, "\uD83D\uDD00" to false, "\u2699\uFE0F" to true)) {
            val btn = LinearLayout(this).apply {
                gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
                if (active) { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(NC.PRIMARY_CON) }; setPadding(dp(12), dp(10), dp(12), dp(10)) }
            }
            val tv = TextView(this).apply { text = icon; textSize = 20f; gravity = Gravity.CENTER }
            btn.addView(tv); bar.addView(btn)
        }
        return bar
    }

    private fun glassCard() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = roundedBg(NC.SURFACE, NC.BORDER, dp(12))
        setPadding(dp(16), dp(16), dp(16), dp(16))
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
    }

    private fun sectionHeader(icon: String, title: String, iconColor: Int): LinearLayout {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, dp(14)) }
        val iconTv = TextView(this).apply { text = icon; textSize = 18f; setTextColor(iconColor); setPadding(0, 0, dp(10), 0) }
        val titleTv = TextView(this).apply { text = title; textSize = 16f; setTextColor(NC.ON_SURFACE); typeface = Typeface.DEFAULT_BOLD }
        row.addView(iconTv); row.addView(titleTv); return row
    }

    private fun infoRow(label: String, value: String): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = roundedBg(NC.SURFACE_HIGH, NC.BORDER_VAR, dp(8)); setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        val lbl = TextView(this).apply { text = label; textSize = 13f; setTextColor(NC.ON_SURF_VAR); layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f) }
        val val_ = TextView(this).apply { text = value; textSize = 13f; setTextColor(NC.PRIMARY); typeface = Typeface.MONOSPACE }
        row.addView(lbl); row.addView(val_); return row
    }

    private fun textBadge(text: String, bg: Int, fg: Int) = TextView(this).apply { this.text = text; textSize = 12f; setTextColor(fg); typeface = Typeface.MONOSPACE; background = roundedBg(bg, fg, dp(4)); setPadding(dp(10), dp(6), dp(10), dp(6)) }
    private fun primaryBtn(t: String) = TextView(this).apply { text = t; textSize = 14f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; background = roundedBg(NC.PRIMARY_CON, NC.PRIMARY_CON, dp(20)); setPadding(dp(20), dp(12), dp(20), dp(12)); layoutParams = LinearLayout.LayoutParams(MATCH, WRAP) }
    private fun outlineBtn(t: String) = TextView(this).apply { text = t; textSize = 13f; setTextColor(NC.ON_SURF_VAR); gravity = Gravity.CENTER; background = roundedBg(NC.SURFACE_HIGH, NC.BORDER, dp(20)); setPadding(dp(16), dp(10), dp(16), dp(10)) }
    private fun dangerBtn(t: String) = TextView(this).apply { text = t; textSize = 13f; setTextColor(NC.ERROR); gravity = Gravity.CENTER; background = roundedBg(Color.parseColor("#3d1212"), Color.parseColor("#93000a"), dp(20)); setPadding(dp(16), dp(10), dp(16), dp(10)) }
    private fun spacer(dp_: Int) = android.view.View(this).apply { layoutParams = LinearLayout.LayoutParams(MATCH, dp(dp_)) }
    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()
    private fun roundedBg(fill: Int, stroke: Int, r: Int) = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; setColor(fill); setStroke(dp(1), stroke); cornerRadius = r.toFloat() }
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
}
