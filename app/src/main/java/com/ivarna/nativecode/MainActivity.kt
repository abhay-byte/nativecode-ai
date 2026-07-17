package com.ivarna.nativecode

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.Menu
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.setPadding
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    // ── Views ──────────────────────────────────────────────────────────────────
    private lateinit var rootLayout: LinearLayout
    private lateinit var contentFrame: FrameLayout
    private lateinit var bottomNavigation: BottomNavigationView

    // Persistent Header (Unified across all pages)
    private lateinit var unifiedHeader: LinearLayout

    private lateinit var drawerLayout: androidx.drawerlayout.widget.DrawerLayout
    private lateinit var sidebarLayout: LinearLayout
    private lateinit var sidebarScrollView: ScrollView
    private lateinit var sidebarListContainer: LinearLayout

    private lateinit var menuBtn: TextView
    private lateinit var addTerminalBtn: TextView
    private lateinit var backBtn: TextView

    private val sessionsList = ArrayList<TerminalSession>()
    private var activeSessionIndex = -1

    private lateinit var viewClient: TerminalViewClient
    private lateinit var sessionClient: TerminalSessionClient

    // Layout panels (pages)
    private lateinit var homeScrollView: ScrollView
    private lateinit var homeLayout: LinearLayout

    private lateinit var fileExplorerScrollView: ScrollView
    private lateinit var fileExplorerLayout: LinearLayout

    private lateinit var terminalWorkspaceLayout: LinearLayout
    private lateinit var terminalViewContainer: FrameLayout
    private lateinit var terminalView: TerminalView
    private var terminalSession: TerminalSession? = null

    private lateinit var gitOperationsScrollView: ScrollView
    private lateinit var gitOperationsLayout: LinearLayout

    private lateinit var settingsHubScrollView: ScrollView
    private lateinit var settingsHubLayout: LinearLayout

    // Sub-pages (pushed on stack)
    private lateinit var fileViewerScrollView: ScrollView
    private lateinit var diffViewerScrollView: ScrollView
    private lateinit var scriptsScrollView: ScrollView
    private lateinit var scriptsLayout: LinearLayout

    private lateinit var scriptInstallLayout: LinearLayout
    private lateinit var scriptInstallViewContainer: FrameLayout
    private lateinit var scriptInstallTerminalView: TerminalView
    private var scriptInstallSession: TerminalSession? = null

    // Home dashboard widgets
    private lateinit var homeStatusDot: View
    private lateinit var homeStatusLabel: TextView
    private lateinit var homeContainerLabel: TextView
    private lateinit var startGuiBtn: TextView
    private lateinit var stopGuiBtn: TextView
    private lateinit var openX11Btn: TextView

    private val executor = Executors.newCachedThreadPool()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val ID_HOME     = 1
    private val ID_FILES    = 2
    private val ID_TERMINAL = 3
    private val ID_GIT      = 4
    private val ID_SETTINGS = 5
    private val ID_SCRIPTS  = 6
    private val ID_SCRIPT_INSTALL = 7

    private var isScriptRunning = false

    // History tracking for back button support
    private val pageStack = java.util.Stack<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = NC.BG
        window.navigationBarColor = NC.BG

        val setupCompleteFile = File(filesDir, "setup_complete")
        if (!setupCompleteFile.exists()) {
            val intent = Intent(this, OnboardingActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            return
        }

        buildRootLayout()
        setContentView(drawerLayout)

        // Apply Insets
        ViewCompat.setOnApplyWindowInsetsListener(drawerLayout) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            unifiedHeader.setPadding(dp(16), bars.top + dp(12), dp(16), dp(12))
            
            // Adjust sidebar layout padding to respect notification/status bar
            sidebarLayout.setPadding(dp(16), bars.top + dp(16), dp(16), dp(16))
            
            if (bottomNavigation.visibility == View.VISIBLE) {
                bottomNavigation.setPadding(0, 0, 0, bars.bottom)
                contentFrame.setPadding(0, 0, 0, 0)
            } else {
                bottomNavigation.setPadding(0, 0, 0, 0)
                val bottomPadding = if (ime.bottom > 0) ime.bottom else bars.bottom
                contentFrame.setPadding(0, 0, 0, bottomPadding)
            }
            insets
        }
        ViewCompat.requestApplyInsets(drawerLayout)

        bottomNavigation.setOnItemSelectedListener { item ->
            val pageId = item.itemId
            if (pageStack.isEmpty() || pageStack.peek() != pageId) {
                pageStack.push(pageId)
            }
            navigateToPage(pageId)
            true
        }

        deployScripts()
        showHome()
        onSetupComplete()
    }

    // ── Unified Navigation & Switcher ────────────────────────────────────────

    private fun navigateToPage(id: Int) {
        // Hide all screens
        homeScrollView.visibility = View.GONE
        fileExplorerScrollView.visibility = View.GONE
        terminalWorkspaceLayout.visibility = View.GONE
        gitOperationsScrollView.visibility = View.GONE
        settingsHubScrollView.visibility = View.GONE
        fileViewerScrollView.visibility = View.GONE
        diffViewerScrollView.visibility = View.GONE
        if (::scriptsScrollView.isInitialized) {
            scriptsScrollView.visibility = View.GONE
        }
        if (::scriptInstallLayout.isInitialized) {
            scriptInstallLayout.visibility = View.GONE
        }

        // Make top bar visible by default
        unifiedHeader.visibility = View.VISIBLE

        if (id == ID_TERMINAL) {
            if (::backBtn.isInitialized) {
                backBtn.visibility = if (isScriptRunning) View.GONE else View.VISIBLE
            }
            if (::menuBtn.isInitialized) menuBtn.visibility = View.VISIBLE
            if (::addTerminalBtn.isInitialized) addTerminalBtn.visibility = View.VISIBLE
            bottomNavigation.menu.findItem(bottomNavigation.selectedItemId)?.isChecked = false
            bottomNavigation.visibility = View.GONE
            if (::drawerLayout.isInitialized) drawerLayout.setDrawerLockMode(androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_UNLOCKED)
        } else if (id == ID_SCRIPT_INSTALL) {
            if (::backBtn.isInitialized) {
                backBtn.visibility = if (isScriptRunning) View.GONE else View.VISIBLE
            }
            if (::menuBtn.isInitialized) menuBtn.visibility = View.GONE
            if (::addTerminalBtn.isInitialized) addTerminalBtn.visibility = View.GONE
            bottomNavigation.menu.findItem(bottomNavigation.selectedItemId)?.isChecked = false
            bottomNavigation.visibility = View.GONE
            if (::drawerLayout.isInitialized) drawerLayout.setDrawerLockMode(androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        } else {
            if (::backBtn.isInitialized) backBtn.visibility = View.GONE
            if (::menuBtn.isInitialized) menuBtn.visibility = View.GONE
            if (::addTerminalBtn.isInitialized) addTerminalBtn.visibility = View.GONE
            bottomNavigation.visibility = View.VISIBLE
            if (::drawerLayout.isInitialized) drawerLayout.setDrawerLockMode(androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        }
        ViewCompat.requestApplyInsets(drawerLayout)

        when (id) {
            ID_HOME -> {
                homeScrollView.visibility = View.VISIBLE
            }
            ID_FILES -> {
                fileExplorerScrollView.visibility = View.VISIBLE
            }
            ID_TERMINAL -> {
                terminalWorkspaceLayout.visibility = View.VISIBLE
                terminalView.requestFocus()
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(terminalView, InputMethodManager.SHOW_IMPLICIT)
            }
            ID_GIT -> {
                gitOperationsScrollView.visibility = View.VISIBLE
            }
            ID_SETTINGS -> {
                settingsHubScrollView.visibility = View.VISIBLE
            }
            ID_SCRIPTS -> {
                if (::scriptsScrollView.isInitialized) {
                    scriptsScrollView.visibility = View.VISIBLE
                }
            }
            ID_SCRIPT_INSTALL -> {
                if (::scriptInstallLayout.isInitialized) {
                    scriptInstallLayout.visibility = View.VISIBLE
                }
            }
        }
    }

    override fun onBackPressed() {
        if (fileViewerScrollView.visibility == View.VISIBLE || diffViewerScrollView.visibility == View.VISIBLE) {
            // Pop back to file explorer / git operations respectively
            val prevPage = if (fileViewerScrollView.visibility == View.VISIBLE) ID_FILES else ID_GIT
            navigateToPage(prevPage)
            bottomNavigation.selectedItemId = prevPage
            return
        }

        if (::scriptInstallLayout.isInitialized && scriptInstallLayout.visibility == View.VISIBLE) {
            if (isScriptRunning) {
                // Ignore back key during active running script
                return
            } else {
                navigateToPage(ID_SCRIPTS)
                return
            }
        }

        if (::scriptsScrollView.isInitialized && scriptsScrollView.visibility == View.VISIBLE) {
            navigateToPage(ID_SETTINGS)
            bottomNavigation.selectedItemId = ID_SETTINGS
            return
        }

        if (pageStack.size > 1) {
            pageStack.pop() // remove current
            val prevPage = pageStack.peek()
            navigateToPage(prevPage)
            if (prevPage != ID_TERMINAL) {
                bottomNavigation.selectedItemId = prevPage
            }
        } else {
            super.onBackPressed()
        }
    }

    // ── Layout construction ───────────────────────────────────────────────────

    private fun buildRootLayout() {
        drawerLayout = androidx.drawerlayout.widget.DrawerLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(MATCH, MATCH)
            setBackgroundColor(NC.BG)
        }

        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(NC.BG)
            layoutParams = ViewGroup.LayoutParams(MATCH, MATCH)
        }
        drawerLayout.addView(rootLayout)

        // Sidebar container
        sidebarLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#15121b"))
            val params = androidx.drawerlayout.widget.DrawerLayout.LayoutParams(dp(260), MATCH).apply {
                gravity = Gravity.START
            }
            layoutParams = params
            setPadding(dp(16), dp(24), dp(16), dp(16))
        }

        // Add header to sidebar
        val sidebarTitle = TextView(this).apply {
            text = "Active Terminals"
            textSize = 18f
            setTextColor(NC.PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(16))
        }
        sidebarLayout.addView(sidebarTitle)

        // Scroll view for terminal list
        sidebarScrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f)
        }
        sidebarListContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(MATCH, WRAP)
        }
        sidebarScrollView.addView(sidebarListContainer)
        sidebarLayout.addView(sidebarScrollView)

        drawerLayout.addView(sidebarLayout)

        // ── Unified Top Bar ──────────────────────────────────────────────────
        unifiedHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#15121b"))
            setPadding(dp(16), dp(12), dp(16), dp(12))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        val logoView = ImageView(this).apply {
            setImageResource(R.mipmap.logo)
            layoutParams = LinearLayout.LayoutParams(dp(32), dp(32))
        }
        backBtn = TextView(this).apply {
            text = " ◀ "
            textSize = 20f
            setTextColor(NC.ON_SURFACE)
            setPadding(dp(8), 0, dp(8), 0)
            setOnClickListener {
                onBackPressed()
            }
        }
        menuBtn = TextView(this).apply {
            text = " ☰ "
            textSize = 20f
            setTextColor(NC.ON_SURFACE)
            setPadding(dp(8), 0, dp(8), 0)
            setOnClickListener {
                if (drawerLayout.isDrawerOpen(sidebarLayout)) {
                    drawerLayout.closeDrawer(sidebarLayout)
                } else {
                    drawerLayout.openDrawer(sidebarLayout)
                }
            }
        }
        val spacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        }
        val displayBtn = TextView(this).apply {
            text = "Display"
            textSize = 13f
            setTextColor(NC.SECONDARY)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = roundedBg(NC.SURFACE_HIGH, NC.BORDER, dp(6))
            setPadding(dp(12), dp(6), dp(12), dp(6))
            layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).apply {
                rightMargin = dp(8)
            }
            setOnClickListener {
                val intent = Intent(this@MainActivity, com.termux.x11.MainActivity::class.java)
                startActivity(intent)
            }
        }
        val terminalBtn = TextView(this).apply {
            text = "Terminal"
            textSize = 13f
            setTextColor(NC.PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = roundedBg(NC.SURFACE_HIGH, NC.BORDER, dp(6))
            setPadding(dp(12), dp(6), dp(12), dp(6))
            layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).apply {
                rightMargin = dp(8)
            }
            setOnClickListener {
                if (pageStack.isEmpty() || pageStack.peek() != ID_TERMINAL) {
                    pageStack.push(ID_TERMINAL)
                }
                navigateToPage(ID_TERMINAL)
            }
        }
        addTerminalBtn = TextView(this).apply {
            text = "＋"
            textSize = 15f
            setTextColor(NC.SECONDARY)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = roundedBg(NC.SURFACE_HIGH, NC.BORDER, dp(6))
            setPadding(dp(12), dp(6), dp(12), dp(6))
            layoutParams = LinearLayout.LayoutParams(WRAP, WRAP)
            setOnClickListener {
                createNewTerminalSession()
                if (pageStack.isEmpty() || pageStack.peek() != ID_TERMINAL) {
                    pageStack.push(ID_TERMINAL)
                }
                navigateToPage(ID_TERMINAL)
            }
        }

        unifiedHeader.addView(logoView)
        unifiedHeader.addView(backBtn)
        unifiedHeader.addView(menuBtn)
        unifiedHeader.addView(spacer)
        unifiedHeader.addView(displayBtn)
        unifiedHeader.addView(terminalBtn)
        unifiedHeader.addView(addTerminalBtn)
        rootLayout.addView(unifiedHeader)

        contentFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f)
        }

        bottomNavigation = BottomNavigationView(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
            setBackgroundColor(Color.parseColor("#1d1a24"))
            itemIconTintList = null
            menu.add(Menu.NONE, ID_HOME,     Menu.NONE, "Home").setIcon(android.R.drawable.ic_menu_info_details)
            menu.add(Menu.NONE, ID_FILES,    Menu.NONE, "Files").setIcon(android.R.drawable.ic_menu_agenda)
            menu.add(Menu.NONE, ID_GIT,      Menu.NONE, "Git").setIcon(android.R.drawable.ic_menu_share)
            menu.add(Menu.NONE, ID_SETTINGS, Menu.NONE, "Settings").setIcon(android.R.drawable.ic_menu_preferences)
        }

        rootLayout.addView(contentFrame)
        rootLayout.addView(bottomNavigation)

        // Initialize all layout panels
        buildHomeLayout()
        buildFileExplorerLayout()
        buildTerminalLayout()
        buildGitOperationsLayout()
        buildSettingsHubLayout()
        buildFileViewerLayout()
        buildDiffViewerLayout()
        buildScriptsLayout()
        buildScriptInstallLayout()

        // Add all layouts to contentFrame
        contentFrame.addView(homeScrollView)
        contentFrame.addView(fileExplorerScrollView)
        contentFrame.addView(terminalWorkspaceLayout)
        contentFrame.addView(gitOperationsScrollView)
        contentFrame.addView(settingsHubScrollView)
        contentFrame.addView(fileViewerScrollView)
        contentFrame.addView(diffViewerScrollView)
        contentFrame.addView(scriptsScrollView)
        contentFrame.addView(scriptInstallLayout)

        pageStack.push(ID_HOME)
    }

    private fun createNewTerminalSession() {
        val nld     = applicationInfo.nativeLibraryDir
        val shell   = File(nld, "libbash.so").absolutePath
        val cwd     = File(filesDir, "home").absolutePath
        val args    = arrayOf(shell, "-c", "exec python /data/data/com.ivarna.nativecode/files/usr/bin/proot-distro login debian --shared-tmp --user flux")
        val envMap  = HashMap(System.getenv())
        envMap["PATH"]                       = "$nld:/data/data/com.ivarna.nativecode/files/usr/bin:/system/bin"
        envMap["PD_PROOT_BIN"]               = File(nld, "libproot.so").absolutePath
        envMap["PROOT_LOADER"]               = File(nld, "libloader.so").absolutePath
        envMap["HOME"]                       = "/data/data/com.ivarna.nativecode/files/home"
        envMap["TERM"]                       = "xterm-256color"
        envMap["PREFIX"]                     = "/data/data/com.ivarna.nativecode/files/usr"
        envMap["LD_LIBRARY_PATH"]            = "/data/data/com.ivarna.nativecode/files/usr/lib"
        envMap["LD_PRELOAD"]                 = "/data/data/com.ivarna.nativecode/files/usr/lib/libtermux-exec.so"
        envMap["TERMUX_APP__PACKAGE_NAME"]   = "com.ivarna.nativecode"
        envMap["TERMUX__PREFIX"]             = "/data/data/com.ivarna.nativecode/files/usr"
        envMap["TERMUX__HOME"]               = "/data/data/com.ivarna.nativecode/files/home"
        envMap["SSL_CERT_FILE"]              = "/data/data/com.ivarna.nativecode/files/usr/etc/tls/cert.pem"
        envMap["CURL_CA_BUNDLE"]             = "/data/data/com.ivarna.nativecode/files/usr/etc/tls/cert.pem"
        val env = envMap.map { "${it.key}=${it.value}" }.toTypedArray()

        if (::sessionClient.isInitialized) {
            val session = TerminalSession(shell, cwd, args, env, 10000, sessionClient)
            sessionsList.add(session)
            switchTerminalSession(sessionsList.size - 1)
        }
    }

    private fun switchTerminalSession(index: Int) {
        if (index < 0 || index >= sessionsList.size) return
        activeSessionIndex = index
        val session = sessionsList[index]
        terminalSession = session
        terminalView.attachSession(session)
        terminalView.onScreenUpdated()
        terminalView.requestFocus()
        updateSidebarTerminalsList()
    }

    private fun closeTerminalSession(index: Int) {
        if (index < 0 || index >= sessionsList.size) return
        val session = sessionsList[index]
        session.finishIfRunning()
        sessionsList.removeAt(index)

        if (sessionsList.isEmpty()) {
            createNewTerminalSession()
        } else {
            if (activeSessionIndex >= sessionsList.size) {
                activeSessionIndex = sessionsList.size - 1
            }
            switchTerminalSession(activeSessionIndex)
        }
        updateSidebarTerminalsList()
    }

    private fun updateSidebarTerminalsList() {
        if (!::sidebarListContainer.isInitialized) return
        sidebarListContainer.removeAllViews()
        for (i in 0 until sessionsList.size) {
            val isSelected = (i == activeSessionIndex)
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = roundedBg(
                    if (isSelected) NC.SURFACE_HIGH else Color.TRANSPARENT,
                    if (isSelected) NC.BORDER else Color.TRANSPARENT,
                    dp(6)
                )
                layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply {
                    bottomMargin = dp(6)
                }
                setOnClickListener {
                    switchTerminalSession(i)
                    drawerLayout.closeDrawer(sidebarLayout)
                }
            }

            val terminalIcon = TextView(this@MainActivity).apply {
                text = "  "
                textSize = 14f
                setTextColor(if (isSelected) NC.SECONDARY else NC.OUTLINE)
            }

            val nameTv = TextView(this@MainActivity).apply {
                text = "Terminal ${i + 1}"
                textSize = 14f
                setTextColor(if (isSelected) NC.ON_SURFACE else NC.ON_SURF_VAR)
                typeface = Typeface.MONOSPACE
                layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
            }

            val closeBtn = TextView(this@MainActivity).apply {
                text = "✕"
                textSize = 14f
                setTextColor(NC.ERROR)
                setPadding(dp(8), dp(4), dp(8), dp(4))
                setOnClickListener {
                    closeTerminalSession(i)
                }
            }

            row.addView(terminalIcon)
            row.addView(nameTv)
            row.addView(closeBtn)
            sidebarListContainer.addView(row)
        }
    }

    // ── Screen Builders ──────────────────────────────────────────────────────

    private fun buildHomeLayout() {
        homeScrollView = ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
            visibility = View.GONE
        }
        homeLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        homeScrollView.addView(homeLayout)

        // Status Card
        homeLayout.addView(buildStatusCard())

        // GUI launcher
        homeLayout.addView(buildGuiLaunchCard())

        // Resources
        homeLayout.addView(buildResourcesCard())

        // Projects
        homeLayout.addView(buildRecentProjectsSection())
    }

    private fun buildFileExplorerLayout() {
        fileExplorerScrollView = ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
            visibility = View.GONE
        }
        fileExplorerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16))
        }
        fileExplorerScrollView.addView(fileExplorerLayout)

        // Search Bar
        val searchFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(16) }
        }
        val searchEt = EditText(this).apply {
            hint = "Search files..."; setHintTextColor(NC.OUTLINE); textSize = 13f; setTextColor(NC.ON_SURFACE)
            setBackgroundColor(NC.SURFACE)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = roundedBg(NC.SURFACE, NC.BORDER, dp(6))
        }
        searchFrame.addView(searchEt)
        fileExplorerLayout.addView(searchFrame)

        // Project Root Panel
        val rootPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(NC.SURFACE, NC.BORDER, dp(10))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(16) }
        }
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
        rootPanel.addView(header)

        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(4), dp(8), dp(8))
        }
        for ((icon, name, isFolder) in listOf(
            Triple("\uD83D\uDCC1", "app", true),
            Triple("\uD83D\uDCC1", "gradle", true),
            Triple("\uD83D\uDCDD", ".gitignore", false),
            Triple("\uD83D\uDCBB", "build.gradle.kts", false),
            Triple("\u2699\uFE0F", "settings.gradle.kts", false)
        )) {
            val row = fileRow(icon, name, isFolder, modified = name == "build.gradle.kts")
            row.setOnClickListener {
                if (!isFolder) {
                    showFileViewer(name)
                }
            }
            list.addView(row)
        }
        rootPanel.addView(list)
        fileExplorerLayout.addView(rootPanel)

        // Git Changes Panel
        val gitPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(NC.SURFACE, NC.BORDER, dp(10))
        }
        val gitHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(NC.SURFACE_VAR); setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        val gitTitle = TextView(this).apply {
            text = "\uD83C\uDF3F Git Changes (2)"; textSize = 15f; setTextColor(NC.ON_SURFACE); typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        val commitBtn = TextView(this).apply { text = "Commit"; textSize = 13f; setTextColor(NC.PRIMARY); typeface = Typeface.DEFAULT_BOLD }
        gitHeader.addView(gitTitle); gitHeader.addView(commitBtn)
        gitPanel.addView(gitHeader)

        val gitList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(4), dp(8), dp(8))
        }
        for (name in listOf("MainActivity.kt", "build.gradle.kts")) {
            val row = fileRow("\uD83D\uDCBB", name, false, modified = true)
            row.setOnClickListener {
                showDiffViewer(name)
            }
            gitList.addView(row)
        }
        gitPanel.addView(gitList)
        fileExplorerLayout.addView(gitPanel)
    }

    private fun buildTerminalLayout() {
        terminalWorkspaceLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
            visibility = View.GONE
        }

        terminalViewContainer = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f)
        }
        terminalView = TerminalView(this, null).apply {
            isFocusable = true; isFocusableInTouchMode = true
        }
        terminalViewContainer.addView(terminalView)
        terminalWorkspaceLayout.addView(terminalViewContainer)

        // Shortcut keyboard bar
        terminalWorkspaceLayout.addView(buildKeyboardToolbar())

        // Bottom floating attachments action
        val attachBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(NC.BG)
            setPadding(dp(12), dp(10), dp(12), dp(12))
        }
        val attachBtn = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            background = roundedBg(NC.SURFACE, NC.BORDER_VAR, dp(8))
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        val attachTv = TextView(this).apply {
            text = "\uD83D\uDCCE  Add Context (Ingest Image/File)"
            textSize = 12f; setTextColor(NC.ON_SURF_VAR); typeface = Typeface.MONOSPACE
        }
        attachBtn.addView(attachTv)
        attachBar.addView(attachBtn)
        terminalWorkspaceLayout.addView(attachBar)
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

    private fun buildGitOperationsLayout() {
        gitOperationsScrollView = ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
            visibility = View.GONE
        }
        gitOperationsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16))
        }
        gitOperationsScrollView.addView(gitOperationsLayout)

        // Action buttons row
        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(16))
        }
        val cloneBtn = outlineBtn("⬇ Clone Repository")
        cloneBtn.layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).apply { rightMargin = dp(8) }
        val pushBtn = primaryBtn("↑ Push")
        pushBtn.layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        actionRow.addView(cloneBtn); actionRow.addView(pushBtn)
        gitOperationsLayout.addView(actionRow)

        // GitHub CLI Auth card
        gitOperationsLayout.addView(buildAuthCard())
        gitOperationsLayout.addView(spacer(12))

        // Branches card
        gitOperationsLayout.addView(buildBranchCard())
        gitOperationsLayout.addView(spacer(12))

        // Git input credential card
        gitOperationsLayout.addView(buildGitAuthInputCard())
    }

    private fun buildSettingsHubLayout() {
        settingsHubScrollView = ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
            visibility = View.GONE
        }
        settingsHubLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16))
        }
        settingsHubScrollView.addView(settingsHubLayout)

        // Environment card
        settingsHubLayout.addView(buildEnvironmentCard())
        settingsHubLayout.addView(spacer(12))

        // AI Tools list
        settingsHubLayout.addView(buildAIToolsCard())
        settingsHubLayout.addView(spacer(12))

        // Appearance
        settingsHubLayout.addView(buildAppearanceCard())
        settingsHubLayout.addView(spacer(12))

        // Account
        settingsHubLayout.addView(buildAccountCard())
        settingsHubLayout.addView(spacer(12))

        // System Scripts
        settingsHubLayout.addView(buildScriptsSectionButton())
    }

    private fun buildScriptsSectionButton(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedBg(NC.SURFACE, NC.BORDER, dp(12))
            setPadding(dp(16))
            setOnClickListener {
                if (pageStack.isEmpty() || pageStack.peek() != ID_SCRIPTS) {
                    pageStack.push(ID_SCRIPTS)
                }
                navigateToPage(ID_SCRIPTS)
            }
            
            val icon = TextView(this@MainActivity).apply {
                text = "📜 "
                textSize = 18f
                setPadding(0, 0, dp(12), 0)
            }
            val details = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
            }
            val name = TextView(this@MainActivity).apply {
                text = "System Scripts"
                textSize = 15f
                setTextColor(NC.ON_SURFACE)
                typeface = Typeface.DEFAULT_BOLD
            }
            val sub = TextView(this@MainActivity).apply {
                text = "Run installation, configuration, or control scripts"
                textSize = 12f
                setTextColor(NC.ON_SURF_VAR)
            }
            details.addView(name)
            details.addView(sub)
            val arrow = TextView(this@MainActivity).apply {
                text = "❯"
                textSize = 14f
                setTextColor(NC.OUTLINE)
            }
            addView(icon)
            addView(details)
            addView(arrow)
        }
    }

    private fun buildScriptsLayout() {
        scriptsScrollView = ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
            visibility = View.GONE
        }
        scriptsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16))
        }
        scriptsScrollView.addView(scriptsLayout)

        val header = TextView(this).apply {
            text = "System Scripts"
            textSize = 20f
            setTextColor(NC.PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(16))
        }
        scriptsLayout.addView(header)

        // --- Host Scripts Section ---
        val hostHeader = TextView(this).apply {
            text = "Host (Native Termux) Scripts"
            textSize = 14f
            setTextColor(NC.ON_SURFACE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(8), 0, dp(8))
        }
        scriptsLayout.addView(hostHeader)

        val hostScripts = arrayOf(
            "setup_termux.sh" to "Setup basic environment and directories on host.",
            "termux_tweaks.sh" to "Apply device specific patches and shell tweaks on host.",
            "flux_install.sh" to "Install Debian container and initial config on host.",
            "start_gui.sh" to "Start desktop environment and display services on host.",
            "stop_gui.sh" to "Terminate running GUI sessions safely on host."
        )

        for ((name, desc) in hostScripts) {
            scriptsLayout.addView(buildScriptCard(name, desc, false))
        }

        scriptsLayout.addView(spacer(16))

        // --- Guest Scripts Section ---
        val guestHeader = TextView(this).apply {
            text = "Guest (Debian Container) Scripts"
            textSize = 14f
            setTextColor(NC.ON_SURFACE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(8), 0, dp(8))
        }
        scriptsLayout.addView(guestHeader)

        val guestScripts = arrayOf(
            "setup_debian_family.sh" to "Create users and VNC startup configurations in guest.",
            "setup_customization_debian.sh" to "Apply dark themes and custom packages inside Debian guest.",
            "setup_hw_accel_debian.sh" to "Configure hardware acceleration and VirGL rendering in guest.",
            "setup_cli_tools.sh" to "Install Node.js/NVM and AI CLI tools (Aider, Claude, Cline) in guest."
        )

        for ((name, desc) in guestScripts) {
            scriptsLayout.addView(buildScriptCard(name, desc, true))
        }
    }

    private fun buildScriptCard(name: String, desc: String, runInDebian: Boolean): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(NC.SURFACE, NC.BORDER, dp(12))
            setPadding(dp(16))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply {
                bottomMargin = dp(12)
            }
            setOnClickListener {
                runScriptInTerminal(name, runInDebian)
            }

            val title = TextView(this@MainActivity).apply {
                text = name
                textSize = 15f
                setTextColor(NC.SECONDARY)
                typeface = Typeface.MONOSPACE
                setPadding(0, 0, 0, dp(4))
            }
            val descTv = TextView(this@MainActivity).apply {
                text = desc
                textSize = 12f
                setTextColor(NC.ON_SURF_VAR)
            }
            addView(title)
            addView(descTv)
        }
    }

    private fun buildScriptInstallLayout() {
        scriptInstallLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
            visibility = View.GONE
        }

        scriptInstallViewContainer = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f)
        }

        scriptInstallTerminalView = TerminalView(this, null).apply {
            isFocusable = false
            isFocusableInTouchMode = false
            setOnTouchListener { _, _ -> true }
            try {
                val fontFile = File(filesDir, "home/.termux/font.ttf")
                val tf = if (fontFile.exists()) {
                    Typeface.createFromFile(fontFile)
                } else {
                    Typeface.createFromAsset(assets, "fonts/font.ttf")
                }
                setTypeface(tf)
            } catch (e: Exception) {
                Log.e("Terminal", "Failed to set custom typeface on script view", e)
            }
        }

        scriptInstallViewContainer.addView(scriptInstallTerminalView)
        scriptInstallLayout.addView(scriptInstallViewContainer)
    }

    private fun runScriptInTerminal(scriptName: String, runInDebian: Boolean) {
        scriptInstallTerminalView.setTextSize(40)
        val nld     = applicationInfo.nativeLibraryDir
        val shell   = File(nld, "libbash.so").absolutePath
        val cwd     = File(filesDir, "home").absolutePath
        val scriptPath = File(cwd, scriptName).absolutePath
        val args = if (runInDebian) {
            arrayOf(
                shell,
                "-c",
                "exec python /data/data/com.ivarna.nativecode/files/usr/bin/proot-distro login debian --shared-tmp -- bash /data/data/com.ivarna.nativecode/files/home/$scriptName"
            )
        } else {
            arrayOf(shell, scriptPath)
        }
        val envMap  = HashMap(System.getenv())
        envMap["PATH"]                       = "$nld:/data/data/com.ivarna.nativecode/files/usr/bin:/system/bin"
        envMap["PD_PROOT_BIN"]               = File(nld, "libproot.so").absolutePath
        envMap["PROOT_LOADER"]               = File(nld, "libloader.so").absolutePath
        envMap["HOME"]                       = "/data/data/com.ivarna.nativecode/files/home"
        envMap["TERM"]                       = "xterm-256color"
        envMap["PREFIX"]                     = "/data/data/com.ivarna.nativecode/files/usr"
        envMap["LD_LIBRARY_PATH"]            = "/data/data/com.ivarna.nativecode/files/usr/lib"
        envMap["LD_PRELOAD"]                 = "/data/data/com.ivarna.nativecode/files/usr/lib/libtermux-exec.so"
        envMap["TERMUX_APP__PACKAGE_NAME"]   = "com.ivarna.nativecode"
        envMap["TERMUX__PREFIX"]             = "/data/data/com.ivarna.nativecode/files/usr"
        envMap["TERMUX__HOME"]               = "/data/data/com.ivarna.nativecode/files/home"
        envMap["SSL_CERT_FILE"]              = "/data/data/com.ivarna.nativecode/files/usr/etc/tls/cert.pem"
        envMap["CURL_CA_BUNDLE"]             = "/data/data/com.ivarna.nativecode/files/usr/etc/tls/cert.pem"
        val env = envMap.map { "${it.key}=${it.value}" }.toTypedArray()

        val scriptViewClient = object : TerminalViewClient {
            override fun onScale(scale: Float): Float = scale
            override fun onSingleTapUp(e: MotionEvent) {}
            override fun shouldBackButtonBeMappedToEscape(): Boolean = false
            override fun shouldEnforceCharBasedInput(): Boolean      = false
            override fun shouldUseCtrlSpaceWorkaround(): Boolean      = false
            override fun isTerminalViewSelected(): Boolean            = false
            override fun copyModeChanged(active: Boolean) {}
            override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent, session: TerminalSession): Boolean = true
            override fun onKeyUp(keyCode: Int, event: android.view.KeyEvent): Boolean = true
            override fun onLongPress(e: MotionEvent): Boolean = true
            override fun readControlKey(): Boolean = false
            override fun readAltKey(): Boolean     = false
            override fun readShiftKey(): Boolean   = false
            override fun readFnKey(): Boolean      = false
            override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean = true
            override fun onEmulatorSet() {}
            override fun logError(tag: String, message: String)   {}
            override fun logWarn(tag: String, message: String)    {}
            override fun logInfo(tag: String, message: String)    {}
            override fun logDebug(tag: String, message: String)   {}
            override fun logVerbose(tag: String, message: String) {}
            override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) {}
            override fun logStackTrace(tag: String, e: Exception) {}
        }

        val scriptSessionClient = object : TerminalSessionClient {
            override fun onTextChanged(session: TerminalSession) {
                scriptInstallTerminalView.onScreenUpdated()
            }
            override fun onTitleChanged(session: TerminalSession) {}
            override fun onSessionFinished(session: TerminalSession) {
                Log.d("Terminal", "Script finished: ${session.exitStatus}")
                mainHandler.post {
                    Toast.makeText(this@MainActivity, "$scriptName Finished!", Toast.LENGTH_LONG).show()
                    isScriptRunning = false
                    if (pageStack.isNotEmpty() && pageStack.peek() == ID_SCRIPT_INSTALL) {
                        backBtn.visibility = View.VISIBLE
                    }
                }
            }
            override fun onCopyTextToClipboard(session: TerminalSession, text: String) {}
            override fun onPasteTextFromClipboard(session: TerminalSession) {}
            override fun onBell(session: TerminalSession) {}
            override fun onColorsChanged(session: TerminalSession) {}
            override fun onTerminalCursorStateChange(state: Boolean) {}
            override fun getTerminalCursorStyle(): Int? = 1
            override fun logError(tag: String, message: String) { Log.e(tag, message) }
            override fun logWarn(tag: String, message: String) { Log.w(tag, message) }
            override fun logInfo(tag: String, message: String) { Log.i(tag, message) }
            override fun logDebug(tag: String, message: String) { Log.d(tag, message) }
            override fun logVerbose(tag: String, message: String) { Log.v(tag, message) }
            override fun logStackTraceWithMessage(tag: String, message: String, e: java.lang.Exception) { Log.e(tag, message, e) }
            override fun logStackTrace(tag: String, e: java.lang.Exception) { Log.e(tag, "Stacktrace", e) }
        }

        scriptInstallTerminalView.setTerminalViewClient(scriptViewClient)
        val session = TerminalSession(shell, cwd, args, env, 10000, scriptSessionClient)
        scriptInstallSession = session
        scriptInstallTerminalView.attachSession(session)
        isScriptRunning = true

        if (pageStack.isEmpty() || pageStack.peek() != ID_SCRIPT_INSTALL) {
            pageStack.push(ID_SCRIPT_INSTALL)
        }
        navigateToPage(ID_SCRIPT_INSTALL)
    }

    // ── Sub-pages Builders ───────────────────────────────────────────────────

    private fun buildFileViewerLayout() {
        fileViewerScrollView = ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
            visibility = View.GONE
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16))
        }
        fileViewerScrollView.addView(container)

        // Image preview card
        container.addView(buildImagePreviewCard())
        container.addView(spacer(16))

        // Code viewer card
        container.addView(buildCodeViewerCard())
    }

    private fun buildDiffViewerLayout() {
        diffViewerScrollView = ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
            visibility = View.GONE
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16))
        }
        diffViewerScrollView.addView(container)

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

        // Diff table
        val hScroll = HorizontalScrollView(this).apply {
            setBackgroundColor(NC.LOGBG)
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        val table = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(MATCH, WRAP)
        }

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

            val tableRow = LinearLayout(this).apply {
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
            tableRow.addView(oldLn); tableRow.addView(newLn); tableRow.addView(markerTv); tableRow.addView(codeTv)
            table.addView(tableRow)
        }
        hScroll.addView(table)
        diffCard.addView(hScroll)
        container.addView(diffCard)

        // Bottom action bar
        container.addView(spacer(16))
        val bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, dp(16))
        }
        val discardBtn = outlineBtn("Discard")
        val commitBtn = primaryBtn("Commit Changes")
        discardBtn.layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).apply { rightMargin = dp(12) }
        bottomBar.addView(discardBtn); bottomBar.addView(commitBtn)
        container.addView(bottomBar)
    }

    // ── Helper Sub-page Actions ──────────────────────────────────────────────

    private fun showFileViewer(name: String) {
        unifiedHeader.visibility = View.VISIBLE
        homeScrollView.visibility = View.GONE
        fileExplorerScrollView.visibility = View.GONE
        terminalWorkspaceLayout.visibility = View.GONE
        gitOperationsScrollView.visibility = View.GONE
        settingsHubScrollView.visibility = View.GONE
        diffViewerScrollView.visibility = View.GONE

        fileViewerScrollView.visibility = View.VISIBLE
    }

    private fun showDiffViewer(name: String) {
        unifiedHeader.visibility = View.VISIBLE
        homeScrollView.visibility = View.GONE
        fileExplorerScrollView.visibility = View.GONE
        terminalWorkspaceLayout.visibility = View.GONE
        gitOperationsScrollView.visibility = View.GONE
        settingsHubScrollView.visibility = View.GONE
        fileViewerScrollView.visibility = View.GONE

        diffViewerScrollView.visibility = View.VISIBLE
    }

    // ── Shared widget builders (ported from old activities) ─────────────────────

    private fun buildStatusCard(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(NC.SURFACE, NC.BORDER, dp(12))
            setPadding(dp(16))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(12) }
        }
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(12))
        }
        val cardTitle = TextView(this).apply {
            text = "Environment Status"; textSize = 18f; setTextColor(NC.PRIMARY); typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        titleRow.addView(cardTitle); card.addView(titleRow)

        val pill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = roundedBg(NC.LOGBG, NC.BORDER, dp(6))
            setPadding(dp(12), dp(8), dp(12), dp(8))
            layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).apply { bottomMargin = dp(8) }
        }
        homeStatusDot = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(10), dp(10)).apply { rightMargin = dp(8) }
            background = circleDrawable(NC.SECONDARY)
        }
        homeStatusLabel = TextView(this).apply { text = "Ready"; textSize = 13f; setTextColor(NC.ON_SURFACE); typeface = Typeface.MONOSPACE }
        pill.addView(homeStatusDot); pill.addView(homeStatusLabel); card.addView(pill)

        val infoRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val dnsIcon = TextView(this).apply { text = "\uD83D\uDCE1 "; textSize = 13f; setTextColor(NC.OUTLINE) }
        homeContainerLabel = TextView(this).apply { text = "Container: PRoot (Debian 12)"; textSize = 13f; setTextColor(NC.ON_SURF_VAR); typeface = Typeface.MONOSPACE }
        infoRow.addView(dnsIcon); infoRow.addView(homeContainerLabel); card.addView(infoRow)

        pulseView(homeStatusDot)
        return card
    }

    private fun buildGuiLaunchCard(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(NC.SURFACE, NC.BORDER, dp(12))
            setPadding(dp(16))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(12) }
        }
        val sectionTitle = TextView(this).apply { text = "Graphical Desktop"; textSize = 16f; setTextColor(NC.ON_SURFACE); typeface = Typeface.DEFAULT_BOLD; setPadding(0, 0, 0, dp(4)) }
        val sectionSub = TextView(this).apply { text = "Launch Termux X11 and start or stop the XFCE4 desktop session."; textSize = 13f; setTextColor(NC.ON_SURF_VAR); setPadding(0, 0, 0, dp(12)) }
        card.addView(sectionTitle); card.addView(sectionSub)

        openX11Btn = primaryButton("  Open X11 Display") {
            val intent = Intent(this, com.termux.x11.MainActivity::class.java)
            startActivity(intent)
        }
        card.addView(openX11Btn)
        card.addView(spacer(8))

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        startGuiBtn = secondaryButton("\u25B6 Start XFCE") { startGui() }
        startGuiBtn.isEnabled = false; startGuiBtn.alpha = 0.5f
        startGuiBtn.layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).apply { rightMargin = dp(8) }

        stopGuiBtn = dangerButton("\u25A0 Stop XFCE") { stopGui() }
        stopGuiBtn.isEnabled = false; stopGuiBtn.alpha = 0.5f
        stopGuiBtn.layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)

        row.addView(startGuiBtn); row.addView(stopGuiBtn); card.addView(row)
        return card
    }

    private fun buildResourcesCard(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(Color.parseColor("#1a1822"), Color.parseColor("#ffffff1a"), dp(12))
            setPadding(dp(16))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(12) }
        }
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, dp(12))
        }
        val resTitle = TextView(this).apply { text = "System Resources"; textSize = 16f; setTextColor(NC.ON_SURFACE); typeface = Typeface.DEFAULT_BOLD }
        val resSub = TextView(this).apply { text = "Monitoring container usage"; textSize = 12f; setTextColor(NC.ON_SURF_VAR) }
        titleRow.addView(resTitle); titleRow.addView(resSub); card.addView(titleRow)

        val statsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        statsRow.addView(statWidget("CPU", "12%", NC.SECONDARY))
        statsRow.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })
        statsRow.addView(statWidget("MEM", "1.2 GB", NC.PRIMARY))
        statsRow.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })
        statsRow.addView(statWidget("DISK", "45%", NC.TERTIARY))
        card.addView(statsRow)
        return card
    }

    private fun buildRecentProjectsSection(): View {
        val section = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, dp(12))
        }
        val sectionTitle = TextView(this).apply { text = "Recent Workspaces"; textSize = 16f; setTextColor(NC.ON_SURFACE); typeface = Typeface.DEFAULT_BOLD; layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f) }
        val viewAll = TextView(this).apply { text = "View All"; textSize = 13f; setTextColor(NC.SECONDARY) }
        headerRow.addView(sectionTitle); headerRow.addView(viewAll); section.addView(headerRow)

        section.addView(projectCard("MyAndroidApp", "/home/user/workspace/MyAndroidApp", "2 hours ago"))
        section.addView(spacer(8))
        section.addView(projectCard("API_Server", "/home/user/workspace/API_Server", "Yesterday"))
        return section
    }

    private fun buildAuthCard(): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = roundedBg(NC.SURFACE, NC.BORDER, dp(12)); setPadding(dp(16))
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, dp(8)) }
        val icon = TextView(this).apply { text = "\uD83D\uDDA5\uFE0F "; textSize = 16f; setTextColor(NC.SECONDARY) }
        val title = TextView(this).apply { text = "Terminal Authentication"; textSize = 16f; setTextColor(NC.ON_SURFACE); typeface = Typeface.DEFAULT_BOLD }
        header.addView(icon); header.addView(title); card.addView(header)

        val sub = TextView(this).apply { text = "Authenticate using the GitHub CLI interactive flow."; textSize = 13f; setTextColor(NC.ON_SURF_VAR); setPadding(0, 0, 0, dp(12)) }
        card.addView(sub)

        val termBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(NC.LOGBG); background = roundedBg(NC.LOGBG, NC.BORDER, dp(8)); setPadding(dp(12))
        }
        val dotsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, dp(8)) }
        for (col in listOf(NC.ERROR, NC.TERTIARY, NC.SECONDARY)) {
            val dot = LinearLayout(this).apply { background = circleDrawable(col); layoutParams = LinearLayout.LayoutParams(dp(10), dp(10)).apply { rightMargin = dp(6) } }
            dotsRow.addView(dot)
        }
        termBlock.addView(dotsRow)
        for ((prompt, line) in listOf(
            "\$ " to "gh auth login",
            "? " to "What account do you want to log into? GitHub.com",
            "? " to "Preferred protocol for Git operations? HTTPS",
            "! " to "First copy your one-time code: ABCD-1234"
        )) {
            val tv = TextView(this).apply { text = "$prompt$line"; textSize = 12f; typeface = Typeface.MONOSPACE; setTextColor(if (prompt == "! ") NC.PRIMARY else NC.ON_SURF_VAR) }
            termBlock.addView(tv)
        }
        card.addView(termBlock)
        card.addView(spacer(12))

        val codeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val codeLbl = TextView(this).apply { text = "Code: "; textSize = 13f; setTextColor(NC.ON_SURF_VAR); typeface = Typeface.MONOSPACE }
        val codeVal = textBadge("ABCD-1234", NC.SURFACE_HIGH, NC.ON_SURFACE)
        val spacer2 = View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) }
        val openBtn = primaryButton("Open Browser") {}
        openBtn.layoutParams = LinearLayout.LayoutParams(WRAP, WRAP)
        codeRow.addView(codeLbl); codeRow.addView(codeVal); codeRow.addView(spacer2); codeRow.addView(openBtn)
        card.addView(codeRow)
        return card
    }

    private fun buildBranchCard(): LinearLayout {
        val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = roundedBg(NC.SURFACE, NC.BORDER, dp(12)); setPadding(dp(16)) }
        val headerRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, dp(12)) }
        val icon = TextView(this).apply { text = "\uD83C\uDF3F "; textSize = 16f; setTextColor(NC.SECONDARY) }
        val title = TextView(this).apply { text = "Active Branches"; textSize = 16f; setTextColor(NC.ON_SURFACE); typeface = Typeface.DEFAULT_BOLD; layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f) }
        val fetchBtn = TextView(this).apply { text = "↺ git fetch"; textSize = 11f; setTextColor(NC.ON_SURF_VAR); typeface = Typeface.MONOSPACE; background = roundedBg(NC.SURFACE_HIGH, NC.BORDER, dp(4)); setPadding(dp(8), dp(4), dp(8), dp(4)) }
        headerRow.addView(icon); headerRow.addView(title); headerRow.addView(fetchBtn); card.addView(headerRow)

        val treeBlock = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = roundedBg(NC.LOGBG, NC.BORDER, dp(8)); setPadding(dp(12)) }
        for ((prefix, branch, isHead) in listOf(Triple("├── ", "main", false), Triple("└── ", "feature/auth", true))) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(4), 0, dp(4)) }
            val preTv = TextView(this).apply { text = prefix; textSize = 12f; setTextColor(NC.OUTLINE); typeface = Typeface.MONOSPACE }
            val nameTv = TextView(this).apply { text = branch; textSize = 12f; typeface = Typeface.MONOSPACE; setTextColor(if (isHead) NC.PRIMARY else NC.ON_SURF_VAR); if (isHead) typeface = Typeface.DEFAULT_BOLD }
            row.addView(preTv); row.addView(nameTv)
            if (isHead) {
                row.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })
                row.addView(textBadge("HEAD", Color.argb(51, 210, 187, 255), NC.PRIMARY))
            }
            treeBlock.addView(row)
        }
        card.addView(treeBlock)
        return card
    }

    private fun buildGitAuthInputCard(): LinearLayout {
        val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = roundedBg(NC.SURFACE, NC.BORDER, dp(12)); setPadding(dp(16)) }
        card.addView(inputField("GitHub Username / Token", "ghp_xxxxxxxxxxxxxxxxxxxx"))
        card.addView(spacer(12))
        card.addView(inputField("Repository URL or Name", "owner/repo"))
        return card
    }

    private fun buildEnvironmentCard(): LinearLayout {
        val card = glassCard()
        card.addView(sectionHeader("\uD83D\uDDA5\uFE0F", "Environment", NC.SECONDARY))
        card.addView(infoRow("Container Method", "PRoot"))
        card.addView(spacer(8))
        card.addView(infoRow("OS Version", "Debian 12"))
        return card
    }

    private fun buildAIToolsCard(): LinearLayout {
        val card = glassCard()
        card.addView(sectionHeader("\uD83E\uDD16", "AI Tools", NC.PRIMARY))
        val tagRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 0, 0, dp(16)) }
        for (tool in listOf("Claude", "Aider", "Cline")) {
            val tag = textBadge(tool, Color.argb(26, 76, 215, 246), NC.SECONDARY)
            tag.layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).apply { rightMargin = dp(8) }
            tagRow.addView(tag)
        }
        card.addView(tagRow)
        return card
    }

    private fun buildAppearanceCard(): LinearLayout {
        val card = glassCard()
        card.addView(sectionHeader("\uD83C\uDFA8", "Appearance", NC.TERTIARY))
        val themes = arrayOf("Midnight Aurora (Dark)", "Abyss (Deep Dark)", "Solarized (Light)")
        val spinner = Spinner(this).apply {
            background = roundedBg(NC.SURFACE_HIGH, NC.BORDER_VAR, dp(6))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, themes)
        card.addView(spinner)
        return card
    }

    private fun buildAccountCard(): LinearLayout {
        val card = glassCard()
        card.addView(sectionHeader("\uD83D\uDC64", "Account Connections", NC.SECONDARY))
        val ghRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = roundedBg(NC.SURFACE, NC.BORDER_VAR, dp(8)); setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        val ghDetails = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f) }
        val ghName = TextView(this).apply { text = "GitHub"; textSize = 14f; setTextColor(NC.ON_SURFACE) }
        val ghStatus = TextView(this).apply { text = "Connected (dev_ninja)"; textSize = 12f; setTextColor(NC.SECONDARY); typeface = Typeface.MONOSPACE }
        ghDetails.addView(ghName); ghDetails.addView(ghStatus)
        val checkTv = TextView(this).apply { text = "✓"; textSize = 18f; setTextColor(NC.SECONDARY) }
        ghRow.addView(ghDetails); ghRow.addView(checkTv)
        card.addView(ghRow)
        return card
    }

    private fun buildImagePreviewCard(): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = roundedBg(NC.SURFACE, NC.BORDER, dp(12))
        }
        val imgArea = LinearLayout(this).apply { gravity = Gravity.CENTER; setBackgroundColor(NC.SURFACE_HIGH); layoutParams = LinearLayout.LayoutParams(MATCH, dp(150)) }
        val placeholder = TextView(this).apply { text = "\uD83D\uDDBC\uFE0F"; textSize = 48f }
        imgArea.addView(placeholder); card.addView(imgArea)
        val info = TextView(this).apply { text = "  main.png • 1920×1080 • 2.4 MB"; textSize = 12f; setTextColor(NC.ON_SURF_VAR); setPadding(dp(12), dp(10), dp(12), dp(10)) }
        card.addView(info)
        return card
    }

    private fun buildCodeViewerCard(): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = roundedBg(NC.LOGBG, NC.BORDER_VAR, dp(12))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setBackgroundColor(NC.SURFACE); setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        val fileNameTv = TextView(this).apply { text = "main.rs"; textSize = 13f; setTextColor(NC.ON_SURFACE); typeface = Typeface.MONOSPACE; layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f) }
        val copyBtn = TextView(this).apply { text = "COPY"; textSize = 10f; setTextColor(NC.ON_SURFACE); background = roundedBg(NC.SURFACE_VAR, NC.BORDER, dp(4)); setPadding(dp(8), dp(4), dp(8), dp(4)) }
        header.addView(fileNameTv); header.addView(copyBtn); card.addView(header)

        val codeScroll = HorizontalScrollView(this).apply { setPadding(dp(12), dp(12), dp(12), dp(12)) }
        val codeLines = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val lines = listOf("fn main() {", "    println!(\"Hello World!\");", "}")
        for ((idx, line) in lines.withIndex()) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val num = TextView(this).apply { text = "${idx + 1}"; textSize = 12f; setTextColor(NC.OUTLINE); typeface = Typeface.MONOSPACE; setPadding(0, 0, dp(10), 0) }
            val code = TextView(this).apply { text = line; textSize = 12f; setTextColor(NC.ON_SURFACE); typeface = Typeface.MONOSPACE }
            row.addView(num); row.addView(code); codeLines.addView(row)
        }
        codeScroll.addView(codeLines); card.addView(codeScroll)
        return card
    }

    // ── Helper UI properties / primitives ─────────────────────────────────────

    private fun fileRow(icon: String, name: String, isFolder: Boolean, modified: Boolean = false): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(10), dp(8), dp(10))
            val iconTv = TextView(this@MainActivity).apply { text = icon; textSize = 16f; setPadding(0, 0, dp(10), 0); setTextColor(if (isFolder) NC.SECONDARY else NC.OUTLINE) }
            val nameTv = TextView(this@MainActivity).apply { text = name; textSize = 13f; typeface = Typeface.MONOSPACE; setTextColor(NC.ON_SURFACE); layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f) }
            addView(iconTv); addView(nameTv)
            if (modified) {
                val badge = TextView(this@MainActivity).apply { text = "M"; textSize = 10f; setTextColor(NC.ON_SURFACE); background = roundedBg(NC.TERTIARY, NC.TERTIARY, dp(4)); setPadding(dp(6), dp(2), dp(6), dp(2)) }
                addView(badge)
            }
        }
    }

    private fun glassCard() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = roundedBg(NC.SURFACE, NC.BORDER, dp(12)); setPadding(dp(16)) }
    private fun sectionHeader(icon: String, title: String, iconColor: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, dp(10))
        addView(TextView(this@MainActivity).apply { text = icon; textSize = 18f; setTextColor(iconColor); setPadding(0, 0, dp(10), 0) })
        addView(TextView(this@MainActivity).apply { text = title; textSize = 15f; setTextColor(NC.ON_SURFACE); typeface = Typeface.DEFAULT_BOLD })
    }
    private fun infoRow(label: String, value: String) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; background = roundedBg(NC.SURFACE_HIGH, NC.BORDER_VAR, dp(8)); setPadding(dp(12), dp(10), dp(12), dp(10))
        addView(TextView(this@MainActivity).apply { text = label; textSize = 13f; setTextColor(NC.ON_SURF_VAR); layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f) })
        addView(TextView(this@MainActivity).apply { text = value; textSize = 13f; setTextColor(NC.PRIMARY); typeface = Typeface.MONOSPACE })
    }
    private fun inputField(label: String, hint: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, dp(10))
        addView(TextView(this@MainActivity).apply { text = label; textSize = 10f; setTextColor(NC.ON_SURF_VAR); typeface = Typeface.MONOSPACE; setPadding(0, 0, 0, dp(4)) })
        addView(EditText(this@MainActivity).apply { this.hint = hint; setHintTextColor(NC.OUTLINE); textSize = 13f; setTextColor(NC.ON_SURFACE); typeface = Typeface.MONOSPACE; setBackgroundColor(NC.SURFACE_HIGH); background = roundedBg(NC.SURFACE_HIGH, NC.BORDER_VAR, dp(6)); setPadding(dp(12), dp(10), dp(12), dp(10)) })
    }

    private fun lineNumCell(num: String) = TextView(this).apply { text = num; textSize = 11f; setTextColor(NC.OUTLINE); typeface = Typeface.MONOSPACE; gravity = Gravity.END; setPadding(dp(6), 0, dp(6), 0); layoutParams = LinearLayout.LayoutParams(dp(36), WRAP) }
    private fun textBadge(text: String, bg: Int, fg: Int) = TextView(this).apply { this.text = text; textSize = 10f; setTextColor(fg); typeface = Typeface.MONOSPACE; background = roundedBg(bg, fg, dp(4)); setPadding(dp(6), dp(2), dp(6), dp(2)) }
    private fun primaryBtn(t: String) = TextView(this).apply { text = t; textSize = 13f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; background = roundedBg(NC.PRIMARY_CON, NC.PRIMARY_CON, dp(20)); setPadding(dp(20), dp(10), dp(20), dp(10)) }
    private fun outlineBtn(t: String) = TextView(this).apply { text = t; textSize = 13f; setTextColor(NC.ON_SURFACE); gravity = Gravity.CENTER; background = roundedBg(NC.SURFACE, NC.BORDER, dp(20)); setPadding(dp(20), dp(10), dp(20), dp(10)) }
    private fun spacer(dp_: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(MATCH, dp(dp_)) }
    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()
    private fun roundedBg(fill: Int, stroke: Int, r: Int) = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; setColor(fill); setStroke(dp(1), stroke); cornerRadius = r.toFloat() }
    private fun circleDrawable(color: Int) = android.graphics.drawable.ShapeDrawable(android.graphics.drawable.shapes.OvalShape()).apply { paint.color = color }
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    // ── Setup & shell integrations (original working logic preserved) ─────────

    private fun onSetupComplete() {
        mainHandler.post {
            startGuiBtn.isEnabled = true; startGuiBtn.alpha = 1f
            stopGuiBtn.isEnabled = true; stopGuiBtn.alpha = 1f
            homeStatusLabel.text = "Ready"
            initTerminalView()
        }
    }

    private fun deployScripts() {
        try {
            val homeDir = File(filesDir, "home").also { it.mkdirs() }
            val scripts = arrayOf("setup_termux.sh", "termux_tweaks.sh", "flux_install.sh", "start_gui.sh", "stop_gui.sh", "setup_cli_tools.sh")
            for (script in scripts) {
                val assetPath = if (script.contains("tweaks")) "scripts/termux_tweaks.sh" else "scripts/$script"
                val out = File(homeDir, script)
                assets.open(assetPath).use { input -> FileOutputStream(out).use { input.copyTo(it) } }
                out.setExecutable(true)
            }
            // Deploy font.ttf
            val termuxDir = File(homeDir, ".termux").also { it.mkdirs() }
            val fontOut = File(termuxDir, "font.ttf")
            assets.open("fonts/font.ttf").use { input -> FileOutputStream(fontOut).use { input.copyTo(it) } }
        } catch (e: Exception) {
            Log.e("Setup", "Failed to deploy scripts", e)
        }
    }

    private fun runShellCommand(cmd: Array<String>): Int {
        val adjusted = if (cmd.isNotEmpty() && cmd[0].startsWith("/data/data/"))
            arrayOf("/system/bin/linker64") + cmd else cmd
        val pb  = ProcessBuilder(*adjusted)
        val env = pb.environment()
        val nld = applicationInfo.nativeLibraryDir
        env["PATH"]                       = "$nld:/data/data/com.ivarna.nativecode/files/usr/bin:/system/bin"
        env["PD_PROOT_BIN"]               = File(nld, "libproot.so").absolutePath
        env["PROOT_LOADER"]               = File(nld, "libloader.so").absolutePath
        env["LD_LIBRARY_PATH"]            = "/data/data/com.ivarna.nativecode/files/usr/lib:/data/data/com.ivarna.nativecode/files/usr/opt/virglrenderer-android/lib"
        env["LD_PRELOAD"]                 = "/data/data/com.ivarna.nativecode/files/usr/lib/libtermux-exec.so"
        env["PREFIX"]                     = "/data/data/com.ivarna.nativecode/files/usr"
        env["HOME"]                       = "/data/data/com.ivarna.nativecode/files/home"
        env["TMPDIR"]                     = "/data/data/com.ivarna.nativecode/files/usr/tmp"
        env["PROOT_TMP_DIR"]              = "/data/data/com.ivarna.nativecode/files/usr/tmp"
        env["TERMUX_APP__PACKAGE_NAME"]   = "com.ivarna.nativecode"
        env["TERMUX_X11_APK_PATH"]        = applicationInfo.sourceDir
        env["TERMUX_X11_OVERRIDE_PACKAGE"]= "com.ivarna.nativecode"
        env["TERMUX__PREFIX"]             = "/data/data/com.ivarna.nativecode/files/usr"
        env["TERMUX__HOME"]               = "/data/data/com.ivarna.nativecode/files/home"
        env["SSL_CERT_FILE"]              = "/data/data/com.ivarna.nativecode/files/usr/etc/tls/cert.pem"
        env["CURL_CA_BUNDLE"]             = "/data/data/com.ivarna.nativecode/files/usr/etc/tls/cert.pem"
        pb.redirectErrorStream(true)
        val proc = pb.start()
        val stream = proc.inputStream
        val buf = ByteArray(1024)
        while (stream.read(buf) != -1) { /* Just consume output */ }
        return proc.waitFor()
    }

    private fun startGui() {
        val serviceIntent = Intent(this, BackgroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent)
        else startService(serviceIntent)

        executor.execute {
            val nld  = applicationInfo.nativeLibraryDir
            val bash = File(nld, "libbash.so").absolutePath
            runShellCommand(arrayOf(bash, "/data/data/com.ivarna.nativecode/files/home/start_gui.sh", "debian"))
        }

        mainHandler.postDelayed({
            val x11 = Intent(this, com.termux.x11.MainActivity::class.java)
            x11.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(x11)
        }, 500)
    }

    private fun stopGui() {
        val stopBroadcast = Intent("com.termux.x11.ACTION_STOP")
        stopBroadcast.setPackage(packageName)
        sendBroadcast(stopBroadcast)
        stopService(Intent(this, BackgroundService::class.java))

        executor.execute {
            val nld  = applicationInfo.nativeLibraryDir
            val bash = File(nld, "libbash.so").absolutePath
            runShellCommand(arrayOf(bash, "/data/data/com.ivarna.nativecode/files/home/stop_gui.sh", "debian"))
        }
    }

    private fun initTerminalView() {
        terminalView.setTextSize(40)
        try {
            val tf = Typeface.createFromAsset(assets, "fonts/font.ttf")
            terminalView.setTypeface(tf)
        } catch (e: Exception) {
            Log.e("Terminal", "Failed to set custom typeface", e)
        }

        viewClient = object : TerminalViewClient {
            override fun onScale(scale: Float): Float = scale
            override fun onSingleTapUp(e: MotionEvent) {
                terminalView.requestFocus()
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(terminalView, InputMethodManager.SHOW_IMPLICIT)
            }
            override fun shouldBackButtonBeMappedToEscape(): Boolean = false
            override fun shouldEnforceCharBasedInput(): Boolean      = false
            override fun shouldUseCtrlSpaceWorkaround(): Boolean      = false
            override fun isTerminalViewSelected(): Boolean            = true
            override fun copyModeChanged(active: Boolean) {}
            override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent, session: TerminalSession): Boolean = false
            override fun onKeyUp(keyCode: Int, event: android.view.KeyEvent): Boolean = false
            override fun onLongPress(e: MotionEvent): Boolean = false
            override fun readControlKey(): Boolean = false
            override fun readAltKey(): Boolean     = false
            override fun readShiftKey(): Boolean   = false
            override fun readFnKey(): Boolean      = false
            override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean = false
            override fun onEmulatorSet() {}
            override fun logError(tag: String, message: String)   {}
            override fun logWarn(tag: String, message: String)    {}
            override fun logInfo(tag: String, message: String)    {}
            override fun logDebug(tag: String, message: String)   {}
            override fun logVerbose(tag: String, message: String) {}
            override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) {}
            override fun logStackTrace(tag: String, e: Exception) {}
        }

        sessionClient = object : TerminalSessionClient {
            override fun onTextChanged(session: TerminalSession) {
                if (session == terminalSession) {
                    terminalView.onScreenUpdated()
                }
            }
            override fun onTitleChanged(session: TerminalSession)   {}
            override fun onSessionFinished(session: TerminalSession) {
                Log.d("Terminal", "Session finished: ${session.exitStatus}")
                mainHandler.post {
                    val idx = sessionsList.indexOf(session)
                    if (idx != -1) {
                        closeTerminalSession(idx)
                    }
                }
            }
            override fun onCopyTextToClipboard(session: TerminalSession, text: String) {}
            override fun onPasteTextFromClipboard(session: TerminalSession) {}
            override fun onBell(session: TerminalSession)           {}
            override fun onColorsChanged(session: TerminalSession)  {}
            override fun onTerminalCursorStateChange(state: Boolean){}
            override fun getTerminalCursorStyle(): Int? = 1
            override fun logError(tag: String, message: String)   { Log.e(tag, message) }
            override fun logWarn(tag: String, message: String)    { Log.w(tag, message) }
            override fun logInfo(tag: String, message: String)    { Log.i(tag, message) }
            override fun logDebug(tag: String, message: String)   { Log.d(tag, message) }
            override fun logVerbose(tag: String, message: String) { Log.v(tag, message) }
            override fun logStackTraceWithMessage(tag: String, message: String, e: java.lang.Exception) { Log.e(tag, message, e) }
            override fun logStackTrace(tag: String, e: java.lang.Exception) { Log.e(tag, "Stacktrace", e) }
        }

        terminalView.setTerminalViewClient(viewClient)
        createNewTerminalSession()

        terminalView.postDelayed({
            if (pageStack.isNotEmpty() && pageStack.peek() == ID_TERMINAL) {
                terminalView.requestFocus()
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(terminalView, InputMethodManager.SHOW_IMPLICIT)
            }
        }, 1000)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopService(Intent(this, BackgroundService::class.java))
        for (session in sessionsList) {
            session.finishIfRunning()
        }
        scriptInstallSession?.finishIfRunning()
    }

    // ── Show page helpers ────────────────────────────────────────────────────

    private fun showHome() {
        bottomNavigation.selectedItemId = ID_HOME
    }

    private fun iconButton(icon: String): TextView = TextView(this).apply {
        text = icon; textSize = 18f; setPadding(dp(8), dp(4), dp(8), dp(4))
    }

    private fun primaryButton(label: String, onClick: () -> Unit): TextView = TextView(this).apply {
        text = label; textSize = 15f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        background = roundedBg(NC.PRIMARY_CON, NC.PRIMARY_CON, dp(24))
        setPadding(dp(16), dp(12), dp(16), dp(12))
        setOnClickListener { onClick() }
    }

    private fun secondaryButton(label: String, onClick: () -> Unit): TextView = TextView(this).apply {
        text = label; textSize = 13f; setTextColor(NC.ON_SURFACE); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        background = roundedBg(NC.SURFACE_HIGH, NC.BORDER_VAR, dp(20))
        setPadding(dp(12), dp(10), dp(12), dp(10))
        setOnClickListener { onClick() }
    }

    private fun dangerButton(label: String, onClick: () -> Unit): TextView = TextView(this).apply {
        text = label; textSize = 13f; setTextColor(NC.ERROR); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        background = roundedBg(Color.parseColor("#3d1212"), Color.parseColor("#93000a"), dp(20))
        setPadding(dp(12), dp(10), dp(12), dp(10))
        setOnClickListener { onClick() }
    }

    private fun statWidget(label: String, value: String, color: Int): LinearLayout {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        val labelTv = TextView(this).apply { text = label; textSize = 12f; setTextColor(color); typeface = Typeface.MONOSPACE; gravity = Gravity.CENTER }
        val valueTv = TextView(this).apply { text = value; textSize = 20f; setTextColor(NC.ON_SURFACE); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER }
        col.addView(labelTv); col.addView(valueTv); return col
    }

    private fun projectCard(name: String, path: String, time: String): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = roundedBg(NC.SURFACE, NC.BORDER, dp(12)); setPadding(dp(14))
            val icon = TextView(this@MainActivity).apply { text = "\uD83D\uDCC1"; textSize = 24f; setPadding(0, 0, dp(12), 0) }
            val details = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f) }
            val nameRow = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val nameTv = TextView(this@MainActivity).apply { text = name; textSize = 16f; setTextColor(NC.PRIMARY); typeface = Typeface.DEFAULT_BOLD; layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f) }
            val timeTv = TextView(this@MainActivity).apply { text = time; textSize = 11f; setTextColor(NC.ON_SURF_VAR); typeface = Typeface.MONOSPACE }
            nameRow.addView(nameTv); nameRow.addView(timeTv)
            val pathRow = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(4), 0, 0) }
            val gitBadge = textBadge("Git", NC.LOGBG, NC.SECONDARY)
            val pathTv = TextView(this@MainActivity).apply { text = "  $path"; textSize = 11f; setTextColor(NC.OUTLINE); typeface = Typeface.MONOSPACE; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
            pathRow.addView(gitBadge); pathRow.addView(pathTv)
            details.addView(nameRow); details.addView(pathRow)
            addView(icon); addView(details)
        }
    }

    private fun pulseView(v: View) {
        val anim = ObjectAnimator.ofFloat(v, "alpha", 0.4f, 1f).apply {
            duration = 1500; repeatMode = ValueAnimator.REVERSE; repeatCount = ValueAnimator.INFINITE
        }
        anim.start()
    }

    private fun iconTextBtn(icon: String, onClick: () -> Unit) = TextView(this).apply {
        text = icon; textSize = 18f; setTextColor(NC.ON_SURF_VAR)
        setPadding(dp(8), dp(4), dp(8), dp(4))
        setOnClickListener { onClick() }
    }
}
