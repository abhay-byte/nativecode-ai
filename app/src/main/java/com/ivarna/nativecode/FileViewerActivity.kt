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
 * Page 5: File Viewer / Code View Editor
 * HTML prototype: file_viewer_area/code.html
 */
class FileViewerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = NC.BG
        window.navigationBarColor = NC.BG

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(NC.BG)
            layoutParams = ViewGroup.LayoutParams(MATCH, MATCH)
        }

        // Top App Bar
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#15121b"))
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        val backBtn = TextView(this).apply { text = "←"; textSize = 18f; setTextColor(NC.SECONDARY); setPadding(dp(4), 0, dp(12), 0); setOnClickListener { finish() } }
        val fileTitleTv = TextView(this).apply { text = "main.rs"; textSize = 18f; setTextColor(NC.PRIMARY); typeface = Typeface.DEFAULT_BOLD; layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f) }
        val moreBtn = TextView(this).apply { text = "⋮"; textSize = 20f; setTextColor(NC.SECONDARY) }
        topBar.addView(backBtn); topBar.addView(fileTitleTv); topBar.addView(moreBtn)
        root.addView(topBar)

        val scroll = ScrollView(this).apply { layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f) }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(24))
        }

        // Image preview card
        content.addView(buildImagePreviewCard())
        content.addView(spacer(16))

        // Code viewer card (main.rs)
        content.addView(buildCodeViewerCard())

        scroll.addView(content)
        root.addView(scroll)

        // Bottom nav (files tab active)
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

    private fun buildImagePreviewCard(): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(NC.SURFACE, NC.BORDER, dp(12))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }

        // Image placeholder area
        val imgArea = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setBackgroundColor(NC.SURFACE_HIGH)
            layoutParams = LinearLayout.LayoutParams(MATCH, dp(180))
        }
        val placeholder = TextView(this).apply {
            text = "\uD83D\uDDBC\uFE0F"
            textSize = 64f; gravity = Gravity.CENTER
        }
        imgArea.addView(placeholder)

        // PNG badge
        val badge = textBadge("PNG", NC.SURFACE_VAR, NC.ON_SURFACE)
        badge.layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).apply {
            topMargin = -dp(8); rightMargin = dp(8); gravity = Gravity.END
        }

        card.addView(imgArea)

        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        val info = TextView(this).apply { text = "\uD83D\uDDBC\uFE0F  1920×1080 • 2.4 MB"; textSize = 13f; setTextColor(NC.ON_SURF_VAR); layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f) }
        val dlBtn = TextView(this).apply { text = "⬇"; textSize = 18f; setTextColor(NC.ON_SURFACE); background = roundedBg(NC.SURFACE_VAR, NC.BORDER, dp(6)); setPadding(dp(8), dp(6), dp(8), dp(6)) }
        footer.addView(info); footer.addView(dlBtn)
        card.addView(footer)
        return card
    }

    private fun buildCodeViewerCard(): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(NC.LOGBG, NC.BORDER_VAR, dp(12))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }

        // Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(NC.SURFACE); setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        val termIcon = TextView(this).apply { text = "\uD83D\uDCBB "; textSize = 14f; setTextColor(NC.TERTIARY) }
        val fileNameTv = TextView(this).apply { text = "main.rs"; textSize = 13f; setTextColor(NC.ON_SURFACE); typeface = Typeface.MONOSPACE; layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f) }
        val copyBtn = TextView(this).apply {
            text = "COPY"; textSize = 10f; setTextColor(NC.ON_SURFACE); typeface = Typeface.MONOSPACE
            background = roundedBg(NC.SURFACE_VAR, NC.BORDER, dp(4)); setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        header.addView(termIcon); header.addView(fileNameTv); header.addView(copyBtn)
        card.addView(header)

        // Code content
        val codeScroll = HorizontalScrollView(this).apply { setPadding(dp(12), dp(12), dp(12), dp(12)) }
        val codeLines = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val lines = listOf(
            listOf("\u029f" to NC.PRIMARY, " " to 0, "main" to NC.SECONDARY, "() {" to NC.ON_SURFACE),
            listOf("    " to 0, "println!" to NC.TERTIARY, "(\"Hello from the Deep Void!\");" to Color.parseColor("#acedff")),
            listOf("    " to 0),
            listOf("    " to 0, "let" to NC.PRIMARY, " active_state = " to NC.ON_SURFACE, "true" to NC.PRIMARY_CON, ";" to NC.ON_SURFACE),
            listOf("    " to 0, "if" to NC.PRIMARY, " active_state {" to NC.ON_SURFACE),
            listOf("        " to 0, "initialize_system" to NC.SECONDARY, "();" to NC.ON_SURFACE),
            listOf("    }" to NC.ON_SURFACE),
            listOf("}" to NC.ON_SURFACE)
        )

        for ((lineNum, tokens) in lines.withIndex()) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val lineNumTv = TextView(this).apply {
                text = "${lineNum + 1}"; textSize = 12f; setTextColor(NC.OUTLINE); typeface = Typeface.MONOSPACE
                gravity = Gravity.END; layoutParams = LinearLayout.LayoutParams(dp(28), WRAP)
                setPadding(0, 0, dp(12), 0)
            }
            row.addView(lineNumTv)
            for ((text, color) in tokens) {
                if (text.isEmpty()) continue
                val tv = TextView(this).apply {
                    this.text = text; textSize = 12f; typeface = Typeface.MONOSPACE
                    setTextColor(if (color == 0) NC.ON_SURFACE else color)
                }
                row.addView(tv)
            }
            codeLines.addView(row)
        }
        codeScroll.addView(codeLines)
        card.addView(codeScroll)
        return card
    }

    private fun buildBottomBar(): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            setBackgroundColor(NC.SURFACE_VAR); setPadding(dp(16), dp(10), dp(16), dp(16))
        }
        for ((icon, active) in listOf("\uD83C\uDFE0" to false, "\uD83D\uDCC2" to true, "\uD83D\uDCBB" to false, "\uD83D\uDD00" to false, "\u2699\uFE0F" to false)) {
            val btn = LinearLayout(this).apply {
                gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
                if (active) { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(NC.PRIMARY_CON) }; setPadding(dp(12), dp(10), dp(12), dp(10)) }
            }
            val tv = TextView(this).apply { text = icon; textSize = 20f; gravity = Gravity.CENTER }
            btn.addView(tv); bar.addView(btn)
        }
        return bar
    }

    private fun textBadge(text: String, bg: Int, fg: Int) = TextView(this).apply { this.text = text; textSize = 10f; setTextColor(fg); typeface = Typeface.MONOSPACE; background = roundedBg(bg, fg, dp(4)); setPadding(dp(6), dp(2), dp(6), dp(2)) }
    private fun spacer(dp_: Int) = android.view.View(this).apply { layoutParams = LinearLayout.LayoutParams(MATCH, dp(dp_)) }
    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()
    private fun roundedBg(fill: Int, stroke: Int, r: Int) = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; setColor(fill); setStroke(dp(1), stroke); cornerRadius = r.toFloat() }
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
}
