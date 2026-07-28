package com.ivarna.nativecode

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.shapes.OvalShape
import android.os.Bundle
import android.view.MotionEvent
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
import android.system.Os
import com.ivarna.nativecode.terminal.GpuAccelDetector
import com.ivarna.nativecode.terminal.HostCommandBuilder
import com.ivarna.nativecode.terminal.TermuxHostPaths
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.Executors

class OnboardingActivity : AppCompatActivity() {

    private lateinit var rootLayout: FrameLayout
    private lateinit var pageContainer: FrameLayout

    // Onboarding page index (0..4): intro → slideshow → isolation → full setup → complete
    private var currentPageIndex = 0

    private val executor = Executors.newCachedThreadPool()
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    // ── Shared setup status state ──────────────────────────────────────────────
    private var isDebianBaseSetupStarted = false
    private var enableDebianCustomization = true

    // ── Linux isolation method selected on page 3 ─────────────────────────────
    // "proot" (default, rootless) or "chroot" (requires KernelSU/Magisk root)
    private var selectedIsolationMethod = "proot"

    // page 3 (Full Environment Setup: base + AI CLIs) views
    private lateinit var baseStatusText: TextView
    private lateinit var baseProgressBar: ProgressBar
    private lateinit var baseLogText: TextView
    private lateinit var baseLogScroll: ScrollView
    private lateinit var baseNextBtn: View

    // page 4 (Complete) views
    private lateinit var completeText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val isForced = intent.getBooleanExtra("force_onboarding", false)
        val setupComplete = File(filesDir, "setup_complete").exists() ||
                getSharedPreferences("nativecode_prefs", MODE_PRIVATE).getBoolean("onboarding_completed", false)

        if (!isForced && setupComplete) {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            return
        }

        // Make window status/navigation bar transparent for immersive dark layout
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false

        rootLayout = FrameLayout(this).apply {
            background = roundedBg(NC.BG, NC.BG, 0)
            layoutParams = ViewGroup.LayoutParams(MATCH, MATCH)
        }

        pageContainer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
        }
        rootLayout.addView(pageContainer)

        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { v, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.setPadding(0, statusBars.top, 0, navBars.bottom)
            insets
        }

        setContentView(rootLayout)

        deployScripts()
        val startPage = intent.getIntExtra("target_page", 0)
        showPage(startPage)
    }

    private fun showPage(index: Int) {
        currentPageIndex = index
        pageContainer.removeAllViews()

        val view = when (index) {
            0 -> buildIntroPage()
            1 -> buildSlideshowPage()
            2 -> buildIsolationPage()
            3 -> buildDebianBasePage()
            4, 5 -> buildCompletePage() // 5 = legacy target_page after AI-tools page removed
            else -> buildIntroPage()
        }

        view.alpha = 0f
        pageContainer.addView(view)
        view.animate().alpha(1f).setDuration(250).start()
    }

    // ── Page 1: Brand Intro ───────────────────────────────────────────────────
    private fun buildIntroPage(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
        }

        val topSpacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f)
        }
        root.addView(topSpacer)

        // Center sharp Cyber-Brutalist container for logo
        val logoCard = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = cyberBrutalistBg(
                fillColor = NC.SURFACE_CONTAINER,
                strokeColor = NC.BORDER,
                shadowColor = NC.SURFACE_BRIGHT,
                offsetDp = 6,
                cornerRadiusDp = 0,
                rightFaceColor = NC.OUTLINE_VAR
            )
            setPadding(dp(20), dp(20), dp(20), dp(20))
            layoutParams = LinearLayout.LayoutParams(dp(170), dp(170)).apply {
                bottomMargin = dp(32)
            }
        }
        val logoIv = ImageView(this).apply {
            setImageResource(R.drawable.logo_highres)
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(dp(130), dp(130))
        }
        logoCard.addView(logoIv)
        root.addView(logoCard)

        val nameTv = TextView(this).apply {
            text = "NativeCode"
            textSize = 38f
            setTextColor(NC.PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            letterSpacing = -0.01f
            setPadding(0, 0, 0, dp(12))
        }
        root.addView(nameTv)

        val taglineTv = TextView(this).apply {
            text = "Portable Linux & AI Developer Environment"
            textSize = 14f
            setTextColor(NC.ON_SURFACE)
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(36))
        }
        root.addView(taglineTv)

        // Feature tag pills row (sharp Cyber-Brutalist design)
        val tagRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(48))
        }
        tagRow.addView(featureBadge(R.drawable.ic_terminal, "Debian Trixie"))
        tagRow.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(8), 1) })
        tagRow.addView(featureBadge(R.drawable.ic_smart_toy, "AI & LLMs"))
        tagRow.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(8), 1) })
        tagRow.addView(featureBadge(R.drawable.ic_laptop, "Dev Suite"))
        root.addView(tagRow)

        val botSpacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f)
        }
        root.addView(botSpacer)

        val btn = primaryButton("Get Started", R.drawable.ic_arrow_right) {
            showPage(1)
        }
        root.addView(btn)

        return root
    }

    // ── Page 2: Feature Slideshow ─────────────────────────────────────────────
    private fun buildSlideshowPage(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
        }

        root.addView(smallHeader("Core Capabilities", R.drawable.ic_extension))

        val contentFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f).apply {
                topMargin = dp(12)
                bottomMargin = dp(16)
            }
        }
        root.addView(contentFrame)

        val slides = listOf(
            SlideData(
                category = "WORKSPACE",
                title = "Project Management",
                iconResId = R.drawable.ic_folder,
                desc = "Multi-repo project tree manager with quick creation, workspace switching, and file organization.",
                type = PreviewType.PROJECT_TREE
            ),
            SlideData(
                category = "AI ENGINE",
                title = "AI Tools & LLM Harness",
                iconResId = R.drawable.ic_smart_toy,
                desc = "Run Claude Code, Codex, Aider, and OpenCode AI CLI tools directly inside your native terminal environment.",
                type = PreviewType.AI_CLI
            ),
            SlideData(
                category = "RUNTIMES",
                title = "Development Environment",
                iconResId = R.drawable.ic_laptop,
                desc = "Preconfigured Node.js 26 LTS, Python 3.12, GCC, and package managers ready for instant execution.",
                type = PreviewType.DEV_SUITE
            ),
            SlideData(
                category = "DESKTOP",
                title = "XFCE4 Graphic Server",
                iconResId = R.drawable.ic_display,
                desc = "Integrated Termux-X11 display server support to launch full Linux XFCE desktop GUIs on Android.",
                type = PreviewType.XFCE_GUI
            ),
            SlideData(
                category = "LINUX OS",
                title = "Debian Trixie Container",
                iconResId = R.drawable.ic_terminal,
                desc = "Native glibc Debian 13 PRoot environment with apt package manager and full Linux userland.",
                type = PreviewType.DEBIAN_ENV
            ),
            SlideData(
                category = "CONTROL",
                title = "Git Diff & Version Control",
                iconResId = R.drawable.ic_git,
                desc = "Native Git integration with real-time status tracking, visual diff inspection, and branch switching.",
                type = PreviewType.GIT_DIFF
            )
        )

        var activeSlide = 0
        var autoRunnable: Runnable? = null

        fun renderMockup(type: PreviewType): View {
            val box = FrameLayout(this).apply {
                background = cyberBrutalistBg(
                    fillColor = Color.parseColor("#121212"),
                    strokeColor = NC.BORDER,
                    shadowColor = NC.SURFACE_BRIGHT,
                    offsetDp = 6,
                    cornerRadiusDp = 0,
                    rightFaceColor = NC.OUTLINE_VAR
                )
                setPadding(dp(4), dp(4), dp(4), dp(4))
                layoutParams = LinearLayout.LayoutParams(MATCH, dp(200)).apply {
                    bottomMargin = dp(14)
                }
            }

            val imgRes = when (type) {
                PreviewType.PROJECT_TREE -> R.drawable.img_slide_workspace
                PreviewType.AI_CLI -> R.drawable.img_slide_ai
                PreviewType.DEV_SUITE -> R.drawable.img_slide_dev
                PreviewType.XFCE_GUI -> R.drawable.img_slide_xfce
                PreviewType.DEBIAN_ENV -> R.drawable.img_slide_debian
                PreviewType.GIT_DIFF -> R.drawable.img_slide_git
            }

            val iv = ImageView(this).apply {
                setImageResource(imgRes)
                scaleType = ImageView.ScaleType.FIT_CENTER
                layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
            }
            box.addView(iv)
            return box
        }

        fun renderSlideCard(slideIdx: Int): View {
            val slide = slides[slideIdx]
            val container = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = FrameLayout.LayoutParams(MATCH, WRAP).apply {
                    gravity = Gravity.CENTER
                }
            }

            // Top Card 1: Nexus Style Image Preview Card
            container.addView(renderMockup(slide.type))

            // Bottom Card 2: Capability Info & Text Card
            val textCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = cyberBrutalistBg(
                    fillColor = NC.SURFACE_CONTAINER,
                    strokeColor = NC.BORDER,
                    shadowColor = NC.SURFACE_BRIGHT,
                    offsetDp = 6,
                    cornerRadiusDp = 0,
                    rightFaceColor = NC.OUTLINE_VAR
                )
                setPadding(dp(18), dp(16), dp(18), dp(16))
                layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
            }

            // Slide Index Tag
            val tagTv = TextView(this).apply {
                text = "[ ${slide.category} — 0${slideIdx + 1} / 0${slides.size} ]"
                textSize = 10f
                setTextColor(NC.PRIMARY)
                typeface = Typeface.MONOSPACE
                setPadding(0, 0, 0, dp(8))
            }
            textCard.addView(tagTv)

            // Slide Header (Icon + Title)
            val headerRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, dp(6))
            }
            val iconIv = ImageView(this).apply {
                setImageResource(slide.iconResId)
                setColorFilter(NC.PRIMARY)
                layoutParams = LinearLayout.LayoutParams(dp(22), dp(22)).apply {
                    rightMargin = dp(8)
                }
            }
            val titleTv = TextView(this).apply {
                text = slide.title
                textSize = 16f
                setTextColor(NC.ON_SURFACE)
                typeface = Typeface.DEFAULT_BOLD
            }
            headerRow.addView(iconIv)
            headerRow.addView(titleTv)
            textCard.addView(headerRow)

            val descTv = TextView(this).apply {
                text = slide.desc
                textSize = 12f
                setTextColor(NC.ON_SURF_VAR)
                setLineSpacing(dp(2).toFloat(), 1.2f)
            }
            textCard.addView(descTv)

            container.addView(textCard)
            return container
        }

        contentFrame.addView(renderSlideCard(0))

        // Progress Dots Row
        val dotsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply {
                bottomMargin = dp(20)
            }
        }
        val dotViews = mutableListOf<View>()

        fun updateDots(selectedIdx: Int) {
            for (i in dotViews.indices) {
                val isSelected = (i == selectedIdx)
                val widthDp = if (isSelected) 24 else 8
                val params = dotViews[i].layoutParams as LinearLayout.LayoutParams
                params.width = dp(widthDp)
                dotViews[i].layoutParams = params
                dotViews[i].background = roundedBg(
                    if (isSelected) NC.PRIMARY else NC.SURFACE_HIGHEST,
                    if (isSelected) NC.PRIMARY else NC.BORDER_VAR,
                    0
                )
            }
        }

        fun switchSlide(newIdx: Int) {
            if (newIdx == activeSlide) return
            activeSlide = newIdx
            val currentView = if (contentFrame.childCount > 0) contentFrame.getChildAt(0) else null
            val nextView = renderSlideCard(newIdx)
            nextView.alpha = 0f

            if (currentView != null) {
                currentView.animate().alpha(0f).setDuration(160).withEndAction {
                    contentFrame.removeAllViews()
                    contentFrame.addView(nextView)
                    nextView.animate().alpha(1f).setDuration(200).start()
                }.start()
            } else {
                contentFrame.addView(nextView)
                nextView.animate().alpha(1f).setDuration(200).start()
            }
            updateDots(activeSlide)
        }

        for (i in slides.indices) {
            val isSelected = (i == 0)
            val dot = View(this).apply {
                val widthDp = if (isSelected) 24 else 8
                layoutParams = LinearLayout.LayoutParams(dp(widthDp), dp(8)).apply {
                    leftMargin = dp(4)
                    rightMargin = dp(4)
                }
                background = roundedBg(
                    if (isSelected) NC.PRIMARY else NC.SURFACE_HIGHEST,
                    if (isSelected) NC.PRIMARY else NC.BORDER_VAR,
                    0
                )
                isClickable = true
                setOnClickListener {
                    switchSlide(i)
                }
            }
            dotsRow.addView(dot)
            dotViews.add(dot)
        }
        root.addView(dotsRow)

        // Swipe gesture handler on contentFrame
        var downX = 0f
        contentFrame.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val deltaX = event.x - downX
                    if (deltaX < -90) { // Swipe left -> Next
                        val next = (activeSlide + 1) % slides.size
                        switchSlide(next)
                    } else if (deltaX > 90) { // Swipe right -> Prev
                        val prev = if (activeSlide > 0) activeSlide - 1 else slides.size - 1
                        switchSlide(prev)
                    }
                    true
                }
                else -> false
            }
        }

        // Auto-advance timer (4 seconds)
        autoRunnable = object : Runnable {
            override fun run() {
                if (currentPageIndex != 1) return // Stop if navigated away
                val next = (activeSlide + 1) % slides.size
                switchSlide(next)
                mainHandler.postDelayed(this, 4000)
            }
        }
        mainHandler.postDelayed(autoRunnable!!, 4000)

        // Navigation Action Buttons
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val prevBtn = secondaryButton("Back") {
            if (activeSlide > 0) {
                switchSlide(activeSlide - 1)
            } else {
                showPage(0)
            }
        }
        prevBtn.layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).apply { rightMargin = dp(8) }

        val nextBtn = primaryButton("Continue", R.drawable.ic_arrow_right) {
            if (activeSlide < slides.size - 1) {
                switchSlide(activeSlide + 1)
            } else {
                showPage(2)
            }
        }
        nextBtn.layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)

        btnRow.addView(prevBtn)
        btnRow.addView(nextBtn)
        root.addView(btnRow)

        return root
    }

    private data class SlideData(
        val category: String,
        val title: String,
        val iconResId: Int,
        val desc: String,
        val type: PreviewType
    )

    private enum class PreviewType {
        PROJECT_TREE,
        AI_CLI,
        DEV_SUITE,
        XFCE_GUI,
        DEBIAN_ENV,
        GIT_DIFF
    }

    // ── Page 3: Method ────────────────────────────────────────────────────────
    private fun buildIsolationPage(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
        }

        root.addView(smallHeader("Method", R.drawable.ic_shield))

        val spacer = View(this).apply { layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f) }
        root.addView(spacer)

        // Helper to update selection state of the two method cards
        fun updateIsolationCardSelections(selectedMethod: String,
                prootCardRef: LinearLayout, chrootCardRef: LinearLayout) {
            if (selectedMethod == "proot") {
                prootCardRef.background = cyberBrutalistBg(
                    fillColor = NC.SURFACE_CONTAINER, strokeColor = NC.PRIMARY,
                    shadowColor = NC.SURFACE_BRIGHT, offsetDp = 6,
                    cornerRadiusDp = 0, rightFaceColor = NC.OUTLINE_VAR)
                prootCardRef.alpha = 1f
                chrootCardRef.background = cyberBrutalistBg(
                    fillColor = NC.SURFACE_LOW, strokeColor = NC.BORDER,
                    shadowColor = NC.SURFACE_BRIGHT, offsetDp = 6,
                    cornerRadiusDp = 0, rightFaceColor = NC.OUTLINE_VAR)
                chrootCardRef.alpha = 0.65f
            } else {
                chrootCardRef.background = cyberBrutalistBg(
                    fillColor = NC.SURFACE_CONTAINER, strokeColor = NC.SECONDARY,
                    shadowColor = NC.SURFACE_BRIGHT, offsetDp = 6,
                    cornerRadiusDp = 0, rightFaceColor = NC.OUTLINE_VAR)
                chrootCardRef.alpha = 1f
                prootCardRef.background = cyberBrutalistBg(
                    fillColor = NC.SURFACE_LOW, strokeColor = NC.BORDER,
                    shadowColor = NC.SURFACE_BRIGHT, offsetDp = 6,
                    cornerRadiusDp = 0, rightFaceColor = NC.OUTLINE_VAR)
                prootCardRef.alpha = 0.65f
            }
        }

        // Option A: PROOT (RECOMMENDED)
        val prootCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cyberBrutalistBg(
                fillColor = NC.SURFACE_CONTAINER,
                strokeColor = NC.PRIMARY,
                shadowColor = NC.SURFACE_BRIGHT,
                offsetDp = 6,
                cornerRadiusDp = 0,
                rightFaceColor = NC.OUTLINE_VAR
            )
            setPadding(dp(20), dp(18), dp(20), dp(18))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(16) }
            isClickable = true
            isFocusable = true
        }
        val prootTop = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val prootIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_shield_thick)
            setColorFilter(NC.PRIMARY)
            layoutParams = LinearLayout.LayoutParams(dp(22), dp(22)).apply { rightMargin = dp(10) }
        }
        val prootTitle = TextView(this).apply {
            text = "PROOT"
            textSize = 16f
            setTextColor(NC.ON_SURFACE)
            typeface = Typeface.DEFAULT_BOLD
        }
        val prootBadge = textBadge("RECOMMENDED", NC.PRIMARY_CON, NC.ON_PRIMARY_CON)
        prootTop.addView(prootIcon); prootTop.addView(prootTitle)
        prootTop.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })
        prootTop.addView(prootBadge)
        prootCard.addView(prootTop)

        val prootDesc = TextView(this).apply {
            text = "User-space isolation mode. Runs completely rootless by mapping system paths via proot binder scripts. Perfectly stable for standard development."
            textSize = 13f
            setTextColor(NC.ON_SURF_VAR)
            setLineSpacing(dp(2).toFloat(), 1.25f)
            setPadding(0, dp(10), 0, 0)
        }
        prootCard.addView(prootDesc)
        root.addView(prootCard)

        // Option B: CHROOT (Root Required via KernelSU/Magisk)
        val chrootCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cyberBrutalistBg(
                fillColor = NC.SURFACE_LOW,
                strokeColor = NC.BORDER,
                shadowColor = NC.SURFACE_BRIGHT,
                offsetDp = 6,
                cornerRadiusDp = 0,
                rightFaceColor = NC.OUTLINE_VAR
            )
            setPadding(dp(20), dp(18), dp(20), dp(18))
            alpha = 0.65f
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
            isClickable = true
            isFocusable = true
        }
        val chrootTop = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val chrootIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_lock_open)
            setColorFilter(NC.SECONDARY)
            layoutParams = LinearLayout.LayoutParams(dp(22), dp(22)).apply { rightMargin = dp(10) }
        }
        val chrootTitle = TextView(this).apply {
            text = "CHROOT"
            textSize = 16f
            setTextColor(NC.ON_SURFACE)
            typeface = Typeface.DEFAULT_BOLD
        }
        val chrootBadge = textBadge("ROOT REQUIRED", NC.SURFACE_HIGHEST, NC.SECONDARY)
        chrootTop.addView(chrootIcon); chrootTop.addView(chrootTitle)
        chrootTop.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })
        chrootTop.addView(chrootBadge)
        chrootCard.addView(chrootTop)

        val chrootDesc = TextView(this).apply {
            text = "Kernel-level isolation via Linux chroot(2). Requires KernelSU or Magisk root. Maximum hardware compatibility, native glibc performance."
            textSize = 13f
            setTextColor(NC.ON_SURF_VAR)
            setLineSpacing(dp(2).toFloat(), 1.25f)
            setPadding(0, dp(10), 0, 0)
        }
        chrootCard.addView(chrootDesc)
        root.addView(chrootCard)

        // Wire up click listeners for card selection
        prootCard.setOnClickListener {
            selectedIsolationMethod = "proot"
            updateIsolationCardSelections("proot", prootCard, chrootCard)
        }
        chrootCard.setOnClickListener {
            selectedIsolationMethod = "chroot"
            updateIsolationCardSelections("chroot", prootCard, chrootCard)
        }

        // Customization Script Toggle Card (Cyber-Brutalist Design)
        val customToggleCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = cyberBrutalistBg(
                fillColor = NC.SURFACE_CONTAINER,
                strokeColor = NC.BORDER,
                shadowColor = NC.SURFACE_BRIGHT,
                offsetDp = 6,
                cornerRadiusDp = 0,
                rightFaceColor = NC.OUTLINE_VAR
            )
            setPadding(dp(18), dp(16), dp(18), dp(16))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply {
                topMargin = dp(16)
                bottomMargin = dp(16)
            }
        }
        val customTextCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).apply { rightMargin = dp(12) }
        }
        val customTitle = TextView(this).apply {
            text = "Debian Customization Script"
            textSize = 14f
            setTextColor(NC.ON_SURFACE)
            typeface = Typeface.DEFAULT_BOLD
        }
        val customSub = TextView(this).apply {
            text = "Apply desktop themes & shell aliases (setup_customization_debian.sh)"
            textSize = 11f
            setTextColor(NC.ON_SURF_VAR)
        }
        customTextCol.addView(customTitle)
        customTextCol.addView(customSub)

        val customToggle = CustomBrutalistToggle(this, enableDebianCustomization) { isChecked ->
            enableDebianCustomization = isChecked
        }.apply {
            isEnabled = false
            isClickable = false
            alpha = 0.75f
        }
        customToggleCard.addView(customTextCol)
        customToggleCard.addView(customToggle)
        root.addView(customToggleCard)

        val spacer2 = View(this).apply { layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f) }
        root.addView(spacer2)

        // Buttons
        val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val prevBtn = secondaryButton("Back") { showPage(1) }
        prevBtn.layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).apply { rightMargin = dp(8) }
        val nextBtn = primaryButton("Configure & Install", R.drawable.ic_arrow_right) {
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

    // ── Page 3: Full Environment Setup (base + AI CLIs) ────────────────────────
    private fun buildDebianBasePage(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
        }

        root.addView(smallHeader("Environment Setup", R.drawable.ic_storage))

        // Status Card (Cyber-Brutalist sharp 0px card)
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cyberBrutalistBg(
                fillColor = NC.SURFACE_CONTAINER,
                strokeColor = NC.BORDER,
                shadowColor = NC.SURFACE_BRIGHT,
                offsetDp = 6,
                cornerRadiusDp = 0,
                rightFaceColor = NC.OUTLINE_VAR
            )
            setPadding(dp(18), dp(16), dp(18), dp(16))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(16) }
        }
        baseStatusText = TextView(this).apply {
            text = "Initializing full environment setup (base + AI CLIs)..."
            textSize = 13f
            setTextColor(NC.PRIMARY)
            typeface = Typeface.MONOSPACE
            setPadding(0, 0, 0, dp(12))
        }
        baseProgressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            indeterminateTintList = ColorStateList.valueOf(NC.PRIMARY)
            layoutParams = LinearLayout.LayoutParams(MATCH, dp(6))
        }
        card.addView(baseStatusText)
        card.addView(baseProgressBar)
        root.addView(card)

        // Terminal Console View (Cyber-Brutalist sharp 0px card)
        val consoleCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cyberBrutalistBg(
                fillColor = NC.LOGBG,
                strokeColor = NC.BORDER,
                shadowColor = NC.SURFACE_BRIGHT,
                offsetDp = 6,
                cornerRadiusDp = 0,
                rightFaceColor = NC.OUTLINE_VAR
            )
            layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f).apply { bottomMargin = dp(16) }
        }
        val consoleHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedBg(NC.SURFACE_CONTAINER, NC.BORDER, 0)
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        val consoleIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_terminal)
            setColorFilter(NC.PRIMARY)
            layoutParams = LinearLayout.LayoutParams(dp(16), dp(16)).apply { rightMargin = dp(8) }
        }
        val consoleTitle = TextView(this).apply {
            text = "[ SETUP LOG ]"
            textSize = 11f
            setTextColor(NC.PRIMARY)
            typeface = Typeface.MONOSPACE
            letterSpacing = 0.05f
        }
        consoleHeader.addView(consoleIcon)
        consoleHeader.addView(consoleTitle)
        consoleCard.addView(consoleHeader)

        baseLogScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, MATCH)
        }
        baseLogText = TextView(this).apply {
            text = ""
            textSize = 11f
            setTextColor(NC.PRIMARY)
            typeface = Typeface.MONOSPACE
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setLineSpacing(dp(2).toFloat(), 1.2f)
        }
        baseLogScroll.addView(baseLogText)
        consoleCard.addView(baseLogScroll)
        root.addView(consoleCard)

        // Next Button (initially disabled) — full setup (base + AI CLIs) must finish first
        baseNextBtn = primaryButton("Next: Complete Setup", R.drawable.ic_arrow_right) {
            showPage(4)
        }
        baseNextBtn.isEnabled = false
        baseNextBtn.alpha = 0.45f
        root.addView(baseNextBtn)

        return root
    }

    private fun runDebianBaseSetup() {
        // ── CHROOT path (KernelSU / Magisk root required) ────────────────────
        if (selectedIsolationMethod == "chroot") {
            updateBaseStatus("[CHROOT] Checking root access...")
            executor.execute {
                val rootOk = RootShell.isRootAvailable()
                if (!rootOk) {
                    updateBaseStatus("[CHROOT] ERROR: Root not available. Grant superuser to NativeCode in KernelSU/Magisk manager, then retry.")
                    mainHandler.post {
                        if (::baseProgressBar.isInitialized) {
                            baseProgressBar.isIndeterminate = false
                            baseProgressBar.progress = 0
                        }
                    }
                    return@execute
                }
                updateBaseStatus("[CHROOT] Root confirmed. Running setup_debian13_chroot.sh...")
                RootShell.executeScriptAsset(
                    context = this,
                    assetName = "scripts/chroot/setup_debian13_chroot.sh",
                    onLine = { line -> updateBaseStatus(line) },
                    onDone = { code ->
                        if (code == 0) {
                            // Step E: Provision Debian packages (runs inside chroot as root)
                            updateBaseStatus("[CHROOT] E. Provisioning Debian packages...")
                            copyAndRunInChroot(
                                assetName = "scripts/setup_debian_family.sh",
                                scriptName = "setup_debian_family.sh",
                                cmd = "bash /tmp/setup_debian_family.sh"
                            ) { codeE ->
                                if (codeE != 0) {
                                    updateBaseStatus("[CHROOT] Debian family setup failed (exit $codeE).")
                                    return@copyAndRunInChroot
                                }
                                // Step F: Hardware acceleration (Adreno→turnip, else virgl)
                                val gpuDetect = GpuAccelDetector.detect()
                                updateBaseStatus(
                                    "[CHROOT] F. Configuring Hardware Acceleration " +
                                        "(${gpuDetect.mode}, vendor=${gpuDetect.vendorHint})..."
                                )
                                getSharedPreferences("nativecode_prefs", MODE_PRIVATE).edit()
                                    .putString("flux_gpu", gpuDetect.mode)
                                    .putString("flux_gpu_vendor", gpuDetect.vendorHint)
                                    .apply()
                                copyAndRunInChroot(
                                    assetName = "scripts/setup_hw_accel_debian.sh",
                                    scriptName = "setup_hw_accel_debian.sh",
                                    cmd = "env FLUX_GPU=${gpuDetect.mode} bash /tmp/setup_hw_accel_debian.sh"
                                ) { codeF ->
                                    if (codeF != 0) {
                                        updateBaseStatus("[CHROOT] HW accel setup failed (exit $codeF). Continuing...")
                                    }
                                    // Step G: Customization (optional) → Step H: AI CLIs → finish
                                    if (enableDebianCustomization) {
                                        updateBaseStatus("[CHROOT] G. Customizing Guest Environment...")
                                        copyAndRunInChroot(
                                            assetName = "scripts/setup_customization_debian.sh",
                                            scriptName = "setup_customization_debian.sh",
                                            cmd = "env FLUX_THEME=dark bash /tmp/setup_customization_debian.sh"
                                        ) { codeG ->
                                            if (codeG != 0) {
                                                updateBaseStatus("[CHROOT] Customization failed (exit $codeG). Continuing...")
                                            }
                                            runCliToolsSetupChroot { finishChrootBaseSetup() }
                                        }
                                    } else {
                                        updateBaseStatus("[CHROOT] G. Skipping Guest Customization (Toggle Off)...")
                                        runCliToolsSetupChroot { finishChrootBaseSetup() }
                                    }
                                }
                            }
                        } else {
                            updateBaseStatus("[CHROOT] Setup failed with exit code $code. Check logs above.")
                        }
                    }
                )
            }
            return
        }

        // ── PROOT path (default, rootless) ────────────────────────────────────
        executor.execute {
            try {
                // Persist linux_method = proot on completion
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

                val tarProcess = Runtime.getRuntime().exec(
                    arrayOf("tar", "-xf", tarFile.absolutePath, "-C", filesDir.absolutePath)
                )
                if (tarProcess.waitFor() != 0) {
                    // Fallback to stream extraction if shell tar command fails
                    assets.open("bootstrap.tar").use { input ->
                        extractTarStream(input, filesDir)
                    }
                }
                if (tarFile.exists()) tarFile.delete()

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

                // SSOT: rewrite residual com.termux paths in bootstrap text files
                TermuxHostPaths.applyPackageToExtractedPrefix(filesDir)

                File(varDir, "log/apt").mkdirs(); File(varDir, "lib/dpkg").mkdirs(); tmpDir.mkdirs()

                updateBaseStatus("C. Deploying Host Configs...")
                deployScripts()
                File(etcDir, "resolv.conf").writeText("nameserver 8.8.8.8\nnameserver 8.8.4.4\n")

                updateBaseStatus("D. Initializing Host Environment...")
                // Always re-validate host deps after bootstrap; clear stale marker
                HostCommandBuilder.clearSetupMarker(this@OnboardingActivity)
                val nld = applicationInfo.nativeLibraryDir
                val proot = TermuxHostPaths.libProot(this@OnboardingActivity).absolutePath
                val bash = TermuxHostPaths.libBash(this@OnboardingActivity).absolutePath
                check(
                    runShellCommand(
                        arrayOf(proot, bash, File(homeDir, "setup_termux.sh").absolutePath),
                        forceHostSetup = true
                    ) == 0
                ) { "Host setup failed" }

                updateBaseStatus("E. Provisioning Debian Guest Container...")
                val debBytes = assets.open("scripts/setup_debian_family.sh").use { it.readBytes() }
                val debPayload = Base64.encodeToString(debBytes, Base64.NO_WRAP)
                check(runShellCommand(arrayOf(bash, File(homeDir, "flux_install.sh").absolutePath, "debian", debPayload)) == 0) { "Debian guest install failed" }

                val gpuDetect = GpuAccelDetector.detect()
                updateBaseStatus(
                    "F. Configuring Hardware Accel " +
                        "(${gpuDetect.mode}, vendor=${gpuDetect.vendorHint})..."
                )
                getSharedPreferences("nativecode_prefs", MODE_PRIVATE).edit()
                    .putString("flux_gpu", gpuDetect.mode)
                    .putString("flux_gpu_vendor", gpuDetect.vendorHint)
                    .apply()
                val hwScript = File(File(usrDir, "tmp"), "setup_hw_accel_debian.sh")
                assets.open("scripts/setup_hw_accel_debian.sh").use { input -> FileOutputStream(hwScript).use { input.copyTo(it) } }
                hwScript.setExecutable(true)
                check(runShellCommand(arrayOf(
                    TermuxHostPaths.BIN + "/python",
                    TermuxHostPaths.PROOT_DISTRO,
                    "login", "debian", "--shared-tmp", "--",
                    "env", "FLUX_GPU=${gpuDetect.mode}", "bash", "/tmp/setup_hw_accel_debian.sh"
                )) == 0) { "GPU setup failed" }

                if (enableDebianCustomization) {
                    updateBaseStatus("G. Customizing Guest Environment...")
                    val customScript = File(File(usrDir, "tmp"), "setup_customization_debian.sh")
                    assets.open("scripts/setup_customization_debian.sh").use { input -> FileOutputStream(customScript).use { input.copyTo(it) } }
                    customScript.setExecutable(true)
                    check(runShellCommand(arrayOf(
                        TermuxHostPaths.BIN + "/python",
                        TermuxHostPaths.PROOT_DISTRO,
                        "login", "debian", "--shared-tmp", "--",
                        "env", "FLUX_THEME=dark", "bash", "/tmp/setup_customization_debian.sh"
                    )) == 0) { "Customization failed" }
                } else {
                    updateBaseStatus("G. Skipping Guest Customization (Toggle Off)...")
                }

                // Step H: AI CLI tools (was separate onboarding page — now end of main setup)
                updateBaseStatus("H. Installing AI CLI tools (NVM, Node, opencode, codex, claude, …)...")
                val cliCode = runCliToolsSetupProot()
                if (cliCode == 0) {
                    updateBaseStatus("H. AI CLI tools provisioned successfully.")
                } else {
                    updateBaseStatus("H. AI CLI tools finished with exit $cliCode (continuing; re-run setup_cli_tools.sh later if needed).")
                }

                // Persist linux_method = proot
                getSharedPreferences("nativecode_prefs", MODE_PRIVATE)
                    .edit().putString("linux_method", "proot").apply()
                updateBaseStatus("Full Environment Setup Successful!")
                mainHandler.post {
                    if (::baseProgressBar.isInitialized) {
                        baseProgressBar.isIndeterminate = false
                        baseProgressBar.progress = 100
                    }
                    if (::baseNextBtn.isInitialized) {
                        baseNextBtn.isEnabled = true
                        baseNextBtn.alpha = 1f
                    }
                }
            } catch (e: Exception) {
                Log.e("Onboarding", "Debian Base Setup failed", e)
                updateBaseStatus("Error: ${e.message}")
            }
        }
    }

    private fun moveDirectoryContents(source: File, destination: File) {
        if (!source.exists()) return
        destination.mkdirs()
        val files = source.listFiles() ?: return
        for (file in files) {
            val destFile = File(destination, file.name)
            if (file.isDirectory) {
                moveDirectoryContents(file, destFile)
                file.delete()
            } else {
                file.renameTo(destFile)
            }
        }
        source.delete()
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
                FileOutputStream(outFile).use { fos ->
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

    private fun updateBaseStatus(msg: String) {
        mainHandler.post {
            if (::baseStatusText.isInitialized) {
                baseStatusText.text = msg
                if (::baseLogText.isInitialized) {
                    baseLogText.append("\n>>> $msg\n")
                    if (::baseLogScroll.isInitialized) {
                        baseLogScroll.post { baseLogScroll.fullScroll(View.FOCUS_DOWN) }
                    }
                }
            }
        }
    }

    // ── AI CLI tools (end of main Environment Setup, proot + chroot) ───────────

    /** Deploy + run setup_cli_tools.sh inside proot debian. Logs to base console. */
    private fun runCliToolsSetupProot(): Int {
        val usrDir = File(filesDir, "usr")
        val cliScript = File(File(usrDir, "tmp"), "setup_cli_tools.sh")
        cliScript.parentFile?.mkdirs()
        assets.open("scripts/setup_cli_tools.sh").use { input ->
            FileOutputStream(cliScript).use { input.copyTo(it) }
        }
        cliScript.setExecutable(true)
        updateBaseStatus("Deployed setup_cli_tools.sh — running in debian guest...")
        return runShellCommand(
            arrayOf(
                TermuxHostPaths.BIN + "/python",
                TermuxHostPaths.PROOT_DISTRO,
                "login", "debian", "--shared-tmp", "--",
                "bash", "/tmp/setup_cli_tools.sh"
            )
        )
    }

    /**
     * Deploy + run setup_cli_tools.sh inside chroot as root.
     * Always continues to [onDone] (soft-fail) so onboarding can complete.
     */
    private fun runCliToolsSetupChroot(onDone: () -> Unit) {
        updateBaseStatus("[CHROOT] H. Installing AI CLI tools (NVM, Node, opencode, codex, claude, …)...")
        copyAndRunInChroot(
            assetName = "scripts/setup_cli_tools.sh",
            scriptName = "setup_cli_tools.sh",
            cmd = "bash /tmp/setup_cli_tools.sh"
        ) { code ->
            if (code == 0) {
                updateBaseStatus("[CHROOT] H. AI CLI tools provisioned successfully.")
            } else {
                updateBaseStatus(
                    "[CHROOT] H. AI CLI tools finished with exit $code " +
                        "(continuing; re-run setup_cli_tools.sh later if needed)."
                )
            }
            onDone()
        }
    }

    // ── Page 4: Complete ──────────────────────────────────────────────────────
    private fun buildCompletePage(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
        }

        val spacer1 = View(this).apply { layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f) }
        root.addView(spacer1)

        // Hero Success Card (Cyber-Brutalist sharp 0px container)
        val heroCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = cyberBrutalistBg(
                fillColor = NC.SURFACE_CONTAINER,
                strokeColor = NC.PRIMARY,
                shadowColor = NC.SURFACE_BRIGHT,
                offsetDp = 6,
                cornerRadiusDp = 0,
                rightFaceColor = NC.OUTLINE_VAR
            )
            setPadding(dp(24), dp(28), dp(24), dp(28))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply {
                bottomMargin = dp(20)
            }
        }

        val iconBox = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = cyberBrutalistBg(
                fillColor = NC.SURFACE_CONTAINER,
                strokeColor = NC.PRIMARY,
                shadowColor = NC.PRIMARY_CON,
                offsetDp = 4,
                cornerRadiusDp = 0,
                rightFaceColor = NC.PRIMARY_CON
            )
            setPadding(dp(16))
            layoutParams = LinearLayout.LayoutParams(dp(76), dp(76)).apply {
                bottomMargin = dp(18)
            }
        }
        val iconIv = ImageView(this).apply {
            setImageResource(R.drawable.ic_check_circle)
            setColorFilter(NC.PRIMARY)
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
        }
        iconBox.addView(iconIv)
        heroCard.addView(iconBox)
        pulseView(iconBox)

        val title = TextView(this).apply {
            text = "Setup Successful!"
            textSize = 28f
            setTextColor(NC.PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            letterSpacing = -0.02f
            setPadding(0, 0, 0, dp(8))
        }
        heroCard.addView(title)

        val subtitle = TextView(this).apply {
            text = "Linux container & AI harness fully provisioned"
            textSize = 12f
            setTextColor(NC.ON_SURF_VAR)
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
        }
        heroCard.addView(subtitle)
        root.addView(heroCard)

        // Detail Summary Card (Cyber-Brutalist sharp 0px card)
        val summaryCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cyberBrutalistBg(
                fillColor = NC.SURFACE_CONTAINER,
                strokeColor = NC.BORDER,
                shadowColor = NC.SURFACE_BRIGHT,
                offsetDp = 6,
                cornerRadiusDp = 0,
                rightFaceColor = NC.OUTLINE_VAR
            )
            setPadding(dp(18), dp(16), dp(18), dp(16))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(20) }
        }

        val summaryHeader = TextView(this).apply {
            text = "[ PROVISIONED COMPONENTS ]"
            textSize = 11f
            setTextColor(NC.PRIMARY)
            typeface = Typeface.MONOSPACE
            letterSpacing = 0.05f
            setPadding(0, 0, 0, dp(14))
        }
        summaryCard.addView(summaryHeader)

        summaryCard.addView(summaryRow(R.drawable.ic_terminal, "Guest OS", "Debian 13 (Trixie)", "READY"))
        summaryCard.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(MATCH, dp(10)) })
        summaryCard.addView(summaryRow(R.drawable.ic_laptop, "Dev Runtime", "Node.js v26 / NVM", "READY"))
        summaryCard.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(MATCH, dp(10)) })
        summaryCard.addView(summaryRow(R.drawable.ic_smart_toy, "AI Tools", "opencode & codex", "READY"))
        root.addView(summaryCard)

        val spacer2 = View(this).apply { layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f) }
        root.addView(spacer2)

        // Write marker file to declare setup is completely done
        File(filesDir, "setup_complete").createNewFile()
        getSharedPreferences("nativecode_prefs", MODE_PRIVATE).edit().putBoolean("onboarding_completed", true).apply()

        val btn = primaryButton("Launch Environment", R.drawable.ic_arrow_right) {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
        root.addView(btn)

        return root
    }

    private fun summaryRow(iconRes: Int, labelStr: String, detailStr: String, badgeStr: String): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val icon = ImageView(this).apply {
            setImageResource(iconRes)
            setColorFilter(NC.PRIMARY)
            layoutParams = LinearLayout.LayoutParams(dp(18), dp(18)).apply { rightMargin = dp(10) }
        }
        val labelTv = TextView(this).apply {
            text = "$labelStr: "
            textSize = 12f
            setTextColor(NC.ON_SURF_VAR)
            typeface = Typeface.MONOSPACE
        }
        val detailTv = TextView(this).apply {
            text = detailStr
            textSize = 12f
            setTextColor(NC.ON_SURFACE)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        val badge = textBadge(badgeStr, NC.PRIMARY_CON, NC.ON_PRIMARY_CON)

        row.addView(icon)
        row.addView(labelTv)
        row.addView(detailTv)
        row.addView(badge)
        return row
    }

    // ── Helper execution scripts ──────────────────────────────────────────────

    /** Persist linux_method=chroot, unlock Next button after all chroot setup steps finish. */
    private fun finishChrootBaseSetup() {
        getSharedPreferences("nativecode_prefs", MODE_PRIVATE)
            .edit().putString("linux_method", "chroot").apply()
        updateBaseStatus("[CHROOT] Full environment setup complete! linux_method=chroot saved.")
        mainHandler.post {
            if (::baseProgressBar.isInitialized) {
                baseProgressBar.isIndeterminate = false
                baseProgressBar.progress = 100
            }
            if (::baseNextBtn.isInitialized) {
                baseNextBtn.isEnabled = true
                baseNextBtn.alpha = 1f
            }
        }
    }

    /**
     * Copy an asset script into the chroot /tmp, make it executable, then run it
     * inside the chroot as root. [onDone] is called on the main thread with the exit code.
     */
    private fun copyAndRunInChroot(
        assetName: String,
        scriptName: String,
        cmd: String,
        onDone: (Int) -> Unit
    ) {
        executor.execute {
            // 1. Write asset into app-private staging dir (app has write access here)
            val stageDir = File(filesDir, "staged_scripts").also { it.mkdirs() }
            val staged = File(stageDir, scriptName)
            try {
                assets.open(assetName).use { input ->
                    java.io.FileOutputStream(staged).use { input.copyTo(it) }
                }
                staged.setReadable(true, false)
            } catch (e: Exception) {
                Log.e("Onboarding", "copyAndRunInChroot stage failed: ${e.message}")
                updateBaseStatus("[CHROOT] Error staging $scriptName: ${e.message}")
                mainHandler.post { onDone(-1) }
                return@execute
            }

            // 2. As root: mkdir chroot/tmp, cp staged script in, chmod +x, then run
            val chrootTmpPath = "/data/local/tmp/chrootDebian13/tmp"
            val copyCmd = "mkdir -p $chrootTmpPath && cp ${staged.absolutePath} $chrootTmpPath/$scriptName && chmod +x $chrootTmpPath/$scriptName"
            val copyCode = RootShell.executeSync(copyCmd)
            if (copyCode != 0) {
                updateBaseStatus("[CHROOT] Error copying $scriptName into chroot (exit $copyCode).")
                mainHandler.post { onDone(-1) }
                return@execute
            }

            // 3. Run inside chroot
            RootShell.executeInChroot(
                cmd = cmd,
                user = "root",
                onLine = { line -> updateBaseStatus(line) },
                onDone = onDone
            )
        }
    }

    private fun deployScripts() {
        try {
            // SSOT host env + residual bootstrap path rewrite (safe if no usr yet)
            TermuxHostPaths.applyPackageToExtractedPrefix(filesDir)
            val homeDir = File(filesDir, "home").also { it.mkdirs() }
            val scripts = arrayOf(
                "setup_termux.sh",
                "flux_install.sh",
                "start_gui.sh",
                "stop_gui.sh",
                "setup_cli_tools.sh",
                "setup_debian13_chroot.sh",
                "uninstall_debian13_chroot.sh"
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
                    Log.w("Onboarding", "Script $assetPath not found in assets", e)
                }
            }
            // Deploy font.ttf
            val termuxDir = File(homeDir, ".termux").also { it.mkdirs() }
            val fontOut = File(termuxDir, "font.ttf")
            assets.open("fonts/font.ttf").use { input -> FileOutputStream(fontOut).use { input.copyTo(it) } }
            // Pinned Debian 13 rootfs for proot-distro install ./file --name debian
            deployRootfsArchive(homeDir)
        } catch (e: Exception) {
            Log.e("Onboarding", "Failed to deploy scripts", e)
        }
    }

    /** Copy assets/rootfs/debian_13_rootfs.tar.xz → $HOME (skip if already present & large). */
    private fun deployRootfsArchive(homeDir: File) {
        val out = File(homeDir, "debian_13_rootfs.tar.xz")
        if (out.isFile && out.length() > 50L * 1024L * 1024L) {
            Log.i("Onboarding", "Rootfs already deployed: ${out.absolutePath} (${out.length()} bytes)")
            return
        }
        try {
            assets.open("rootfs/debian_13_rootfs.tar.xz").use { input ->
                FileOutputStream(out).use { input.copyTo(it) }
            }
            Log.i("Onboarding", "Deployed rootfs: ${out.absolutePath} (${out.length()} bytes)")
        } catch (e: Exception) {
            Log.e("Onboarding", "Failed to deploy rootfs archive from assets", e)
        }
    }

    private fun runShellCommand(
        cmd: Array<String>,
        forceHostSetup: Boolean = false
    ): Int {
        val adjusted = if (cmd.isNotEmpty() && cmd[0].startsWith("/data/data/"))
            arrayOf("/system/bin/linker64") + cmd else cmd
        val pb = ProcessBuilder(*adjusted)
        HostCommandBuilder.applyTo(this, pb, forceHostSetup = forceHostSetup)
        pb.redirectErrorStream(true)
        val proc = pb.start()
        val buf = ByteArray(1024)
        val stream = proc.inputStream
        var read: Int
        while (stream.read(buf).also { read = it } != -1) {
            val out = String(buf, 0, read)
            mainHandler.post {
                if (::baseLogText.isInitialized) {
                    baseLogText.append(out)
                    if (::baseLogScroll.isInitialized) {
                        baseLogScroll.post { baseLogScroll.fullScroll(View.FOCUS_DOWN) }
                    }
                }
            }
        }
        return proc.waitFor()
    }

    // ── Helper UI components ──────────────────────────────────────────────────

    private fun smallHeader(title: String, iconRes: Int = R.drawable.ic_terminal): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(16))
        }
        val icon = ImageView(this).apply {
            setImageResource(iconRes)
            setColorFilter(NC.PRIMARY)
            layoutParams = LinearLayout.LayoutParams(dp(22), dp(22)).apply { rightMargin = dp(10) }
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

    private fun primaryButton(label: String, iconRes: Int? = null, onClick: () -> Unit): LinearLayout {
        val btn = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = cyberBrutalistBg(
                fillColor = NC.PRIMARY_CON,
                strokeColor = NC.OUTLINE_VAR,
                shadowColor = NC.SURFACE_BRIGHT,
                offsetDp = 6,
                cornerRadiusDp = 0,
                rightFaceColor = NC.OUTLINE_VAR
            )
            setPadding(dp(20), dp(16), dp(20), dp(16))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        v.translationX = dp(4).toFloat()
                        v.translationY = dp(4).toFloat()
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.translationX = 0f
                        v.translationY = 0f
                    }
                }
                false
            }
        }
        val tv = TextView(this).apply {
            text = label
            textSize = 16f
            setTextColor(NC.ON_PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        btn.addView(tv)
        if (iconRes != null) {
            val iv = ImageView(this).apply {
                setImageResource(iconRes)
                setColorFilter(NC.ON_PRIMARY)
                layoutParams = LinearLayout.LayoutParams(dp(18), dp(18)).apply {
                    leftMargin = dp(8)
                }
            }
            btn.addView(iv)
        }
        return btn
    }

    private fun secondaryButton(label: String, onClick: () -> Unit): LinearLayout {
        val btn = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = cyberBrutalistBg(
                fillColor = NC.SURFACE_CONTAINER,
                strokeColor = NC.BORDER,
                shadowColor = NC.SURFACE_BRIGHT,
                offsetDp = 6,
                cornerRadiusDp = 0,
                rightFaceColor = NC.OUTLINE_VAR
            )
            setPadding(dp(20), dp(16), dp(20), dp(16))
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        v.translationX = dp(4).toFloat()
                        v.translationY = dp(4).toFloat()
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.translationX = 0f
                        v.translationY = 0f
                    }
                }
                false
            }
        }
        val tv = TextView(this).apply {
            text = label
            textSize = 14f
            setTextColor(NC.ON_SURFACE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        btn.addView(tv)
        return btn
    }

    private fun featureBadge(iconRes: Int, textStr: String): LinearLayout {
        val badge = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedBg(NC.SURFACE_CONTAINER, NC.BORDER, 0)
            setPadding(dp(10), dp(7), dp(10), dp(7))
        }
        val icon = ImageView(this).apply {
            setImageResource(iconRes)
            setColorFilter(NC.PRIMARY)
            layoutParams = LinearLayout.LayoutParams(dp(14), dp(14)).apply { rightMargin = dp(6) }
        }
        val tv = TextView(this).apply {
            text = textStr
            textSize = 11f
            setTextColor(NC.ON_SURFACE)
            typeface = Typeface.MONOSPACE
        }
        badge.addView(icon)
        badge.addView(tv)
        return badge
    }

    private fun cyberBrutalistBg(
        fillColor: Int,
        strokeColor: Int = NC.BORDER,
        shadowColor: Int = NC.SURFACE_BRIGHT,
        offsetDp: Int = 6,
        cornerRadiusDp: Int = 0,
        rightFaceColor: Int = NC.OUTLINE_VAR
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

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()
    private fun roundedBg(fill: Int, stroke: Int, r: Int) = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; setColor(fill); setStroke(dp(1), stroke); cornerRadius = r.toFloat() }
    private fun textBadge(text: String, bg: Int, fg: Int) = TextView(this).apply { this.text = text; textSize = 10f; setTextColor(fg); typeface = Typeface.MONOSPACE; background = roundedBg(bg, fg, dp(4)); setPadding(dp(8), dp(3), dp(8), dp(3)) }

    private fun pulseView(v: View) {
        val anim = ObjectAnimator.ofFloat(v, "alpha", 0.45f, 1f).apply {
            duration = 1500
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
        }
        anim.start()
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
                if (!isEnabled) return@setOnClickListener
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
            return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dpValue.toFloat(),
                resources.displayMetrics
            ).toInt()
        }
    }

    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
}
