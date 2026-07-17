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
 * Page 6/7: Git Operations Hub + GitHub CLI
 * HTML prototypes: git_operations/code.html & github_cli_operations/code.html
 */
class GitOperationsActivity : AppCompatActivity() {

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
        root.addView(topBar("Git Operations"))

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f)
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
        }

        // Page header
        val pageTitle = TextView(this).apply {
            text = "Git Operations"
            textSize = 22f; setTextColor(NC.PRIMARY); typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(4))
        }
        val pageSub = TextView(this).apply {
            text = "Manage repository connections and branches."
            textSize = 14f; setTextColor(NC.ON_SURF_VAR); setPadding(0, 0, 0, dp(20))
        }
        content.addView(pageTitle); content.addView(pageSub)

        // Action buttons row
        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(16))
        }
        val cloneBtn = outlineButton("⬇ Clone Repository")
        cloneBtn.layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).apply { rightMargin = dp(8) }
        val pushBtn = primaryBtn("↑ Push")
        pushBtn.layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        actionRow.addView(cloneBtn); actionRow.addView(pushBtn)
        content.addView(actionRow)

        // GitHub CLI Auth card (matches github_cli_operations)
        val authCard = buildAuthCard()
        content.addView(authCard)
        content.addView(spacer(12))

        // Branches card
        val branchCard = buildBranchCard()
        content.addView(branchCard)
        content.addView(spacer(12))

        // Git auth inputs card (matches git_operations)
        val gitAuthCard = buildGitAuthInputCard()
        content.addView(gitAuthCard)

        scrollView.addView(content)
        root.addView(scrollView)

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

    private fun buildAuthCard(): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(NC.SURFACE, NC.BORDER, dp(12))
            setPadding(dp(16), dp(16), dp(16), dp(16))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(8))
        }
        val icon = TextView(this).apply { text = "\uD83D\uDDA5\uFE0F "; textSize = 16f; setTextColor(NC.SECONDARY) }
        val title = TextView(this).apply { text = "Terminal Authentication"; textSize = 16f; setTextColor(NC.ON_SURFACE); typeface = Typeface.DEFAULT_BOLD }
        header.addView(icon); header.addView(title); card.addView(header)

        val sub = TextView(this).apply {
            text = "Authenticate using the GitHub CLI interactive flow."
            textSize = 13f; setTextColor(NC.ON_SURF_VAR); setPadding(0, 0, 0, dp(12))
        }
        card.addView(sub)

        // Terminal block
        val termBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(NC.LOGBG)
            background = roundedBg(NC.LOGBG, NC.BORDER, dp(8))
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        // Window dots
        val dotsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(8))
        }
        for (col in listOf(NC.ERROR, NC.TERTIARY, NC.SECONDARY)) {
            val dot = LinearLayout(this).apply {
                background = circleDrawable(col)
                layoutParams = LinearLayout.LayoutParams(dp(10), dp(10)).apply { rightMargin = dp(6) }
            }
            dotsRow.addView(dot)
        }
        termBlock.addView(dotsRow)
        for ((prompt, line) in listOf(
            "\$ " to "gh auth login",
            "? " to "What account do you want to log into? GitHub.com",
            "? " to "Preferred protocol for Git operations? HTTPS",
            "! " to "First copy your one-time code: ABCD-1234",
            "- " to "Press Enter to open github.com in your browser..."
        )) {
            val tv = TextView(this).apply {
                text = "$prompt$line"
                textSize = 12f; typeface = Typeface.MONOSPACE
                setTextColor(if (prompt == "! ") NC.PRIMARY else NC.ON_SURF_VAR)
            }
            termBlock.addView(tv)
        }
        card.addView(termBlock)
        card.addView(spacer(12))

        val codeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        val codeLbl = TextView(this).apply { text = "Code: "; textSize = 13f; setTextColor(NC.ON_SURF_VAR); typeface = Typeface.MONOSPACE }
        val codeVal = textBadge("ABCD-1234", NC.SURFACE_HIGH, NC.ON_SURFACE)
        val spacer2 = android.view.View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) }
        val openBtn = primaryBtn("Open Browser")
        openBtn.layoutParams = LinearLayout.LayoutParams(WRAP, WRAP)
        codeRow.addView(codeLbl); codeRow.addView(codeVal); codeRow.addView(spacer2); codeRow.addView(openBtn)
        card.addView(codeRow)
        return card
    }

    private fun buildBranchCard(): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(NC.SURFACE, NC.BORDER, dp(12))
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(12))
        }
        val icon = TextView(this).apply { text = "\uD83C\uDF3F "; textSize = 16f; setTextColor(NC.SECONDARY) }
        val title = TextView(this).apply { text = "Active Branches"; textSize = 16f; setTextColor(NC.ON_SURFACE); typeface = Typeface.DEFAULT_BOLD; layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f) }
        val fetchBtn = TextView(this).apply { text = "↺ git fetch"; textSize = 11f; setTextColor(NC.ON_SURF_VAR); typeface = Typeface.MONOSPACE; background = roundedBg(NC.SURFACE_HIGH, NC.BORDER, dp(4)); setPadding(dp(8), dp(4), dp(8), dp(4)) }
        headerRow.addView(icon); headerRow.addView(title); headerRow.addView(fetchBtn); card.addView(headerRow)

        val treeBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(NC.LOGBG, NC.BORDER, dp(8))
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        for ((prefix, branch, isHead) in listOf(
            Triple("├── ", "main", false),
            Triple("└── ", "feature/auth", true),
            Triple("    ├── ", "fix/login-bug", false)
        )) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(4), 0, dp(4))
            }
            val preTv = TextView(this).apply { text = prefix; textSize = 12f; setTextColor(NC.OUTLINE); typeface = Typeface.MONOSPACE }
            val nameTv = TextView(this).apply {
                text = branch; textSize = 12f; typeface = Typeface.MONOSPACE
                setTextColor(if (isHead) NC.PRIMARY else NC.ON_SURF_VAR)
                if (isHead) typeface = Typeface.DEFAULT_BOLD
            }
            row.addView(preTv); row.addView(nameTv)
            if (isHead) {
                row.addView(android.view.View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })
                val headBadge = textBadge("HEAD", Color.argb(51, 210, 187, 255), NC.PRIMARY)
                row.addView(headBadge)
            }
            treeBlock.addView(row)
        }
        card.addView(treeBlock)
        return card
    }

    private fun buildGitAuthInputCard(): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(NC.SURFACE, NC.BORDER, dp(12))
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, dp(12)) }
        val lockIcon = TextView(this).apply { text = "\uD83D\uDD10 "; textSize = 16f; setTextColor(NC.SECONDARY) }
        val title = TextView(this).apply { text = "Authenticate with GitHub"; textSize = 16f; setTextColor(NC.ON_SURFACE); typeface = Typeface.DEFAULT_BOLD }
        header.addView(lockIcon); header.addView(title); card.addView(header)

        val sub = TextView(this).apply { text = "Provide credentials to push or pull private repositories securely."; textSize = 13f; setTextColor(NC.ON_SURF_VAR); setPadding(0, 0, 0, dp(16)) }
        card.addView(sub)

        card.addView(inputField("GitHub Username / Token", "ghp_xxxxxxxxxxxxxxxxxxxx"))
        card.addView(spacer(12))
        card.addView(inputField("Repository URL or Name", "owner/repo"))
        card.addView(spacer(16))

        val authBtn = primaryBtn("Authenticate")
        authBtn.layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).apply { gravity = Gravity.END }
        card.addView(authBtn)
        return card
    }

    private fun inputField(label: String, hint: String): LinearLayout {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val lbl = TextView(this).apply { text = label; textSize = 10f; setTextColor(NC.ON_SURF_VAR); typeface = Typeface.MONOSPACE; letterSpacing = 0.1f; setPadding(0, 0, 0, dp(4)) }
        val et = EditText(this).apply {
            this.hint = hint; setHintTextColor(NC.OUTLINE); textSize = 13f; setTextColor(NC.ON_SURFACE); typeface = Typeface.MONOSPACE
            setBackgroundColor(NC.SURFACE_HIGH); setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; setColor(NC.SURFACE_HIGH); setStroke(dp(1), NC.BORDER_VAR); cornerRadius = dp(6).toFloat() }
        }
        col.addView(lbl); col.addView(et); return col
    }

    private fun topBar(title: String): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#15121b"))
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        val back = TextView(this).apply { text = "← "; textSize = 18f; setTextColor(NC.ON_SURF_VAR); setOnClickListener { finish() } }
        val titleTv = TextView(this).apply { text = title; textSize = 18f; setTextColor(NC.ON_SURFACE); typeface = Typeface.DEFAULT_BOLD }
        bar.addView(back); bar.addView(titleTv); return bar
    }

    private fun primaryBtn(text: String) = TextView(this).apply {
        this.text = text; textSize = 13f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        background = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; setColor(NC.PRIMARY_CON); cornerRadius = dp(20).toFloat() }
        setPadding(dp(16), dp(10), dp(16), dp(10))
    }
    private fun outlineButton(text: String) = TextView(this).apply {
        this.text = text; textSize = 13f; setTextColor(NC.ON_SURFACE); gravity = Gravity.CENTER
        background = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; setColor(NC.SURFACE_VAR); setStroke(dp(1), NC.BORDER); cornerRadius = dp(20).toFloat() }
        setPadding(dp(16), dp(10), dp(16), dp(10))
    }
    private fun textBadge(text: String, bg: Int, fg: Int) = TextView(this).apply { this.text = text; textSize = 10f; setTextColor(fg); typeface = Typeface.MONOSPACE; background = roundedBg(bg, fg, dp(4)); setPadding(dp(6), dp(2), dp(6), dp(2)) }
    private fun spacer(dp_: Int) = android.view.View(this).apply { layoutParams = LinearLayout.LayoutParams(MATCH, dp(dp_)) }
    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()
    private fun roundedBg(fill: Int, stroke: Int, r: Int) = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; setColor(fill); setStroke(dp(1), stroke); cornerRadius = r.toFloat() }
    private fun circleDrawable(color: Int): android.graphics.drawable.ShapeDrawable = android.graphics.drawable.ShapeDrawable(android.graphics.drawable.shapes.OvalShape()).apply { paint.color = color }
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
}
