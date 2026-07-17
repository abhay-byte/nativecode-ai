package com.ivarna.nativecode

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
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

    private lateinit var homeScrollView: ScrollView
    private lateinit var homeLayout: LinearLayout
    private lateinit var terminalLayout: FrameLayout
    private lateinit var setupLayout: LinearLayout    // shown during first-time setup

    private lateinit var terminalView: TerminalView
    private var terminalSession: TerminalSession? = null

    // Setup / status widgets
    private lateinit var setupStatusText: TextView
    private lateinit var logText: TextView
    private lateinit var logScrollView: ScrollView
    private lateinit var setupProgressBar: ProgressBar

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = NC.BG
        window.navigationBarColor = NC.BG

        buildRootLayout()
        setContentView(rootLayout)

        // Respect system bars: top bar absorbs status-bar height, bottom nav absorbs nav-bar height
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            // Apply top inset to contentFrame so all content starts below status bar
            contentFrame.setPadding(0, bars.top, 0, 0)
            // Apply bottom inset to bottom nav so it sits above gesture bar
            bottomNavigation.setPadding(0, 0, 0, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(rootLayout)

        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                ID_HOME -> {
                    showHome()
                    true
                }
                ID_FILES -> {
                    startActivity(Intent(this, FileExplorerActivity::class.java))
                    true
                }
                ID_TERMINAL -> {
                    showTerminal()
                    true
                }
                ID_GIT -> {
                    startActivity(Intent(this, GitOperationsActivity::class.java))
                    true
                }
                ID_SETTINGS -> {
                    startActivity(Intent(this, SettingsHubActivity::class.java))
                    true
                }
                else -> false
            }
        }

        deployScripts()

        val setupCompleteFile = File(filesDir, "setup_complete")
        if (setupCompleteFile.exists()) {
            showHome()
            onSetupComplete()
        } else {
            showSetup()
            runFirstTimeSetup()
        }
    }

    // ── Layout construction ───────────────────────────────────────────────────

    private fun buildRootLayout() {
        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(NC.BG)
            layoutParams = ViewGroup.LayoutParams(MATCH, MATCH)
        }

        contentFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f)
        }

        bottomNavigation = BottomNavigationView(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
            setBackgroundColor(Color.parseColor("#1d1a24"))
            itemIconTintList = null
            menu.add(Menu.NONE, ID_HOME,     Menu.NONE, "Home").setIcon(android.R.drawable.ic_menu_info_details)
            menu.add(Menu.NONE, ID_FILES,    Menu.NONE, "Files").setIcon(android.R.drawable.ic_menu_agenda)
            menu.add(Menu.NONE, ID_TERMINAL, Menu.NONE, "Terminal").setIcon(android.R.drawable.ic_menu_view)
            menu.add(Menu.NONE, ID_GIT,      Menu.NONE, "Git").setIcon(android.R.drawable.ic_menu_share)
            menu.add(Menu.NONE, ID_SETTINGS, Menu.NONE, "Settings").setIcon(android.R.drawable.ic_menu_preferences)
        }

        rootLayout.addView(contentFrame)
        rootLayout.addView(bottomNavigation)

        buildSetupLayout()
        buildHomeLayout()
        buildTerminalLayout()

        contentFrame.addView(setupLayout)
        contentFrame.addView(homeScrollView)
        contentFrame.addView(terminalLayout)

        setupLayout.visibility = View.GONE
        homeScrollView.visibility = View.GONE
        terminalLayout.visibility = View.GONE
    }

    private fun buildSetupLayout() {
        setupLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(NC.BG)
            setPadding(dp(16))
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
        }

        // Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(24))
        }
        val memIcon = TextView(this).apply {
            text = "⬛"
            textSize = 20f
        }
        val appTitle = TextView(this).apply {
            text = "NativeCode"
            textSize = 20f
            setTextColor(NC.PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(8), 0, 0, 0)
        }
        val versionBadge = textBadge("v1.0-beta", NC.SURFACE_VAR, NC.ON_SURF_VAR)
        val spacer = View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) }
        header.addView(memIcon)
        header.addView(appTitle)
        header.addView(spacer)
        header.addView(versionBadge)
        setupLayout.addView(header)

        // Title
        val titleTv = TextView(this).apply {
            text = "Environment Setup"
            textSize = 22f
            setTextColor(NC.ON_SURFACE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(4))
        }
        val subtitleTv = TextView(this).apply {
            text = "Configure your development container and select AI assistance tools."
            textSize = 13f
            setTextColor(NC.ON_SURF_VAR)
            setPadding(0, 0, 0, dp(16))
        }
        setupLayout.addView(titleTv)
        setupLayout.addView(subtitleTv)

        // Progress card (glass-panel style)
        val progressCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(NC.SURFACE, NC.BORDER, dp(12))
            setPadding(dp(16))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply {
                bottomMargin = dp(16)
            }
        }
        val statusLabel = capLabel("STATUS", NC.SECONDARY)
        setupStatusText = TextView(this).apply {
            text = "Initializing..."
            textSize = 13f
            setTextColor(NC.ON_SURFACE)
            typeface = Typeface.MONOSPACE
            setPadding(0, dp(4), 0, dp(8))
        }
        setupProgressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            layoutParams = LinearLayout.LayoutParams(MATCH, dp(6))
            progressDrawable = gradientProgressDrawable()
        }
        progressCard.addView(statusLabel)
        progressCard.addView(setupStatusText)
        progressCard.addView(setupProgressBar)
        setupLayout.addView(progressCard)

        // Log output
        logScrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f)
            background = roundedBg(NC.LOGBG, NC.BORDER, dp(8))
        }
        logText = TextView(this).apply {
            text = ""
            textSize = 11f
            setTextColor(Color.parseColor("#4cd7f6"))
            typeface = Typeface.MONOSPACE
            setPadding(dp(12))
        }
        logScrollView.addView(logText)
        setupLayout.addView(logScrollView)
    }

    private fun buildHomeLayout() {
        homeScrollView = ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
            setBackgroundColor(NC.BG)
        }
        homeLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(NC.BG)
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        homeScrollView.addView(homeLayout)

        val pad = dp(16)

        // ── Top App Bar ──────────────────────────────────────────────────────
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#15121b"))
            setPadding(pad, dp(12), pad, dp(12))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        val logoIcon = TextView(this).apply {
            text = "\uD83D\uDCBB"
            textSize = 18f
        }
        val logoText = TextView(this).apply {
            text = "NativeCode"
            textSize = 18f
            setTextColor(NC.PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(8), 0, 0, 0)
        }
        val topSpacer = View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) }
        val settingsBtn = iconButton("\u2699\uFE0F")
        topBar.addView(logoIcon)
        topBar.addView(logoText)
        topBar.addView(topSpacer)
        topBar.addView(settingsBtn)
        homeLayout.addView(topBar)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad)
        }

        // ── Environment Status card ──────────────────────────────────────────
        val statusCard = buildStatusCard()
        content.addView(statusCard)

        // ── XFCE / X11 Launch Section ────────────────────────────────────────
        val guiCard = buildGuiLaunchCard()
        content.addView(guiCard)

        // ── System Resources card ────────────────────────────────────────────
        val resourcesCard = buildResourcesCard()
        content.addView(resourcesCard)

        // ── Recent Projects section ──────────────────────────────────────────
        val projectsSection = buildRecentProjectsSection()
        content.addView(projectsSection)

        homeLayout.addView(content)
    }

    private fun buildStatusCard(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(NC.SURFACE, NC.BORDER, dp(12))
            setPadding(dp(16))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply {
                bottomMargin = dp(12)
            }
        }

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(12))
        }
        val cardTitle = TextView(this).apply {
            text = "Environment Status"
            textSize = 20f
            setTextColor(NC.PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        titleRow.addView(cardTitle)
        card.addView(titleRow)

        // Status pill
        val pill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedBg(NC.LOGBG, NC.BORDER, dp(6))
            setPadding(dp(12), dp(8), dp(12), dp(8))
            layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).apply {
                bottomMargin = dp(8)
            }
        }
        homeStatusDot = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(10), dp(10)).apply { rightMargin = dp(8) }
            background = circleDrawable(NC.SECONDARY)
        }
        homeStatusLabel = TextView(this).apply {
            text = "Ready"
            textSize = 13f
            setTextColor(NC.ON_SURFACE)
            typeface = Typeface.MONOSPACE
        }
        pill.addView(homeStatusDot)
        pill.addView(homeStatusLabel)
        card.addView(pill)

        // Container info
        val infoRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val dnsIcon = TextView(this).apply {
            text = "\uD83D\uDCE1 "
            textSize = 13f
            setTextColor(NC.OUTLINE)
        }
        homeContainerLabel = TextView(this).apply {
            text = "Container: PRoot (Debian 12)"
            textSize = 13f
            setTextColor(NC.ON_SURF_VAR)
            typeface = Typeface.MONOSPACE
        }
        infoRow.addView(dnsIcon)
        infoRow.addView(homeContainerLabel)
        card.addView(infoRow)

        pulseView(homeStatusDot)

        return card
    }

    private fun buildGuiLaunchCard(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(NC.SURFACE, NC.BORDER, dp(12))
            setPadding(dp(16))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply {
                bottomMargin = dp(12)
            }
        }

        val sectionTitle = TextView(this).apply {
            text = "Graphical Desktop"
            textSize = 16f
            setTextColor(NC.ON_SURFACE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(4))
        }
        val sectionSub = TextView(this).apply {
            text = "Launch Termux X11 and start or stop the XFCE4 desktop session."
            textSize = 13f
            setTextColor(NC.ON_SURF_VAR)
            setPadding(0, 0, 0, dp(12))
        }
        card.addView(sectionTitle)
        card.addView(sectionSub)

        // Primary: Open X11 Display (full-width)
        openX11Btn = primaryButton("  Open X11 Display")
        openX11Btn.setOnClickListener {
            val intent = Intent(this, com.termux.x11.MainActivity::class.java)
            startActivity(intent)
        }
        card.addView(openX11Btn)

        val spacer4 = View(this).apply { layoutParams = LinearLayout.LayoutParams(MATCH, dp(8)) }
        card.addView(spacer4)

        // Start / Stop row
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }

        startGuiBtn = secondaryButton("\u25B6 Start XFCE")
        startGuiBtn.isEnabled = false
        startGuiBtn.alpha = 0.5f
        startGuiBtn.setOnClickListener { startGui() }
        startGuiBtn.layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).apply {
            rightMargin = dp(8)
        }

        stopGuiBtn = dangerButton("\u25A0 Stop XFCE")
        stopGuiBtn.isEnabled = false
        stopGuiBtn.alpha = 0.5f
        stopGuiBtn.setOnClickListener { stopGui() }
        stopGuiBtn.layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)

        row.addView(startGuiBtn)
        row.addView(stopGuiBtn)
        card.addView(row)

        return card
    }

    private fun buildResourcesCard(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(Color.parseColor("#1a1822"), Color.parseColor("#ffffff1a"), dp(12))
            setPadding(dp(16))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply {
                bottomMargin = dp(12)
            }
        }

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(12))
        }
        val resTitle = TextView(this).apply {
            text = "System Resources"
            textSize = 16f
            setTextColor(NC.ON_SURFACE)
            typeface = Typeface.DEFAULT_BOLD
        }
        val resSub = TextView(this).apply {
            text = "Monitoring container usage"
            textSize = 12f
            setTextColor(NC.ON_SURF_VAR)
        }
        titleRow.addView(resTitle)
        titleRow.addView(resSub)
        card.addView(titleRow)

        val statsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
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
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(12))
        }
        val sectionTitle = TextView(this).apply {
            text = "Recent Projects"
            textSize = 18f
            setTextColor(NC.ON_SURFACE)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        val viewAll = TextView(this).apply {
            text = "View All"
            textSize = 13f
            setTextColor(NC.SECONDARY)
        }
        headerRow.addView(sectionTitle)
        headerRow.addView(viewAll)
        section.addView(headerRow)

        section.addView(projectCard("MyAndroidApp", "/home/user/workspace/MyAndroidApp", "2 hours ago"))
        section.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(MATCH, dp(8)) })
        section.addView(projectCard("API_Server", "/home/user/workspace/API_Server", "Yesterday"))
        section.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(MATCH, dp(24)) })

        return section
    }

    private fun buildTerminalLayout() {
        terminalLayout = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
            visibility = View.GONE
        }
        terminalView = TerminalView(this, null).apply {
            isFocusable = true
            isFocusableInTouchMode = true
        }
        terminalLayout.addView(terminalView)
    }

    // ── Visibility helpers ────────────────────────────────────────────────────

    private fun showHome() {
        setupLayout.visibility = View.GONE
        homeScrollView.visibility = View.VISIBLE
        terminalLayout.visibility = View.GONE
    }

    private fun showTerminal() {
        setupLayout.visibility = View.GONE
        homeScrollView.visibility = View.GONE
        terminalLayout.visibility = View.VISIBLE
        terminalView.requestFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(terminalView, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun showSetup() {
        setupLayout.visibility = View.VISIBLE
        homeScrollView.visibility = View.GONE
        terminalLayout.visibility = View.GONE
        bottomNavigation.menu.findItem(ID_TERMINAL).isEnabled = false
        bottomNavigation.menu.findItem(ID_FILES).isEnabled = false
        bottomNavigation.menu.findItem(ID_GIT).isEnabled = false
        bottomNavigation.menu.findItem(ID_SETTINGS).isEnabled = false
    }

    // ── Setup flow ────────────────────────────────────────────────────────────

    private fun onSetupComplete() {
        mainHandler.post {
            startGuiBtn.isEnabled = true
            startGuiBtn.alpha = 1f
            stopGuiBtn.isEnabled = true
            stopGuiBtn.alpha = 1f
            homeStatusLabel.text = "Ready"
            bottomNavigation.menu.findItem(ID_TERMINAL).isEnabled = true
            bottomNavigation.menu.findItem(ID_FILES).isEnabled = true
            bottomNavigation.menu.findItem(ID_GIT).isEnabled = true
            bottomNavigation.menu.findItem(ID_SETTINGS).isEnabled = true
            initTerminalView()
        }
    }

    private fun deployScripts() {
        try {
            val homeDir = File(filesDir, "home").also { it.mkdirs() }
            val scripts = arrayOf("setup_termux.sh", "termux_tweaks.sh", "flux_install.sh", "start_gui.sh", "stop_gui.sh")
            for (script in scripts) {
                val assetPath = if (script.contains("tweaks")) "scripts/termux_tweaks.sh" else "scripts/$script"
                val out = File(homeDir, script)
                assets.open(assetPath).use { input -> FileOutputStream(out).use { input.copyTo(it) } }
                out.setExecutable(true)
            }
            val scriptsDir = File(homeDir, "scripts").also { it.mkdirs() }
            val allScripts = arrayOf(
                "setup_termux.sh", "termux_tweaks.sh", "flux_install.sh",
                "start_gui.sh", "stop_gui.sh", "setup_debian_family.sh",
                "setup_hw_accel_debian.sh", "setup_customization_debian.sh"
            )
            for (script in allScripts) {
                val assetPath = if (script.contains("tweaks")) "scripts/termux_tweaks.sh" else "scripts/$script"
                val out = File(scriptsDir, script)
                assets.open(assetPath).use { input -> FileOutputStream(out).use { input.copyTo(it) } }
                out.setExecutable(true)
            }
        } catch (e: Exception) {
            Log.e("FluxSetup", "Failed to deploy scripts", e)
        }
    }

    private fun runFirstTimeSetup() {
        setupProgressBar.isIndeterminate = true

        executor.execute {
            try {
                updateStatus("A. Preparing Directories...")
                val usrDir  = File(filesDir, "usr")
                val tmpDir  = File(usrDir, "tmp")
                val etcDir  = File(usrDir, "etc")
                val varDir  = File(usrDir, "var")
                val homeDir = File(filesDir, "home")
                tmpDir.mkdirs(); etcDir.mkdirs(); homeDir.mkdirs()
                File(varDir, "log/apt").mkdirs()
                File(varDir, "lib/dpkg").mkdirs()

                updateStatus("B. Extracting Bootstrap Assets...")
                val tarFile = File(filesDir, "bootstrap.tar")
                if (!tarFile.exists()) {
                    assets.open("bootstrap.tar").use { FileOutputStream(tarFile).use { o -> it.copyTo(o) } }
                }
                Runtime.getRuntime().exec(arrayOf("tar", "-xf", tarFile.absolutePath, "-C", filesDir.absolutePath)).waitFor()
                tarFile.delete()

                val nested = File(filesDir, "data/data/com.ivarna.nativecode/files")
                if (nested.exists()) {
                    moveDirectoryContents(nested, filesDir)
                    File(filesDir, "data").deleteRecursively()
                }

                val loginInitPy = File(filesDir, "usr/lib/python3.14/site-packages/proot_distro/commands/login/__init__.py")
                if (loginInitPy.exists()) {
                    var content = loginInitPy.readText()
                    content = content.replace(
                        "\"PROOT_NO_SECCOMP\", \"PROOT_VERBOSE\"",
                        "\"PROOT_NO_SECCOMP\", \"PROOT_VERBOSE\", \"PROOT_LOADER\""
                    )
                    loginInitPy.writeText(content)
                }

                File(varDir, "log/apt").mkdirs(); File(varDir, "lib/dpkg").mkdirs(); tmpDir.mkdirs()

                updateStatus("C. Deploying Custom Scripts...")
                deployScripts()

                File(etcDir, "resolv.conf").writeText("nameserver 8.8.8.8\nnameserver 8.8.4.4\n")

                updateStatus("D. Initializing Host Environment...")
                val nld   = applicationInfo.nativeLibraryDir
                val proot = File(nld, "libproot.so").absolutePath
                val bash  = File(nld, "libbash.so").absolutePath
                check(runShellCommand(arrayOf(proot, bash, File(homeDir, "setup_termux.sh").absolutePath)) == 0) { "Host env setup failed" }

                updateStatus("E. Downloading & Installing Debian Guest...")
                val debBytes   = assets.open("scripts/setup_debian_family.sh").use { it.readBytes() }
                val debPayload = Base64.encodeToString(debBytes, Base64.NO_WRAP)
                check(runShellCommand(arrayOf(bash, File(homeDir, "flux_install.sh").absolutePath, "debian", debPayload)) == 0) { "Debian setup failed" }

                updateStatus("F. Setting Up GPU Hardware Acceleration...")
                val hwScript = File(File(usrDir, "tmp"), "setup_hw_accel_debian.sh")
                assets.open("scripts/setup_hw_accel_debian.sh").use { FileOutputStream(hwScript).use { o -> it.copyTo(o) } }
                hwScript.setExecutable(true)
                check(runShellCommand(arrayOf(
                    "/data/data/com.ivarna.nativecode/files/usr/bin/python",
                    "/data/data/com.ivarna.nativecode/files/usr/bin/proot-distro",
                    "login", "debian", "--shared-tmp", "--",
                    "env", "FLUX_GPU=virgl", "bash", "/tmp/setup_hw_accel_debian.sh"
                )) == 0) { "GPU setup failed" }

                updateStatus("G. Setting Up Desktop Customizations...")
                val custScript = File(File(usrDir, "tmp"), "setup_customization_debian.sh")
                assets.open("scripts/setup_customization_debian.sh").use { FileOutputStream(custScript).use { o -> it.copyTo(o) } }
                custScript.setExecutable(true)
                check(runShellCommand(arrayOf(
                    "/data/data/com.ivarna.nativecode/files/usr/bin/python",
                    "/data/data/com.ivarna.nativecode/files/usr/bin/proot-distro",
                    "login", "debian", "--shared-tmp", "--",
                    "env", "FLUX_THEME=dark", "bash", "/tmp/setup_customization_debian.sh"
                )) == 0) { "Desktop customization failed" }

                File(filesDir, "setup_complete").createNewFile()
                mainHandler.post {
                    updateStatus("Setup Complete!")
                    setupProgressBar.visibility = View.GONE
                    showHome()
                    onSetupComplete()
                }
            } catch (e: Exception) {
                Log.e("FluxSetup", "Setup failed", e)
                updateStatus("Error: ${e.message}")
            }
        }
    }

    private fun moveDirectoryContents(source: File, target: File) {
        source.listFiles()?.forEach { file ->
            val dest = File(target, file.name)
            if (file.isDirectory) { dest.mkdirs(); moveDirectoryContents(file, dest) }
            else file.renameTo(dest)
        }
    }

    private fun updateStatus(text: String) {
        mainHandler.post {
            setupStatusText.text = text
            logText.append("\n>>> $text\n")
            logScrollView.post { logScrollView.fullScroll(View.FOCUS_DOWN) }
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
        val proc   = pb.start()
        val buf    = ByteArray(1024)
        val stream = proc.inputStream
        var read: Int
        while (stream.read(buf).also { read = it } != -1) {
            val out = String(buf, 0, read)
            mainHandler.post {
                logText.append(out)
                logScrollView.post { logScrollView.fullScroll(View.FOCUS_DOWN) }
            }
        }
        return proc.waitFor()
    }

    // ── GUI start / stop ──────────────────────────────────────────────────────

    private fun startGui() {
        val serviceIntent = Intent(this, BackgroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent)
        else startService(serviceIntent)

        executor.execute {
            updateStatus("Starting XFCE4 GUI session...")
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
            updateStatus("Stopping XFCE4 GUI session...")
            val nld  = applicationInfo.nativeLibraryDir
            val bash = File(nld, "libbash.so").absolutePath
            runShellCommand(arrayOf(bash, "/data/data/com.ivarna.nativecode/files/home/stop_gui.sh", "debian"))
        }
    }

    // ── Terminal ──────────────────────────────────────────────────────────────

    private fun initTerminalView() {
        terminalView.setTextSize(40)
        val nld     = applicationInfo.nativeLibraryDir
        val shell   = File(nld, "libbash.so").absolutePath
        val cwd     = File(filesDir, "home").absolutePath
        val args    = arrayOf(shell, "-l")
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

        val viewClient = object : TerminalViewClient {
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

        val sessionClient = object : TerminalSessionClient {
            override fun onTextChanged(session: TerminalSession)    { terminalView.onScreenUpdated() }
            override fun onTitleChanged(session: TerminalSession)   {}
            override fun onSessionFinished(session: TerminalSession) { Log.d("Terminal", "Session finished: ${session.exitStatus}") }
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
        terminalSession = TerminalSession(shell, cwd, args, env, 10000, sessionClient)
        terminalView.attachSession(terminalSession)
        terminalView.postDelayed({
            terminalView.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(terminalView, InputMethodManager.SHOW_IMPLICIT)
        }, 1000)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopService(Intent(this, BackgroundService::class.java))
        terminalSession?.finishIfRunning()
    }

    // ── UI factory helpers ────────────────────────────────────────────────────

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP  = ViewGroup.LayoutParams.WRAP_CONTENT

    private fun roundedBg(fill: Int, stroke: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            setStroke(dp(1), stroke)
            cornerRadius = radius.toFloat()
        }

    private fun circleDrawable(color: Int): ShapeDrawable =
        ShapeDrawable(OvalShape()).apply { paint.color = color }

    private fun capLabel(text: String, color: Int): TextView = TextView(this).apply {
        this.text = text
        textSize = 10f
        setTextColor(color)
        typeface = Typeface.MONOSPACE
        letterSpacing = 0.1f
        setPadding(0, 0, 0, dp(4))
    }

    private fun textBadge(text: String, bg: Int, fg: Int): TextView = TextView(this).apply {
        this.text = text
        textSize = 11f
        setTextColor(fg)
        typeface = Typeface.MONOSPACE
        background = roundedBg(bg, NC.BORDER, dp(4))
        setPadding(dp(8), dp(4), dp(8), dp(4))
    }

    private fun iconButton(icon: String): TextView = TextView(this).apply {
        text = icon
        textSize = 18f
        setPadding(dp(8), dp(4), dp(8), dp(4))
    }

    private fun primaryButton(label: String): TextView = TextView(this).apply {
        text = label
        textSize = 15f
        setTextColor(Color.WHITE)
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        background = roundedBg(NC.PRIMARY_CON, NC.PRIMARY_CON, dp(24))
        setPadding(dp(16), dp(12), dp(16), dp(12))
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
    }

    private fun secondaryButton(label: String): TextView = TextView(this).apply {
        text = label
        textSize = 13f
        setTextColor(NC.ON_SURFACE)
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        background = roundedBg(NC.SURFACE_HIGH, NC.BORDER_VAR, dp(20))
        setPadding(dp(12), dp(10), dp(12), dp(10))
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
    }

    private fun dangerButton(label: String): TextView = TextView(this).apply {
        text = label
        textSize = 13f
        setTextColor(NC.ERROR)
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        background = roundedBg(Color.parseColor("#3d1212"), Color.parseColor("#93000a"), dp(20))
        setPadding(dp(12), dp(10), dp(12), dp(10))
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
    }

    private fun statWidget(label: String, value: String, color: Int): LinearLayout {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        val labelTv = TextView(this).apply {
            text = label
            textSize = 12f
            setTextColor(color)
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
        }
        val valueTv = TextView(this).apply {
            this.text = value
            textSize = 20f
            setTextColor(NC.ON_SURFACE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        col.addView(labelTv)
        col.addView(valueTv)
        return col
    }

    private fun projectCard(name: String, path: String, time: String): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedBg(NC.SURFACE, NC.BORDER, dp(12))
            setPadding(dp(14), dp(14), dp(14), dp(14))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }

        val icon = TextView(this).apply {
            text = "\uD83D\uDCC1"
            textSize = 24f
            setPadding(0, 0, dp(12), 0)
        }

        val details = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        val nameRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val nameTv = TextView(this).apply {
            text = name
            textSize = 16f
            setTextColor(NC.PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        val timeTv = TextView(this).apply {
            this.text = time
            textSize = 11f
            setTextColor(NC.ON_SURF_VAR)
            typeface = Typeface.MONOSPACE
        }
        nameRow.addView(nameTv)
        nameRow.addView(timeTv)

        val pathRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, 0)
        }
        val gitBadge = textBadge("Git", NC.LOGBG, NC.SECONDARY).apply {
            setPadding(dp(6), dp(2), dp(6), dp(2))
        }
        val pathTv = TextView(this).apply {
            this.text = "  $path"
            textSize = 11f
            setTextColor(NC.OUTLINE)
            typeface = Typeface.MONOSPACE
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        pathRow.addView(gitBadge)
        pathRow.addView(pathTv)

        details.addView(nameRow)
        details.addView(pathRow)
        card.addView(icon)
        card.addView(details)
        return card
    }

    private fun gradientProgressDrawable(): android.graphics.drawable.Drawable {
        val gd = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(NC.PRIMARY_CON, NC.SECONDARY)
        )
        gd.cornerRadius = dp(3).toFloat()
        return gd
    }

    private fun pulseView(v: View) {
        val anim = ObjectAnimator.ofFloat(v, "alpha", 0.4f, 1f).apply {
            duration = 1500
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
        }
        anim.start()
    }
}
