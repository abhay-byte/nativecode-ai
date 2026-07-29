package com.ivarna.nativecode

import android.animation.ObjectAnimator
import android.system.Os
import android.system.OsConstants
import java.io.InputStream
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.PixelFormat
import android.graphics.ColorFilter
import android.graphics.Typeface
import java.util.Locale
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import android.net.Uri
import android.view.KeyEvent
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File
import com.ivarna.nativecode.terminal.*
import java.io.FileOutputStream
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    // ── Views ──────────────────────────────────────────────────────────────────
    private lateinit var rootLayout: LinearLayout
    private lateinit var contentFrame: FrameLayout
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var projectBottomNavigation: BottomNavigationView
    private lateinit var mainContentLayout: LinearLayout
    private lateinit var sideNavContainer: FrameLayout
    private lateinit var bottomNavContainer: FrameLayout
    private lateinit var globalNavRail: com.google.android.material.navigationrail.NavigationRailView
    private lateinit var projectNavRail: com.google.android.material.navigationrail.NavigationRailView


    // Persistent Header (Unified across all pages)
    private lateinit var unifiedHeader: LinearLayout

    private lateinit var drawerLayout: androidx.drawerlayout.widget.DrawerLayout
    private lateinit var sidebarLayout: LinearLayout
    private lateinit var sidebarScrollView: ScrollView
    private lateinit var sidebarListContainer: LinearLayout

    private lateinit var menuBtn: ImageView
    private lateinit var displayBtn: ImageView
    private lateinit var addTerminalBtn: ImageView
    private lateinit var backBtn: ImageView
    private lateinit var scriptInstallBackBtn: ImageView
    private lateinit var scriptInstallTitleTv: TextView

    private val sessionsList = ArrayList<TerminalSession>()
    private val terminalSessionTypes = ArrayList<String>()
    private lateinit var terminalToolSelectorScrollView: ScrollView
    private lateinit var toggleExtraKeysBtn: ImageView
    private var activeSessionIndex = -1

    private var termFontSize = 40
    private var workspaceFontSize = 40
    private var scriptFontSize = 40
    private var showExtraKeys = true
    private val MIN_FONT_SIZE = 10
    private val MAX_FONT_SIZE = 72

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
    private var cachedProjectIconPath: String? = null
    private var cachedProjectIconBitmap: android.graphics.Bitmap? = null
    private lateinit var fileViewerRootContainer: LinearLayout
    private lateinit var fileViewerTopBar: LinearLayout
    private lateinit var projectDirTreeTopBar: LinearLayout
    private lateinit var projectSettingsTopBar: LinearLayout
    private lateinit var projectGitDiffTopBar: LinearLayout
    private lateinit var fileViewerScrollView: ScrollView
    private lateinit var fileViewerContainer: LinearLayout
    private var dirSearchQuery: String = ""
    private lateinit var diffViewerRootContainer: LinearLayout
    private lateinit var diffViewerTopBar: LinearLayout
    private lateinit var diffViewerScrollView: ScrollView
    private lateinit var diffViewerContainer: LinearLayout
    private lateinit var scriptsScrollView: ScrollView
    private lateinit var scriptsLayout: LinearLayout
    /** Repairs page: host | guest | chroot */
    private var repairsSelectedTab: String = "host"
    private var repairsRootOk: Boolean? = null
    private var repairsListContainer: LinearLayout? = null
    private var repairsTabHostBtn: TextView? = null
    private var repairsTabGuestBtn: TextView? = null
    private var repairsTabChrootBtn: TextView? = null
    private var repairsRootBadge: TextView? = null
    private lateinit var prootSettingsScrollView: ScrollView
    private lateinit var chrootSettingsScrollView: ScrollView

    private lateinit var scriptInstallLayout: LinearLayout
    private lateinit var scriptInstallViewContainer: FrameLayout
    private lateinit var scriptInstallTerminalView: TerminalView
    private var scriptInstallSession: TerminalSession? = null

    private lateinit var projectCreateContainer: LinearLayout
    private lateinit var projectCreateScrollView: ScrollView
    private lateinit var projectCreateLayout: LinearLayout
    private var projectCreateSelectedMethod: String = "proot"
    private var projectCreateProotChip: TextView? = null
    private var projectCreateChrootChip: TextView? = null
    private lateinit var projectsListContainer: FrameLayout
    private lateinit var projectsListScrollView: ScrollView
    private lateinit var projectsListLayout: LinearLayout

    // Home dashboard widgets
    private lateinit var homeStatusDot: View
    private lateinit var homeStatusLabel: TextView
    private lateinit var homeContainerLabel: TextView
    private lateinit var startGuiBtn: TextView
    private lateinit var stopGuiBtn: TextView

    private val composeCpuState = androidx.compose.runtime.mutableIntStateOf(34)
    private val composeMemState = androidx.compose.runtime.mutableIntStateOf(82)
    private val composeRamUsedState = androidx.compose.runtime.mutableLongStateOf(0L)
    private val composeRamTotalState = androidx.compose.runtime.mutableLongStateOf(0L)
    private val composeSwapUsedState = androidx.compose.runtime.mutableLongStateOf(0L)
    private val composeSwapTotalState = androidx.compose.runtime.mutableLongStateOf(0L)
    private val composeDiskState = androidx.compose.runtime.mutableIntStateOf(0)

    // Proot Settings page — size-only (app storage rootfs)
    private var prootSizeValueTv: TextView? = null
    private var prootSizeUnitTv: TextView? = null
    private var prootSizeHintTv: TextView? = null
    private var prootRefreshBtn: TextView? = null
    private var prootLoadingRow: LinearLayout? = null
    private var prootProgressBar: ProgressBar? = null
    private var prootPathTv: TextView? = null
    private var prootMeasuring = false

    // Chroot Settings page — live labels for async refresh
    private var chrootStatusBadge: TextView? = null
    private var chrootRootBadge: TextView? = null
    private var chrootRootRowView: View? = null
    private var chrootSizePanelView: View? = null
    private var chrootPathRowView: View? = null
    private var chrootSizeValueTv: TextView? = null
    private var chrootSizeUnitTv: TextView? = null
    private var chrootSizeHintTv: TextView? = null
    private var chrootUninstallBtn: TextView? = null
    private var chrootInstallBtn: TextView? = null
    private var chrootRefreshBtn: TextView? = null
    private var chrootLoadingRow: LinearLayout? = null
    private var chrootProgressBar: ProgressBar? = null
    private var chrootMeasuring = false
    private var pendingChrootUninstall = false

    // Chroot Settings — Processes card (detect / kill / verify)
    private var chrootProcCountTv: TextView? = null
    private var chrootProcUnitTv: TextView? = null
    private var chrootProcHintTv: TextView? = null
    private var chrootProcSampleBox: LinearLayout? = null
    private var chrootProcScanBtn: TextView? = null
    private var chrootProcKillBtn: TextView? = null
    private var chrootProcLoadingRow: LinearLayout? = null
    private var chrootProcStatusTv: TextView? = null
    private var chrootProcMeasuring = false
    private var chrootProcKilling = false
    private var chrootProcLastCount = -1

    companion object {
        private const val PREF_PROOT_BYTES = "proot_size_bytes"
        private const val PREF_PROOT_DIR = "proot_dir_present"
        private const val PREF_PROOT_LAST_MS = "proot_last_check_ms"
        private const val PREF_CHROOT_INSTALLED = "chroot_installed"
        private const val PREF_CHROOT_DIR = "chroot_dir_present"
        private const val PREF_CHROOT_BYTES = "chroot_size_bytes"
        private const val PREF_CHROOT_ROOT_OK = "chroot_root_ok"
        private const val PREF_CHROOT_SIZE_VIA_ROOT = "chroot_size_via_root"
        private const val PREF_CHROOT_LAST_MS = "chroot_last_check_ms"
        private const val PREF_CHROOT_PROC_COUNT = "chroot_proc_count"
        private const val PREF_CHROOT_PROC_LAST_MS = "chroot_proc_last_ms"
    }

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
    private val ID_CHROOT_SETTINGS = 14
    private val ID_PROOT_SETTINGS = 15
    
    private var fileViewerBackPage = ID_FILES
    private var diffViewerBackPage = ID_GIT

    private var isScriptRunning = false
    private var resourceMonitorRunnable: Runnable? = null
    private var lastCpuTotal = 0L
    private var lastCpuIdle = 0L

    private var activeProjectName = "MyAndroidApp"
    private var activeProjectPath = "/home/flux/repos/my-android-app"
    /** Isolation method for [activeProjectPath] (proot|chroot). SSOT for path resolve. */
    private var activeProjectMethod: String = "proot"
    private lateinit var fileExplorerTitleTv: TextView
    private lateinit var projectIconInput: EditText
    private lateinit var projectNameInput: EditText
    private lateinit var projectPathInput: EditText
    private lateinit var projectGithubInput: EditText
    private lateinit var projectCreateBtn: TextView
    private lateinit var recentProjectsContainer: LinearLayout

    private var activeIconInput: EditText? = null
    private var activeIconPreviewIv: ImageView? = null
    private var activeIconPreviewTv: TextView? = null

    private fun updateIconPreview(input: String, previewIv: ImageView, previewTv: TextView) {
        val str = input.trim()
        if (str.isEmpty()) {
            previewIv.setImageResource(R.drawable.ic_folder)
            previewIv.setColorFilter(NC.PRIMARY)
            previewIv.visibility = View.VISIBLE
            previewTv.visibility = View.GONE
            return
        }
        val file = File(str)
        if (file.exists() && file.isFile) {
            try {
                val bm = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                if (bm != null) {
                    previewIv.setImageBitmap(bm)
                    previewIv.clearColorFilter()
                    previewIv.visibility = View.VISIBLE
                    previewTv.visibility = View.GONE
                    return
                }
            } catch (e: Exception) {}
        }
        if (str.startsWith("http://") || str.startsWith("https://") || str.startsWith("content://") || str.startsWith("file://")) {
            try {
                val uri = android.net.Uri.parse(str)
                previewIv.setImageURI(uri)
                previewIv.clearColorFilter()
                previewIv.visibility = View.VISIBLE
                previewTv.visibility = View.GONE
                return
            } catch (e: Exception) {}
        }
        previewTv.text = str
        previewTv.visibility = View.VISIBLE
        previewIv.visibility = View.GONE
    }

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
                activeIconPreviewIv?.let { iv ->
                    activeIconPreviewTv?.let { tv ->
                        updateIconPreview(destFile.absolutePath, iv, tv)
                    }
                }
            } catch (e: Exception) {
                // Try taking persistable permission as fallback
                try {
                    contentResolver.takePersistableUriPermission(selectedUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (pe: Exception) {}
                activeIconInput?.setText(selectedUri.toString())
            }
        }
    }

    private lateinit var projectSettingsContainer: FrameLayout
    private lateinit var projectSettingsScrollView: ScrollView
    private lateinit var projectSettingsLayout: LinearLayout
    private lateinit var configIconInput: EditText

    private lateinit var projectDirTreeContainer: FrameLayout
    private lateinit var projectDirTreeScrollView: ScrollView
    private lateinit var projectDirTreeLayout: LinearLayout
    private lateinit var projectGitDiffContainer: FrameLayout
    private lateinit var projectGitDiffScrollView: ScrollView
    private lateinit var projectGitDiffLayout: LinearLayout

    private val expandedFolders = HashSet<String>()

    private lateinit var projectWorkspaceContainer: FrameLayout
    private lateinit var projectWorkspaceLayout: LinearLayout
    private lateinit var workspaceTerminalView: TerminalView
    private lateinit var workspaceTabBar: LinearLayout
    private lateinit var workspaceTabBarScroll: HorizontalScrollView
    private lateinit var workspaceHubLayout: View
    /** Host for workspace hub AI tool cards; rebuilt on linux_method change. */
    private var workspaceHubToolsHost: LinearLayout? = null
    private lateinit var workspaceDirTreeLayout: LinearLayout
    private lateinit var workspaceGitDiffLayout: LinearLayout
    private lateinit var workspaceProjectNameTv: TextView
    private lateinit var workspaceProjectIconIv: ImageView

    private val workspaceSessions = ArrayList<TerminalSession>()
    private val workspaceTabNames = ArrayList<String>()
    private var activeWorkspaceTabIndex = -1

    private lateinit var workspaceTerminalContainer: LinearLayout
    private lateinit var workspaceKeyboardToolbar: LinearLayout
    private lateinit var terminalKeyboardToolbar: LinearLayout

    private data class ModifierState(
        var ctrlActive: Boolean = false, var ctrlLocked: Boolean = false,
        var altActive: Boolean = false,  var altLocked: Boolean = false,
        var shiftActive: Boolean = false, var shiftLocked: Boolean = false,
        var onStateChanged: (() -> Unit)? = null
    ) {
        fun readCtrl(autoReadSetFalse: Boolean = true): Boolean {
            if (!ctrlActive) return false
            if (autoReadSetFalse && !ctrlLocked) {
                ctrlActive = false
                onStateChanged?.invoke()
            }
            return true
        }
        fun readAlt(autoReadSetFalse: Boolean = true): Boolean {
            if (!altActive) return false
            if (autoReadSetFalse && !altLocked) {
                altActive = false
                onStateChanged?.invoke()
            }
            return true
        }
        fun readShift(autoReadSetFalse: Boolean = true): Boolean {
            if (!shiftActive) return false
            if (autoReadSetFalse && !shiftLocked) {
                shiftActive = false
                onStateChanged?.invoke()
            }
            return true
        }
        fun readFn(autoReadSetFalse: Boolean = true): Boolean = false
    }
    private val termModState = ModifierState()
    private val wsModState   = ModifierState()

    private val termImagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { handleImageAttachment(it, isWorkspace = false) } }

    private val wsImagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { handleImageAttachment(it, isWorkspace = true) } }

    private val projectSessionsMap = HashMap<String, ArrayList<TerminalSession>>()
    private val projectTabNamesMap = HashMap<String, ArrayList<String>>()
    private val projectActiveTabIndexMap = HashMap<String, Int>()

    // History tracking for back button support
    private val pageStack = java.util.Stack<Int>()

    // ── Linux isolation method ────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Safe if Splash already ran; covers deep-link / skip-splash entry
        AppUpgrade.runIfNeeded(this)
        
        // Enable fullscreen/immersive mode
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        window.statusBarColor = NC.BG
        window.navigationBarColor = NC.BG

        val prefs = getSharedPreferences("nativecode_prefs", MODE_PRIVATE)
        val setupCompleteFile = File(filesDir, "setup_complete")
        val isSetupComplete = setupCompleteFile.exists() || prefs.getBoolean("onboarding_completed", false)
        if (!isSetupComplete) {
            val intent = Intent(this, OnboardingActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            return
        }
        termFontSize = prefs.getInt("pref_terminal_zoom", 40)
        workspaceFontSize = termFontSize
        scriptFontSize = termFontSize
        showExtraKeys = prefs.getBoolean("pref_show_extra_keys", true)
        LinuxCommandBuilder.currentMethod = prefs.getString("linux_method", "proot") ?: "proot"
        activeProjectName = prefs.getString("active_project_name", activeProjectName) ?: activeProjectName
        activeProjectPath = prefs.getString("active_project_path", activeProjectPath) ?: activeProjectPath
        activeProjectMethod = prefs.getString("active_project_method", null)
            ?: getProjects().find { it.path == activeProjectPath }?.linuxMethod
            ?: LinuxCommandBuilder.currentMethod

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
            
            val currentPage = if (pageStack.isNotEmpty()) pageStack.peek() else ID_HOME
            val showGlobalNav = (currentPage == ID_HOME || currentPage == ID_PROJECTS_LIST || currentPage == ID_TERMINAL || currentPage == ID_SETTINGS)
            val showProjectNav = (currentPage == ID_PROJECT_WORKSPACE || currentPage == ID_PROJECT_SETTINGS || currentPage == ID_PROJECT_DIR_TREE || currentPage == ID_PROJECT_GIT_DIFF)
            val isLandscape = resources.displayMetrics.widthPixels > resources.displayMetrics.heightPixels
            val isKeyboardOpen = ime.bottom > 0

            if (::bottomNavContainer.isInitialized) {
                if (!isLandscape && (showGlobalNav || showProjectNav) && !isKeyboardOpen) {
                    bottomNavContainer.visibility = View.VISIBLE
                    bottomNavContainer.setPadding(bars.left, 0, bars.right, bars.bottom)
                    contentFrame.setPadding(0, 0, 0, 0)
                } else {
                    bottomNavContainer.visibility = View.GONE
                    bottomNavContainer.setPadding(0, 0, 0, 0)
                    contentFrame.setPadding(0, 0, 0, if (isKeyboardOpen) ime.bottom else bars.bottom)
                }
            }
            if (currentPage == ID_TERMINAL) {
                forceTerminalResize(if (::terminalView.isInitialized) terminalView else null)
            } else if (currentPage == ID_PROJECT_WORKSPACE) {
                forceTerminalResize(if (::workspaceTerminalView.isInitialized) workspaceTerminalView else null)
            }
            insets
        }
        ViewCompat.requestApplyInsets(drawerLayout)

        // Setup back callback for predictive/system back
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                onBackPressed()
            }
        })

        setupBottomNavigationListener()
        setupProjectBottomNavigationListener()
        setupGlobalNavRailListener()
        setupProjectNavRailListener()

        deployScripts()
        val targetPage = intent.getIntExtra("target_page", ID_HOME)
        if (targetPage != ID_HOME) {
            pageStack.push(targetPage)
            navigateToPage(targetPage)
        } else {
            showHome()
        }
        onSetupComplete()
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onPause() {
        super.onPause()
        getSharedPreferences("nativecode_prefs", MODE_PRIVATE).edit()
            .putString("active_project_name", activeProjectName)
            .putString("active_project_path", activeProjectPath)
            .putString("active_project_method", activeProjectMethod)
            .apply()
    }

    /** Host dir for active project using project method (not ambient global alone). */
    private fun activeProjectHostDir(): File =
        ProjectPathResolver.resolve(this, activeProjectPath, activeProjectMethod)

    /**
     * Apply isolation method for a project: switch global, persist, repair if clone
     * lives only under the opposite rootfs.
     * @return effective method used after optional recovery
     */
    private fun applyProjectIsolation(path: String, preferredMethod: String): String {
        var method = preferredMethod
        val found = ProjectManager.detectRepoMethod(this, path, preferredMethod)
        if (found != null && found != preferredMethod) {
            method = found
            val list = getProjects().toMutableList()
            val idx = list.indexOfFirst { it.path == path }
            if (idx >= 0) {
                val p = list[idx]
                list[idx] = p.copy(linuxMethod = method)
                saveProjects(list)
            }
            Toast.makeText(
                this,
                "Repo found under ${ProjectPathResolver.methodLabel(method)}; using that method",
                Toast.LENGTH_LONG
            ).show()
        }
        activeProjectMethod = method
        LinuxCommandBuilder.currentMethod = method
        getSharedPreferences("nativecode_prefs", MODE_PRIVATE)
            .edit()
            .putString("linux_method", method)
            .putString("active_project_method", method)
            .apply()
        refreshToolCardsForMethod()
        return method
    }

    private fun handleIntent(intent: Intent?) {
        intent?.let {
            // Chroot uninstall script deep-link: nativecode://callback?result=success&name=distro_uninstall_...
            val data = it.data
            if (data != null && data.scheme == "nativecode" && data.host == "callback") {
                val name = data.getQueryParameter("name").orEmpty()
                val result = data.getQueryParameter("result").orEmpty()
                if (name.contains("uninstall", ignoreCase = true) && result == "success") {
                    onChrootUninstalled(fromCallback = true)
                }
            }
            val projPath = it.getStringExtra("PROJECT_PATH")
            if (projPath != null) {
                activeProjectPath = projPath
                val preferred = getProjects().find { p -> p.path == projPath }?.linuxMethod
                    ?: LinuxCommandBuilder.currentMethod
                applyProjectIsolation(projPath, preferred)
            }
            val projName = it.getStringExtra("PROJECT_NAME")
            if (projName != null) {
                activeProjectName = projName
            }
            val targetPage = it.getIntExtra("EXTRA_TARGET_PAGE", -1)
            if (targetPage != -1) {
                navigateToPage(targetPage)
            }
            val createTerm = it.getStringExtra("CREATE_TERM")
            if (createTerm != null) {
                createNewTerminalSession(createTerm)
                navigateToPage(ID_TERMINAL)
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

    private fun setupBottomNavigationListener() {
        if (!::bottomNavigation.isInitialized) return
        bottomNavigation.setOnItemSelectedListener { item ->
            val pageId = item.itemId
            if (::globalNavRail.isInitialized) {
                globalNavRail.setOnItemSelectedListener(null)
                globalNavRail.selectedItemId = pageId
                setupGlobalNavRailListener()
            }
            if (pageStack.isEmpty() || pageStack.peek() != pageId) {
                pageStack.push(pageId)
            }
            navigateToPage(pageId)
            true
        }
    }
    
    private fun setupGlobalNavRailListener() {
        if (!::globalNavRail.isInitialized) return
        globalNavRail.setOnItemSelectedListener { item ->
            val pageId = item.itemId
            if (::bottomNavigation.isInitialized) {
                bottomNavigation.setOnItemSelectedListener(null)
                bottomNavigation.selectedItemId = pageId
                setupBottomNavigationListener()
            }
            if (pageStack.isEmpty() || pageStack.peek() != pageId) {
                pageStack.push(pageId)
            }
            navigateToPage(pageId)
            true
        }
    }

    private fun setupProjectBottomNavigationListener() {
        if (!::projectBottomNavigation.isInitialized) return
        projectBottomNavigation.setOnItemSelectedListener { item ->
            val pageId = item.itemId
            if (::projectNavRail.isInitialized) {
                projectNavRail.setOnItemSelectedListener(null)
                projectNavRail.selectedItemId = pageId
                setupProjectNavRailListener()
            }
            if (pageStack.isEmpty() || pageStack.peek() != pageId) {
                pageStack.push(pageId)
            }
            navigateToPage(pageId)
            true
        }
    }
    
    private fun setupProjectNavRailListener() {
        if (!::projectNavRail.isInitialized) return
        projectNavRail.setOnItemSelectedListener { item ->
            val pageId = item.itemId
            if (::projectBottomNavigation.isInitialized) {
                projectBottomNavigation.setOnItemSelectedListener(null)
                projectBottomNavigation.selectedItemId = pageId
                setupProjectBottomNavigationListener()
            }
            if (pageStack.isEmpty() || pageStack.peek() != pageId) {
                pageStack.push(pageId)
            }
            navigateToPage(pageId)
            true
        }
    }

    private fun navigateToPage(id: Int) {
        navigateToPage(id, true)
    }

    private fun navigateToPage(id: Int, pushToStack: Boolean) {
        if (pushToStack) {
            if (pageStack.isEmpty() || pageStack.peek() != id) {
                pageStack.push(id)
            }
        }

        updateNavigationVisibility(id)
        updateCustomBottomNavSelection(id)

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
        if (::fileViewerRootContainer.isInitialized) {
            fileViewerRootContainer.visibility = View.GONE
        }
        if (::diffViewerRootContainer.isInitialized) {
            diffViewerRootContainer.visibility = View.GONE
        }
        if (::scriptsScrollView.isInitialized) {
            scriptsScrollView.visibility = View.GONE
        }
        if (::prootSettingsScrollView.isInitialized) {
            prootSettingsScrollView.visibility = View.GONE
        }
        if (::chrootSettingsScrollView.isInitialized) {
            chrootSettingsScrollView.visibility = View.GONE
        }
        if (::scriptInstallLayout.isInitialized) {
            scriptInstallLayout.visibility = View.GONE
        }
        if (::projectCreateContainer.isInitialized) {
            // Only toggle container — scroll child must stay VISIBLE or form is blank
            projectCreateContainer.visibility = View.GONE
        }
        if (::projectsListContainer.isInitialized) {
            projectsListContainer.visibility = View.GONE
        }
        if (::projectWorkspaceContainer.isInitialized) {
            projectWorkspaceContainer.visibility = View.GONE
        }
        if (::projectSettingsContainer.isInitialized) {
            projectSettingsContainer.visibility = View.GONE
        }
        if (::projectDirTreeContainer.isInitialized) {
            projectDirTreeContainer.visibility = View.GONE
        }
        if (::projectGitDiffContainer.isInitialized) {
            projectGitDiffContainer.visibility = View.GONE
        }

        if (id == ID_TERMINAL) {
            unifiedHeader.visibility = View.GONE
            if (::drawerLayout.isInitialized) drawerLayout.setDrawerLockMode(androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_UNLOCKED)
        } else if (id == ID_SCRIPT_INSTALL) {
            unifiedHeader.visibility = View.GONE
            if (::bottomNavigation.isInitialized) bottomNavigation.menu.findItem(bottomNavigation.selectedItemId)?.isChecked = false
            if (::drawerLayout.isInitialized) drawerLayout.setDrawerLockMode(androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        } else if (id == ID_PROJECT_CREATE) {
            // App title bar (logo + telemetry) stays visible on create/import
            unifiedHeader.visibility = View.VISIBLE
            if (::drawerLayout.isInitialized) drawerLayout.setDrawerLockMode(androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        } else if (id == ID_PROJECT_WORKSPACE || id == ID_PROJECT_SETTINGS || id == ID_PROJECT_DIR_TREE || id == ID_PROJECT_GIT_DIFF) {
            unifiedHeader.visibility = View.GONE
            if (::drawerLayout.isInitialized) drawerLayout.setDrawerLockMode(androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        } else {
            unifiedHeader.visibility = View.VISIBLE
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

        homeScrollView.visibility = View.VISIBLE
                homeScrollView.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                populateRecentProjects()
                val valTv = homeLayout.findViewWithTag<TextView>("APP_STORAGE_VAL")
                val subTv = homeLayout.findViewWithTag<TextView>("APP_STORAGE_SUB")
                val barFill = homeLayout.findViewWithTag<View>("APP_STORAGE_BAR")
                if (valTv != null && subTv != null && barFill != null) {
                    updateAppStorageUsage(valTv, subTv, barFill, null)
                }
                animateHomeLayoutEntrance()
            }
            ID_FILES -> {
                fileExplorerScrollView.visibility = View.VISIBLE
                if (::fileExplorerTitleTv.isInitialized) {
                    fileExplorerTitleTv.text = activeProjectName
                }
            }
            ID_TERMINAL -> {
                terminalWorkspaceLayout.visibility = View.VISIBLE
                if (sessionsList.isNotEmpty() && activeSessionIndex >= 0 && activeSessionIndex < sessionsList.size) {
                    if (::terminalToolSelectorScrollView.isInitialized) terminalToolSelectorScrollView.visibility = View.GONE
                    if (::terminalViewContainer.isInitialized) terminalViewContainer.visibility = View.VISIBLE
                    if (::terminalKeyboardToolbar.isInitialized) terminalKeyboardToolbar.visibility = if (showExtraKeys) View.VISIBLE else View.GONE
                    terminalView.isFocusable = true
                    terminalView.isFocusableInTouchMode = true
                    terminalView.requestFocus()
                } else {
                    if (::terminalViewContainer.isInitialized) terminalViewContainer.visibility = View.GONE
                    if (::terminalKeyboardToolbar.isInitialized) terminalKeyboardToolbar.visibility = View.GONE
                    if (::terminalToolSelectorScrollView.isInitialized) {
                        refreshTerminalToolSelector()
                        terminalToolSelectorScrollView.visibility = View.VISIBLE
                        terminalToolSelectorScrollView.post {
                            terminalToolSelectorScrollView.requestLayout()
                            terminalToolSelectorScrollView.invalidate()
                        }
                    }
                }
            }
            ID_GIT -> {
                gitOperationsScrollView.visibility = View.VISIBLE
            }
            ID_SETTINGS -> {
                settingsHubScrollView.visibility = View.VISIBLE
            }
            ID_PROOT_SETTINGS -> {
                if (::prootSettingsScrollView.isInitialized) {
                    prootSettingsScrollView.visibility = View.VISIBLE
                }
                applyCachedProotInfo()
                refreshProotSettingsCard(force = false)
            }
            ID_CHROOT_SETTINGS -> {
                if (::chrootSettingsScrollView.isInitialized) {
                    chrootSettingsScrollView.visibility = View.VISIBLE
                }
                applyInstantChrootStatus()
                applyCachedChrootInfo()
                applyCachedChrootProcInfo()
                // Sequential root work: size then processes (no concurrent su race)
                refreshChrootSettingsPage(force = false)
            }
            ID_SCRIPTS -> {
                if (::scriptsScrollView.isInitialized) {
                    scriptsScrollView.visibility = View.VISIBLE
                    refreshRepairsPage()
                }
            }
            ID_SCRIPT_INSTALL -> {
                if (::scriptInstallLayout.isInitialized) {
                    scriptInstallLayout.visibility = View.VISIBLE
                }
            }
            ID_PROJECT_CREATE -> {
                if (::projectCreateContainer.isInitialized) {
                    projectCreateContainer.visibility = View.VISIBLE
                    if (::projectCreateScrollView.isInitialized) {
                        projectCreateScrollView.visibility = View.VISIBLE
                    }
                    refreshProjectCreateMethodChips()
                }
            }
            ID_PROJECTS_LIST -> {
                if (::projectsListContainer.isInitialized) {
                    projectsListContainer.visibility = View.VISIBLE
                    populateProjectsList()
                }
            }
            ID_PROJECT_WORKSPACE -> {
                if (::projectWorkspaceContainer.isInitialized) {
                    projectWorkspaceContainer.visibility = View.VISIBLE
                    workspaceTerminalView.isFocusable = true
                    workspaceTerminalView.isFocusableInTouchMode = true
                    openProjectWorkspace()
                }
            }
            ID_PROJECT_SETTINGS -> {
                if (::projectSettingsContainer.isInitialized) {
                    projectSettingsContainer.visibility = View.VISIBLE
                    openProjectSettings()
                }
            }
            ID_PROJECT_DIR_TREE -> {
                if (::projectDirTreeContainer.isInitialized) {
                    projectDirTreeContainer.visibility = View.VISIBLE
                    openProjectDirTree()
                }
            }
            ID_PROJECT_GIT_DIFF -> {
                if (::projectGitDiffContainer.isInitialized) {
                    projectGitDiffContainer.visibility = View.VISIBLE
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
        if (::projectSettingsContainer.isInitialized && projectSettingsContainer.visibility == View.VISIBLE) {
            navigateToPage(ID_PROJECT_WORKSPACE)
            return
        }

        if (::projectDirTreeContainer.isInitialized && projectDirTreeContainer.visibility == View.VISIBLE) {
            navigateToPage(ID_PROJECT_WORKSPACE)
            return
        }

        if (::projectGitDiffContainer.isInitialized && projectGitDiffContainer.visibility == View.VISIBLE) {
            navigateToPage(ID_PROJECT_WORKSPACE)
            return
        }

        if (::projectWorkspaceContainer.isInitialized && projectWorkspaceContainer.visibility == View.VISIBLE) {
            navigateToPage(ID_PROJECTS_LIST)
            return
        }

        if ((::projectCreateContainer.isInitialized && projectCreateContainer.visibility == View.VISIBLE) ||
            (::projectCreateScrollView.isInitialized && projectCreateScrollView.visibility == View.VISIBLE)
        ) {
            navigateToPage(ID_PROJECTS_LIST)
            return
        }

        if ((::fileViewerRootContainer.isInitialized && fileViewerRootContainer.visibility == View.VISIBLE) || (::diffViewerRootContainer.isInitialized && diffViewerRootContainer.visibility == View.VISIBLE)) {
            val prevPage = if (::fileViewerRootContainer.isInitialized && fileViewerRootContainer.visibility == View.VISIBLE) fileViewerBackPage else diffViewerBackPage
            navigateToPage(prevPage)
            return
        }

        if (::scriptInstallLayout.isInitialized && scriptInstallLayout.visibility == View.VISIBLE) {
            if (isScriptRunning) {
                return
            }
            // Return to launcher page (Chroot Settings after uninstall, Scripts hub, etc.)
            if (pageStack.size > 1 && pageStack.peek() == ID_SCRIPT_INSTALL) {
                pageStack.pop()
                navigateToPage(pageStack.peek(), false)
            } else {
                navigateToPage(ID_SCRIPTS, false)
            }
            return
        }

        if (::prootSettingsScrollView.isInitialized && prootSettingsScrollView.visibility == View.VISIBLE) {
            navigateToPage(ID_SETTINGS)
            return
        }

        if (::chrootSettingsScrollView.isInitialized && chrootSettingsScrollView.visibility == View.VISIBLE) {
            navigateToPage(ID_SETTINGS)
            return
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


    private fun updateNavigationVisibility(id: Int) {
        val isLandscape = (::rootLayout.isInitialized && rootLayout.isLaidOut && rootLayout.width > rootLayout.height) ||
            (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R && windowManager.currentWindowMetrics.bounds.width() > windowManager.currentWindowMetrics.bounds.height()) ||
            resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE ||
            resources.displayMetrics.widthPixels > resources.displayMetrics.heightPixels

        if (::rootLayout.isInitialized) {
            rootLayout.orientation = if (isLandscape) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        }
        if (::mainContentLayout.isInitialized) {
            mainContentLayout.layoutParams = if (isLandscape) {
                LinearLayout.LayoutParams(0, MATCH, 1f)
            } else {
                LinearLayout.LayoutParams(MATCH, MATCH)
            }
        }

        var showGlobalNav = false
        var showProjectNav = false

        if (id == ID_HOME || id == ID_PROJECTS_LIST || id == ID_TERMINAL || id == ID_SETTINGS) {
            showGlobalNav = true
        } else if (id == ID_PROJECT_WORKSPACE || id == ID_PROJECT_SETTINGS || id == ID_PROJECT_DIR_TREE || id == ID_PROJECT_GIT_DIFF) {
            showProjectNav = true
        }
        
        if (id == ID_SCRIPTS || id == ID_SCRIPT_INSTALL || id == ID_PROJECT_CREATE ||
            id == ID_CHROOT_SETTINGS || id == ID_PROOT_SETTINGS
        ) {
            showGlobalNav = false
            showProjectNav = false
        }

        if (::sideNavContainer.isInitialized) {
            sideNavContainer.visibility = if (isLandscape && (showGlobalNav || showProjectNav)) android.view.View.VISIBLE else android.view.View.GONE
        }
        if (::bottomNavContainer.isInitialized) {
            val isKeyboardOpen = drawerLayout.rootWindowInsets?.let {
                androidx.core.view.WindowInsetsCompat.toWindowInsetsCompat(it).getInsets(androidx.core.view.WindowInsetsCompat.Type.ime()).bottom > 0
            } ?: false
            bottomNavContainer.visibility = if (!isLandscape && !isKeyboardOpen && (showGlobalNav || showProjectNav)) android.view.View.VISIBLE else android.view.View.GONE
        }
        
        if (::globalNavRail.isInitialized) {
            globalNavRail.visibility = if (showGlobalNav) android.view.View.VISIBLE else android.view.View.GONE
        }
        if (::projectNavRail.isInitialized) {
            projectNavRail.visibility = if (showProjectNav) android.view.View.VISIBLE else android.view.View.GONE
        }
        if (::bottomNavigation.isInitialized) {
            bottomNavigation.visibility = android.view.View.GONE
        }
        if (::projectBottomNavigation.isInitialized) {
            projectBottomNavigation.visibility = android.view.View.GONE
        }
        
        // Handle window insets based on orientation
        if (isLandscape && ::mainContentLayout.isInitialized) {
            rootLayout.rootWindowInsets?.let { insets ->
                val bars = androidx.core.view.WindowInsetsCompat.toWindowInsetsCompat(insets).getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                mainContentLayout.setPadding(0, bars.top, bars.right, bars.bottom)
                bottomNavContainer.setPadding(0, 0, 0, 0)
                sideNavContainer.setPadding(bars.left, bars.top, 0, bars.bottom)
            }
        } else if (::mainContentLayout.isInitialized) {
            rootLayout.rootWindowInsets?.let { insets ->
                val bars = androidx.core.view.WindowInsetsCompat.toWindowInsetsCompat(insets).getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                mainContentLayout.setPadding(0, bars.top, 0, 0)
                bottomNavContainer.setPadding(bars.left, 0, bars.right, bars.bottom)
                sideNavContainer.setPadding(0, 0, 0, 0)
            }
        }

        val currentPage = if (pageStack.isNotEmpty()) pageStack.peek() else ID_HOME

        if (isLandscape && showGlobalNav && ::globalNavRail.isInitialized) {
            globalNavRail.setOnItemSelectedListener(null)
            globalNavRail.selectedItemId = currentPage
            setupGlobalNavRailListener()
        }
        if (isLandscape && showProjectNav && ::projectNavRail.isInitialized) {
            projectNavRail.setOnItemSelectedListener(null)
            projectNavRail.selectedItemId = currentPage
            setupProjectNavRailListener()
        }
        if (!isLandscape && showGlobalNav && ::bottomNavigation.isInitialized) {
            bottomNavigation.setOnItemSelectedListener(null)
            bottomNavigation.selectedItemId = currentPage
            setupBottomNavigationListener()
        }
        if (!isLandscape && showProjectNav && ::projectBottomNavigation.isInitialized) {
            projectBottomNavigation.setOnItemSelectedListener(null)
            projectBottomNavigation.selectedItemId = currentPage
            setupProjectBottomNavigationListener()
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

        // Detect initial orientation so we don't start with wrong layout params
        val initLandscape = resources.displayMetrics.widthPixels > resources.displayMetrics.heightPixels

        rootLayout = LinearLayout(this).apply {
            orientation = if (initLandscape) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            setBackgroundColor(NC.BG)
            layoutParams = ViewGroup.LayoutParams(MATCH, MATCH)
            addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                val oldWidth = oldRight - oldLeft
                val oldHeight = oldBottom - oldTop
                val newWidth = right - left
                val newHeight = bottom - top
                if (newWidth != oldWidth || newHeight != oldHeight) {
                    val currentPage = if (pageStack.isNotEmpty()) pageStack.peek() else ID_HOME
                    updateNavigationVisibility(currentPage)
                }
            }
        }
        drawerLayout.addView(rootLayout)

        sideNavContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(WRAP, MATCH)
            setBackgroundColor(android.graphics.Color.parseColor("#120F16"))
            visibility = android.view.View.GONE
        }
        rootLayout.addView(sideNavContainer)

        mainContentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(NC.BG)
            layoutParams = if (initLandscape) {
                LinearLayout.LayoutParams(0, MATCH, 1f)
            } else {
                LinearLayout.LayoutParams(MATCH, MATCH)
            }
        }
        rootLayout.addView(mainContentLayout)

        // Sidebar container
        sidebarLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(NC.SURFACE_LOWEST)
            val params = androidx.drawerlayout.widget.DrawerLayout.LayoutParams(dp(260), MATCH).apply {
                gravity = Gravity.START
            }
            layoutParams = params
            setPadding(dp(16), dp(20), dp(16), dp(16))
        }

        // Add header to sidebar
        val sidebarHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(16))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        val sidebarTitle = TextView(this).apply {
            text = "Active Terminals"
            textSize = 18f
            setTextColor(NC.PRIMARY)
            typeface = Typeface.MONOSPACE
            paint.isFakeBoldText = true
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        val sidebarAddBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_add)
            setColorFilter(NC.PRIMARY)
            setPadding(dp(4), dp(4), dp(4), dp(4))
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(28))
            setOnClickListener {
                drawerLayout.closeDrawer(sidebarLayout)
                val topBarAddBtn = terminalWorkspaceLayout.findViewWithTag<View>("ADD_TERM_BTN")
                showNewTerminalDropdown(topBarAddBtn ?: it) { type ->
                    createNewTerminalSession(type)
                }
            }
        }
        sidebarHeaderRow.addView(sidebarTitle)
        sidebarHeaderRow.addView(sidebarAddBtn)
        sidebarLayout.addView(sidebarHeaderRow)

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
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(NC.SURFACE_LOWEST)
                setStroke(dp(1), Color.parseColor("#3360F99E"))
            }
            setPadding(dp(16), dp(10), dp(16), dp(10))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        val logoContainer = FrameLayout(this).apply {
            background = cyberBrutalistBg(
                fillColor = NC.SURFACE_HIGH,
                strokeColor = Color.parseColor("#8060F99E"),
                shadowColor = NC.SHADOW_DARK,
                offsetDp = 2
            )
            setPadding(dp(4), dp(4), dp(4), dp(4))
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
        }
        val logoView = ImageView(this).apply {
            setImageResource(R.drawable.logo_highres)
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
        }
        logoContainer.addView(logoView)

        backBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_arrow_back)
            setColorFilter(NC.ON_SURFACE)
            setPadding(dp(6), dp(6), dp(6), dp(6))
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
            setOnClickListener {
                onBackPressed()
            }
        }
        menuBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_menu)
            setColorFilter(NC.ON_SURFACE)
            setPadding(dp(6), dp(6), dp(6), dp(6))
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
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

        // System Telemetry Badge (Top Right)
        val fontNarrow = Typeface.createFromAsset(assets, "fonts/font.ttf")

        val statusPlate = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = cyberBrutalistBg(
                fillColor = NC.SURFACE_LOW,
                strokeColor = Color.parseColor("#3c4a3f"),
                shadowColor = NC.SHADOW_GREEN,
                offsetDp = 6
            )
            setPadding(dp(10), dp(2), dp(10), dp(2))
            layoutParams = LinearLayout.LayoutParams(WRAP, dp(37))
        }

        val batIcon = ImageView(this).apply {
            tag = "HEADER_BAT_ICON"
            setImageResource(if (isBatteryCharging()) R.drawable.ic_battery_charging else R.drawable.ic_battery_discharging)
            setColorFilter(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(dp(18), dp(18)).apply {
                rightMargin = dp(4)
                gravity = Gravity.CENTER_VERTICAL
            }
        }
        val batTv = TextView(this).apply {
            tag = "HEADER_BAT"
            text = "${readBatteryUsage()}%"
            textSize = 12f
            setTextColor(Color.WHITE)
            typeface = fontNarrow
        }
        val batContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(batIcon)
            addView(batTv)
        }

        val divider1 = View(this).apply {
            setBackgroundColor(Color.parseColor("#26FFFFFF"))
            layoutParams = LinearLayout.LayoutParams(dp(1), dp(12)).apply {
                leftMargin = dp(6)
                rightMargin = dp(6)
            }
        }

        val terminalGreen = Color.parseColor("#3DDC84")

        val cpuTvHeader = TextView(this).apply {
            tag = "HEADER_CPU"
            text = "C: ${readCpuUsage()}%"
            textSize = 12f
            setTextColor(terminalGreen)
            typeface = fontNarrow
        }

        val divider2 = View(this).apply {
            setBackgroundColor(Color.parseColor("#26FFFFFF"))
            layoutParams = LinearLayout.LayoutParams(dp(1), dp(12)).apply {
                leftMargin = dp(6)
                rightMargin = dp(6)
            }
        }

        val ramTvHeader = TextView(this).apply {
            tag = "HEADER_RAM"
            text = "R: ${readMemUsage()}"
            textSize = 12f
            setTextColor(terminalGreen)
            typeface = fontNarrow
        }

        statusPlate.addView(batContainer)
        statusPlate.addView(divider1)
        statusPlate.addView(cpuTvHeader)
        statusPlate.addView(divider2)
        statusPlate.addView(ramTvHeader)

        unifiedHeader.addView(logoContainer)
        unifiedHeader.addView(spacer)
        unifiedHeader.addView(displayBtn)
        unifiedHeader.addView(statusPlate)
        mainContentLayout.addView(unifiedHeader)

        startResourceMonitoring()

        contentFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f)
            background = TerminalScanlineDrawable()
        }

        val navStates = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_checked)
        )
        val navColors = intArrayOf(
            NC.SURFACE_LOWEST,
            Color.parseColor("#99FFFFFF")
        )
        val navTintList = android.content.res.ColorStateList(navStates, navColors)

        bottomNavigation = BottomNavigationView(this).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH, WRAP)
            setBackgroundColor(NC.SURFACE_LOWEST)
            itemIconTintList = navTintList
            itemTextColor = navTintList
            itemActiveIndicatorColor = android.content.res.ColorStateList.valueOf(NC.PRIMARY)
            menu.add(Menu.NONE, ID_HOME,          Menu.NONE, "HOME").setIcon(R.drawable.ic_home)
            menu.add(Menu.NONE, ID_PROJECTS_LIST, Menu.NONE, "PROJECT").setIcon(R.drawable.ic_folder)
            menu.add(Menu.NONE, ID_TERMINAL,      Menu.NONE, "TERMINAL").setIcon(R.drawable.ic_terminal)
            menu.add(Menu.NONE, ID_SETTINGS,      Menu.NONE, "SETTINGS").setIcon(R.drawable.ic_settings)
        }

        projectBottomNavigation = BottomNavigationView(this).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH, WRAP)
            setBackgroundColor(NC.SURFACE_LOWEST)
            itemIconTintList = navTintList
            itemTextColor = navTintList
            itemActiveIndicatorColor = android.content.res.ColorStateList.valueOf(NC.PRIMARY)
            menu.add(Menu.NONE, ID_PROJECT_WORKSPACE, Menu.NONE, "WORKSPACE").setIcon(R.drawable.ic_home)
            menu.add(Menu.NONE, ID_PROJECT_DIR_TREE, Menu.NONE, "DIRECTORY").setIcon(R.drawable.ic_folder)
            menu.add(Menu.NONE, ID_PROJECT_GIT_DIFF, Menu.NONE, "DIFF").setIcon(R.drawable.ic_git)
            menu.add(Menu.NONE, ID_PROJECT_SETTINGS, Menu.NONE, "CONFIG").setIcon(R.drawable.ic_project_config)
        }

        mainContentLayout.addView(contentFrame)
        
        bottomNavContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(NC.SURFACE_LOWEST)
                setStroke(dp(1), Color.parseColor("#1AFFFFFF"))
            }
        }
        mainContentLayout.addView(bottomNavContainer)

        bottomNavContainer.addView(buildCustomBottomNav())
        bottomNavContainer.addView(buildCustomProjectBottomNav())

        globalNavRail = com.google.android.material.navigationrail.NavigationRailView(this).apply {
            layoutParams = FrameLayout.LayoutParams(WRAP, MATCH)
            setBackgroundColor(android.graphics.Color.parseColor("#120F16"))
            itemIconTintList = navTintList
            itemTextColor = navTintList
            labelVisibilityMode = com.google.android.material.navigationrail.NavigationRailView.LABEL_VISIBILITY_LABELED
            menu.add(android.view.Menu.NONE, ID_HOME,          android.view.Menu.NONE, "Home").setIcon(R.drawable.ic_home)
            menu.add(android.view.Menu.NONE, ID_PROJECTS_LIST, android.view.Menu.NONE, "Projects").setIcon(R.drawable.ic_folder)
            menu.add(android.view.Menu.NONE, ID_TERMINAL,      android.view.Menu.NONE, "Terminal").setIcon(R.drawable.ic_terminal)
            menu.add(android.view.Menu.NONE, ID_SETTINGS,      android.view.Menu.NONE, "Settings").setIcon(R.drawable.ic_settings)
            
            setOnItemSelectedListener { item ->
                val pageId = item.itemId
                if (::bottomNavigation.isInitialized) {
                    bottomNavigation.setOnItemSelectedListener(null)
                    bottomNavigation.selectedItemId = pageId
                    setupBottomNavigationListener()
                }
                if (pageStack.isEmpty() || pageStack.peek() != pageId) {
                    pageStack.push(pageId)
                }
                navigateToPage(pageId)
                true
            }
        }

        projectNavRail = com.google.android.material.navigationrail.NavigationRailView(this).apply {
            layoutParams = FrameLayout.LayoutParams(WRAP, MATCH)
            setBackgroundColor(android.graphics.Color.parseColor("#120F16"))
            itemIconTintList = navTintList
            itemTextColor = navTintList
            labelVisibilityMode = com.google.android.material.navigationrail.NavigationRailView.LABEL_VISIBILITY_LABELED
            menu.add(android.view.Menu.NONE, ID_PROJECT_WORKSPACE, android.view.Menu.NONE, "Workspace").setIcon(R.drawable.ic_home)
            menu.add(android.view.Menu.NONE, ID_PROJECT_DIR_TREE, android.view.Menu.NONE, "Directory").setIcon(R.drawable.ic_folder)
            menu.add(android.view.Menu.NONE, ID_PROJECT_GIT_DIFF, android.view.Menu.NONE, "Diff").setIcon(R.drawable.ic_git)
            menu.add(android.view.Menu.NONE, ID_PROJECT_SETTINGS, android.view.Menu.NONE, "Config").setIcon(R.drawable.ic_project_config)

            setOnItemSelectedListener { item ->
                val pageId = item.itemId
                if (::projectBottomNavigation.isInitialized) {
                    projectBottomNavigation.setOnItemSelectedListener(null)
                    projectBottomNavigation.selectedItemId = pageId
                    setupProjectBottomNavigationListener()
                }
                if (pageStack.isEmpty() || pageStack.peek() != pageId) {
                    pageStack.push(pageId)
                }
                navigateToPage(pageId)
                true
            }
        }
        
        sideNavContainer.addView(globalNavRail)
        sideNavContainer.addView(projectNavRail)


        // Initialize all layout panels
        buildHomeLayout()
        buildFileExplorerLayout()
        buildTerminalLayout()
        buildGitOperationsLayout()
        buildSettingsHubLayout()
        buildFileViewerLayout()
        buildDiffViewerLayout()
        buildScriptsLayout()
        buildProotSettingsPage()
        buildChrootSettingsPage()
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
        contentFrame.addView(fileViewerRootContainer)
        contentFrame.addView(diffViewerRootContainer)
        contentFrame.addView(scriptsScrollView)
        contentFrame.addView(prootSettingsScrollView)
        contentFrame.addView(chrootSettingsScrollView)
        contentFrame.addView(scriptInstallLayout)
        contentFrame.addView(projectCreateContainer)
        contentFrame.addView(projectsListContainer)
        contentFrame.addView(projectWorkspaceContainer)
        contentFrame.addView(projectSettingsContainer)
        contentFrame.addView(projectDirTreeContainer)
        contentFrame.addView(projectGitDiffContainer)

        pageStack.push(ID_HOME)
    }

    private fun extractTarStream(inputStream: InputStream, targetDir: File) {
        val buffer = ByteArray(512)
        while (true) {
            var bytesRead = 0
            while (bytesRead < 512) {
                val r = inputStream.read(buffer, bytesRead, 512 - bytesRead)
                if (r == -1) break
                bytesRead += r
            }
            if (bytesRead < 512) break

            var allZero = true
            for (b in buffer) {
                if (b != 0.toByte()) { allZero = false; break }
            }
            if (allZero) break

            fun parseString(offset: Int, length: Int): String {
                var len = 0
                while (len < length && buffer[offset + len] != 0.toByte()) { len++ }
                return String(buffer, offset, len, Charsets.UTF_8)
            }

            var name = parseString(0, 100)
            val prefix = parseString(345, 155)
            if (prefix.isNotEmpty()) name = "$prefix/$name"

            val sizeStr = parseString(124, 12).trim()
            val size = try { sizeStr.toLong(8) } catch (e: Exception) { 0L }
            val type = buffer[156].toInt().toChar()
            val linkName = parseString(157, 100)

            val relPath = name.replace("^data/data/com.ivarna.nativecode/files/".toRegex(), "").trimStart('/')
            if (relPath.isEmpty()) {
                val dataBlocks = Math.ceil(size.toDouble() / 512.0).toLong()
                skipBytes(inputStream, dataBlocks * 512L)
                continue
            }

            val outFile = File(targetDir, relPath)

            if (type == '5') {
                outFile.mkdirs()
            } else if (type == '2') {
                outFile.parentFile?.mkdirs()
                try {
                    Os.symlink(linkName, outFile.absolutePath)
                } catch (e: Exception) {
                    try {
                        val linkTarget = File(outFile.parentFile, linkName)
                        if (linkTarget.exists() && linkTarget.isFile) {
                            linkTarget.copyTo(outFile, overwrite = true)
                        }
                    } catch (_: Exception) {}
                }
            } else if (type == '0' || type == '\u0000') {
                outFile.parentFile?.mkdirs()
                java.io.FileOutputStream(outFile).use { fos ->
                    var remaining = size
                    val copyBuf = ByteArray(8192)
                    while (remaining > 0) {
                        val toRead = Math.min(copyBuf.size.toLong(), remaining).toInt()
                        val r = inputStream.read(copyBuf, 0, toRead)
                        if (r == -1) break
                        fos.write(copyBuf, 0, r)
                        remaining -= r
                    }
                }
                val padding = ((512 - (size % 512)) % 512).toInt()
                if (padding > 0) {
                    skipBytes(inputStream, padding.toLong())
                }
                val modeStr = parseString(100, 8).trim()
                try {
                    val mode = modeStr.toInt(8)
                    if ((mode and 73) != 0) {
                        outFile.setExecutable(true, false)
                    }
                } catch (_: Exception) {}
            } else {
                val dataBlocks = Math.ceil(size.toDouble() / 512.0).toLong()
                skipBytes(inputStream, dataBlocks * 512L)
            }
        }
    }

    private fun skipBytes(inputStream: InputStream, count: Long) {
        var remaining = count
        val buf = ByteArray(8192)
        while (remaining > 0) {
            val toRead = Math.min(buf.size.toLong(), remaining).toInt()
            val r = inputStream.read(buf, 0, toRead)
            if (r == -1) break
            remaining -= r
        }
    }

    private fun ensureBootstrapExtracted() {
        val termuxExecFile = File(filesDir, "usr/lib/libtermux-exec.so")
        if (termuxExecFile.exists()) {
            // Already extracted — still re-apply SSOT package rewrite + host env
            TermuxHostPaths.applyPackageToExtractedPrefix(filesDir)
            return
        }

        try {
            val usrDir = File(filesDir, "usr")
            val tmpDir = File(usrDir, "tmp")
            val etcDir = File(usrDir, "etc")
            val homeDir = File(filesDir, "home")
            tmpDir.mkdirs(); etcDir.mkdirs(); homeDir.mkdirs()

            assets.open("bootstrap.tar").use { input ->
                extractTarStream(input, filesDir)
            }
            TermuxHostPaths.applyPackageToExtractedPrefix(filesDir)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    private fun createNewTerminalSession(type: String = "shell") {
        if (sessionsList.size >= 10) {
            Toast.makeText(this, "Maximum 10 tabs allowed", Toast.LENGTH_SHORT).show()
            return
        }
        if (type == "codex" && LinuxCommandBuilder.currentMethod == "chroot") {
            Toast.makeText(this, "Codex unavailable in chroot", Toast.LENGTH_SHORT).show()
            return
        }
        ensureBootstrapExtracted()
        maybeToastHeavyToolLaunch(type)
        val nld     = applicationInfo.nativeLibraryDir
        val shell   = File(nld, "libbash.so").absolutePath
        val cwd     = File(filesDir, "home").absolutePath
        val shellCmd = ChrootCommandBuilder.buildToolShellCommand(this, type, workDir = null)
        val (args, envMap) = LinuxCommandBuilder.build(this, shellCmd)
        val env = envMap.map { "${it.key}=${it.value}" }.toTypedArray()
        if (!::sessionClient.isInitialized) {
            initTerminalView()
        }
        val isChroot = LinuxCommandBuilder.currentMethod == "chroot"
        val sessionExec = if (isChroot) ChrootCommandBuilder.SESSION_EXEC else shell
        val session = TerminalSession(sessionExec, cwd, args, env, 10000, sessionClient)
        sessionsList.add(session)
        terminalSessionTypes.add(type)
        switchTerminalSession(sessionsList.size - 1)
        updateAppTerminalService()
    }


    private fun switchTerminalSession(index: Int) {
        if (index < 0 || index >= sessionsList.size) return
        activeSessionIndex = index
        val session = sessionsList[index]
        terminalSession = session

        mainHandler.post {
            if (::terminalToolSelectorScrollView.isInitialized) {
                terminalToolSelectorScrollView.visibility = View.GONE
            }
            if (::terminalViewContainer.isInitialized) {
                terminalViewContainer.visibility = View.VISIBLE
            }
            if (::terminalKeyboardToolbar.isInitialized) {
                terminalKeyboardToolbar.visibility = if (showExtraKeys) View.VISIBLE else View.GONE
            }
            if (::toggleExtraKeysBtn.isInitialized) {
                toggleExtraKeysBtn.visibility = View.VISIBLE
            }
            terminalView.attachSession(session)
            terminalView.onScreenUpdated()
            terminalView.isFocusable = true
            terminalView.isFocusableInTouchMode = true
            terminalView.requestFocus()
            forceTerminalResize(terminalView)
            updateSidebarTerminalsList()
        }
    }

    private fun closeTerminalSession(index: Int) {
        if (index < 0 || index >= sessionsList.size) return
        val session = sessionsList[index]
        session.finishIfRunning()
        sessionsList.removeAt(index)
        if (index < terminalSessionTypes.size) {
            terminalSessionTypes.removeAt(index)
        }

        if (sessionsList.isEmpty()) {
            activeSessionIndex = -1
            terminalSession = null
            if (::terminalViewContainer.isInitialized) {
                terminalViewContainer.visibility = View.GONE
            }
            if (::terminalKeyboardToolbar.isInitialized) {
                terminalKeyboardToolbar.visibility = View.GONE
            }
            if (::terminalToolSelectorScrollView.isInitialized) {
                terminalToolSelectorScrollView.visibility = View.VISIBLE
            }
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
            val type = terminalSessionTypes.getOrNull(i) ?: "shell"
            val toolLabel = when (type) {
                "opencode"    -> "opencode"
                "codex"       -> "codex"
                "agy"         -> "agy"
                "claude-code" -> "claude-code"
                "qwen-code"   -> "qwen-code"
                "grok"        -> "grok"
                "kiro"        -> "kiro"
                else          -> "Debian Shell"
            }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = cyberBrutalistBg(
                    fillColor = if (isSelected) NC.SURFACE_HIGH else NC.SURFACE_LOW,
                    strokeColor = if (isSelected) NC.PRIMARY else Color.parseColor("#3c4a3f"),
                    shadowColor = if (isSelected) NC.SHADOW_GREEN else NC.SHADOW_DARK,
                    offsetDp = if (isSelected) 4 else 2,
                    cornerRadiusDp = 0
                )
                layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply {
                    bottomMargin = dp(8)
                }
                setOnClickListener {
                    switchTerminalSession(i)
                    drawerLayout.closeDrawer(sidebarLayout)
                }
            }

            val terminalIcon = ImageView(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(18), dp(18)).apply {
                    rightMargin = dp(10)
                }
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            val filename = if (type == "qwen-code") "qwen-code.webp" else "$type.png"
            try {
                assets.open("images/cli/$filename").use { input ->
                    val bmp = android.graphics.BitmapFactory.decodeStream(input)
                    if (bmp != null) {
                        terminalIcon.setImageBitmap(bmp)
                    } else {
                        terminalIcon.setImageResource(R.drawable.ic_terminal)
                        terminalIcon.setColorFilter(if (isSelected) NC.PRIMARY else NC.ON_SURF_VAR)
                    }
                }
            } catch (_: Exception) {
                terminalIcon.setImageResource(R.drawable.ic_terminal)
                terminalIcon.setColorFilter(if (isSelected) NC.PRIMARY else NC.ON_SURF_VAR)
            }

            val nameTv = TextView(this@MainActivity).apply {
                text = "${i + 1}. $toolLabel"
                textSize = 14f
                setTextColor(if (isSelected) NC.ON_SURFACE else NC.ON_SURF_VAR)
                typeface = Typeface.MONOSPACE
                layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
            }

            val closeBtn = ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.ic_close)
                setColorFilter(NC.ERROR)
                setPadding(dp(4), dp(4), dp(4), dp(4))
                layoutParams = LinearLayout.LayoutParams(dp(24), dp(24))
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

    private fun buildTerminalToolSelectorView(): ScrollView {
        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f)
            setBackgroundColor(NC.BG)
            isFillViewport = true
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
            layoutParams = FrameLayout.LayoutParams(MATCH, WRAP)
        }

        data class TermToolDef(val type: String, val label: String, val desc: String)

        val shellTools = listOf(
            TermToolDef("shell", "Debian Shell", "Debian Shell")
        )
        val freeTools = listOf(
            TermToolDef("opencode", "opencode", "Claude Agent")
        )
        val paidTools = listOf(
            TermToolDef("codex",       "codex",       "OpenAI Codex"),
            TermToolDef("agy",         "agy",         "Antigravity"),
            TermToolDef("claude-code", "claude-code", "Claude Code"),
            TermToolDef("qwen-code",   "qwen-code",   "Qwen Code"),
            TermToolDef("grok",        "grok",        "Grok CLI"),
            TermToolDef("kiro",        "kiro",        "Kiro CLI")
        ).let { list ->
            if (LinuxCommandBuilder.currentMethod == "chroot")
                list.filter { it.type != "codex" }
            else list
        }

        fun makeToolCard(tool: TermToolDef): LinearLayout {
            val card = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = cyberBrutalistBg(
                    fillColor = NC.SURFACE_LOW,
                    strokeColor = NC.OUTLINE_VAR,
                    shadowColor = NC.SHADOW_DARK,
                    offsetDp = 4,
                    cornerRadiusDp = 0
                )
                setPadding(dp(16), dp(20), dp(16), dp(18))
                layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).apply {
                    setMargins(dp(4), dp(4), dp(4), dp(4))
                }
                setOnClickListener { createNewTerminalSession(tool.type) }
            }

            val iconSize = dp(64)
            val iconView = ImageView(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply { bottomMargin = dp(10) }
                scaleType = ImageView.ScaleType.FIT_CENTER
            }

            val filename = if (tool.type == "qwen-code") "qwen-code.webp" else "${tool.type}.png"
            try {
                assets.open("images/cli/$filename").use {
                    val bmp = android.graphics.BitmapFactory.decodeStream(it)
                    if (bmp != null) iconView.setImageBitmap(bmp)
                }
            } catch (_: Exception) {
                iconView.setImageResource(R.drawable.ic_terminal_thick)
                iconView.setColorFilter(NC.PRIMARY)
            }

            card.addView(iconView)

            val nameTv = TextView(this@MainActivity).apply {
                text = tool.label
                textSize = 14f
                setTextColor(NC.ON_SURFACE)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
            }
            card.addView(nameTv)

            val descTv = TextView(this@MainActivity).apply {
                text = tool.desc
                textSize = 11f
                setTextColor(NC.ON_SURF_VAR)
                typeface = Typeface.MONOSPACE
                gravity = Gravity.CENTER
                setPadding(0, dp(2), 0, 0)
            }
            card.addView(descTv)

            return card
        }

        fun addSection(title: String, subtitle: String, tools: List<TermToolDef>) {
            val headerRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(4), dp(12), dp(4), dp(4))
            }
            val titleTv = TextView(this@MainActivity).apply {
                text = title
                textSize = 15f
                setTextColor(NC.PRIMARY)
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
            }
            val subTitleTv = TextView(this@MainActivity).apply {
                text = subtitle
                textSize = 10f
                setTextColor(NC.ON_SURF_VAR)
                typeface = Typeface.MONOSPACE
                gravity = Gravity.END
            }
            headerRow.addView(titleTv)
            headerRow.addView(subTitleTv)

            val divider = View(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(MATCH, dp(1)).apply { bottomMargin = dp(8); topMargin = dp(2) }
                setBackgroundColor(NC.OUTLINE_VAR)
            }

            container.addView(headerRow)
            container.addView(divider)

            tools.chunked(2).forEach { rowTools ->
                val row = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply {
                        bottomMargin = dp(4)
                    }
                }
                val card1 = makeToolCard(rowTools[0])
                row.addView(card1)
                if (rowTools.size > 1) {
                    val card2 = makeToolCard(rowTools[1])
                    row.addView(card2)
                } else {
                    val dummySpacer = View(this@MainActivity).apply {
                        layoutParams = LinearLayout.LayoutParams(0, dp(140), 1f).apply {
                            setMargins(dp(4), dp(4), dp(4), dp(4))
                        }
                    }
                    row.addView(dummySpacer)
                }
                container.addView(row)
            }
        }

        addSection("DEBIAN SHELL", "// SYSTEM SHELL", shellTools)
        addSection("FREE CLI TOOLS", "// OPEN SOURCE / FREE", freeTools)
        addSection("PAID CLI TOOLS", "// PRO / SUBSCRIPTION", paidTools)

        scrollView.addView(container)
        scrollView.post {
            scrollView.requestLayout()
            container.requestLayout()
            scrollView.invalidate()
        }
        return scrollView
    }

    /** Rebuild terminal tool selector so chroot/proot codex visibility stays in sync. */
    private fun refreshTerminalToolSelector() {
        if (!::terminalToolSelectorScrollView.isInitialized) return
        if (!::terminalWorkspaceLayout.isInitialized) return
        val oldVis = terminalToolSelectorScrollView.visibility
        val idx = terminalWorkspaceLayout.indexOfChild(terminalToolSelectorScrollView)
        terminalWorkspaceLayout.removeView(terminalToolSelectorScrollView)
        terminalToolSelectorScrollView = buildTerminalToolSelectorView().apply {
            visibility = oldVis
        }
        if (idx >= 0) {
            terminalWorkspaceLayout.addView(terminalToolSelectorScrollView, idx)
        } else {
            terminalWorkspaceLayout.addView(terminalToolSelectorScrollView)
        }
    }

    /** Rebuild workspace hub tool cards after linux_method change. */
    private fun refreshWorkspaceHubTools() {
        val host = workspaceHubToolsHost ?: return
        host.removeAllViews()
        populateWorkspaceHubTools(host)
    }

    private data class WorkspaceAiToolDef(
        val type: String,
        val label: String,
        val desc: String
    )

    private fun populateWorkspaceHubTools(host: LinearLayout) {
        val aiTools = listOf(
            WorkspaceAiToolDef("opencode", "opencode", "Claude Agent"),
            WorkspaceAiToolDef("codex", "codex", "OpenAI Codex"),
            WorkspaceAiToolDef("agy", "agy", "Antigravity"),
            WorkspaceAiToolDef("claude-code", "claude-code", "Claude Code"),
            WorkspaceAiToolDef("qwen-code", "qwen-code", "Qwen Code"),
            WorkspaceAiToolDef("grok", "grok", "Grok CLI"),
            WorkspaceAiToolDef("kiro", "kiro", "Kiro CLI"),
            WorkspaceAiToolDef("shell", "shell", "Debian Shell")
        ).let { list ->
            if (LinuxCommandBuilder.currentMethod == "chroot")
                list.filter { it.type != "codex" }
            else list
        }

        fun makeToolCard(tool: WorkspaceAiToolDef): LinearLayout {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = cyberBrutalistBg(
                    fillColor = NC.SURFACE_LOW,
                    strokeColor = NC.OUTLINE_VAR,
                    shadowColor = NC.SHADOW_DARK,
                    offsetDp = 4,
                    cornerRadiusDp = 0
                )
                setPadding(dp(16), dp(20), dp(16), dp(18))
                layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).apply {
                    setMargins(dp(4), dp(4), dp(4), dp(4))
                }
                setOnClickListener { createWorkspaceTerminalTab(tool.type) }
                setOnTouchListener { v, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            v.translationX = dp(2).toFloat()
                            v.translationY = dp(2).toFloat()
                            v.background = cyberBrutalistBg(
                                fillColor = NC.SURFACE_CONTAINER,
                                strokeColor = NC.PRIMARY,
                                shadowColor = NC.SHADOW_DARK,
                                offsetDp = 2,
                                cornerRadiusDp = 0
                            )
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            v.translationX = 0f
                            v.translationY = 0f
                            v.background = cyberBrutalistBg(
                                fillColor = NC.SURFACE_LOW,
                                strokeColor = NC.OUTLINE_VAR,
                                shadowColor = NC.SHADOW_DARK,
                                offsetDp = 4,
                                cornerRadiusDp = 0
                            )
                        }
                    }
                    false
                }
            }

            val iconSize = dp(64)
            val iconView = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply { bottomMargin = dp(10) }
                scaleType = ImageView.ScaleType.FIT_CENTER
            }

            val filename = if (tool.type == "qwen-code") "qwen-code.webp" else "${tool.type}.png"
            try {
                assets.open("images/cli/$filename").use {
                    val bmp = android.graphics.BitmapFactory.decodeStream(it)
                    if (bmp != null) iconView.setImageBitmap(bmp)
                }
            } catch (_: Exception) {
                iconView.setImageResource(R.drawable.ic_extension)
                iconView.setColorFilter(NC.PRIMARY)
            }

            card.addView(iconView)

            val nameTv = TextView(this).apply {
                text = tool.label
                textSize = 14f
                setTextColor(NC.ON_SURFACE)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
            }
            card.addView(nameTv)

            val descTv = TextView(this).apply {
                text = tool.desc
                textSize = 11f
                setTextColor(NC.ON_SURF_VAR)
                typeface = Typeface.MONOSPACE
                gravity = Gravity.CENTER
                setPadding(0, dp(2), 0, 0)
            }
            card.addView(descTv)

            return card
        }

        aiTools.chunked(2).forEach { pair ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(2) }
            }
            pair.forEach { tool -> row.addView(makeToolCard(tool)) }
            if (pair.size == 1) {
                val spacer = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).apply {
                        setMargins(dp(4), dp(4), dp(4), dp(4))
                    }
                }
                row.addView(spacer)
            }
            host.addView(row)
        }
    }

    private fun refreshToolCardsForMethod() {
        refreshTerminalToolSelector()
        refreshWorkspaceHubTools()
    }

    // ── Custom Cyber-Brutalist Bottom Navigation ────────────────────────────

    private lateinit var customBottomNav: LinearLayout
    private val navTabViews = ArrayList<LinearLayout>()
    private lateinit var customProjectBottomNav: LinearLayout
    private val projectNavTabViews = ArrayList<LinearLayout>()

    private fun buildCustomBottomNav(): LinearLayout {
        customBottomNav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(MATCH, dp(64))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(NC.SURFACE_LOWEST)
            }
        }

        val tabsData = listOf(
            Triple(ID_HOME, R.drawable.ic_home, "HOME"),
            Triple(ID_PROJECTS_LIST, R.drawable.ic_folder, "PROJECT"),
            Triple(ID_TERMINAL, R.drawable.ic_terminal, "TERMINAL"),
            Triple(ID_SETTINGS, R.drawable.ic_settings, "SETTINGS")
        )

        navTabViews.clear()
        for ((pageId, iconRes, label) in tabsData) {
            val tabLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(0, dp(6), 0, dp(6))
                layoutParams = LinearLayout.LayoutParams(0, MATCH, 1f)
                setOnClickListener {
                    if (pageStack.isEmpty() || pageStack.peek() != pageId) {
                        pageStack.push(pageId)
                    }
                    navigateToPage(pageId)
                }
            }

            val iconIv = ImageView(this).apply {
                tag = "TAB_ICON"
                setImageResource(iconRes)
                layoutParams = LinearLayout.LayoutParams(dp(26), dp(26))
            }
            val labelTv = TextView(this).apply {
                tag = "TAB_LABEL"
                text = label
                textSize = 11f
                typeface = Typeface.MONOSPACE
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply {
                    topMargin = dp(3)
                }
            }
            tabLayout.addView(iconIv)
            tabLayout.addView(labelTv)
            tabLayout.tag = pageId
            navTabViews.add(tabLayout)
            customBottomNav.addView(tabLayout)
        }

        updateCustomBottomNavSelection(ID_HOME)
        return customBottomNav
    }

    private fun updateCustomBottomNavSelection(activeId: Int) {
        val isProjectPage = activeId == ID_PROJECT_WORKSPACE ||
            activeId == ID_PROJECT_DIR_TREE ||
            activeId == ID_PROJECT_GIT_DIFF ||
            activeId == ID_PROJECT_SETTINGS

        // Toggle which nav is visible
        if (::customBottomNav.isInitialized) {
            customBottomNav.visibility = if (isProjectPage) View.GONE else View.VISIBLE
        }
        if (::customProjectBottomNav.isInitialized) {
            customProjectBottomNav.visibility = if (isProjectPage) View.VISIBLE else View.GONE
        }

        // Update global nav tab highlights
        if (::customBottomNav.isInitialized) {
            for (tab in navTabViews) {
                val pageId = tab.tag as? Int ?: continue
                val isSelected = when (pageId) {
                    ID_HOME -> activeId == ID_HOME
                    ID_PROJECTS_LIST -> activeId == ID_PROJECTS_LIST
                    ID_TERMINAL -> activeId == ID_TERMINAL
                    ID_SETTINGS -> activeId == ID_SETTINGS
                    else -> pageId == activeId
                }
                val iconIv = tab.findViewWithTag<ImageView>("TAB_ICON")
                val labelTv = tab.findViewWithTag<TextView>("TAB_LABEL")
                if (isSelected) {
                    tab.setBackgroundColor(NC.PRIMARY)
                    iconIv?.imageTintList = android.content.res.ColorStateList.valueOf(NC.SURFACE_LOWEST)
                    labelTv?.setTextColor(NC.SURFACE_LOWEST)
                    labelTv?.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                } else {
                    tab.setBackgroundColor(NC.SURFACE_LOWEST)
                    iconIv?.imageTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#99FFFFFF"))
                    labelTv?.setTextColor(Color.parseColor("#99FFFFFF"))
                    labelTv?.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
                }
            }
        }

        // Update project nav tab highlights
        if (::customProjectBottomNav.isInitialized) {
            for (tab in projectNavTabViews) {
                val pageId = tab.tag as? Int ?: continue
                val isSelected = pageId == activeId
                val iconIv = tab.findViewWithTag<ImageView>("TAB_ICON")
                val labelTv = tab.findViewWithTag<TextView>("TAB_LABEL")
                if (isSelected) {
                    tab.setBackgroundColor(NC.PRIMARY)
                    iconIv?.imageTintList = android.content.res.ColorStateList.valueOf(NC.SURFACE_LOWEST)
                    labelTv?.setTextColor(NC.SURFACE_LOWEST)
                    labelTv?.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                } else {
                    tab.setBackgroundColor(NC.SURFACE_LOWEST)
                    iconIv?.imageTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#99FFFFFF"))
                    labelTv?.setTextColor(Color.parseColor("#99FFFFFF"))
                    labelTv?.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
                }
            }
        }
    }

    private fun buildCustomProjectBottomNav(): LinearLayout {
        customProjectBottomNav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(MATCH, dp(64))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(NC.SURFACE_LOWEST)
            }
            visibility = View.GONE // hidden by default; shown when in project pages
        }

        val tabsData = listOf(
            Triple(ID_PROJECT_WORKSPACE, R.drawable.ic_home, "WORKSPACE"),
            Triple(ID_PROJECT_DIR_TREE, R.drawable.ic_folder, "DIRECTORY"),
            Triple(ID_PROJECT_GIT_DIFF, R.drawable.ic_git, "DIFF"),
            Triple(ID_PROJECT_SETTINGS, R.drawable.ic_project_config, "CONFIG")
        )

        projectNavTabViews.clear()
        for ((pageId, iconRes, label) in tabsData) {
            val tabLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(0, dp(6), 0, dp(6))
                layoutParams = LinearLayout.LayoutParams(0, MATCH, 1f)
                setOnClickListener {
                    if (pageStack.isEmpty() || pageStack.peek() != pageId) {
                        pageStack.push(pageId)
                    }
                    navigateToPage(pageId)
                }
            }
            val iconIv = ImageView(this).apply {
                tag = "TAB_ICON"
                setImageResource(iconRes)
                layoutParams = LinearLayout.LayoutParams(dp(26), dp(26))
            }
            val labelTv = TextView(this).apply {
                tag = "TAB_LABEL"
                text = label
                textSize = 11f
                typeface = Typeface.MONOSPACE
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(3) }
            }
            tabLayout.addView(iconIv)
            tabLayout.addView(labelTv)
            tabLayout.tag = pageId
            projectNavTabViews.add(tabLayout)
            customProjectBottomNav.addView(tabLayout)
        }

        return customProjectBottomNav
    }

    // ── Screen Builders ──────────────────────────────────────────────────────

    private fun buildHomeLayout() {
        homeScrollView = ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
            visibility = View.GONE
            isVerticalScrollBarEnabled = false
            background = TerminalScanlineDrawable()
        }
        homeLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(32))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
            background = TerminalScanlineDrawable()
        }
        homeScrollView.addView(homeLayout)

        // 1. Dashboard Banner & Quick Actions
        homeLayout.addView(buildHomeHeaderBanner())
        homeLayout.addView(spacer(14))

        // 2. System Telemetry Cards (SYS_CPU & SYS_MEM)
        homeLayout.addView(buildSystemTelemetryCards())

        // 3. Top 3 Recent Workspaces Section with Project Icon
        homeLayout.addView(buildRecentProjectsSection())
    }

    
    private fun createSystemTelemetryPlate(
        batTag: String = "HEADER_BAT",
        cpuTag: String = "HEADER_CPU",
        ramTag: String = "HEADER_RAM"
    ): LinearLayout {
        val fontNarrow = try { Typeface.createFromAsset(assets, "fonts/font.ttf") } catch (e: Exception) { Typeface.MONOSPACE }
        val terminalGreen = Color.parseColor("#3DDC84")

        val statusPlate = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = cyberBrutalistBg(
                fillColor = NC.SURFACE_LOW,
                strokeColor = Color.parseColor("#3c4a3f"),
                shadowColor = NC.SHADOW_GREEN,
                offsetDp = 4,
                cornerRadiusDp = 0
            )
            setPadding(dp(8), dp(2), dp(8), dp(2))
            layoutParams = LinearLayout.LayoutParams(WRAP, dp(34))
        }

        val batIcon = ImageView(this).apply {
            tag = "${batTag}_ICON"
            setImageResource(if (isBatteryCharging()) R.drawable.ic_battery_charging else R.drawable.ic_battery_discharging)
            setColorFilter(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(dp(16), dp(16)).apply {
                rightMargin = dp(4)
                gravity = Gravity.CENTER_VERTICAL
            }
        }
        val batTv = TextView(this).apply {
            tag = batTag
            text = "${readBatteryUsage()}%"
            textSize = 12f
            setTextColor(Color.WHITE)
            typeface = fontNarrow
        }
        val batContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(batIcon)
            addView(batTv)
        }

        val divider1 = View(this).apply {
            setBackgroundColor(Color.parseColor("#26FFFFFF"))
            layoutParams = LinearLayout.LayoutParams(dp(1), dp(12)).apply {
                leftMargin = dp(6)
                rightMargin = dp(6)
            }
        }

        val cpuTvHeader = TextView(this).apply {
            tag = cpuTag
            text = "C: ${readCpuUsage()}%"
            textSize = 12f
            setTextColor(terminalGreen)
            typeface = fontNarrow
        }

        val divider2 = View(this).apply {
            setBackgroundColor(Color.parseColor("#26FFFFFF"))
            layoutParams = LinearLayout.LayoutParams(dp(1), dp(12)).apply {
                leftMargin = dp(6)
                rightMargin = dp(6)
            }
        }

        val ramTvHeader = TextView(this).apply {
            tag = ramTag
            text = "R: ${readMemUsage()}"
            textSize = 12f
            setTextColor(terminalGreen)
            typeface = fontNarrow
        }

        statusPlate.addView(batContainer)
        statusPlate.addView(divider1)
        statusPlate.addView(cpuTvHeader)
        statusPlate.addView(divider2)
        statusPlate.addView(ramTvHeader)

        return statusPlate
    }

    private fun cyberBrutalistBg(
        fillColor: Int,
        strokeColor: Int = Color.parseColor("#3c4a3f"),
        shadowColor: Int = Color.parseColor("#393939"),
        offsetDp: Int = 6,
        cornerRadiusDp: Int = 0,
        rightFaceColor: Int = Color.parseColor("#3c4a3f")
    ): LayerDrawable {
        val bottomShadow = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(shadowColor)
            if (cornerRadiusDp > 0) cornerRadius = dp(cornerRadiusDp).toFloat()
        }
        val rightShadow = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(rightFaceColor)
            if (cornerRadiusDp > 0) cornerRadius = dp(cornerRadiusDp).toFloat()
        }
        val frontDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fillColor)
            setStroke(dp(1), strokeColor)
            if (cornerRadiusDp > 0) cornerRadius = dp(cornerRadiusDp).toFloat()
        }
        val layers = arrayOf<Drawable>(bottomShadow, rightShadow, frontDrawable)
        val off = dp(offsetDp)
        return LayerDrawable(layers).apply {
            setLayerInset(0, off, off, 0, 0)
            setLayerInset(1, off, 0, 0, off)
            setLayerInset(2, 0, 0, off, off)
        }
    }

    private fun createHoveringNewProjectFab(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            
            background = cyberBrutalistBg(
                fillColor = NC.PRIMARY_CON,
                strokeColor = Color.parseColor("#3c4a3f"),
                shadowColor = NC.SHADOW_DARK,
                offsetDp = 6,
                cornerRadiusDp = 0,
                rightFaceColor = Color.parseColor("#3c4a3f")
            )
            setPadding(dp(16), dp(12), dp(18), dp(12))
            elevation = dp(8).toFloat()

            val iconIv = ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.ic_add)
                setColorFilter(NC.ON_PRIMARY)
                layoutParams = LinearLayout.LayoutParams(dp(18), dp(18)).apply { rightMargin = dp(6) }
            }

            val textTv = TextView(this@MainActivity).apply {
                text = "NEW PROJECT"
                textSize = 13f
                setTextColor(NC.ON_PRIMARY)
                typeface = Typeface.MONOSPACE
                paint.isFakeBoldText = true
            }

            addView(iconIv)
            addView(textTv)

            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        v.translationX = dp(4).toFloat()
                        v.translationY = dp(4).toFloat()
                        v.background = cyberBrutalistBg(
                            fillColor = Color.parseColor("#49E48F"),
                            strokeColor = Color.parseColor("#3c4a3f"),
                            shadowColor = NC.SHADOW_DARK,
                            offsetDp = 2,
                            cornerRadiusDp = 0,
                            rightFaceColor = Color.parseColor("#3c4a3f")
                        )
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.translationX = 0f
                        v.translationY = 0f
                        v.background = cyberBrutalistBg(
                            fillColor = NC.PRIMARY_CON,
                            strokeColor = Color.parseColor("#3c4a3f"),
                            shadowColor = NC.SHADOW_DARK,
                            offsetDp = 6,
                            cornerRadiusDp = 0,
                            rightFaceColor = Color.parseColor("#3c4a3f")
                        )
                    }
                }
                false
            }

            setOnClickListener {
                if (pageStack.isEmpty() || pageStack.peek() != ID_PROJECT_CREATE) {
                    pageStack.push(ID_PROJECT_CREATE)
                }
                navigateToPage(ID_PROJECT_CREATE)
            }
        }
    }

    private fun buildSystemTelemetryCards(): View {
        return androidx.compose.ui.platform.ComposeView(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply {
                leftMargin = -dp(16)
                rightMargin = -dp(16)
                bottomMargin = dp(16)
            }
            setContent {
                SystemTelemetryCards(
                    cpuPercentage = composeCpuState.intValue,
                    memPercentage = composeMemState.intValue,
                    ramUsedMb = composeRamUsedState.longValue,
                    ramTotalMb = composeRamTotalState.longValue,
                    swapUsedMb = composeSwapUsedState.longValue,
                    swapTotalMb = composeSwapTotalState.longValue,
                    diskPercentage = composeDiskState.intValue
                )
            }
        }
    }

    private fun buildHomeHeaderBanner(): View {
        val heroCard = FrameLayout(this).apply {
            background = cyberBrutalistBg(
                fillColor = NC.SURFACE_LOW,
                strokeColor = Color.parseColor("#3c4a3f"),
                shadowColor = NC.SHADOW_GREEN,
                offsetDp = 6
            )
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }

        val bgIv = object : ImageView(this) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), 0)
            }
        }.apply {
            setImageResource(R.drawable.hero_bg)
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = 0.5f
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
        }
        heroCard.addView(bgIv)

        val overlay = object : View(this) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), 0)
            }
        }.apply {
            setBackgroundColor(Color.parseColor("#B00E0E0E"))
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
        }
        heroCard.addView(overlay)

        val contentCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            layoutParams = FrameLayout.LayoutParams(MATCH, WRAP)
        }

        val titleTv = TextView(this).apply {
            text = "Welcome to\nNativeCode"
            textSize = 24f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setLineSpacing(0f, 1.15f)
        }

        val subtextTv = TextView(this).apply {
            text = "Full Linux development environment on Android. Run AI coding assistants, code in Node.js, Python, or C++, and build projects on your device."
            textSize = 11f
            setTextColor(Color.parseColor("#D0D0D0"))
            typeface = Typeface.MONOSPACE
            setPadding(0, dp(10), 0, dp(16))
            setLineSpacing(0f, 1.25f)
        }

        val createBtn = TextView(this).apply {
            text = "  CREATE NEW PROJECT"
            textSize = 13f
            setTextColor(Color.WHITE)
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_add, 0, 0, 0)
            compoundDrawableTintList = android.content.res.ColorStateList.valueOf(NC.PRIMARY)
            background = cyberBrutalistBg(
                fillColor = Color.parseColor("#CC121212"),
                strokeColor = Color.parseColor("#3DDC84"),
                shadowColor = NC.SHADOW_GREEN,
                offsetDp = 4
            )
            setPadding(dp(16), dp(12), dp(16), dp(12))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
            setOnClickListener {
                if (pageStack.isEmpty() || pageStack.peek() != ID_PROJECT_CREATE) {
                    pageStack.push(ID_PROJECT_CREATE)
                }
                navigateToPage(ID_PROJECT_CREATE)
            }
            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> v.animate().translationX(dp(2).toFloat()).translationY(dp(2).toFloat()).setDuration(60).start()
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate().translationX(0f).translationY(0f).setDuration(60).start()
                }
                false
            }
        }

        contentCol.addView(titleTv)
        contentCol.addView(subtextTv)
        contentCol.addView(createBtn)
        heroCard.addView(contentCol)

        return heroCard
    }

    private fun animateHomeLayoutEntrance() {
        if (!::homeLayout.isInitialized) return
        homeLayout.post {
            for (i in 0 until homeLayout.childCount) {
                val child = homeLayout.getChildAt(i)
                child.alpha = 0f
                child.translationY = dp(24).toFloat()
                child.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(350)
                    .setStartDelay((i * 50).toLong())
                    .setInterpolator(android.view.animation.DecelerateInterpolator())
                    .start()
            }
        }
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
            text = "Project Root"; textSize = 15f; setTextColor(NC.ON_SURFACE); typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        val newFolder = TextView(this).apply { text = "+ Folder"; textSize = 16f; setTextColor(NC.ON_SURF_VAR); setPadding(dp(8), 0, dp(4), 0) }
        val newFile = TextView(this).apply { text = "+ File"; textSize = 16f; setTextColor(NC.ON_SURF_VAR) }
        header.addView(title); header.addView(newFolder); header.addView(newFile)
        rootPanel.addView(header)

        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(4), dp(8), dp(8))
        }
        for ((icon, name, isFolder) in listOf(
            Triple("", "app", true),
            Triple("", "gradle", true),
            Triple("", ".gitignore", false),
            Triple("", "build.gradle.kts", false),
            Triple("", "settings.gradle.kts", false)
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
            text = "Git Changes (2)"; textSize = 15f; setTextColor(NC.ON_SURFACE); typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        val commitBtn = TextView(this).apply { text = "Commit"; textSize = 13f; setTextColor(NC.PRIMARY); typeface = Typeface.DEFAULT_BOLD }
        gitHeader.addView(gitTitle); gitHeader.addView(commitBtn)
        gitPanel.addView(gitHeader)

        val gitList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(4), dp(8), dp(8))
        }
        for (name in listOf("MainActivity.kt", "build.gradle.kts")) {
            val row = fileRow("", name, false, modified = true)
            row.setOnClickListener {
                showDiffViewer(name)
            }
            gitList.addView(row)
        }
        gitPanel.addView(gitList)
        fileExplorerLayout.addView(gitPanel)
    }

    // ── Key injection helper ───────────────────────────────────────────────────
    private val SPECIAL_KEY_CODES = mapOf(
        "ESC"   to KeyEvent.KEYCODE_ESCAPE,
        "TAB"   to KeyEvent.KEYCODE_TAB,
        "ENTER" to KeyEvent.KEYCODE_ENTER,
        "BKSP"  to KeyEvent.KEYCODE_DEL,
        "DEL"   to KeyEvent.KEYCODE_FORWARD_DEL,
        "UP"    to KeyEvent.KEYCODE_DPAD_UP,
        "DOWN"  to KeyEvent.KEYCODE_DPAD_DOWN,
        "LEFT"  to KeyEvent.KEYCODE_DPAD_LEFT,
        "RIGHT" to KeyEvent.KEYCODE_DPAD_RIGHT,
        "HOME"  to KeyEvent.KEYCODE_MOVE_HOME,
        "END"   to KeyEvent.KEYCODE_MOVE_END,
        "PGUP"  to KeyEvent.KEYCODE_PAGE_UP,
        "PGDN"  to KeyEvent.KEYCODE_PAGE_DOWN,
        "INS"   to KeyEvent.KEYCODE_INSERT,
        "F1"    to KeyEvent.KEYCODE_F1,
        "F2"    to KeyEvent.KEYCODE_F2,
        "F3"    to KeyEvent.KEYCODE_F3,
        "F4"    to KeyEvent.KEYCODE_F4,
        "F5"    to KeyEvent.KEYCODE_F5,
        "F6"    to KeyEvent.KEYCODE_F6,
        "F7"    to KeyEvent.KEYCODE_F7,
        "F8"    to KeyEvent.KEYCODE_F8,
        "F9"    to KeyEvent.KEYCODE_F9,
        "F10"   to KeyEvent.KEYCODE_F10,
        "F11"   to KeyEvent.KEYCODE_F11,
        "F12"   to KeyEvent.KEYCODE_F12
    )

    private fun injectKey(tv: TerminalView?, key: String, ctrl: Boolean, alt: Boolean, shift: Boolean) {
        tv ?: return
        val keyCode = SPECIAL_KEY_CODES[key]
        if (keyCode != null) {
            var meta = 0
            if (ctrl)  meta = meta or KeyEvent.META_CTRL_ON  or KeyEvent.META_CTRL_LEFT_ON
            if (alt)   meta = meta or KeyEvent.META_ALT_ON   or KeyEvent.META_ALT_LEFT_ON
            if (shift) meta = meta or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
            val evDown = KeyEvent(0, 0, KeyEvent.ACTION_DOWN, keyCode, 0, meta)
            tv.onKeyDown(keyCode, evDown)
            val evUp = KeyEvent(0, 0, KeyEvent.ACTION_UP, keyCode, 0, meta)
            tv.onKeyUp(keyCode, evUp)
        } else {
            key.codePoints().forEach { cp -> tv.inputCodePoint(cp, ctrl, alt) }
        }
    }

    /** App terminal → global method; workspace → active project method. */
    private fun resolveAttachMethod(isWorkspace: Boolean): String {
        return if (isWorkspace) {
            activeProjectMethod.ifBlank { LinuxCommandBuilder.currentMethod }
        } else {
            LinuxCommandBuilder.currentMethod
        }
    }

    private fun mimeToImageExt(mime: String?): String {
        val m = mime?.lowercase(Locale.US) ?: return "jpg"
        return when {
            m.contains("png") -> "png"
            m.contains("webp") -> "webp"
            m.contains("gif") -> "gif"
            m.contains("jpeg") || m.contains("jpg") -> "jpg"
            m.startsWith("image/") -> m.substringAfterLast('/').substringBefore(';').ifBlank { "jpg" }
            else -> "jpg"
        }
    }

    private fun setImagePathClipboard(guestPath: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("image_path", guestPath))
    }

    /**
     * Stage under app UID, then install into proot rootfs (direct) or chroot (root cp).
     * Guest clipboard path is always a Debian path when verified.
     */
    private fun handleImageAttachment(uri: Uri, isWorkspace: Boolean) {
        val method = resolveAttachMethod(isWorkspace)
        val mime = contentResolver.getType(uri)
        if (mime != null && !mime.startsWith("image/")) {
            Toast.makeText(this, "Not an image", Toast.LENGTH_SHORT).show()
            return
        }
        val ext = mimeToImageExt(mime)
        val fname = "attach_${System.currentTimeMillis()}.$ext"
        val guestPath = if (isWorkspace && activeProjectPath.isNotBlank()) {
            "${activeProjectPath.trimEnd('/')}/$fname"
        } else {
            "/home/flux/$fname"
        }

        executor.execute {
            try {
                val stageDir = ProjectPathResolver.stageAttachDir(this@MainActivity)
                val stageFile = File(stageDir, fname)
                contentResolver.openInputStream(uri)?.use { inp ->
                    FileOutputStream(stageFile).use { out -> inp.copyTo(out) }
                } ?: run {
                    mainHandler.post {
                        Toast.makeText(this@MainActivity, "Failed to read image", Toast.LENGTH_SHORT).show()
                    }
                    return@execute
                }
                if (!stageFile.isFile || stageFile.length() <= 0L) {
                    mainHandler.post {
                        Toast.makeText(this@MainActivity, "Image copy empty", Toast.LENGTH_SHORT).show()
                    }
                    return@execute
                }

                val finalGuestPath: String
                if (method == "chroot") {
                    if (!RootShell.isRootAvailable()) {
                        finalGuestPath = ProjectPathResolver.stageAttachGuestPath(fname)
                        Log.w(
                            "ImageAttach",
                            "chroot no root; bind fallback path=$finalGuestPath stage=${stageFile.absolutePath}"
                        )
                        mainHandler.post {
                            setImagePathClipboard(finalGuestPath)
                            Toast.makeText(
                                this@MainActivity,
                                "No root for /home/flux — using $finalGuestPath",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        return@execute
                    }
                    val result = RootShell.copyIntoChroot(
                        hostSrc = stageFile,
                        guestAbsPath = guestPath,
                        chrootPath = ChrootCommandBuilder.CHROOT_PATH
                    )
                    if (result.exitCode != 0) {
                        Log.e(
                            "ImageAttach",
                            "chroot cp fail code=${result.exitCode} out=${result.stdout.take(200)}"
                        )
                        mainHandler.post {
                            Toast.makeText(
                                this@MainActivity,
                                "Chroot copy failed (exit ${result.exitCode})",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        return@execute
                    }
                    stageFile.delete()
                    finalGuestPath = guestPath
                } else {
                    val hostTarget = ProjectPathResolver.resolve(this@MainActivity, guestPath, method)
                    hostTarget.parentFile?.mkdirs()
                    stageFile.copyTo(hostTarget, overwrite = true)
                    if (!hostTarget.isFile || hostTarget.length() <= 0L) {
                        mainHandler.post {
                            Toast.makeText(this@MainActivity, "Proot write failed", Toast.LENGTH_SHORT).show()
                        }
                        return@execute
                    }
                    stageFile.delete()
                    finalGuestPath = guestPath
                }

                Log.i(
                    "ImageAttach",
                    "ok method=$method workspace=$isWorkspace guest=$finalGuestPath"
                )
                mainHandler.post {
                    setImagePathClipboard(finalGuestPath)
                    Toast.makeText(
                        this@MainActivity,
                        "Copied: $finalGuestPath",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Log.e("ImageAttach", "Failed to copy image", e)
                mainHandler.post {
                    Toast.makeText(
                        this@MainActivity,
                        "Attach failed: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun makeToolbarKeyBtn(label: String, widePad: Boolean = false, cornerRadius: Int = dp(5), marginRight: Int = dp(5), height: Int = WRAP, exactWidth: Int? = null, iconResId: Int? = null): TextView {
        return TextView(this).apply {
            text = if (iconResId != null) "" else label
            if (iconResId != null) {
                setCompoundDrawablesWithIntrinsicBounds(iconResId, 0, 0, 0)
                compoundDrawableTintList = android.content.res.ColorStateList.valueOf(NC.ON_SURF_VAR)
            }
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextColor(NC.ON_SURF_VAR)
            background = roundedBg(NC.SURFACE, NC.BORDER, cornerRadius)
            val hp = if (widePad) dp(10) else dp(8)
            setPadding(hp, dp(6), hp, dp(6))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(exactWidth ?: WRAP, height).apply { rightMargin = marginRight }
        }
    }

    private fun makeModifierBtn(label: String, state: () -> Boolean, cornerRadius: Int = dp(5), marginRight: Int = dp(5), onPress: () -> Unit, onLongPress: () -> Unit): TextView {
        val btn = TextView(this).apply {
            text = label
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextColor(NC.ON_SURF_VAR)
            background = roundedBg(NC.SURFACE, NC.BORDER, cornerRadius)
            setPadding(dp(9), dp(6), dp(9), dp(6))
            layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).apply { rightMargin = marginRight }
        }
        fun updateStyle() {
            if (state()) {
                btn.setTextColor(NC.PRIMARY)
                btn.background = roundedBg(
                    (NC.PRIMARY and 0x00FFFFFF) or 0x33000000, NC.PRIMARY, cornerRadius
                )
            } else {
                btn.setTextColor(NC.ON_SURF_VAR)
                btn.background = roundedBg(NC.SURFACE, NC.BORDER, cornerRadius)
            }
        }
        btn.setOnClickListener { onPress(); updateStyle() }
        btn.setOnLongClickListener { onLongPress(); updateStyle(); true }
        return btn
    }

    // ── Shared special-keys toolbar builder ────────────────────────────────────
    // Returns a LinearLayout (vertical, 2 rows + optional F-keys row)
    @Suppress("UNUSED_PARAMETER")
    private fun buildSpecialKeysToolbar(
        tvRef: () -> TerminalView?,
        sessionRef: () -> TerminalSession?,
        modState: ModifierState,
        onPickImage: () -> Unit
    ): LinearLayout {
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
            background = roundedBg(NC.SURFACE, NC.BORDER, 0)
        }
        
        val metrics = resources.displayMetrics
        val keyWidth = metrics.widthPixels / 8

        // ── Row 1: Modifiers + core keys + attach ──────────────────────────
        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 0)
        }

        fun consumeModifiers() {
            if (!modState.ctrlLocked) modState.ctrlActive = false
            if (!modState.altLocked)  modState.altActive  = false
            if (!modState.shiftLocked) modState.shiftActive = false
        }

        // Modifier buttons — we hold refs so we can refresh them
        val ctrlBtn = makeModifierBtn(
            "CTRL", { modState.ctrlActive }, cornerRadius = 0, marginRight = 0,
            onPress = {
                if (modState.ctrlLocked) {
                    modState.ctrlActive = false; modState.ctrlLocked = false
                } else {
                    modState.ctrlActive = !modState.ctrlActive
                    if (!modState.ctrlActive) modState.ctrlLocked = false
                }
            },
            onLongPress = { modState.ctrlActive = true; modState.ctrlLocked = true }
        ).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply { rightMargin = 0 }
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 0)
        }
        val altBtn = makeModifierBtn(
            "ALT", { modState.altActive }, cornerRadius = 0, marginRight = 0,
            onPress = {
                if (modState.altLocked) {
                    modState.altActive = false; modState.altLocked = false
                } else {
                    modState.altActive = !modState.altActive
                    if (!modState.altActive) modState.altLocked = false
                }
            },
            onLongPress = { modState.altActive = true; modState.altLocked = true }
        ).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply { rightMargin = 0 }
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 0)
        }
        val shiftBtn = makeModifierBtn(
            "SHFT", { modState.shiftActive }, cornerRadius = 0, marginRight = 0,
            onPress = {
                if (modState.shiftLocked) {
                    modState.shiftActive = false; modState.shiftLocked = false
                } else {
                    modState.shiftActive = !modState.shiftActive
                    if (!modState.shiftActive) modState.shiftLocked = false
                }
            },
            onLongPress = { modState.shiftActive = true; modState.shiftLocked = true }
        ).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply { rightMargin = 0 }
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 0)
        }

        // Helper: refresh modifier button appearances (called after a key is consumed)
        fun refreshModBtns() {
            listOf(ctrlBtn to { modState.ctrlActive },
                   altBtn  to { modState.altActive },
                   shiftBtn to { modState.shiftActive }).forEach { (btn, activeGetter) ->
                if (activeGetter()) {
                    btn.setTextColor(NC.PRIMARY)
                    btn.background = roundedBg((NC.PRIMARY and 0x00FFFFFF) or 0x33000000, NC.PRIMARY, 0)
                } else {
                    btn.setTextColor(NC.ON_SURF_VAR)
                    btn.background = roundedBg(NC.SURFACE, NC.BORDER, 0)
                }
            }
        }
        modState.onStateChanged = { runOnUiThread { refreshModBtns() } }

        // Core key row 1: ESC TAB ENTER BKSP
        data class KeyDef(val label: String, val key: String)
        val coreKeys1 = listOf(
            KeyDef("ESC", "ESC"), KeyDef("TAB", "TAB"), KeyDef("ENT", "ENTER"), KeyDef("BKSP", "BKSP")
        )
        val coreBtn1 = coreKeys1.map { kd ->
            makeToolbarKeyBtn(kd.label, cornerRadius = 0, marginRight = 0).apply {
                layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply { rightMargin = 0 }
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 0)
            }.also { btn ->
                btn.setOnClickListener {
                    injectKey(tvRef(), kd.key, modState.ctrlActive, modState.altActive, modState.shiftActive)
                    consumeModifiers(); refreshModBtns()
                }
            }
        }

        // Image attach button
        val attachBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_attach_image)
            setColorFilter(NC.ON_SURF_VAR)
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = roundedBg(NC.SURFACE, NC.BORDER, 0)
            setPadding(dp(13), dp(13), dp(13), dp(13))
            layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply { rightMargin = 0 }
            setOnClickListener { onPickImage() }
        }

        row1.addView(ctrlBtn); row1.addView(altBtn); row1.addView(shiftBtn)
        coreBtn1.forEach { row1.addView(it) }
        row1.addView(attachBtn)

        // ── Row 2: Scrollable special keys ────────────────────────────────
        val row2scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 0)
        }

        // Arrow cluster
        val arrowKeys = listOf(
            Triple(R.drawable.ic_arrow_left, "LEFT", "←"),
            Triple(R.drawable.ic_arrow_up, "UP", "↑"),
            Triple(R.drawable.ic_arrow_down, "DOWN", "↓"),
            Triple(R.drawable.ic_arrow_right, "RIGHT", "→")
        )
        arrowKeys.forEach { (iconId, key, label) ->
            val btn = makeToolbarKeyBtn(label, marginRight = 0, height = dp(44), exactWidth = keyWidth, iconResId = iconId)
            btn.setOnClickListener {
                injectKey(tvRef(), key, modState.ctrlActive, modState.altActive, modState.shiftActive)
                consumeModifiers(); refreshModBtns()
            }
            row2.addView(btn)
        }

        // Nav cluster divider
        val div2 = View(this).apply {
            setBackgroundColor(NC.BORDER)
            layoutParams = LinearLayout.LayoutParams(dp(1), dp(44))
        }
        // row2.addView(div2)

        val navKeys = listOf(
            Triple(R.drawable.ic_backspace, "DEL", "⌦"),
            Triple(null, "INS", "Ins")
        )
        navKeys.forEach { (iconId, key, label) ->
            val btn = makeToolbarKeyBtn(label, marginRight = 0, height = dp(44), exactWidth = keyWidth, iconResId = iconId)
            btn.setOnClickListener {
                injectKey(tvRef(), key, modState.ctrlActive, modState.altActive, modState.shiftActive)
                consumeModifiers(); refreshModBtns()
            }
            row2.addView(btn)
        }

        // Symbol cluster divider
        val div3 = View(this).apply {
            setBackgroundColor(NC.BORDER)
            layoutParams = LinearLayout.LayoutParams(dp(1), dp(44))
        }
        // row2.addView(div3)

        val symKeys = listOf("/", "|", "~", "-", "_", "\\")
        symKeys.forEach { sym ->
            val btn = makeToolbarKeyBtn(sym, marginRight = 0, height = dp(44), exactWidth = keyWidth)
            btn.setOnClickListener {
                injectKey(tvRef(), sym, modState.ctrlActive, modState.altActive, modState.shiftActive)
                consumeModifiers(); refreshModBtns()
            }
            row2.addView(btn)
        }

        // F-key toggle
        val div4 = View(this).apply {
            setBackgroundColor(NC.BORDER)
            layoutParams = LinearLayout.LayoutParams(dp(1), dp(44))
        }
        // row2.addView(div4)

        var fRowVisible = false
        val fnBtn = makeToolbarKeyBtn("Fn", marginRight = 0, height = dp(44), exactWidth = keyWidth)
        row2.addView(fnBtn)

        // F-keys row (hidden by default)
        val fRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
        }
        val fScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
            setPadding(0, 0, 0, 0)
        }
        (1..12).forEach { n ->
            val btn = makeToolbarKeyBtn("F$n", marginRight = 0, height = dp(44), exactWidth = keyWidth)
            btn.setOnClickListener {
                injectKey(tvRef(), "F$n", modState.ctrlActive, modState.altActive, modState.shiftActive)
                consumeModifiers(); refreshModBtns()
            }
            fRow.addView(btn)
        }
        fScroll.addView(fRow)

        fnBtn.setOnClickListener {
            fRowVisible = !fRowVisible
            fScroll.visibility = if (fRowVisible) View.VISIBLE else View.GONE
            fnBtn.setTextColor(if (fRowVisible) NC.PRIMARY else NC.ON_SURF_VAR)
            fnBtn.background = if (fRowVisible)
                roundedBg((NC.PRIMARY and 0x00FFFFFF) or 0x33000000, NC.PRIMARY, dp(5))
            else roundedBg(NC.SURFACE, NC.BORDER, dp(5))
        }

        row2scroll.addView(row2)

        wrapper.addView(row1)
        wrapper.addView(row2scroll)
        wrapper.addView(fScroll)

        return wrapper
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
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(NC.SURFACE_LOWEST)
                setStroke(dp(1), Color.parseColor("#3C4A3F"))
            }
            setPadding(dp(12), dp(8), dp(12), dp(8))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        val menuTerminalBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_menu)
            setColorFilter(NC.ON_SURFACE)
            setPadding(dp(6), dp(6), dp(6), dp(6))
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
            setOnClickListener {
                if (drawerLayout.isDrawerOpen(sidebarLayout)) {
                    drawerLayout.closeDrawer(sidebarLayout)
                } else {
                    drawerLayout.openDrawer(sidebarLayout)
                }
            }
        }
        val termSysPlate = createSystemTelemetryPlate("TERM_HEADER_BAT", "TERM_HEADER_CPU", "TERM_HEADER_RAM").apply {
            layoutParams = LinearLayout.LayoutParams(WRAP, dp(34)).apply {
                leftMargin = dp(8)
            }
        }
        val spacerTerminal = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(1), 1f)
        }
        val addTerminalWorkspaceBtn = ImageView(this).apply {
            tag = "ADD_TERM_BTN"
            setImageResource(R.drawable.ic_add)
            setColorFilter(NC.ON_SURFACE)
            setPadding(dp(6), dp(6), dp(6), dp(6))
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
            setOnClickListener {
                showNewTerminalDropdown(this) { type ->
                    createNewTerminalSession(type)
                }
            }
        }
        toggleExtraKeysBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_keyboard)
            setColorFilter(if (showExtraKeys) NC.PRIMARY else NC.ON_SURF_VAR)
            setPadding(dp(6), dp(6), dp(6), dp(6))
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply { rightMargin = dp(4) }
            setOnClickListener {
                setExtraKeysEnabled(!showExtraKeys)
                setColorFilter(if (showExtraKeys) NC.PRIMARY else NC.ON_SURF_VAR)
            }
        }
        terminalTopBar.addView(menuTerminalBtn)
        terminalTopBar.addView(termSysPlate)
        terminalTopBar.addView(spacerTerminal)
        terminalTopBar.addView(toggleExtraKeysBtn)
        terminalTopBar.addView(addTerminalWorkspaceBtn)
        terminalWorkspaceLayout.addView(terminalTopBar)

        terminalToolSelectorScrollView = buildTerminalToolSelectorView().apply {
            visibility = View.VISIBLE
        }
        terminalWorkspaceLayout.addView(terminalToolSelectorScrollView)

        terminalViewContainer = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f)
            visibility = View.GONE
        }
        terminalView = TerminalView(this, null).apply {
            isFocusable = false; isFocusableInTouchMode = false
        }
        registerForContextMenu(terminalView)
        terminalViewContainer.addView(terminalView)
        terminalViewContainer.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            forceTerminalResize(if (::terminalView.isInitialized) terminalView else null)
        }
        terminalWorkspaceLayout.addView(terminalViewContainer)

        // Full special-keys toolbar (replaces old dummy bar + attach bar)
        terminalKeyboardToolbar = buildSpecialKeysToolbar(
            tvRef = { if (::terminalView.isInitialized) terminalView else null },
            sessionRef = { terminalSession },
            modState = termModState,
            onPickImage = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    termImagePickerLauncher.launch("image/*")
                } else {
                    termImagePickerLauncher.launch("image/*")
                }
            }
        ).apply {
            visibility = if (showExtraKeys) View.VISIBLE else View.GONE
        }
        terminalWorkspaceLayout.addView(terminalKeyboardToolbar)
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
        val cloneBtn = outlineBtn(" Clone Repository")
        cloneBtn.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_download, 0, 0, 0)
        cloneBtn.compoundDrawableTintList = android.content.res.ColorStateList.valueOf(NC.PRIMARY)
        cloneBtn.layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).apply { rightMargin = dp(8) }
        val pushBtn = primaryBtn(" Push")
        pushBtn.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_upload, 0, 0, 0)
        pushBtn.compoundDrawableTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
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
            clipToPadding = false
            setPadding(0, 0, 0, dp(80))
            setBackgroundColor(NC.BG)
        }
        settingsHubLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        settingsHubScrollView.addView(settingsHubLayout)

        val headerCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(16))
        }
        val headerTitleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val headerIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_tune_thick)
            setColorFilter(NC.PRIMARY)
            layoutParams = LinearLayout.LayoutParams(dp(26), dp(26)).apply { rightMargin = dp(10) }
        }
        val titleTv = TextView(this).apply {
            text = "SETTINGS HUB"
            textSize = 24f
            setTextColor(NC.PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
        }
        headerTitleRow.addView(headerIcon)
        headerTitleRow.addView(titleTv)
        val subTitleTv = TextView(this).apply {
            text = "// SYSTEM CONFIGURATION & PREFERENCES"
            textSize = 11f
            setTextColor(NC.ON_SURF_VAR)
            typeface = Typeface.MONOSPACE
            setPadding(0, dp(4), 0, dp(12))
        }
        val divider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, dp(1)).apply { bottomMargin = dp(16) }
            setBackgroundColor(NC.OUTLINE_VAR)
        }
        headerCol.addView(headerTitleRow)
        headerCol.addView(subTitleTv)
        headerCol.addView(divider)
        settingsHubLayout.addView(headerCol)

        // Graphical Desktop card
        settingsHubLayout.addView(buildGuiLaunchCard())
        settingsHubLayout.addView(spacer(16))

        // Terminal Settings card
        settingsHubLayout.addView(buildTerminalSettingsCard())
        settingsHubLayout.addView(spacer(16))

        // Environment card (proot / chroot switch)
        settingsHubLayout.addView(buildEnvironmentCard())
        settingsHubLayout.addView(spacer(16))

        // Proot Settings — size-only (app storage rootfs); above chroot
        settingsHubLayout.addView(buildProotSettingsSectionButton())
        settingsHubLayout.addView(spacer(16))

        // Chroot Settings — opens dedicated page (size / root / uninstall)
        settingsHubLayout.addView(buildChrootSettingsSectionButton())
        settingsHubLayout.addView(spacer(16))

        // Repairs (host / guest / chroot fix scripts)
        settingsHubLayout.addView(buildScriptsSectionButton())
        settingsHubLayout.addView(spacer(16))

        // Onboarding Setup
        settingsHubLayout.addView(buildOnboardingSectionButton())
    }

    private fun buildScriptsSectionButton(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = cyberBrutalistBg(
                fillColor = NC.SURFACE_LOW,
                strokeColor = NC.OUTLINE_VAR,
                shadowColor = NC.SHADOW_DARK,
                offsetDp = 6,
                cornerRadiusDp = 0
            )
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setOnClickListener {
                if (pageStack.isEmpty() || pageStack.peek() != ID_SCRIPTS) {
                    pageStack.push(ID_SCRIPTS)
                }
                navigateToPage(ID_SCRIPTS)
            }

            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        v.translationX = dp(4).toFloat()
                        v.translationY = dp(4).toFloat()
                        v.background = cyberBrutalistBg(
                            fillColor = NC.SURFACE_LOW,
                            strokeColor = NC.OUTLINE_VAR,
                            shadowColor = NC.SHADOW_DARK,
                            offsetDp = 2,
                            cornerRadiusDp = 0
                        )
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.translationX = 0f
                        v.translationY = 0f
                        v.background = cyberBrutalistBg(
                            fillColor = NC.SURFACE_LOW,
                            strokeColor = NC.OUTLINE_VAR,
                            shadowColor = NC.SHADOW_DARK,
                            offsetDp = 6,
                            cornerRadiusDp = 0
                        )
                    }
                }
                false
            }
            
            val icon = ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.ic_build)
                setColorFilter(NC.PRIMARY)
                layoutParams = LinearLayout.LayoutParams(dp(24), dp(24)).apply { rightMargin = dp(12) }
            }
            val details = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
            }
            val name = TextView(this@MainActivity).apply {
                text = "REPAIRS"
                textSize = 15f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
            }
            val sub = TextView(this@MainActivity).apply {
                text = "Re-run host, guest, and chroot fix scripts"
                textSize = 11f
                setTextColor(NC.ON_SURF_VAR)
                typeface = Typeface.MONOSPACE
            }
            details.addView(name)
            details.addView(sub)
            val arrow = ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.ic_chevron_right)
                setColorFilter(NC.PRIMARY)
                layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
            }
            addView(icon)
            addView(details)
            addView(arrow)
        }
    }

    private fun buildProotSettingsSectionButton(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = cyberBrutalistBg(
                fillColor = NC.SURFACE_LOW,
                strokeColor = NC.OUTLINE_VAR,
                shadowColor = NC.SHADOW_DARK,
                offsetDp = 6,
                cornerRadiusDp = 0
            )
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setOnClickListener {
                if (pageStack.isEmpty() || pageStack.peek() != ID_PROOT_SETTINGS) {
                    pageStack.push(ID_PROOT_SETTINGS)
                }
                navigateToPage(ID_PROOT_SETTINGS)
            }

            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        v.translationX = dp(4).toFloat()
                        v.translationY = dp(4).toFloat()
                        v.background = cyberBrutalistBg(
                            fillColor = NC.SURFACE_LOW,
                            strokeColor = NC.OUTLINE_VAR,
                            shadowColor = NC.SHADOW_DARK,
                            offsetDp = 2,
                            cornerRadiusDp = 0
                        )
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.translationX = 0f
                        v.translationY = 0f
                        v.background = cyberBrutalistBg(
                            fillColor = NC.SURFACE_LOW,
                            strokeColor = NC.OUTLINE_VAR,
                            shadowColor = NC.SHADOW_DARK,
                            offsetDp = 6,
                            cornerRadiusDp = 0
                        )
                    }
                }
                false
            }

            val icon = ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.ic_storage)
                setColorFilter(NC.PRIMARY)
                layoutParams = LinearLayout.LayoutParams(dp(24), dp(24)).apply { rightMargin = dp(12) }
            }
            val details = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
            }
            val name = TextView(this@MainActivity).apply {
                text = "PROOT SETTINGS"
                textSize = 15f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
            }
            val sub = TextView(this@MainActivity).apply {
                text = "Debian rootfs size (app storage)"
                textSize = 11f
                setTextColor(NC.ON_SURF_VAR)
                typeface = Typeface.MONOSPACE
            }
            details.addView(name)
            details.addView(sub)
            val arrow = ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.ic_chevron_right)
                setColorFilter(NC.PRIMARY)
                layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
            }
            addView(icon)
            addView(details)
            addView(arrow)
        }
    }

    private fun buildChrootSettingsSectionButton(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = cyberBrutalistBg(
                fillColor = NC.SURFACE_LOW,
                strokeColor = NC.OUTLINE_VAR,
                shadowColor = NC.SHADOW_DARK,
                offsetDp = 6,
                cornerRadiusDp = 0
            )
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setOnClickListener {
                if (pageStack.isEmpty() || pageStack.peek() != ID_CHROOT_SETTINGS) {
                    pageStack.push(ID_CHROOT_SETTINGS)
                }
                navigateToPage(ID_CHROOT_SETTINGS)
            }

            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        v.translationX = dp(4).toFloat()
                        v.translationY = dp(4).toFloat()
                        v.background = cyberBrutalistBg(
                            fillColor = NC.SURFACE_LOW,
                            strokeColor = NC.OUTLINE_VAR,
                            shadowColor = NC.SHADOW_DARK,
                            offsetDp = 2,
                            cornerRadiusDp = 0
                        )
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.translationX = 0f
                        v.translationY = 0f
                        v.background = cyberBrutalistBg(
                            fillColor = NC.SURFACE_LOW,
                            strokeColor = NC.OUTLINE_VAR,
                            shadowColor = NC.SHADOW_DARK,
                            offsetDp = 6,
                            cornerRadiusDp = 0
                        )
                    }
                }
                false
            }

            val icon = ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.ic_storage)
                setColorFilter(NC.PRIMARY)
                layoutParams = LinearLayout.LayoutParams(dp(24), dp(24)).apply { rightMargin = dp(12) }
            }
            val details = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
            }
            val name = TextView(this@MainActivity).apply {
                text = "CHROOT SETTINGS"
                textSize = 15f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
            }
            val sub = TextView(this@MainActivity).apply {
                text = "Rootfs size, path, and uninstall (outside app storage)"
                textSize = 11f
                setTextColor(NC.ON_SURF_VAR)
                typeface = Typeface.MONOSPACE
            }
            details.addView(name)
            details.addView(sub)
            val arrow = ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.ic_chevron_right)
                setColorFilter(NC.PRIMARY)
                layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
            }
            addView(icon)
            addView(details)
            addView(arrow)
        }
    }

    private fun buildOnboardingSectionButton(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = cyberBrutalistBg(
                fillColor = NC.SURFACE_LOW,
                strokeColor = NC.OUTLINE_VAR,
                shadowColor = NC.SHADOW_DARK,
                offsetDp = 6,
                cornerRadiusDp = 0
            )
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setOnClickListener {
                val intent = Intent(this@MainActivity, OnboardingActivity::class.java).apply {
                    putExtra("force_onboarding", true)
                }
                startActivity(intent)
            }

            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        v.translationX = dp(4).toFloat()
                        v.translationY = dp(4).toFloat()
                        v.background = cyberBrutalistBg(
                            fillColor = NC.SURFACE_LOW,
                            strokeColor = NC.OUTLINE_VAR,
                            shadowColor = NC.SHADOW_DARK,
                            offsetDp = 2,
                            cornerRadiusDp = 0
                        )
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.translationX = 0f
                        v.translationY = 0f
                        v.background = cyberBrutalistBg(
                            fillColor = NC.SURFACE_LOW,
                            strokeColor = NC.OUTLINE_VAR,
                            shadowColor = NC.SHADOW_DARK,
                            offsetDp = 6,
                            cornerRadiusDp = 0
                        )
                    }
                }
                false
            }
            
            val icon = ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.ic_reset_thick)
                setColorFilter(NC.PRIMARY)
                layoutParams = LinearLayout.LayoutParams(dp(24), dp(24)).apply { rightMargin = dp(12) }
            }
            val details = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
            }
            val name = TextView(this@MainActivity).apply {
                text = "RE-RUN ONBOARDING"
                textSize = 15f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
            }
            val sub = TextView(this@MainActivity).apply {
                text = "Relaunch initial setup and onboarding walkthrough"
                textSize = 11f
                setTextColor(NC.ON_SURF_VAR)
                typeface = Typeface.MONOSPACE
            }
            details.addView(name)
            details.addView(sub)
            val arrow = ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.ic_chevron_right)
                setColorFilter(NC.PRIMARY)
                layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
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
            setBackgroundColor(NC.BG)
        }
        scriptsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
        }
        scriptsScrollView.addView(scriptsLayout)

        // Header: back + icon + title (match chroot settings)
        val headerCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(12))
        }
        val headerTitleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val pageBackBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_arrow_back)
            setColorFilter(NC.PRIMARY)
            setPadding(dp(4), dp(4), dp(4), dp(4))
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply { rightMargin = dp(8) }
            contentDescription = "Back"
            setOnClickListener {
                if (pageStack.isNotEmpty() && pageStack.peek() == ID_SCRIPTS) {
                    pageStack.pop()
                }
                navigateToPage(ID_SETTINGS, false)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(NC.SURFACE_CONTAINER)
                setStroke(dp(1), NC.OUTLINE_VAR)
            }
        }
        val headerIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_build)
            setColorFilter(NC.PRIMARY)
            layoutParams = LinearLayout.LayoutParams(dp(26), dp(26)).apply { rightMargin = dp(10) }
        }
        val titleTv = TextView(this).apply {
            text = "REPAIRS"
            textSize = 22f
            setTextColor(NC.PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        headerTitleRow.addView(pageBackBtn)
        headerTitleRow.addView(headerIcon)
        headerTitleRow.addView(titleTv)
        val subTitleTv = TextView(this).apply {
            text = "// RE-RUN SETUP & FIX SCRIPTS"
            textSize = 11f
            setTextColor(NC.ON_SURF_VAR)
            typeface = Typeface.MONOSPACE
            setPadding(0, dp(4), 0, dp(8))
        }
        val divider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, dp(1)).apply { bottomMargin = dp(12) }
            setBackgroundColor(NC.OUTLINE_VAR)
        }
        headerCol.addView(headerTitleRow)
        headerCol.addView(subTitleTv)
        headerCol.addView(divider)
        scriptsLayout.addView(headerCol)

        // Root probe badge
        repairsRootBadge = TextView(this).apply {
            text = "ROOT …"
            textSize = 10f
            typeface = Typeface.MONOSPACE
            setTextColor(NC.ON_SURF_VAR)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(NC.SURFACE_LOWEST)
                setStroke(dp(1), NC.OUTLINE_VAR)
            }
            layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).apply { bottomMargin = dp(12) }
        }
        scriptsLayout.addView(repairsRootBadge)

        // Segment tabs: HOST | GUEST | CHROOT*
        val tabsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(16) }
        }
        repairsTabHostBtn = makeRepairsTabBtn("HOST") {
            repairsSelectedTab = "host"
            applyRepairsTabStyles()
            populateRepairsList()
        }
        repairsTabGuestBtn = makeRepairsTabBtn("GUEST") {
            repairsSelectedTab = "guest"
            applyRepairsTabStyles()
            populateRepairsList()
        }
        repairsTabChrootBtn = makeRepairsTabBtn("CHROOT") {
            repairsSelectedTab = "chroot"
            applyRepairsTabStyles()
            populateRepairsList()
        }
        tabsRow.addView(repairsTabHostBtn, LinearLayout.LayoutParams(0, WRAP, 1f).apply { rightMargin = dp(6) })
        tabsRow.addView(repairsTabGuestBtn, LinearLayout.LayoutParams(0, WRAP, 1f).apply { rightMargin = dp(6) })
        tabsRow.addView(repairsTabChrootBtn, LinearLayout.LayoutParams(0, WRAP, 1f))
        scriptsLayout.addView(tabsRow)

        repairsListContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        scriptsLayout.addView(repairsListContainer)

        applyRepairsTabStyles()
        populateRepairsList()
    }

    private fun makeRepairsTabBtn(label: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(12), dp(10), dp(12))
            setOnClickListener { onClick() }
        }
    }

    private fun applyRepairsTabStyles() {
        fun style(btn: TextView?, selected: Boolean) {
            btn ?: return
            btn.setTextColor(if (selected) NC.ON_PRIMARY else NC.ON_SURF_VAR)
            btn.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(if (selected) NC.PRIMARY else NC.SURFACE_LOW)
                setStroke(dp(1), if (selected) NC.PRIMARY else NC.OUTLINE_VAR)
            }
        }
        style(repairsTabHostBtn, repairsSelectedTab == "host")
        style(repairsTabGuestBtn, repairsSelectedTab == "guest")
        style(repairsTabChrootBtn, repairsSelectedTab == "chroot")
        val chrootOk = repairsRootOk == true
        repairsTabChrootBtn?.visibility = if (chrootOk) View.VISIBLE else View.GONE
        if (!chrootOk && repairsSelectedTab == "chroot") {
            repairsSelectedTab = "host"
            style(repairsTabHostBtn, true)
            style(repairsTabChrootBtn, false)
        }
    }

    private fun refreshRepairsPage() {
        repairsRootBadge?.text = "ROOT …"
        repairsRootBadge?.setTextColor(NC.ON_SURF_VAR)
        executor.execute {
            val ok = try {
                RootShell.isRootAvailable()
            } catch (_: Exception) {
                false
            }
            mainHandler.post {
                repairsRootOk = ok
                repairsRootBadge?.text = if (ok) "ROOT OK" else "ROOT UNAVAILABLE"
                repairsRootBadge?.setTextColor(if (ok) NC.PRIMARY else NC.SECONDARY)
                applyRepairsTabStyles()
                populateRepairsList()
            }
        }
    }

    private fun populateRepairsList() {
        val list = repairsListContainer ?: return
        list.removeAllViews()

        when (repairsSelectedTab) {
            "host" -> {
                // setup only — start/stop GUI live elsewhere (home / tools)
                val hostScripts = arrayOf(
                    "setup_termux.sh" to "Setup basic environment and directories on host.",
                    "flux_install.sh" to "One-click: install Debian proot + setup_debian_family (user/xfce/vnc)."
                )
                for ((name, desc) in hostScripts) {
                    list.addView(
                        buildScriptCard(
                            name = name,
                            desc = desc,
                            badge = "HOST",
                            runLabel = "RUN",
                            onRun = { runScriptInTerminal(name, "host") }
                        )
                    )
                }
            }
            "guest" -> {
                val rootOk = repairsRootOk == true
                val hint = TextView(this).apply {
                    text = if (rootOk) {
                        "// DEBIAN GUEST — RUN ASKS PROOT OR CHROOT"
                    } else {
                        "// DEBIAN GUEST — PROOT ONLY (NO ROOT)"
                    }
                    textSize = 10f
                    setTextColor(NC.ON_SURF_VAR)
                    typeface = Typeface.MONOSPACE
                    setPadding(0, 0, 0, dp(10))
                }
                list.addView(hint)
                val guestScripts = arrayOf(
                    "setup_debian_family.sh" to "Create users and VNC startup configurations in guest.",
                    "setup_customization_debian.sh" to "Apply dark themes and custom packages inside Debian guest.",
                    "setup_hw_accel_debian.sh" to "Configure GPU accel: Turnip (Snapdragon/Adreno) or VirGL (others).",
                    "setup_cli_tools.sh" to "Install Node.js/NVM and AI CLI tools (Aider, Claude, Cline) in guest."
                )
                for ((name, desc) in guestScripts) {
                    list.addView(
                        buildScriptCard(
                            name = name,
                            desc = desc,
                            badge = "GUEST",
                            runLabel = "RUN",
                            onRun = { runGuestRepairScript(name) }
                        )
                    )
                }
            }
            "chroot" -> {
                if (repairsRootOk != true) {
                    val msg = TextView(this).apply {
                        text = "Root required for chroot install script."
                        textSize = 12f
                        setTextColor(NC.SECONDARY)
                        typeface = Typeface.MONOSPACE
                    }
                    list.addView(msg)
                    return
                }
                // install only — uninstall stays on Chroot Settings
                list.addView(
                    buildScriptCard(
                        name = "setup_debian13_chroot.sh",
                        desc = "Install Debian 13 (Trixie) Chroot environment (Requires Root).",
                        badge = "ROOT",
                        runLabel = "RUN",
                        onRun = { runScriptInTerminal("setup_debian13_chroot.sh", "chroot_host") }
                    )
                )
            }
        }
    }

    /**
     * Guest Debian scripts run on proot and/or chroot.
     * With root: pick target. Without root: proot only.
     */
    private fun runGuestRepairScript(scriptName: String) {
        if (repairsRootOk == true) {
            showGuestRunTargetDialog(scriptName)
        } else {
            runScriptInTerminal(scriptName, "proot")
        }
    }

    /** Brutalist picker: PROOT vs CHROOT for guest repair scripts. */
    private fun showGuestRunTargetDialog(scriptName: String) {
        val dialog = android.app.Dialog(this, android.R.style.Theme_Translucent_NoTitleBar)
        dialog.setCancelable(true)

        val scrim = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#CC0A0A0A"))
            layoutParams = ViewGroup.LayoutParams(MATCH, MATCH)
            setOnClickListener { dialog.dismiss() }
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cyberBrutalistBg(
                fillColor = NC.SURFACE_LOW,
                strokeColor = NC.OUTLINE_VAR,
                shadowColor = NC.SHADOW_DARK,
                offsetDp = 6,
                cornerRadiusDp = 0,
                rightFaceColor = Color.parseColor("#3C4A3F")
            )
            setPadding(dp(20), dp(20), dp(20), dp(20))
            layoutParams = FrameLayout.LayoutParams(MATCH, WRAP).apply {
                gravity = Gravity.CENTER
                leftMargin = dp(24)
                rightMargin = dp(24)
            }
            isClickable = true
        }

        card.addView(TextView(this).apply {
            text = "RUN WHERE?"
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = Typeface.MONOSPACE
            paint.isFakeBoldText = true
            setPadding(0, 0, 0, dp(8))
        })
        card.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, dp(1)).apply { bottomMargin = dp(12) }
            setBackgroundColor(NC.OUTLINE_VAR)
        })
        card.addView(TextView(this).apply {
            text = scriptName
            textSize = 12f
            setTextColor(NC.PRIMARY)
            typeface = Typeface.MONOSPACE
            setPadding(0, 0, 0, dp(6))
        })
        card.addView(TextView(this).apply {
            text = "Debian guest scripts work on proot and chroot."
            textSize = 12f
            setTextColor(NC.ON_SURF_VAR)
            typeface = Typeface.MONOSPACE
            setPadding(0, 0, 0, dp(16))
        })

        fun targetBtn(
            label: String,
            fill: Int,
            textColor: Int,
            mode: String,
            endMargin: Int
        ): TextView {
            return TextView(this).apply {
                text = label
                textSize = 12f
                setTextColor(textColor)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                background = cyberBrutalistBg(
                    fillColor = fill,
                    strokeColor = NC.OUTLINE_VAR,
                    shadowColor = NC.SHADOW_DARK,
                    offsetDp = 4,
                    cornerRadiusDp = 0
                )
                setPadding(dp(12), dp(14), dp(12), dp(14))
                layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).apply {
                    if (endMargin > 0) rightMargin = endMargin
                }
                setOnClickListener {
                    dialog.dismiss()
                    runScriptInTerminal(scriptName, mode)
                }
            }
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(
                targetBtn(
                    "PROOT",
                    NC.PRIMARY,
                    NC.ON_PRIMARY,
                    "proot",
                    dp(10)
                )
            )
            addView(
                targetBtn(
                    "CHROOT",
                    NC.SURFACE_CONTAINER,
                    Color.WHITE,
                    "chroot_guest",
                    0
                )
            )
        }
        card.addView(row)

        card.addView(TextView(this).apply {
            text = "CANCEL"
            textSize = 11f
            setTextColor(NC.ON_SURF_VAR)
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, 0)
            setOnClickListener { dialog.dismiss() }
        })

        scrim.addView(card)
        dialog.setContentView(scrim)
        dialog.show()
    }

    private fun buildScriptCard(
        name: String,
        desc: String,
        badge: String,
        runLabel: String,
        onRun: () -> Unit,
        destructive: Boolean = false
    ): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cyberBrutalistBg(
                fillColor = NC.SURFACE_LOW,
                strokeColor = if (destructive) NC.ERROR else NC.OUTLINE_VAR,
                shadowColor = NC.SHADOW_DARK,
                offsetDp = 6,
                cornerRadiusDp = 0,
                rightFaceColor = NC.OUTLINE_VAR
            )
            setPadding(dp(16), dp(14), dp(16), dp(14))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply {
                bottomMargin = dp(14)
            }

            val titleRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val title = TextView(this@MainActivity).apply {
                text = name
                textSize = 14f
                setTextColor(NC.ON_SURFACE)
                typeface = Typeface.MONOSPACE
                layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
            }
            val badgeTv = TextView(this@MainActivity).apply {
                text = badge
                textSize = 10f
                typeface = Typeface.MONOSPACE
                setTextColor(if (destructive) NC.ERROR else NC.PRIMARY)
                setPadding(dp(8), dp(2), dp(8), dp(2))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(NC.SURFACE_LOWEST)
                    setStroke(dp(1), if (destructive) NC.ERROR else NC.OUTLINE_VAR)
                }
            }
            titleRow.addView(title)
            titleRow.addView(badgeTv)

            val descTv = TextView(this@MainActivity).apply {
                text = desc
                textSize = 11f
                setTextColor(NC.ON_SURF_VAR)
                typeface = Typeface.MONOSPACE
                setPadding(0, dp(6), 0, dp(12))
            }

            val btnFill = if (destructive) NC.ERROR_CON else NC.PRIMARY
            val btnText = if (destructive) NC.ON_ERROR_CON else NC.ON_PRIMARY
            val btnStroke = if (destructive) NC.ERROR else NC.OUTLINE_VAR
            val runBtn = TextView(this@MainActivity).apply {
                text = runLabel
                textSize = 12f
                setTextColor(btnText)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                background = cyberBrutalistBg(
                    fillColor = btnFill,
                    strokeColor = btnStroke,
                    shadowColor = NC.SHADOW_DARK,
                    offsetDp = 4,
                    cornerRadiusDp = 0
                )
                setPadding(dp(16), dp(10), dp(16), dp(10))
                layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).apply {
                    gravity = Gravity.END
                }
                setOnClickListener { onRun() }
                setOnTouchListener { v, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            v.translationX = dp(3).toFloat()
                            v.translationY = dp(3).toFloat()
                            v.background = cyberBrutalistBg(
                                fillColor = btnFill,
                                strokeColor = btnStroke,
                                shadowColor = NC.SHADOW_DARK,
                                offsetDp = 2,
                                cornerRadiusDp = 0
                            )
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            v.translationX = 0f
                            v.translationY = 0f
                            v.background = cyberBrutalistBg(
                                fillColor = btnFill,
                                strokeColor = btnStroke,
                                shadowColor = NC.SHADOW_DARK,
                                offsetDp = 4,
                                cornerRadiusDp = 0
                            )
                        }
                    }
                    false
                }
            }

            addView(titleRow)
            addView(descTv)
            addView(runBtn)
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
        scriptInstallBackBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_arrow_back)
            setColorFilter(NC.ON_SURFACE)
            setPadding(dp(6), dp(6), dp(6), dp(6))
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
            setOnClickListener {
                onBackPressed()
            }
        }
        scriptInstallTitleTv = TextView(this).apply {
            text = "Script Runner"
            textSize = 16f
            setTextColor(NC.ON_SURFACE)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }

        topBar.addView(scriptInstallBackBtn)
        topBar.addView(scriptInstallTitleTv)
        scriptInstallLayout.addView(topBar)

        scriptInstallViewContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f)
        }

        scriptInstallTerminalView = TerminalView(this, null).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
        }

        scriptInstallViewContainer.addView(scriptInstallTerminalView)
        scriptInstallLayout.addView(scriptInstallViewContainer)
    }

    /** Top-bar label for script runner page — never always "Script Installation". */
    private fun scriptRunnerTitle(scriptName: String, runMode: String): String {
        return when (scriptName) {
            "uninstall_debian13_chroot.sh" -> "Chroot Uninstall"
            "setup_debian13_chroot.sh" -> "Chroot Installation"
            "setup_debian_family.sh" -> "Debian Family Setup"
            "setup_customization_debian.sh" -> "Debian Customization"
            "setup_hw_accel_debian.sh" -> "Hardware Acceleration Setup"
            "setup_cli_tools.sh" -> "CLI Tools Setup"
            "setup_termux.sh" -> "Host Environment Setup"
            "flux_install.sh" -> "Flux / PRoot Install"
            "start_gui.sh" -> "Start Graphical Desktop"
            "stop_gui.sh" -> "Stop Graphical Desktop"
            else -> when {
                scriptName.contains("uninstall", ignoreCase = true) -> "Script Uninstall"
                scriptName.startsWith("setup_") -> "Script Setup"
                scriptName.startsWith("start_") -> "Script Start"
                scriptName.startsWith("stop_") -> "Script Stop"
                else -> when (runMode) {
                    "chroot_host", "chroot" -> "Chroot Script"
                    "chroot_guest" -> "Chroot Guest Script"
                    "proot" -> "PRoot Script"
                    "host" -> "Host Script"
                    else -> "Script Runner"
                }
            }
        }
    }

    private fun runScriptInTerminal(scriptName: String, runMode: String = "proot") {
        scriptInstallTerminalView.setTextSize(scriptFontSize)
        if (::scriptInstallTitleTv.isInitialized) {
            scriptInstallTitleTv.text = scriptRunnerTitle(scriptName, runMode)
        }

        // Ensure all scripts are deployed to files/home
        deployScripts()

        val nld     = applicationInfo.nativeLibraryDir
        val shell   = File(nld, "libbash.so").absolutePath
        val cwd     = File(filesDir, "home").absolutePath
        val scriptFile = File(cwd, scriptName)
        if (!scriptFile.exists()) {
            try {
                val assetPath = when {
                    scriptName.contains("chroot") -> "scripts/chroot/$scriptName"
                    else -> "scripts/$scriptName"
                }
                assets.open(assetPath).use { input -> FileOutputStream(scriptFile).use { input.copyTo(it) } }
                scriptFile.setExecutable(true)
            } catch (e: Exception) {
                Log.e("ScriptRun", "Failed to deploy script $scriptName", e)
            }
        }
        val scriptPath = scriptFile.absolutePath
        val args: Array<String>
        val envMap: HashMap<String, String>

        when (runMode) {
            "chroot", "chroot_host" -> {
                // Host-level chroot script (setup or uninstall) executed via explicit /system/bin/su
                args = arrayOf("/system/bin/sh", "-c", "/system/bin/su -c \"sh $scriptPath\"")
                envMap = HashMap(System.getenv()).apply {
                    put("PATH", "/system/bin:/system/xbin:/sbin:$nld:/data/data/com.ivarna.nativecode/files/usr/bin")
                    put("HOME", "/data/data/com.ivarna.nativecode/files/home")
                    put("TERM", "xterm-256color")
                }
            }
            "chroot_guest" -> {
                // Guest script executed inside Debian Chroot container
                val stageCmd = "mkdir -p /data/local/tmp/chrootDebian13/tmp && cp $scriptPath /data/local/tmp/chrootDebian13/tmp/$scriptName && chmod +x /data/local/tmp/chrootDebian13/tmp/$scriptName"
                val guestInner = if (scriptName == "setup_hw_accel_debian.sh") {
                    val gpu = GpuAccelDetector.fluxGpuEnv()
                    getSharedPreferences("nativecode_prefs", MODE_PRIVATE).edit()
                        .putString("flux_gpu", gpu).apply()
                    "env FLUX_GPU=$gpu bash /tmp/$scriptName"
                } else {
                    "bash /tmp/$scriptName"
                }
                val runCmd = if (File("/data/local/tmp/run_debian13_root.sh").exists()) {
                    "/data/local/tmp/run_debian13_root.sh $guestInner"
                } else {
                    "/system/bin/mount -o remount,dev,suid /data >/dev/null 2>&1 || busybox mount -o remount,dev,suid /data >/dev/null 2>&1 || true; busybox mount --bind /dev /data/local/tmp/chrootDebian13/dev 2>/dev/null; busybox mount --bind /sys /data/local/tmp/chrootDebian13/sys 2>/dev/null; busybox mount -t proc proc /data/local/tmp/chrootDebian13/proc 2>/dev/null; busybox mount -t devpts devpts /data/local/tmp/chrootDebian13/dev/pts 2>/dev/null; busybox chroot /data/local/tmp/chrootDebian13 /bin/su - root -c \"$guestInner\""
                }
                args = arrayOf("/system/bin/sh", "-c", "/system/bin/su -c \"$stageCmd && $runCmd\"")
                envMap = HashMap(System.getenv()).apply {
                    put("PATH", "/system/bin:/system/xbin:/sbin:$nld:/data/data/com.ivarna.nativecode/files/usr/bin")
                    put("HOME", "/data/data/com.ivarna.nativecode/files/home")
                    put("TERM", "xterm-256color")
                }
            }
            "proot" -> {
                // Guest script executed inside Debian PRoot container
                val scriptCmd = if (scriptName == "setup_hw_accel_debian.sh") {
                    val gpu = GpuAccelDetector.fluxGpuEnv()
                    getSharedPreferences("nativecode_prefs", MODE_PRIVATE).edit()
                        .putString("flux_gpu", gpu).apply()
                    "env FLUX_GPU=$gpu bash /data/data/com.ivarna.nativecode/files/home/$scriptName"
                } else {
                    "bash /data/data/com.ivarna.nativecode/files/home/$scriptName"
                }
                val (a, e) = LinuxCommandBuilder.build(this, scriptCmd, user = "root")
                args = a; envMap = e
            }
            else -> { // "host"
                val force = HostCommandBuilder.shouldForceHostSetup(scriptName)
                if (force) HostCommandBuilder.clearSetupMarker(this)
                val (hostArgs, hostEnv) = HostCommandBuilder.build(
                    this,
                    scriptPath,
                    forceHostSetup = force
                )
                args = hostArgs
                envMap = hostEnv
            }
        }
        val env = envMap.map { "${it.key}=${it.value}" }.toTypedArray()

        val scriptViewClient = object : TerminalViewClient {
            override fun onScale(scale: Float): Float {
                if (scale < 0.9f || scale > 1.1f) {
                    scriptFontSize = (scriptFontSize * scale).toInt().coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
                    scriptInstallTerminalView.setTextSize(scriptFontSize)
                    return 1.0f
                }
                return scale
            }
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
                     // Chroot uninstall: flip prefs + refresh Settings card when rootfs gone
                     if (scriptName == "uninstall_debian13_chroot.sh" && pendingChrootUninstall) {
                         pendingChrootUninstall = false
                         if (session.exitStatus == 0 || !ProjectPathResolver.isChrootRootfsPresent()) {
                             onChrootUninstalled(fromCallback = false)
                         } else {
                             refreshChrootSettingsCard()
                             Toast.makeText(
                                 this@MainActivity,
                                 "Uninstall may have failed — check log / root access",
                                 Toast.LENGTH_LONG
                             ).show()
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
                executor.execute {
                    session.emulator?.paste(text) ?: session.write(text)
                }
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
        fileViewerRootContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
            visibility = View.GONE
            setBackgroundColor(NC.BG)
        }
        fileViewerTopBar = createProjectSubpageTopBar()
        fileViewerScrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f)
            setBackgroundColor(NC.BG)
            clipToPadding = false
            setPadding(0, 0, 0, dp(100))
        }
        fileViewerContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(16))
        }
        fileViewerScrollView.addView(fileViewerContainer)
        fileViewerRootContainer.addView(fileViewerTopBar)
        fileViewerRootContainer.addView(fileViewerScrollView)
    }

    private fun buildDiffViewerLayout() {
        diffViewerRootContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
            visibility = View.GONE
            setBackgroundColor(NC.BG)
        }
        diffViewerTopBar = createProjectSubpageTopBar()
        diffViewerScrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f)
            setBackgroundColor(NC.BG)
            clipToPadding = false
            setPadding(0, 0, 0, dp(100))
        }
        diffViewerContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16))
        }
        diffViewerScrollView.addView(diffViewerContainer)
        diffViewerRootContainer.addView(diffViewerTopBar)
        diffViewerRootContainer.addView(diffViewerScrollView)
    }

    // ── Helper Sub-page Actions ──────────────────────────────────────────────

    private fun showFileViewer(name: String, backPage: Int = ID_FILES) {
        fileViewerBackPage = backPage
        if (backPage == ID_PROJECT_DIR_TREE) {
            unifiedHeader.visibility = View.GONE
            projectBottomNavigation.visibility = View.VISIBLE
            bottomNavigation.visibility = View.GONE
            fileViewerTopBar.visibility = View.VISIBLE
            updateProjectSubpageTopBar(fileViewerTopBar)
        } else {
            unifiedHeader.visibility = View.VISIBLE
            projectBottomNavigation.visibility = View.GONE
            bottomNavigation.visibility = View.VISIBLE
            fileViewerTopBar.visibility = View.GONE
        }
        homeScrollView.visibility = View.GONE
        fileExplorerScrollView.visibility = View.GONE
        terminalWorkspaceLayout.visibility = View.GONE
        gitOperationsScrollView.visibility = View.GONE
        settingsHubScrollView.visibility = View.GONE
        if (::prootSettingsScrollView.isInitialized) {
            prootSettingsScrollView.visibility = View.GONE
        }
        if (::chrootSettingsScrollView.isInitialized) {
            chrootSettingsScrollView.visibility = View.GONE
        }
        if (::diffViewerRootContainer.isInitialized) {
            diffViewerRootContainer.visibility = View.GONE
        }

        if (::projectGitDiffContainer.isInitialized) projectGitDiffContainer.visibility = View.GONE
        if (::projectWorkspaceContainer.isInitialized) projectWorkspaceContainer.visibility = View.GONE
        if (::projectSettingsContainer.isInitialized) projectSettingsContainer.visibility = View.GONE
        if (::projectDirTreeContainer.isInitialized) projectDirTreeContainer.visibility = View.GONE

        fileViewerRootContainer.visibility = View.VISIBLE
        fileViewerScrollView.visibility = View.VISIBLE

        renderFileViewerContent(name, backPage)
    }

    private fun renderFileViewerContent(pathStr: String, backPage: Int) {
        fileViewerScrollView.scrollTo(0, 0)
        fileViewerContainer.removeAllViews()

        var targetFile = File(pathStr)
        val projectHost = activeProjectHostDir()
        if (!targetFile.exists() && !targetFile.isAbsolute) {
            targetFile = File(projectHost, pathStr)
        }

        val headerCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(8))
        }
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleTv = TextView(this).apply {
            text = "FILE VIEWER"
            textSize = 18f
            setTextColor(NC.PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        val relativePathStr = if (targetFile.absolutePath.contains(projectHost.absolutePath)) {
            targetFile.absolutePath.removePrefix(projectHost.absolutePath).trimStart('/')
        } else {
            targetFile.name
        }
        val subTv = TextView(this).apply {
            text = "// $relativePathStr"
            textSize = 10f
            setTextColor(NC.ON_SURF_VAR)
            typeface = Typeface.MONOSPACE
        }
        val copyPathHeaderBtn = TextView(this).apply {
            text = "COPY PATH"
            textSize = 10f
            setTextColor(NC.PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
            background = roundedBg(NC.SURFACE_VAR, NC.BORDER, dp(4))
            setPadding(dp(8), dp(4), dp(8), dp(4))
            layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).apply { leftMargin = dp(8) }
            setOnClickListener {
                copyToClipboard("File Path", targetFile.absolutePath)
            }
        }
        headerRow.addView(titleTv)
        headerRow.addView(subTv)
        headerRow.addView(copyPathHeaderBtn)
        val divider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, dp(1)).apply { topMargin = dp(8) }
            setBackgroundColor(NC.OUTLINE_VAR)
        }
        headerCard.addView(headerRow)
        headerCard.addView(divider)
        fileViewerContainer.addView(headerCard)

        val contentWrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(16))
        }
        fileViewerContainer.addView(contentWrapper)

        if (!targetFile.exists()) {
            val errorTv = TextView(this).apply {
                text = "File not found: ${targetFile.absolutePath}"
                setTextColor(NC.ERROR)
                textSize = 14f
                typeface = Typeface.MONOSPACE
            }
            contentWrapper.addView(errorTv)
            return
        }

        val ext = targetFile.extension.lowercase(Locale.ROOT)
        when {
            ext in listOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "ico", "svg") -> {
                contentWrapper.addView(buildImageGifCard(targetFile))
            }
            ext in listOf("mp4", "webm", "mkv", "3gp", "avi", "mov") -> {
                contentWrapper.addView(buildVideoCard(targetFile))
            }
            ext in listOf("html", "htm") -> {
                contentWrapper.addView(buildHtmlCard(targetFile))
            }
            ext == "apk" -> {
                contentWrapper.addView(buildApkCard(targetFile))
            }
            ext == "md" || ext == "markdown" -> {
                contentWrapper.addView(buildMarkdownViewerCard(targetFile))
            }
            else -> {
                if (isTextFile(targetFile, ext)) {
                    contentWrapper.addView(buildCodeViewerCard(targetFile))
                } else {
                    contentWrapper.addView(buildBinaryFileCard(targetFile))
                }
            }
        }
    }

    private fun buildImageGifCard(file: File): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(NC.SURFACE, NC.BORDER, dp(12))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }

        val imgArea = FrameLayout(this).apply {
            setBackgroundColor(NC.SURFACE_HIGH)
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply {
                minimumHeight = dp(180)
            }
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }

        var dimensionsStr = "Unknown resolution"
        val bitmap = try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (e: Exception) {
            null
        }

        if (bitmap != null) {
            dimensionsStr = "${bitmap.width}×${bitmap.height}"
            val imageView = ImageView(this).apply {
                layoutParams = FrameLayout.LayoutParams(MATCH, WRAP).apply {
                    gravity = Gravity.CENTER
                }
                adjustViewBounds = true
                maxHeight = dp(300)
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageBitmap(bitmap)
            }
            imgArea.addView(imageView)
        } else {
            val placeholder = ImageView(this).apply {
                setImageResource(R.drawable.ic_attach_image)
                setColorFilter(NC.ON_SURF_VAR)
                layoutParams = FrameLayout.LayoutParams(dp(64), dp(64)).apply {
                    gravity = Gravity.CENTER
                }
            }
            imgArea.addView(placeholder)
        }
        card.addView(imgArea)

        val infoRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }

        val infoTv = TextView(this).apply {
            text = "  ${file.name} • $dimensionsStr • ${formatFileSize(file.length())}"
            textSize = 12f
            setTextColor(NC.ON_SURF_VAR)
            typeface = Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        infoRow.addView(infoTv)

        val openExtBtn = TextView(this).apply {
            text = "OPEN"
            textSize = 10f
            setTextColor(NC.PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
            background = roundedBg(NC.SURFACE_VAR, NC.BORDER, dp(4))
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setOnClickListener { openExternalFile(file, "image/*") }
        }
        infoRow.addView(openExtBtn)

        card.addView(infoRow)
        return card
    }

    private fun buildVideoCard(file: File): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(NC.SURFACE, NC.BORDER, dp(12))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }

        val videoArea = FrameLayout(this).apply {
            setBackgroundColor(NC.SURFACE_HIGH)
            layoutParams = LinearLayout.LayoutParams(MATCH, dp(240))
        }

        val videoView = android.widget.VideoView(this).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH, Gravity.CENTER)
            setVideoPath(file.absolutePath)
        }
        val mediaController = android.widget.MediaController(this)
        mediaController.setAnchorView(videoView)
        videoView.setMediaController(mediaController)

        videoArea.addView(videoView)
        card.addView(videoArea)

        val infoRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }

        val infoTv = TextView(this).apply {
            text = "  ${file.name} • VIDEO • ${formatFileSize(file.length())}"
            textSize = 12f
            setTextColor(NC.ON_SURF_VAR)
            typeface = Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        infoRow.addView(infoTv)

        val openExtBtn = TextView(this).apply {
            text = "EXTERNAL PLAYER"
            textSize = 10f
            setTextColor(NC.PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
            background = roundedBg(NC.SURFACE_VAR, NC.BORDER, dp(4))
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setOnClickListener { openExternalFile(file, "video/*") }
        }
        infoRow.addView(openExtBtn)

        card.addView(infoRow)
        return card
    }

    private fun buildHtmlCard(file: File): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }

        val actionCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = cyberBrutalistBg(
                fillColor = NC.SURFACE_LOW,
                strokeColor = NC.BORDER,
                shadowColor = NC.SHADOW_GREEN,
                offsetDp = 4,
                cornerRadiusDp = 0,
                rightFaceColor = NC.BORDER
            )
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(16) }
        }

        val htmlIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_display)
            setColorFilter(NC.PRIMARY)
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply { rightMargin = dp(12) }
        }

        val infoCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        val titleTv = TextView(this).apply {
            text = file.name
            textSize = 14f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }
        val subTv = TextView(this).apply {
            text = "HTML Document • ${formatFileSize(file.length())}"
            textSize = 11f
            setTextColor(NC.ON_SURF_VAR)
            typeface = Typeface.MONOSPACE
        }
        infoCol.addView(titleTv)
        infoCol.addView(subTv)

        val openBrowserBtn = primaryButton("OPEN IN BROWSER") {
            openHtmlFile(file)
        }

        actionCard.addView(htmlIcon)
        actionCard.addView(infoCol)
        actionCard.addView(openBrowserBtn)

        container.addView(actionCard)
        container.addView(buildCodeViewerCard(file))

        return container
    }

    private fun buildApkCard(file: File): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }

        val apkCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cyberBrutalistBg(
                fillColor = NC.SURFACE_LOW,
                strokeColor = NC.BORDER,
                shadowColor = NC.SHADOW_GREEN,
                offsetDp = 6,
                cornerRadiusDp = 0,
                rightFaceColor = NC.BORDER
            )
            setPadding(dp(16), dp(16), dp(16), dp(16))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }

        val pm = packageManager
        val pkgInfo = try { pm.getPackageArchiveInfo(file.absolutePath, 0) } catch (e: Exception) { null }
        var appLabel = file.name
        var pkgName = "Package Archive"
        var verStr = "v1.0"
        var iconDrawable: Drawable? = null

        pkgInfo?.applicationInfo?.let { appInfo ->
            appInfo.sourceDir = file.absolutePath
            appInfo.publicSourceDir = file.absolutePath
            appLabel = try { appInfo.loadLabel(pm).toString() } catch (e: Exception) { file.name }
            pkgName = pkgInfo.packageName ?: ""
            verStr = "v${pkgInfo.versionName ?: "1.0"}"
            iconDrawable = try { appInfo.loadIcon(pm) } catch (e: Exception) { null }
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val iconIv = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply { rightMargin = dp(12) }
            if (iconDrawable != null) {
                setImageDrawable(iconDrawable)
            } else {
                setImageResource(R.drawable.ic_extension)
                setColorFilter(NC.PRIMARY)
            }
        }

        val infoCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }

        val titleTv = TextView(this).apply {
            text = appLabel
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }
        val subTv = TextView(this).apply {
            text = "$pkgName • $verStr\n${formatFileSize(file.length())}"
            textSize = 11f
            setTextColor(NC.ON_SURF_VAR)
            typeface = Typeface.MONOSPACE
        }
        infoCol.addView(titleTv)
        infoCol.addView(subTv)

        headerRow.addView(iconIv)
        headerRow.addView(infoCol)
        apkCard.addView(headerRow)

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(16) }
        }

        val installBtn = primaryButton("INSTALL APK") {
            installApkFile(file)
        }

        btnRow.addView(installBtn)
        apkCard.addView(btnRow)

        container.addView(apkCard)
        return container
    }

    private fun buildCodeViewerCard(file: File): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(NC.LOGBG, NC.BORDER_VAR, dp(12))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(NC.SURFACE)
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }

        val fileContentStr = try {
            val maxLength = 500 * 1024
            if (file.length() > maxLength) {
                file.inputStream().use { input ->
                    val bytes = ByteArray(maxLength)
                    val read = input.read(bytes)
                    String(bytes, 0, read, Charsets.UTF_8) + "\n... [Truncated file content]"
                }
            } else {
                file.readText(Charsets.UTF_8)
            }
        } catch (e: Exception) {
            "Error reading file: ${e.message}"
        }

        val lines = fileContentStr.lines()

        val fileNameTv = TextView(this).apply {
            text = "${file.name} (${lines.size} L • ${formatFileSize(file.length())})"
            textSize = 12f
            setTextColor(NC.ON_SURFACE)
            typeface = Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }

        val copyBtn = TextView(this).apply {
            text = "COPY"
            textSize = 10f
            setTextColor(NC.ON_SURFACE)
            background = roundedBg(NC.SURFACE_VAR, NC.BORDER, dp(4))
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("File content", fileContentStr)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this@MainActivity, "Copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        }
        header.addView(fileNameTv)
        header.addView(copyBtn)
        card.addView(header)

        val codeScroll = HorizontalScrollView(this).apply {
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        val codeLines = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val maxLinesToShow = 1000
        val displayLines = if (lines.size > maxLinesToShow) lines.take(maxLinesToShow) else lines

        for ((idx, line) in displayLines.withIndex()) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val num = TextView(this).apply {
                text = "${idx + 1}".padStart(4, ' ')
                textSize = 11f
                setTextColor(NC.OUTLINE)
                typeface = Typeface.MONOSPACE
                setPadding(0, 0, dp(10), 0)
            }
            val code = TextView(this).apply {
                text = line
                textSize = 11f
                setTextColor(NC.ON_SURFACE)
                typeface = Typeface.MONOSPACE
                setTextIsSelectable(true)
            }
            row.addView(num)
            row.addView(code)
            codeLines.addView(row)
        }

        if (lines.size > maxLinesToShow) {
            val moreTv = TextView(this).apply {
                text = "... ${lines.size - maxLinesToShow} more lines hidden"
                textSize = 11f
                setTextColor(NC.ON_SURF_VAR)
                typeface = Typeface.MONOSPACE
                setPadding(dp(20), dp(8), 0, 0)
            }
            codeLines.addView(moreTv)
        }

        codeScroll.addView(codeLines)
        card.addView(codeScroll)
        return card
    }

    private fun buildBinaryFileCard(file: File): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cyberBrutalistBg(
                fillColor = NC.SURFACE_LOW,
                strokeColor = NC.BORDER,
                shadowColor = NC.SHADOW_DARK,
                offsetDp = 6,
                cornerRadiusDp = 0,
                rightFaceColor = NC.BORDER
            )
            setPadding(dp(16), dp(16), dp(16), dp(16))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }

        val iconIv = ImageView(this).apply {
            setImageResource(R.drawable.ic_extension)
            setColorFilter(NC.PRIMARY)
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply {
                bottomMargin = dp(12)
            }
        }

        val titleTv = TextView(this).apply {
            text = file.name
            textSize = 15f
            setTextColor(Color.WHITE)
            typeface = Typeface.MONOSPACE
            paint.isFakeBoldText = true
        }

        val descTv = TextView(this).apply {
            text = "Binary File • ${formatFileSize(file.length())}\nDirect preview not available for this file type."
            textSize = 11f
            setTextColor(NC.ON_SURF_VAR)
            typeface = Typeface.MONOSPACE
            setPadding(0, dp(4), 0, dp(16))
        }

        val openExtBtn = primaryButton("OPEN WITH EXTERNAL APP") {
            openExternalFile(file)
        }

        card.addView(iconIv)
        card.addView(titleTv)
        card.addView(descTv)
        card.addView(openExtBtn)

        return card
    }

    private fun isTextFile(file: File, ext: String): Boolean {
        val knownTextExts = setOf(
            "txt", "md", "json", "xml", "kt", "java", "py", "rs", "c", "cpp", "h", "hpp", "sh",
            "yml", "yaml", "gradle", "kts", "properties", "conf", "ini", "js", "ts", "css", "log",
            "env", "toml", "lock", "bat", "cmd", "diff", "patch", "sql", "go", "rb", "php", "swift",
            "m", "mm", "cs", "lua", "zig", "asm", "dockerfile", "gitignore", "cmake"
        )
        if (knownTextExts.contains(ext) || file.name.startsWith(".")) return true
        if (file.length() > 2 * 1024 * 1024) return false
        return try {
            file.inputStream().use { input ->
                val bytes = ByteArray(1024.coerceAtMost(file.length().toInt()))
                val read = input.read(bytes)
                if (read <= 0) return true
                var nullCount = 0
                for (i in 0 until read) {
                    if (bytes[i] == 0.toByte()) nullCount++
                }
                nullCount == 0
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun openHtmlFile(file: File) {
        val uri = getFileUri(file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "text/html")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(Intent.createChooser(intent, "Open HTML in Browser"))
        } catch (e: Exception) {
            Toast.makeText(this, "No browser found to open HTML", Toast.LENGTH_SHORT).show()
        }
    }

    private fun installApkFile(file: File) {
        val uri = getFileUri(file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to launch APK installer: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openExternalFile(file: File, mimeType: String = "*/*") {
        val uri = getFileUri(file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(Intent.createChooser(intent, "Open with"))
        } catch (e: Exception) {
            Toast.makeText(this, "No application available to open this file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getFileUri(file: File): Uri {
        return try {
            androidx.core.content.FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                file
            )
        } catch (e: Exception) {
            Uri.fromFile(file)
        }
    }

    private fun formatFileSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format(Locale.US, "%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups.coerceAtMost(3)])
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Copied: $text", Toast.LENGTH_SHORT).show()
    }

    private fun buildMarkdownViewerCard(file: File): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }

        var isPreviewMode = true

        val tabBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedBg(NC.SURFACE_LOW, NC.BORDER, dp(8))
            setPadding(dp(4), dp(4), dp(4), dp(4))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(12) }
        }

        val previewTab = TextView(this).apply {
            text = "PREVIEW"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(8), dp(16), dp(8))
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }

        val sourceTab = TextView(this).apply {
            text = "SOURCE"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(8), dp(16), dp(8))
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }

        fun updateTabs() {
            if (isPreviewMode) {
                previewTab.setTextColor(NC.ON_PRIMARY)
                previewTab.background = roundedBg(NC.PRIMARY, NC.PRIMARY, dp(6))
                sourceTab.setTextColor(NC.ON_SURF_VAR)
                sourceTab.background = null
            } else {
                sourceTab.setTextColor(NC.ON_PRIMARY)
                sourceTab.background = roundedBg(NC.PRIMARY, NC.PRIMARY, dp(6))
                previewTab.setTextColor(NC.ON_SURF_VAR)
                previewTab.background = null
            }
        }
        updateTabs()

        tabBar.addView(previewTab)
        tabBar.addView(sourceTab)
        container.addView(tabBar)

        val mdContent = try { file.readText(Charsets.UTF_8) } catch (e: Exception) { "" }

        val webView = android.webkit.WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { minimumHeight = dp(350) }
            setBackgroundColor(NC.BG)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            val html = buildObsidianMarkdownHtml(mdContent, file.parentFile?.absolutePath ?: "")
            loadDataWithBaseURL("file://${file.parent}/", html, "text/html", "UTF-8", null)
        }

        val codeCard = buildCodeViewerCard(file)
        codeCard.visibility = View.GONE

        previewTab.setOnClickListener {
            if (!isPreviewMode) {
                isPreviewMode = true
                updateTabs()
                webView.visibility = View.VISIBLE
                codeCard.visibility = View.GONE
            }
        }

        sourceTab.setOnClickListener {
            if (isPreviewMode) {
                isPreviewMode = false
                updateTabs()
                webView.visibility = View.GONE
                codeCard.visibility = View.VISIBLE
            }
        }

        container.addView(webView)
        container.addView(codeCard)
        return container
    }

    private fun buildObsidianMarkdownHtml(rawMd: String, basePath: String): String {
        val safeMd = rawMd
            .replace("\\", "\\\\")
            .replace("`", "\\`")
            .replace("$", "\\$")

        return """
            <!DOCTYPE html>
            <html>
            <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <script src="https://cdn.jsdelivr.net/npm/marked/marked.min.js"></script>
            <style>
              body {
                background-color: #131313;
                color: #e5e2e1;
                font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                padding: 16px;
                line-height: 1.6;
                margin: 0;
                word-wrap: break-word;
              }
              h1, h2, h3, h4, h5, h6 {
                color: #60f99e;
                font-family: monospace, sans-serif;
                border-bottom: 1px solid #3c4a3f;
                padding-bottom: 6px;
                margin-top: 24px;
                margin-bottom: 12px;
              }
              a { color: #60f99e; text-decoration: none; font-weight: 500; }
              code {
                background-color: #201f1f;
                color: #60f99e;
                font-family: monospace;
                padding: 2px 6px;
                border-radius: 4px;
                font-size: 0.9em;
              }
              pre {
                background-color: #0e0e0e;
                border: 1px solid #3c4a3f;
                padding: 12px;
                border-radius: 8px;
                overflow-x: auto;
              }
              pre code { background: transparent; padding: 0; color: #e5e2e1; }
              blockquote {
                border-left: 3px solid #60f99e;
                margin: 12px 0;
                padding-left: 14px;
                color: #bbcbbc;
                background: #1c1b1b;
                padding-top: 4px;
                padding-bottom: 4px;
              }
              ul, ol { padding-left: 24px; margin: 12px 0; }
              li { margin-bottom: 6px; }
              table {
                width: 100%;
                border-collapse: collapse;
                margin: 18px 0;
                background: #1c1b1b;
                border-radius: 6px;
                overflow: hidden;
              }
              th, td {
                border: 1px solid #3c4a3f;
                padding: 10px 14px;
                text-align: left;
              }
              th { background-color: #201f1f; color: #60f99e; font-family: monospace; }
              hr { border: none; border-top: 1px solid #3c4a3f; margin: 24px 0; }
              img { max-width: 100%; height: auto; border-radius: 6px; display: block; margin: 12px auto; }
              div[align="center"] { text-align: center; }
            </style>
            </head>
            <body>
              <div id="content"></div>
              <script>
                const rawMarkdown = `$safeMd`;

                function parseMarkdownOffline(md) {
                  let html = md;

                  // Code blocks ```
                  html = html.replace(/```(\w*)\n([\s\S]*?)```/g, function(m, lang, code) {
                    return '<pre><code>' + escapeXml(code) + '</code></pre>';
                  });

                  // Tables
                  html = html.replace(/(?:^|\n)((?:\|[^\n]+\|\n?)+)/g, function(m, tableStr) {
                    const lines = tableStr.trim().split('\n').filter(l => l.trim().length > 0);
                    if (lines.length < 2) return m;
                    let tHtml = '<table>';
                    lines.forEach((line, idx) => {
                      if (line.includes('---')) return;
                      const cells = line.split('|').map(c => c.trim()).filter((c, i, a) => i > 0 && i < a.length - 1);
                      const tag = idx === 0 ? 'th' : 'td';
                      tHtml += '<tr>' + cells.map(c => '<' + tag + '>' + parseInline(c) + '</' + tag + '>').join('') + '</tr>';
                    });
                    tHtml += '</table>';
                    return tHtml;
                  });

                  // Headers
                  html = html.replace(/^######\s*(.*)$/gm, '<h6>$1</h6>');
                  html = html.replace(/^#####\s*(.*)$/gm, '<h5>$1</h5>');
                  html = html.replace(/^####\s*(.*)$/gm, '<h4>$1</h4>');
                  html = html.replace(/^###\s*(.*)$/gm, '<h3>$1</h3>');
                  html = html.replace(/^##\s*(.*)$/gm, '<h2>$1</h2>');
                  html = html.replace(/^#\s*(.*)$/gm, '<h1>$1</h1>');

                  // Blockquotes
                  html = html.replace(/^>\s*(.*)$/gm, '<blockquote>$1</blockquote>');

                  // HR
                  html = html.replace(/^---+$|^\*\*\*+$/gm, '<hr>');

                  // Inline
                  html = parseInline(html);

                  // Lists
                  html = html.replace(/^\s*[-*]\s+(.*)$/gm, '<li>$1</li>');
                  html = html.replace(/(?:<li>.*?<\/li>\n?)+/g, function(m) { return '<ul>' + m + '</ul>'; });

                  // Paragraphs
                  html = html.replace(/\n{2,}/g, '</p><p>');

                  return html;
                }

                function parseInline(text) {
                  return text
                    .replace(/\!\s*\[([^\]]*)\]\(([^)]+)\)/g, '<img src="$2" alt="$1" />')
                    .replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2" target="_blank">$1</a>')
                    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
                    .replace(/\*([^*]+)\*/g, '<em>$1</em>')
                    .replace(/`([^`]+)`/g, '<code>$1</code>');
                }

                function escapeXml(str) {
                  return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
                }

                try {
                  if (typeof marked !== 'undefined' && marked.parse) {
                    document.getElementById('content').innerHTML = marked.parse(rawMarkdown);
                  } else {
                    document.getElementById('content').innerHTML = parseMarkdownOffline(rawMarkdown);
                  }
                } catch(e) {
                  document.getElementById('content').innerHTML = parseMarkdownOffline(rawMarkdown);
                }
              </script>
            </body>
            </html>
        """.trimIndent()
    }

    private fun showDiffViewer(name: String, backPage: Int = ID_GIT) {
        diffViewerBackPage = backPage
        if (backPage == ID_PROJECT_GIT_DIFF) {
            unifiedHeader.visibility = View.GONE
            projectBottomNavigation.visibility = View.VISIBLE
            bottomNavigation.visibility = View.GONE
            diffViewerTopBar.visibility = View.VISIBLE
            updateProjectSubpageTopBar(diffViewerTopBar)
        } else {
            unifiedHeader.visibility = View.VISIBLE
            projectBottomNavigation.visibility = View.GONE
            bottomNavigation.visibility = View.VISIBLE
            diffViewerTopBar.visibility = View.GONE
        }
        homeScrollView.visibility = View.GONE
        fileExplorerScrollView.visibility = View.GONE
        terminalWorkspaceLayout.visibility = View.GONE
        gitOperationsScrollView.visibility = View.GONE
        settingsHubScrollView.visibility = View.GONE
        if (::fileViewerRootContainer.isInitialized) {
            fileViewerRootContainer.visibility = View.GONE
        }

        if (::projectGitDiffContainer.isInitialized) projectGitDiffContainer.visibility = View.GONE
        if (::projectWorkspaceContainer.isInitialized) projectWorkspaceContainer.visibility = View.GONE
        if (::projectSettingsContainer.isInitialized) projectSettingsContainer.visibility = View.GONE
        if (::projectDirTreeContainer.isInitialized) projectDirTreeContainer.visibility = View.GONE

        diffViewerRootContainer.visibility = View.VISIBLE
        diffViewerScrollView.visibility = View.VISIBLE
        loadDiffForFile(name)
    }

    private val TYPE_CONTEXT = 0
    private val TYPE_DEL     = 1
    private val TYPE_ADD     = 2
    private val TYPE_HUNK    = 3

    private data class ParsedDiffRow(val type: Int, val old: String, val new_: String, val code: String)

    private fun loadDiffForFile(name: String) {
        diffViewerScrollView.scrollTo(0, 0)
        diffViewerContainer.removeAllViews()
        val loadingTv = TextView(this).apply {
            text = "Loading diff for $name..."
            setTextColor(NC.ON_SURF_VAR)
            textSize = 14f
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        diffViewerContainer.addView(loadingTv)

        executor.execute {
            val gitCmd = "cd $activeProjectPath && git diff HEAD -- \"$name\""
            val (lcArgs, lcEnv) = LinuxCommandBuilder.build(this, gitCmd)
            val pb = ProcessBuilder(*lcArgs)
            val env = pb.environment()
            env.putAll(lcEnv)
            env["GIT_PAGER"] = "cat"
            env["GIT_TERMINAL_PROMPT"] = "0"
            env["TERM"] = "dumb"
            pb.redirectErrorStream(true)

            try {
                val proc = pb.start()
                val lines = ArrayList<String>()
                proc.inputStream.bufferedReader().useLines { seq ->
                    seq.forEach { lines.add(it) }
                }
                proc.waitFor()

                var filteredLines = lines.filter { line ->
                    line.startsWith("diff ") || line.startsWith("index ") ||
                    line.startsWith("--- ") || line.startsWith("+++ ") ||
                    line.startsWith("@@") || line.startsWith("+") ||
                    line.startsWith("-") || line.startsWith(" ") ||
                    line.startsWith("\\") || line.startsWith("new file") ||
                    line.startsWith("deleted file") || line.startsWith("Binary")
                }

                if (filteredLines.isEmpty()) {
                    val untrackedCmd = "cd $activeProjectPath && [ -f \"$name\" ] && git diff --no-index /dev/null \"$name\""
                    val (utArgs, utEnv) = LinuxCommandBuilder.build(this, untrackedCmd)
                    val pbUntracked = ProcessBuilder(*utArgs)
                    val envUntracked = pbUntracked.environment()
                    envUntracked.putAll(utEnv)
                    pbUntracked.redirectErrorStream(true)
                    val procUntracked = pbUntracked.start()
                    val linesUntracked = ArrayList<String>()
                    procUntracked.inputStream.bufferedReader().useLines { seq ->
                        seq.forEach { linesUntracked.add(it) }
                    }
                    procUntracked.waitFor()
                    val filteredUntracked = linesUntracked.filter { line ->
                        line.startsWith("diff ") || line.startsWith("index ") ||
                        line.startsWith("--- ") || line.startsWith("+++ ") ||
                        line.startsWith("@@") || line.startsWith("+") ||
                        line.startsWith("-") || line.startsWith(" ") ||
                        line.startsWith("\\") || line.startsWith("new file") ||
                        line.startsWith("deleted file") || line.startsWith("Binary")
                    }
                    if (filteredUntracked.isNotEmpty()) {
                        filteredLines = filteredUntracked
                    }
                }

                mainHandler.post {
                    renderDiffLines(name, filteredLines)
                }
            } catch (e: Exception) {
                mainHandler.post {
                    diffViewerContainer.removeAllViews()
                    val errorTv = TextView(this@MainActivity).apply {
                        text = "Error running git diff: ${e.message}"
                        setTextColor(NC.ERROR)
                        setPadding(dp(16), dp(16), dp(16), dp(16))
                    }
                    diffViewerContainer.addView(errorTv)
                }
            }
        }
    }

    private fun renderDiffLines(fileName: String, lines: List<String>) {
        diffViewerContainer.removeAllViews()

        if (lines.isEmpty() || (lines.size == 1 && lines[0].trim().isEmpty())) {
            val noChanges = TextView(this).apply {
                text = "No changes detected in $fileName"
                setTextColor(NC.ON_SURF_VAR)
                setPadding(dp(16), dp(16), dp(16), dp(16))
            }
            diffViewerContainer.addView(noChanges)
            return
        }

        val isBinary = lines.any { it.startsWith("Binary files") || it.contains("differ") }
        if (isBinary) {
            val binaryTv = TextView(this).apply {
                text = "Binary file changes. No preview available."
                setTextColor(NC.ON_SURF_VAR)
                setPadding(dp(16), dp(16), dp(16), dp(16))
            }
            diffViewerContainer.addView(binaryTv)
            return
        }

        var additions = 0
        var deletions = 0
        val diffRows = ArrayList<ParsedDiffRow>()

        var oldLineNum = 0
        var newLineNum = 0

        for (line in lines) {
            if (line.startsWith("diff --git") || line.startsWith("index ") || line.startsWith("new file mode") || line.startsWith("deleted file mode")) {
                continue
            }
            if (line.startsWith("--- ") || line.startsWith("+++ ")) {
                continue
            }

            if (line.startsWith("@@")) {
                val parts = line.split(" ")
                if (parts.size >= 3) {
                    val oldPart = parts[1].removePrefix("-").split(",")
                    val newPart = parts[2].removePrefix("+").split(",")
                    oldLineNum = oldPart[0].toIntOrNull() ?: 0
                    newLineNum = newPart[0].toIntOrNull() ?: 0
                }
                diffRows.add(ParsedDiffRow(type = TYPE_HUNK, old = "", new_ = "", code = line))
                continue
            }

            if (line.startsWith("+")) {
                additions++
                diffRows.add(ParsedDiffRow(type = TYPE_ADD, old = "", new_ = newLineNum.toString(), code = line.substring(1)))
                newLineNum++
            } else if (line.startsWith("-")) {
                deletions++
                diffRows.add(ParsedDiffRow(type = TYPE_DEL, old = oldLineNum.toString(), new_ = "", code = line.substring(1)))
                oldLineNum++
            } else {
                if (line.startsWith("\\")) continue
                val code = if (line.startsWith(" ")) line.substring(1) else line
                diffRows.add(ParsedDiffRow(type = TYPE_CONTEXT, old = oldLineNum.toString(), new_ = newLineNum.toString(), code = code))
                oldLineNum++
                newLineNum++
            }
        }

        val diffCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cyberBrutalistBg(
                fillColor = NC.SURFACE,
                strokeColor = NC.BORDER,
                shadowColor = NC.SHADOW_DARK,
                offsetDp = 8,
                cornerRadiusDp = 0
            )
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }

        val cardHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(NC.SURFACE_VAR)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        val pathTv = TextView(this).apply {
            text = fileName
            textSize = 12f
            setTextColor(NC.ON_SURF_VAR)
            typeface = Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        val removedBadge = textBadge("-$deletions", Color.parseColor("#3d1212"), NC.ERROR)
        val addedBadge = textBadge("+$additions", Color.parseColor("#0d2a2a"), NC.SECONDARY)
        removedBadge.layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).apply { rightMargin = dp(6) }
        cardHeader.addView(pathTv)
        if (deletions > 0) cardHeader.addView(removedBadge)
        if (additions > 0) cardHeader.addView(addedBadge)
        diffCard.addView(cardHeader)

        val hScroll = HorizontalScrollView(this).apply {
            setBackgroundColor(NC.LOGBG)
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        val table = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(MATCH, WRAP)
        }

        for (row in diffRows) {
            val tableRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(3), dp(12), dp(3))
            }

            when (row.type) {
                TYPE_HUNK -> {
                    tableRow.setBackgroundColor(Color.argb(30, 255, 255, 255))
                    val hunkTv = TextView(this@MainActivity).apply {
                        text = row.code
                        textSize = 11f
                        setTextColor(NC.OUTLINE)
                        typeface = Typeface.MONOSPACE
                        setPadding(dp(12), dp(2), dp(12), dp(2))
                    }
                    tableRow.addView(hunkTv)
                }
                else -> {
                    val bg = when (row.type) {
                        TYPE_DEL -> Color.argb(50, 147, 0, 10)
                        TYPE_ADD -> Color.argb(50, 3, 181, 211)
                        else -> Color.TRANSPARENT
                    }
                    tableRow.setBackgroundColor(bg)

                    val oldLn = lineNumCell(row.old)
                    val newLn = lineNumCell(row.new_)

                    val marker = when (row.type) {
                        TYPE_DEL -> "-"
                        TYPE_ADD -> "+"
                        else -> " "
                    }
                    val markerColor = when (row.type) {
                        TYPE_DEL -> NC.ERROR
                        TYPE_ADD -> NC.SECONDARY
                        else -> NC.OUTLINE
                    }
                    val codeColor = when (row.type) {
                        TYPE_DEL -> NC.ON_SURF_VAR
                        else -> NC.ON_SURFACE
                    }

                    val markerTv = TextView(this@MainActivity).apply {
                        text = marker
                        textSize = 12f
                        setTextColor(markerColor)
                        typeface = Typeface.MONOSPACE
                        gravity = Gravity.CENTER
                        layoutParams = LinearLayout.LayoutParams(dp(24), WRAP)
                    }
                    val codeTv = TextView(this@MainActivity).apply {
                        text = row.code
                        textSize = 12f
                        setTextColor(codeColor)
                        typeface = Typeface.MONOSPACE
                        if (row.type == TYPE_DEL) paintFlags = paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                    }

                    tableRow.addView(oldLn)
                    tableRow.addView(newLn)
                    tableRow.addView(markerTv)
                    tableRow.addView(codeTv)
                }
            }
            table.addView(tableRow)
        }

        hScroll.addView(table)
        diffCard.addView(hScroll)
        diffViewerContainer.addView(diffCard)

        diffViewerContainer.addView(spacer(16))
        val bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, dp(16))
        }
        val discardBtn = outlineBtn("Discard")
        discardBtn.setOnClickListener {
            discardChanges(fileName)
        }
        val commitBtn = primaryBtn("Commit Changes")
        commitBtn.setOnClickListener {
            showCommitDialog(fileName)
        }
        discardBtn.layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).apply { rightMargin = dp(12) }
        bottomBar.addView(discardBtn)
        bottomBar.addView(commitBtn)
        diffViewerContainer.addView(bottomBar)
    }

    private fun discardChanges(fileName: String) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Discard Changes")
            .setMessage("Are you sure you want to discard changes in $fileName?")
            .setPositiveButton("Discard") { _, _ ->
                executor.execute {
                    val discardCmd = "cd $activeProjectPath && (git checkout -- \"$fileName\" || rm -rf \"$fileName\")"
                    val (lcArgs, lcEnv) = LinuxCommandBuilder.build(this, discardCmd)
                    val pb = ProcessBuilder(*lcArgs)
                    val env = pb.environment()
                    env.putAll(lcEnv)
                    env["GIT_PAGER"] = "cat"
                    env["GIT_TERMINAL_PROMPT"] = "0"
                    env["TERM"] = "dumb"
                    pb.redirectErrorStream(true)
                    try {
                        val proc = pb.start()
                        proc.waitFor()
                        mainHandler.post {
                            onBackPressed()
                            refreshGitDiffTree()
                        }
                    } catch (e: Exception) {
                        mainHandler.post {
                            Toast.makeText(this@MainActivity, "Failed to discard: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCommitDialog(fileName: String) {
        val input = EditText(this).apply {
            hint = "Commit message"
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        val container = FrameLayout(this).apply {
            addView(input, FrameLayout.LayoutParams(MATCH, WRAP).apply {
                leftMargin = dp(16)
                rightMargin = dp(16)
                topMargin = dp(8)
                bottomMargin = dp(8)
            })
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Commit changes")
            .setView(container)
            .setPositiveButton("Commit") { _, _ ->
                val msg = input.text.toString().trim()
                if (msg.isEmpty()) {
                    Toast.makeText(this, "Commit message cannot be empty", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                executor.execute {
                    val commitCmd = "cd $activeProjectPath && git add \"$fileName\" && git commit -m \"$msg\""
                    val (lcArgs, lcEnv) = LinuxCommandBuilder.build(this, commitCmd)
                    val pb = ProcessBuilder(*lcArgs)
                    val env = pb.environment()
                    env.putAll(lcEnv)
                    env["GIT_PAGER"] = "cat"
                    env["GIT_TERMINAL_PROMPT"] = "0"
                    env["TERM"] = "dumb"
                    pb.redirectErrorStream(true)
                    try {
                        val proc = pb.start()
                        proc.waitFor()
                        mainHandler.post {
                            Toast.makeText(this@MainActivity, "Committed successfully", Toast.LENGTH_SHORT).show()
                            onBackPressed()
                            refreshGitDiffTree()
                        }
                    } catch (e: Exception) {
                        mainHandler.post {
                            Toast.makeText(this@MainActivity, "Commit failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Shared widget builders (ported from old activities) ─────────────────────

    private fun buildStatusCard(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cyberBrutalistBg(
                fillColor = NC.SURFACE_LOW,
                strokeColor = NC.OUTLINE_VAR,
                shadowColor = NC.SHADOW_DARK,
                offsetDp = 6,
                cornerRadiusDp = 0
            )
            setPadding(dp(16), dp(16), dp(16), dp(16))
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
        val dnsIcon = ImageView(this).apply { setImageResource(R.drawable.ic_dns_thick); setColorFilter(NC.PRIMARY); layoutParams = LinearLayout.LayoutParams(dp(18), dp(18)).apply { rightMargin = dp(6) } }
        homeContainerLabel = TextView(this).apply { text = ProjectPathResolver.methodLabel(); textSize = 13f; setTextColor(NC.ON_SURF_VAR); typeface = Typeface.MONOSPACE }
        infoRow.addView(dnsIcon); infoRow.addView(homeContainerLabel); card.addView(infoRow)

        pulseView(homeStatusDot)
        return card
    }

    private fun buildGuiLaunchCard(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cyberBrutalistBg(
                fillColor = NC.SURFACE_LOW,
                strokeColor = NC.OUTLINE_VAR,
                shadowColor = NC.SHADOW_DARK,
                offsetDp = 6,
                cornerRadiusDp = 0
            )
            setPadding(dp(16), dp(16), dp(16), dp(16))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        val sectionTitleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(4))
        }
        val sectionTitleIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_laptop_thick)
            setColorFilter(NC.PRIMARY)
            layoutParams = LinearLayout.LayoutParams(dp(20), dp(20)).apply { rightMargin = dp(8) }
        }
        val sectionTitle = TextView(this).apply {
            text = "GRAPHICAL DESKTOP"
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }
        sectionTitleRow.addView(sectionTitleIcon)
        sectionTitleRow.addView(sectionTitle)
        val sectionSub = TextView(this).apply {
            text = "Launch Termux X11 and start or stop the XFCE4 desktop session."
            textSize = 12f
            setTextColor(NC.ON_SURF_VAR)
            typeface = Typeface.MONOSPACE
            setPadding(0, 0, 0, dp(14))
        }
        card.addView(sectionTitleRow)
        card.addView(sectionSub)

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        startGuiBtn = TextView(this).apply {
            text = "  START XFCE DESKTOP"
            textSize = 14f
            setTextColor(NC.ON_PRIMARY)
            typeface = Typeface.MONOSPACE
            paint.isFakeBoldText = true
            gravity = Gravity.CENTER
            background = cyberBrutalistBg(
                fillColor = NC.PRIMARY_CON,
                strokeColor = NC.OUTLINE_VAR,
                shadowColor = NC.SHADOW_DARK,
                offsetDp = 6,
                cornerRadiusDp = 0
            )
            setPadding(dp(16), dp(12), dp(16), dp(12))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)

            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        v.translationX = dp(4).toFloat()
                        v.translationY = dp(4).toFloat()
                        v.background = cyberBrutalistBg(
                            fillColor = Color.parseColor("#49E48F"),
                            strokeColor = NC.OUTLINE_VAR,
                            shadowColor = NC.SHADOW_DARK,
                            offsetDp = 2,
                            cornerRadiusDp = 0
                        )
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.translationX = 0f
                        v.translationY = 0f
                        v.background = cyberBrutalistBg(
                            fillColor = NC.PRIMARY_CON,
                            strokeColor = NC.OUTLINE_VAR,
                            shadowColor = NC.SHADOW_DARK,
                            offsetDp = 6,
                            cornerRadiusDp = 0
                        )
                    }
                }
                false
            }

            setOnClickListener {
                startGui()
                startGuiBtn.visibility = View.GONE
                stopGuiBtn.visibility = View.VISIBLE
                stopGuiBtn.isEnabled = true
                stopGuiBtn.alpha = 1f
                if (::displayBtn.isInitialized) {
                    displayBtn.visibility = View.VISIBLE
                }
            }
        }
        startGuiBtn.isEnabled = false
        startGuiBtn.alpha = 0.5f

        stopGuiBtn = dangerButton("  STOP XFCE DESKTOP") {
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
        stopGuiBtn.isEnabled = false
        stopGuiBtn.alpha = 0.5f
        stopGuiBtn.visibility = View.GONE

        row.addView(startGuiBtn)
        row.addView(stopGuiBtn)
        card.addView(row)
        return card
    }

    private fun buildResourcesCard(): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }

        // 1. APP STORAGE Used Card
        val storageCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cyberBrutalistBg(
                fillColor = NC.SURFACE_LOW,
                strokeColor = Color.parseColor("#3c4a3f"),
                shadowColor = NC.SHADOW_GREEN,
                offsetDp = 6
            )
            setPadding(dp(16), dp(16), dp(16), dp(16))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(16) }
        }

        // Header Row: APP STORAGE Used & analytics icon
        val storageHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(10) }
        }
        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        val storageTitle = TextView(this).apply {
            text = "APP STORAGE USED"
            textSize = 12f
            setTextColor(Color.parseColor("#99E5E2E1"))
            typeface = Typeface.MONOSPACE
        }
        val titleUnderline = View(this).apply {
            setBackgroundColor(Color.parseColor("#4D60F99E"))
            layoutParams = LinearLayout.LayoutParams(dp(130), dp(1)).apply { topMargin = dp(2) }
        }
        titleCol.addView(storageTitle)
        titleCol.addView(titleUnderline)

        val storageIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_storage)
            imageTintList = android.content.res.ColorStateList.valueOf(NC.PRIMARY)
            layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
        }
        storageHeaderRow.addView(titleCol)
        storageHeaderRow.addView(storageIcon)
        storageCard.addView(storageHeaderRow)

        // Storage Value & Badge Row
        val valRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(16) }
        }
        val storageValTv = TextView(this).apply {
            tag = "APP_STORAGE_VAL"
            text = "84.2"
            textSize = 36f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, dp(6), 0)
        }
        val storageUnitTv = TextView(this).apply {
            tag = "APP_STORAGE_SUB"
            text = "GB"
            textSize = 18f
            setTextColor(Color.parseColor("#B3FFFFFF"))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(4))
        }
        val versionBadge = TextView(this).apply {
            text = "STABLE_V1"
            textSize = 10f
            setTextColor(NC.SURFACE_LOWEST)
            typeface = Typeface.MONOSPACE
            background = cyberBrutalistBg(NC.PRIMARY, Color.parseColor("#3c4a3f"), NC.SHADOW_GREEN, 6, 0)
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        val valRowLeft = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
            addView(storageValTv)
            addView(storageUnitTv)
        }
        valRow.addView(valRowLeft)
        valRow.addView(versionBadge)
        storageCard.addView(valRow)

        // Gauge Progress Bar (Height 32dp)
        val gaugeFrame = FrameLayout(this).apply {
            background = cyberBrutalistBg(NC.SURFACE_LOWEST, Color.parseColor("#1AFFFFFF"), NC.SHADOW_DARK, 6, 0)
            layoutParams = LinearLayout.LayoutParams(MATCH, dp(32))
        }
        val barFill = View(this).apply {
            tag = "APP_STORAGE_BAR"
            background = roundedBg(NC.PRIMARY, NC.PRIMARY, 0)
            layoutParams = FrameLayout.LayoutParams(dp(200), MATCH)
        }
        gaugeFrame.addView(barFill)

        // Overlay gauge ticks (vertical tick lines)
        val ticksLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            weightSum = 10f
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
            setPadding(dp(8), 0, dp(8), 0)
        }
        for (i in 0..9) {
            val tick = View(this).apply {
                setBackgroundColor(Color.parseColor("#26FFFFFF"))
                layoutParams = LinearLayout.LayoutParams(dp(1), MATCH)
            }
            val spacerView = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, MATCH, 1f)
            }
            ticksLayout.addView(tick)
            if (i < 9) ticksLayout.addView(spacerView)
        }
        gaugeFrame.addView(ticksLayout)
        storageCard.addView(gaugeFrame)

        // Ticks footer labels row
        val footerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(6) }
        }
        val label0 = TextView(this).apply {
            text = "0% LOAD"
            textSize = 10f
            setTextColor(Color.parseColor("#66FFFFFF"))
            typeface = Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        val label80 = TextView(this).apply {
            text = "CRITICAL_80%"
            textSize = 10f
            setTextColor(Color.parseColor("#66FFFFFF"))
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        val label100 = TextView(this).apply {
            text = "100% MAX"
            textSize = 10f
            setTextColor(Color.parseColor("#66FFFFFF"))
            typeface = Typeface.MONOSPACE
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        footerRow.addView(label0)
        footerRow.addView(label80)
        footerRow.addView(label100)
        storageCard.addView(footerRow)

        container.addView(storageCard)

        // 2. STAT ROW (CPU, RAM, DISK - 3 columns grid)
        val statRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 3f
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }

        fun buildStatCard(labelStr: String, defaultVal: String, fillPercent: Int, accentColor: Int, isLast: Boolean): Pair<View, TextView> {
            val card = RelativeLayout(this).apply {
                background = cyberBrutalistBg(NC.SURFACE_LOW, Color.parseColor("#3c4a3f"), NC.SHADOW_GREEN, 6, 0)
                setPadding(dp(12), dp(12), dp(12), dp(12))
                val lp = LinearLayout.LayoutParams(0, dp(112), 1f)
                if (!isLast) lp.rightMargin = dp(8)
                layoutParams = lp
            }

            // Top-right corner indicator block
            val cornerBlock = View(this).apply {
                setBackgroundColor(accentColor)
                val rlp = RelativeLayout.LayoutParams(dp(10), dp(10)).apply {
                    addRule(RelativeLayout.ALIGN_PARENT_TOP)
                    addRule(RelativeLayout.ALIGN_PARENT_END)
                }
                layoutParams = rlp
            }
            card.addView(cornerBlock)

            // Content container
            val contentCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                val rlp = RelativeLayout.LayoutParams(MATCH, WRAP).apply {
                    addRule(RelativeLayout.ALIGN_PARENT_TOP)
                    addRule(RelativeLayout.ALIGN_PARENT_START)
                }
                layoutParams = rlp
            }

            val labelTv = TextView(this).apply {
                text = labelStr
                textSize = 11f
                setTextColor(Color.parseColor("#80FFFFFF"))
                typeface = Typeface.MONOSPACE
            }
            val valueTv = TextView(this).apply {
                text = defaultVal
                textSize = 18f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, dp(4), 0, 0)
            }
            contentCol.addView(labelTv)
            contentCol.addView(valueTv)
            card.addView(contentCol)

            // Bottom progress bar
            val barTrack = FrameLayout(this).apply {
                setBackgroundColor(NC.SURFACE_LOWEST)
                val rlp = RelativeLayout.LayoutParams(MATCH, dp(4)).apply {
                    addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                }
                layoutParams = rlp
            }
            val fillBar = View(this).apply {
                setBackgroundColor(accentColor)
                layoutParams = FrameLayout.LayoutParams((fillPercent * dp(80) / 100).coerceAtLeast(dp(6)), MATCH)
            }
            barTrack.addView(fillBar)
            card.addView(barTrack)

            return Pair(card, valueTv)
        }

        val (cpuCard, cpuTv) = buildStatCard("CPU", "12%", 12, NC.PRIMARY, false)
        val (ramCard, ramTv) = buildStatCard("RAM", "4.1G", 45, Color.parseColor("#80FFFFFF"), false)
        val (diskCard, diskTv) = buildStatCard("DISK", "94%", 94, NC.ERROR, true)

        statRow.addView(cpuCard)
        statRow.addView(ramCard)
        statRow.addView(diskCard)

        container.addView(statRow)

        startResourceMonitoring(cpuTv, ramTv, diskTv)
        updateAppStorageUsage(storageValTv, storageUnitTv, barFill, null)

        return container
    }

    private fun buildRecentProjectsSection(): View {
        val section = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(16) }
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(12))
        }
        val sectionTitle = TextView(this).apply {
            text = ">> RECENT WORKSPACES"
            textSize = 13f
            setTextColor(NC.PRIMARY)
            typeface = Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        val viewAll = TextView(this).apply {
            text = "View All"
            textSize = 12f
            setTextColor(Color.parseColor("#80FFFFFF"))
            typeface = Typeface.MONOSPACE
            visibility = View.GONE
            setOnClickListener {
                if (pageStack.isEmpty() || pageStack.peek() != ID_PROJECTS_LIST) {
                    pageStack.push(ID_PROJECTS_LIST)
                }
                navigateToPage(ID_PROJECTS_LIST)
            }
        }
        headerRow.addView(sectionTitle)
        headerRow.addView(viewAll)
        section.addView(headerRow)

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
        val projects = getProjects().sortedByDescending { it.lastOpened }
        if (projects.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "No recent projects found"
                textSize = 13f
                setTextColor(NC.ON_SURF_VAR)
                setPadding(dp(4), dp(8), dp(4), dp(8))
                typeface = Typeface.MONOSPACE
            }
            recentProjectsContainer.addView(emptyTv)
            return
        }
        val dateFormat = java.text.SimpleDateFormat("MM.dd.yy", java.util.Locale.US)
        for (p in projects.take(5)) {
            val dateStr = if (p.lastOpened > 0) dateFormat.format(java.util.Date(p.lastOpened)) else "recent"
            val card = projectCard(p.name, p.path, dateStr, p.icon)
            card.setOnClickListener {
                markProjectOpened(p.path)
                activeProjectName = p.name
                activeProjectPath = p.path
                applyProjectIsolation(p.path, p.linuxMethod)
                if (pageStack.isEmpty() || pageStack.peek() != ID_PROJECT_WORKSPACE) {
                    pageStack.push(ID_PROJECT_WORKSPACE)
                }
                navigateToPage(ID_PROJECT_WORKSPACE)
            }
            recentProjectsContainer.addView(card)
            recentProjectsContainer.addView(spacer(10))
        }
    }

    private fun buildAuthCard(): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = roundedBg(NC.SURFACE, NC.BORDER, dp(12)); setPadding(dp(16))
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, dp(8)) }
        val icon = ImageView(this).apply { setImageResource(R.drawable.ic_laptop_thick); setColorFilter(NC.PRIMARY); layoutParams = LinearLayout.LayoutParams(dp(20), dp(20)).apply { rightMargin = dp(8) } }
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
        val icon = ImageView(this).apply { setImageResource(R.drawable.ic_git_thick); setColorFilter(NC.PRIMARY); layoutParams = LinearLayout.LayoutParams(dp(20), dp(20)).apply { rightMargin = dp(8) } }
        val title = TextView(this).apply { text = "Active Branches"; textSize = 16f; setTextColor(NC.ON_SURFACE); typeface = Typeface.DEFAULT_BOLD; layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f) }
        val fetchBtn = TextView(this).apply {
            text = " git fetch"
            textSize = 11f
            setTextColor(NC.ON_SURF_VAR)
            typeface = Typeface.MONOSPACE
            setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_refresh, 0, 0, 0)
            compoundDrawableTintList = android.content.res.ColorStateList.valueOf(NC.ON_SURF_VAR)
            background = roundedBg(NC.SURFACE_HIGH, NC.BORDER, dp(4))
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
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
        card.addView(sectionHeader(R.drawable.ic_laptop_thick, "Linux Isolation Mode", NC.PRIMARY))

        val descTv = TextView(this).apply {
            text = "Select execution engine for Linux sessions and CLI tools:"
            textSize = 12f
            setTextColor(NC.ON_SURF_VAR)
            setPadding(0, 0, 0, dp(12))
        }
        card.addView(descTv)

        val modeContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }

        val prootCard = LinearLayout(this)
        val chrootCard = LinearLayout(this)

        fun updateCardStyles() {
            val isProot = LinuxCommandBuilder.currentMethod == "proot"
            prootCard.apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = cyberBrutalistBg(
                    fillColor = if (isProot) NC.SURFACE_CONTAINER else NC.SURFACE_LOWEST,
                    strokeColor = if (isProot) NC.PRIMARY else NC.OUTLINE_VAR,
                    shadowColor = NC.SHADOW_DARK,
                    offsetDp = 4,
                    cornerRadiusDp = 0
                )
                setPadding(dp(12), dp(12), dp(12), dp(12))
                layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(10) }
                isClickable = true
                isFocusable = true
            }

            chrootCard.apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = cyberBrutalistBg(
                    fillColor = if (!isProot) NC.SURFACE_CONTAINER else NC.SURFACE_LOWEST,
                    strokeColor = if (!isProot) NC.SECONDARY else NC.OUTLINE_VAR,
                    shadowColor = NC.SHADOW_DARK,
                    offsetDp = 4,
                    cornerRadiusDp = 0
                )
                setPadding(dp(12), dp(12), dp(12), dp(12))
                layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
                isClickable = true
                isFocusable = true
            }
        }

        // Build PRoot Option Row
        val prootLeft = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        val prootTitle = TextView(this).apply {
            text = "PROOT (Rootless)"
            textSize = 14f
            setTextColor(NC.ON_SURFACE)
            typeface = Typeface.DEFAULT_BOLD
        }
        val prootSub = TextView(this).apply {
            text = "User-space isolation. No root required."
            textSize = 11f
            setTextColor(NC.ON_SURF_VAR)
        }
        prootLeft.addView(prootTitle)
        prootLeft.addView(prootSub)
        prootCard.addView(prootLeft)

        // Build Chroot Option Row
        val chrootLeft = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        val chrootTitle = TextView(this).apply {
            text = "CHROOT (KernelSU / Root)"
            textSize = 14f
            setTextColor(NC.ON_SURFACE)
            typeface = Typeface.DEFAULT_BOLD
        }
        val chrootSub = TextView(this).apply {
            text = if (ProjectPathResolver.isChrootInstalled()) "Installed & Ready. Kernel-level speed." else "Requires Root & Debian 13 Chroot Setup"
            textSize = 11f
            setTextColor(if (ProjectPathResolver.isChrootInstalled()) NC.PRIMARY else NC.SECONDARY)
        }
        chrootLeft.addView(chrootTitle)
        chrootLeft.addView(chrootSub)
        chrootCard.addView(chrootLeft)

        updateCardStyles()

        prootCard.setOnClickListener {
            LinuxCommandBuilder.currentMethod = "proot"
            getSharedPreferences("nativecode_prefs", MODE_PRIVATE)
                .edit().putString("linux_method", "proot").apply()
            updateCardStyles()
            if (::homeContainerLabel.isInitialized) homeContainerLabel.text = ProjectPathResolver.methodLabel()
            refreshToolCardsForMethod()
            Toast.makeText(this, "Switched to PRoot Mode", Toast.LENGTH_SHORT).show()
        }

        chrootCard.setOnClickListener {
            executor.execute {
                val rootAvailable = RootShell.isRootAvailable()
                mainHandler.post {
                    if (!rootAvailable) {
                        Toast.makeText(this, "Root access (su) not detected via KernelSU/Magisk", Toast.LENGTH_LONG).show()
                    } else if (!ProjectPathResolver.isChrootInstalled()) {
                        Toast.makeText(this, "Root detected! Please run Chroot setup in Onboarding to finish installation.", Toast.LENGTH_LONG).show()
                        LinuxCommandBuilder.currentMethod = "chroot"
                        getSharedPreferences("nativecode_prefs", MODE_PRIVATE)
                            .edit().putString("linux_method", "chroot").apply()
                        updateCardStyles()
                        if (::homeContainerLabel.isInitialized) homeContainerLabel.text = ProjectPathResolver.methodLabel()
                        refreshToolCardsForMethod()
                    } else {
                        LinuxCommandBuilder.currentMethod = "chroot"
                        getSharedPreferences("nativecode_prefs", MODE_PRIVATE)
                            .edit().putString("linux_method", "chroot").apply()
                        updateCardStyles()
                        if (::homeContainerLabel.isInitialized) homeContainerLabel.text = ProjectPathResolver.methodLabel()
                        refreshToolCardsForMethod()
                        Toast.makeText(this, "Switched to Chroot Mode", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        modeContainer.addView(prootCard)
        modeContainer.addView(chrootCard)
        card.addView(modeContainer)

        card.addView(spacer(12))
        card.addView(infoRow("OS Version", "Debian 13 (Trixie)"))
        return card
    }

    /**
     * Dedicated Proot Settings page (Settings Hub → this).
     * Size-only: Debian proot rootfs under app filesDir + REFRESH + path.
     */
    private fun buildProotSettingsPage() {
        prootSettingsScrollView = ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
            visibility = View.GONE
            clipToPadding = false
            setPadding(0, 0, 0, dp(80))
            setBackgroundColor(NC.BG)
        }
        val pageLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        prootSettingsScrollView.addView(pageLayout)

        val headerCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(16))
        }
        val headerTitleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val pageBackBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_arrow_back)
            setColorFilter(NC.PRIMARY)
            setPadding(dp(4), dp(4), dp(4), dp(4))
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply { rightMargin = dp(8) }
            contentDescription = "Back"
            setOnClickListener {
                if (pageStack.isNotEmpty() && pageStack.peek() == ID_PROOT_SETTINGS) {
                    pageStack.pop()
                }
                navigateToPage(ID_SETTINGS, false)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(NC.SURFACE_CONTAINER)
                setStroke(dp(1), NC.OUTLINE_VAR)
            }
        }
        val headerIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_storage)
            setColorFilter(NC.PRIMARY)
            layoutParams = LinearLayout.LayoutParams(dp(26), dp(26)).apply { rightMargin = dp(10) }
        }
        val titleTv = TextView(this).apply {
            text = "PROOT SETTINGS"
            textSize = 22f
            setTextColor(NC.PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        headerTitleRow.addView(pageBackBtn)
        headerTitleRow.addView(headerIcon)
        headerTitleRow.addView(titleTv)
        val subTitleTv = TextView(this).apply {
            text = "// DEBIAN PROOT — APP STORAGE"
            textSize = 11f
            setTextColor(NC.ON_SURF_VAR)
            typeface = Typeface.MONOSPACE
            setPadding(0, dp(4), 0, dp(12))
        }
        val divider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, dp(1)).apply { bottomMargin = dp(8) }
            setBackgroundColor(NC.OUTLINE_VAR)
        }
        headerCol.addView(headerTitleRow)
        headerCol.addView(subTitleTv)
        headerCol.addView(divider)
        pageLayout.addView(headerCol)
        pageLayout.addView(buildProotSettingsContentCard())
    }

    /** Proot detail card — rootfs size + host path only (no install/root). */
    private fun buildProotSettingsContentCard(): LinearLayout {
        val card = glassCard()
        card.addView(sectionHeader(R.drawable.ic_storage, "Storage", NC.PRIMARY))

        val subTv = TextView(this).apply {
            text = "// DEBIAN PROOT ROOTFS — MEASURED IN-APP"
            textSize = 10f
            setTextColor(NC.ON_SURF_VAR)
            typeface = Typeface.MONOSPACE
            setPadding(0, 0, 0, dp(10))
        }
        card.addView(subTv)

        val sizePanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(NC.SURFACE_LOWEST)
                setStroke(dp(1), NC.OUTLINE_VAR)
            }
            setPadding(dp(12), dp(12), dp(12), dp(12))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(10) }
        }
        val sizeHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val sizeTitle = TextView(this).apply {
            text = "LINUX STORAGE"
            textSize = 11f
            setTextColor(NC.ON_SURF_VAR)
            typeface = Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        prootRefreshBtn = TextView(this).apply {
            text = " REFRESH"
            textSize = 10f
            setTextColor(NC.PRIMARY)
            typeface = Typeface.MONOSPACE
            setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_refresh, 0, 0, 0)
            compoundDrawableTintList = android.content.res.ColorStateList.valueOf(NC.PRIMARY)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(NC.SURFACE_CONTAINER)
                setStroke(dp(1), NC.OUTLINE_VAR)
            }
            setPadding(dp(8), dp(4), dp(10), dp(4))
            setOnClickListener { refreshProotSettingsCard(force = true) }
        }
        sizeHeader.addView(sizeTitle)
        sizeHeader.addView(prootRefreshBtn)
        sizePanel.addView(sizeHeader)

        prootLoadingRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setPadding(0, dp(10), 0, dp(4))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        prootProgressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            layoutParams = LinearLayout.LayoutParams(0, dp(4), 1f)
            indeterminateTintList = android.content.res.ColorStateList.valueOf(NC.PRIMARY)
        }
        val loadingLabel = TextView(this).apply {
            text = " SCANNING"
            textSize = 10f
            setTextColor(NC.PRIMARY)
            typeface = Typeface.MONOSPACE
            setPadding(dp(8), 0, 0, 0)
        }
        prootLoadingRow?.addView(prootProgressBar)
        prootLoadingRow?.addView(loadingLabel)
        sizePanel.addView(prootLoadingRow)

        val sizeValRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            setPadding(0, dp(8), 0, 0)
        }
        prootSizeValueTv = TextView(this).apply {
            text = "—"
            textSize = 32f
            setTextColor(NC.PRIMARY)
            typeface = Typeface.MONOSPACE
            paint.isFakeBoldText = true
            layoutParams = LinearLayout.LayoutParams(WRAP, WRAP)
        }
        prootSizeUnitTv = TextView(this).apply {
            text = ""
            textSize = 14f
            setTextColor(NC.ON_SURF_VAR)
            typeface = Typeface.MONOSPACE
            setPadding(dp(6), 0, 0, dp(6))
        }
        sizeValRow.addView(prootSizeValueTv)
        sizeValRow.addView(prootSizeUnitTv)
        sizePanel.addView(sizeValRow)

        prootSizeHintTv = TextView(this).apply {
            text = "…"
            textSize = 10f
            setTextColor(NC.ON_SURF_VAR)
            typeface = Typeface.MONOSPACE
            setPadding(0, dp(4), 0, 0)
        }
        sizePanel.addView(prootSizeHintTv)
        card.addView(sizePanel)

        val pathRow = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(NC.SURFACE_LOWEST)
                setStroke(dp(1), NC.OUTLINE_VAR)
            }
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(4) }
        }
        pathRow.addView(TextView(this).apply {
            text = "HOST PATH"
            textSize = 10f
            setTextColor(NC.ON_SURF_VAR)
            typeface = Typeface.MONOSPACE
            setPadding(0, 0, 0, dp(4))
        })
        prootPathTv = TextView(this).apply {
            text = ProjectPathResolver.prootRootfsDir(this@MainActivity).absolutePath
            textSize = 12f
            setTextColor(Color.WHITE)
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
        }
        pathRow.addView(prootPathTv)
        card.addView(pathRow)

        applyCachedProotInfo()
        refreshProotSettingsCard(force = false)
        return card
    }

    private fun applyCachedProotInfo() {
        val prefs = getSharedPreferences("nativecode_prefs", MODE_PRIVATE)
        if (!prefs.contains(PREF_PROOT_LAST_MS)) return

        val bytes = prefs.getLong(PREF_PROOT_BYTES, -1L).takeIf { it >= 0L }
        val dirOk = prefs.getBoolean(PREF_PROOT_DIR, false)
        val lastMs = prefs.getLong(PREF_PROOT_LAST_MS, 0L)

        if (bytes != null) {
            val (v, u) = formatStorageBytes(bytes)
            prootSizeValueTv?.text = v
            prootSizeUnitTv?.text = u
            prootSizeValueTv?.alpha = 0.85f
            prootSizeHintTv?.text = formatProotCacheHint(lastMs)
            prootSizeHintTv?.setTextColor(NC.ON_SURF_VAR)
        } else if (!dirOk) {
            prootSizeValueTv?.text = "—"
            prootSizeUnitTv?.text = ""
            prootSizeHintTv?.text = "Proot rootfs not found"
            prootSizeHintTv?.setTextColor(NC.SECONDARY)
        }
    }

    private fun formatProotCacheHint(lastMs: Long): String {
        val age = if (lastMs > 0L) {
            val mins = ((System.currentTimeMillis() - lastMs) / 60000L).coerceAtLeast(0L)
            when {
                mins < 1 -> "just now"
                mins < 60 -> "${mins}m ago"
                mins < 1440 -> "${mins / 60}h ago"
                else -> "${mins / 1440}d ago"
            }
        } else "unknown"
        return "Cached · Debian proot rootfs · $age"
    }

    private fun saveProotInfo(dirPresent: Boolean, bytes: Long?) {
        getSharedPreferences("nativecode_prefs", MODE_PRIVATE).edit()
            .putBoolean(PREF_PROOT_DIR, dirPresent)
            .putLong(PREF_PROOT_BYTES, bytes ?: -1L)
            .putLong(PREF_PROOT_LAST_MS, System.currentTimeMillis())
            .apply()
    }

    private fun setProotLoading(loading: Boolean) {
        prootMeasuring = loading
        prootLoadingRow?.visibility = if (loading) View.VISIBLE else View.GONE
        prootRefreshBtn?.isEnabled = !loading
        prootRefreshBtn?.alpha = if (loading) 0.45f else 1f
        if (loading) {
            prootSizeHintTv?.text = "Measuring Debian proot rootfs…"
            prootSizeHintTv?.setTextColor(NC.PRIMARY)
            prootSizeValueTv?.alpha = 0.45f
        } else {
            prootSizeValueTv?.alpha = 1f
        }
    }

    /**
     * Measure complete proot rootfs under app filesDir (no root / no excludes).
     * Prefers `du -sb`; falls back to Java walk.
     */
    private fun refreshProotSettingsCard(force: Boolean = true) {
        if (prootSizeValueTv == null) return
        if (prootMeasuring) return

        val rootfs = ProjectPathResolver.prootRootfsDir(this)
        prootPathTv?.text = rootfs.absolutePath

        setProotLoading(true)
        executor.execute {
            val dirPresent = rootfs.isDirectory
            var bytes: Long? = null
            var measureNote: String

            if (!dirPresent) {
                measureNote = "Proot rootfs not found"
            } else {
                bytes = measureProotRootfsBytes(rootfs)
                measureNote = if (bytes != null) {
                    "Debian proot rootfs · measured in-app"
                } else {
                    "Size probe failed"
                }
            }

            saveProotInfo(dirPresent, bytes)
            val (valStr, unitStr) = formatStorageBytes(bytes)
            Log.d(
                "ProotSettings",
                "path=${rootfs.absolutePath} present=$dirPresent bytes=$bytes"
            )

            mainHandler.post {
                setProotLoading(false)
                prootSizeValueTv?.text = valStr
                prootSizeUnitTv?.text = unitStr
                prootSizeHintTv?.text = measureNote
                prootSizeHintTv?.setTextColor(
                    if (dirPresent && bytes != null) NC.ON_SURF_VAR else NC.SECONDARY
                )
            }
        }
    }

    /** `du -sb` on own filesDir; Java walk fallback. No bind excludes (plain tree). */
    private fun measureProotRootfsBytes(rootfs: File): Long? {
        val path = rootfs.absolutePath
        try {
            val pb = ProcessBuilder("du", "-sb", path)
            pb.redirectErrorStream(true)
            val proc = pb.start()
            val out = proc.inputStream.bufferedReader().use { it.readText() }
            val ok = proc.waitFor() == 0
            val parsed = out.trim().lines()
                .mapNotNull { line ->
                    line.trim().split(Regex("\\s+")).firstOrNull()?.toLongOrNull()
                }
                .firstOrNull()
            if (ok && parsed != null && parsed >= 0L) return parsed
            if (parsed != null && parsed >= 0L) return parsed
        } catch (_: Exception) {
            // fall through to walk
        }
        return try {
            var total = 0L
            rootfs.walkTopDown().forEach { f ->
                if (f.isFile) total += f.length()
            }
            total
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Dedicated Chroot Settings page (Settings Hub → this).
     * Hub only shows a nav row; full status/size/root/uninstall lives here.
     */
    private fun buildChrootSettingsPage() {
        chrootSettingsScrollView = ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
            visibility = View.GONE
            clipToPadding = false
            setPadding(0, 0, 0, dp(80))
            setBackgroundColor(NC.BG)
        }
        val pageLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        chrootSettingsScrollView.addView(pageLayout)

        val headerCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(16))
        }
        val headerTitleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val pageBackBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_arrow_back)
            setColorFilter(NC.PRIMARY)
            setPadding(dp(4), dp(4), dp(4), dp(4))
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply { rightMargin = dp(8) }
            contentDescription = "Back"
            setOnClickListener {
                // Prefer explicit Settings return (sub-page)
                if (pageStack.isNotEmpty() && pageStack.peek() == ID_CHROOT_SETTINGS) {
                    pageStack.pop()
                }
                navigateToPage(ID_SETTINGS, false)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(NC.SURFACE_CONTAINER)
                setStroke(dp(1), NC.OUTLINE_VAR)
            }
        }
        val headerIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_storage)
            setColorFilter(NC.PRIMARY)
            layoutParams = LinearLayout.LayoutParams(dp(26), dp(26)).apply { rightMargin = dp(10) }
        }
        val titleTv = TextView(this).apply {
            text = "CHROOT SETTINGS"
            textSize = 22f
            setTextColor(NC.PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        headerTitleRow.addView(pageBackBtn)
        headerTitleRow.addView(headerIcon)
        headerTitleRow.addView(titleTv)
        val subTitleTv = TextView(this).apply {
            text = "// ROOT-LEVEL DEBIAN — OUTSIDE APP STORAGE"
            textSize = 11f
            setTextColor(NC.ON_SURF_VAR)
            typeface = Typeface.MONOSPACE
            setPadding(0, dp(4), 0, dp(12))
        }
        val divider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, dp(1)).apply { bottomMargin = dp(8) }
            setBackgroundColor(NC.OUTLINE_VAR)
        }
        headerCol.addView(headerTitleRow)
        headerCol.addView(subTitleTv)
        headerCol.addView(divider)
        pageLayout.addView(headerCol)
        pageLayout.addView(buildChrootSettingsContentCard())
        pageLayout.addView(spacer(16))
        pageLayout.addView(buildChrootProcessesCard())
    }

    /**
     * Chroot detail card content — external rootfs at CHROOT_PATH.
     * Instant status from marker + cache; root/size async.
     */
    private fun buildChrootSettingsContentCard(): LinearLayout {
        val card = glassCard()
        card.addView(sectionHeader(R.drawable.ic_storage, "Storage & Manage", NC.PRIMARY))

        val subTv = TextView(this).apply {
            text = "// ROOT-LEVEL DEBIAN — OUTSIDE APP STORAGE"
            textSize = 10f
            setTextColor(NC.ON_SURF_VAR)
            typeface = Typeface.MONOSPACE
            setPadding(0, 0, 0, dp(10))
        }
        card.addView(subTv)

        // Warning strip
        val warnStrip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.parseColor("#2A1A10"))
                setStroke(dp(1), NC.TERTIARY)
            }
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(12) }
        }
        val warnIcon = TextView(this).apply {
            text = "!"
            textSize = 12f
            setTextColor(Color.parseColor("#0A0A0A"))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(NC.TERTIARY)
            }
            layoutParams = LinearLayout.LayoutParams(dp(20), dp(20)).apply { rightMargin = dp(10) }
        }
        val warnText = TextView(this).apply {
            text = "Rootfs is not removed when you uninstall the app. Free space here."
            textSize = 11f
            setTextColor(NC.TERTIARY)
            typeface = Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        warnStrip.addView(warnIcon)
        warnStrip.addView(warnText)
        card.addView(warnStrip)

        // STATUS row — updated instantly from marker/cache (before size)
        card.addView(buildChrootMetaRow("STATUS") { row ->
            chrootStatusBadge = TextView(this).apply {
                text = "…"
                textSize = 10f
                typeface = Typeface.MONOSPACE
                setPadding(dp(8), dp(3), dp(8), dp(3))
            }
            row.addView(chrootStatusBadge)
        })

        // ROOT row — only visible when root is granted (hidden if no root → card = NOT INSTALLED only)
        chrootRootRowView = buildChrootMetaRow("ROOT ACCESS") { row ->
            chrootRootBadge = TextView(this).apply {
                text = "CHECKING"
                textSize = 10f
                typeface = Typeface.MONOSPACE
                setPadding(dp(8), dp(3), dp(8), dp(3))
                setTextColor(NC.ON_SURF_VAR)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(NC.SURFACE_HIGHEST)
                    setStroke(dp(1), NC.OUTLINE_VAR)
                }
            }
            row.addView(chrootRootBadge)
        }
        card.addView(chrootRootRowView)

        // Storage metric panel + loading strip
        val sizePanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(NC.SURFACE_LOWEST)
                setStroke(dp(1), NC.OUTLINE_VAR)
            }
            setPadding(dp(12), dp(12), dp(12), dp(12))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(10) }
        }
        chrootSizePanelView = sizePanel
        val sizeHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val sizeTitle = TextView(this).apply {
            text = "LINUX STORAGE"
            textSize = 11f
            setTextColor(NC.ON_SURF_VAR)
            typeface = Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        chrootRefreshBtn = TextView(this).apply {
            text = " REFRESH"
            textSize = 10f
            setTextColor(NC.PRIMARY)
            typeface = Typeface.MONOSPACE
            setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_refresh, 0, 0, 0)
            compoundDrawableTintList = android.content.res.ColorStateList.valueOf(NC.PRIMARY)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(NC.SURFACE_CONTAINER)
                setStroke(dp(1), NC.OUTLINE_VAR)
            }
            setPadding(dp(8), dp(4), dp(10), dp(4))
            setOnClickListener { refreshChrootSettingsCard(force = true) }
        }
        sizeHeader.addView(sizeTitle)
        sizeHeader.addView(chrootRefreshBtn)
        sizePanel.addView(sizeHeader)

        // Indeterminate loading row (visible while root/du runs)
        chrootLoadingRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setPadding(0, dp(10), 0, dp(4))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        chrootProgressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            layoutParams = LinearLayout.LayoutParams(0, dp(4), 1f)
            indeterminateTintList = android.content.res.ColorStateList.valueOf(NC.PRIMARY)
        }
        val loadingLabel = TextView(this).apply {
            text = " SCANNING"
            textSize = 10f
            setTextColor(NC.PRIMARY)
            typeface = Typeface.MONOSPACE
            setPadding(dp(8), 0, 0, 0)
        }
        chrootLoadingRow?.addView(chrootProgressBar)
        chrootLoadingRow?.addView(loadingLabel)
        sizePanel.addView(chrootLoadingRow)

        val sizeValRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            setPadding(0, dp(8), 0, 0)
        }
        chrootSizeValueTv = TextView(this).apply {
            text = "—"
            textSize = 32f
            setTextColor(NC.PRIMARY)
            typeface = Typeface.MONOSPACE
            paint.isFakeBoldText = true
            layoutParams = LinearLayout.LayoutParams(WRAP, WRAP)
        }
        chrootSizeUnitTv = TextView(this).apply {
            text = ""
            textSize = 14f
            setTextColor(NC.ON_SURF_VAR)
            typeface = Typeface.MONOSPACE
            setPadding(dp(6), 0, 0, dp(6))
        }
        sizeValRow.addView(chrootSizeValueTv)
        sizeValRow.addView(chrootSizeUnitTv)
        sizePanel.addView(sizeValRow)

        chrootSizeHintTv = TextView(this).apply {
            text = "…"
            textSize = 10f
            setTextColor(NC.ON_SURF_VAR)
            typeface = Typeface.MONOSPACE
            setPadding(0, dp(4), 0, 0)
        }
        sizePanel.addView(chrootSizeHintTv)
        card.addView(sizePanel)

        // Path row (management detail — hidden when no root)
        val pathRow = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(NC.SURFACE_LOWEST)
                setStroke(dp(1), NC.OUTLINE_VAR)
            }
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(14) }
        }
        pathRow.addView(TextView(this).apply {
            text = "HOST PATH"
            textSize = 10f
            setTextColor(NC.ON_SURF_VAR)
            typeface = Typeface.MONOSPACE
            setPadding(0, 0, 0, dp(4))
        })
        pathRow.addView(TextView(this).apply {
            text = ChrootCommandBuilder.CHROOT_PATH
            textSize = 12f
            setTextColor(Color.WHITE)
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
        })
        chrootPathRowView = pathRow
        card.addView(pathRow)

        chrootUninstallBtn = TextView(this).apply {
            text = "UNINSTALL CHROOT"
            textSize = 13f
            setTextColor(NC.ERROR)
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            background = cyberBrutalistBg(
                fillColor = Color.parseColor("#1E1212"),
                strokeColor = NC.ERROR,
                shadowColor = NC.SHADOW_DARK,
                offsetDp = 6,
                cornerRadiusDp = 0,
                rightFaceColor = Color.parseColor("#3C4A3F")
            )
            setPadding(dp(16), dp(12), dp(16), dp(12))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
            setOnClickListener { confirmAndUninstallChroot() }
            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        v.translationX = dp(4).toFloat()
                        v.translationY = dp(4).toFloat()
                        v.background = cyberBrutalistBg(
                            fillColor = Color.parseColor("#1E1212"),
                            strokeColor = NC.ERROR,
                            shadowColor = NC.SHADOW_DARK,
                            offsetDp = 2,
                            cornerRadiusDp = 0,
                            rightFaceColor = Color.parseColor("#3C4A3F")
                        )
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.translationX = 0f
                        v.translationY = 0f
                        v.background = cyberBrutalistBg(
                            fillColor = Color.parseColor("#1E1212"),
                            strokeColor = NC.ERROR,
                            shadowColor = NC.SHADOW_DARK,
                            offsetDp = 6,
                            cornerRadiusDp = 0,
                            rightFaceColor = Color.parseColor("#3C4A3F")
                        )
                    }
                }
                false
            }
        }
        card.addView(chrootUninstallBtn)

        chrootInstallBtn = TextView(this).apply {
            text = "INSTALL CHROOT"
            textSize = 13f
            setTextColor(Color.parseColor("#0A0A0A"))
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            background = cyberBrutalistBg(
                fillColor = NC.PRIMARY,
                strokeColor = NC.PRIMARY,
                shadowColor = NC.SHADOW_DARK,
                offsetDp = 6,
                cornerRadiusDp = 0,
                rightFaceColor = Color.parseColor("#3C4A3F")
            )
            setPadding(dp(16), dp(12), dp(16), dp(12))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(0) }
            visibility = View.GONE
            setOnClickListener { launchChrootInstallOnboarding() }
            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        v.translationX = dp(4).toFloat()
                        v.translationY = dp(4).toFloat()
                        v.background = cyberBrutalistBg(
                            fillColor = NC.PRIMARY,
                            strokeColor = NC.PRIMARY,
                            shadowColor = NC.SHADOW_DARK,
                            offsetDp = 2,
                            cornerRadiusDp = 0,
                            rightFaceColor = Color.parseColor("#3C4A3F")
                        )
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.translationX = 0f
                        v.translationY = 0f
                        v.background = cyberBrutalistBg(
                            fillColor = NC.PRIMARY,
                            strokeColor = NC.PRIMARY,
                            shadowColor = NC.SHADOW_DARK,
                            offsetDp = 6,
                            cornerRadiusDp = 0,
                            rightFaceColor = Color.parseColor("#3C4A3F")
                        )
                    }
                }
                false
            }
        }
        card.addView(chrootInstallBtn)

        // Instant status + cache only; live probe runs on page enter (sequential size→proc)
        applyInstantChrootStatus()
        applyCachedChrootInfo()
        return card
    }

    private fun buildChrootMetaRow(label: String, addValue: (LinearLayout) -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(NC.SURFACE_LOWEST)
                setStroke(dp(1), NC.OUTLINE_VAR)
            }
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(10) }
            addView(TextView(this@MainActivity).apply {
                text = label
                textSize = 12f
                setTextColor(Color.WHITE)
                typeface = Typeface.MONOSPACE
                layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
            })
            addValue(this)
        }
    }

    /** Instant (main-thread): marker + dir File.exists — no su, no du. */
    private fun applyInstantChrootStatus() {
        val markerOk = ProjectPathResolver.isChrootInstalled()
        val dirExists = ProjectPathResolver.isChrootRootfsPresent()
        val installed = markerOk || dirExists
        applyChrootStatusBadge(chrootStatusBadge, installed, markerOk, dirExists)
        applyChrootInstallUninstallVisibility(installed)
    }

    /** Paint last known size/root from SharedPreferences (nativecode_prefs). */
    private fun applyCachedChrootInfo() {
        val prefs = getSharedPreferences("nativecode_prefs", MODE_PRIVATE)
        if (!prefs.contains(PREF_CHROOT_LAST_MS)) return

        val rootOk = prefs.getBoolean(PREF_CHROOT_ROOT_OK, false)
        // Cached no-root → NOT INSTALLED only (never show stale DENIED / partial GB)
        if (!rootOk) {
            applyNoRootChrootCardUi()
            return
        }

        val bytes = prefs.getLong(PREF_CHROOT_BYTES, -1L).takeIf { it >= 0L }
        val viaRoot = prefs.getBoolean(PREF_CHROOT_SIZE_VIA_ROOT, false)
        val lastMs = prefs.getLong(PREF_CHROOT_LAST_MS, 0L)
        val markerOk = prefs.getBoolean(PREF_CHROOT_INSTALLED, false)
        val dirOk = prefs.getBoolean(PREF_CHROOT_DIR, false)

        chrootRootRowView?.visibility = View.VISIBLE
        chrootSizePanelView?.visibility = View.VISIBLE
        chrootPathRowView?.visibility = View.VISIBLE

        val installed = markerOk || dirOk
        applyChrootStatusBadge(chrootStatusBadge, installed, markerOk, dirOk)
        applyChrootRootBadge(chrootRootBadge, rootOk = true, checking = false)
        applyChrootInstallUninstallVisibility(installed)

        if (bytes != null) {
            val (v, u) = formatStorageBytes(bytes)
            chrootSizeValueTv?.text = v
            chrootSizeUnitTv?.text = u
            chrootSizeValueTv?.alpha = 0.85f
        }
        chrootSizeHintTv?.text = formatChrootCacheHint(lastMs, viaRoot, rootOk = true)
        chrootSizeHintTv?.setTextColor(NC.ON_SURF_VAR)
    }

    private fun formatCacheAge(lastMs: Long): String {
        if (lastMs <= 0L) return "unknown"
        val mins = ((System.currentTimeMillis() - lastMs) / 60000L).coerceAtLeast(0L)
        return when {
            mins < 1 -> "just now"
            mins < 60 -> "${mins}m ago"
            mins < 1440 -> "${mins / 60}h ago"
            else -> "${mins / 1440}d ago"
        }
    }

    private fun formatChrootCacheHint(lastMs: Long, viaRoot: Boolean, rootOk: Boolean): String {
        val age = formatCacheAge(lastMs)
        val how = when {
            viaRoot -> "root du"
            rootOk -> "root"
            else -> "no-root walk"
        }
        return "Cached · $how · $age"
    }

    /**
     * Persist chroot card state. **Does not poison size cache on probe fail:**
     * when [bytes] is null and dir still present with a prior good size, leave
     * `chroot_size_bytes` + last-measure time alone so UI can show dimmed cache age.
     */
    private fun saveChrootInfo(
        installed: Boolean,
        dirExists: Boolean,
        bytes: Long?,
        rootOk: Boolean,
        viaRoot: Boolean
    ) {
        val prefs = getSharedPreferences("nativecode_prefs", MODE_PRIVATE)
        val edit = prefs.edit()
            .putBoolean(PREF_CHROOT_INSTALLED, installed)
            .putBoolean(PREF_CHROOT_DIR, dirExists)
            .putBoolean(PREF_CHROOT_ROOT_OK, rootOk)

        when {
            bytes != null && bytes >= 0L -> {
                edit.putLong(PREF_CHROOT_BYTES, bytes)
                edit.putBoolean(PREF_CHROOT_SIZE_VIA_ROOT, viaRoot)
                edit.putLong(PREF_CHROOT_LAST_MS, System.currentTimeMillis())
            }
            !rootOk || !dirExists -> {
                // Gone / no root — clear size
                edit.putLong(PREF_CHROOT_BYTES, -1L)
                edit.putBoolean(PREF_CHROOT_SIZE_VIA_ROOT, false)
                edit.putLong(PREF_CHROOT_LAST_MS, System.currentTimeMillis())
            }
            // else: probe failed but dir still there — keep previous good bytes + LAST_MS
            else -> {
                if (!prefs.contains(PREF_CHROOT_LAST_MS)) {
                    edit.putLong(PREF_CHROOT_LAST_MS, System.currentTimeMillis())
                }
            }
        }
        edit.apply()
    }

    /** Last good size from prefs, or null. */
    private fun cachedChrootBytes(): Long? {
        val prefs = getSharedPreferences("nativecode_prefs", MODE_PRIVATE)
        return prefs.getLong(PREF_CHROOT_BYTES, -1L).takeIf { it >= 0L }
    }

    private fun cachedChrootLastMs(): Long {
        return getSharedPreferences("nativecode_prefs", MODE_PRIVATE)
            .getLong(PREF_CHROOT_LAST_MS, 0L)
    }

    private fun clearChrootInfoCache() {
        getSharedPreferences("nativecode_prefs", MODE_PRIVATE).edit()
            .remove(PREF_CHROOT_INSTALLED)
            .remove(PREF_CHROOT_DIR)
            .remove(PREF_CHROOT_BYTES)
            .remove(PREF_CHROOT_ROOT_OK)
            .remove(PREF_CHROOT_SIZE_VIA_ROOT)
            .remove(PREF_CHROOT_LAST_MS)
            .remove(PREF_CHROOT_PROC_COUNT)
            .remove(PREF_CHROOT_PROC_LAST_MS)
            .apply()
        chrootProcLastCount = -1
    }

    private fun setChrootLoading(loading: Boolean) {
        chrootMeasuring = loading
        chrootLoadingRow?.visibility = if (loading) View.VISIBLE else View.GONE
        chrootRefreshBtn?.isEnabled = !loading
        chrootRefreshBtn?.alpha = if (loading) 0.45f else 1f
        if (loading) {
            chrootRootBadge?.let {
                it.text = "CHECKING"
                it.setTextColor(NC.ON_SURF_VAR)
                it.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(NC.SURFACE_HIGHEST)
                    setStroke(dp(1), NC.OUTLINE_VAR)
                }
            }
            chrootSizeHintTv?.text = "Checking root · measuring rootfs…"
            chrootSizeHintTv?.setTextColor(NC.PRIMARY)
            chrootSizeValueTv?.alpha = 0.45f
        } else {
            chrootSizeValueTv?.alpha = 1f
        }
    }

    /**
     * Page-level sequential refresh: size probe **then** process list on one BG
     * thread so two RootShell.capture never race on enter (F1).
     */
    private fun refreshChrootSettingsPage(force: Boolean = false) {
        if (chrootSizeValueTv == null) return
        if (chrootMeasuring || chrootProcMeasuring || chrootProcKilling) return

        setChrootLoading(true)
        chrootRootRowView?.visibility = View.VISIBLE
        chrootSizePanelView?.visibility = View.VISIBLE
        chrootPathRowView?.visibility = View.VISIBLE
        // Block process SCAN/KILL while size runs
        setChrootProcKillEnabled(false)
        chrootProcScanBtn?.isEnabled = false
        chrootProcScanBtn?.alpha = 0.45f

        val appCtx = applicationContext
        executor.execute {
            if (force) RootShell.clearSuCache()
            runChrootSizeProbeOnBg(appCtx)
            // Sequential: processes after size (same executor job)
            if (chrootProcCountTv != null) {
                mainHandler.post { setChrootProcLoading(loading = true) }
                val procResult = ChrootProcessManager.list(appCtx)
                mainHandler.post {
                    setChrootProcLoading(loading = false)
                    applyChrootProcessListUi(procResult)
                    chrootProcScanBtn?.isEnabled = true
                    chrootProcScanBtn?.alpha = 1f
                }
            } else {
                mainHandler.post {
                    chrootProcScanBtn?.isEnabled = true
                    chrootProcScanBtn?.alpha = 1f
                }
            }
        }
    }

    /**
     * @param force true = user tapped REFRESH (clear su cache + re-probe).
     * Size only — does not start process list (use [refreshChrootSettingsPage] on enter).
     */
    private fun refreshChrootSettingsCard(force: Boolean = true) {
        if (chrootSizeValueTv == null) return
        if (chrootMeasuring || chrootProcMeasuring || chrootProcKilling) return

        setChrootLoading(true)
        chrootRootRowView?.visibility = View.VISIBLE
        chrootSizePanelView?.visibility = View.VISIBLE
        chrootPathRowView?.visibility = View.VISIBLE
        setChrootProcKillEnabled(false)
        chrootProcScanBtn?.isEnabled = false
        chrootProcScanBtn?.alpha = 0.45f

        val appCtx = applicationContext
        executor.execute {
            if (force) RootShell.clearSuCache()
            runChrootSizeProbeOnBg(appCtx)
            mainHandler.post {
                chrootProcScanBtn?.isEnabled = true
                chrootProcScanBtn?.alpha = 1f
                // Re-enable kill if processes were known running
                if (chrootProcLastCount > 0 && !chrootProcMeasuring && !chrootProcKilling) {
                    setChrootProcKillEnabled(true)
                }
            }
        }
    }

    /**
     * BG: root probe + staged size helper. Posts UI. Must run on executor only.
     * Keeps good size cache on fail (F2); paints dimmed cache + fail hint.
     */
    private fun runChrootSizeProbeOnBg(appCtx: Context) {
        val rootOk = RootShell.isRootAvailable()

        if (!rootOk) {
            saveChrootInfo(
                installed = false,
                dirExists = false,
                bytes = null,
                rootOk = false,
                viaRoot = false
            )
            mainHandler.post {
                setChrootLoading(false)
                applyNoRootChrootCardUi()
            }
            return
        }

        mainHandler.post {
            chrootRootRowView?.visibility = View.VISIBLE
            chrootSizePanelView?.visibility = View.VISIBLE
            chrootPathRowView?.visibility = View.VISIBLE
            applyChrootRootBadge(chrootRootBadge, rootOk = true, checking = false)
        }

        val markerOk = ProjectPathResolver.isChrootInstalled()
        val measure = ChrootSizeManager.measure(appCtx)
        var dirExists = measure.dirExists
        if (measure.error == "no_dir") {
            dirExists = false
        } else if (measure.bytes != null || measure.error == null) {
            dirExists = true
        } else {
            // Soft-fail: keep local marker/dir hints
            dirExists = ProjectPathResolver.isChrootRootfsPresent() || markerOk || dirExists
        }

        val liveBytes = measure.bytes
        val viaRoot = measure.viaRoot
        val priorBytes = cachedChrootBytes()
        val priorMs = cachedChrootLastMs()

        val measureNote: String
        val displayBytes: Long?
        val dimmedCache: Boolean

        when {
            liveBytes != null -> {
                displayBytes = liveBytes
                dimmedCache = false
                measureNote = "Debian rootfs · binds excluded (sdcard/mnt/dev)"
            }
            !dirExists -> {
                displayBytes = null
                dimmedCache = false
                measureNote = "No chroot rootfs on host"
            }
            priorBytes != null -> {
                // F2 fix: keep last good size on probe fail
                displayBytes = priorBytes
                dimmedCache = true
                val age = formatCacheAge(priorMs)
                val why = when (measure.error) {
                    "timeout" -> "timeout"
                    "empty_output" -> "empty"
                    else -> "probe failed"
                }
                measureNote = "Size $why · showing cache · $age"
                Log.w("ChrootSize", "probe fail keep cache err=${measure.error} raw=${measure.raw.take(400)}")
            }
            else -> {
                displayBytes = null
                dimmedCache = false
                measureNote = "Root OK · size probe failed"
                Log.w("ChrootSize", "probe fail no cache err=${measure.error} raw=${measure.raw.take(400)}")
            }
        }

        val installed = markerOk || dirExists
        saveChrootInfo(installed, dirExists, liveBytes, rootOk = true, viaRoot)
        val (valStr, unitStr) = formatStorageBytes(displayBytes)

        mainHandler.post {
            setChrootLoading(false)
            applyChrootStatusBadge(chrootStatusBadge, installed, markerOk, dirExists)
            applyChrootRootBadge(chrootRootBadge, rootOk = true, checking = false)
            chrootSizeValueTv?.text = valStr
            chrootSizeUnitTv?.text = unitStr
            chrootSizeValueTv?.alpha = if (dimmedCache) 0.75f else 1f
            chrootSizeHintTv?.text = measureNote
            chrootSizeHintTv?.setTextColor(
                when {
                    dimmedCache -> NC.TERTIARY
                    installed -> NC.ON_SURF_VAR
                    else -> NC.SECONDARY
                }
            )
            applyChrootInstallUninstallVisibility(installed)
        }
    }

    /**
     * No root access for this app → card only reports NOT INSTALLED.
     * Hide ROOT / path / uninstall management chrome (no false DENIED, no partial size).
     */
    private fun applyNoRootChrootCardUi() {
        applyChrootStatusBadge(
            chrootStatusBadge,
            installed = false,
            markerOk = false,
            dirExists = false
        )
        chrootRootRowView?.visibility = View.GONE
        chrootPathRowView?.visibility = View.GONE
        // Not detected → install CTA only (onboarding grants root path)
        applyChrootInstallUninstallVisibility(installed = false)
        // Keep size panel as empty state (not partial walk)
        chrootSizePanelView?.visibility = View.VISIBLE
        chrootSizeValueTv?.text = "—"
        chrootSizeUnitTv?.text = ""
        chrootSizeValueTv?.alpha = 1f
        chrootSizeHintTv?.text = "Root required · install via onboarding after granting su"
        chrootSizeHintTv?.setTextColor(NC.SECONDARY)
    }

    private fun applyChrootStatusBadge(
        badge: TextView?,
        installed: Boolean,
        markerOk: Boolean,
        dirExists: Boolean
    ) {
        if (badge == null) return
        when {
            markerOk -> {
                badge.text = "INSTALLED"
                badge.setTextColor(Color.parseColor("#0A0A0A"))
                badge.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(NC.PRIMARY)
                }
            }
            dirExists || installed -> {
                badge.text = "PARTIAL"
                badge.setTextColor(Color.parseColor("#0A0A0A"))
                badge.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(NC.TERTIARY)
                }
            }
            else -> {
                badge.text = "NOT INSTALLED"
                badge.setTextColor(NC.ON_SURF_VAR)
                badge.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(NC.SURFACE_HIGHEST)
                    setStroke(dp(1), NC.OUTLINE_VAR)
                }
            }
        }
    }

    private fun applyChrootRootBadge(badge: TextView?, rootOk: Boolean, checking: Boolean) {
        if (badge == null) return
        when {
            checking -> {
                badge.text = "CHECKING"
                badge.setTextColor(NC.ON_SURF_VAR)
                badge.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(NC.SURFACE_HIGHEST)
                    setStroke(dp(1), NC.OUTLINE_VAR)
                }
            }
            rootOk -> {
                badge.text = "GRANTED"
                badge.setTextColor(Color.parseColor("#0A0A0A"))
                badge.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(NC.PRIMARY)
                }
            }
            else -> {
                badge.text = "DENIED"
                badge.setTextColor(Color.parseColor("#0A0A0A"))
                badge.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(NC.ERROR)
                }
            }
        }
    }

    private fun formatStorageBytes(bytes: Long?): Pair<String, String> {
        if (bytes == null) return "—" to ""
        if (bytes < 0L) return "—" to ""
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024.0) {
            String.format(Locale.US, "%.1f", mb / 1024.0) to "GB"
        } else if (mb >= 1.0) {
            String.format(Locale.US, "%.1f", mb) to "MB"
        } else {
            val kb = bytes / 1024.0
            if (kb >= 1.0) {
                String.format(Locale.US, "%.0f", kb) to "KB"
            } else {
                bytes.toString() to "B"
            }
        }
    }

    /**
     * Install CTA only when rootfs/marker not detected.
     * Uninstall only when installed (or partial dir present).
     */
    private fun applyChrootInstallUninstallVisibility(installed: Boolean) {
        if (installed) {
            chrootInstallBtn?.visibility = View.GONE
            chrootUninstallBtn?.visibility = View.VISIBLE
            chrootUninstallBtn?.isEnabled = true
            chrootUninstallBtn?.alpha = 1f
        } else {
            chrootUninstallBtn?.visibility = View.GONE
            chrootUninstallBtn?.isEnabled = false
            chrootUninstallBtn?.alpha = 0.4f
            chrootInstallBtn?.visibility = View.VISIBLE
        }
    }

    /** Full chroot install chain via Onboarding (isolation=chroot → Environment Setup). */
    private fun launchChrootInstallOnboarding() {
        val intent = Intent(this, OnboardingActivity::class.java).apply {
            putExtra("force_onboarding", true)
            putExtra("preferred_isolation", "chroot")
            putExtra("target_page", 4) // Environment Setup (full install log)
            putExtra("auto_start_setup", true)
        }
        startActivity(intent)
    }

    private fun confirmAndUninstallChroot() {
        if (chrootUninstallBtn?.isEnabled != true) {
            Toast.makeText(this, "No chroot rootfs to uninstall", Toast.LENGTH_SHORT).show()
            return
        }
        showBrutalistConfirmDialog(
            title = "UNINSTALL CHROOT?",
            message =
                "Permanently deletes ${ChrootCommandBuilder.CHROOT_PATH}, unmounts binds, " +
                    "and removes host launcher scripts.\n\n" +
                    "The app package stays installed. PRoot data is not touched.\n\n" +
                    "This cannot be undone.",
            confirmLabel = "UNINSTALL",
            cancelLabel = "CANCEL",
            destructive = true,
            onConfirm = {
                pendingChrootUninstall = true
                runScriptInTerminal("uninstall_debian13_chroot.sh", "chroot_host")
            }
        )
    }

    // ── Chroot Processes card (detect → kill → verify) ───────────────────────

    private fun buildChrootProcessesCard(): LinearLayout {
        val card = glassCard()
        card.addView(sectionHeader(R.drawable.ic_stop_thick, "Processes", NC.ERROR))

        val subTv = TextView(this).apply {
            text = "// orphans survive app close (unlike proot). Kill before uninstall if stuck."
            textSize = 10f
            setTextColor(NC.ON_SURF_VAR)
            typeface = Typeface.MONOSPACE
            setPadding(0, 0, 0, dp(10))
        }
        card.addView(subTv)

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(NC.SURFACE_LOWEST)
                setStroke(dp(1), NC.OUTLINE_VAR)
            }
            setPadding(dp(12), dp(12), dp(12), dp(12))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(12) }
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = "CHROOT PROCESSES"
            textSize = 11f
            setTextColor(NC.ON_SURF_VAR)
            typeface = Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        chrootProcScanBtn = TextView(this).apply {
            text = " SCAN"
            textSize = 10f
            setTextColor(NC.PRIMARY)
            typeface = Typeface.MONOSPACE
            setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_refresh, 0, 0, 0)
            compoundDrawableTintList = android.content.res.ColorStateList.valueOf(NC.PRIMARY)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(NC.SURFACE_CONTAINER)
                setStroke(dp(1), NC.OUTLINE_VAR)
            }
            setPadding(dp(8), dp(4), dp(10), dp(4))
            setOnClickListener { refreshChrootProcessesCard(force = true) }
        }
        header.addView(title)
        header.addView(chrootProcScanBtn)
        panel.addView(header)

        chrootProcLoadingRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setPadding(0, dp(10), 0, dp(4))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            layoutParams = LinearLayout.LayoutParams(0, dp(4), 1f)
            indeterminateTintList = android.content.res.ColorStateList.valueOf(NC.ERROR)
        }
        val loadingLabel = TextView(this).apply {
            text = " SCANNING"
            textSize = 10f
            setTextColor(NC.ERROR)
            typeface = Typeface.MONOSPACE
            setPadding(dp(8), 0, 0, 0)
        }
        chrootProcLoadingRow?.addView(progress)
        chrootProcLoadingRow?.addView(loadingLabel)
        panel.addView(chrootProcLoadingRow)

        val countRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            setPadding(0, dp(8), 0, 0)
        }
        chrootProcCountTv = TextView(this).apply {
            text = "—"
            textSize = 32f
            setTextColor(NC.ERROR)
            typeface = Typeface.MONOSPACE
            paint.isFakeBoldText = true
            layoutParams = LinearLayout.LayoutParams(WRAP, WRAP)
        }
        chrootProcUnitTv = TextView(this).apply {
            text = "running"
            textSize = 14f
            setTextColor(NC.ON_SURF_VAR)
            typeface = Typeface.MONOSPACE
            setPadding(dp(8), 0, 0, dp(6))
        }
        countRow.addView(chrootProcCountTv)
        countRow.addView(chrootProcUnitTv)
        panel.addView(countRow)

        panel.addView(TextView(this).apply {
            text = "root=${ChrootCommandBuilder.CHROOT_PATH}"
            textSize = 10f
            setTextColor(NC.ON_SURF_VAR)
            typeface = Typeface.MONOSPACE
            setPadding(0, dp(4), 0, dp(6))
            setTextIsSelectable(true)
        })

        chrootProcHintTv = TextView(this).apply {
            text = "…"
            textSize = 10f
            setTextColor(NC.ON_SURF_VAR)
            typeface = Typeface.MONOSPACE
            setPadding(0, 0, 0, dp(6))
        }
        panel.addView(chrootProcHintTv)

        chrootProcSampleBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, dp(4), 0, dp(4))
        }
        panel.addView(chrootProcSampleBox)
        card.addView(panel)

        chrootProcKillBtn = TextView(this).apply {
            text = "KILL ALL CHROOT PROCESSES"
            textSize = 13f
            setTextColor(NC.ERROR)
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            background = cyberBrutalistBg(
                fillColor = Color.parseColor("#1E1212"),
                strokeColor = NC.ERROR,
                shadowColor = NC.SHADOW_DARK,
                offsetDp = 6,
                cornerRadiusDp = 0,
                rightFaceColor = Color.parseColor("#3C4A3F")
            )
            setPadding(dp(16), dp(12), dp(16), dp(12))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
            isEnabled = false
            alpha = 0.4f
            setOnClickListener { confirmAndKillChrootProcesses() }
            setOnTouchListener { v, event ->
                if (!v.isEnabled) return@setOnTouchListener false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        v.translationX = dp(4).toFloat()
                        v.translationY = dp(4).toFloat()
                        v.background = cyberBrutalistBg(
                            fillColor = Color.parseColor("#1E1212"),
                            strokeColor = NC.ERROR,
                            shadowColor = NC.SHADOW_DARK,
                            offsetDp = 2,
                            cornerRadiusDp = 0,
                            rightFaceColor = Color.parseColor("#3C4A3F")
                        )
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.translationX = 0f
                        v.translationY = 0f
                        v.background = cyberBrutalistBg(
                            fillColor = Color.parseColor("#1E1212"),
                            strokeColor = NC.ERROR,
                            shadowColor = NC.SHADOW_DARK,
                            offsetDp = 6,
                            cornerRadiusDp = 0,
                            rightFaceColor = Color.parseColor("#3C4A3F")
                        )
                    }
                }
                false
            }
        }
        card.addView(chrootProcKillBtn)

        chrootProcStatusTv = TextView(this).apply {
            text = ""
            textSize = 10f
            setTextColor(NC.ON_SURF_VAR)
            typeface = Typeface.MONOSPACE
            setPadding(0, dp(10), 0, 0)
            visibility = View.GONE
        }
        card.addView(chrootProcStatusTv)

        applyCachedChrootProcInfo()
        return card
    }

    private fun applyCachedChrootProcInfo() {
        if (chrootProcCountTv == null) return
        val prefs = getSharedPreferences("nativecode_prefs", MODE_PRIVATE)
        if (!prefs.contains(PREF_CHROOT_PROC_LAST_MS)) return
        val count = prefs.getInt(PREF_CHROOT_PROC_COUNT, -1)
        val lastMs = prefs.getLong(PREF_CHROOT_PROC_LAST_MS, 0L)
        if (count < 0) return
        chrootProcLastCount = count
        chrootProcCountTv?.text = count.toString()
        chrootProcCountTv?.alpha = 0.75f
        chrootProcUnitTv?.text = if (count == 1) "running" else "running"
        val age = if (lastMs > 0L) {
            val mins = ((System.currentTimeMillis() - lastMs) / 60000L).coerceAtLeast(0L)
            when {
                mins < 1 -> "just now"
                mins < 60 -> "${mins}m ago"
                else -> "${mins / 60}h ago"
            }
        } else "unknown"
        chrootProcHintTv?.text = "Cached · $age"
        chrootProcHintTv?.setTextColor(NC.ON_SURF_VAR)
        setChrootProcKillEnabled(count > 0 && !chrootProcMeasuring && !chrootProcKilling)
    }

    private fun saveChrootProcInfo(count: Int) {
        getSharedPreferences("nativecode_prefs", MODE_PRIVATE).edit()
            .putInt(PREF_CHROOT_PROC_COUNT, count)
            .putLong(PREF_CHROOT_PROC_LAST_MS, System.currentTimeMillis())
            .apply()
        chrootProcLastCount = count
    }

    private fun setChrootProcLoading(loading: Boolean, killing: Boolean = false) {
        chrootProcMeasuring = loading && !killing
        chrootProcKilling = killing
        chrootProcLoadingRow?.visibility = if (loading || killing) View.VISIBLE else View.GONE
        // Update loading label text
        (chrootProcLoadingRow?.getChildAt(1) as? TextView)?.text =
            if (killing) " KILLING" else " SCANNING"
        chrootProcScanBtn?.isEnabled = !loading && !killing
        chrootProcScanBtn?.alpha = if (loading || killing) 0.45f else 1f
        if (loading || killing) {
            chrootProcCountTv?.alpha = 0.45f
            chrootProcHintTv?.text = if (killing) "Sending SIGKILL…" else "Scanning /proc for chroot root…"
            chrootProcHintTv?.setTextColor(if (killing) NC.ERROR else NC.PRIMARY)
            setChrootProcKillEnabled(false)
        } else {
            chrootProcCountTv?.alpha = 1f
        }
    }

    private fun setChrootProcKillEnabled(enabled: Boolean) {
        chrootProcKillBtn?.isEnabled = enabled
        chrootProcKillBtn?.alpha = if (enabled) 1f else 0.4f
    }

    private fun refreshChrootProcessesCard(force: Boolean = true) {
        if (chrootProcCountTv == null) return
        // P3: do not race size measure / concurrent su
        if (chrootMeasuring || chrootProcMeasuring || chrootProcKilling) return

        setChrootProcLoading(loading = true)
        val appCtx = applicationContext
        executor.execute {
            if (force) RootShell.clearSuCache()
            val result = ChrootProcessManager.list(appCtx)
            mainHandler.post {
                setChrootProcLoading(loading = false)
                applyChrootProcessListUi(result)
            }
        }
    }

    private fun applyChrootProcessListUi(result: ChrootProcessManager.ListResult) {
        if (!result.rootOk || result.error == "root_required") {
            chrootProcCountTv?.text = "—"
            chrootProcUnitTv?.text = ""
            chrootProcHintTv?.text = "Root required"
            chrootProcHintTv?.setTextColor(NC.SECONDARY)
            chrootProcSampleBox?.removeAllViews()
            chrootProcSampleBox?.visibility = View.GONE
            setChrootProcKillEnabled(false)
            chrootProcStatusTv?.visibility = View.GONE
            return
        }
        if (result.error == "stage_failed") {
            chrootProcCountTv?.text = "—"
            chrootProcHintTv?.text = "Failed to stage helper script"
            chrootProcHintTv?.setTextColor(NC.ERROR)
            setChrootProcKillEnabled(false)
            return
        }

        val n = result.processes.size
        saveChrootProcInfo(n)
        chrootProcCountTv?.text = n.toString()
        chrootProcCountTv?.alpha = 1f
        chrootProcCountTv?.setTextColor(if (n > 0) NC.ERROR else NC.PRIMARY)
        chrootProcUnitTv?.text = "running"

        chrootProcHintTv?.text = when {
            n == 0 -> "No chroot processes"
            n == 1 -> "1 process uses chroot root"
            else -> "$n processes use chroot root"
        }
        chrootProcHintTv?.setTextColor(if (n > 0) NC.TERTIARY else NC.ON_SURF_VAR)

        fillChrootProcSample(result.processes)
        setChrootProcKillEnabled(n > 0)
    }

    private fun fillChrootProcSample(procs: List<ChrootProcessManager.Proc>) {
        val box = chrootProcSampleBox ?: return
        box.removeAllViews()
        if (procs.isEmpty()) {
            box.visibility = View.GONE
            return
        }
        box.visibility = View.VISIBLE
        box.addView(TextView(this).apply {
            text = "SAMPLE"
            textSize = 10f
            setTextColor(NC.ON_SURF_VAR)
            typeface = Typeface.MONOSPACE
            setPadding(0, 0, 0, dp(4))
        })
        val show = procs.take(5)
        for (p in show) {
            box.addView(TextView(this).apply {
                text = "${p.pid}  ${p.comm}"
                textSize = 11f
                setTextColor(Color.WHITE)
                typeface = Typeface.MONOSPACE
                setPadding(0, dp(2), 0, dp(2))
            })
        }
        val more = procs.size - show.size
        if (more > 0) {
            box.addView(TextView(this).apply {
                text = "+$more more"
                textSize = 10f
                setTextColor(NC.ON_SURF_VAR)
                typeface = Typeface.MONOSPACE
                setPadding(0, dp(2), 0, 0)
            })
        }
    }

    private fun applyChrootProcessKillUi(result: ChrootProcessManager.KillResult) {
        if (!result.rootOk || result.error == "root_required") {
            chrootProcHintTv?.text = "Root required"
            chrootProcHintTv?.setTextColor(NC.SECONDARY)
            setChrootProcKillEnabled(false)
            Toast.makeText(this, "Root required to kill chroot processes", Toast.LENGTH_SHORT).show()
            return
        }
        if (result.error == "stage_failed") {
            chrootProcHintTv?.text = "Failed to stage helper script"
            chrootProcHintTv?.setTextColor(NC.ERROR)
            Toast.makeText(this, "Could not stage kill helper", Toast.LENGTH_SHORT).show()
            return
        }

        val rem = result.remaining.size
        saveChrootProcInfo(rem)
        chrootProcCountTv?.text = rem.toString()
        chrootProcCountTv?.alpha = 1f
        chrootProcCountTv?.setTextColor(if (rem > 0) NC.ERROR else NC.PRIMARY)
        chrootProcUnitTv?.text = "running"
        fillChrootProcSample(result.remaining)
        setChrootProcKillEnabled(rem > 0)

        val status = if (result.verifiedClean) {
            "last: killed ${result.killed} · verified 0 remaining"
        } else {
            "last: killed ${result.killed} · ${rem} still alive — retry"
        }
        chrootProcStatusTv?.text = status
        chrootProcStatusTv?.visibility = View.VISIBLE
        chrootProcStatusTv?.setTextColor(if (result.verifiedClean) NC.PRIMARY else NC.ERROR)

        chrootProcHintTv?.text = if (result.verifiedClean) {
            "No chroot processes"
        } else {
            "$rem process(es) still use chroot root"
        }
        chrootProcHintTv?.setTextColor(if (result.verifiedClean) NC.ON_SURF_VAR else NC.TERTIARY)

        Toast.makeText(
            this,
            if (result.verifiedClean) "Killed ${result.killed} · clean" else "Killed ${result.killed} · $rem remaining",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun confirmAndKillChrootProcesses() {
        if (chrootProcKillBtn?.isEnabled != true) return
        if (chrootMeasuring) return // P3: no concurrent su with size probe
        val n = chrootProcLastCount.coerceAtLeast(0)
        showBrutalistConfirmDialog(
            title = "KILL CHROOT PROCESSES?",
            message =
                "Sends SIGKILL to every process whose root is ${ChrootCommandBuilder.CHROOT_PATH}.\n\n" +
                    "Open chroot shells and guest daemons will die. Host Android processes are not targeted.\n\n" +
                    "Rootfs and mounts stay (use Uninstall to remove)." +
                    if (n > 0) "\n\nDetected: $n process(es)." else "",
            confirmLabel = "KILL ALL",
            cancelLabel = "CANCEL",
            destructive = true,
            onConfirm = {
                if (!chrootMeasuring) {
                    setChrootProcLoading(loading = true, killing = true)
                    val appCtx = applicationContext
                    executor.execute {
                        val result = ChrootProcessManager.killAll(appCtx)
                        mainHandler.post {
                            setChrootProcLoading(loading = false, killing = false)
                            applyChrootProcessKillUi(result)
                        }
                    }
                }
            }
        )
    }

    /**
     * Cyber-brutalist confirm sheet (ui_design.md): sharp 0px, NC surfaces,
     * two-tone extrusion, mono labels — never stock Material AlertDialog.
     */
    private fun showBrutalistConfirmDialog(
        title: String,
        message: String,
        confirmLabel: String,
        cancelLabel: String = "CANCEL",
        destructive: Boolean = false,
        onConfirm: () -> Unit
    ) {
        val dialog = android.app.Dialog(this, android.R.style.Theme_Translucent_NoTitleBar)
        dialog.setCancelable(true)

        val scrim = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#CC0A0A0A"))
            layoutParams = ViewGroup.LayoutParams(MATCH, MATCH)
            setOnClickListener { dialog.dismiss() }
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cyberBrutalistBg(
                fillColor = NC.SURFACE_LOW,
                strokeColor = if (destructive) NC.ERROR else NC.OUTLINE_VAR,
                shadowColor = NC.SHADOW_DARK,
                offsetDp = 6,
                cornerRadiusDp = 0,
                rightFaceColor = Color.parseColor("#3C4A3F")
            )
            setPadding(dp(20), dp(20), dp(20), dp(20))
            layoutParams = FrameLayout.LayoutParams(MATCH, WRAP).apply {
                gravity = Gravity.CENTER
                leftMargin = dp(24)
                rightMargin = dp(24)
            }
            isClickable = true // absorb scrim clicks
        }

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(12))
        }
        if (destructive) {
            titleRow.addView(TextView(this).apply {
                text = "!"
                textSize = 14f
                setTextColor(Color.parseColor("#0A0A0A"))
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(NC.ERROR)
                }
                layoutParams = LinearLayout.LayoutParams(dp(22), dp(22)).apply { rightMargin = dp(10) }
            })
        }
        titleRow.addView(TextView(this).apply {
            text = title
            textSize = 16f
            setTextColor(if (destructive) NC.ERROR else Color.WHITE)
            typeface = Typeface.MONOSPACE
            paint.isFakeBoldText = true
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        })
        card.addView(titleRow)

        card.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, dp(1)).apply { bottomMargin = dp(14) }
            setBackgroundColor(NC.OUTLINE_VAR)
        })

        card.addView(TextView(this).apply {
            text = message
            textSize = 13f
            setTextColor(NC.ON_SURF_VAR)
            typeface = Typeface.MONOSPACE
            setLineSpacing(dp(2).toFloat(), 1.15f)
            setPadding(0, 0, 0, dp(20))
        })

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        fun pressable(
            label: String,
            fill: Int,
            stroke: Int,
            textColor: Int,
            weight: Float,
            endMargin: Int,
            action: () -> Unit
        ): TextView {
            return TextView(this).apply {
                text = label
                textSize = 12f
                setTextColor(textColor)
                typeface = Typeface.MONOSPACE
                gravity = Gravity.CENTER
                background = cyberBrutalistBg(
                    fillColor = fill,
                    strokeColor = stroke,
                    shadowColor = NC.SHADOW_DARK,
                    offsetDp = 4,
                    cornerRadiusDp = 0,
                    rightFaceColor = Color.parseColor("#3C4A3F")
                )
                setPadding(dp(12), dp(12), dp(12), dp(12))
                layoutParams = LinearLayout.LayoutParams(0, WRAP, weight).apply {
                    if (endMargin > 0) rightMargin = endMargin
                }
                setOnClickListener { action() }
                setOnTouchListener { v, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            v.translationX = dp(2).toFloat()
                            v.translationY = dp(2).toFloat()
                            v.background = cyberBrutalistBg(
                                fillColor = fill,
                                strokeColor = stroke,
                                shadowColor = NC.SHADOW_DARK,
                                offsetDp = 2,
                                cornerRadiusDp = 0,
                                rightFaceColor = Color.parseColor("#3C4A3F")
                            )
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            v.translationX = 0f
                            v.translationY = 0f
                            v.background = cyberBrutalistBg(
                                fillColor = fill,
                                strokeColor = stroke,
                                shadowColor = NC.SHADOW_DARK,
                                offsetDp = 4,
                                cornerRadiusDp = 0,
                                rightFaceColor = Color.parseColor("#3C4A3F")
                            )
                        }
                    }
                    false
                }
            }
        }

        val cancelBtn = pressable(
            cancelLabel,
            fill = NC.SURFACE_CONTAINER,
            stroke = NC.OUTLINE_VAR,
            textColor = Color.WHITE,
            weight = 1f,
            endMargin = dp(10)
        ) { dialog.dismiss() }

        val confirmFill = if (destructive) Color.parseColor("#1E1212") else NC.PRIMARY
        val confirmStroke = if (destructive) NC.ERROR else NC.PRIMARY
        val confirmText = if (destructive) NC.ERROR else Color.parseColor("#0A0A0A")
        val confirmBtn = pressable(
            confirmLabel,
            fill = confirmFill,
            stroke = confirmStroke,
            textColor = confirmText,
            weight = 1f,
            endMargin = 0
        ) {
            dialog.dismiss()
            onConfirm()
        }

        btnRow.addView(cancelBtn)
        btnRow.addView(confirmBtn)
        card.addView(btnRow)

        scrim.addView(card)
        dialog.setContentView(scrim)
        dialog.window?.apply {
            setLayout(MATCH, MATCH)
            setBackgroundDrawableResource(android.R.color.transparent)
            // keep status bar readable
            statusBarColor = Color.TRANSPARENT
            navigationBarColor = Color.TRANSPARENT
        }
        dialog.show()
    }

    private fun onChrootUninstalled(fromCallback: Boolean) {
        if (LinuxCommandBuilder.currentMethod == "chroot") {
            LinuxCommandBuilder.currentMethod = "proot"
            getSharedPreferences("nativecode_prefs", MODE_PRIVATE)
                .edit().putString("linux_method", "proot").apply()
            if (::homeContainerLabel.isInitialized) {
                homeContainerLabel.text = ProjectPathResolver.methodLabel()
            }
            refreshToolCardsForMethod()
        }
        clearChrootInfoCache()
        applyInstantChrootStatus()
        chrootSizeValueTv?.text = "—"
        chrootSizeUnitTv?.text = ""
        chrootSizeHintTv?.text = "Uninstalled"
        applyChrootRootBadge(chrootRootBadge, rootOk = false, checking = false)
        chrootProcCountTv?.text = "0"
        chrootProcHintTv?.text = "No chroot processes"
        chrootProcSampleBox?.removeAllViews()
        chrootProcSampleBox?.visibility = View.GONE
        chrootProcStatusTv?.visibility = View.GONE
        setChrootProcKillEnabled(false)
        refreshChrootSettingsPage(force = true)
        val msg = if (fromCallback) {
            "Chroot uninstalled — switched to PRoot if needed"
        } else {
            "Chroot uninstall finished — storage will refresh"
        }
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
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

    private fun glassCard() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = cyberBrutalistBg(
            fillColor = NC.SURFACE_LOW,
            strokeColor = NC.OUTLINE_VAR,
            shadowColor = NC.SHADOW_DARK,
            offsetDp = 6,
            cornerRadiusDp = 0
        )
        setPadding(dp(16), dp(16), dp(16), dp(16))
    }
    private fun sectionHeader(iconRes: Int, title: String, iconColor: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, dp(10))
        val iconIv = ImageView(this@MainActivity).apply {
            setImageResource(iconRes)
            setColorFilter(iconColor)
            layoutParams = LinearLayout.LayoutParams(dp(20), dp(20)).apply { rightMargin = dp(10) }
        }
        val titleTv = TextView(this@MainActivity).apply { text = title; textSize = 15f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD }
        addView(iconIv); addView(titleTv)
    }
    private fun infoRow(label: String, value: String) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(NC.SURFACE_LOWEST)
            setStroke(dp(1), NC.OUTLINE_VAR)
        }
        setPadding(dp(12), dp(10), dp(12), dp(10))
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)

        val labelTv = TextView(this@MainActivity).apply {
            text = label.uppercase()
            textSize = 12f
            setTextColor(Color.WHITE)
            typeface = Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        val valTv = TextView(this@MainActivity).apply {
            text = value
            textSize = 12f
            setTextColor(NC.PRIMARY)
            typeface = Typeface.MONOSPACE
            paint.isFakeBoldText = true
        }
        addView(labelTv)
        addView(valTv)
    }
    private fun inputField(label: String, hint: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, dp(10))
        addView(TextView(this@MainActivity).apply { text = label; textSize = 10f; setTextColor(NC.ON_SURF_VAR); typeface = Typeface.MONOSPACE; setPadding(0, 0, 0, dp(4)) })
        addView(EditText(this@MainActivity).apply { this.hint = hint; setHintTextColor(NC.OUTLINE); textSize = 13f; setTextColor(NC.ON_SURFACE); typeface = Typeface.MONOSPACE; setBackgroundColor(NC.SURFACE_HIGH); background = roundedBg(NC.SURFACE_HIGH, NC.BORDER_VAR, dp(6)); setPadding(dp(12), dp(10), dp(12), dp(10)) })
    }

    private fun lineNumCell(num: String) = TextView(this).apply { text = num; textSize = 11f; setTextColor(NC.OUTLINE); typeface = Typeface.MONOSPACE; gravity = Gravity.END; setPadding(dp(6), 0, dp(6), 0); layoutParams = LinearLayout.LayoutParams(dp(36), WRAP) }
    private fun textBadge(text: String, bg: Int, fg: Int) = TextView(this).apply { this.text = text; textSize = 10f; setTextColor(fg); typeface = Typeface.MONOSPACE; background = roundedBg(bg, fg, dp(4)); setPadding(dp(6), dp(2), dp(6), dp(2)) }
    private fun primaryBtn(t: String) = TextView(this).apply {
        text = t; textSize = 13f; setTextColor(Color.parseColor("#0A0A0A")); typeface = Typeface.MONOSPACE; gravity = Gravity.CENTER
        background = cyberBrutalistBg(
            fillColor = Color.parseColor("#3DDC84"),
            strokeColor = Color.parseColor("#3DDC84"),
            shadowColor = Color.parseColor("#393939"),
            offsetDp = 6,
            cornerRadiusDp = 0,
            rightFaceColor = Color.parseColor("#3C4A3F")
        )
        setPadding(dp(20), dp(10), dp(20), dp(10))
    }
    private fun outlineBtn(t: String) = TextView(this).apply {
        text = t; textSize = 13f; setTextColor(Color.parseColor("#FAFAFA")); typeface = Typeface.MONOSPACE; gravity = Gravity.CENTER
        background = cyberBrutalistBg(
            fillColor = Color.parseColor("#1E1E1E"),
            strokeColor = Color.parseColor("#3DDC84"),
            shadowColor = Color.parseColor("#393939"),
            offsetDp = 6,
            cornerRadiusDp = 0,
            rightFaceColor = Color.parseColor("#3C4A3F")
        )
        setPadding(dp(20), dp(10), dp(20), dp(10))
    }
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
            TermuxHostPaths.applyPackageToExtractedPrefix(filesDir)
            val homeDir = File(filesDir, "home").also { it.mkdirs() }
            val scripts = arrayOf(
                "setup_termux.sh",
                "flux_install.sh",
                "start_gui.sh",
                "stop_gui.sh",
                "setup_debian_family.sh",
                "setup_customization_debian.sh",
                "setup_hw_accel_debian.sh",
                "setup_cli_tools.sh",
                "setup_debian13_chroot.sh",
                "uninstall_debian13_chroot.sh",
                "chroot_processes.sh"
            )
            for (script in scripts) {
                val assetPath = when {
                    script.contains("chroot") -> "scripts/chroot/$script"
                    else -> "scripts/$script"
                }
                val out = File(homeDir, script)
                try {
                    assets.open(assetPath).use { input -> FileOutputStream(out).use { input.copyTo(it) } }
                    out.setExecutable(true)
                } catch (e: Exception) {
                    Log.w("Setup", "Script $assetPath not found in assets", e)
                }
            }
            // Deploy font.ttf
            val termuxDir = File(homeDir, ".termux").also { it.mkdirs() }
            val fontOut = File(termuxDir, "font.ttf")
            assets.open("fonts/font.ttf").use { input -> FileOutputStream(fontOut).use { input.copyTo(it) } }
            // Pinned Debian 13 rootfs for proot-distro install ./file --name debian
            deployRootfsArchive(homeDir)
        } catch (e: Exception) {
            Log.e("Setup", "Failed to deploy scripts", e)
        }
    }

    /** Copy assets/rootfs/debian_13_rootfs.tar.xz → $HOME (skip if already present & large). */
    private fun deployRootfsArchive(homeDir: File) {
        val out = File(homeDir, "debian_13_rootfs.tar.xz")
        // Skip re-copy of ~82MiB archive once deployed
        if (out.isFile && out.length() > 50L * 1024L * 1024L) {
            Log.i("Setup", "Rootfs already deployed: ${out.absolutePath} (${out.length()} bytes)")
            return
        }
        try {
            assets.open("rootfs/debian_13_rootfs.tar.xz").use { input ->
                FileOutputStream(out).use { input.copyTo(it) }
            }
            Log.i("Setup", "Deployed rootfs: ${out.absolutePath} (${out.length()} bytes)")
        } catch (e: Exception) {
            Log.e("Setup", "Failed to deploy rootfs archive from assets", e)
        }
    }

    /** Run a command and stream each output line to [onLine] on the main thread. */
    private fun startGui() {
        val serviceIntent = Intent(this, BackgroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent)
        else startService(serviceIntent)

        executor.execute {
            val nld  = applicationInfo.nativeLibraryDir
            val bash = File(nld, "libbash.so").absolutePath
            ShellCommandRunner.run(this, arrayOf(bash, File(TermuxHostPaths.HOME, "start_gui.sh").absolutePath, "debian"))
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
            ShellCommandRunner.run(this, arrayOf(bash, "/data/data/com.ivarna.nativecode/files/home/stop_gui.sh", "debian"))
        }
    }

    private fun initTerminalView() {
        terminalView.setTextSize(termFontSize)
        try {
            val tf = Typeface.createFromAsset(assets, "fonts/font.ttf")
            terminalView.setTypeface(tf)
        } catch (e: Exception) {
            Log.e("Terminal", "Failed to set custom typeface", e)
        }

        viewClient = object : TerminalViewClient {
            override fun onScale(scale: Float): Float {
                if (scale < 0.9f || scale > 1.1f) {
                    val nextSize = (termFontSize * scale).toInt()
                    setGlobalTerminalZoom(nextSize)
                    return 1.0f
                }
                return scale
            }
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
            override fun readControlKey(): Boolean = termModState.readCtrl(true)
            override fun readAltKey(): Boolean     = termModState.readAlt(true)
            override fun readShiftKey(): Boolean   = termModState.readShift(true)
            override fun readFnKey(): Boolean      = termModState.readFn(true)
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
                executor.execute {
                    session.emulator?.paste(text) ?: session.write(text)
                }
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
        navigateToPage(ID_HOME)
    }

    private fun iconButton(icon: String): TextView = TextView(this).apply {
        text = icon; textSize = 18f; setPadding(dp(8), dp(4), dp(8), dp(4))
    }

    private fun primaryButton(label: String, onClick: () -> Unit): TextView = TextView(this).apply {
        text = label
        textSize = 14f
        setTextColor(Color.parseColor("#0A0A0A"))
        typeface = Typeface.MONOSPACE
        paint.isFakeBoldText = true
        gravity = Gravity.CENTER
        background = cyberBrutalistBg(
            fillColor = NC.PRIMARY_CON,
            strokeColor = Color.parseColor("#3C4A3F"),
            shadowColor = NC.SHADOW_DARK,
            offsetDp = 6,
            cornerRadiusDp = 0,
            rightFaceColor = Color.parseColor("#3C4A3F")
        )
        setPadding(dp(18), dp(12), dp(18), dp(12))
        setOnClickListener { onClick() }
        setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    v.translationX = dp(4).toFloat()
                    v.translationY = dp(4).toFloat()
                    v.background = cyberBrutalistBg(
                        fillColor = NC.PRIMARY_CON,
                        strokeColor = Color.parseColor("#3C4A3F"),
                        shadowColor = NC.SHADOW_DARK,
                        offsetDp = 2,
                        cornerRadiusDp = 0,
                        rightFaceColor = Color.parseColor("#3C4A3F")
                    )
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    v.translationX = 0f
                    v.translationY = 0f
                    v.background = cyberBrutalistBg(
                        fillColor = NC.PRIMARY_CON,
                        strokeColor = Color.parseColor("#3C4A3F"),
                        shadowColor = NC.SHADOW_DARK,
                        offsetDp = 6,
                        cornerRadiusDp = 0,
                        rightFaceColor = Color.parseColor("#3C4A3F")
                    )
                }
            }
            false
        }
    }

    private fun secondaryButton(label: String, onClick: () -> Unit): TextView = TextView(this).apply {
        text = label
        textSize = 13f
        setTextColor(NC.ON_SURFACE)
        typeface = Typeface.MONOSPACE
        paint.isFakeBoldText = true
        gravity = Gravity.CENTER
        background = cyberBrutalistBg(
            fillColor = NC.SURFACE_CONTAINER,
            strokeColor = NC.PRIMARY,
            shadowColor = NC.SHADOW_DARK,
            offsetDp = 4,
            cornerRadiusDp = 0,
            rightFaceColor = Color.parseColor("#3C4A3F")
        )
        setPadding(dp(14), dp(10), dp(14), dp(10))
        setOnClickListener { onClick() }
        setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    v.translationX = dp(2).toFloat()
                    v.translationY = dp(2).toFloat()
                    v.background = cyberBrutalistBg(
                        fillColor = NC.SURFACE_CONTAINER,
                        strokeColor = NC.PRIMARY,
                        shadowColor = NC.SHADOW_DARK,
                        offsetDp = 2,
                        cornerRadiusDp = 0,
                        rightFaceColor = Color.parseColor("#3C4A3F")
                    )
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    v.translationX = 0f
                    v.translationY = 0f
                    v.background = cyberBrutalistBg(
                        fillColor = NC.SURFACE_CONTAINER,
                        strokeColor = NC.PRIMARY,
                        shadowColor = NC.SHADOW_DARK,
                        offsetDp = 4,
                        cornerRadiusDp = 0,
                        rightFaceColor = Color.parseColor("#3C4A3F")
                    )
                }
            }
            false
        }
    }

    private fun dangerButton(label: String, onClick: () -> Unit): TextView = TextView(this).apply {
        text = label
        textSize = 13f
        setTextColor(Color.WHITE)
        typeface = Typeface.MONOSPACE
        paint.isFakeBoldText = true
        gravity = Gravity.CENTER
        background = cyberBrutalistBg(
            fillColor = Color.parseColor("#93000A"),
            strokeColor = Color.parseColor("#FFB4AB"),
            shadowColor = NC.SHADOW_DARK,
            offsetDp = 4,
            cornerRadiusDp = 0,
            rightFaceColor = Color.parseColor("#690005")
        )
        setPadding(dp(14), dp(10), dp(14), dp(10))
        setOnClickListener { onClick() }
    }

    private fun statWidget(label: String, value: String, color: Int): LinearLayout {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        val labelTv = TextView(this).apply { text = label; textSize = 12f; setTextColor(color); typeface = Typeface.MONOSPACE; gravity = Gravity.CENTER }
        val valueTv = TextView(this).apply { tag = label; text = value; textSize = 20f; setTextColor(NC.ON_SURFACE); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER }
        col.addView(labelTv); col.addView(valueTv); return col
    }

    private fun projectCard(name: String, path: String, time: String, iconStr: String = ""): View {
        val isGit = path.endsWith(".git") || File(path, ".git").exists() || path.contains("git", ignoreCase = true) || !name.contains("ui_shell", ignoreCase = true)
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = cyberBrutalistBg(
                fillColor = NC.SURFACE_LOW,
                strokeColor = Color.parseColor("#3c4a3f"),
                shadowColor = NC.SHADOW_DARK,
                offsetDp = 6,
                cornerRadiusDp = 0,
                rightFaceColor = Color.parseColor("#3c4a3f")
            )
            setPadding(dp(16), dp(14), dp(16), dp(14))

            val iconContainer = FrameLayout(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply {
                    rightMargin = dp(14)
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(NC.SURFACE_LOWEST)
                    setStroke(dp(1), Color.parseColor("#3360F99E"))
                    cornerRadius = dp(4).toFloat()
                }
            }

            if (iconStr.isEmpty()) {
                val iv = ImageView(this@MainActivity).apply {
                    setImageResource(R.drawable.ic_folder_special)
                    setColorFilter(NC.PRIMARY)
                    layoutParams = FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER)
                }
                iconContainer.addView(iv)
            } else if (iconStr.length <= 4 && !iconStr.startsWith("/") && !iconStr.startsWith("http") && !iconStr.startsWith("content")) {
                val tv = TextView(this@MainActivity).apply {
                    text = iconStr
                    textSize = 20f
                    gravity = Gravity.CENTER
                    layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
                }
                iconContainer.addView(tv)
            } else {
                val iv = ImageView(this@MainActivity).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
                }
                iconContainer.addView(iv)
                executor.execute {
                    try {
                        val bitmap = when {
                            iconStr.startsWith("content://") -> {
                                contentResolver.openInputStream(android.net.Uri.parse(iconStr))?.use {
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
                                iv.setImageBitmap(bitmap)
                            } else {
                                iconContainer.removeAllViews()
                                val defaultIv = ImageView(this@MainActivity).apply {
                                    setImageResource(R.drawable.ic_folder_special)
                                    setColorFilter(NC.PRIMARY)
                                    layoutParams = FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER)
                                }
                                iconContainer.addView(defaultIv)
                            }
                        }
                    } catch (e: Exception) {
                        mainHandler.post {
                            iconContainer.removeAllViews()
                            val defaultIv = ImageView(this@MainActivity).apply {
                                setImageResource(R.drawable.ic_folder_special)
                                setColorFilter(NC.PRIMARY)
                                layoutParams = FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER)
                            }
                            iconContainer.addView(defaultIv)
                        }
                    }
                }
            }

            val infoCol = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
            }

            val titleTv = TextView(this@MainActivity).apply {
                text = name.uppercase()
                textSize = 15f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
            }
            val timeTv = TextView(this@MainActivity).apply {
                text = if (time.startsWith("UPDATED:", ignoreCase = true)) time.uppercase() else "UPDATED: ${time.uppercase()}"
                textSize = 11f
                setTextColor(Color.parseColor("#66FFFFFF"))
                typeface = Typeface.MONOSPACE
            }
            infoCol.addView(titleTv)
            infoCol.addView(timeTv)

            val badgeBox = TextView(this@MainActivity).apply {
                text = if (isGit) "GIT" else "LOCAL"
                textSize = 11f
                setTextColor(if (isGit) NC.PRIMARY else Color.WHITE)
                typeface = Typeface.MONOSPACE
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(NC.SURFACE_LOWEST)
                    setStroke(dp(1), if (isGit) Color.parseColor("#3360F99E") else Color.parseColor("#33FFFFFF"))
                }
                setPadding(dp(8), dp(3), dp(8), dp(3))
            }

            val actionIcon = ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.ic_chevron_right)
                imageTintList = android.content.res.ColorStateList.valueOf(NC.PRIMARY)
                layoutParams = LinearLayout.LayoutParams(dp(20), dp(20)).apply { leftMargin = dp(10) }
            }

            addView(iconContainer)
            addView(infoCol)
            addView(actionIcon)

            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        v.translationX = dp(4).toFloat()
                        v.translationY = dp(4).toFloat()
                        v.background = cyberBrutalistBg(
                            fillColor = NC.SURFACE_LOW,
                            strokeColor = Color.parseColor("#3c4a3f"),
                            shadowColor = NC.SHADOW_DARK,
                            offsetDp = 2,
                            cornerRadiusDp = 0,
                            rightFaceColor = Color.parseColor("#3c4a3f")
                        )
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.translationX = 0f
                        v.translationY = 0f
                        v.background = cyberBrutalistBg(
                            fillColor = NC.SURFACE_LOW,
                            strokeColor = Color.parseColor("#3c4a3f"),
                            shadowColor = NC.SHADOW_DARK,
                            offsetDp = 6,
                            cornerRadiusDp = 0,
                            rightFaceColor = Color.parseColor("#3c4a3f")
                        )
                    }
                }
                false
            }
        }
    }

    private fun projectGridCard(name: String, path: String, time: String, iconStr: String = "", method: String = "proot"): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = cyberBrutalistBg(
                fillColor = NC.SURFACE_LOW,
                strokeColor = Color.parseColor("#3c4a3f"),
                shadowColor = NC.SHADOW_DARK,
                offsetDp = 6,
                cornerRadiusDp = 0,
                rightFaceColor = Color.parseColor("#3c4a3f")
            )
            setPadding(dp(16), dp(24), dp(16), dp(20))

            val iconContainer = FrameLayout(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(56), dp(56)).apply {
                    bottomMargin = dp(16)
                    gravity = Gravity.CENTER_HORIZONTAL
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(NC.SURFACE_LOWEST)
                    setStroke(dp(1), Color.parseColor("#3360F99E"))
                    cornerRadius = 0f
                }
            }

            if (iconStr.isEmpty()) {
                val iv = ImageView(this@MainActivity).apply {
                    setImageResource(R.drawable.ic_folder_special)
                    setColorFilter(NC.PRIMARY)
                    layoutParams = FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER)
                }
                iconContainer.addView(iv)
            } else if (iconStr.length <= 4 && !iconStr.startsWith("/") && !iconStr.startsWith("http") && !iconStr.startsWith("content")) {
                val tv = TextView(this@MainActivity).apply {
                    text = iconStr
                    textSize = 24f
                    gravity = Gravity.CENTER
                    layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
                }
                iconContainer.addView(tv)
            } else {
                val iv = ImageView(this@MainActivity).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
                }
                iconContainer.addView(iv)
                executor.execute {
                    try {
                        val bitmap = when {
                            iconStr.startsWith("content://") -> {
                                contentResolver.openInputStream(android.net.Uri.parse(iconStr))?.use {
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
                                iv.setImageBitmap(bitmap)
                            } else {
                                iconContainer.removeAllViews()
                                val defaultIv = ImageView(this@MainActivity).apply {
                                    setImageResource(R.drawable.ic_folder_special)
                                    setColorFilter(NC.PRIMARY)
                                    layoutParams = FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER)
                                }
                                iconContainer.addView(defaultIv)
                            }
                        }
                    } catch (e: Exception) {
                        mainHandler.post {
                            iconContainer.removeAllViews()
                            val defaultIv = ImageView(this@MainActivity).apply {
                                setImageResource(R.drawable.ic_folder_special)
                                setColorFilter(NC.PRIMARY)
                                layoutParams = FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER)
                            }
                            iconContainer.addView(defaultIv)
                        }
                    }
                }
            }

            val titleTv = TextView(this@MainActivity).apply {
                text = name.uppercase()
                textSize = 15f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(6) }
            }

            val timeTv = TextView(this@MainActivity).apply {
                text = if (time.startsWith("UPDATED:", ignoreCase = true)) time.uppercase() else "UPDATED: ${time.uppercase()}"
                textSize = 11f
                setTextColor(Color.parseColor("#869587"))
                typeface = Typeface.MONOSPACE
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }

            val methodTag = TextView(this@MainActivity).apply {
                text = if (method == "chroot") "CHROOT" else "PROOT"
                textSize = 10f
                setTextColor(if (method == "chroot") NC.TERTIARY else NC.PRIMARY)
                typeface = Typeface.MONOSPACE
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(NC.SURFACE_LOWEST)
                    setStroke(dp(1), if (method == "chroot") NC.TERTIARY else Color.parseColor("#3360F99E"))
                }
                setPadding(dp(6), dp(2), dp(6), dp(2))
                layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).apply {
                    topMargin = dp(8)
                    gravity = Gravity.CENTER_HORIZONTAL
                }
            }

            addView(iconContainer)
            addView(titleTv)
            addView(timeTv)
            addView(methodTag)

            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        v.translationX = dp(4).toFloat()
                        v.translationY = dp(4).toFloat()
                        v.background = cyberBrutalistBg(
                            fillColor = NC.SURFACE_LOW,
                            strokeColor = Color.parseColor("#3c4a3f"),
                            shadowColor = NC.SHADOW_DARK,
                            offsetDp = 2,
                            cornerRadiusDp = 0,
                            rightFaceColor = Color.parseColor("#3c4a3f")
                        )
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.translationX = 0f
                        v.translationY = 0f
                        v.background = cyberBrutalistBg(
                            fillColor = NC.SURFACE_LOW,
                            strokeColor = Color.parseColor("#3c4a3f"),
                            shadowColor = NC.SHADOW_DARK,
                            offsetDp = 6,
                            cornerRadiusDp = 0,
                            rightFaceColor = Color.parseColor("#3c4a3f")
                        )
                    }
                }
                false
            }
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

    private fun isBatteryCharging(): Boolean {
        return try {
            val bm = getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && bm != null) {
                bm.isCharging
            } else {
                val intent = registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
                val status = intent?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
                status == android.os.BatteryManager.BATTERY_STATUS_CHARGING || status == android.os.BatteryManager.BATTERY_STATUS_FULL
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun readBatteryUsage(): Int {
        return try {
            val bm = getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
            val level = bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            if (level in 0..100) {
                level
            } else {
                val intent = registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
                val rawLevel = intent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = intent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
                if (rawLevel >= 0 && scale > 0) (rawLevel * 100) / scale else 0
            }
        } catch (e: Exception) {
            0
        }
    }

    private fun startResourceMonitoring(cpuTv: TextView? = null, memTv: TextView? = null, diskTv: TextView? = null) {
        if (resourceMonitorRunnable != null) {
            executor.execute {
                val cpuUsage = readCpuUsage()
                val memUsage = readMemUsage()
                val memPercent = readMemPercent()
                val diskUsage = readDiskUsage()
                val batUsage = readBatteryUsage()
                val isCharging = isBatteryCharging()
                val batDrawableRes = if (isCharging) R.drawable.ic_battery_charging else R.drawable.ic_battery_discharging
                mainHandler.post {
                    cpuTv?.text = "$cpuUsage%"
                    memTv?.text = memUsage
                    diskTv?.text = "$diskUsage%"
                    if (::unifiedHeader.isInitialized) {
                        val headerCpu = unifiedHeader.findViewWithTag<TextView>("HEADER_CPU")
                        val headerRam = unifiedHeader.findViewWithTag<TextView>("HEADER_RAM")
                        val headerBat = unifiedHeader.findViewWithTag<TextView>("HEADER_BAT")
                        val headerBatIcon = unifiedHeader.findViewWithTag<ImageView>("HEADER_BAT_ICON")
                        headerCpu?.text = "C: $cpuUsage%"
                        headerRam?.text = "R: $memUsage"
                        headerBat?.text = "$batUsage%"
                        headerBatIcon?.setImageResource(batDrawableRes)
                    }
                    if (::terminalWorkspaceLayout.isInitialized) {
                        val termCpu = terminalWorkspaceLayout.findViewWithTag<TextView>("TERM_HEADER_CPU")
                        val termRam = terminalWorkspaceLayout.findViewWithTag<TextView>("TERM_HEADER_RAM")
                        val termBat = terminalWorkspaceLayout.findViewWithTag<TextView>("TERM_HEADER_BAT")
                        val termBatIcon = terminalWorkspaceLayout.findViewWithTag<ImageView>("TERM_HEADER_BAT_ICON")
                        termCpu?.text = "C: $cpuUsage%"
                        termRam?.text = "R: $memUsage"
                        termBat?.text = "$batUsage%"
                        termBatIcon?.setImageResource(batDrawableRes)
                    }
                    if (::projectWorkspaceLayout.isInitialized) {
                        val projCpu = projectWorkspaceLayout.findViewWithTag<TextView>("PROJ_HEADER_CPU")
                        val projRam = projectWorkspaceLayout.findViewWithTag<TextView>("PROJ_HEADER_RAM")
                        val projBat = projectWorkspaceLayout.findViewWithTag<TextView>("PROJ_HEADER_BAT")
                        val projBatIcon = projectWorkspaceLayout.findViewWithTag<ImageView>("PROJ_HEADER_BAT_ICON")
                        projCpu?.text = "C: $cpuUsage%"
                        projRam?.text = "R: $memUsage"
                        projBat?.text = "$batUsage%"
                        projBatIcon?.setImageResource(batDrawableRes)
                    }
                    val memStats = readMemDetails()
                    if (::homeLayout.isInitialized) {
                        composeCpuState.intValue = cpuUsage
                        composeMemState.intValue = memPercent
                        composeRamUsedState.longValue = memStats.ramUsedMb
                        composeRamTotalState.longValue = memStats.ramTotalMb
                        composeSwapUsedState.longValue = memStats.swapUsedMb
                        composeSwapTotalState.longValue = memStats.swapTotalMb
                        composeDiskState.intValue = diskUsage

                        val cpuValTv = homeLayout.findViewWithTag<TextView>("HOME_CPU_VAL")
                        val cpuRing = homeLayout.findViewWithTag<CircularProgressView>("HOME_CPU_RING")
                        val cpuTag = homeLayout.findViewWithTag<TextView>("HOME_CPU_TAG")

                        cpuValTv?.text = "$cpuUsage%"
                        cpuRing?.progress = cpuUsage.toFloat()
                        cpuTag?.text = if (cpuUsage > 80) "[WARN]" else "[OK]"
                        cpuTag?.setTextColor(if (cpuUsage > 80) Color.parseColor("#FF8A8A") else Color.parseColor("#3DDC84"))

                        val memValTv = homeLayout.findViewWithTag<TextView>("HOME_MEM_VAL")
                        val memRing = homeLayout.findViewWithTag<CircularProgressView>("HOME_MEM_RING")
                        val memTag = homeLayout.findViewWithTag<TextView>("HOME_MEM_TAG")

                        memValTv?.text = "$memPercent%"
                        memRing?.progress = memPercent.toFloat()
                        memTag?.text = if (memPercent > 80) "[WARN]" else "[OK]"
                        memTag?.setTextColor(if (memPercent > 80) Color.parseColor("#FF8A8A") else Color.parseColor("#3DDC84"))
                    }
                }
            }
            return
        }
        val monitorRunnable = object : Runnable {
            override fun run() {
                executor.execute {
                    val cpuUsage = readCpuUsage()
                    val memUsage = readMemUsage()
                    val memPercent = readMemPercent()
                    val diskUsage = readDiskUsage()
                    val batUsage = readBatteryUsage()
                    val isCharging = isBatteryCharging()
                    val batDrawableRes = if (isCharging) R.drawable.ic_battery_charging else R.drawable.ic_battery_discharging
                    mainHandler.post {
                        cpuTv?.text = "$cpuUsage%"
                        memTv?.text = memUsage
                        diskTv?.text = "$diskUsage%"
                        if (::unifiedHeader.isInitialized) {
                            val headerCpu = unifiedHeader.findViewWithTag<TextView>("HEADER_CPU")
                            val headerRam = unifiedHeader.findViewWithTag<TextView>("HEADER_RAM")
                            val headerBat = unifiedHeader.findViewWithTag<TextView>("HEADER_BAT")
                            val headerBatIcon = unifiedHeader.findViewWithTag<ImageView>("HEADER_BAT_ICON")
                            headerCpu?.text = "C: $cpuUsage%"
                            headerRam?.text = "R: $memUsage"
                            headerBat?.text = "$batUsage%"
                            headerBatIcon?.setImageResource(batDrawableRes)
                        }
                        if (::terminalWorkspaceLayout.isInitialized) {
                            val termCpu = terminalWorkspaceLayout.findViewWithTag<TextView>("TERM_HEADER_CPU")
                            val termRam = terminalWorkspaceLayout.findViewWithTag<TextView>("TERM_HEADER_RAM")
                            val termBat = terminalWorkspaceLayout.findViewWithTag<TextView>("TERM_HEADER_BAT")
                            val termBatIcon = terminalWorkspaceLayout.findViewWithTag<ImageView>("TERM_HEADER_BAT_ICON")
                            termCpu?.text = "C: $cpuUsage%"
                            termRam?.text = "R: $memUsage"
                            termBat?.text = "$batUsage%"
                            termBatIcon?.setImageResource(batDrawableRes)
                        }
                        if (::projectWorkspaceLayout.isInitialized) {
                            val projCpu = projectWorkspaceLayout.findViewWithTag<TextView>("PROJ_HEADER_CPU")
                            val projRam = projectWorkspaceLayout.findViewWithTag<TextView>("PROJ_HEADER_RAM")
                            val projBat = projectWorkspaceLayout.findViewWithTag<TextView>("PROJ_HEADER_BAT")
                            val projBatIcon = projectWorkspaceLayout.findViewWithTag<ImageView>("PROJ_HEADER_BAT_ICON")
                            projCpu?.text = "C: $cpuUsage%"
                            projRam?.text = "R: $memUsage"
                            projBat?.text = "$batUsage%"
                            projBatIcon?.setImageResource(batDrawableRes)
                        }
                        val memStats = readMemDetails()
                        if (::homeLayout.isInitialized) {
                            composeCpuState.intValue = cpuUsage
                            composeMemState.intValue = memPercent
                            composeRamUsedState.longValue = memStats.ramUsedMb
                            composeRamTotalState.longValue = memStats.ramTotalMb
                            composeSwapUsedState.longValue = memStats.swapUsedMb
                            composeSwapTotalState.longValue = memStats.swapTotalMb
                            composeDiskState.intValue = diskUsage

                            val cpuValTv = homeLayout.findViewWithTag<TextView>("HOME_CPU_VAL")
                            val cpuRing = homeLayout.findViewWithTag<CircularProgressView>("HOME_CPU_RING")
                            val cpuTag = homeLayout.findViewWithTag<TextView>("HOME_CPU_TAG")

                            cpuValTv?.text = "$cpuUsage%"
                            cpuRing?.progress = cpuUsage.toFloat()
                            cpuTag?.text = if (cpuUsage > 80) "[WARN]" else "[OK]"
                            cpuTag?.setTextColor(if (cpuUsage > 80) Color.parseColor("#FF8A8A") else Color.parseColor("#3DDC84"))

                            val memValTv = homeLayout.findViewWithTag<TextView>("HOME_MEM_VAL")
                            val memRing = homeLayout.findViewWithTag<CircularProgressView>("HOME_MEM_RING")
                            val memTag = homeLayout.findViewWithTag<TextView>("HOME_MEM_TAG")

                            memValTv?.text = "$memPercent%"
                            memRing?.progress = memPercent.toFloat()
                            memTag?.text = if (memPercent > 80) "[WARN]" else "[OK]"
                            memTag?.setTextColor(if (memPercent > 80) Color.parseColor("#FF8A8A") else Color.parseColor("#3DDC84"))
                        }
                    }
                }
                mainHandler.postDelayed(this, 2000)
            }
        }
        resourceMonitorRunnable = monitorRunnable
        mainHandler.post(monitorRunnable)
    }

    private fun readMemPercent(): Int {
        try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            if (am != null) {
                val mi = android.app.ActivityManager.MemoryInfo()
                am.getMemoryInfo(mi)
                if (mi.totalMem > 0) {
                    return (((mi.totalMem - mi.availMem) * 100) / mi.totalMem).toInt().coerceIn(0, 100)
                }
            }
        } catch (e: Exception) {}
        try {
            val file = File("/proc/meminfo")
            if (!file.exists()) return 0
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
            if (totalKb > 0L) {
                return (((totalKb - availKb) * 100) / totalKb).toInt().coerceIn(0, 100)
            }
        } catch (e: Exception) {}
        return 0
    }

    private fun readCpuUsage(): Int {
        val snapshot = CpuUtilizationProvider.getCpuSnapshot()
        return snapshot.overallPercent.toInt()
    }

    data class MemStats(
        val ramUsedMb: Long = 0,
        val ramTotalMb: Long = 0,
        val swapUsedMb: Long = 0,
        val swapTotalMb: Long = 0
    )

    private fun readMemDetails(): MemStats {
        var ramTotalKb = 0L
        var ramAvailKb = 0L
        var swapTotalKb = 0L
        var swapFreeKb = 0L

        try {
            val file = File("/proc/meminfo")
            if (file.exists()) {
                file.forEachLine { line ->
                    if (line.startsWith("MemTotal:")) {
                        val parts = line.split(Regex("\\s+"))
                        if (parts.size >= 2) ramTotalKb = parts[1].toLong()
                    } else if (line.startsWith("MemAvailable:")) {
                        val parts = line.split(Regex("\\s+"))
                        if (parts.size >= 2) ramAvailKb = parts[1].toLong()
                    } else if (line.startsWith("SwapTotal:")) {
                        val parts = line.split(Regex("\\s+"))
                        if (parts.size >= 2) swapTotalKb = parts[1].toLong()
                    } else if (line.startsWith("SwapFree:")) {
                        val parts = line.split(Regex("\\s+"))
                        if (parts.size >= 2) swapFreeKb = parts[1].toLong()
                    }
                }
            }
        } catch (e: Exception) {}

        if (ramTotalKb == 0L) {
            try {
                val am = getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
                if (am != null) {
                    val mi = android.app.ActivityManager.MemoryInfo()
                    am.getMemoryInfo(mi)
                    ramTotalKb = mi.totalMem / 1024
                    ramAvailKb = mi.availMem / 1024
                }
            } catch (e: Exception) {}
        }

        val ramTotalMb = ramTotalKb / 1024
        val ramUsedMb = ((ramTotalKb - ramAvailKb).coerceAtLeast(0)) / 1024
        val swapTotalMb = swapTotalKb / 1024
        val swapUsedMb = ((swapTotalKb - swapFreeKb).coerceAtLeast(0)) / 1024

        return MemStats(ramUsedMb, ramTotalMb, swapUsedMb, swapTotalMb)
    }

    private fun readMemUsage(): String {
        try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            if (am != null) {
                val mi = android.app.ActivityManager.MemoryInfo()
                am.getMemoryInfo(mi)
                val usedBytes = mi.totalMem - mi.availMem
                val usedGb = usedBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
                return String.format(java.util.Locale.US, "%.1fG", usedGb)
            }
        } catch (e: Exception) {
            // fallback to /proc/meminfo
        }
        try {
            val file = File("/proc/meminfo")
            if (!file.exists()) return "0.0G"
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
            if (totalKb <= 0L) return "0.0G"
            val usedKb = totalKb - availKb
            val usedGb = usedKb.toDouble() / (1024 * 1024)
            return String.format(java.util.Locale.US, "%.1fG", usedGb)
        } catch (e: Exception) {
            return "0.0G"
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

    data class Project(val name: String, val icon: String, val path: String, val lastOpened: Long = 0L, val linuxMethod: String = "proot")

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
                    obj.getString("path"),
                    obj.optLong("lastOpened", 0L),
                    obj.optString("linuxMethod", "proot")
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
            obj.put("lastOpened", p.lastOpened)
            obj.put("linuxMethod", p.linuxMethod)
            arr.put(obj)
        }
        prefs.edit().putString("projects_json", arr.toString()).apply()
    }

    private fun markProjectOpened(path: String) {
        val list = getProjects().toMutableList()
        val idx = list.indexOfFirst { it.path == path }
        if (idx >= 0) {
            val p = list[idx]
            list[idx] = p.copy(lastOpened = System.currentTimeMillis())
            saveProjects(list)
        }
    }

    private fun formatRelativeTime(timestamp: Long): String {
        if (timestamp <= 0L) return "Recent"
        val diff = System.currentTimeMillis() - timestamp
        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24
        return when {
            seconds < 60 -> "Just now"
            minutes < 60 -> "${minutes}m ago"
            hours < 24 -> "${hours}h ago"
            days < 7 -> "${days}d ago"
            else -> "Recently"
        }
    }

    private fun isSymlink(file: File): Boolean {
        return try {
            val stat = android.system.Os.lstat(file.absolutePath)
            android.system.OsConstants.S_ISLNK(stat.st_mode)
        } catch (e: Exception) {
            false
        }
    }

    private fun getFolderSize(file: File): Long {
        if (!file.exists() || isSymlink(file)) return 0L
        if (file.isFile) return file.length()
        var size = 0L
        val files = file.listFiles() ?: return 0L
        for (f in files) {
            if (isSymlink(f)) continue
            size += if (f.isDirectory) getFolderSize(f) else f.length()
        }
        return size
    }

    private fun getTotalDeviceStorageBytes(): Long {
        return try {
            val stat = android.os.StatFs(android.os.Environment.getDataDirectory().path)
            stat.totalBytes
        } catch (e: Exception) {
            0L
        }
    }

    private fun getAvailableDeviceStorageBytes(): Long {
        return try {
            val stat = android.os.StatFs(android.os.Environment.getDataDirectory().path)
            stat.availableBytes
        } catch (e: Exception) {
            0L
        }
    }

    private fun updateAppStorageUsage(valTv: TextView, subTv: TextView, fillView: View, refreshBtn: View?) {
        valTv.text = "Calculating..."
        executor.execute {
            try {
                val apkSize = File(applicationInfo.sourceDir).length()
                val dataDir = File(applicationInfo.dataDir)
                val dataSize = getFolderSize(dataDir)
                val extCacheSize = externalCacheDir?.let { getFolderSize(it) } ?: 0L

                val totalAppBytes = apkSize + dataSize + extCacheSize
                val appMB = apkSize / (1024.0 * 1024.0)
                val dataMB = (dataSize + extCacheSize) / (1024.0 * 1024.0)
                val totalAppMB = totalAppBytes / (1024.0 * 1024.0)

                val deviceTotalBytes = getTotalDeviceStorageBytes()
                val deviceFreeBytes = getAvailableDeviceStorageBytes()
                val deviceTotalGB = deviceTotalBytes / (1024.0 * 1024.0 * 1024.0)
                val deviceFreeGB = deviceFreeBytes / (1024.0 * 1024.0 * 1024.0)
                val percentageOfDevice = if (deviceTotalBytes > 0) {
                    (totalAppBytes.toDouble() / deviceTotalBytes.toDouble()) * 100.0
                } else 0.0

                mainHandler.post {
                    val formattedVal = if (totalAppMB >= 1024.0) {
                        String.format(java.util.Locale.US, "%.1f", totalAppMB / 1024.0)
                    } else {
                        String.format(java.util.Locale.US, "%.1f", totalAppMB)
                    }
                    valTv.text = formattedVal
                    subTv.text = if (totalAppMB >= 1024.0) "GB" else "MB"

                    fillView.post {
                        val parentView = fillView.parent as? View
                        if (parentView != null && parentView.width > 0) {
                            val parentWidth = parentView.width
                            val fraction = (totalAppMB / (totalAppMB + 2048.0)).coerceIn(0.25, 0.95)
                            val targetWidth = (parentWidth * fraction).toInt()

                            val anim = ValueAnimator.ofInt(fillView.layoutParams.width, targetWidth)
                            anim.duration = 600
                            anim.addUpdateListener { va ->
                                fillView.layoutParams.width = va.animatedValue as Int
                                fillView.requestLayout()
                            }
                            anim.start()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                mainHandler.post {
                    valTv.text = "N/A"
                    subTv.text = "Could not calculate storage size"
                }
            }
        }
    }

    private fun styleCreateMethodChip(chip: TextView, selected: Boolean) {
        // ui_design: primary filled selected / secondary outline unselected; sharp 0px; two-tone extrusion
        chip.background = cyberBrutalistBg(
            fillColor = if (selected) NC.PRIMARY_CON else NC.SURFACE_CONTAINER,
            strokeColor = if (selected) Color.parseColor("#3C4A3F") else NC.PRIMARY,
            shadowColor = NC.SHADOW_DARK,
            offsetDp = 4,
            cornerRadiusDp = 0,
            rightFaceColor = Color.parseColor("#3C4A3F")
        )
        chip.setTextColor(if (selected) Color.parseColor("#0A0A0A") else NC.ON_SURFACE)
        chip.paint.isFakeBoldText = true
    }

    private fun refreshProjectCreateMethodChips() {
        projectCreateSelectedMethod = LinuxCommandBuilder.currentMethod
        if (projectCreateSelectedMethod == "chroot" && !ProjectPathResolver.isChrootInstalled()) {
            projectCreateSelectedMethod = "proot"
        }
        val chrootOk = ProjectPathResolver.isChrootInstalled()
        projectCreateProotChip?.let {
            styleCreateMethodChip(it, projectCreateSelectedMethod == "proot")
            it.isEnabled = true
            it.alpha = 1f
        }
        projectCreateChrootChip?.let {
            styleCreateMethodChip(it, projectCreateSelectedMethod == "chroot")
            it.isEnabled = chrootOk
            it.alpha = if (chrootOk) 1f else 0.4f
        }
    }

    private fun buildProjectCreateLayout() {
        projectCreateContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
            visibility = View.GONE
            setBackgroundColor(NC.BG)
        }
        projectCreateScrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f)
            clipToPadding = false
            setPadding(0, 0, 0, dp(24))
        }
        projectCreateLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        projectCreateScrollView.addView(projectCreateLayout)

        // Sticky page title bar (app unifiedHeader sits above this)
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(NC.SURFACE_LOWEST)
                setStroke(dp(1), Color.parseColor("#3C4A3F"))
            }
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        val backBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_arrow_back)
            setColorFilter(NC.ON_SURFACE)
            setPadding(dp(6), dp(6), dp(6), dp(6))
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply { rightMargin = dp(4) }
            setOnClickListener {
                onBackPressed()
            }
        }
        val logoIv = ImageView(this).apply {
            setImageResource(R.drawable.logo_highres)
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply { rightMargin = dp(10) }
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        val titleTv = TextView(this).apply {
            text = "Create / Import Project"
            textSize = 16f
            setTextColor(NC.ON_SURFACE)
            typeface = Typeface.MONOSPACE
            paint.isFakeBoldText = true
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        topBar.addView(backBtn)
        topBar.addView(logoIv)
        topBar.addView(titleTv)
        projectCreateContainer.addView(topBar)
        projectCreateContainer.addView(projectCreateScrollView)

        // --- Card 1: Project Name ---
        val nameCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cyberBrutalistBg(
                fillColor = NC.SURFACE_LOW,
                strokeColor = Color.parseColor("#3c4a3f"),
                shadowColor = NC.SHADOW_DARK,
                offsetDp = 4,
                cornerRadiusDp = 0,
                rightFaceColor = Color.parseColor("#3c4a3f")
            )
            setPadding(dp(16), dp(16), dp(16), dp(16))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(14) }
        }
        nameCard.addView(TextView(this).apply {
            text = "PROJECT NAME"
            setTextColor(NC.PRIMARY)
            textSize = 12f
            typeface = Typeface.MONOSPACE
            paint.isFakeBoldText = true
            setPadding(0, 0, 0, dp(8))
        })
        projectNameInput = EditText(this).apply {
            hint = "My Awesome App"
            setHintTextColor(NC.OUTLINE)
            setTextColor(NC.ON_SURFACE)
            typeface = Typeface.MONOSPACE
            background = cyberBrutalistBg(
                fillColor = NC.SURFACE_LOWEST,
                strokeColor = Color.parseColor("#3c4a3f"),
                shadowColor = Color.TRANSPARENT,
                offsetDp = 0,
                cornerRadiusDp = 0
            )
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        nameCard.addView(projectNameInput)
        projectCreateLayout.addView(nameCard)

        // --- Card 2: Project Icon (Only Browse + Preview Box) ---
        val iconCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cyberBrutalistBg(
                fillColor = NC.SURFACE_LOW,
                strokeColor = Color.parseColor("#3c4a3f"),
                shadowColor = NC.SHADOW_DARK,
                offsetDp = 4,
                cornerRadiusDp = 0,
                rightFaceColor = Color.parseColor("#3c4a3f")
            )
            setPadding(dp(16), dp(16), dp(16), dp(16))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(14) }
        }
        iconCard.addView(TextView(this).apply {
            text = "PROJECT ICON"
            setTextColor(NC.PRIMARY)
            textSize = 12f
            typeface = Typeface.MONOSPACE
            paint.isFakeBoldText = true
            setPadding(0, 0, 0, dp(8))
        })

        val iconRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }

        val iconPreviewFrame = FrameLayout(this).apply {
            background = cyberBrutalistBg(
                fillColor = NC.SURFACE_HIGH,
                strokeColor = NC.PRIMARY,
                shadowColor = NC.SHADOW_GREEN,
                offsetDp = 2,
                cornerRadiusDp = 0
            )
            setPadding(dp(4), dp(4), dp(4), dp(4))
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply { rightMargin = dp(14) }
        }

        val iconPreviewIv = ImageView(this).apply {
            setImageResource(R.drawable.ic_folder)
            setColorFilter(NC.PRIMARY)
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
        }
        val iconPreviewTv = TextView(this).apply {
            textSize = 20f
            gravity = Gravity.CENTER
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
        }
        iconPreviewFrame.addView(iconPreviewIv)
        iconPreviewFrame.addView(iconPreviewTv)

        projectIconInput = EditText(this).apply {
            hint = ""
        }

        val chooseIconBtn = secondaryButton("Browse") {
            activeIconInput = projectIconInput
            activeIconPreviewIv = iconPreviewIv
            activeIconPreviewTv = iconPreviewTv
            projectIconPickerLauncher.launch("image/*")
        }

        iconRow.addView(iconPreviewFrame)
        iconRow.addView(chooseIconBtn)
        iconCard.addView(iconRow)
        projectCreateLayout.addView(iconCard)

        // Dummy/hidden projectPathInput initialized for backward compatibility
        projectPathInput = EditText(this).apply {
            setText("/home/flux/repos/")
        }

        // --- Card 3: GitHub Repository URL ---
        val githubCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cyberBrutalistBg(
                fillColor = NC.SURFACE_LOW,
                strokeColor = Color.parseColor("#3c4a3f"),
                shadowColor = NC.SHADOW_DARK,
                offsetDp = 4,
                cornerRadiusDp = 0,
                rightFaceColor = Color.parseColor("#3c4a3f")
            )
            setPadding(dp(16), dp(16), dp(16), dp(16))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(20) }
        }
        githubCard.addView(TextView(this).apply {
            text = "GITHUB REPOSITORY URL (OPTIONAL)"
            setTextColor(NC.PRIMARY)
            textSize = 12f
            typeface = Typeface.MONOSPACE
            paint.isFakeBoldText = true
            setPadding(0, 0, 0, dp(8))
        })
        projectGithubInput = EditText(this).apply {
            hint = "https://github.com/username/repo.git"
            setHintTextColor(NC.OUTLINE)
            setTextColor(NC.ON_SURFACE)
            typeface = Typeface.MONOSPACE
            background = cyberBrutalistBg(
                fillColor = NC.SURFACE_LOWEST,
                strokeColor = Color.parseColor("#3c4a3f"),
                shadowColor = Color.TRANSPARENT,
                offsetDp = 0,
                cornerRadiusDp = 0
            )
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        githubCard.addView(projectGithubInput)
        projectCreateLayout.addView(githubCard)

        // --- Card 4: Linux Isolation Mode ---
        val methodCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cyberBrutalistBg(
                fillColor = NC.SURFACE_LOW,
                strokeColor = Color.parseColor("#3c4a3f"),
                shadowColor = NC.SHADOW_DARK,
                offsetDp = 4,
                cornerRadiusDp = 0,
                rightFaceColor = Color.parseColor("#3c4a3f")
            )
            setPadding(dp(16), dp(16), dp(16), dp(16))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(20) }
        }
        methodCard.addView(TextView(this).apply {
            text = "LINUX ISOLATION MODE"
            setTextColor(NC.PRIMARY)
            textSize = 12f
            typeface = Typeface.MONOSPACE
            paint.isFakeBoldText = true
            setPadding(0, 0, 0, dp(8))
        })
        methodCard.addView(TextView(this).apply {
            text = "Select engine for this project. Toggle updates selection immediately."
            textSize = 11f
            setTextColor(NC.ON_SURF_VAR)
            typeface = Typeface.MONOSPACE
            setPadding(0, 0, 0, dp(12))
        })

        projectCreateSelectedMethod = LinuxCommandBuilder.currentMethod
        if (projectCreateSelectedMethod == "chroot" && !ProjectPathResolver.isChrootInstalled()) {
            projectCreateSelectedMethod = "proot"
        }

        val prootChip = TextView(this).apply {
            text = "PRoot"
            textSize = 13f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(12), dp(16), dp(12))
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).apply { rightMargin = dp(8) }
        }
        val chrootChip = TextView(this).apply {
            text = "Chroot"
            textSize = 13f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(12), dp(16), dp(12))
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).apply { leftMargin = dp(8) }
        }
        projectCreateProotChip = prootChip
        projectCreateChrootChip = chrootChip
        refreshProjectCreateMethodChips()

        prootChip.setOnClickListener {
            projectCreateSelectedMethod = "proot"
            styleCreateMethodChip(prootChip, true)
            styleCreateMethodChip(chrootChip, false)
            chrootChip.alpha = if (ProjectPathResolver.isChrootInstalled()) 1f else 0.4f
        }
        chrootChip.setOnClickListener {
            if (ProjectPathResolver.isChrootInstalled()) {
                projectCreateSelectedMethod = "chroot"
                styleCreateMethodChip(prootChip, false)
                styleCreateMethodChip(chrootChip, true)
            } else {
                Toast.makeText(this, "Chroot not installed. Install via Settings → Chroot.", Toast.LENGTH_SHORT).show()
            }
        }
        prootChip.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> v.animate().translationX(dp(2).toFloat()).translationY(dp(2).toFloat()).setDuration(60).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate().translationX(0f).translationY(0f).setDuration(60).start()
            }
            false
        }
        chrootChip.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> v.animate().translationX(dp(2).toFloat()).translationY(dp(2).toFloat()).setDuration(60).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate().translationX(0f).translationY(0f).setDuration(60).start()
            }
            false
        }

        val chipRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(4))
        }
        chipRow.addView(prootChip)
        chipRow.addView(chrootChip)
        methodCard.addView(chipRow)
        projectCreateLayout.addView(methodCard)

        // --- Primary Button ---
        projectCreateBtn = primaryButton("CREATE / IMPORT PROJECT") {
            val name = projectNameInput.text.toString().trim()
            val gitUrl = projectGithubInput.text.toString().trim()
            val icon = projectIconInput.text.toString().trim()
            val method = projectCreateSelectedMethod

            if (name.isEmpty()) {
                Toast.makeText(this, "Please enter a project name", Toast.LENGTH_SHORT).show()
                return@primaryButton
            }

            val path = if (gitUrl.isNotEmpty()) {
                val repoName = ProjectManager.repoNameFromUrl(gitUrl)
                "/home/flux/repos/$repoName"
            } else {
                val slug = name.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
                "/home/flux/repos/$slug"
            }

            // Switch isolation to chip method BEFORE clone so all nested builds match
            LinuxCommandBuilder.currentMethod = method
            getSharedPreferences("nativecode_prefs", MODE_PRIVATE)
                .edit().putString("linux_method", method).apply()
            refreshToolCardsForMethod()

            if (gitUrl.isNotEmpty()) {
                projectCreateBtn.isEnabled = false
                projectCreateBtn.alpha = 0.5f

                // Build full-screen clone progress overlay
                val overlayRoot = FrameLayout(this).apply {
                    setBackgroundColor(NC.SURFACE_LOWEST)
                    layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
                }

                val overlayHeader = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        setColor(NC.SURFACE_LOWEST)
                        setStroke(dp(1), Color.parseColor("#3C4A3F"))
                    }
                    setPadding(dp(16), dp(14), dp(16), dp(14))
                    layoutParams = FrameLayout.LayoutParams(MATCH, WRAP).apply { gravity = Gravity.TOP }
                }
                val overlayTitleRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                val pulseIcon = TextView(this).apply {
                    text = "\u25cf"
                    textSize = 12f
                    setTextColor(NC.PRIMARY)
                    setPadding(0, 0, dp(8), 0)
                    tag = "PULSE_ICON"
                }
                val overlayTitle = TextView(this).apply {
                    text = "CLONING REPOSITORY"
                    textSize = 14f
                    setTextColor(NC.ON_SURFACE)
                    typeface = Typeface.MONOSPACE
                    paint.isFakeBoldText = true
                }
                val repoShortName = gitUrl.substringAfterLast("/")
                val overlaySubtitle = TextView(this).apply {
                    text = "\$ git clone $repoShortName"
                    textSize = 12f
                    setTextColor(NC.ON_SURF_VAR)
                    typeface = Typeface.MONOSPACE
                    setPadding(0, dp(4), 0, 0)
                }
                overlayTitleRow.addView(pulseIcon)
                overlayTitleRow.addView(overlayTitle)
                overlayHeader.addView(overlayTitleRow)
                overlayHeader.addView(overlaySubtitle)

                // Indeterminate progress strip
                val progressStrip = FrameLayout(this).apply {
                    layoutParams = FrameLayout.LayoutParams(MATCH, dp(3)).apply {
                        gravity = Gravity.TOP
                        topMargin = dp(62)
                    }
                    setBackgroundColor(Color.parseColor("#22FFFFFF"))
                }
                val progressFill = View(this).apply {
                    setBackgroundColor(NC.PRIMARY)
                    layoutParams = FrameLayout.LayoutParams(0, MATCH)
                    tag = "PROGRESS_FILL"
                }
                progressStrip.addView(progressFill)

                // Animate progress fill indefinitely
                val animRunnable = object : Runnable {
                    private var pos = 0f
                    override fun run() {
                        pos += 0.02f
                        if (pos > 1f) pos = 0f
                        val totalW = progressStrip.width
                        val fillW = (totalW * 0.3f).toInt()
                        val offset = (totalW * pos).toInt()
                        val lp = progressFill.layoutParams as FrameLayout.LayoutParams
                        lp.width = fillW
                        lp.leftMargin = offset
                        progressFill.layoutParams = lp
                        mainHandler.postDelayed(this, 16)
                    }
                }
                mainHandler.post(animRunnable)

                // Log scroll area
                val logScroll = ScrollView(this).apply {
                    layoutParams = FrameLayout.LayoutParams(MATCH, MATCH).apply {
                        topMargin = dp(66)
                        bottomMargin = dp(52)
                    }
                    setBackgroundColor(Color.parseColor("#0A0A0A"))
                }
                val logLayout = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(14), dp(12), dp(14), dp(12))
                }
                logScroll.addView(logLayout)

                // Status bar at bottom
                val statusBar = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        setColor(NC.SURFACE_CONTAINER)
                        setStroke(dp(1), Color.parseColor("#3C4A3F"))
                    }
                    setPadding(dp(14), dp(10), dp(14), dp(10))
                    layoutParams = FrameLayout.LayoutParams(MATCH, dp(48)).apply { gravity = Gravity.BOTTOM }
                }
                val statusTv = TextView(this).apply {
                    text = "Connecting to GitHub..."
                    textSize = 12f
                    setTextColor(NC.PRIMARY)
                    typeface = Typeface.MONOSPACE
                    layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
                }
                val lineTv = TextView(this).apply {
                    text = "0 lines"
                    textSize = 11f
                    setTextColor(NC.ON_SURF_VAR)
                    typeface = Typeface.MONOSPACE
                }
                statusBar.addView(statusTv)
                statusBar.addView(lineTv)

                overlayRoot.addView(overlayHeader)
                overlayRoot.addView(progressStrip)
                overlayRoot.addView(logScroll)
                overlayRoot.addView(statusBar)

                contentFrame.addView(overlayRoot)
                overlayRoot.bringToFront()

                // Animate pulse icon
                val pulseRunnable = object : Runnable {
                    var visible = true
                    override fun run() {
                        visible = !visible
                        pulseIcon.setTextColor(if (visible) NC.PRIMARY else Color.TRANSPARENT)
                        mainHandler.postDelayed(this, 600)
                    }
                }
                mainHandler.post(pulseRunnable)

                var lineCount = 0

                fun appendLog(line: String) {
                    lineCount++
                    val tv = TextView(this).apply {
                        text = line
                        textSize = 11f
                        setTextColor(when {
                            line.contains("error", ignoreCase = true) || line.contains("fatal", ignoreCase = true) -> NC.ERROR
                            line.contains("Cloning") || line.contains("Receiving") || line.contains("Resolving") -> NC.PRIMARY
                            line.contains("done.") || line.contains("complete") -> Color.parseColor("#60F99E")
                            else -> NC.ON_SURFACE
                        })
                        typeface = Typeface.MONOSPACE
                        setPadding(0, dp(2), 0, dp(2))
                    }
                    logLayout.addView(tv)
                    logScroll.post { logScroll.fullScroll(View.FOCUS_DOWN) }
                    lineTv.text = "$lineCount lines"
                    statusTv.text = when {
                        line.contains("Cloning") -> "Cloning objects..."
                        line.contains("Receiving") -> "Receiving objects..."
                        line.contains("Resolving") -> "Resolving deltas..."
                        line.contains("Checking out") -> "Checking out files..."
                        line.contains("done.") -> "Finalizing..."
                        else -> statusTv.text
                    }
                }

                executor.execute {
                    ProjectManager.cloneRepo(
                        this@MainActivity,
                        gitUrl,
                        method = method,
                        onProgress = { line ->
                            mainHandler.post { appendLog(line) }
                        },
                        onDone = { exitCode ->
                            mainHandler.post {
                                mainHandler.removeCallbacks(pulseRunnable)
                                mainHandler.removeCallbacks(animRunnable)
                                contentFrame.removeView(overlayRoot)
                                projectCreateBtn.isEnabled = true
                                projectCreateBtn.alpha = 1f
                                if (exitCode == 0) {
                                    val hostDir = ProjectPathResolver.resolve(this@MainActivity, path, method)
                                    if (!File(hostDir, ".git").isDirectory) {
                                        Toast.makeText(
                                            this@MainActivity,
                                            "Clone reported OK but no .git under ${ProjectPathResolver.methodLabel(method)}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        navigateToPage(ID_PROJECT_CREATE)
                                        return@post
                                    }
                                    addAndOpenProject(name, icon, path, method, skipEnsureDir = true)
                                } else {
                                    Toast.makeText(this@MainActivity, "Clone failed. Check URL or network.", Toast.LENGTH_LONG).show()
                                    navigateToPage(ID_PROJECT_CREATE)
                                }
                            }
                        }
                    )
                }
            } else {
                addAndOpenProject(name, icon, path, method)
            }
        }
        projectCreateLayout.addView(projectCreateBtn)
    }

    private fun addAndOpenProject(
        name: String,
        icon: String,
        path: String,
        method: String = "proot",
        skipEnsureDir: Boolean = false
    ) {
        val projects = getProjects().toMutableList()
        val existingIndex = projects.indexOfFirst { it.path == path }
        val newProj = Project(name, icon, path, linuxMethod = method)
        if (existingIndex >= 0) {
            projects[existingIndex] = newProj
        } else {
            projects.add(newProj)
        }
        saveProjects(projects)

        activeProjectName = name
        activeProjectPath = path
        activeProjectMethod = method

        // Auto-switch global mode to project's mode
        LinuxCommandBuilder.currentMethod = method
        getSharedPreferences("nativecode_prefs", MODE_PRIVATE)
            .edit()
            .putString("linux_method", method)
            .putString("active_project_name", name)
            .putString("active_project_path", path)
            .putString("active_project_method", method)
            .apply()
        refreshToolCardsForMethod()

        // Non-git create only: mkdir under the selected method (avoid empty shell over real clone)
        if (!skipEnsureDir) {
            ProjectManager.ensureDir(this, path, method = method)
        }

        Toast.makeText(this, "Project opened: $name", Toast.LENGTH_SHORT).show()

        if (pageStack.isEmpty() || pageStack.peek() != ID_PROJECT_WORKSPACE) {
            pageStack.push(ID_PROJECT_WORKSPACE)
        }
        navigateToPage(ID_PROJECT_WORKSPACE)
    }

    private fun buildProjectsListLayout() {
        projectsListContainer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
            visibility = View.GONE
            setBackgroundColor(NC.BG)
        }
        projectsListScrollView = ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
            clipToPadding = false
            setPadding(0, 0, 0, dp(100))
        }
        projectsListLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        projectsListScrollView.addView(projectsListLayout)
        projectsListContainer.addView(projectsListScrollView)

        val fab = createHoveringNewProjectFab()
        val fabParams = FrameLayout.LayoutParams(WRAP, WRAP).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            rightMargin = dp(20)
            bottomMargin = dp(24)
        }
        projectsListContainer.addView(fab, fabParams)
    }

    private fun populateProjectsList() {
        projectsListLayout.removeAllViews()

        val headerCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(16))
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }

        val titleTv = TextView(this).apply {
            text = "ALL PROJECTS"
            textSize = 24f
            setTextColor(NC.PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }

        val count = getProjects().size
        val countBadge = TextView(this).apply {
            text = "$count PROJECTS"
            textSize = 11f
            setTextColor(NC.PRIMARY)
            typeface = Typeface.MONOSPACE
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(NC.SURFACE_LOWEST)
                setStroke(dp(1), Color.parseColor("#3360F99E"))
            }
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }

        topRow.addView(titleTv)
        topRow.addView(countBadge)

        val subTitleTv = TextView(this).apply {
            text = "// DEBIAN DISTRO REPOSITORY • DEPLOYMENT HOST"
            textSize = 11f
            setTextColor(NC.ON_SURF_VAR)
            typeface = Typeface.MONOSPACE
            setPadding(0, dp(4), 0, dp(12))
        }

        val divider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, dp(1)).apply { bottomMargin = dp(16) }
            setBackgroundColor(NC.OUTLINE_VAR)
        }

        headerCol.addView(topRow)
        headerCol.addView(subTitleTv)
        headerCol.addView(divider)

        projectsListLayout.addView(headerCol)

        val list = getProjects()
        if (list.isEmpty()) {
            val emptyCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = cyberBrutalistBg(
                    fillColor = NC.SURFACE_LOW,
                    strokeColor = NC.OUTLINE_VAR,
                    shadowColor = NC.SHADOW_DARK,
                    offsetDp = 6,
                    cornerRadiusDp = 0
                )
                setPadding(dp(24), dp(32), dp(24), dp(32))
                layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(16) }
            }

            val emptyTitle = TextView(this).apply {
                text = "NO PROJECTS INITIALIZED"
                textSize = 15f
                setTextColor(Color.WHITE)
                typeface = Typeface.MONOSPACE
                gravity = Gravity.CENTER
            }

            val emptyDesc = TextView(this).apply {
                text = "No projects created in Debian rootfs yet.\nTap the '+ NEW PROJECT' button to initialize your first project repository."
                textSize = 12f
                setTextColor(NC.ON_SURF_VAR)
                typeface = Typeface.MONOSPACE
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, dp(16))
            }

            val createBtn = primaryButton(" + INITIALIZE FIRST PROJECT") {
                if (pageStack.isEmpty() || pageStack.peek() != ID_PROJECT_CREATE) {
                    pageStack.push(ID_PROJECT_CREATE)
                }
                navigateToPage(ID_PROJECT_CREATE)
            }

            emptyCard.addView(emptyTitle)
            emptyCard.addView(emptyDesc)
            emptyCard.addView(createBtn)

            projectsListLayout.addView(emptyCard)
        } else {
            for (i in list.indices step 2) {
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply {
                        bottomMargin = dp(16)
                    }
                }

                val p1 = list[i]
                val card1 = projectGridCard(p1.name, p1.path, "Tap to open", p1.icon, p1.linuxMethod).apply {
                    layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).apply {
                        rightMargin = dp(8)
                    }
                    setOnClickListener {
                        markProjectOpened(p1.path)
                        activeProjectName = p1.name
                        activeProjectPath = p1.path
                        applyProjectIsolation(p1.path, p1.linuxMethod)
                        Toast.makeText(this@MainActivity, "Project opened: ${p1.name}", Toast.LENGTH_SHORT).show()
                        if (pageStack.isEmpty() || pageStack.peek() != ID_PROJECT_WORKSPACE) {
                            pageStack.push(ID_PROJECT_WORKSPACE)
                        }
                        navigateToPage(ID_PROJECT_WORKSPACE)
                    }
                }
                row.addView(card1)

                if (i + 1 < list.size) {
                    val p2 = list[i + 1]
                    val card2 = projectGridCard(p2.name, p2.path, "Tap to open", p2.icon, p2.linuxMethod).apply {
                        layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).apply {
                            leftMargin = dp(8)
                        }
                        setOnClickListener {
                            markProjectOpened(p2.path)
                            activeProjectName = p2.name
                            activeProjectPath = p2.path
                            applyProjectIsolation(p2.path, p2.linuxMethod)
                            Toast.makeText(this@MainActivity, "Project opened: ${p2.name}", Toast.LENGTH_SHORT).show()
                            if (pageStack.isEmpty() || pageStack.peek() != ID_PROJECT_WORKSPACE) {
                                pageStack.push(ID_PROJECT_WORKSPACE)
                            }
                            navigateToPage(ID_PROJECT_WORKSPACE)
                        }
                    }
                    row.addView(card2)
                } else {
                    val dummySpacer = View(this).apply {
                        layoutParams = LinearLayout.LayoutParams(0, MATCH, 1f).apply {
                            leftMargin = dp(8)
                        }
                    }
                    row.addView(dummySpacer)
                }

                projectsListLayout.addView(row)
            }
        }
    }


    private fun refreshWorkspaceDirTree() {
        if (!::workspaceDirTreeLayout.isInitialized) return
        workspaceDirTreeLayout.removeAllViews()
        val projectHostDir = activeProjectHostDir()
        if (projectHostDir.exists() && projectHostDir.isDirectory) {
            if (dirSearchQuery.isEmpty()) {
                renderDirectoryTree(projectHostDir, workspaceDirTreeLayout, 0)
            } else {
                renderSearchDirectoryResults(projectHostDir, dirSearchQuery, workspaceDirTreeLayout)
            }
            if (workspaceDirTreeLayout.childCount == 0) {
                val emptyCard = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    background = cyberBrutalistBg(
                        fillColor = NC.SURFACE_LOW,
                        strokeColor = Color.parseColor("#3c4a3f"),
                        shadowColor = NC.SHADOW_DARK,
                        offsetDp = 6,
                        cornerRadiusDp = 0,
                        rightFaceColor = Color.parseColor("#3c4a3f")
                    )
                    setPadding(dp(24), dp(32), dp(24), dp(32))
                    layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply {
                        topMargin = dp(16)
                        leftMargin = dp(8)
                        rightMargin = dp(8)
                    }
                }

                val emptyIcon = ImageView(this).apply {
                    setImageResource(R.drawable.ic_folder_special)
                    setColorFilter(NC.PRIMARY)
                    layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply {
                        bottomMargin = dp(12)
                    }
                }

                val emptyTitle = TextView(this).apply {
                    text = "NO FILES IN DIRECTORY"
                    textSize = 15f
                    setTextColor(Color.WHITE)
                    typeface = Typeface.MONOSPACE
                    paint.isFakeBoldText = true
                    gravity = Gravity.CENTER
                }

                val emptyDesc = TextView(this).apply {
                    text = "Workspace folder is currently empty:\n${projectHostDir.absolutePath}\n\nUse terminal to create files or refresh directory below."
                    textSize = 11f
                    setTextColor(NC.ON_SURF_VAR)
                    typeface = Typeface.MONOSPACE
                    gravity = Gravity.CENTER
                    setPadding(0, dp(8), 0, dp(16))
                }

                val refreshBtn = primaryButton("REFRESH DIRECTORY") {
                    refreshWorkspaceDirTree()
                }

                emptyCard.addView(emptyIcon)
                emptyCard.addView(emptyTitle)
                emptyCard.addView(emptyDesc)
                emptyCard.addView(refreshBtn)

                workspaceDirTreeLayout.addView(emptyCard)
            }
        } else {
            val emptyCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = cyberBrutalistBg(
                    fillColor = NC.SURFACE_LOW,
                    strokeColor = Color.parseColor("#93000a"),
                    shadowColor = NC.SHADOW_DARK,
                    offsetDp = 6,
                    cornerRadiusDp = 0,
                    rightFaceColor = Color.parseColor("#93000a")
                )
                setPadding(dp(24), dp(32), dp(24), dp(32))
                layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply {
                    topMargin = dp(16)
                    leftMargin = dp(8)
                    rightMargin = dp(8)
                }
            }

            val emptyIcon = ImageView(this).apply {
                setImageResource(R.drawable.ic_folder)
                setColorFilter(Color.parseColor("#ffb4ab"))
                layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply {
                    bottomMargin = dp(12)
                }
            }

            val emptyTitle = TextView(this).apply {
                text = "DIRECTORY NOT FOUND"
                textSize = 15f
                setTextColor(Color.WHITE)
                typeface = Typeface.MONOSPACE
                paint.isFakeBoldText = true
                gravity = Gravity.CENTER
            }

            val emptyDesc = TextView(this).apply {
                text = "Workspace folder does not exist:\n${projectHostDir.absolutePath}\n\nTap below to initialize directory."
                textSize = 11f
                setTextColor(Color.parseColor("#ffb4ab"))
                typeface = Typeface.MONOSPACE
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, dp(16))
            }

            val createDirBtn = primaryButton("CREATE DIRECTORY") {
                try {
                    projectHostDir.mkdirs()
                    refreshWorkspaceDirTree()
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Failed to create directory", Toast.LENGTH_SHORT).show()
                }
            }

            emptyCard.addView(emptyIcon)
            emptyCard.addView(emptyTitle)
            emptyCard.addView(emptyDesc)
            emptyCard.addView(createDirBtn)

            workspaceDirTreeLayout.addView(emptyCard)
        }
    }

    private fun renderSearchDirectoryResults(rootDir: File, query: String, container: LinearLayout) {
        val matches = mutableListOf<File>()
        fun scan(dir: File) {
            val files = dir.listFiles() ?: return
            for (f in files) {
                if (f.name.startsWith(".")) continue
                val relPath = if (f.absolutePath.startsWith(rootDir.absolutePath)) {
                    f.absolutePath.removePrefix(rootDir.absolutePath).trimStart('/')
                } else {
                    f.name
                }
                if (f.name.contains(query, ignoreCase = true) || relPath.contains(query, ignoreCase = true)) {
                    matches.add(f)
                }
                if (f.isDirectory) {
                    scan(f)
                }
            }
        }
        scan(rootDir)
        matches.sortWith(compareBy({ !it.isDirectory }, { it.name }))

        if (matches.isEmpty()) {
            val emptyCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = cyberBrutalistBg(
                    fillColor = NC.SURFACE_LOW,
                    strokeColor = NC.BORDER,
                    shadowColor = NC.SHADOW_DARK,
                    offsetDp = 4,
                    cornerRadiusDp = 0,
                    rightFaceColor = NC.BORDER
                )
                setPadding(dp(20), dp(24), dp(20), dp(24))
                layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(8) }
            }
            val emptyTv = TextView(this).apply {
                text = "NO FILES MATCHING \"$query\""
                textSize = 13f
                setTextColor(NC.ON_SURF_VAR)
                typeface = Typeface.MONOSPACE
                gravity = Gravity.CENTER
            }
            emptyCard.addView(emptyTv)
            container.addView(emptyCard)
            return
        }

        val searchHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(8), dp(4), dp(8))
        }
        val searchHeaderTitle = TextView(this).apply {
            text = "SEARCH RESULTS // ${matches.size} MATCHES FOR \"$query\""
            textSize = 10f
            setTextColor(NC.PRIMARY)
            typeface = Typeface.MONOSPACE
            paint.isFakeBoldText = true
        }
        searchHeaderRow.addView(searchHeaderTitle)
        container.addView(searchHeaderRow)

        val resultsCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cyberBrutalistBg(
                fillColor = NC.SURFACE_LOW,
                strokeColor = NC.BORDER,
                shadowColor = NC.SHADOW_DARK,
                offsetDp = 6,
                cornerRadiusDp = 0,
                rightFaceColor = NC.BORDER
            )
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }

        for ((idx, file) in matches.withIndex()) {
            val relPath = if (file.absolutePath.startsWith(rootDir.absolutePath)) {
                file.absolutePath.removePrefix(rootDir.absolutePath).trimStart('/')
            } else {
                file.name
            }
            val isDir = file.isDirectory

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                setOnClickListener {
                    if (isDir) {
                        expandedFolders.add(file.absolutePath)
                        dirSearchQuery = ""
                        refreshWorkspaceDirTree()
                    } else {
                        showFileViewer(file.absolutePath, ID_PROJECT_DIR_TREE)
                    }
                }
                setOnLongClickListener {
                    copyToClipboard("File Path", file.absolutePath)
                    true
                }
                setOnTouchListener { v, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> v.setBackgroundColor(NC.SURFACE_HIGH)
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.setBackgroundColor(Color.TRANSPARENT)
                    }
                    false
                }
            }

            val iconIv = ImageView(this).apply {
                setImageResource(if (isDir) R.drawable.ic_folder else R.drawable.ic_extension)
                setColorFilter(if (isDir) NC.PRIMARY else NC.ON_SURF_VAR)
                layoutParams = LinearLayout.LayoutParams(dp(20), dp(20)).apply { rightMargin = dp(10) }
            }

            val textCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
            }

            val nameTv = TextView(this).apply {
                text = file.name
                textSize = 13f
                setTextColor(NC.ON_SURFACE)
                typeface = Typeface.DEFAULT_BOLD
            }
            val pathTv = TextView(this).apply {
                text = relPath
                textSize = 10f
                setTextColor(NC.ON_SURF_VAR)
                typeface = Typeface.MONOSPACE
            }
            textCol.addView(nameTv)
            textCol.addView(pathTv)

            val copyPathBtn = TextView(this).apply {
                text = "COPY PATH"
                textSize = 9f
                setTextColor(NC.PRIMARY)
                typeface = Typeface.DEFAULT_BOLD
                background = roundedBg(NC.SURFACE_VAR, NC.BORDER, dp(4))
                setPadding(dp(8), dp(4), dp(8), dp(4))
                setOnClickListener {
                    copyToClipboard("File Path", file.absolutePath)
                }
            }

            row.addView(iconIv)
            row.addView(textCol)
            row.addView(copyPathBtn)
            resultsCard.addView(row)

            if (idx < matches.size - 1) {
                val divider = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(MATCH, dp(1)).apply {
                        leftMargin = dp(12)
                        rightMargin = dp(12)
                    }
                    setBackgroundColor(NC.BORDER_VAR)
                }
                resultsCard.addView(divider)
            }
        }

        container.addView(resultsCard)
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
            val indentPx = dp(16 * depth + 8)
            val isDir = file.isDirectory

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(indentPx, dp(9), dp(12), dp(9))
                background = if (isDir) GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(if (isExpanded) NC.SURFACE_CONTAINER else Color.TRANSPARENT)
                    setStroke(0, Color.TRANSPARENT)
                } else null
                setOnClickListener {
                    if (isDir) {
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
                setOnLongClickListener {
                    copyToClipboard("File Path", file.absolutePath)
                    true
                }
                setOnTouchListener { v, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> v.setBackgroundColor(NC.SURFACE_HIGH)
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.background = if (isDir && isExpanded) GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            setColor(NC.SURFACE_CONTAINER)
                        } else null
                    }
                    false
                }
            }

            // Left accent bar for directories
            if (depth == 0 && isDir) {
                val accent = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(2), ViewGroup.LayoutParams.MATCH_PARENT)
                    setBackgroundColor(if (isExpanded) NC.PRIMARY else NC.OUTLINE_VAR)
                }
                row.addView(accent)
                row.addView(spacer(6).apply { layoutParams = LinearLayout.LayoutParams(dp(6), ViewGroup.LayoutParams.MATCH_PARENT) })
            }
            
            val indicatorTv = TextView(this).apply {
                if (isDir) {
                    typeface = materialTf
                    text = if (isExpanded) "\uE313" else "\uE315"
                    textSize = 16f
                    setTextColor(if (isExpanded) NC.PRIMARY else NC.ON_SURF_VAR)
                    setPadding(0, 0, dp(4), 0)
                } else {
                    text = ""
                    setPadding(0, 0, dp(20), 0)
                }
            }
            
            val iconTv = TextView(this).apply {
                typeface = materialTf
                val icon = when {
                    isDir -> {
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
                setTextColor(if (isDir) NC.PRIMARY else NC.SECONDARY)
            }
            
            val nameTv = TextView(this).apply {
                text = file.name
                textSize = 14f
                setTextColor(if (isDir) NC.ON_SURFACE else NC.ON_SURF_VAR)
                layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
                if (isDir) {
                    typeface = Typeface.DEFAULT_BOLD
                } else {
                    typeface = Typeface.MONOSPACE
                }
            }

            val sizeOrChevron: View = if (isDir) {
                ImageView(this).apply {
                    setImageResource(R.drawable.ic_chevron_right)
                    setColorFilter(if (isExpanded) NC.PRIMARY else NC.OUTLINE_VAR)
                    layoutParams = LinearLayout.LayoutParams(dp(16), dp(16))
                }
            } else {
                val ext = file.extension.uppercase()
                TextView(this).apply {
                    text = if (ext.isNotEmpty()) ext else "FILE"
                    textSize = 9f
                    setTextColor(NC.ON_SURF_VAR)
                    typeface = Typeface.MONOSPACE
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        setColor(NC.SURFACE_HIGHEST)
                        setStroke(dp(1), NC.OUTLINE_VAR)
                    }
                    setPadding(dp(4), dp(2), dp(4), dp(2))
                }
            }
            
            row.addView(indicatorTv)
            row.addView(iconTv)
            row.addView(nameTv)
            row.addView(sizeOrChevron)
            container.addView(row)

            if (depth == 0) {
                val rowDiv = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(MATCH, dp(1))
                    setBackgroundColor(NC.SURFACE_HIGH)
                }
                container.addView(rowDiv)
            }
            
            if (isDir && isExpanded) {
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
            val gitCmd = "cd $activeProjectPath && git status --porcelain"
            val (lcArgs, lcEnv) = LinuxCommandBuilder.build(this, gitCmd)
            val pb = ProcessBuilder(*lcArgs)
            val env = pb.environment()
            env.putAll(lcEnv)
            env["GIT_PAGER"] = "cat"
            env["GIT_TERMINAL_PROMPT"] = "0"
            env["TERM"] = "dumb"
            pb.redirectErrorStream(true)
            try {
                val proc = pb.start()
                val lines = ArrayList<String>()
                proc.inputStream.bufferedReader().useLines { seq ->
                    seq.forEach { lines.add(it) }
                }
                proc.waitFor()
                Log.d("GitDiff", "refreshGitDiffTree: read ${lines.size} lines")
                for (line in lines) {
                    Log.d("GitDiff", "  line: '$line' (len=${line.length})")
                }
                
                val filteredLines = lines.filter { line ->
                    line.length > 3 && line[2] == ' ' && 
                    (line[0] in listOf('M', 'A', 'D', 'R', 'C', 'U', '?', '!', ' ')) &&
                    (line[1] in listOf('M', 'A', 'D', 'R', 'C', 'U', '?', '!', ' '))
                }

                mainHandler.post {
                    workspaceGitDiffLayout.removeAllViews()
                    if (filteredLines.isEmpty()) {
                        val noChangesCard = LinearLayout(this@MainActivity).apply {
                            orientation = LinearLayout.VERTICAL
                            gravity = Gravity.CENTER
                            background = cyberBrutalistBg(
                                fillColor = NC.SURFACE_LOW,
                                strokeColor = NC.OUTLINE_VAR,
                                shadowColor = NC.SHADOW_DARK,
                                offsetDp = 8,
                                cornerRadiusDp = 0
                            )
                            setPadding(dp(16), dp(24), dp(16), dp(24))
                            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(8) }
                        }
                        val noChangesIcon = ImageView(this@MainActivity).apply {
                            setImageResource(R.drawable.ic_check_circle)
                            setColorFilter(NC.PRIMARY)
                            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply {
                                gravity = Gravity.CENTER_HORIZONTAL
                                bottomMargin = dp(8)
                            }
                        }
                        val noChanges = TextView(this@MainActivity).apply {
                            text = "NO CHANGES DETECTED"
                            textSize = 14f
                            setTextColor(NC.ON_SURFACE)
                            typeface = Typeface.MONOSPACE
                            gravity = Gravity.CENTER
                            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(6) }
                        }
                        val noChangesSub = TextView(this@MainActivity).apply {
                            text = "Working tree is clean"
                            textSize = 11f
                            setTextColor(NC.ON_SURF_VAR)
                            typeface = Typeface.MONOSPACE
                            gravity = Gravity.CENTER
                            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(4) }
                        }
                        noChangesCard.addView(noChangesIcon)
                        noChangesCard.addView(noChanges)
                        noChangesCard.addView(noChangesSub)
                        workspaceGitDiffLayout.addView(noChangesCard)
                    } else {
                        for (line in filteredLines) {
                            if (line.trim().isEmpty()) continue
                            val status = line.take(2).trim()
                            val file = line.substring(3)
                            val statusChar = status.firstOrNull() ?: ' '
                            val accentColor = when (statusChar) {
                                'M' -> NC.PRIMARY_CON       // Modified — green
                                'A' -> Color.parseColor("#43e188")   // Added — bright green
                                'D' -> NC.ERROR_CON         // Deleted — red
                                'R', 'C' -> NC.TERTIARY_CON // Renamed/Copied — amber
                                '?' -> NC.SECONDARY         // Untracked — grey
                                else -> NC.SEC_CON
                            }
                            val statusLabel = when (statusChar) {
                                'M' -> "MOD"
                                'A' -> "ADD"
                                'D' -> "DEL"
                                'R' -> "REN"
                                'C' -> "CPY"
                                '?' -> "NEW"
                                '!' -> "IGN"
                                else -> status.trim().ifEmpty { "---" }
                            }
                            val row = LinearLayout(this@MainActivity).apply {
                                orientation = LinearLayout.HORIZONTAL
                                gravity = Gravity.CENTER_VERTICAL
                                background = cyberBrutalistBg(
                                    fillColor = NC.SURFACE_LOW,
                                    strokeColor = NC.OUTLINE_VAR,
                                    shadowColor = NC.SHADOW_DARK,
                                    offsetDp = 8,
                                    cornerRadiusDp = 0
                                )
                                setPadding(dp(12), dp(10), dp(12), dp(10))
                                layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply {
                                    bottomMargin = dp(8)
                                }
                                setOnClickListener {
                                    showDiffViewer(file, ID_PROJECT_GIT_DIFF)
                                }
                                setOnTouchListener { v, event ->
                                    when (event.action) {
                                        MotionEvent.ACTION_DOWN -> v.background = cyberBrutalistBg(
                                            fillColor = NC.SURFACE_HIGH,
                                            strokeColor = NC.OUTLINE_VAR,
                                            shadowColor = NC.SHADOW_DARK,
                                            offsetDp = 8,
                                            cornerRadiusDp = 0
                                        )
                                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.background = cyberBrutalistBg(
                                            fillColor = NC.SURFACE_LOW,
                                            strokeColor = NC.OUTLINE_VAR,
                                            shadowColor = NC.SHADOW_DARK,
                                            offsetDp = 8,
                                            cornerRadiusDp = 0
                                        )
                                    }
                                    false
                                }
                            }
                            // Left accent bar by status
                            val accentBar = View(this@MainActivity).apply {
                                layoutParams = LinearLayout.LayoutParams(dp(3), ViewGroup.LayoutParams.MATCH_PARENT).apply { rightMargin = dp(10) }
                                setBackgroundColor(accentColor)
                            }
                            val statusBadge = TextView(this@MainActivity).apply {
                                text = statusLabel
                                textSize = 9f
                                setTextColor(NC.SURFACE_LOWEST)
                                typeface = Typeface.MONOSPACE
                                paint.isFakeBoldText = true
                                background = GradientDrawable().apply {
                                    shape = GradientDrawable.RECTANGLE
                                    setColor(accentColor)
                                }
                                setPadding(dp(5), dp(3), dp(5), dp(3))
                                layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).apply { rightMargin = dp(10) }
                            }
                            val fileTv = TextView(this@MainActivity).apply {
                                text = file
                                textSize = 12f
                                setTextColor(NC.ON_SURFACE)
                                typeface = Typeface.MONOSPACE
                                layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
                                maxLines = 1
                                ellipsize = android.text.TextUtils.TruncateAt.END
                            }
                            val chevron = ImageView(this@MainActivity).apply {
                                setImageResource(R.drawable.ic_chevron_right)
                                setColorFilter(NC.OUTLINE)
                                layoutParams = LinearLayout.LayoutParams(dp(16), dp(16))
                            }
                            row.addView(accentBar)
                            row.addView(statusBadge)
                            row.addView(fileTv)
                            row.addView(chevron)
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
        if (type == "codex" && LinuxCommandBuilder.currentMethod == "chroot") {
            Toast.makeText(this, "Codex unavailable in chroot", Toast.LENGTH_SHORT).show()
            return
        }

        maybeToastHeavyToolLaunch(type)
        val nld = applicationInfo.nativeLibraryDir
        val shell = File(nld, "libbash.so").absolutePath
        val cwd = File(filesDir, "home").absolutePath
        val shellCmd = ChrootCommandBuilder.buildToolShellCommand(this, type, activeProjectPath)
        val (args, envMap) = LinuxCommandBuilder.build(this, shellCmd)
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
                executor.execute {
                    session.emulator?.paste(text) ?: session.write(text)
                }
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

        val isChroot = LinuxCommandBuilder.currentMethod == "chroot"
        val sessionExec = if (isChroot) ChrootCommandBuilder.SESSION_EXEC else shell
        val session = TerminalSession(sessionExec, cwd, args, env, 10000, sessionClient)
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
        forceTerminalResize(workspaceTerminalView)
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

    private fun showNewTerminalDropdown(anchorView: View, onSelect: ((String) -> Unit)? = null) {
        val tools = listOf(
            Pair("Debian Shell", "shell"),
            Pair("opencode", "opencode"),
            Pair("codex", "codex"),
            Pair("agy", "agy"),
            Pair("claude-code", "claude-code"),
            Pair("qwen-code", "qwen-code"),
            Pair("grok", "grok"),
            Pair("kiro", "kiro")
        ).let { list ->
            if (LinuxCommandBuilder.currentMethod == "chroot")
                list.filter { it.second != "codex" }
            else list
        }

        val popupView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = cyberBrutalistBg(
                fillColor = NC.SURFACE_CONTAINER,
                strokeColor = NC.PRIMARY,
                shadowColor = NC.SHADOW_DARK,
                offsetDp = 4,
                cornerRadiusDp = 0,
                rightFaceColor = NC.OUTLINE_VAR
            )
        }

        val popupWindow = PopupWindow(
            popupView,
            dp(200),
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            isFocusable = true
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            elevation = dp(8).toFloat()
        }

        for (item in tools) {
            val label = item.first
            val type = item.second

            val itemLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = roundedBg(NC.SURFACE_CONTAINER, Color.TRANSPARENT, 0)

                val iconView = ImageView(this@MainActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(18), dp(18)).apply {
                        rightMargin = dp(10)
                        gravity = Gravity.CENTER_VERTICAL
                    }
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }

                val filename = if (type == "qwen-code") "qwen-code.webp" else "$type.png"
                try {
                    assets.open("images/cli/$filename").use { input ->
                        val bmp = android.graphics.BitmapFactory.decodeStream(input)
                        if (bmp != null) {
                            iconView.setImageBitmap(bmp)
                        } else {
                            iconView.setImageResource(R.drawable.ic_terminal_thick)
                            iconView.setColorFilter(NC.PRIMARY)
                        }
                    }
                } catch (_: Exception) {
                    iconView.setImageResource(R.drawable.ic_terminal_thick)
                    iconView.setColorFilter(NC.PRIMARY)
                }

                val labelTv = TextView(this@MainActivity).apply {
                    text = label
                    textSize = 13f
                    setTextColor(NC.ON_SURFACE)
                    typeface = Typeface.MONOSPACE
                }

                addView(iconView)
                addView(labelTv)

                setOnTouchListener { v, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            v.background = roundedBg(NC.SURFACE_HIGH, NC.PRIMARY, 0)
                            labelTv.setTextColor(NC.PRIMARY)
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            v.background = roundedBg(NC.SURFACE_CONTAINER, Color.TRANSPARENT, 0)
                            labelTv.setTextColor(NC.ON_SURFACE)
                        }
                    }
                    false
                }

                setOnClickListener {
                    popupWindow.dismiss()
                    if (onSelect != null) {
                        onSelect.invoke(type)
                    } else {
                        createWorkspaceTerminalTab(type)
                    }
                }
            }

            popupView.addView(itemLayout)
        }

        popupWindow.showAsDropDown(anchorView, 0, dp(4))
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
                setPadding(dp(10), dp(6), dp(10), dp(6))
                background = cyberBrutalistBg(
                    fillColor = if (isSelected) NC.SURFACE_CONTAINER else NC.SURFACE_LOW,
                    strokeColor = if (isSelected) NC.PRIMARY else NC.OUTLINE_VAR,
                    shadowColor = NC.SHADOW_DARK,
                    offsetDp = if (isSelected) 3 else 2,
                    cornerRadiusDp = 0,
                    rightFaceColor = NC.OUTLINE_VAR
                )
                layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).apply {
                    rightMargin = dp(6)
                    topMargin = dp(2)
                    bottomMargin = dp(4)
                }
                setOnClickListener {
                    switchWorkspaceTab(i)
                }
            }

            val iconView = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(16), dp(16)).apply {
                    rightMargin = dp(6)
                    gravity = Gravity.CENTER_VERTICAL
                }
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            val filename = if (tabName == "qwen-code") "qwen-code.webp" else "$tabName.png"
            try {
                assets.open("images/cli/$filename").use { input ->
                    val bmp = android.graphics.BitmapFactory.decodeStream(input)
                    if (bmp != null) {
                        iconView.setImageBitmap(bmp)
                    } else {
                        iconView.setImageResource(R.drawable.ic_terminal_thick)
                        iconView.setColorFilter(if (isSelected) NC.PRIMARY else NC.ON_SURF_VAR)
                    }
                }
            } catch (_: Exception) {
                iconView.setImageResource(R.drawable.ic_terminal_thick)
                iconView.setColorFilter(if (isSelected) NC.PRIMARY else NC.ON_SURF_VAR)
            }
            
            val titleTv = TextView(this).apply {
                text = "${i + 1}. $tabName"
                textSize = 12f
                setTextColor(if (isSelected) NC.PRIMARY else NC.ON_SURFACE)
                typeface = Typeface.MONOSPACE
            }
            
            val closeTv = ImageView(this).apply {
                setImageResource(R.drawable.ic_close)
                setColorFilter(if (isSelected) NC.PRIMARY else NC.ON_SURF_VAR)
                setPadding(dp(2), dp(2), dp(2), dp(2))
                layoutParams = LinearLayout.LayoutParams(dp(18), dp(18)).apply {
                    leftMargin = dp(6)
                    gravity = Gravity.CENTER_VERTICAL
                }
                setOnClickListener {
                    closeWorkspaceTab(i)
                }
            }
            
            tab.addView(iconView)
            tab.addView(titleTv)
            tab.addView(closeTv)
            workspaceTabBar.addView(tab)
        }
        
        if (workspaceSessions.size < 10) {
            val addTabBtn = ImageView(this).apply {
                setImageResource(R.drawable.ic_add)
                setColorFilter(NC.PRIMARY)
                setPadding(dp(6), dp(6), dp(6), dp(6))
                background = cyberBrutalistBg(
                    fillColor = NC.SURFACE_CONTAINER,
                    strokeColor = NC.PRIMARY,
                    shadowColor = NC.SHADOW_DARK,
                    offsetDp = 3,
                    cornerRadiusDp = 0,
                    rightFaceColor = NC.OUTLINE_VAR
                )
                layoutParams = LinearLayout.LayoutParams(dp(30), dp(30)).apply {
                    leftMargin = dp(4)
                    topMargin = dp(2)
                    bottomMargin = dp(4)
                    gravity = Gravity.CENTER_VERTICAL
                }
                setOnClickListener {
                    showNewTerminalDropdown(this)
                }
            }
            workspaceTabBar.addView(addTabBtn)
        }
    }

    private fun buildProjectWorkspaceLayout() {
        projectWorkspaceContainer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
            visibility = View.GONE
            setBackgroundColor(NC.BG)
        }
        projectWorkspaceLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
            setBackgroundColor(NC.BG)
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(NC.SURFACE_LOWEST)
                setStroke(dp(1), Color.parseColor("#3C4A3F"))
            }
            setPadding(dp(12), dp(8), dp(12), dp(8))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }

        val backWorkspaceBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_arrow_back)
            setColorFilter(NC.ON_SURFACE)
            setPadding(dp(6), dp(6), dp(6), dp(6))
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
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

        val projSysPlate = createSystemTelemetryPlate("PROJ_HEADER_BAT", "PROJ_HEADER_CPU", "PROJ_HEADER_RAM").apply {
            layoutParams = LinearLayout.LayoutParams(WRAP, dp(34)).apply {
                rightMargin = dp(8)
            }
        }

        val workspaceExtraKeysBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_keyboard)
            setColorFilter(if (showExtraKeys) NC.PRIMARY else NC.ON_SURF_VAR)
            setPadding(dp(6), dp(6), dp(6), dp(6))
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply { rightMargin = dp(4) }
            setOnClickListener {
                setExtraKeysEnabled(!showExtraKeys)
                setColorFilter(if (showExtraKeys) NC.PRIMARY else NC.ON_SURF_VAR)
            }
        }

        topBar.addView(backWorkspaceBtn)
        topBar.addView(workspaceProjectIconIv)
        topBar.addView(workspaceProjectNameTv)
        topBar.addView(projSysPlate)
        topBar.addView(workspaceExtraKeysBtn)
        projectWorkspaceLayout.addView(topBar)

        workspaceTabBarScroll = HorizontalScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(NC.SURFACE_LOWEST)
                setStroke(dp(1), NC.OUTLINE_VAR)
            }
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
        workspaceTerminalView.setTextSize(workspaceFontSize)
        try {
            val tf = Typeface.createFromAsset(assets, "fonts/font.ttf")
            workspaceTerminalView.setTypeface(tf)
        } catch (e: Exception) {}
        
        val workspaceViewClient = object : TerminalViewClient {
            override fun onScale(scale: Float): Float {
                if (scale < 0.9f || scale > 1.1f) {
                    val nextSize = (workspaceFontSize * scale).toInt()
                    setGlobalTerminalZoom(nextSize)
                    return 1.0f
                }
                return scale
            }
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
            override fun readControlKey(): Boolean = wsModState.readCtrl(true)
            override fun readAltKey(): Boolean     = wsModState.readAlt(true)
            override fun readShiftKey(): Boolean   = wsModState.readShift(true)
            override fun readFnKey(): Boolean      = wsModState.readFn(true)
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
        termViewContainer.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            forceTerminalResize(if (::workspaceTerminalView.isInitialized) workspaceTerminalView else null)
        }
        workspaceTerminalContainer.addView(termViewContainer)

        workspaceKeyboardToolbar = buildSpecialKeysToolbar(
            tvRef = { if (::workspaceTerminalView.isInitialized) workspaceTerminalView else null },
            sessionRef = { if (activeWorkspaceTabIndex >= 0 && activeWorkspaceTabIndex < workspaceSessions.size) workspaceSessions[activeWorkspaceTabIndex] else null },
            modState = wsModState,
            onPickImage = { wsImagePickerLauncher.launch("image/*") }
        ).apply {
            visibility = if (showExtraKeys) View.VISIBLE else View.GONE
        }
        workspaceTerminalContainer.addView(workspaceKeyboardToolbar)

        centerFrame.addView(workspaceTerminalContainer)

        workspaceHubLayout = ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
            clipToPadding = false
            setPadding(0, 0, 0, dp(16))
            setBackgroundColor(NC.BG)
            isVerticalScrollBarEnabled = false
        }

        val hubInner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(16))
        }
        (workspaceHubLayout as ScrollView).addView(hubInner)

        // Hub header
        val hubHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(4))
        }
        val hubTitleTv = TextView(this).apply {
            text = "SELECT AI TOOL"
            textSize = 18f
            setTextColor(NC.PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        val hubSubTitleTv = TextView(this).apply {
            text = "// LAUNCH WORKSPACE"
            textSize = 10f
            setTextColor(NC.ON_SURF_VAR)
            typeface = Typeface.MONOSPACE
            gravity = Gravity.END
        }
        hubHeaderRow.addView(hubTitleTv)
        hubHeaderRow.addView(hubSubTitleTv)
        val hubDivider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, dp(1)).apply { bottomMargin = dp(12) }
            setBackgroundColor(NC.OUTLINE_VAR)
        }
        hubInner.addView(hubHeaderRow)
        hubInner.addView(hubDivider)

        val hubToolsHost = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        workspaceHubToolsHost = hubToolsHost
        populateWorkspaceHubTools(hubToolsHost)
        hubInner.addView(hubToolsHost)

        centerFrame.addView(workspaceHubLayout)
        mainArea.addView(centerFrame)

        projectWorkspaceLayout.addView(mainArea)
        projectWorkspaceContainer.addView(projectWorkspaceLayout)

    }

     private data class StatusCardData(val title: String, val type: String, val desc: String, val color: Int)

    private fun buildProjectSettingsLayout() {
        projectSettingsContainer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
            visibility = View.GONE
            setBackgroundColor(NC.BG)
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
        }
        projectSettingsTopBar = createProjectSubpageTopBar()
        projectSettingsScrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f)
            clipToPadding = false
            setPadding(0, 0, 0, dp(100))
            setBackgroundColor(NC.BG)
        }
        projectSettingsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        projectSettingsScrollView.addView(projectSettingsLayout)
        col.addView(projectSettingsTopBar)
        col.addView(projectSettingsScrollView)
        projectSettingsContainer.addView(col)
    }

    private fun createProjectSubpageTopBar(): LinearLayout {
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(NC.SURFACE_LOWEST)
                setStroke(dp(1), NC.OUTLINE_VAR)
            }
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        val backBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_arrow_back)
            setColorFilter(NC.ON_SURFACE)
            setPadding(dp(6), dp(6), dp(6), dp(6))
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
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
        topBar.addView(backBtn)
        topBar.addView(workspaceIconIv)
        topBar.addView(titleTv)

        updateProjectSubpageTopBar(topBar)
        return topBar
    }

    private fun updateProjectSubpageTopBar(topBar: LinearLayout) {
        val workspaceIconIv = topBar.getChildAt(1) as? ImageView
        val titleTv = topBar.getChildAt(2) as? TextView
        titleTv?.text = activeProjectName

        val iconStr = getProjects().find { it.path == activeProjectPath }?.icon ?: ""
        if (iconStr.isNotEmpty()) {
            if (iconStr == cachedProjectIconPath && cachedProjectIconBitmap != null) {
                workspaceIconIv?.setImageBitmap(cachedProjectIconBitmap)
                workspaceIconIv?.visibility = View.VISIBLE
            } else {
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
                                cachedProjectIconPath = iconStr
                                cachedProjectIconBitmap = bitmap
                                workspaceIconIv?.setImageBitmap(bitmap)
                                workspaceIconIv?.visibility = View.VISIBLE
                            }
                        }
                    } catch (e: Exception) {
                        mainHandler.post { workspaceIconIv?.visibility = View.GONE }
                    }
                }
            }
        } else {
            workspaceIconIv?.visibility = View.GONE
        }
    }

    private fun openProjectSettings() {
        projectSettingsLayout.removeAllViews()
        updateProjectSubpageTopBar(projectSettingsTopBar)

        val settingsContentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(16))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        projectSettingsLayout.addView(settingsContentLayout)

        // Settings page header
        val settingsHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val settingsHeaderTitle = TextView(this).apply {
            text = "SETTINGS"
            textSize = 18f
            setTextColor(NC.PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        val settingsHeaderSub = TextView(this).apply {
            text = "// PROJECT CONFIG"
            textSize = 10f
            setTextColor(NC.ON_SURF_VAR)
            typeface = Typeface.MONOSPACE
        }
        settingsHeaderRow.addView(settingsHeaderTitle)
        settingsHeaderRow.addView(settingsHeaderSub)
        val settingsDivider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, dp(1)).apply {
                topMargin = dp(8)
                bottomMargin = dp(16)
            }
            setBackgroundColor(NC.OUTLINE_VAR)
        }
        settingsContentLayout.addView(settingsHeaderRow)
        settingsContentLayout.addView(settingsDivider)

        // Project Name card (rename)
        val nameCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cyberBrutalistBg(
                fillColor = NC.SURFACE_LOW,
                strokeColor = NC.OUTLINE_VAR,
                shadowColor = NC.SHADOW_DARK,
                offsetDp = 6,
                cornerRadiusDp = 0
            )
            setPadding(dp(16), dp(16), dp(16), dp(16))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(16) }
        }
        val nameCardTitleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(12))
        }
        val nameCardIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_project_config)
            setColorFilter(NC.PRIMARY)
            layoutParams = LinearLayout.LayoutParams(dp(20), dp(20)).apply { rightMargin = dp(8) }
        }
        val nameCardTitle = TextView(this).apply {
            text = "PROJECT NAME"
            textSize = 14f
            setTextColor(NC.PRIMARY)
            typeface = Typeface.MONOSPACE
            paint.isFakeBoldText = true
        }
        nameCardTitleRow.addView(nameCardIcon)
        nameCardTitleRow.addView(nameCardTitle)
        nameCard.addView(nameCardTitleRow)
        nameCard.addView(TextView(this).apply {
            text = "Display name only — repo path stays the same."
            textSize = 11f
            setTextColor(NC.ON_SURF_VAR)
            typeface = Typeface.MONOSPACE
            setPadding(0, 0, 0, dp(10))
        })
        val configNameInput = EditText(this).apply {
            setText(activeProjectName)
            hint = "Project name"
            setHintTextColor(NC.OUTLINE)
            setTextColor(NC.ON_SURFACE)
            textSize = 14f
            typeface = Typeface.MONOSPACE
            background = cyberBrutalistBg(
                fillColor = NC.SURFACE_LOWEST,
                strokeColor = NC.OUTLINE_VAR,
                shadowColor = Color.TRANSPARENT,
                offsetDp = 0,
                cornerRadiusDp = 0
            )
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setSingleLine(true)
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        nameCard.addView(configNameInput)
        settingsContentLayout.addView(nameCard)

        settingsContentLayout.addView(buildTerminalSettingsCard())
        settingsContentLayout.addView(spacer(16))

        // Project Icon card
        val iconCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cyberBrutalistBg(
                fillColor = NC.SURFACE_LOW,
                strokeColor = NC.OUTLINE_VAR,
                shadowColor = NC.SHADOW_DARK,
                offsetDp = 6,
                cornerRadiusDp = 0
            )
            setPadding(dp(16), dp(16), dp(16), dp(16))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(16) }
        }
        val iconCardTitleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(12))
        }
        val iconCardIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_folder_special)
            setColorFilter(NC.PRIMARY)
            layoutParams = LinearLayout.LayoutParams(dp(20), dp(20)).apply { rightMargin = dp(8) }
        }
        val iconCardTitle = TextView(this).apply {
            text = "PROJECT ICON"
            textSize = 14f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }
        iconCardTitleRow.addView(iconCardIcon)
        iconCardTitleRow.addView(iconCardTitle)
        iconCard.addView(iconCardTitleRow)

        // Preview + controls in a row
        val iconRowLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val previewContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(80), dp(80)).apply { rightMargin = dp(16) }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(NC.SURFACE_CONTAINER)
                setStroke(dp(2), NC.OUTLINE_VAR)
            }
            clipToOutline = true
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        }

        val iconInputCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }

        fun updatePreview(iconStr: String) {
            previewContainer.removeAllViews()
            if (iconStr.isEmpty()) {
                val iv = ImageView(this@MainActivity).apply {
                    setImageResource(R.drawable.ic_folder_special)
                    setColorFilter(NC.PRIMARY)
                    layoutParams = FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER)
                }
                previewContainer.addView(iv)
            } else if (iconStr.length <= 4 && !iconStr.startsWith("/") && !iconStr.startsWith("http")) {
                val tv = TextView(this@MainActivity).apply {
                    text = iconStr; textSize = 32f; gravity = Gravity.CENTER
                    layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
                }
                previewContainer.addView(tv)
            } else {
                val iv = ImageView(this@MainActivity).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
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
                                val defaultIv = ImageView(this@MainActivity).apply {
                                    setImageResource(R.drawable.ic_folder_special)
                                    setColorFilter(NC.PRIMARY)
                                    layoutParams = FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER)
                                }
                                previewContainer.addView(defaultIv)
                            }
                        }
                    } catch (e: Exception) {
                        mainHandler.post {
                            previewContainer.removeAllViews()
                            val defaultIv = ImageView(this@MainActivity).apply {
                                setImageResource(R.drawable.ic_folder_special)
                                setColorFilter(NC.PRIMARY)
                                layoutParams = FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER)
                            }
                            previewContainer.addView(defaultIv)
                        }
                    }
                }
            }
        }

        val currentProj = getProjects().find { it.path == activeProjectPath }
        val currentIcon = currentProj?.icon ?: ""
        updatePreview(currentIcon)

        val configIconInput = EditText(this).apply {
            setText(currentIcon)
            textSize = 12f
            setTextColor(NC.ON_SURFACE)
            setHintTextColor(NC.ON_SURF_VAR)
            hint = "Path / URL / emoji"
            typeface = Typeface.MONOSPACE
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(NC.SURFACE_CONTAINER)
                setStroke(dp(1), NC.OUTLINE_VAR)
            }
            setPadding(dp(10), dp(8), dp(10), dp(8))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(10) }
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
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        iconInputCol.addView(configIconInput)
        iconInputCol.addView(chooseIconBtn)
        iconRowLayout.addView(previewContainer)
        iconRowLayout.addView(iconInputCol)
        iconCard.addView(iconRowLayout)
        settingsContentLayout.addView(iconCard)

        val saveBtn = primaryButton("SAVE CONFIGURATION") {
            val newName = configNameInput.text.toString().trim()
            val newIcon = configIconInput.text.toString().trim()
            if (newName.isEmpty()) {
                Toast.makeText(this, "Project name cannot be empty", Toast.LENGTH_SHORT).show()
                return@primaryButton
            }
            val list = getProjects().toMutableList()
            val idx = list.indexOfFirst { it.path == activeProjectPath }
            if (idx >= 0) {
                val oldProj = list[idx]
                list[idx] = oldProj.copy(name = newName, icon = newIcon)
                saveProjects(list)
                activeProjectName = newName
                getSharedPreferences("nativecode_prefs", MODE_PRIVATE).edit()
                    .putString("active_project_name", activeProjectName)
                    .apply()
                if (::workspaceProjectNameTv.isInitialized) {
                    workspaceProjectNameTv.text = activeProjectName
                }
                if (::projectSettingsTopBar.isInitialized) {
                    updateProjectSubpageTopBar(projectSettingsTopBar)
                }
                if (::projectDirTreeTopBar.isInitialized) {
                    updateProjectSubpageTopBar(projectDirTreeTopBar)
                }
                if (::projectGitDiffTopBar.isInitialized) {
                    updateProjectSubpageTopBar(projectGitDiffTopBar)
                }
                Toast.makeText(this, "Configuration saved successfully!", Toast.LENGTH_SHORT).show()
                onBackPressed()
            }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply {
                bottomMargin = dp(16)
            }
        }
        settingsContentLayout.addView(saveBtn)

        settingsContentLayout.addView(spacer(16))
        val removeBtn = dangerButton("REMOVE PROJECT") {
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
        }.apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        settingsContentLayout.addView(removeBtn)
    }

    private fun buildProjectDirTreeLayout() {
        projectDirTreeContainer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
            visibility = View.GONE
            setBackgroundColor(NC.BG)
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
        }
        projectDirTreeTopBar = createProjectSubpageTopBar()
        projectDirTreeScrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f)
            clipToPadding = false
            setPadding(0, 0, 0, dp(100))
            setBackgroundColor(NC.BG)
        }
        projectDirTreeLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        projectDirTreeScrollView.addView(projectDirTreeLayout)
        col.addView(projectDirTreeTopBar)
        col.addView(projectDirTreeScrollView)
        projectDirTreeContainer.addView(col)
    }

    private fun openProjectDirTree() {
        projectDirTreeLayout.removeAllViews()
        projectDirTreeScrollView.scrollTo(0, 0)
        updateProjectSubpageTopBar(projectDirTreeTopBar)

        // Directory page header
        val dirHeader = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(8))
        }
        val dirHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val dirHeaderTitle = TextView(this).apply {
            text = "DIRECTORY"
            textSize = 18f
            setTextColor(NC.PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        val dirHeaderSub = TextView(this).apply {
            text = "// FILE TREE"
            textSize = 10f
            setTextColor(NC.ON_SURF_VAR)
            typeface = Typeface.MONOSPACE
        }
        dirHeaderRow.addView(dirHeaderTitle)
        dirHeaderRow.addView(dirHeaderSub)

        val searchCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedBg(NC.SURFACE_LOW, NC.BORDER, dp(8))
            setPadding(dp(12), dp(8), dp(12), dp(8))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply {
                topMargin = dp(10)
            }
        }

        val searchIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_search)
            setColorFilter(NC.PRIMARY)
            layoutParams = LinearLayout.LayoutParams(dp(18), dp(18)).apply { rightMargin = dp(8) }
        }

        val searchEt = EditText(this).apply {
            hint = "// SEARCH FILES IN WORKSPACE..."
            setHintTextColor(NC.OUTLINE)
            setTextColor(NC.ON_SURFACE)
            textSize = 12f
            typeface = Typeface.MONOSPACE
            background = null
            setSingleLine(true)
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
            setText(dirSearchQuery)
        }

        val clearBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_close)
            setColorFilter(NC.OUTLINE)
            setPadding(dp(4), dp(4), dp(4), dp(4))
            layoutParams = LinearLayout.LayoutParams(dp(24), dp(24))
            visibility = if (dirSearchQuery.isNotEmpty()) View.VISIBLE else View.GONE
            setOnClickListener {
                searchEt.setText("")
            }
        }

        searchEt.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                dirSearchQuery = s?.toString()?.trim() ?: ""
                clearBtn.visibility = if (dirSearchQuery.isNotEmpty()) View.VISIBLE else View.GONE
                refreshWorkspaceDirTree()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        searchCard.addView(searchIcon)
        searchCard.addView(searchEt)
        searchCard.addView(clearBtn)

        val dirDivider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, dp(1)).apply { topMargin = dp(10) }
            setBackgroundColor(NC.OUTLINE_VAR)
        }
        dirHeader.addView(dirHeaderRow)
        dirHeader.addView(searchCard)
        dirHeader.addView(dirDivider)
        projectDirTreeLayout.addView(dirHeader)
        
        workspaceDirTreeLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(8))
        }
        projectDirTreeLayout.addView(workspaceDirTreeLayout)
        refreshWorkspaceDirTree()
    }

    private fun buildProjectGitDiffLayout() {
        projectGitDiffContainer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
            visibility = View.GONE
            setBackgroundColor(NC.BG)
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
        }
        projectGitDiffTopBar = createProjectSubpageTopBar()
        projectGitDiffScrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f)
            clipToPadding = false
            setPadding(0, 0, 0, dp(100))
            setBackgroundColor(NC.BG)
        }
        projectGitDiffLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        projectGitDiffScrollView.addView(projectGitDiffLayout)
        col.addView(projectGitDiffTopBar)
        col.addView(projectGitDiffScrollView)
        projectGitDiffContainer.addView(col)
    }

    private fun openProjectGitDiff() {
        projectGitDiffLayout.removeAllViews()
        updateProjectSubpageTopBar(projectGitDiffTopBar)

        // Git diff page header
        val gitHeader = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(8))
        }
        val gitHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val gitHeaderTitle = TextView(this).apply {
            text = "GIT DIFF"
            textSize = 18f
            setTextColor(NC.PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        val gitHeaderSub = TextView(this).apply {
            text = "// CHANGED FILES"
            textSize = 10f
            setTextColor(NC.ON_SURF_VAR)
            typeface = Typeface.MONOSPACE
        }
        val methodBadge = TextView(this).apply {
            text = if (LinuxCommandBuilder.currentMethod == "chroot") "CHROOT" else "PROOT"
            textSize = 9f
            setTextColor(if (LinuxCommandBuilder.currentMethod == "chroot") NC.TERTIARY else NC.PRIMARY)
            typeface = Typeface.MONOSPACE
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(NC.SURFACE_LOWEST)
                setStroke(dp(1), if (LinuxCommandBuilder.currentMethod == "chroot") NC.TERTIARY else Color.parseColor("#3360F99E"))
            }
            setPadding(dp(6), dp(2), dp(6), dp(2))
        }
        gitHeaderRow.addView(gitHeaderTitle)
        gitHeaderRow.addView(gitHeaderSub)
        gitHeaderRow.addView(methodBadge)
        val gitDivider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, dp(1)).apply { topMargin = dp(8) }
            setBackgroundColor(NC.OUTLINE_VAR)
        }
        gitHeader.addView(gitHeaderRow)
        gitHeader.addView(gitDivider)
        projectGitDiffLayout.addView(gitHeader)
        
        workspaceGitDiffLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(4), dp(12), dp(8))
        }
        projectGitDiffLayout.addView(workspaceGitDiffLayout)
        refreshGitDiffTree()
    }

    private fun setGlobalTerminalZoom(newSize: Int) {
        val clamped = newSize.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
        termFontSize = clamped
        workspaceFontSize = clamped
        scriptFontSize = clamped
        getSharedPreferences("nativecode_prefs", MODE_PRIVATE).edit()
            .putInt("pref_terminal_zoom", clamped).apply()
        
        if (::terminalView.isInitialized) {
            terminalView.setTextSize(clamped)
        }
        if (::workspaceTerminalView.isInitialized) {
            workspaceTerminalView.setTextSize(clamped)
        }
        if (::scriptInstallTerminalView.isInitialized) {
            scriptInstallTerminalView.setTextSize(clamped)
        }
        forceTerminalResize(if (::terminalView.isInitialized) terminalView else null)
        forceTerminalResize(if (::workspaceTerminalView.isInitialized) workspaceTerminalView else null)
    }

    private fun setExtraKeysEnabled(enabled: Boolean) {
        showExtraKeys = enabled
        getSharedPreferences("nativecode_prefs", MODE_PRIVATE).edit()
            .putBoolean("pref_show_extra_keys", enabled).apply()
        
        if (::terminalKeyboardToolbar.isInitialized) {
            terminalKeyboardToolbar.visibility = if (enabled) View.VISIBLE else View.GONE
        }
        if (::workspaceKeyboardToolbar.isInitialized) {
            workspaceKeyboardToolbar.visibility = if (enabled) View.VISIBLE else View.GONE
        }
        forceTerminalResize(if (::terminalView.isInitialized) terminalView else null)
        forceTerminalResize(if (::workspaceTerminalView.isInitialized) workspaceTerminalView else null)
    }

    /** Push view size to PTY and SIGWINCH session pid (chroot WINCH trap forwards to guest). */
    private fun forceTerminalResize(tv: TerminalView?) {
        tv ?: return
        tv.post {
            try {
                if (tv.width <= 0 || tv.height <= 0) return@post
                tv.updateSize()
                tv.onScreenUpdated()
                val session = tv.currentSession ?: return@post
                if (!session.isRunning) return@post
                val pid = session.pid
                if (pid > 0) {
                    try {
                        Os.kill(pid, OsConstants.SIGWINCH)
                    } catch (_: Exception) {
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun maybeToastHeavyToolLaunch(type: String) {
        if (type != "codex") return
        Toast.makeText(
            this,
            "Loading Codex (~250MB). If blank, run: codex login",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun buildTerminalSettingsCard(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cyberBrutalistBg(
                fillColor = NC.SURFACE_LOW,
                strokeColor = NC.OUTLINE_VAR,
                shadowColor = NC.SHADOW_DARK,
                offsetDp = 6,
                cornerRadiusDp = 0
            )
            setPadding(dp(16), dp(16), dp(16), dp(16))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }

        val termTitleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(4))
        }
        val termTitleIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_terminal_thick)
            setColorFilter(NC.PRIMARY)
            layoutParams = LinearLayout.LayoutParams(dp(20), dp(20)).apply { rightMargin = dp(8) }
        }
        val title = TextView(this).apply {
            text = "TERMINAL SETTINGS"
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }
        termTitleRow.addView(termTitleIcon)
        termTitleRow.addView(title)
        val sub = TextView(this).apply {
            text = "Configure global terminal font zoom size and extra keyboard toolbar buttons."
            textSize = 12f
            setTextColor(NC.ON_SURF_VAR)
            typeface = Typeface.MONOSPACE
            setPadding(0, 0, 0, dp(14))
        }
        card.addView(termTitleRow)
        card.addView(sub)

        // 1. Global Terminal Zoom Section
        val zoomHeader = TextView(this).apply {
            text = "GLOBAL TERMINAL ZOOM"
            textSize = 13f
            setTextColor(NC.PRIMARY)
            typeface = Typeface.MONOSPACE
            paint.isFakeBoldText = true
            setPadding(0, 0, 0, dp(4))
        }
        val zoomValueTv = TextView(this).apply {
            text = "FONT SIZE: " + termFontSize + " PT (" + (termFontSize * 100 / 40) + "%)"
            textSize = 12f
            setTextColor(Color.WHITE)
            typeface = Typeface.MONOSPACE
            setPadding(0, 0, 0, dp(8))
        }
        
        val zoomControlRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(12))
        }

        val zoomMinusBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_remove_thick)
            setColorFilter(Color.WHITE)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = cyberBrutalistBg(
                fillColor = NC.SURFACE_CONTAINER,
                strokeColor = NC.OUTLINE_VAR,
                shadowColor = NC.SHADOW_DARK,
                offsetDp = 4,
                cornerRadiusDp = 0
            )
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))

            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        v.translationX = dp(2).toFloat()
                        v.translationY = dp(2).toFloat()
                        v.background = cyberBrutalistBg(
                            fillColor = NC.SURFACE_CONTAINER,
                            strokeColor = NC.OUTLINE_VAR,
                            shadowColor = NC.SHADOW_DARK,
                            offsetDp = 2,
                            cornerRadiusDp = 0
                        )
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.translationX = 0f
                        v.translationY = 0f
                        v.background = cyberBrutalistBg(
                            fillColor = NC.SURFACE_CONTAINER,
                            strokeColor = NC.OUTLINE_VAR,
                            shadowColor = NC.SHADOW_DARK,
                            offsetDp = 4,
                            cornerRadiusDp = 0
                        )
                    }
                }
                false
            }
        }

        val zoomPlusBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_add_thick)
            setColorFilter(Color.WHITE)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = cyberBrutalistBg(
                fillColor = NC.SURFACE_CONTAINER,
                strokeColor = NC.OUTLINE_VAR,
                shadowColor = NC.SHADOW_DARK,
                offsetDp = 4,
                cornerRadiusDp = 0
            )
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))

            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        v.translationX = dp(2).toFloat()
                        v.translationY = dp(2).toFloat()
                        v.background = cyberBrutalistBg(
                            fillColor = NC.SURFACE_CONTAINER,
                            strokeColor = NC.OUTLINE_VAR,
                            shadowColor = NC.SHADOW_DARK,
                            offsetDp = 2,
                            cornerRadiusDp = 0
                        )
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.translationX = 0f
                        v.translationY = 0f
                        v.background = cyberBrutalistBg(
                            fillColor = NC.SURFACE_CONTAINER,
                            strokeColor = NC.OUTLINE_VAR,
                            shadowColor = NC.SHADOW_DARK,
                            offsetDp = 4,
                            cornerRadiusDp = 0
                        )
                    }
                }
                false
            }
        }

        val zoomSeekBar = SeekBar(this).apply {
            max = MAX_FONT_SIZE - MIN_FONT_SIZE
            progress = termFontSize - MIN_FONT_SIZE
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).apply {
                leftMargin = dp(12)
                rightMargin = dp(12)
            }
        }

        fun updateZoomUI() {
            zoomValueTv.text = "FONT SIZE: " + termFontSize + " PT (" + (termFontSize * 100 / 40) + "%)"
            val targetProg = termFontSize - MIN_FONT_SIZE
            if (zoomSeekBar.progress != targetProg) {
                zoomSeekBar.progress = targetProg
            }
        }

        zoomMinusBtn.setOnClickListener {
            setGlobalTerminalZoom(termFontSize - 2)
            updateZoomUI()
        }
        zoomPlusBtn.setOnClickListener {
            setGlobalTerminalZoom(termFontSize + 2)
            updateZoomUI()
        }
        zoomSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val newSize = MIN_FONT_SIZE + progress
                    setGlobalTerminalZoom(newSize)
                    zoomValueTv.text = "FONT SIZE: " + newSize + " PT (" + (newSize * 100 / 40) + "%)"
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        zoomControlRow.addView(zoomMinusBtn)
        zoomControlRow.addView(zoomSeekBar)
        zoomControlRow.addView(zoomPlusBtn)

        card.addView(zoomHeader)
        card.addView(zoomValueTv)
        card.addView(zoomControlRow)

        // Divider line
        val div = View(this).apply {
            setBackgroundColor(NC.OUTLINE_VAR)
            layoutParams = LinearLayout.LayoutParams(MATCH, dp(1)).apply {
                topMargin = dp(4)
                bottomMargin = dp(12)
            }
        }
        card.addView(div)

        // 2. Extra Keyboard Buttons Toggle Section
        val toggleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val toggleLabelLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).apply { rightMargin = dp(12) }
        }
        val toggleTitle = TextView(this).apply {
            text = "EXTRA 2 KEYBOARD ROWS"
            textSize = 13f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }
        val toggleSub = TextView(this).apply {
            text = "Show special key rows (CTRL, ALT, ESC, Arrows, symbols) on terminal screens."
            textSize = 11f
            setTextColor(NC.ON_SURF_VAR)
            typeface = Typeface.MONOSPACE
        }
        toggleLabelLayout.addView(toggleTitle)
        toggleLabelLayout.addView(toggleSub)

        val switchToggle = CustomBrutalistToggle(this, showExtraKeys) { isChecked ->
            setExtraKeysEnabled(isChecked)
        }

        toggleRow.addView(toggleLabelLayout)
        toggleRow.addView(switchToggle)
        card.addView(toggleRow)

        return card
    }

    
    private class CustomBrutalistToggle(
        context: Context,
        initialChecked: Boolean = false,
        private val onCheckedChange: ((Boolean) -> Unit)? = null
    ) : LinearLayout(context) {

        var isChecked: Boolean = initialChecked
            set(value) {
                field = value
                updateState(animate = false)
            }

        private val trackView = FrameLayout(context)
        private val thumbView = View(context)

        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true

            val trackWidth = dp(46)
            val trackHeight = dp(24)
            val thumbSize = dp(18)
            val margin = dp(3)

            trackView.layoutParams = LayoutParams(trackWidth, trackHeight)
            
            val thumbParams = FrameLayout.LayoutParams(thumbSize, thumbSize).apply {
                gravity = Gravity.CENTER_VERTICAL
                leftMargin = margin
            }
            thumbView.layoutParams = thumbParams

            trackView.addView(thumbView)
            addView(trackView)

            updateState(animate = false)

            setOnClickListener {
                isChecked = !isChecked
                updateState(animate = true)
                onCheckedChange?.invoke(isChecked)
            }
        }

        private fun updateState(animate: Boolean) {
            val trackWidth = dp(46)
            val thumbSize = dp(18)
            val margin = dp(3)

            val trackColor = if (isChecked) Color.parseColor("#3DDC84") else Color.parseColor("#1E1E1E")
            val trackBorder = if (isChecked) Color.parseColor("#3DDC84") else Color.parseColor("#333333")
            val thumbColor = if (isChecked) Color.parseColor("#0A0A0A") else Color.parseColor("#A0A0A0")

            trackView.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(trackColor)
                setStroke(dp(2), trackBorder)
                cornerRadius = 0f
            }

            thumbView.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(thumbColor)
                cornerRadius = 0f
            }

            val targetMargin = if (isChecked) {
                trackWidth - thumbSize - margin
            } else {
                margin
            }

            val params = thumbView.layoutParams as FrameLayout.LayoutParams
            if (animate) {
                val anim = ValueAnimator.ofInt(params.leftMargin, targetMargin)
                anim.duration = 150
                anim.addUpdateListener { va ->
                    params.leftMargin = va.animatedValue as Int
                    thumbView.layoutParams = params
                }
                anim.start()
            } else {
                params.leftMargin = targetMargin
                thumbView.layoutParams = params
            }
        }

        private fun dp(dpValue: Int): Int {
            return (dpValue * context.resources.displayMetrics.density).toInt()
        }
    }

    private class TerminalScanlineDrawable(
        private val bgColor: Int = Color.parseColor("#131313"),
        private val lineSpacingDp: Int = 4,
        private val lineColor: Int = Color.parseColor("#12FFFFFF")
    ) : Drawable() {
        private val bgPaint = Paint().apply { color = bgColor }
        private val linePaint = Paint().apply {
            color = lineColor
            strokeWidth = 1f
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            canvas.drawRect(b, bgPaint)

            val displayMetrics = canvas.density
            val density = if (displayMetrics <= 0) 2.5f else displayMetrics.toFloat()
            val spacing = lineSpacingDp * density
            var y = 0f
            while (y < b.height()) {
                canvas.drawLine(0f, y, b.width().toFloat(), y, linePaint)
                y += spacing
            }
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private class CircularProgressView(context: Context) : View(context) {
        var progress: Float = 0f
            set(value) {
                field = value.coerceIn(0f, 100f)
                postInvalidate()
            }
        var arcColor: Int = Color.parseColor("#C8B6FF")
            set(value) {
                field = value
                postInvalidate()
            }
        var trackColor: Int = Color.parseColor("#222222")
            set(value) {
                field = value
                postInvalidate()
            }

        private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
        }
        private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        private val rectF = RectF()

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val density = resources.displayMetrics.density
            val strokeWidthPx = 7f * density
            trackPaint.strokeWidth = strokeWidthPx
            trackPaint.color = trackColor

            progressPaint.strokeWidth = strokeWidthPx
            progressPaint.color = arcColor

            val inset = strokeWidthPx / 2f + 2f * density
            rectF.set(inset, inset, width - inset, height - inset)

            canvas.drawOval(rectF, trackPaint)

            val sweep = (progress / 100f) * 360f
            if (sweep > 0f) {
                canvas.drawArc(rectF, -90f, sweep, false, progressPaint)
            }
        }
    }
}
