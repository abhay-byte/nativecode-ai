package com.ivarna.nativecode

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import java.io.File

/**
 * Page 4 variant: Terminal with collapsible project sidebar drawer
 * HTML prototype: terminal_with_project_sidebar/code.html
 *
 * Also serves as Page 9 (Terminal AI Agent): terminal_ai_agent/code.html
 * Both share a TerminalView + keyboard toolbar + sliding file drawer.
 */
class TerminalWithSidebarActivity : AppCompatActivity() {

    private lateinit var terminalView: TerminalView
    private var terminalSession: TerminalSession? = null
    private lateinit var drawerLayout: LinearLayout
    private var drawerOpen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = NC.BG
        window.navigationBarColor = NC.BG

        val root = FrameLayout(this).apply {
            setBackgroundColor(NC.BG)
            layoutParams = ViewGroup.LayoutParams(MATCH, MATCH)
        }

        // ── Drawer (slide-in from left) ──────────────────────────────────────
        drawerLayout = buildDrawer()
        drawerLayout.visibility = View.GONE

        // ── Main column ──────────────────────────────────────────────────────
        val mainCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
        }

        // Top bar
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#15121b"))
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        val menuBtn = iconTextBtn("\u2630") { toggleDrawer() }
        val backBtn = iconTextBtn("←") { finish() }
        val titleTv = TextView(this).apply {
            text = "Terminal"
            textSize = 16f; setTextColor(NC.ON_SURFACE); typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(8), 0, 0, 0)
        }
        val agentBadge = TextView(this).apply {
            text = "Agent: Bash ▾"
            textSize = 11f; setTextColor(NC.SECONDARY); typeface = Typeface.MONOSPACE
            background = roundedBg(NC.SURFACE_HIGH, NC.BORDER, dp(4))
            setPadding(dp(8), dp(4), dp(8), dp(4))
            layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).apply { leftMargin = dp(8) }
        }
        val topSpacer = View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) }
        val moreBtn = iconTextBtn("\u22EE") {}
        topBar.addView(menuBtn); topBar.addView(backBtn); topBar.addView(titleTv)
        topBar.addView(agentBadge); topBar.addView(topSpacer); topBar.addView(moreBtn)
        mainCol.addView(topBar)

        // Terminal view (fills flex-1)
        val termFrame = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f)
        }
        terminalView = TerminalView(this, null).apply {
            isFocusable = true; isFocusableInTouchMode = true
        }
        termFrame.addView(terminalView)
        mainCol.addView(termFrame)

        // Keyboard shortcut toolbar
        mainCol.addView(buildKeyboardToolbar())

        // Input bar (attach file)
        mainCol.addView(buildInputBar())

        root.addView(mainCol)
        root.addView(drawerLayout)


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
        initTerminal()
    }

    private fun buildDrawer(): LinearLayout {
        val drawer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1d1a24"))
            layoutParams = FrameLayout.LayoutParams(dp(280), MATCH).apply {
                gravity = Gravity.START
            }
            elevation = dp(8).toFloat()
        }

        // Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#15121b"))
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        val title = TextView(this).apply {
            text = "Project Directory"; textSize = 16f; setTextColor(NC.ON_SURFACE)
            typeface = Typeface.DEFAULT_BOLD; layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        val closeBtn = iconTextBtn("✕") { toggleDrawer() }
        header.addView(title); header.addView(closeBtn)
        drawer.addView(header)

        // File tree
        val scroll = ScrollView(this).apply { layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f) }
        val tree = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        for ((icon, name, indent, color) in listOf(
            listOf("\uD83D\uDCC1", "app", 0, NC.SECONDARY),
            listOf("\uD83D\uDCC1", "gradle", 0, NC.SECONDARY),
            listOf("\uD83D\uDCDD", "build.gradle.kts", 16, NC.ON_SURF_VAR),
            listOf("\uD83D\uDCDD", "settings.gradle.kts", 16, NC.ON_SURF_VAR),
            listOf("\uD83D\uDCDD", "gradle.properties", 16, NC.ON_SURF_VAR)
        )) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(indent as Int), dp(8), dp(8), dp(8))
            }
            val iconTv = TextView(this).apply { text = icon as String; textSize = 16f; setPadding(0, 0, dp(10), 0) }
            val nameTv = TextView(this).apply { text = name as String; textSize = 13f; setTextColor(color as Int); typeface = Typeface.MONOSPACE }
            row.addView(iconTv); row.addView(nameTv)
            tree.addView(row)
        }
        scroll.addView(tree)
        drawer.addView(scroll)
        return drawer
    }

    private fun buildKeyboardToolbar(): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(NC.SURFACE_HIGH)
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }
        val scroll = HorizontalScrollView(this).apply { layoutParams = LinearLayout.LayoutParams(MATCH, WRAP) }
        val inner = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        for (key in listOf("Tab", "Ctrl", "Alt", "Esc", "/", "|", "~", "-")) {
            val btn = TextView(this).apply {
                text = key; textSize = 11f; typeface = Typeface.MONOSPACE
                setTextColor(NC.ON_SURF_VAR)
                background = roundedBg(NC.SURFACE, NC.BORDER, dp(4))
                setPadding(dp(10), dp(6), dp(10), dp(6))
                layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).apply { rightMargin = dp(6) }
                setOnClickListener {
                    val text = when (key) {
                        "Tab" -> "\t"; "Esc" -> "\u001b"
                        else -> key
                    }
                    terminalSession?.write(text)
                }
            }
            inner.addView(btn)
        }
        scroll.addView(inner); bar.addView(scroll)
        return bar
    }

    private fun buildInputBar(): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(NC.BG)
            setPadding(dp(12), dp(10), dp(12), dp(16))
            gravity = Gravity.CENTER_VERTICAL
        }
        val attachBtn = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            background = roundedBg(NC.SURFACE, NC.BORDER_VAR, dp(8))
            background = roundedBg(NC.SURFACE, NC.BORDER_VAR, dp(8))
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        val attachTv = TextView(this).apply {
            text = "\uD83D\uDCCE  Add context (Images/Files)"
            textSize = 12f; setTextColor(NC.ON_SURF_VAR); typeface = Typeface.MONOSPACE
        }
        attachBtn.addView(attachTv)
        bar.addView(attachBtn)
        return bar
    }

    private fun toggleDrawer() {
        drawerOpen = !drawerOpen
        drawerLayout.visibility = if (drawerOpen) View.VISIBLE else View.GONE
    }

    private fun initTerminal() {
        terminalView.setTextSize(40)
        val nld = applicationInfo.nativeLibraryDir
        val shell = File(nld, "libbash.so").absolutePath
        val cwd = File(filesDir, "home").absolutePath
        val envMap = HashMap(System.getenv())
        envMap["PATH"] = "$nld:/data/data/com.ivarna.nativecode/files/usr/bin:/system/bin"
        envMap["PROOT_LOADER"] = File(nld, "libloader.so").absolutePath
        envMap["HOME"] = "/data/data/com.ivarna.nativecode/files/home"
        envMap["TERM"] = "xterm-256color"
        envMap["PREFIX"] = "/data/data/com.ivarna.nativecode/files/usr"
        envMap["LD_LIBRARY_PATH"] = "/data/data/com.ivarna.nativecode/files/usr/lib"
        envMap["LD_PRELOAD"] = "/data/data/com.ivarna.nativecode/files/usr/lib/libtermux-exec.so"
        envMap["TERMUX_APP__PACKAGE_NAME"] = "com.ivarna.nativecode"
        envMap["TERMUX__PREFIX"] = "/data/data/com.ivarna.nativecode/files/usr"
        envMap["TERMUX__HOME"] = "/data/data/com.ivarna.nativecode/files/home"
        val env = envMap.map { "${it.key}=${it.value}" }.toTypedArray()

        val vc = object : TerminalViewClient {
            override fun onScale(s: Float) = s
            override fun onSingleTapUp(e: MotionEvent) {
                terminalView.requestFocus()
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showSoftInput(terminalView, InputMethodManager.SHOW_IMPLICIT)
            }
            override fun shouldBackButtonBeMappedToEscape() = false
            override fun shouldEnforceCharBasedInput() = false
            override fun shouldUseCtrlSpaceWorkaround() = false
            override fun isTerminalViewSelected() = true
            override fun copyModeChanged(a: Boolean) {}
            override fun onKeyDown(k: Int, e: android.view.KeyEvent, s: TerminalSession) = false
            override fun onKeyUp(k: Int, e: android.view.KeyEvent) = false
            override fun onLongPress(e: MotionEvent) = false
            override fun readControlKey() = false; override fun readAltKey() = false
            override fun readShiftKey() = false; override fun readFnKey() = false
            override fun onCodePoint(c: Int, ctrl: Boolean, s: TerminalSession) = false
            override fun onEmulatorSet() {}
            override fun logError(t: String, m: String) {}; override fun logWarn(t: String, m: String) {}
            override fun logInfo(t: String, m: String) {}; override fun logDebug(t: String, m: String) {}
            override fun logVerbose(t: String, m: String) {}
            override fun logStackTraceWithMessage(t: String, m: String, e: Exception) {}
            override fun logStackTrace(t: String, e: Exception) {}
        }
        val sc = object : TerminalSessionClient {
            override fun onTextChanged(s: TerminalSession) { terminalView.onScreenUpdated() }
            override fun onTitleChanged(s: TerminalSession) {}
            override fun onSessionFinished(s: TerminalSession) {}
            override fun onCopyTextToClipboard(s: TerminalSession, t: String) {}
            override fun onPasteTextFromClipboard(s: TerminalSession) {}
            override fun onBell(s: TerminalSession) {}
            override fun onColorsChanged(s: TerminalSession) {}
            override fun onTerminalCursorStateChange(b: Boolean) {}
            override fun getTerminalCursorStyle(): Int? = 1
            override fun logError(t: String, m: String) {}; override fun logWarn(t: String, m: String) {}
            override fun logInfo(t: String, m: String) {}; override fun logDebug(t: String, m: String) {}
            override fun logVerbose(t: String, m: String) {}
            override fun logStackTraceWithMessage(t: String, m: String, e: java.lang.Exception) {}
            override fun logStackTrace(t: String, e: java.lang.Exception) {}
        }
        terminalView.setTerminalViewClient(vc)
        terminalSession = TerminalSession(shell, cwd, arrayOf(shell, "-l"), env, 10000, sc)
        terminalView.attachSession(terminalSession)
        terminalView.postDelayed({
            terminalView.requestFocus()
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                .showSoftInput(terminalView, InputMethodManager.SHOW_IMPLICIT)
        }, 800)
    }

    override fun onDestroy() {
        super.onDestroy()
        terminalSession?.finishIfRunning()
    }

    private fun iconTextBtn(icon: String, onClick: () -> Unit) = TextView(this).apply {
        text = icon; textSize = 18f; setTextColor(NC.ON_SURF_VAR)
        setPadding(dp(8), dp(4), dp(8), dp(4))
        setOnClickListener { onClick() }
    }
    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()
    private fun roundedBg(fill: Int, stroke: Int, r: Int) = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; setColor(fill); setStroke(dp(1), stroke); cornerRadius = r.toFloat() }
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
}
