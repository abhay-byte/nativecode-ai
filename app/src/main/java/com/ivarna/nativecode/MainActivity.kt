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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.setPadding
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.ContextMenu
import android.view.MenuItem
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    // ── Views ──────────────────────────────────────────────────────────────────
    private lateinit var rootLayout: LinearLayout
    private lateinit var contentFrame: FrameLayout
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var projectBottomNavigation: BottomNavigationView

    // Persistent Header (Unified across all pages)
    private lateinit var unifiedHeader: LinearLayout

    private lateinit var drawerLayout: androidx.drawerlayout.widget.DrawerLayout
    private lateinit var sidebarLayout: LinearLayout
    private lateinit var sidebarScrollView: ScrollView
    private lateinit var sidebarListContainer: LinearLayout

    private lateinit var menuBtn: TextView
    private lateinit var displayBtn: ImageView
    private lateinit var addTerminalBtn: ImageView
    private lateinit var backBtn: TextView
    private lateinit var scriptInstallBackBtn: TextView

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

    private lateinit var projectCreateScrollView: ScrollView
    private lateinit var projectCreateLayout: LinearLayout
    private lateinit var projectsListScrollView: ScrollView
    private lateinit var projectsListLayout: LinearLayout

    // Home dashboard widgets
    private lateinit var homeStatusDot: View
    private lateinit var homeStatusLabel: TextView
    private lateinit var homeContainerLabel: TextView
    private lateinit var startGuiBtn: TextView
    private lateinit var stopGuiBtn: TextView

    private val executor = Executors.newCachedThreadPool()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val ID_HOME     = 1
    private val ID_FILES    = 2
    private val ID_TERMINAL = 3
    private val ID_GIT      = 4
    private val ID_SETTINGS = 5
    private val ID_SCRIPTS  = 6
    private val ID_SCRIPT_INSTALL = 7
    private val ID_PROJECT_CREATE = 8
    private val ID_PROJECTS_LIST = 9
    private val ID_PROJECT_WORKSPACE = 10
    private val ID_PROJECT_SETTINGS = 11
    private val ID_PROJECT_DIR_TREE = 12
    private val ID_PROJECT_GIT_DIFF = 13
    
    private var fileViewerBackPage = ID_FILES
    private var diffViewerBackPage = ID_GIT

    private var isScriptRunning = false
    private var resourceMonitorRunnable: Runnable? = null
    private var lastCpuTotal = 0L
    private var lastCpuIdle = 0L

    private var activeProjectName = "MyAndroidApp"
    private var activeProjectPath = "/home/flux/projects/MyAndroidApp"
    private lateinit var fileExplorerTitleTv: TextView
    private lateinit var projectIconInput: EditText
    private lateinit var projectNameInput: EditText
    private lateinit var projectPathInput: EditText
    private lateinit var projectGithubInput: EditText
    private lateinit var projectCreateBtn: TextView
    private lateinit var recentProjectsContainer: LinearLayout

    private var activeIconInput: EditText? = null

    private val projectIconPickerLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { selectedUri ->
            try {
                val filename = "icon_" + System.currentTimeMillis() + ".png"
                val destFile = File(filesDir, filename)
                contentResolver.openInputStream(selectedUri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                activeIconInput?.setText(destFile.absolutePath)
            } catch (e: Exception) {
                // Try taking persistable permission as fallback
                try {
                    contentResolver.takePersistableUriPermission(selectedUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (pe: Exception) {}
                activeIconInput?.setText(selectedUri.toString())
            }
        }
    }

    private lateinit var projectSettingsScrollView: ScrollView
    private lateinit var projectSettingsLayout: LinearLayout
    private lateinit var configIconInput: EditText

    private lateinit var projectDirTreeScrollView: ScrollView
    private lateinit var projectDirTreeLayout: LinearLayout
    private lateinit var projectGitDiffScrollView: ScrollView
    private lateinit var projectGitDiffLayout: LinearLayout

    private val expandedFolders = HashSet<String>()

    private lateinit var projectWorkspaceLayout: LinearLayout
    private lateinit var workspaceTerminalView: TerminalView
    private lateinit var workspaceTabBar: LinearLayout
    private lateinit var workspaceTabBarScroll: HorizontalScrollView
    private lateinit var workspaceHubLayout: LinearLayout
    private lateinit var workspaceDirTreeLayout: LinearLayout
    private lateinit var workspaceGitDiffLayout: LinearLayout
    private lateinit var workspaceProjectNameTv: TextView
    private lateinit var workspaceProjectIconIv: ImageView

    private val workspaceSessions = ArrayList<TerminalSession>()
    private val workspaceTabNames = ArrayList<String>()
    private var activeWorkspaceTabIndex = -1

    private lateinit var workspaceTerminalContainer: LinearLayout
    private lateinit var workspaceKeyboardToolbar: LinearLayout
    private lateinit var workspaceAttachBar: LinearLayout

    private val projectSessionsMap = HashMap<String, ArrayList<TerminalSession>>()
    private val projectTabNamesMap = HashMap<String, ArrayList<String>>()
    private val projectActiveTabIndexMap = HashMap<String, Int>()

    // History tracking for back button support
    private val pageStack = java.util.Stack<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable fullscreen/immersive mode
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

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
            unifiedHeader.setPadding(dp(16), dp(12), dp(16), dp(12))
            
            // Adjust sidebar layout padding to respect notification/status bar
            sidebarLayout.setPadding(dp(16), dp(16), dp(16), dp(16))
            
            if (::projectWorkspaceLayout.isInitialized) {
                val workspaceTopBar = projectWorkspaceLayout.getChildAt(0) as? LinearLayout
                workspaceTopBar?.setPadding(dp(12), dp(10), dp(12), dp(10))
            }
            if (::terminalWorkspaceLayout.isInitialized) {
                val terminalTopBar = terminalWorkspaceLayout.getChildAt(0) as? LinearLayout
                terminalTopBar?.setPadding(dp(12), dp(10), dp(12), dp(10))
            }
            if (::scriptInstallLayout.isInitialized) {
                val installTopBar = scriptInstallLayout.getChildAt(0) as? LinearLayout
                installTopBar?.setPadding(dp(12), dp(10), dp(12), dp(10))
            }
            if (::projectCreateScrollView.isInitialized) {
                projectCreateScrollView.setPadding(0, 0, 0, 0)
            }
            if (::projectSettingsLayout.isInitialized) {
                val settingsTopBar = projectSettingsLayout.getChildAt(0) as? LinearLayout
                settingsTopBar?.setPadding(dp(12), dp(10), dp(12), dp(10))
            }
            if (::projectDirTreeLayout.isInitialized) {
                val dirTreeTopBar = projectDirTreeLayout.getChildAt(0) as? LinearLayout
                dirTreeTopBar?.setPadding(dp(12), dp(10), dp(12), dp(10))
            }
            if (::projectGitDiffLayout.isInitialized) {
                val gitDiffTopBar = projectGitDiffLayout.getChildAt(0) as? LinearLayout
                gitDiffTopBar?.setPadding(dp(12), dp(10), dp(12), dp(10))
            }
            
            val isTerminalPage = pageStack.isNotEmpty() && pageStack.peek() == ID_TERMINAL
            val isProjectTerminalPage = pageStack.isNotEmpty() && pageStack.peek() == ID_PROJECT_WORKSPACE
            if (isTerminalPage) {
                if (ime.bottom > 0) {
                    bottomNavigation.visibility = View.GONE
                } else {
                    bottomNavigation.visibility = View.VISIBLE
                }
            } else if (isProjectTerminalPage) {
                if (ime.bottom > 0) {
                    projectBottomNavigation.visibility = View.GONE
                } else {
                    projectBottomNavigation.visibility = View.VISIBLE
                }
            }
            
            val isProjectTab = pageStack.isNotEmpty() && (
                pageStack.peek() == ID_PROJECT_WORKSPACE ||
                pageStack.peek() == ID_PROJECT_SETTINGS ||
                pageStack.peek() == ID_PROJECT_DIR_TREE ||
                pageStack.peek() == ID_PROJECT_GIT_DIFF
            )
            val currentBottomNav = if (isProjectTab) projectBottomNavigation else bottomNavigation
            val otherBottomNav = if (isProjectTab) bottomNavigation else projectBottomNavigation

            if (currentBottomNav.visibility == View.VISIBLE) {
                currentBottomNav.setPadding(0, 0, 0, bars.bottom)
                contentFrame.setPadding(0, 0, 0, 0)
            } else {
                currentBottomNav.setPadding(0, 0, 0, 0)
                val bottomPadding = if (ime.bottom > 0) ime.bottom else bars.bottom
                contentFrame.setPadding(0, 0, 0, bottomPadding)
            }
            otherBottomNav.setPadding(0, 0, 0, 0)
            insets
        }
        ViewCompat.requestApplyInsets(drawerLayout)

        // Setup back callback for predictive/system back
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                onBackPressed()
            }
        })

        bottomNavigation.setOnItemSelectedListener { item ->
            val pageId = item.itemId
            if (pageStack.isEmpty() || pageStack.peek() != pageId) {
                pageStack.push(pageId)
            }
            navigateToPage(pageId)
            true
        }

        projectBottomNavigation.setOnItemSelectedListener { item ->
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
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.let {
            val projPath = it.getStringExtra("PROJECT_PATH")
            if (projPath != null) {
                activeProjectPath = projPath
            }
            val projName = it.getStringExtra("PROJECT_NAME")
            if (projName != null) {
                activeProjectName = projName
            }
            val targetPage = it.getIntExtra("EXTRA_TARGET_PAGE", -1)
            if (targetPage != -1) {
                navigateToPage(targetPage)
            }
        }
    }

    private fun updateAppTerminalService() {
        val count = sessionsList.size
        val intent = Intent(this, AppTerminalService::class.java).apply {
            putExtra("SESSION_COUNT", count)
        }
        if (count > 0) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } else {
            stopService(intent)
        }
    }

    private fun updateProjectTerminalService() {
        val count = workspaceSessions.size
        val intent = Intent(this, ProjectTerminalService::class.java).apply {
            putExtra("SESSION_COUNT", count)
            putExtra("PROJECT_NAME", activeProjectName)
            putExtra("PROJECT_PATH", activeProjectPath)
        }
        if (count > 0) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } else {
            stopService(intent)
        }
    }

    // ── Unified Navigation & Switcher ────────────────────────────────────────

    private fun navigateToPage(id: Int) {
        navigateToPage(id, true)
    }

    private fun navigateToPage(id: Int, pushToStack: Boolean) {
        if (pushToStack) {
            if (pageStack.isEmpty() || pageStack.peek() != id) {
                pageStack.push(id)
            }
        }

        // Hide keyboard when switching pages
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(drawerLayout.windowToken, 0)
        window.decorView.clearFocus()

        if (::terminalView.isInitialized) {
            terminalView.isFocusable = false
            terminalView.isFocusableInTouchMode = false
            terminalView.clearFocus()
        }
        if (::workspaceTerminalView.isInitialized) {
            workspaceTerminalView.isFocusable = false
            workspaceTerminalView.isFocusableInTouchMode = false
            workspaceTerminalView.clearFocus()
        }

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
        if (::projectCreateScrollView.isInitialized) {
            projectCreateScrollView.visibility = View.GONE
        }
        if (::projectsListScrollView.isInitialized) {
            projectsListScrollView.visibility = View.GONE
        }
        if (::projectWorkspaceLayout.isInitialized) {
            projectWorkspaceLayout.visibility = View.GONE
        }
        if (::projectSettingsScrollView.isInitialized) {
            projectSettingsScrollView.visibility = View.GONE
        }
        if (::projectDirTreeScrollView.isInitialized) {
            projectDirTreeScrollView.visibility = View.GONE
        }
        if (::projectGitDiffScrollView.isInitialized) {
            projectGitDiffScrollView.visibility = View.GONE
        }

        if (id == ID_TERMINAL) {
            unifiedHeader.visibility = View.GONE
            bottomNavigation.visibility = View.VISIBLE
            projectBottomNavigation.visibility = View.GONE
            if (::drawerLayout.isInitialized) drawerLayout.setDrawerLockMode(androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_UNLOCKED)
        } else if (id == ID_SCRIPT_INSTALL) {
            unifiedHeader.visibility = View.GONE
            bottomNavigation.menu.findItem(bottomNavigation.selectedItemId)?.isChecked = false
            bottomNavigation.visibility = View.GONE
            projectBottomNavigation.visibility = View.GONE
            if (::drawerLayout.isInitialized) drawerLayout.setDrawerLockMode(androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        } else if (id == ID_PROJECT_CREATE) {
            unifiedHeader.visibility = View.GONE
            bottomNavigation.visibility = View.GONE
            projectBottomNavigation.visibility = View.GONE
            if (::drawerLayout.isInitialized) drawerLayout.setDrawerLockMode(androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        } else if (id == ID_PROJECT_WORKSPACE || id == ID_PROJECT_SETTINGS || id == ID_PROJECT_DIR_TREE || id == ID_PROJECT_GIT_DIFF) {
            unifiedHeader.visibility = View.GONE
            bottomNavigation.visibility = View.GONE
            projectBottomNavigation.visibility = View.VISIBLE
            if (::drawerLayout.isInitialized) drawerLayout.setDrawerLockMode(androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        } else {
            unifiedHeader.visibility = View.VISIBLE
            bottomNavigation.visibility = View.VISIBLE
            projectBottomNavigation.visibility = View.GONE
            if (::drawerLayout.isInitialized) drawerLayout.setDrawerLockMode(androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        }
        
        // Hide back button on top level pages if stack is 1 or empty
        if (::backBtn.isInitialized) {
            backBtn.visibility = if (pageStack.size > 1) View.VISIBLE else View.GONE
        }
        
        ViewCompat.requestApplyInsets(drawerLayout)

        when (id) {
            ID_HOME -> {
                homeScrollView.visibility = View.VISIBLE
                homeScrollView.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                populateRecentProjects()
            }
            ID_FILES -> {
                fileExplorerScrollView.visibility = View.VISIBLE
                if (::fileExplorerTitleTv.isInitialized) {
                    fileExplorerTitleTv.text = "\uD83D\uDCC2 $activeProjectName"
                }
            }
            ID_TERMINAL -> {
                terminalWorkspaceLayout.visibility = View.VISIBLE
                terminalView.isFocusable = true
                terminalView.isFocusableInTouchMode = true
                terminalView.requestFocus()
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
            ID_PROJECT_CREATE -> {
                if (::projectCreateScrollView.isInitialized) {
                    projectCreateScrollView.visibility = View.VISIBLE
                }
            }
            ID_PROJECTS_LIST -> {
                if (::projectsListScrollView.isInitialized) {
                    projectsListScrollView.visibility = View.VISIBLE
                    populateProjectsList()
                }
            }
            ID_PROJECT_WORKSPACE -> {
                if (::projectWorkspaceLayout.isInitialized) {
                    projectWorkspaceLayout.visibility = View.VISIBLE
                    workspaceTerminalView.isFocusable = true
                    workspaceTerminalView.isFocusableInTouchMode = true
                    openProjectWorkspace()
                }
            }
            ID_PROJECT_SETTINGS -> {
                if (::projectSettingsScrollView.isInitialized) {
                    projectSettingsScrollView.visibility = View.VISIBLE
                    openProjectSettings()
                }
            }
            ID_PROJECT_DIR_TREE -> {
                if (::projectDirTreeScrollView.isInitialized) {
                    projectDirTreeScrollView.visibility = View.VISIBLE
                    openProjectDirTree()
                }
            }
            ID_PROJECT_GIT_DIFF -> {
                if (::projectGitDiffScrollView.isInitialized) {
                    projectGitDiffScrollView.visibility = View.VISIBLE
                    openProjectGitDiff()
                }
            }
        }
        selectBottomNavItem(id)
    }

    private fun selectBottomNavItem(id: Int) {
        if (::bottomNavigation.isInitialized) {
            val hasItem = try {
                bottomNavigation.menu.findItem(id) != null
            } catch (e: Exception) {
                false
            }
            if (hasItem) {
                bottomNavigation.setOnItemSelectedListener(null)
                bottomNavigation.selectedItemId = id
                bottomNavigation.setOnItemSelectedListener { item ->
                    val pageId = item.itemId
                    if (pageStack.isEmpty() || pageStack.peek() != pageId) {
                        pageStack.push(pageId)
                    }
                    navigateToPage(pageId)
                    true
                }
            }
        }
        if (::projectBottomNavigation.isInitialized) {
            val hasItem = try {
                projectBottomNavigation.menu.findItem(id) != null
            } catch (e: Exception) {
                false
            }
            if (hasItem) {
                projectBottomNavigation.setOnItemSelectedListener(null)
                projectBottomNavigation.selectedItemId = id
                projectBottomNavigation.setOnItemSelectedListener { item ->
                    val pageId = item.itemId
                    if (pageStack.isEmpty() || pageStack.peek() != pageId) {
                        pageStack.push(pageId)
                    }
                    navigateToPage(pageId)
                    true
                }
            }
        }
    }

    override fun onBackPressed() {
        if (::projectSettingsScrollView.isInitialized && projectSettingsScrollView.visibility == View.VISIBLE) {
            navigateToPage(ID_PROJECT_WORKSPACE)
            return
        }

        if (::projectDirTreeScrollView.isInitialized && projectDirTreeScrollView.visibility == View.VISIBLE) {
            navigateToPage(ID_PROJECT_WORKSPACE)
            return
        }

        if (::projectGitDiffScrollView.isInitialized && projectGitDiffScrollView.visibility == View.VISIBLE) {
            navigateToPage(ID_PROJECT_WORKSPACE)
            return
        }

        if (::projectWorkspaceLayout.isInitialized && projectWorkspaceLayout.visibility == View.VISIBLE) {
            navigateToPage(ID_PROJECTS_LIST)
            return
        }

        if (::projectCreateScrollView.isInitialized && projectCreateScrollView.visibility == View.VISIBLE) {
            navigateToPage(ID_PROJECTS_LIST)
            return
        }

        if (fileViewerScrollView.visibility == View.VISIBLE || diffViewerScrollView.visibility == View.VISIBLE) {
            val prevPage = if (fileViewerScrollView.visibility == View.VISIBLE) fileViewerBackPage else diffViewerBackPage
            navigateToPage(prevPage)
            return
        }

        if (::scriptInstallLayout.isInitialized && scriptInstallLayout.visibility == View.VISIBLE) {
            if (isScriptRunning) {
                return
            } else {
                navigateToPage(ID_SCRIPTS)
                return
            }
        }

        if (::scriptsScrollView.isInitialized && scriptsScrollView.visibility == View.VISIBLE) {
            navigateToPage(ID_SETTINGS)
            return
        }

        if (pageStack.size > 1) {
            pageStack.pop() // remove current
            val prev = pageStack.peek()
            navigateToPage(prev, false)
        } else {
            showExitConfirmDialog()
        }
    }

    private fun showExitConfirmDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Exit NativeCode?")
            .setMessage("Terminal sessions will keep running in the background.")
            .setPositiveButton("Exit") { _, _ ->
                super.onBackPressed()
            }
            .setNegativeButton("Cancel", null)
            .show()
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
        displayBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_display)
            setColorFilter(NC.SECONDARY)
            setPadding(dp(6), dp(6), dp(6), dp(6))
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply {
                rightMargin = dp(8)
            }
            setOnClickListener {
                val intent = Intent(this@MainActivity, com.termux.x11.MainActivity::class.java)
                startActivity(intent)
            }
        }
        val terminalBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_terminal)
            setColorFilter(NC.PRIMARY)
            setPadding(dp(6), dp(6), dp(6), dp(6))
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply {
                rightMargin = dp(8)
            }
            setOnClickListener {
                if (pageStack.isEmpty() || pageStack.peek() != ID_TERMINAL) {
                    pageStack.push(ID_TERMINAL)
                }
                navigateToPage(ID_TERMINAL)
            }
        }
        addTerminalBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_add)
            setColorFilter(NC.SECONDARY)
            setPadding(dp(6), dp(6), dp(6), dp(6))
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
            setOnClickListener {
                createNewTerminalSession()
                if (pageStack.isEmpty() || pageStack.peek() != ID_TERMINAL) {
                    pageStack.push(ID_TERMINAL)
                }
                navigateToPage(ID_TERMINAL)
            }
        }

        unifiedHeader.addView(logoView)
        unifiedHeader.addView(spacer)
        unifiedHeader.addView(displayBtn)
        rootLayout.addView(unifiedHeader)

        contentFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f)
        }

        val states = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_checked)
        )
        val colors = intArrayOf(
            NC.PRIMARY,
            NC.OUTLINE
        )
        val tintList = android.content.res.ColorStateList(states, colors)

        bottomNavigation = BottomNavigationView(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
            setBackgroundColor(Color.parseColor("#120F16"))
            itemIconTintList = tintList
            itemTextColor = tintList
            menu.add(Menu.NONE, ID_HOME,          Menu.NONE, "Home").setIcon(R.drawable.ic_home)
            menu.add(Menu.NONE, ID_PROJECTS_LIST, Menu.NONE, "Projects").setIcon(R.drawable.ic_folder)
            menu.add(Menu.NONE, ID_TERMINAL,      Menu.NONE, "Terminal").setIcon(R.drawable.ic_terminal)
            menu.add(Menu.NONE, ID_SETTINGS,      Menu.NONE, "Settings").setIcon(R.drawable.ic_settings)
        }

        projectBottomNavigation = BottomNavigationView(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
            setBackgroundColor(Color.parseColor("#120F16"))
            itemIconTintList = tintList
            itemTextColor = tintList
            menu.add(Menu.NONE, ID_PROJECT_WORKSPACE, Menu.NONE, "Workspace").setIcon(R.drawable.ic_home)
            menu.add(Menu.NONE, ID_PROJECT_DIR_TREE, Menu.NONE, "Directory").setIcon(R.drawable.ic_folder)
            menu.add(Menu.NONE, ID_PROJECT_GIT_DIFF, Menu.NONE, "Diff").setIcon(R.drawable.ic_git)
            menu.add(Menu.NONE, ID_PROJECT_SETTINGS, Menu.NONE, "Settings").setIcon(R.drawable.ic_settings)
        }

        rootLayout.addView(contentFrame)
        rootLayout.addView(bottomNavigation)
        rootLayout.addView(projectBottomNavigation)

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
        buildProjectCreateLayout()
        buildProjectsListLayout()
        buildProjectWorkspaceLayout()
        buildProjectSettingsLayout()
        buildProjectDirTreeLayout()
        buildProjectGitDiffLayout()

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
        contentFrame.addView(projectCreateScrollView)
        contentFrame.addView(projectsListScrollView)
        contentFrame.addView(projectWorkspaceLayout)
        contentFrame.addView(projectSettingsScrollView)
        contentFrame.addView(projectDirTreeScrollView)
        contentFrame.addView(projectGitDiffScrollView)

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
            updateAppTerminalService()
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
        updateAppTerminalService()
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

            val terminalIcon = ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.ic_terminal)
                setColorFilter(if (isSelected) NC.SECONDARY else NC.OUTLINE)
                layoutParams = LinearLayout.LayoutParams(dp(16), dp(16)).apply {
                    rightMargin = dp(8)
                }
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

        val homeHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(16) }
        }
        val homeTitle = TextView(this).apply {
            text = "Dashboard"
            textSize = 20f
            setTextColor(NC.ON_SURFACE)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        val newProjBtn = TextView(this).apply {
            text = "＋ New Project"
            textSize = 13f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = roundedBg(NC.PRIMARY_CON, NC.PRIMARY_CON, dp(14))
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setOnClickListener {
                if (pageStack.isEmpty() || pageStack.peek() != ID_PROJECT_CREATE) {
                    pageStack.push(ID_PROJECT_CREATE)
                }
                navigateToPage(ID_PROJECT_CREATE)
            }
        }
        homeHeader.addView(homeTitle)
        homeHeader.addView(newProjBtn)
        homeLayout.addView(homeHeader)

        // Resources
        homeLayout.addView(buildResourcesCard())
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
            fileExplorerTitleTv = this
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

        // Dedicated Terminal Top Bar
        val terminalTopBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#15121b"))
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        val menuTerminalBtn = TextView(this).apply {
            text = " ☰ "
            textSize = 18f
            setTextColor(NC.ON_SURFACE)
            setPadding(dp(6), dp(6), dp(6), dp(6))
            setOnClickListener {
                if (drawerLayout.isDrawerOpen(sidebarLayout)) {
                    drawerLayout.closeDrawer(sidebarLayout)
                } else {
                    drawerLayout.openDrawer(sidebarLayout)
                }
            }
        }
        val titleTerminalTv = TextView(this).apply {
            text = "Terminal Workspace"
            textSize = 16f
            setTextColor(NC.ON_SURFACE)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        val addTerminalWorkspaceBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_add)
            setColorFilter(NC.SECONDARY)
            setPadding(dp(6), dp(6), dp(6), dp(6))
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
            setOnClickListener {
                createNewTerminalSession()
            }
        }
        terminalTopBar.addView(menuTerminalBtn)
        terminalTopBar.addView(titleTerminalTv)
        terminalTopBar.addView(addTerminalWorkspaceBtn)
        terminalWorkspaceLayout.addView(terminalTopBar)

        terminalViewContainer = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f)
        }
        terminalView = TerminalView(this, null).apply {
            isFocusable = false; isFocusableInTouchMode = false
        }
        registerForContextMenu(terminalView)
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

        // Graphical Desktop card
        settingsHubLayout.addView(buildGuiLaunchCard())
        settingsHubLayout.addView(spacer(12))

        // Environment card
        settingsHubLayout.addView(buildEnvironmentCard())
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

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#15121b"))
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        scriptInstallBackBtn = TextView(this).apply {
            text = " ◀ "
            textSize = 18f
            setTextColor(NC.ON_SURFACE)
            setPadding(dp(6), dp(6), dp(6), dp(6))
            setOnClickListener {
                onBackPressed()
            }
        }
        val titleTv = TextView(this).apply {
            text = "Script Installation"
            textSize = 16f
            setTextColor(NC.ON_SURFACE)
            typeface = Typeface.DEFAULT_BOLD
        }
        topBar.addView(scriptInstallBackBtn)
        topBar.addView(titleTv)
        scriptInstallLayout.addView(topBar)

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
                         if (::scriptInstallBackBtn.isInitialized) {
                             scriptInstallBackBtn.visibility = View.VISIBLE
                         }
                     }
                }
            }
            override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("", text))
            }
            override fun onPasteTextFromClipboard(session: TerminalSession) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(this@MainActivity)?.toString() ?: return
                session.write(text)
            }
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
         if (::scriptInstallBackBtn.isInitialized) {
             scriptInstallBackBtn.visibility = View.GONE
         }

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

    private fun showFileViewer(name: String, backPage: Int = ID_FILES) {
        fileViewerBackPage = backPage
        if (backPage == ID_PROJECT_DIR_TREE) {
            unifiedHeader.visibility = View.GONE
            projectBottomNavigation.visibility = View.VISIBLE
            bottomNavigation.visibility = View.GONE
        } else {
            unifiedHeader.visibility = View.VISIBLE
            projectBottomNavigation.visibility = View.GONE
            bottomNavigation.visibility = View.VISIBLE
        }
        homeScrollView.visibility = View.GONE
        fileExplorerScrollView.visibility = View.GONE
        terminalWorkspaceLayout.visibility = View.GONE
        gitOperationsScrollView.visibility = View.GONE
        settingsHubScrollView.visibility = View.GONE
        diffViewerScrollView.visibility = View.GONE

        fileViewerScrollView.visibility = View.VISIBLE
    }

    private fun showDiffViewer(name: String, backPage: Int = ID_GIT) {
        diffViewerBackPage = backPage
        if (backPage == ID_PROJECT_GIT_DIFF) {
            unifiedHeader.visibility = View.GONE
            projectBottomNavigation.visibility = View.VISIBLE
            bottomNavigation.visibility = View.GONE
        } else {
            unifiedHeader.visibility = View.VISIBLE
            projectBottomNavigation.visibility = View.GONE
            bottomNavigation.visibility = View.VISIBLE
        }
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
        homeContainerLabel = TextView(this).apply { text = "PRoot (Debian Trixie)"; textSize = 13f; setTextColor(NC.ON_SURF_VAR); typeface = Typeface.MONOSPACE }
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

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        startGuiBtn = secondaryButton("\u25B6 Start XFCE") {
            startGui()
            startGuiBtn.visibility = View.GONE
            stopGuiBtn.visibility = View.VISIBLE
            stopGuiBtn.isEnabled = true
            stopGuiBtn.alpha = 1f
            if (::displayBtn.isInitialized) {
                displayBtn.visibility = View.VISIBLE
            }
        }
        startGuiBtn.isEnabled = false; startGuiBtn.alpha = 0.5f
        startGuiBtn.layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)

        stopGuiBtn = dangerButton("\u25A0 Stop XFCE") {
            stopGui()
            stopGuiBtn.isEnabled = false
            stopGuiBtn.alpha = 0.5f
            if (::displayBtn.isInitialized) {
                displayBtn.visibility = View.GONE
            }
            mainHandler.postDelayed({
                stopGuiBtn.visibility = View.GONE
                startGuiBtn.visibility = View.VISIBLE
                startGuiBtn.isEnabled = true
                startGuiBtn.alpha = 1f
            }, 5000)
        }
        stopGuiBtn.isEnabled = false; stopGuiBtn.alpha = 0.5f
        stopGuiBtn.visibility = View.GONE
        stopGuiBtn.layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)

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
        statsRow.addView(statWidget("CPU", "0%", NC.SECONDARY))
        statsRow.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })
        statsRow.addView(statWidget("MEM", "0.0 GB", NC.PRIMARY))
        statsRow.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })
        statsRow.addView(statWidget("DISK", "0%", NC.TERTIARY))
        card.addView(statsRow)

        val cpuTv = card.findViewWithTag<TextView>("CPU")
        val memTv = card.findViewWithTag<TextView>("MEM")
        val diskTv = card.findViewWithTag<TextView>("DISK")
        if (cpuTv != null && memTv != null && diskTv != null) {
            startResourceMonitoring(cpuTv, memTv, diskTv)
        }

        return card
    }

    private fun buildRecentProjectsSection(): View {
        val section = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, dp(12))
        }
        val sectionTitle = TextView(this).apply { text = "Recent Workspaces"; textSize = 16f; setTextColor(NC.ON_SURFACE); typeface = Typeface.DEFAULT_BOLD; layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f) }
        val viewAll = TextView(this).apply { 
            text = "View All"
            textSize = 13f
            setTextColor(NC.SECONDARY)
            setOnClickListener {
                if (pageStack.isEmpty() || pageStack.peek() != ID_PROJECTS_LIST) {
                    pageStack.push(ID_PROJECTS_LIST)
                }
                navigateToPage(ID_PROJECTS_LIST)
            }
        }
        headerRow.addView(sectionTitle); headerRow.addView(viewAll); section.addView(headerRow)

        recentProjectsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        section.addView(recentProjectsContainer)
        populateRecentProjects()

        return section
    }

    private fun populateRecentProjects() {
        if (!::recentProjectsContainer.isInitialized) return
        recentProjectsContainer.removeAllViews()
        val list = getProjects()
        if (list.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "No workspaces found."
                textSize = 13f
                setTextColor(NC.ON_SURF_VAR)
                setPadding(0, dp(8), 0, dp(8))
            }
            recentProjectsContainer.addView(emptyTv)
        } else {
            val recent = list.take(3)
            for (p in recent) {
                val card = projectCard(p.name, p.path, "Open Workspace", p.icon)
                card.setOnClickListener {
                    activeProjectName = p.name
                    activeProjectPath = p.path
                    Toast.makeText(this, "Project opened: ${p.name}", Toast.LENGTH_SHORT).show()
                    if (pageStack.isEmpty() || pageStack.peek() != ID_PROJECT_WORKSPACE) {
                        pageStack.push(ID_PROJECT_WORKSPACE)
                    }
                    navigateToPage(ID_PROJECT_WORKSPACE)
                }
                recentProjectsContainer.addView(card)
                recentProjectsContainer.addView(spacer(8))
            }
        }
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
        card.addView(infoRow("OS Version", "Debian Trixie"))
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
            if (::homeStatusLabel.isInitialized) {
                homeStatusLabel.text = "Ready"
            }
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
            override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("", text))
            }
            override fun onPasteTextFromClipboard(session: TerminalSession) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(this@MainActivity)?.toString() ?: return
                session.write(text)
            }
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
    }

    override fun onDestroy() {
        super.onDestroy()
        resourceMonitorRunnable?.let { mainHandler.removeCallbacks(it) }
        stopService(Intent(this, BackgroundService::class.java))
        stopService(Intent(this, AppTerminalService::class.java))
        stopService(Intent(this, ProjectTerminalService::class.java))
        for (session in sessionsList) {
            session.finishIfRunning()
        }
        scriptInstallSession?.finishIfRunning()
    }

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)
        if (v === terminalView || v === workspaceTerminalView) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            menu.add(Menu.NONE, 101, Menu.NONE, "Paste").isEnabled = clipboard.hasPrimaryClip()
        }
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val tv = if (terminalView.hasFocus()) terminalView else workspaceTerminalView
        return when (item.itemId) {
            101 -> {
                tv.getCurrentSession()?.onPasteTextFromClipboard()
                true
            }
            else -> super.onContextItemSelected(item)
        }
    }

    override fun onContextMenuClosed(menu: Menu) {
        super.onContextMenuClosed(menu)
        for (tv in arrayOf(terminalView, workspaceTerminalView)) {
            try {
                val method = tv.javaClass.getMethod("onContextMenuClosed", Menu::class.java)
                method.invoke(tv, menu)
            } catch (e: Exception) {
                try {
                    val method = tv.javaClass.getMethod("unsetStoredSelectedText")
                    method.invoke(tv)
                } catch (ex: Exception) {}
            }
        }
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
        val valueTv = TextView(this).apply { tag = label; text = value; textSize = 20f; setTextColor(NC.ON_SURFACE); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER }
        col.addView(labelTv); col.addView(valueTv); return col
    }

    private fun projectCard(name: String, path: String, time: String, iconStr: String = ""): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = roundedBg(NC.SURFACE, NC.BORDER, dp(12)); setPadding(dp(14))
            
            val iconContainer = FrameLayout(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply { rightMargin = dp(12) }
            }
            
            if (iconStr.isEmpty()) {
                val tv = TextView(this@MainActivity).apply { text = "\uD83D\uDCC1"; textSize = 24f; gravity = Gravity.CENTER }
                iconContainer.addView(tv)
            } else if (iconStr.length <= 4 && !iconStr.startsWith("/") && !iconStr.startsWith("http")) {
                val tv = TextView(this@MainActivity).apply { text = iconStr; textSize = 24f; gravity = Gravity.CENTER }
                iconContainer.addView(tv)
            } else {
                val iv = ImageView(this@MainActivity).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }
                iconContainer.addView(iv)
                executor.execute {
                    try {
                        val bitmap = when {
                            iconStr.startsWith("content://") -> {
                                contentResolver.openInputStream(android.net.Uri.parse(iconStr)).use {
                                    android.graphics.BitmapFactory.decodeStream(it)
                                }
                            }
                            iconStr.startsWith("http://") || iconStr.startsWith("https://") -> {
                                java.net.URL(iconStr).openStream().use {
                                    android.graphics.BitmapFactory.decodeStream(it)
                                }
                            }
                            else -> {
                                android.graphics.BitmapFactory.decodeFile(iconStr)
                            }
                        }
                        if (bitmap != null) {
                            mainHandler.post {
                                iv.setImageBitmap(bitmap)
                            }
                        } else {
                            mainHandler.post {
                                iconContainer.removeAllViews()
                                val tv = TextView(this@MainActivity).apply { text = "\uD83D\uDCC1"; textSize = 24f; gravity = Gravity.CENTER }
                                iconContainer.addView(tv)
                            }
                        }
                    } catch (e: Exception) {
                        mainHandler.post {
                            iconContainer.removeAllViews()
                            val tv = TextView(this@MainActivity).apply { text = "\uD83D\uDCC1"; textSize = 24f; gravity = Gravity.CENTER }
                            iconContainer.addView(tv)
                        }
                    }
                }
            }

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
            
            addView(iconContainer)
            addView(details)
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

    private fun startResourceMonitoring(cpuTv: TextView, memTv: TextView, diskTv: TextView) {
        val monitorRunnable = object : Runnable {
            override fun run() {
                executor.execute {
                    val cpuUsage = readCpuUsage()
                    val memUsage = readMemUsage()
                    val diskUsage = readDiskUsage()
                    mainHandler.post {
                        cpuTv.text = "$cpuUsage%"
                        memTv.text = memUsage
                        diskTv.text = "$diskUsage%"
                    }
                }
                mainHandler.postDelayed(this, 2000)
            }
        }
        resourceMonitorRunnable = monitorRunnable
        mainHandler.post(monitorRunnable)
    }

    private fun readCpuUsage(): Int {
        try {
            val file = File("/proc/stat")
            if (!file.exists()) return 0
            val line = file.bufferedReader().use { it.readLine() } ?: return 0
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size < 8 || parts[0] != "cpu") return 0
            val user = parts[1].toLong()
            val nice = parts[2].toLong()
            val system = parts[3].toLong()
            val idle = parts[4].toLong()
            val iowait = parts[5].toLong()
            val irq = parts[6].toLong()
            val softirq = parts[7].toLong()

            val currentIdle = idle + iowait
            val currentActive = user + nice + system + irq + softirq
            val currentTotal = currentIdle + currentActive

            val totalDiff = currentTotal - lastCpuTotal
            val idleDiff = currentIdle - lastCpuIdle

            lastCpuTotal = currentTotal
            lastCpuIdle = currentIdle

            if (totalDiff <= 0) return 0
            return ((totalDiff - idleDiff) * 100 / totalDiff).toInt().coerceIn(0, 100)
        } catch (e: Exception) {
            return 0
        }
    }

    private fun readMemUsage(): String {
        try {
            val file = File("/proc/meminfo")
            if (!file.exists()) return "0.0 GB"
            var totalKb = 0L
            var availKb = 0L
            file.forEachLine { line ->
                if (line.startsWith("MemTotal:")) {
                    val parts = line.split(Regex("\\s+"))
                    if (parts.size >= 2) totalKb = parts[1].toLong()
                } else if (line.startsWith("MemAvailable:")) {
                    val parts = line.split(Regex("\\s+"))
                    if (parts.size >= 2) availKb = parts[1].toLong()
                }
            }
            if (totalKb <= 0L) return "0.0 GB"
            val usedKb = totalKb - availKb
            val usedGb = usedKb.toDouble() / (1024 * 1024)
            return String.format(java.util.Locale.US, "%.1f GB", usedGb)
        } catch (e: Exception) {
            return "0.0 GB"
        }
    }

    private fun readDiskUsage(): Int {
        try {
            val stat = android.os.StatFs(filesDir.absolutePath)
            val totalBytes = stat.blockCountLong * stat.blockSizeLong
            val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
            if (totalBytes <= 0) return 0
            val usedBytes = totalBytes - availableBytes
            return (usedBytes * 100 / totalBytes).toInt().coerceIn(0, 100)
        } catch (e: Exception) {
            return 0
        }
    }

    // ── Project Management & Storage ──────────────────────────────────────────

    data class Project(val name: String, val icon: String, val path: String)

    private fun getProjects(): List<Project> {
        val prefs = getSharedPreferences("nativecode_prefs", MODE_PRIVATE)
        val data = prefs.getString("projects_json", null) ?: return defaultProjects()
        val list = ArrayList<Project>()
        try {
            val arr = org.json.JSONArray(data)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(Project(
                    obj.getString("name"),
                    obj.getString("icon"),
                    obj.getString("path")
                ))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    private fun defaultProjects(): List<Project> {
        return emptyList()
    }

    private fun saveProjects(list: List<Project>) {
        val prefs = getSharedPreferences("nativecode_prefs", MODE_PRIVATE)
        val arr = org.json.JSONArray()
        for (p in list) {
            val obj = org.json.JSONObject()
            obj.put("name", p.name)
            obj.put("icon", p.icon)
            obj.put("path", p.path)
            arr.put(obj)
        }
        prefs.edit().putString("projects_json", arr.toString()).apply()
    }

    private fun buildProjectCreateLayout() {
        projectCreateScrollView = ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
            visibility = View.GONE
        }
        projectCreateLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16))
        }
        projectCreateScrollView.addView(projectCreateLayout)

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#15121b"))
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        val backBtn = TextView(this).apply {
            text = " ◀ "
            textSize = 18f
            setTextColor(NC.ON_SURFACE)
            setPadding(dp(6), dp(6), dp(6), dp(6))
            setOnClickListener {
                onBackPressed()
            }
        }
        val titleTv = TextView(this).apply {
            text = "Create / Import Project"
            textSize = 16f
            setTextColor(NC.ON_SURFACE)
            typeface = Typeface.DEFAULT_BOLD
        }
        topBar.addView(backBtn)
        topBar.addView(titleTv)
        projectCreateLayout.addView(topBar)
        projectCreateLayout.addView(spacer(16))

        projectCreateLayout.addView(TextView(this).apply { text = "Project Name"; setTextColor(NC.ON_SURF_VAR); textSize = 13f; setPadding(0, 0, 0, dp(4)) })
        projectNameInput = EditText(this).apply {
            hint = "My Awesome App"
            setHintTextColor(NC.OUTLINE)
            setTextColor(NC.ON_SURFACE)
            background = roundedBg(NC.SURFACE, NC.BORDER, dp(6))
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(16) }
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val name = s.toString().trim().replace(" ", "_")
                    if (name.isNotEmpty()) {
                        projectPathInput.setText("/home/flux/projects/$name")
                    }
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
        }
        projectCreateLayout.addView(projectNameInput)

        projectCreateLayout.addView(TextView(this).apply { text = "Project Icon (Link, Emoji, or File)"; setTextColor(NC.ON_SURF_VAR); textSize = 13f; setPadding(0, 0, 0, dp(4)) })
        val iconRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(16) }
        }
        projectIconInput = EditText(this).apply {
            hint = "Emoji or image URL/path"
            setHintTextColor(NC.OUTLINE)
            setTextColor(NC.ON_SURFACE)
            background = roundedBg(NC.SURFACE, NC.BORDER, dp(6))
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).apply { rightMargin = dp(8) }
        }
        val chooseIconBtn = secondaryButton("Browse") {
            activeIconInput = projectIconInput
            projectIconPickerLauncher.launch("image/*")
        }
        iconRow.addView(projectIconInput)
        iconRow.addView(chooseIconBtn)
        projectCreateLayout.addView(iconRow)

        projectCreateLayout.addView(TextView(this).apply { text = "Local Path inside Debian Distro"; setTextColor(NC.ON_SURF_VAR); textSize = 13f; setPadding(0, 0, 0, dp(4)) })
        projectPathInput = EditText(this).apply {
            setText("/home/flux/projects/")
            setHintTextColor(NC.OUTLINE)
            setTextColor(NC.ON_SURFACE)
            background = roundedBg(NC.SURFACE, NC.BORDER, dp(6))
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(16) }
        }
        projectCreateLayout.addView(projectPathInput)

        projectCreateLayout.addView(TextView(this).apply { text = "GitHub Repository URL (Optional - Will clone automatically)"; setTextColor(NC.ON_SURF_VAR); textSize = 13f; setPadding(0, 0, 0, dp(4)) })
        projectGithubInput = EditText(this).apply {
            hint = "https://github.com/username/repo.git"
            setHintTextColor(NC.OUTLINE)
            setTextColor(NC.ON_SURFACE)
            background = roundedBg(NC.SURFACE, NC.BORDER, dp(6))
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(24) }
        }
        projectCreateLayout.addView(projectGithubInput)

        projectCreateBtn = primaryButton("Create / Import Project") {
            val name = projectNameInput.text.toString().trim()
            var path = projectPathInput.text.toString().trim()
            val gitUrl = projectGithubInput.text.toString().trim()
            val icon = projectIconInput.text.toString().trim()

            if (name.isEmpty()) {
                Toast.makeText(this, "Please enter a project name", Toast.LENGTH_SHORT).show()
                return@primaryButton
            }

            if (gitUrl.isNotEmpty()) {
                val repoName = gitUrl.substringAfterLast("/").substringBeforeLast(".git")
                path = "/home/flux/projects/$repoName"
                
                Toast.makeText(this, "Cloning repository inside Debian container...", Toast.LENGTH_LONG).show()
                projectCreateBtn.isEnabled = false
                projectCreateBtn.alpha = 0.5f

                executor.execute {
                    val nld = applicationInfo.nativeLibraryDir
                    val bash = File(nld, "libbash.so").absolutePath
                    val gitCmd = "mkdir -p ~/projects && cd ~/projects && git clone $gitUrl"
                    val args = arrayOf(
                        bash, 
                        "-c", 
                        "exec python /data/data/com.ivarna.nativecode/files/usr/bin/proot-distro login debian --shared-tmp --user flux -- bash -c \"$gitCmd\""
                    )
                    val result = runShellCommand(args)
                    mainHandler.post {
                        projectCreateBtn.isEnabled = true
                        projectCreateBtn.alpha = 1f
                        if (result == 0) {
                            addAndOpenProject(name, icon, path)
                        } else {
                            Toast.makeText(this@MainActivity, "Failed to clone repository.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } else {
                if (path.isEmpty()) {
                    Toast.makeText(this, "Please enter a local path", Toast.LENGTH_SHORT).show()
                    return@primaryButton
                }
                addAndOpenProject(name, icon, path)
            }
        }
        projectCreateLayout.addView(projectCreateBtn)
    }

    private fun addAndOpenProject(name: String, icon: String, path: String) {
        val projects = getProjects().toMutableList()
        val existingIndex = projects.indexOfFirst { it.path == path }
        val newProj = Project(name, icon, path)
        if (existingIndex >= 0) {
            projects[existingIndex] = newProj
        } else {
            projects.add(newProj)
        }
        saveProjects(projects)

        activeProjectName = name
        activeProjectPath = path

        Toast.makeText(this, "Project opened: $name", Toast.LENGTH_SHORT).show()

        if (pageStack.isEmpty() || pageStack.peek() != ID_PROJECT_WORKSPACE) {
            pageStack.push(ID_PROJECT_WORKSPACE)
        }
        navigateToPage(ID_PROJECT_WORKSPACE)
    }

    private fun buildProjectsListLayout() {
        projectsListScrollView = ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
            visibility = View.GONE
        }
        projectsListLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16))
        }
        projectsListScrollView.addView(projectsListLayout)
    }

    private fun populateProjectsList() {
        projectsListLayout.removeAllViews()

        val title = TextView(this).apply {
            text = "All Projects"
            textSize = 20f
            setTextColor(NC.PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(16))
        }
        projectsListLayout.addView(title)

        val list = getProjects()
        if (list.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "No projects created yet. Tap '+' at the top to create one!"
                textSize = 14f
                setTextColor(NC.ON_SURF_VAR)
                gravity = Gravity.CENTER
                setPadding(0, dp(24), 0, 0)
            }
            projectsListLayout.addView(emptyTv)
        } else {
            for (p in list) {
                val card = projectCard(p.name, p.path, "Tap to open", p.icon)
                card.setOnClickListener {
                    activeProjectName = p.name
                    activeProjectPath = p.path
                    Toast.makeText(this, "Project opened: ${p.name}", Toast.LENGTH_SHORT).show()
                    if (pageStack.isEmpty() || pageStack.peek() != ID_PROJECT_WORKSPACE) {
                        pageStack.push(ID_PROJECT_WORKSPACE)
                    }
                    navigateToPage(ID_PROJECT_WORKSPACE)
                }
                projectsListLayout.addView(card)
                projectsListLayout.addView(spacer(8))
            }
        }
    }

    private fun getProjectHostFile(): File {
        val rootfs = "/data/data/com.ivarna.nativecode/files/usr/var/lib/proot-distro/containers/debian/rootfs"
        val resolvedPath = if (activeProjectPath.startsWith("/")) {
            rootfs + activeProjectPath
        } else {
            rootfs + "/home/flux/" + activeProjectPath
        }
        return File(resolvedPath)
    }

    private fun refreshWorkspaceDirTree() {
        if (!::workspaceDirTreeLayout.isInitialized) return
        workspaceDirTreeLayout.removeAllViews()
        val projectHostDir = getProjectHostFile()
        if (projectHostDir.exists() && projectHostDir.isDirectory) {
            renderDirectoryTree(projectHostDir, workspaceDirTreeLayout, 0)
        } else {
            val emptyTv = TextView(this).apply {
                text = "Dir not found on host:\n${projectHostDir.absolutePath}\n\nCreate directory to browse."
                textSize = 12f
                setTextColor(NC.ERROR)
            }
            workspaceDirTreeLayout.addView(emptyTv)
        }
    }

    private fun renderDirectoryTree(dir: File, container: LinearLayout, depth: Int) {
        val materialTf = try {
            Typeface.createFromAsset(assets, "fonts/material_icons.ttf")
        } catch (e: Exception) {
            Typeface.DEFAULT
        }
        val files = dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name })) ?: return
        for (file in files) {
            if (file.name.startsWith(".")) continue
            val isExpanded = expandedFolders.contains(file.absolutePath)
            
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16 * depth + 12), dp(8), dp(12), dp(8))
                setOnClickListener {
                    if (file.isDirectory) {
                        if (isExpanded) {
                            expandedFolders.remove(file.absolutePath)
                        } else {
                            expandedFolders.add(file.absolutePath)
                        }
                        refreshWorkspaceDirTree()
                    } else {
                        showFileViewer(file.absolutePath, ID_PROJECT_DIR_TREE)
                    }
                }
            }
            
            val indicatorTv = TextView(this).apply {
                if (file.isDirectory) {
                    typeface = materialTf
                    text = if (isExpanded) "\uE313" else "\uE315"
                    textSize = 16f
                    setTextColor(NC.ON_SURF_VAR)
                    setPadding(0, 0, dp(4), 0)
                } else {
                    text = ""
                    setPadding(0, 0, dp(20), 0)
                }
            }
            
            val iconTv = TextView(this).apply {
                typeface = materialTf
                val icon = when {
                    file.isDirectory -> {
                        if (isExpanded) "\uE2C8" else "\uE2C7" // folder_open or folder
                    }
                    else -> {
                        val name = file.name.lowercase()
                        val ext = name.substringAfterLast('.', "")
                        when {
                            name == "license" || name == "license.txt" || name == "license.md" -> "\uE90F" // gavel
                            name == "readme.md" || name == "readme" || name == "readme.txt" -> "\uE24D" // insert_drive_file
                            name == "todo.md" || name == "todo" || name == "todo.txt" -> "\uE24D"
                            name == "plan.md" || name == "plan" || name == "plan.txt" -> "\uE24D"
                            name == "gradlew" || name == "gradlew.bat" -> "\uEB8E" // terminal
                            name == "build.gradle.kts" || name == "build.gradle" || name == "settings.gradle.kts" || name == "settings.gradle" -> "\uE8B8" // settings
                            ext == "kt" -> "\uE86F" // code
                            ext == "kts" -> "\uE86F"
                            ext == "java" -> "\uE86F"
                            ext == "class" -> "\uE86F"
                            ext == "jar" -> "\uE149" // archive
                            ext == "xml" -> "\uE86F" // code
                            ext == "json" -> "\uE8B8" // settings
                            ext == "yml" || ext == "yaml" -> "\uE8B8" // settings
                            ext == "properties" || ext == "prop" -> "\uE8B8"
                            ext == "pro" -> "\uE8B8"
                            ext == "sh" || ext == "bash" || ext == "zsh" -> "\uEB8E" // terminal
                            ext == "bat" || ext == "cmd" -> "\uEB8E" // terminal
                            ext in listOf("png", "jpg", "jpeg", "gif", "webp", "ico", "svg") -> "\uE3F4" // image
                            ext == "md" -> "\uE24D" // insert_drive_file
                            ext == "txt" -> "\uE24D"
                            ext == "pdf" -> "\uE24D"
                            ext in listOf("zip", "tar", "gz", "rar", "7z") -> "\uE149" // archive
                            ext == "apk" -> "\uE859" // android
                            else -> "\uE24D"
                        }
                    }
                }
                text = icon
                textSize = 16f
                setPadding(0, 0, dp(8), 0)
                setTextColor(if (file.isDirectory) NC.PRIMARY else NC.ON_SURF_VAR)
            }
            
            val nameTv = TextView(this).apply {
                text = file.name
                textSize = 14f
                setTextColor(if (file.isDirectory) NC.ON_SURFACE else NC.PRIMARY)
                if (file.isDirectory) {
                    typeface = Typeface.DEFAULT_BOLD
                } else {
                    typeface = Typeface.MONOSPACE
                }
            }
            
            row.addView(indicatorTv)
            row.addView(iconTv)
            row.addView(nameTv)
            container.addView(row)
            
            if (file.isDirectory && isExpanded) {
                renderDirectoryTree(file, container, depth + 1)
            }
        }
    }

    private fun refreshGitDiffTree() {
        if (!::workspaceGitDiffLayout.isInitialized) return
        workspaceGitDiffLayout.removeAllViews()
        val loadingTv = TextView(this).apply { text = "Loading diffs..."; setTextColor(NC.ON_SURF_VAR); setPadding(dp(12), dp(12), dp(12), dp(12)) }
        workspaceGitDiffLayout.addView(loadingTv)
        
        executor.execute {
            val nld = applicationInfo.nativeLibraryDir
            val bash = File(nld, "libbash.so").absolutePath
            val gitCmd = "cd $activeProjectPath && git status --porcelain"
            val pb = ProcessBuilder(bash, "-c", "exec python /data/data/com.ivarna.nativecode/files/usr/bin/proot-distro login debian --shared-tmp --user flux -- zsh -c \"$gitCmd\"")
            val env = pb.environment()
            env["PATH"] = "$nld:/data/data/com.ivarna.nativecode/files/usr/bin:/system/bin"
            env["PD_PROOT_BIN"] = File(nld, "libproot.so").absolutePath
            env["PROOT_LOADER"] = File(nld, "libloader.so").absolutePath
            env["LD_LIBRARY_PATH"] = "/data/data/com.ivarna.nativecode/files/usr/lib"
            env["HOME"] = "/data/data/com.ivarna.nativecode/files/home"
            pb.redirectErrorStream(true)
            try {
                val proc = pb.start()
                val lines = ArrayList<String>()
                proc.inputStream.bufferedReader().useLines { seq ->
                    seq.forEach { lines.add(it) }
                }
                proc.waitFor()
                
                mainHandler.post {
                    workspaceGitDiffLayout.removeAllViews()
                    if (lines.isEmpty()) {
                        val noChanges = TextView(this@MainActivity).apply { text = "No changes detected"; setTextColor(NC.ON_SURF_VAR); setPadding(dp(12), dp(12), dp(12), dp(12)) }
                        workspaceGitDiffLayout.addView(noChanges)
                    } else {
                        for (line in lines) {
                            if (line.trim().isEmpty()) continue
                            val status = line.take(2)
                            val file = line.substring(3)
                            val row = LinearLayout(this@MainActivity).apply {
                                orientation = LinearLayout.HORIZONTAL
                                gravity = Gravity.CENTER_VERTICAL
                                setPadding(dp(12), dp(8), dp(12), dp(8))
                                setOnClickListener {
                                    showDiffViewer(file, ID_PROJECT_GIT_DIFF)
                                }
                            }
                            val statusBadge = textBadge(status, if (status.contains("M")) NC.PRIMARY_CON else NC.SECONDARY, NC.ON_SURFACE)
                            val fileTv = TextView(this@MainActivity).apply {
                                text = "  $file"
                                textSize = 12f
                                setTextColor(NC.ON_SURFACE)
                                typeface = Typeface.MONOSPACE
                                maxLines = 1
                                ellipsize = android.text.TextUtils.TruncateAt.END
                            }
                            row.addView(statusBadge)
                            row.addView(fileTv)
                            workspaceGitDiffLayout.addView(row)
                        }
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    workspaceGitDiffLayout.removeAllViews()
                    val errorTv = TextView(this@MainActivity).apply { text = "Error running git status"; setTextColor(NC.ERROR); setPadding(dp(12), dp(12), dp(12), dp(12)) }
                    workspaceGitDiffLayout.addView(errorTv)
                }
            }
        }
    }

    private fun openProjectWorkspace() {
        workspaceProjectNameTv.text = activeProjectName
        
        val iconStr = getProjects().find { it.path == activeProjectPath }?.icon ?: ""
        if (iconStr.isEmpty()) {
            workspaceProjectIconIv.visibility = View.GONE
        } else {
            workspaceProjectIconIv.visibility = View.VISIBLE
            executor.execute {
                try {
                    val bitmap = when {
                        iconStr.startsWith("content://") -> {
                            contentResolver.openInputStream(android.net.Uri.parse(iconStr)).use {
                                android.graphics.BitmapFactory.decodeStream(it)
                            }
                        }
                        iconStr.startsWith("http://") || iconStr.startsWith("https://") -> {
                            java.net.URL(iconStr).openStream().use {
                                android.graphics.BitmapFactory.decodeStream(it)
                            }
                        }
                        else -> {
                            android.graphics.BitmapFactory.decodeFile(iconStr)
                        }
                    }
                    mainHandler.post {
                        if (bitmap != null) {
                            workspaceProjectIconIv.setImageBitmap(bitmap)
                        } else {
                            workspaceProjectIconIv.visibility = View.GONE
                        }
                    }
                } catch (e: Exception) {
                    mainHandler.post { workspaceProjectIconIv.visibility = View.GONE }
                }
            }
        }
        
        refreshWorkspaceDirTree()
        refreshGitDiffTree()
        
        val sessions = projectSessionsMap.getOrPut(activeProjectPath) { ArrayList() }
        val tabNames = projectTabNamesMap.getOrPut(activeProjectPath) { ArrayList() }
        val activeIdx = projectActiveTabIndexMap.get(activeProjectPath) ?: -1
        
        workspaceSessions.clear()
        workspaceSessions.addAll(sessions)
        workspaceTabNames.clear()
        workspaceTabNames.addAll(tabNames)
        activeWorkspaceTabIndex = activeIdx
        
        rebuildWorkspaceTabs()
        if (activeWorkspaceTabIndex >= 0 && activeWorkspaceTabIndex < workspaceSessions.size) {
            switchWorkspaceTab(activeWorkspaceTabIndex)
        }
        updateProjectTerminalService()
    }

    private fun createWorkspaceTerminalTab(type: String) {
        if (workspaceSessions.size >= 10) {
            Toast.makeText(this, "Maximum 10 tabs allowed", Toast.LENGTH_SHORT).show()
            return
        }

        val nld = applicationInfo.nativeLibraryDir
        val shell = File(nld, "libbash.so").absolutePath
        val cwd = File(filesDir, "home").absolutePath
        
        val toolCmd = when (type) {
            "opencode" -> "export NVM_DIR=/home/flux/.nvm && [ -s /home/flux/.nvm/nvm.sh ] && . /home/flux/.nvm/nvm.sh && cd $activeProjectPath && exec opencode"
            "codex" -> "export NVM_DIR=/home/flux/.nvm && [ -s /home/flux/.nvm/nvm.sh ] && . /home/flux/.nvm/nvm.sh && cd $activeProjectPath && exec codex"
            else -> "cd $activeProjectPath && exec zsh"
        }

        val args = arrayOf(shell, "-c", "exec python /data/data/com.ivarna.nativecode/files/usr/bin/proot-distro login debian --shared-tmp --user flux -- zsh -c \"$toolCmd\"")
        val envMap = HashMap(System.getenv())
        envMap["PATH"] = "$nld:/data/data/com.ivarna.nativecode/files/usr/bin:/system/bin"
        envMap["PD_PROOT_BIN"] = File(nld, "libproot.so").absolutePath
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

        val sessionClient = object : TerminalSessionClient {
            override fun onTextChanged(session: TerminalSession) {
                if (workspaceSessions.indexOf(session) == activeWorkspaceTabIndex) {
                    workspaceTerminalView.onScreenUpdated()
                }
            }
            override fun onTitleChanged(session: TerminalSession) {}
            override fun onSessionFinished(session: TerminalSession) {
                mainHandler.post {
                    closeWorkspaceTab(workspaceSessions.indexOf(session))
                }
            }
            override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("", text))
            }
            override fun onPasteTextFromClipboard(session: TerminalSession) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(this@MainActivity)?.toString() ?: return
                session.write(text)
            }
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

        val session = TerminalSession(shell, cwd, args, env, 10000, sessionClient)
        workspaceSessions.add(session)
        workspaceTabNames.add(type)
        activeWorkspaceTabIndex = workspaceSessions.size - 1
        
        saveActiveProjectSessions()
        rebuildWorkspaceTabs()
        switchWorkspaceTab(activeWorkspaceTabIndex)
        updateProjectTerminalService()
    }

    private fun saveActiveProjectSessions() {
        projectSessionsMap[activeProjectPath] = ArrayList(workspaceSessions)
        projectTabNamesMap[activeProjectPath] = ArrayList(workspaceTabNames)
        projectActiveTabIndexMap[activeProjectPath] = activeWorkspaceTabIndex
    }

    private fun switchWorkspaceTab(index: Int) {
        if (index < 0 || index >= workspaceSessions.size) return
        activeWorkspaceTabIndex = index
        val session = workspaceSessions[index]
        workspaceTerminalView.attachSession(session)
        workspaceTerminalView.onScreenUpdated()
        workspaceTerminalView.requestFocus()
        saveActiveProjectSessions()
        rebuildWorkspaceTabs()
    }

    private fun closeWorkspaceTab(index: Int) {
        if (index < 0 || index >= workspaceSessions.size) return
        val session = workspaceSessions[index]
        session.finishIfRunning()
        workspaceSessions.removeAt(index)
        workspaceTabNames.removeAt(index)

        if (workspaceSessions.isEmpty()) {
            activeWorkspaceTabIndex = -1
        } else {
            if (activeWorkspaceTabIndex >= workspaceSessions.size) {
                activeWorkspaceTabIndex = workspaceSessions.size - 1
            }
            switchWorkspaceTab(activeWorkspaceTabIndex)
        }
        saveActiveProjectSessions()
        rebuildWorkspaceTabs()
        updateProjectTerminalService()
    }

    private fun rebuildWorkspaceTabs() {
        workspaceTabBar.removeAllViews()
        
        if (workspaceSessions.isEmpty()) {
            workspaceTabBarScroll.visibility = View.GONE
            if (::workspaceTerminalContainer.isInitialized) {
                workspaceTerminalContainer.visibility = View.GONE
            }
            workspaceHubLayout.visibility = View.VISIBLE
            return
        }
        
        workspaceTabBarScroll.visibility = View.VISIBLE
        if (::workspaceTerminalContainer.isInitialized) {
            workspaceTerminalContainer.visibility = View.VISIBLE
        }
        workspaceHubLayout.visibility = View.GONE

        for (i in 0 until workspaceSessions.size) {
            val isSelected = (i == activeWorkspaceTabIndex)
            val tabName = workspaceTabNames[i]
            
            val tab = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(6), dp(12), dp(6))
                background = roundedBg(
                    if (isSelected) NC.SURFACE_HIGH else Color.TRANSPARENT,
                    if (isSelected) NC.BORDER else Color.TRANSPARENT,
                    dp(6)
                )
                layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).apply {
                    rightMargin = dp(4)
                }
                setOnClickListener {
                    switchWorkspaceTab(i)
                }
            }
            
            val titleTv = TextView(this).apply {
                text = "${i + 1}. $tabName"
                textSize = 12f
                setTextColor(if (isSelected) NC.PRIMARY else NC.ON_SURF_VAR)
                typeface = Typeface.MONOSPACE
            }
            
            val closeTv = TextView(this).apply {
                text = " ✕"
                textSize = 12f
                setTextColor(NC.ERROR)
                setPadding(dp(4), dp(2), dp(4), dp(2))
                setOnClickListener {
                    closeWorkspaceTab(i)
                }
            }
            
            tab.addView(titleTv)
            tab.addView(closeTv)
            workspaceTabBar.addView(tab)
        }
        
        if (workspaceSessions.size < 10) {
            val addTabBtn = ImageView(this).apply {
                setImageResource(R.drawable.ic_add)
                setColorFilter(NC.SECONDARY)
                setPadding(dp(6), dp(6), dp(6), dp(6))
                layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply {
                    leftMargin = dp(8)
                    gravity = Gravity.CENTER_VERTICAL
                }
                setOnClickListener {
                    val popup = PopupMenu(this@MainActivity, this)
                    popup.menu.add("Debian Shell")
                    popup.menu.add("opencode AI")
                    popup.menu.add("codex Search")
                    popup.setOnMenuItemClickListener { item ->
                        val type = when(item.title) {
                            "opencode AI" -> "opencode"
                            "codex Search" -> "codex"
                            else -> "shell"
                        }
                        createWorkspaceTerminalTab(type)
                        true
                    }
                    popup.show()
                }
            }
            workspaceTabBar.addView(addTabBtn)
        }
    }

    private fun buildProjectWorkspaceLayout() {
        projectWorkspaceLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
            visibility = View.GONE
            setBackgroundColor(NC.BG)
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#15121b"))
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }

        val backWorkspaceBtn = TextView(this).apply {
            text = " ◀ "
            textSize = 18f
            setTextColor(NC.ON_SURFACE)
            setPadding(dp(6), dp(6), dp(6), dp(6))
            setOnClickListener {
                onBackPressed()
            }
        }

        workspaceProjectIconIv = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(24), dp(24)).apply { rightMargin = dp(8) }
            scaleType = ImageView.ScaleType.CENTER_CROP
        }

        workspaceProjectNameTv = TextView(this).apply {
            text = ""
            textSize = 16f
            setTextColor(NC.ON_SURFACE)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }

        topBar.addView(backWorkspaceBtn)
        topBar.addView(workspaceProjectIconIv)
        topBar.addView(workspaceProjectNameTv)
        projectWorkspaceLayout.addView(topBar)

        workspaceTabBarScroll = HorizontalScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
            setBackgroundColor(Color.parseColor("#1d1a24"))
            setPadding(dp(8), dp(4), dp(8), dp(4))
            visibility = View.GONE
        }
        workspaceTabBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(WRAP, MATCH)
        }
        workspaceTabBarScroll.addView(workspaceTabBar)
        projectWorkspaceLayout.addView(workspaceTabBarScroll)

        val mainArea = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f)
        }

        val centerFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, MATCH, 1f)
        }

        workspaceTerminalContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
            visibility = View.GONE
        }

        val termViewContainer = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f)
        }

         workspaceTerminalView = TerminalView(this, null).apply {
            isFocusable = false
            isFocusableInTouchMode = false
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
        }
        registerForContextMenu(workspaceTerminalView)
        workspaceTerminalView.setTextSize(40)
        try {
            val tf = Typeface.createFromAsset(assets, "fonts/font.ttf")
            workspaceTerminalView.setTypeface(tf)
        } catch (e: Exception) {}
        
        val workspaceViewClient = object : TerminalViewClient {
            override fun onScale(scale: Float): Float = scale
            override fun onSingleTapUp(e: MotionEvent) {
                workspaceTerminalView.requestFocus()
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(workspaceTerminalView, InputMethodManager.SHOW_IMPLICIT)
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
        workspaceTerminalView.setTerminalViewClient(workspaceViewClient)
        termViewContainer.addView(workspaceTerminalView)
        workspaceTerminalContainer.addView(termViewContainer)

        workspaceKeyboardToolbar = buildWorkspaceKeyboardToolbar()
        workspaceTerminalContainer.addView(workspaceKeyboardToolbar)

        workspaceAttachBar = buildWorkspaceAttachBar()
        workspaceTerminalContainer.addView(workspaceAttachBar)

        centerFrame.addView(workspaceTerminalContainer)

        workspaceHubLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24))
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
        }
        
        val hubTitle = TextView(this).apply {
            text = "Select a Development Environment"
            textSize = 16f
            setTextColor(NC.ON_SURFACE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(16))
        }
        workspaceHubLayout.addView(hubTitle)

        for ((title, type, desc, color) in listOf(
            StatusCardData("opencode", "opencode", "Claude AI Agent Harness", NC.PRIMARY_CON),
            StatusCardData("codex", "codex", "Codex AI Search & Autocomplete", NC.SECONDARY),
            StatusCardData("shell", "shell", "Linux Guest Debian Shell", NC.TERTIARY)
        )) {
            val pillCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = roundedBg(NC.SURFACE, NC.BORDER, dp(10))
                setPadding(dp(14))
                layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(12) }
                setOnClickListener {
                    createWorkspaceTerminalTab(type)
                }
            }
            val cardHeader = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val nameTv = TextView(this).apply { text = title.uppercase(); textSize = 15f; setTextColor(color); typeface = Typeface.DEFAULT_BOLD; layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f) }
            val goTv = TextView(this).apply { text = "LAUNCH \u2794"; textSize = 11f; setTextColor(NC.ON_SURF_VAR); typeface = Typeface.MONOSPACE }
            cardHeader.addView(nameTv); cardHeader.addView(goTv); pillCard.addView(cardHeader)

            val descTv = TextView(this).apply { text = desc; textSize = 12f; setTextColor(NC.ON_SURF_VAR); setPadding(0, dp(4), 0, 0) }
            pillCard.addView(descTv)
            
            workspaceHubLayout.addView(pillCard)
        }
        centerFrame.addView(workspaceHubLayout)
        mainArea.addView(centerFrame)

        projectWorkspaceLayout.addView(mainArea)
    }

     private data class StatusCardData(val title: String, val type: String, val desc: String, val color: Int)

    private fun buildProjectSettingsLayout() {
        projectSettingsScrollView = ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
            visibility = View.GONE
            setBackgroundColor(NC.BG)
        }
        projectSettingsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16))
        }
        projectSettingsScrollView.addView(projectSettingsLayout)
    }

    private fun createProjectSubpageTopBar(): LinearLayout {
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#15121b"))
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        val backBtn = TextView(this).apply {
            text = " ◀ "
            textSize = 18f
            setTextColor(NC.ON_SURFACE)
            setPadding(dp(6), dp(6), dp(6), dp(6))
            setOnClickListener {
                onBackPressed()
            }
        }
        val workspaceIconIv = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(24), dp(24)).apply { rightMargin = dp(8) }
            scaleType = ImageView.ScaleType.CENTER_CROP
            visibility = View.GONE
        }
        val titleTv = TextView(this).apply {
            text = activeProjectName
            textSize = 16f
            setTextColor(NC.ON_SURFACE)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        val iconStr = getProjects().find { it.path == activeProjectPath }?.icon ?: ""
        if (iconStr.isNotEmpty()) {
            executor.execute {
                try {
                    val bitmap = when {
                        iconStr.startsWith("content://") -> {
                            contentResolver.openInputStream(android.net.Uri.parse(iconStr)).use {
                                android.graphics.BitmapFactory.decodeStream(it)
                            }
                        }
                        iconStr.startsWith("http://") || iconStr.startsWith("https://") -> {
                            java.net.URL(iconStr).openStream().use {
                                android.graphics.BitmapFactory.decodeStream(it)
                            }
                        }
                        else -> {
                            android.graphics.BitmapFactory.decodeFile(iconStr)
                        }
                    }
                    mainHandler.post {
                        if (bitmap != null) {
                            workspaceIconIv.setImageBitmap(bitmap)
                            workspaceIconIv.visibility = View.VISIBLE
                        }
                    }
                } catch (e: Exception) {
                    mainHandler.post { workspaceIconIv.visibility = View.GONE }
                }
            }
        }
        topBar.addView(backBtn)
        topBar.addView(workspaceIconIv)
        topBar.addView(titleTv)
        return topBar
    }

    private fun openProjectSettings() {
        projectSettingsLayout.removeAllViews()
        projectSettingsLayout.addView(createProjectSubpageTopBar())
        projectSettingsLayout.addView(spacer(16))

        val header = TextView(this).apply {
            text = "Configure $activeProjectName"
            textSize = 20f
            setTextColor(NC.PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(16))
        }
        projectSettingsLayout.addView(header)

        val previewContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(120), dp(120)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(24)
            }
            background = roundedBg(NC.SURFACE, NC.BORDER, dp(60))
            clipToOutline = true
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        }
        projectSettingsLayout.addView(previewContainer)

        fun updatePreview(iconStr: String) {
            previewContainer.removeAllViews()
            if (iconStr.isEmpty()) {
                val tv = TextView(this@MainActivity).apply { text = "📁"; textSize = 48f; gravity = Gravity.CENTER }
                previewContainer.addView(tv)
            } else if (iconStr.length <= 4 && !iconStr.startsWith("/") && !iconStr.startsWith("http")) {
                val tv = TextView(this@MainActivity).apply { text = iconStr; textSize = 48f; gravity = Gravity.CENTER }
                previewContainer.addView(tv)
            } else {
                val iv = ImageView(this@MainActivity).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }
                previewContainer.addView(iv)
                executor.execute {
                    try {
                        val bitmap = when {
                            iconStr.startsWith("content://") -> {
                                contentResolver.openInputStream(android.net.Uri.parse(iconStr)).use {
                                    android.graphics.BitmapFactory.decodeStream(it)
                                }
                            }
                            iconStr.startsWith("http://") || iconStr.startsWith("https://") -> {
                                java.net.URL(iconStr).openStream().use {
                                    android.graphics.BitmapFactory.decodeStream(it)
                                }
                            }
                            else -> {
                                android.graphics.BitmapFactory.decodeFile(iconStr)
                            }
                        }
                        mainHandler.post {
                            if (bitmap != null) iv.setImageBitmap(bitmap)
                            else {
                                previewContainer.removeAllViews()
                                val tv = TextView(this@MainActivity).apply { text = "📁"; textSize = 48f; gravity = Gravity.CENTER }
                                previewContainer.addView(tv)
                            }
                        }
                    } catch (e: Exception) {
                        mainHandler.post {
                            previewContainer.removeAllViews()
                            val tv = TextView(this@MainActivity).apply { text = "📁"; textSize = 48f; gravity = Gravity.CENTER }
                            previewContainer.addView(tv)
                        }
                    }
                }
            }
        }

        val proj = getProjects().find { it.path == activeProjectPath }
        val currentIcon = proj?.icon ?: ""
        updatePreview(currentIcon)

        configIconInput = EditText(this).apply {
            setText(currentIcon)
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    updatePreview(s.toString().trim())
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
        }
        activeIconInput = configIconInput
        
        val chooseIconBtn = secondaryButton("Browse") {
            activeIconInput = configIconInput
            projectIconPickerLauncher.launch("image/*")
        }.apply {
            layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(32)
            }
        }
        projectSettingsLayout.addView(chooseIconBtn)

        val saveBtn = primaryButton("Save Configuration") {
            val newIcon = configIconInput.text.toString().trim()
            val list = getProjects().toMutableList()
            val idx = list.indexOfFirst { it.path == activeProjectPath }
            if (idx >= 0) {
                val oldProj = list[idx]
                list[idx] = Project(oldProj.name, newIcon, oldProj.path)
                saveProjects(list)
                Toast.makeText(this, "Configuration saved successfully!", Toast.LENGTH_SHORT).show()
                onBackPressed()
            }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply {
                bottomMargin = dp(16)
            }
        }
        projectSettingsLayout.addView(saveBtn)

        projectSettingsLayout.addView(spacer(16))
        val removeBtn = TextView(this).apply {
            text = "Remove Project"
            textSize = 15f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = roundedBg(Color.parseColor("#ba1a1a"), Color.parseColor("#ba1a1a"), dp(24))
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setOnClickListener {
                val list = getProjects().toMutableList()
                val idx = list.indexOfFirst { it.path == activeProjectPath }
                if (idx >= 0) {
                    list.removeAt(idx)
                    saveProjects(list)
                    Toast.makeText(this@MainActivity, "Project removed.", Toast.LENGTH_SHORT).show()
                    activeProjectName = ""
                    activeProjectPath = ""
                    while (pageStack.isNotEmpty() && (
                        pageStack.peek() == ID_PROJECT_WORKSPACE ||
                        pageStack.peek() == ID_PROJECT_SETTINGS ||
                        pageStack.peek() == ID_PROJECT_DIR_TREE ||
                        pageStack.peek() == ID_PROJECT_GIT_DIFF
                    )) {
                        pageStack.pop()
                    }
                    if (pageStack.isEmpty()) {
                        pageStack.push(ID_HOME)
                    }
                    val nextPage = pageStack.peek()
                    navigateToPage(nextPage)
                }
            }
        }
        projectSettingsLayout.addView(removeBtn)
    }

    private fun buildWorkspaceKeyboardToolbar(): LinearLayout {
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
                    if (activeWorkspaceTabIndex >= 0 && activeWorkspaceTabIndex < workspaceSessions.size) {
                        workspaceSessions[activeWorkspaceTabIndex].write(text)
                    }
                }
            }
            inner.addView(btn)
        }
        scroll.addView(inner)
        bar.addView(scroll)
        return bar
    }

    private fun buildWorkspaceAttachBar(): LinearLayout {
        val attachBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; setBackgroundColor(NC.BG); setPadding(dp(12), dp(10), dp(12), dp(12))
        }
        val attachBtn = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            background = roundedBg(NC.SURFACE, NC.BORDER_VAR, dp(8)); setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        val attachTv = TextView(this).apply {
            text = "\uD83D\uDCCE  Add Context (Ingest Image/File)"; textSize = 12f; setTextColor(NC.ON_SURF_VAR); typeface = Typeface.MONOSPACE
        }
        attachBtn.addView(attachTv); attachBar.addView(attachBtn); return attachBar
    }

    private fun buildProjectDirTreeLayout() {
        projectDirTreeScrollView = ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
            visibility = View.GONE
            setBackgroundColor(NC.BG)
        }
        projectDirTreeLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        projectDirTreeScrollView.addView(projectDirTreeLayout)
    }

    private fun openProjectDirTree() {
        projectDirTreeLayout.removeAllViews()
        projectDirTreeLayout.addView(createProjectSubpageTopBar())
        
        workspaceDirTreeLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8))
        }
        projectDirTreeLayout.addView(workspaceDirTreeLayout)
        refreshWorkspaceDirTree()
    }

    private fun buildProjectGitDiffLayout() {
        projectGitDiffScrollView = ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
            visibility = View.GONE
            setBackgroundColor(NC.BG)
        }
        projectGitDiffLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        projectGitDiffScrollView.addView(projectGitDiffLayout)
    }

    private fun openProjectGitDiff() {
        projectGitDiffLayout.removeAllViews()
        projectGitDiffLayout.addView(createProjectSubpageTopBar())
        
        workspaceGitDiffLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8))
        }
        projectGitDiffLayout.addView(workspaceGitDiffLayout)
        refreshGitDiffTree()
    }
}
