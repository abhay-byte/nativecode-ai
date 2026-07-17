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
 * Page 8: Code Diff Viewer
 * HTML prototype: code_diff_viewer/code.html
 */
class CodeDiffViewerActivity : AppCompatActivity() {

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
        val fileTitle = TextView(this).apply { text = "MainActivity.kt"; textSize = 17f; setTextColor(NC.PRIMARY); typeface = Typeface.DEFAULT_BOLD }
        val diffBadge = textBadge("Git Diff", NC.SURFACE_VAR, NC.ON_SURF_VAR)
        diffBadge.layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).apply { leftMargin = dp(10) }
        val topSpacer = android.view.View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) }
        val more = TextView(this).apply { text = "⋮"; textSize = 20f; setTextColor(NC.ON_SURF_VAR) }
        topBar.addView(back); topBar.addView(fileTitle); topBar.addView(diffBadge)
        topBar.addView(topSpacer); topBar.addView(more)
        root.addView(topBar)

        // Scrollable diff content
        val scroll = ScrollView(this).apply { layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f) }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        // Diff card
        val diffCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(NC.SURFACE, NC.BORDER, dp(10))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }

        // Card header with file path + stat badges
        val cardHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(NC.SURFACE_VAR); setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        val pathTv = TextView(this).apply {
            text = "app/src/main/.../MainActivity.kt"; textSize = 11f
            setTextColor(NC.ON_SURF_VAR); typeface = Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        val removedBadge = textBadge("-1", Color.parseColor("#3d1212"), NC.ERROR)
        val addedBadge = textBadge("+2", Color.parseColor("#0d2a2a"), NC.SECONDARY)
        removedBadge.layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).apply { rightMargin = dp(6) }
        cardHeader.addView(pathTv); cardHeader.addView(removedBadge); cardHeader.addView(addedBadge)
        diffCard.addView(cardHeader)

        // Diff table (horizontal scroll)
        val hScroll = HorizontalScrollView(this).apply {
            setBackgroundColor(NC.LOGBG)
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        val table = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(MATCH, WRAP)
        }

        // Diff rows: list of (type, oldLine, newLine, code)
        // type: 0=context, 1=removed, 2=added
        data class DiffRow(val type: Int, val old: String, val new_: String, val code: String)
        val rows = listOf(
            DiffRow(0, "11", "11", "class MainActivity : AppCompatActivity() {"),
            DiffRow(1, "12", "",   "    val oldState = \"loading\""),
            DiffRow(2, "",   "12", "    val newState = \"ready\""),
            DiffRow(2, "",   "13", "    val isReady = true"),
            DiffRow(0, "13", "14", "    override fun onCreate() {")
        )

        for (row in rows) {
            val (type, old, new_, code) = row
            val bg = when (type) {
                1 -> Color.argb(50, 147, 0, 10)
                2 -> Color.argb(50, 3, 181, 211)
                else -> Color.TRANSPARENT
            }
            val marker = when (type) { 1 -> "-"; 2 -> "+"; else -> " " }
            val markerColor = when (type) { 1 -> NC.ERROR; 2 -> NC.SECONDARY; else -> NC.OUTLINE }
            val codeColor = when (type) { 1 -> NC.ON_SURF_VAR; else -> NC.ON_SURFACE }

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(bg); setPadding(0, dp(3), dp(12), dp(3))
            }
            val oldLn = lineNumCell(old); val newLn = lineNumCell(new_)
            val markerTv = TextView(this).apply {
                text = marker; textSize = 12f; setTextColor(markerColor); typeface = Typeface.MONOSPACE
                gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams(dp(24), WRAP)
            }
            val codeTv = TextView(this).apply {
                text = code; textSize = 12f; setTextColor(codeColor); typeface = Typeface.MONOSPACE
                if (type == 1) paintFlags = paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
            }
            row.addView(oldLn); row.addView(newLn); row.addView(markerTv); row.addView(codeTv)
            table.addView(row)
        }
        hScroll.addView(table)
        diffCard.addView(hScroll)
        content.addView(diffCard)
        scroll.addView(content)
        root.addView(scroll)

        // Bottom action bar
        val bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setBackgroundColor(NC.SURFACE_VAR); setPadding(dp(16), dp(12), dp(16), dp(16))
        }
        val discardBtn = outlineBtn("Discard")
        val commitBtn = primaryBtn("Commit Changes")
        discardBtn.layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).apply { rightMargin = dp(12) }
        bottomBar.addView(discardBtn); bottomBar.addView(commitBtn)
        root.addView(bottomBar)


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

    private fun lineNumCell(num: String) = TextView(this).apply {
        text = num; textSize = 11f; setTextColor(NC.OUTLINE); typeface = Typeface.MONOSPACE
        gravity = Gravity.END; setPadding(dp(6), 0, dp(6), 0)
        layoutParams = LinearLayout.LayoutParams(dp(36), WRAP)
    }
    private fun textBadge(text: String, bg: Int, fg: Int) = TextView(this).apply { this.text = text; textSize = 10f; setTextColor(fg); typeface = Typeface.MONOSPACE; background = roundedBg(bg, fg, dp(4)); setPadding(dp(6), dp(2), dp(6), dp(2)) }
    private fun primaryBtn(t: String) = TextView(this).apply { text = t; textSize = 13f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; background = roundedBg(NC.PRIMARY_CON, NC.PRIMARY_CON, dp(20)); setPadding(dp(20), dp(10), dp(20), dp(10)) }
    private fun outlineBtn(t: String) = TextView(this).apply { text = t; textSize = 13f; setTextColor(NC.ON_SURFACE); gravity = Gravity.CENTER; background = roundedBg(NC.SURFACE, NC.BORDER, dp(20)); setPadding(dp(20), dp(10), dp(20), dp(10)) }
    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()
    private fun roundedBg(fill: Int, stroke: Int, r: Int) = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; setColor(fill); setStroke(dp(1), stroke); cornerRadius = r.toFloat() }
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
}
