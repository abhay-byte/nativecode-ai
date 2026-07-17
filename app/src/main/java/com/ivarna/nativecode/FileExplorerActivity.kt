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
 * Page 4: File Explorer Drawer (standalone screen)
 * HTML prototype: file_explorer/code.html
 */
class FileExplorerActivity : AppCompatActivity() {

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
            setBackgroundColor(Color.parseColor("#15121b"))
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        val back = TextView(this).apply { text = "← "; textSize = 18f; setTextColor(NC.ON_SURF_VAR); setOnClickListener { finish() } }
        val projectName = TextView(this).apply {
            text = "MyAndroidApp"; textSize = 18f; setTextColor(NC.PRIMARY); typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        val searchBtn = TextView(this).apply { text = "\uD83D\uDD0D"; textSize = 18f; setPadding(dp(8), 0, dp(8), 0) }
        val profileBtn = TextView(this).apply { text = "\uD83D\uDC64"; textSize = 18f }
        topBar.addView(back); topBar.addView(projectName); topBar.addView(searchBtn); topBar.addView(profileBtn)
        root.addView(topBar)

        val scroll = ScrollView(this).apply { layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f) }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(24))
        }

        // Project Root Panel
        content.addView(buildFilePanel())
        content.addView(spacer(12))

        // Git Changes Panel
        content.addView(buildGitChangesPanel())

        scroll.addView(content)
        root.addView(scroll)

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

    private fun buildFilePanel(): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(NC.SURFACE, NC.BORDER, dp(10))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }

        // Card header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(NC.SURFACE_VAR); setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        val title = TextView(this).apply {
            text = "\uD83D\uDCC2 Project Root"; textSize = 15f; setTextColor(NC.ON_SURFACE); typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        val newFolder = TextView(this).apply { text = "\uD83D\uDCC1+"; textSize = 16f; setTextColor(NC.ON_SURF_VAR); setPadding(dp(8), 0, dp(4), 0) }
        val newFile = TextView(this).apply { text = "\uD83D\uDCDD+"; textSize = 16f; setTextColor(NC.ON_SURF_VAR) }
        header.addView(title); header.addView(newFolder); header.addView(newFile)
        card.addView(header)

        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(4), dp(8), dp(8))
        }
        // Folders
        for ((icon, name, isFolder) in listOf(
            Triple("\uD83D\uDCC1", "app", true),
            Triple("\uD83D\uDCC1", "gradle", true),
            Triple("\uD83D\uDCDD", ".gitignore", false),
            Triple("\uD83D\uDCBB", "build.gradle.kts", false),
            Triple("\u2699\uFE0F", "settings.gradle.kts", false)
        )) {
            val row = fileRow(icon, name, isFolder, modified = name == "build.gradle.kts")
            list.addView(row)
        }
        card.addView(list)
        return card
    }

    private fun buildGitChangesPanel(): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(NC.SURFACE, NC.BORDER, dp(10))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(NC.SURFACE_VAR); setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        val title = TextView(this).apply {
            text = "\uD83C\uDF3F Git Changes (2)"; textSize = 15f; setTextColor(NC.ON_SURFACE); typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        val commitBtn = TextView(this).apply { text = "Commit"; textSize = 13f; setTextColor(NC.PRIMARY); typeface = Typeface.DEFAULT_BOLD }
        header.addView(title); header.addView(commitBtn)
        card.addView(header)

        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(4), dp(8), dp(8))
        }
        for (name in listOf("MainActivity.kt", "build.gradle.kts")) {
            list.addView(fileRow("\uD83D\uDCBB", name, false, modified = true, modColor = NC.TERTIARY))
        }
        card.addView(list)
        return card
    }

    private fun fileRow(icon: String, name: String, isFolder: Boolean, modified: Boolean = false, modColor: Int = NC.TERTIARY): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(10), dp(8), dp(10))
        }
        val iconTv = TextView(this).apply { text = icon; textSize = 16f; setPadding(0, 0, dp(10), 0); setTextColor(if (isFolder) NC.SECONDARY else NC.OUTLINE) }
        val nameTv = TextView(this).apply {
            text = name; textSize = 13f; typeface = Typeface.MONOSPACE
            setTextColor(if (modified) Color.parseColor("#ffdcc6") else NC.ON_SURFACE)
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        row.addView(iconTv); row.addView(nameTv)
        if (modified) {
            val badge = TextView(this).apply {
                text = "M"; textSize = 10f; setTextColor(NC.ON_SURFACE)
                background = roundedBg(modColor, modColor, dp(4))
                setPadding(dp(6), dp(2), dp(6), dp(2))
            }
            row.addView(badge)
        }
        return row
    }

    private fun spacer(dp_: Int) = android.view.View(this).apply { layoutParams = LinearLayout.LayoutParams(MATCH, dp(dp_)) }
    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()
    private fun roundedBg(fill: Int, stroke: Int, r: Int) = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; setColor(fill); setStroke(dp(1), stroke); cornerRadius = r.toFloat() }
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
}
