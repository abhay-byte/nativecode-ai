package com.ivarna.nativecode

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.shapes.OvalShape
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.setPadding
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

class OnboardingActivity : AppCompatActivity() {

    private lateinit var rootLayout: FrameLayout
    private lateinit var pageContainer: FrameLayout

    // Onboarding page index (0 to 5)
    private var currentPageIndex = 0

    private val executor = Executors.newCachedThreadPool()
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    // ── Shared setup status state ──────────────────────────────────────────────
    private var isDebianBaseSetupStarted = false
    private var isCliToolsSetupStarted = false

    // page 4 (Debian Base) views
    private lateinit var baseStatusText: TextView
    private lateinit var baseProgressBar: ProgressBar
    private lateinit var baseLogText: TextView
    private lateinit var baseLogScroll: ScrollView

    // page 5 (AI CLI Tools) views
    private lateinit var cliStatusText: TextView
    private lateinit var cliProgressBar: ProgressBar
    private lateinit var cliLogText: TextView
    private lateinit var cliLogScroll: ScrollView

    // page 6 (Complete) views
    private lateinit var completeText: TextView

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
        if (setupCompleteFile.exists()) {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            return
        }

        rootLayout = FrameLayout(this).apply {
            setBackgroundColor(NC.BG)
            layoutParams = ViewGroup.LayoutParams(MATCH, MATCH)
        }

        pageContainer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
        }
        rootLayout.addView(pageContainer)

        setContentView(rootLayout)

        // Apply Insets
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            rootLayout.setPadding(0, 0, 0, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(rootLayout)

        deployScripts()
        showPage(0)
    }

    // ── Page Rendering ────────────────────────────────────────────────────────

    private fun showPage(index: Int) {
        currentPageIndex = index
        pageContainer.removeAllViews()

        val view = when (index) {
            0 -> buildIntroPage()
            1 -> buildSlideshowPage()
            2 -> buildIsolationPage()
            3 -> buildDebianBasePage()
            4 -> buildCliSetupPage()
            5 -> buildCompletePage()
            else -> buildIntroPage()
        }

        pageContainer.addView(view)
    }

    // ── Page 1: Brand Intro ──────────────────────────────────────────────────
    private fun buildIntroPage(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
        }

        // Center squircle container for icon
        val iconCard = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = roundedBg(NC.SURFACE_VAR, NC.BORDER, dp(24))
            setPadding(dp(24))
            layoutParams = LinearLayout.LayoutParams(dp(140), dp(140)).apply {
                bottomMargin = dp(32)
            }
        }
        val iconIv = ImageView(this).apply {
            setImageResource(R.drawable.ic_laptop)
            setColorFilter(NC.PRIMARY)
            layoutParams = LinearLayout.LayoutParams(dp(64), dp(64))
        }
        iconCard.addView(iconIv)
        root.addView(iconCard)

        val nameTv = TextView(this).apply {
            text = "NativeCode"
            textSize = 36f
            setTextColor(NC.PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(12))
        }
        root.addView(nameTv)

        val taglineTv = TextView(this).apply {
            text = "Portable Linux & AI Developer Environment"
            textSize = 16f
            setTextColor(NC.ON_SURF_VAR)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(64))
        }
        root.addView(taglineTv)

        val btn = primaryButton("Get Started") {
            showPage(1)
        }
        root.addView(btn)

        return root
    }

    // ── Page 2: Feature Slideshow ─────────────────────────────────────────────
    private fun buildSlideshowPage(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24))
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
        }

        // Top Header
        root.addView(smallHeader("Core Capabilities"))

        val contentFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f)
        }
        root.addView(contentFrame)

        // Slides data
        val slides = listOf(
            SlideData(R.drawable.ic_shield, "Bypass Android W^X", "Packages critical execution binaries ('proot', 'bash', 'loader') as JNI libraries to run guest Linux without Root access on modern SDK 36 devices."),
            SlideData(R.drawable.ic_folder, "Local Workspaces", "Fully featured local file management system with folder trees, new file triggers, and native Git status diff checks inside the GUI."),
            SlideData(R.drawable.ic_palette, "XFCE Graphic Server", "Integrated Termux X11 graphic display support. Launch a fully loaded XFCE4 desktop directly on top of PRoot container services."),
            SlideData(R.drawable.ic_smart_toy, "Agentic AI Harness", "Provides direct integration and terminal environments for Claude Code, Aider, Cline, and Codex CLI to automate developer tasks.")
        )

        var activeSlide = 0

        // Slider view container
        fun renderSlide(slideIdx: Int): View {
            val slide = slides[slideIdx]
            val container = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(16))
            }
            val slideIcon = ImageView(this).apply {
                setImageResource(slide.iconResId)
                setColorFilter(NC.PRIMARY)
                layoutParams = LinearLayout.LayoutParams(dp(72), dp(72)).apply { bottomMargin = dp(24) }
            }
            val slideTitle = TextView(this).apply {
                text = slide.title
                textSize = 22f
                setTextColor(NC.PRIMARY)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, dp(12))
            }
            val slideDesc = TextView(this).apply {
                text = slide.desc
                textSize = 14f
                setTextColor(NC.ON_SURF_VAR)
                gravity = Gravity.CENTER
                setPadding(dp(8), 0, dp(8), 0)
            }
            container.addView(slideIcon)
            container.addView(slideTitle)
            container.addView(slideDesc)
            return container
        }

        contentFrame.addView(renderSlide(0))

        // Progress Dots Row
        val dotsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply {
                bottomMargin = dp(32)
            }
        }
        val dotViews = mutableListOf<View>()
        for (i in slides.indices) {
            val dot = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(8), dp(8)).apply {
                    leftMargin = dp(4)
                    rightMargin = dp(4)
                }
                background = circleDrawable(if (i == 0) NC.PRIMARY else NC.BORDER_VAR)
            }
            dotsRow.addView(dot)
            dotViews.add(dot)
        }
        root.addView(dotsRow)

        // Switch slideshow animation/handler
        val timer = mainHandler.postDelayed(object : Runnable {
            override fun run() {
                if (currentPageIndex != 1) return // Stop if navigated away
                activeSlide = (activeSlide + 1) % slides.size
                contentFrame.removeAllViews()
                contentFrame.addView(renderSlide(activeSlide))
                for (i in dotViews.indices) {
                    dotViews[i].background = circleDrawable(if (i == activeSlide) NC.PRIMARY else NC.BORDER_VAR)
                }
                mainHandler.postDelayed(this, 3000)
            }
        }, 3000)

        // Actions
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val prevBtn = secondaryButton("Back") {
            showPage(0)
        }
        prevBtn.layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).apply { rightMargin = dp(8) }
        val nextBtn = primaryButton("Continue") {
            showPage(2)
        }
        nextBtn.layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        btnRow.addView(prevBtn)
        btnRow.addView(nextBtn)
        root.addView(btnRow)

        return root
    }

    private data class SlideData(val iconResId: Int, val title: String, val desc: String)

    // ── Page 3: Architecture Isolation ────────────────────────────────────────
    private fun buildIsolationPage(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24))
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
        }

        root.addView(smallHeader("Isolation Architecture"))

        val spacer = View(this).apply { layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f) }
        root.addView(spacer)

        // Option A: PROOT (RECOMMENDED)
        val prootCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(NC.SURFACE, NC.PRIMARY, dp(12))
            setPadding(dp(16))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(16) }
        }
        val prootTop = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        val prootIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_shield)
            setColorFilter(NC.PRIMARY)
            layoutParams = LinearLayout.LayoutParams(dp(20), dp(20)).apply { rightMargin = dp(8) }
        }
        val prootTitle = TextView(this).apply { text = "PROOT"; textSize = 16f; setTextColor(NC.ON_SURFACE); typeface = Typeface.DEFAULT_BOLD }
        val prootBadge = textBadge("RECOMMENDED", Color.argb(51, 124, 58, 237), NC.PRIMARY)
        prootTop.addView(prootIcon); prootTop.addView(prootTitle)
        prootTop.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })
        prootTop.addView(prootBadge)
        prootCard.addView(prootTop)

        val prootDesc = TextView(this).apply {
            text = "User-space isolation mode. Runs completely rootless by mapping system paths via proot binder scripts. Perfectly stable for standard development."
            textSize = 13f; setTextColor(NC.ON_SURF_VAR); setPadding(0, dp(8), 0, 0)
        }
        prootCard.addView(prootDesc)
        root.addView(prootCard)

        // Option B: CHROOT (Coming soon)
        val chrootCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(NC.SURFACE, NC.BORDER, dp(12))
            setPadding(dp(16))
            alpha = 0.5f
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        val chrootTop = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        val chrootIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_lock_open)
            setColorFilter(NC.TERTIARY)
            layoutParams = LinearLayout.LayoutParams(dp(20), dp(20)).apply { rightMargin = dp(8) }
        }
        val chrootTitle = TextView(this).apply { text = "CHROOT"; textSize = 16f; setTextColor(NC.ON_SURFACE); typeface = Typeface.DEFAULT_BOLD }
        val chrootBadge = textBadge("COMING SOON", NC.SURFACE_VAR, NC.TERTIARY)
        chrootTop.addView(chrootIcon); chrootTop.addView(chrootTitle)
        chrootTop.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })
        chrootTop.addView(chrootBadge)
        chrootCard.addView(chrootTop)

        val chrootDesc = TextView(this).apply {
            text = "Full system performance. Requires root access on your Android device. Direct host hardware mappings and raw speed."
            textSize = 13f; setTextColor(NC.ON_SURF_VAR); setPadding(0, dp(8), 0, 0)
        }
        chrootCard.addView(chrootDesc)
        root.addView(chrootCard)

        val spacer2 = View(this).apply { layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f) }
        root.addView(spacer2)

        // Buttons
        val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val prevBtn = secondaryButton("Back") { showPage(1) }
        prevBtn.layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).apply { rightMargin = dp(8) }
        val nextBtn = primaryButton("Configure & Install") {
            showPage(3)
            if (!isDebianBaseSetupStarted) {
                isDebianBaseSetupStarted = true
                runDebianBaseSetup()
            }
        }
        nextBtn.layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        btnRow.addView(prevBtn); btnRow.addView(nextBtn)
        root.addView(btnRow)

        return root
    }

    // ── Page 4: Debian Base Extraction ────────────────────────────────────────
    private fun buildDebianBasePage(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24))
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
        }

        root.addView(smallHeader("Base Environment Setup"))

        // Status Card
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(NC.SURFACE, NC.BORDER, dp(12))
            setPadding(dp(16))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(16) }
        }
        baseStatusText = TextView(this).apply {
            text = "Initializing base environment extraction..."
            textSize = 13f; setTextColor(NC.ON_SURFACE); typeface = Typeface.MONOSPACE
            setPadding(0, 0, 0, dp(8))
        }
        baseProgressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            layoutParams = LinearLayout.LayoutParams(MATCH, dp(6))
        }
        card.addView(baseStatusText)
        card.addView(baseProgressBar)
        root.addView(card)

        // Live Console log
        baseLogScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f).apply { bottomMargin = dp(16) }
            background = roundedBg(NC.LOGBG, NC.BORDER, dp(8))
        }
        baseLogText = TextView(this).apply {
            text = ""
            textSize = 11f; setTextColor(NC.SECONDARY); typeface = Typeface.MONOSPACE
            setPadding(dp(12))
        }
        baseLogScroll.addView(baseLogText)
        root.addView(baseLogScroll)

        // Next Button (initially disabled)
        val nextBtn = primaryButton("Next: AI CLI Tools") {
            showPage(4)
            if (!isCliToolsSetupStarted) {
                isCliToolsSetupStarted = true
                runCliToolsSetup()
            }
        }
        nextBtn.id = View.generateViewId()
        nextBtn.isEnabled = false
        nextBtn.alpha = 0.5f
        root.addView(nextBtn)

        return root
    }

    private fun runDebianBaseSetup() {
        executor.execute {
            try {
                updateBaseStatus("A. Preparing Directories...")
                val usrDir = File(filesDir, "usr")
                val tmpDir = File(usrDir, "tmp")
                val etcDir = File(usrDir, "etc")
                val varDir = File(usrDir, "var")
                val homeDir = File(filesDir, "home")
                tmpDir.mkdirs(); etcDir.mkdirs(); homeDir.mkdirs()
                File(varDir, "log/apt").mkdirs()
                File(varDir, "lib/dpkg").mkdirs()

                updateBaseStatus("B. Extracting Bootstrap Assets...")
                val tarFile = File(filesDir, "bootstrap.tar")
                if (!tarFile.exists()) {
                    assets.open("bootstrap.tar").use { input ->
                        FileOutputStream(tarFile).use { output -> input.copyTo(output) }
                    }
                }

                // Extract
                val tarProcess = Runtime.getRuntime().exec(
                    arrayOf("tar", "-xf", tarFile.absolutePath, "-C", filesDir.absolutePath)
                )
                tarProcess.waitFor()
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

                updateBaseStatus("C. Deploying Host Configs...")
                deployScripts()
                File(etcDir, "resolv.conf").writeText("nameserver 8.8.8.8\nnameserver 8.8.4.4\n")

                updateBaseStatus("D. Initializing Host Environment...")
                val nld = applicationInfo.nativeLibraryDir
                val proot = File(nld, "libproot.so").absolutePath
                val bash = File(nld, "libbash.so").absolutePath
                check(runShellCommand(arrayOf(proot, bash, File(homeDir, "setup_termux.sh").absolutePath), isCliSetup = false) == 0) { "Host setup failed" }

                updateBaseStatus("E. Provisioning Debian Guest Container...")
                val debBytes = assets.open("scripts/setup_debian_family.sh").use { it.readBytes() }
                val debPayload = Base64.encodeToString(debBytes, Base64.NO_WRAP)
                check(runShellCommand(arrayOf(bash, File(homeDir, "flux_install.sh").absolutePath, "debian", debPayload), isCliSetup = false) == 0) { "Debian guest install failed" }

                updateBaseStatus("F. Configuring Hardware Accel...")
                val hwScript = File(File(usrDir, "tmp"), "setup_hw_accel_debian.sh")
                assets.open("scripts/setup_hw_accel_debian.sh").use { input -> FileOutputStream(hwScript).use { input.copyTo(it) } }
                hwScript.setExecutable(true)
                check(runShellCommand(arrayOf(
                    "/data/data/com.ivarna.nativecode/files/usr/bin/python",
                    "/data/data/com.ivarna.nativecode/files/usr/bin/proot-distro",
                    "login", "debian", "--shared-tmp", "--",
                    "env", "FLUX_GPU=virgl", "bash", "/tmp/setup_hw_accel_debian.sh"
                ), isCliSetup = false) == 0) { "GPU setup failed" }

                updateBaseStatus("G. Customizing Guest Environment...")
                val customScript = File(File(usrDir, "tmp"), "setup_customization_debian.sh")
                assets.open("scripts/setup_customization_debian.sh").use { input -> FileOutputStream(customScript).use { input.copyTo(it) } }
                customScript.setExecutable(true)
                check(runShellCommand(arrayOf(
                    "/data/data/com.ivarna.nativecode/files/usr/bin/python",
                    "/data/data/com.ivarna.nativecode/files/usr/bin/proot-distro",
                    "login", "debian", "--shared-tmp", "--",
                    "env", "FLUX_THEME=dark", "bash", "/tmp/setup_customization_debian.sh"
                ), isCliSetup = false) == 0) { "Customization failed" }

                updateBaseStatus("Debian Base Setup Successful!")
                mainHandler.post {
                    baseProgressBar.visibility = View.GONE
                    // Find button inside the active page layout
                    val layout = pageContainer.getChildAt(0) as? LinearLayout
                    if (layout != null) {
                        for (i in 0 until layout.childCount) {
                            val child = layout.getChildAt(i)
                            if (child is TextView && child.text.contains("Next: AI")) {
                                child.isEnabled = true
                                child.alpha = 1f
                                break
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("Onboarding", "Debian Base Setup failed", e)
                updateBaseStatus("Error: ${e.message}")
            }
        }
    }

    private fun updateBaseStatus(text: String) {
        mainHandler.post {
            if (::baseStatusText.isInitialized) {
                baseStatusText.text = text
                baseLogText.append("\n>>> $text\n")
                baseLogScroll.post { baseLogScroll.fullScroll(View.FOCUS_DOWN) }
            }
        }
    }

    // ── Page 5: AI CLI Tools Provisioning ──────────────────────────────────────
    private fun buildCliSetupPage(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24))
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
        }

        root.addView(smallHeader("AI CLI Tools Setup"))

        // Status Card
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(NC.SURFACE, NC.BORDER, dp(12))
            setPadding(dp(16))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(16) }
        }
        cliStatusText = TextView(this).apply {
            text = "Starting package setup..."
            textSize = 13f; setTextColor(NC.ON_SURFACE); typeface = Typeface.MONOSPACE
            setPadding(0, 0, 0, dp(8))
        }
        cliProgressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            layoutParams = LinearLayout.LayoutParams(MATCH, dp(6))
        }
        card.addView(cliStatusText)
        card.addView(cliProgressBar)
        root.addView(card)

        // Monospace code block terminal
        cliLogScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f).apply { bottomMargin = dp(16) }
            background = roundedBg(NC.LOGBG, NC.BORDER, dp(8))
        }
        cliLogText = TextView(this).apply {
            text = ""
            textSize = 11f; setTextColor(NC.SECONDARY); typeface = Typeface.MONOSPACE
            setPadding(dp(12))
        }
        cliLogScroll.addView(cliLogText)
        root.addView(cliLogScroll)

        // Continue Button
        val nextBtn = primaryButton("Next: Complete Setup") {
            showPage(5)
        }
        nextBtn.isEnabled = false
        nextBtn.alpha = 0.5f
        root.addView(nextBtn)

        return root
    }

    private fun runCliToolsSetup() {
        executor.execute {
            try {
                updateCliStatus("Deploying CLI Tools setup script...")
                val usrDir = File(filesDir, "usr")
                val cliScript = File(File(usrDir, "tmp"), "setup_cli_tools.sh")
                assets.open("scripts/setup_cli_tools.sh").use { input -> FileOutputStream(cliScript).use { input.copyTo(it) } }
                cliScript.setExecutable(true)

                updateCliStatus("Running installation inside Debian (NVM, Node v26, opencode-ai, @openai/codex)...")
                val runCode = runShellCommand(arrayOf(
                    "/data/data/com.ivarna.nativecode/files/usr/bin/python",
                    "/data/data/com.ivarna.nativecode/files/usr/bin/proot-distro",
                    "login", "debian", "--shared-tmp", "--",
                    "bash", "/tmp/setup_cli_tools.sh"
                ), isCliSetup = true)

                if (runCode == 0) {
                    updateCliStatus("AI CLI Tools Provisioned Successfully!")
                    mainHandler.post {
                        cliProgressBar.visibility = View.GONE
                        val layout = pageContainer.getChildAt(0) as? LinearLayout
                        if (layout != null) {
                            for (i in 0 until layout.childCount) {
                                val child = layout.getChildAt(i)
                                if (child is TextView && child.text.contains("Next: Complete")) {
                                    child.isEnabled = true
                                    child.alpha = 1f
                                    break
                                }
                            }
                        }
                    }
                } else {
                    updateCliStatus("CLI Tools Setup failed with exit code $runCode")
                }
            } catch (e: Exception) {
                Log.e("Onboarding", "CLI Tools Setup failed", e)
                updateCliStatus("Error: ${e.message}")
            }
        }
    }

    private fun updateCliStatus(text: String) {
        mainHandler.post {
            if (::cliStatusText.isInitialized) {
                cliStatusText.text = text
                cliLogText.append("\n>>> $text\n")
                cliLogScroll.post { cliLogScroll.fullScroll(View.FOCUS_DOWN) }
            }
        }
    }

    // ── Page 6: Complete ──────────────────────────────────────────────────────
    private fun buildCompletePage(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
        }

        // Success Icon (Pulsing Checkmark)
        val iconBox = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = roundedBg(Color.parseColor("#0d2a2a"), NC.SECONDARY, dp(32))
            setPadding(dp(24))
            layoutParams = LinearLayout.LayoutParams(dp(100), dp(100)).apply {
                bottomMargin = dp(32)
            }
        }
        val iconIv = ImageView(this).apply {
            setImageResource(R.drawable.ic_check_circle)
            setColorFilter(NC.SECONDARY)
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        }
        iconBox.addView(iconIv)
        root.addView(iconBox)
        pulseView(iconBox)

        val title = TextView(this).apply {
            text = "Setup Successful!"
            textSize = 28f
            setTextColor(NC.ON_SURFACE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(12))
        }
        root.addView(title)

        completeText = TextView(this).apply {
            text = "Guest OS: Debian Trixie (Ready)\nNodeJS: v26 (Ready)\nAI packages: opencode-ai, @openai/codex"
            textSize = 14f
            setTextColor(NC.ON_SURF_VAR)
            gravity = Gravity.CENTER
            typeface = Typeface.MONOSPACE
            setPadding(0, 0, 0, dp(48))
        }
        root.addView(completeText)

        // Write marker file to declare setup is completely done
        File(filesDir, "setup_complete").createNewFile()

        val btn = primaryButton("Launch Environment") {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
        root.addView(btn)

        return root
    }

    // ── Helper execution scripts ──────────────────────────────────────────────

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
            Log.e("Onboarding", "Failed to deploy scripts", e)
        }
    }

    private fun moveDirectoryContents(source: File, target: File) {
        source.listFiles()?.forEach { file ->
            val dest = File(target, file.name)
            if (file.isDirectory) { dest.mkdirs(); moveDirectoryContents(file, dest) }
            else file.renameTo(dest)
        }
    }

    private fun runShellCommand(cmd: Array<String>, isCliSetup: Boolean): Int {
        val adjusted = if (cmd.isNotEmpty() && cmd[0].startsWith("/data/data/"))
            arrayOf("/system/bin/linker64") + cmd else cmd
        val pb = ProcessBuilder(*adjusted)
        val env = pb.environment()
        val nld = applicationInfo.nativeLibraryDir
        env["PATH"] = "$nld:/data/data/com.ivarna.nativecode/files/usr/bin:/system/bin"
        env["PD_PROOT_BIN"] = File(nld, "libproot.so").absolutePath
        env["PROOT_LOADER"] = File(nld, "libloader.so").absolutePath
        env["LD_LIBRARY_PATH"] = "/data/data/com.ivarna.nativecode/files/usr/lib:/data/data/com.ivarna.nativecode/files/usr/opt/virglrenderer-android/lib"
        env["LD_PRELOAD"] = "/data/data/com.ivarna.nativecode/files/usr/lib/libtermux-exec.so"
        env["PREFIX"] = "/data/data/com.ivarna.nativecode/files/usr"
        env["HOME"] = "/data/data/com.ivarna.nativecode/files/home"
        env["TMPDIR"] = "/data/data/com.ivarna.nativecode/files/usr/tmp"
        env["PROOT_TMP_DIR"] = "/data/data/com.ivarna.nativecode/files/usr/tmp"
        env["TERMUX_APP__PACKAGE_NAME"] = "com.ivarna.nativecode"
        env["TERMUX_X11_APK_PATH"] = applicationInfo.sourceDir
        env["TERMUX_X11_OVERRIDE_PACKAGE"] = "com.ivarna.nativecode"
        env["TERMUX__PREFIX"] = "/data/data/com.ivarna.nativecode/files/usr"
        env["TERMUX__HOME"] = "/data/data/com.ivarna.nativecode/files/home"
        env["SSL_CERT_FILE"] = "/data/data/com.ivarna.nativecode/files/usr/etc/tls/cert.pem"
        env["CURL_CA_BUNDLE"] = "/data/data/com.ivarna.nativecode/files/usr/etc/tls/cert.pem"
        pb.redirectErrorStream(true)
        val proc = pb.start()
        val buf = ByteArray(1024)
        val stream = proc.inputStream
        var read: Int
        while (stream.read(buf).also { read = it } != -1) {
            val out = String(buf, 0, read)
            mainHandler.post {
                if (isCliSetup) {
                    if (::cliLogText.isInitialized) {
                        cliLogText.append(out)
                        cliLogScroll.post { cliLogScroll.fullScroll(View.FOCUS_DOWN) }
                    }
                } else {
                    if (::baseLogText.isInitialized) {
                        baseLogText.append(out)
                        baseLogScroll.post { baseLogScroll.fullScroll(View.FOCUS_DOWN) }
                    }
                }
            }
        }
        return proc.waitFor()
    }

    // ── Helper UI components ──────────────────────────────────────────────────

    private fun smallHeader(title: String): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(16))
        }
        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_laptop)
            setColorFilter(NC.PRIMARY)
            layoutParams = LinearLayout.LayoutParams(dp(22), dp(22)).apply { rightMargin = dp(8) }
        }
        val text = TextView(this).apply {
            this.text = title
            textSize = 20f
            setTextColor(NC.PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
        }
        root.addView(icon); root.addView(text)
        return root
    }

    private fun primaryButton(label: String, onClick: () -> Unit): TextView = TextView(this).apply {
        text = label
        textSize = 15f
        setTextColor(Color.WHITE)
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        background = roundedBg(NC.PRIMARY_CON, NC.PRIMARY_CON, dp(24))
        setPadding(dp(16), dp(14), dp(16), dp(14))
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        setOnClickListener { onClick() }
    }

    private fun secondaryButton(label: String, onClick: () -> Unit): TextView = TextView(this).apply {
        text = label
        textSize = 14f
        setTextColor(NC.ON_SURFACE)
        gravity = Gravity.CENTER
        background = roundedBg(NC.SURFACE, NC.BORDER, dp(20))
        setPadding(dp(16), dp(10), dp(16), dp(10))
        setOnClickListener { onClick() }
    }

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()
    private fun roundedBg(fill: Int, stroke: Int, r: Int) = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; setColor(fill); setStroke(dp(1), stroke); cornerRadius = r.toFloat() }
    private fun textBadge(text: String, bg: Int, fg: Int) = TextView(this).apply { this.text = text; textSize = 10f; setTextColor(fg); typeface = Typeface.MONOSPACE; background = roundedBg(bg, fg, dp(4)); setPadding(dp(6), dp(2), dp(6), dp(2)) }
    private fun circleDrawable(color: Int): android.graphics.drawable.ShapeDrawable = android.graphics.drawable.ShapeDrawable(OvalShape()).apply { paint.color = color }

    private fun pulseView(v: View) {
        val anim = ObjectAnimator.ofFloat(v, "alpha", 0.4f, 1f).apply {
            duration = 1500
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
        }
        anim.start()
    }

    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
}
