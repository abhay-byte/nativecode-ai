package com.ivarna.nativecode

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.view.MotionEvent
import android.util.Base64
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.setPadding
import android.system.Os
import com.ivarna.nativecode.terminal.GpuAccelDetector
import com.ivarna.nativecode.terminal.HostCommandBuilder
import com.ivarna.nativecode.terminal.ProjectPathResolver
import com.ivarna.nativecode.terminal.TermuxHostPaths
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.Executors
import kotlin.math.min

class OnboardingActivity : AppCompatActivity() {

    private lateinit var rootLayout: FrameLayout
    private lateinit var pageContainer: FrameLayout

    // Onboarding pages: 0 intro → 1 slideshow → 2 requirements → 3 isolation → 4 full setup → 5 complete
    private var currentPageIndex = 0

    private val executor = Executors.newCachedThreadPool()
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    // ── Shared setup status state ──────────────────────────────────────────────
    private var isDebianBaseSetupStarted = false
    private var enableDebianCustomization = true

    // ── Linux isolation method selected on page 3 ─────────────────────────────
    // "proot" (default, rootless) or "chroot" (requires KernelSU/Magisk root)
    private var selectedIsolationMethod = "proot"

    // page 4 (Full Environment Setup: base + AI CLIs) — progress-first UI
    private lateinit var setupPercentText: TextView
    private lateinit var setupPhaseMetaText: TextView
    private lateinit var setupElapsedText: TextView
    private lateinit var setupStepTitleText: TextView
    private lateinit var setupDetailText: TextView
    private lateinit var setupProgressTrack: FrameLayout
    private lateinit var setupProgressFill: View
    private lateinit var baseLogText: TextView
    private lateinit var baseLogScroll: ScrollView
    private lateinit var setupLogPanel: View
    private lateinit var setupLogToggleBtn: LinearLayout
    private lateinit var setupLogToggleLabel: TextView
    private lateinit var baseNextBtn: View

    private var setupLogVisible = false
    private val setupLogBuffer = StringBuilder()
    private var setupOverallPercent = 0
    private var setupPhaseIndex = 0
    private var setupPhaseFraction = 0f
    private var setupPhases: List<SetupPhase> = emptyList()
    private var setupFailed = false
    private var setupFinished = false

    /** Displayed progress 0–100f (animated toward [setupOverallPercent]). */
    private var setupAnimatedPercent = 0f
    private var progressBarAnimator: ValueAnimator? = null
    private var percentTextAnimator: ValueAnimator? = null
    private var progressPulseAnimator: ObjectAnimator? = null
    private var percentPunchAnimator: ObjectAnimator? = null
    private var setupDisplayedPercentInt = 0

    // Elapsed timer (starts with install, freezes on success/fail)
    private var setupElapsedStartMs = 0L
    private var setupElapsedFrozenMs = 0L
    private var setupElapsedRunning = false
    private val setupElapsedTicker = object : Runnable {
        override fun run() {
            if (!setupElapsedRunning) return
            refreshSetupElapsedUi()
            mainHandler.postDelayed(this, 1000L)
        }
    }

    private data class SetupPhase(val id: String, val label: String, val weight: Int)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Install/upgrade: stage scripts before any onboarding shell work
        AppUpgrade.runIfNeeded(this)

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

        // Optional deep-link from Settings → Chroot install
        intent.getStringExtra("preferred_isolation")?.let { pref ->
            if (pref == "chroot" || pref == "proot") selectedIsolationMethod = pref
        }
        val startPage = intent.getIntExtra("target_page", 0)
        showPage(startPage)

        // Jump straight into Environment Setup and run full install chain
        if (intent.getBooleanExtra("auto_start_setup", false) && startPage == 4) {
            if (!isDebianBaseSetupStarted) {
                isDebianBaseSetupStarted = true
                runDebianBaseSetup()
            }
        }
    }

    private fun showPage(index: Int) {
        currentPageIndex = index
        pageContainer.removeAllViews()

        val view = when (index) {
            0 -> buildIntroPage()
            1 -> buildSlideshowPage()
            2 -> buildRequirementsPage()
            3 -> buildIsolationPage()
            4 -> buildDebianBasePage()
            5, 6 -> buildCompletePage() // 6 = legacy target_page after page insert
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
        val shadowOff = 6
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            // margin-mobile 16px (ui_design.md)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
        }

        root.addView(smallHeader("Core Capabilities", R.drawable.ic_extension))

        val contentFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f).apply {
                topMargin = dp(8)
                // Bottom gap leaves room for card's 6dp extrusion + breathing room
                bottomMargin = dp(12 + shadowOff)
            }
            clipChildren = false
            clipToPadding = false
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

        fun slideImageRes(type: PreviewType): Int = when (type) {
            PreviewType.PROJECT_TREE -> R.drawable.img_slide_workspace
            PreviewType.AI_CLI -> R.drawable.img_slide_ai
            PreviewType.DEV_SUITE -> R.drawable.img_slide_dev
            PreviewType.XFCE_GUI -> R.drawable.img_slide_xfce
            PreviewType.DEBIAN_ENV -> R.drawable.img_slide_debian
            PreviewType.GIT_DIFF -> R.drawable.img_slide_git
        }

        fun renderSlideCard(slideIdx: Int): View {
            val slide = slides[slideIdx]
            // Single stacked card: hero graphic fills free height, meta pinned bottom
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = cyberBrutalistBg(
                    fillColor = Color.parseColor("#121212"),
                    strokeColor = NC.BORDER,
                    shadowColor = NC.SURFACE_BRIGHT,
                    offsetDp = shadowOff,
                    cornerRadiusDp = 0,
                    rightFaceColor = NC.OUTLINE_VAR
                )
                // Keep children inside front face (LayerDrawable reserves right/bottom for shadow)
                setPadding(dp(10), dp(10), dp(10 + shadowOff), dp(10 + shadowOff))
                layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
                clipChildren = true
                clipToPadding = true
            }

            // Hero image zone — expands to fill remaining vertical space
            val imageFrame = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(NC.SURFACE_LOWEST)
                    setStroke(dp(1), NC.BORDER)
                }
                clipChildren = true
                clipToPadding = true
            }

            val iv = ImageView(this).apply {
                setImageResource(slideImageRes(slide.type))
                // CENTER_CROP: portrait assets fill hero; landscape slides crop until redone
                scaleType = ImageView.ScaleType.CENTER_CROP
                adjustViewBounds = false
                layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
            }
            imageFrame.addView(iv)
            card.addView(imageFrame)

            // Meta strip (category / title / desc)
            val meta = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply {
                    topMargin = dp(12)
                }
                setPadding(dp(4), 0, dp(4), 0)
            }

            val tagTv = TextView(this).apply {
                text = "[ ${slide.category} — ${String.format("%02d", slideIdx + 1)} / ${String.format("%02d", slides.size)} ]"
                textSize = 11f
                setTextColor(NC.PRIMARY)
                typeface = Typeface.MONOSPACE
                setPadding(0, 0, 0, dp(8))
            }
            meta.addView(tagTv)

            val headerRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, dp(8))
            }
            val iconIv = ImageView(this).apply {
                setImageResource(slide.iconResId)
                setColorFilter(NC.PRIMARY)
                layoutParams = LinearLayout.LayoutParams(dp(24), dp(24)).apply {
                    rightMargin = dp(10)
                }
            }
            val titleTv = TextView(this).apply {
                text = slide.title
                textSize = 18f
                setTextColor(NC.ON_SURFACE)
                typeface = Typeface.DEFAULT_BOLD
            }
            headerRow.addView(iconIv)
            headerRow.addView(titleTv)
            meta.addView(headerRow)

            val descTv = TextView(this).apply {
                text = slide.desc
                textSize = 14f
                setTextColor(NC.ON_SURF_VAR)
                setLineSpacing(0f, 1.35f)
            }
            meta.addView(descTv)

            card.addView(meta)
            return card
        }

        contentFrame.addView(renderSlideCard(0))

        // Progress Dots Row — sharp rectangles (no radius)
        val dotsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply {
                bottomMargin = dp(16)
            }
        }
        val dotViews = mutableListOf<View>()

        fun updateDots(selectedIdx: Int) {
            for (i in dotViews.indices) {
                val isSelected = (i == selectedIdx)
                val widthDp = if (isSelected) 28 else 8
                val params = dotViews[i].layoutParams as LinearLayout.LayoutParams
                params.width = dp(widthDp)
                params.height = dp(8)
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
                val widthDp = if (isSelected) 28 else 8
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
                setOnClickListener { switchSlide(i) }
            }
            dotsRow.addView(dot)
            dotViews.add(dot)
        }
        root.addView(dotsRow)

        // Manual swipe only (no auto-advance)
        var downX = 0f
        contentFrame.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val deltaX = event.x - downX
                    if (deltaX < -90) {
                        switchSlide((activeSlide + 1) % slides.size)
                    } else if (deltaX > 90) {
                        switchSlide(if (activeSlide > 0) activeSlide - 1 else slides.size - 1)
                    }
                    true
                }
                else -> false
            }
        }

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            // Reserve shadow extrusion under buttons
            setPadding(0, 0, 0, dp(shadowOff))
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


    // ── Page 3: Requirements (hard storage gate + soft RAM/swap/SoC) ──────────
    private fun buildRequirementsPage(): View {
        val shadowOff = 6
        val snap = DeviceRequirements.evaluate(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
        }

        root.addView(smallHeader("Requirements", R.drawable.ic_dns_thick))

        val subtitle = TextView(this).apply {
            text = "Device checks before install. Storage is a hard gate."
            textSize = 12f
            setTextColor(NC.ON_SURF_VAR)
            typeface = Typeface.MONOSPACE
            setPadding(0, 0, 0, dp(12))
        }
        root.addView(subtitle)

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f)
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            // room for card 6dp extrusion on last item
            setPadding(0, 0, 0, dp(shadowOff + 8))
        }

        for ((i, check) in snap.checks.withIndex()) {
            list.addView(requirementCheckCard(check).apply {
                layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply {
                    if (i < snap.checks.lastIndex) bottomMargin = dp(12)
                }
            })
        }

        // Summary banner
        val summary = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply {
                topMargin = dp(16)
            }
            when {
                snap.hardBlocked -> {
                    text = "BLOCKED — free more than 10 GB storage to continue."
                    setTextColor(NC.ERROR)
                    background = roundedBg(NC.ERROR_CON, NC.ERROR, 0)
                }
                snap.hasSoftWarnings -> {
                    text = "You can continue, but performance may not be smooth below recommended RAM / swap / SoC."
                    setTextColor(NC.TERTIARY)
                    background = roundedBg(NC.SURFACE_CONTAINER, NC.TERTIARY_CON, 0)
                }
                else -> {
                    text = "All checks look good. Ready for method selection."
                    setTextColor(NC.PRIMARY)
                    background = roundedBg(NC.SURFACE_CONTAINER, NC.PRIMARY, 0)
                }
            }
        }
        list.addView(summary)
        scroll.addView(list)
        root.addView(scroll)

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12), 0, dp(shadowOff))
        }
        val prevBtn = secondaryButton("Back") { showPage(1) }
        prevBtn.layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).apply { rightMargin = dp(8) }

        val nextLabel = if (snap.hardBlocked) "Storage required" else "Continue"
        val nextBtn = primaryButton(nextLabel, if (snap.hardBlocked) null else R.drawable.ic_arrow_right) {
            if (!snap.hardBlocked) showPage(3)
        }
        nextBtn.layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        if (snap.hardBlocked) {
            nextBtn.isEnabled = false
            nextBtn.alpha = 0.4f
            nextBtn.isClickable = false
        }
        btnRow.addView(prevBtn)
        btnRow.addView(nextBtn)
        root.addView(btnRow)

        return root
    }

    private fun requirementCheckCard(check: DeviceRequirements.CheckResult): LinearLayout {
        val stroke: Int
        val badgeBg: Int
        val badgeFg: Int
        val badgeText: String
        when (check.status) {
            DeviceRequirements.Status.PASS -> {
                stroke = NC.PRIMARY
                badgeBg = NC.PRIMARY_CON
                badgeFg = NC.ON_PRIMARY_CON
                badgeText = "PASS"
            }
            DeviceRequirements.Status.FAIL -> {
                stroke = NC.ERROR
                badgeBg = NC.ERROR_CON
                badgeFg = NC.ON_ERROR_CON
                badgeText = "FAIL"
            }
            DeviceRequirements.Status.WARN -> {
                stroke = NC.TERTIARY_CON
                badgeBg = NC.SURFACE_HIGHEST
                badgeFg = NC.TERTIARY
                badgeText = "WARN"
            }
            DeviceRequirements.Status.UNKNOWN -> {
                stroke = NC.OUTLINE
                badgeBg = NC.SURFACE_HIGHEST
                badgeFg = NC.SECONDARY
                badgeText = "UNKNOWN"
            }
        }
        val severityLabel = if (check.severity == DeviceRequirements.Severity.HARD) "HARD" else "SOFT"

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cyberBrutalistBg(
                fillColor = NC.SURFACE_CONTAINER,
                strokeColor = stroke,
                shadowColor = NC.SURFACE_BRIGHT,
                offsetDp = 6,
                cornerRadiusDp = 0,
                rightFaceColor = NC.OUTLINE_VAR
            )
            // pad right/bottom so content stays out of shadow extrusion
            setPadding(dp(16), dp(14), dp(16 + 6), dp(14 + 6))
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = check.title.uppercase()
            textSize = 14f
            setTextColor(NC.ON_SURFACE)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        top.addView(title)
        top.addView(textBadge(severityLabel, NC.SURFACE_HIGHEST, NC.ON_SURF_VAR).apply {
            layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).apply { rightMargin = dp(6) }
        })
        top.addView(textBadge(badgeText, badgeBg, badgeFg))
        card.addView(top)

        val measured = TextView(this).apply {
            text = check.measured
            textSize = 18f
            setTextColor(
                when (check.status) {
                    DeviceRequirements.Status.PASS -> NC.PRIMARY
                    DeviceRequirements.Status.FAIL -> NC.ERROR
                    DeviceRequirements.Status.WARN -> NC.TERTIARY
                    DeviceRequirements.Status.UNKNOWN -> NC.SECONDARY
                }
            )
            typeface = Typeface.MONOSPACE
            setPadding(0, dp(8), 0, dp(2))
        }
        card.addView(measured)

        val req = TextView(this).apply {
            text = "need  ${check.requirement}"
            textSize = 11f
            setTextColor(NC.ON_SURF_VAR)
            typeface = Typeface.MONOSPACE
        }
        card.addView(req)

        val detail = TextView(this).apply {
            text = check.detail
            textSize = 12f
            setTextColor(NC.ON_SURFACE)
            setLineSpacing(dp(2).toFloat(), 1.2f)
            setPadding(0, dp(8), 0, 0)
        }
        card.addView(detail)

        return card
    }

    // ── Page 4: Method ────────────────────────────────────────────────────────
    private fun buildIsolationPage(): View {

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
        }

        root.addView(smallHeader("Method", R.drawable.ic_shield))

        val spacer = View(this).apply { layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f) }
        root.addView(spacer)

        // Mutable: flipped by RootShell.probeRootAvailable (same SSOT as chroot settings)
        var chrootRootOk = false

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
                chrootCardRef.alpha = if (chrootRootOk) 0.65f else 0.4f
            } else if (chrootRootOk) {
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
            } else {
                // No root: never paint chroot as selected
                selectedIsolationMethod = "proot"
                prootCardRef.background = cyberBrutalistBg(
                    fillColor = NC.SURFACE_CONTAINER, strokeColor = NC.PRIMARY,
                    shadowColor = NC.SURFACE_BRIGHT, offsetDp = 6,
                    cornerRadiusDp = 0, rightFaceColor = NC.OUTLINE_VAR)
                prootCardRef.alpha = 1f
                chrootCardRef.background = cyberBrutalistBg(
                    fillColor = NC.SURFACE_LOW, strokeColor = NC.BORDER,
                    shadowColor = NC.SURFACE_BRIGHT, offsetDp = 6,
                    cornerRadiusDp = 0, rightFaceColor = NC.OUTLINE_VAR)
                chrootCardRef.alpha = 0.4f
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
        val chrootBadge = textBadge("CHECKING…", NC.SURFACE_HIGHEST, NC.ON_SURF_VAR)
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

        val chrootWarn = TextView(this).apply {
            text = "⚠ NO ROOT — grant superuser in KernelSU/Magisk to use chroot"
            textSize = 12f
            setTextColor(NC.ERROR)
            typeface = Typeface.MONOSPACE
            setPadding(0, dp(10), 0, 0)
            visibility = View.GONE
        }
        chrootCard.addView(chrootWarn)
        root.addView(chrootCard)

        fun styleChrootBadge(rootOk: Boolean, probing: Boolean) {
            when {
                probing -> {
                    chrootBadge.text = "CHECKING…"
                    chrootBadge.setTextColor(NC.ON_SURF_VAR)
                    chrootBadge.background = roundedBg(NC.SURFACE_HIGHEST, NC.OUTLINE_VAR, dp(4))
                }
                rootOk -> {
                    chrootBadge.text = "ROOT REQUIRED"
                    chrootBadge.setTextColor(NC.SECONDARY)
                    chrootBadge.background = roundedBg(NC.SURFACE_HIGHEST, NC.SECONDARY, dp(4))
                }
                else -> {
                    chrootBadge.text = "NO ROOT"
                    chrootBadge.setTextColor(Color.parseColor("#0A0A0A"))
                    chrootBadge.background = roundedBg(NC.ERROR, NC.ON_ERROR, dp(4))
                }
            }
        }

        fun applyChrootMethodGate(rootOk: Boolean, probing: Boolean) {
            chrootRootOk = rootOk
            styleChrootBadge(rootOk, probing)
            chrootWarn.visibility = if (!probing && !rootOk) View.VISIBLE else View.GONE
            if (probing) {
                chrootCard.alpha = 0.55f
                chrootCard.isClickable = false
                chrootCard.isEnabled = false
                return
            }
            if (!rootOk) {
                // Force proot when chroot unavailable (also overrides preferred_isolation)
                selectedIsolationMethod = "proot"
                updateIsolationCardSelections("proot", prootCard, chrootCard)
                chrootCard.isClickable = true // toast explain only
                chrootCard.isEnabled = true
                chrootCard.setOnClickListener {
                    Toast.makeText(
                        this,
                        "No root. Grant superuser in KernelSU/Magisk to select chroot.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                chrootCard.isClickable = true
                chrootCard.isEnabled = true
                chrootCard.setOnClickListener {
                    selectedIsolationMethod = "chroot"
                    updateIsolationCardSelections("chroot", prootCard, chrootCard)
                }
                // Re-apply selection paint (proot default or preferred chroot)
                updateIsolationCardSelections(selectedIsolationMethod, prootCard, chrootCard)
            }
        }

        // Wire proot always; chroot gated after probe
        prootCard.setOnClickListener {
            selectedIsolationMethod = "proot"
            updateIsolationCardSelections("proot", prootCard, chrootCard)
        }

        // Initial paint: proot selected while root probes (same RootShell as chroot settings page)
        updateIsolationCardSelections(
            if (selectedIsolationMethod == "chroot") "proot" else selectedIsolationMethod,
            prootCard,
            chrootCard
        )
        applyChrootMethodGate(rootOk = false, probing = true)
        RootShell.probeRootAvailable(forceClearCache = false) { ok ->
            applyChrootMethodGate(rootOk = ok, probing = false)
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
        val prevBtn = secondaryButton("Back") { showPage(2) }
        prevBtn.layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).apply { rightMargin = dp(8) }
        val nextBtn = primaryButton("Configure & Install", R.drawable.ic_arrow_right) {
            showPage(4)
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

    // ── Page 4: Full Environment Setup (progress-first; logs on demand) ────────
    private fun buildDebianBasePage(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
        }

        root.addView(smallHeader("Environment Setup", R.drawable.ic_storage))

        // Center cluster: progress + log controls vertically in remaining space
        val center = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f)
        }

        // Progress card (Cyber-Brutalist sharp 0px) — text centered
        val progressCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = cyberBrutalistBg(
                fillColor = NC.SURFACE_CONTAINER,
                strokeColor = NC.BORDER,
                shadowColor = NC.SURFACE_BRIGHT,
                offsetDp = 6,
                cornerRadiusDp = 0,
                rightFaceColor = NC.OUTLINE_VAR
            )
            setPadding(dp(24), dp(28), dp(24), dp(28))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(20) }
        }

        setupPhaseMetaText = TextView(this).apply {
            text = setupPhaseMetaLabel()
            textSize = 11f
            setTextColor(NC.ON_SURF_VAR)
            typeface = Typeface.MONOSPACE
            letterSpacing = 0.06f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(8))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        progressCard.addView(setupPhaseMetaText)

        setupElapsedText = TextView(this).apply {
            text = formatSetupElapsedLabel()
            textSize = 12f
            setTextColor(NC.PRIMARY)
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            letterSpacing = 0.04f
            setPadding(0, 0, 0, dp(16))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
            contentDescription = "Elapsed install time"
        }
        progressCard.addView(setupElapsedText)

        setupPercentText = TextView(this).apply {
            text = "${setupOverallPercent}%"
            textSize = 48f
            setTextColor(NC.PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            letterSpacing = -0.02f
            setPadding(0, 0, 0, dp(20))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
            contentDescription = "Setup progress $setupOverallPercent percent"
        }
        progressCard.addView(setupPercentText)

        setupProgressTrack = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(NC.SURFACE_HIGH)
                setStroke(dp(1), NC.BORDER)
                cornerRadius = 0f
            }
            layoutParams = LinearLayout.LayoutParams(MATCH, dp(16)).apply { bottomMargin = dp(20) }
            clipChildren = true
        }
        // Full-width fill; progress driven by scaleX from left (pivotX=0)
        setupProgressFill = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(NC.PRIMARY_CON)
                cornerRadius = 0f
            }
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
            pivotX = 0f
            scaleX = (setupAnimatedPercent / 100f).coerceIn(0f, 1f)
            alpha = 1f
        }
        setupProgressTrack.addView(setupProgressFill)
        progressCard.addView(setupProgressTrack)

        setupStepTitleText = TextView(this).apply {
            text = if (setupFinished) {
                "Full environment setup complete"
            } else if (setupFailed) {
                "Setup failed — open log for details"
            } else if (isDebianBaseSetupStarted) {
                currentSetupPhase()?.label ?: "Installing environment…"
            } else {
                "Initializing full environment setup (base + AI CLIs)…"
            }
            textSize = 14f
            setTextColor(if (setupFailed) NC.ERROR else NC.ON_SURFACE)
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(6))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        progressCard.addView(setupStepTitleText)

        setupDetailText = TextView(this).apply {
            text = if (setupFinished) "Ready to continue" else "Step progress updates as install runs"
            textSize = 12f
            setTextColor(NC.ON_SURF_VAR)
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        progressCard.addView(setupDetailText)
        center.addView(progressCard)

        // Log toggle (secondary, full width) — logs hidden by default
        setupLogToggleLabel = TextView(this).apply {
            text = if (setupLogVisible) "Hide setup log" else "View setup log"
            textSize = 14f
            setTextColor(NC.ON_SURFACE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        setupLogToggleBtn = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = cyberBrutalistBg(
                fillColor = NC.SURFACE_CONTAINER,
                strokeColor = NC.PRIMARY_CON,
                shadowColor = NC.SURFACE_BRIGHT,
                offsetDp = 6,
                cornerRadiusDp = 0,
                rightFaceColor = NC.OUTLINE_VAR
            )
            setPadding(dp(20), dp(14), dp(20), dp(14))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(12) }
            isClickable = true
            isFocusable = true
            contentDescription = "Toggle setup log visibility"
            setOnClickListener { toggleSetupLogPanel() }
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
        val logIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_terminal)
            setColorFilter(NC.PRIMARY)
            layoutParams = LinearLayout.LayoutParams(dp(16), dp(16)).apply { rightMargin = dp(8) }
        }
        setupLogToggleBtn.addView(logIcon)
        setupLogToggleBtn.addView(setupLogToggleLabel)
        center.addView(setupLogToggleBtn)

        // Log panel (GONE by default) — max height so center cluster stays balanced
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
            layoutParams = LinearLayout.LayoutParams(MATCH, dp(240))
            visibility = if (setupLogVisible) View.VISIBLE else View.GONE
        }
        setupLogPanel = consoleCard
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
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        val consoleClose = TextView(this).apply {
            text = "HIDE"
            textSize = 11f
            setTextColor(NC.ON_SURF_VAR)
            typeface = Typeface.MONOSPACE
            setOnClickListener { setSetupLogVisible(false) }
        }
        consoleHeader.addView(consoleIcon)
        consoleHeader.addView(consoleTitle)
        consoleHeader.addView(consoleClose)
        consoleCard.addView(consoleHeader)

        baseLogScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, MATCH)
        }
        baseLogText = TextView(this).apply {
            text = setupLogBuffer.toString()
            textSize = 11f
            setTextColor(NC.PRIMARY)
            typeface = Typeface.MONOSPACE
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setLineSpacing(dp(2).toFloat(), 1.2f)
        }
        baseLogScroll.addView(baseLogText)
        consoleCard.addView(baseLogScroll)
        center.addView(consoleCard)

        root.addView(center)

        // Next pinned to bottom
        baseNextBtn = primaryButton("Next: Complete Setup", R.drawable.ic_arrow_right) {
            showPage(5)
        }
        if (setupFinished) {
            baseNextBtn.isEnabled = true
            baseNextBtn.alpha = 1f
        } else {
            baseNextBtn.isEnabled = false
            baseNextBtn.alpha = 0.45f
        }
        root.addView(baseNextBtn)

        // Restore bar width after layout
        applySetupProgressUi()

        return root
    }

    private fun toggleSetupLogPanel() {
        setSetupLogVisible(!setupLogVisible)
    }

    private fun setSetupLogVisible(visible: Boolean) {
        setupLogVisible = visible
        if (::setupLogPanel.isInitialized) {
            setupLogPanel.visibility = if (visible) View.VISIBLE else View.GONE
        }
        if (::setupLogToggleLabel.isInitialized) {
            setupLogToggleLabel.text = if (visible) "Hide setup log" else "View setup log"
        }
        if (visible && ::baseLogScroll.isInitialized) {
            baseLogScroll.post { baseLogScroll.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun prootSetupPhases(): List<SetupPhase> = listOf(
        SetupPhase("A", "Preparing directories…", 3),
        SetupPhase("B", "Extracting bootstrap assets…", 12),
        SetupPhase("C", "Deploying host configs…", 5),
        SetupPhase("D", "Initializing host environment…", 12),
        SetupPhase("E", "Provisioning Debian guest…", 30),
        SetupPhase("F", "Configuring hardware acceleration…", 10),
        SetupPhase("G", "Customizing guest environment…", 10),
        SetupPhase("H", "Installing AI CLI tools…", 18)
    )

    private fun chrootSetupPhases(): List<SetupPhase> = listOf(
        SetupPhase("R0", "Checking root access…", 2),
        SetupPhase("R1", "Installing Debian chroot base…", 35),
        SetupPhase("E", "Provisioning Debian packages…", 20),
        SetupPhase("F", "Configuring hardware acceleration…", 10),
        SetupPhase("G", "Customizing guest environment…", 10),
        SetupPhase("H", "Installing AI CLI tools…", 18)
    )

    private fun currentSetupPhase(): SetupPhase? =
        setupPhases.getOrNull(setupPhaseIndex)

    private fun setupPhaseMetaLabel(): String {
        val method = selectedIsolationMethod.uppercase()
        if (setupPhases.isEmpty()) return "SETUP · $method"
        val step = (setupPhaseIndex + 1).coerceAtMost(setupPhases.size)
        return "STEP $step / ${setupPhases.size} · $method"
    }

    private fun beginSetupPhases(method: String) {
        setupFailed = false
        setupFinished = false
        setupPhases = if (method == "chroot") chrootSetupPhases() else prootSetupPhases()
        setupPhaseIndex = 0
        setupPhaseFraction = 0f
        setupOverallPercent = 0
        setupAnimatedPercent = 0f
        setupDisplayedPercentInt = 0
        cancelSetupProgressAnimators()
        startSetupElapsedTimer()
        mainHandler.post {
            applySetupProgressUi(animate = false)
            startProgressPulse()
        }
    }

    private fun startSetupElapsedTimer() {
        setupElapsedStartMs = System.currentTimeMillis()
        setupElapsedFrozenMs = 0L
        setupElapsedRunning = true
        mainHandler.removeCallbacks(setupElapsedTicker)
        mainHandler.post {
            refreshSetupElapsedUi()
            mainHandler.postDelayed(setupElapsedTicker, 1000L)
        }
    }

    private fun stopSetupElapsedTimer() {
        if (setupElapsedRunning && setupElapsedStartMs > 0L) {
            setupElapsedFrozenMs = System.currentTimeMillis() - setupElapsedStartMs
        }
        setupElapsedRunning = false
        mainHandler.removeCallbacks(setupElapsedTicker)
        mainHandler.post { refreshSetupElapsedUi() }
    }

    private fun currentSetupElapsedMs(): Long {
        return when {
            setupElapsedRunning && setupElapsedStartMs > 0L ->
                System.currentTimeMillis() - setupElapsedStartMs
            setupElapsedFrozenMs > 0L -> setupElapsedFrozenMs
            else -> 0L
        }
    }

    private fun formatSetupElapsed(ms: Long): String {
        val totalSec = (ms / 1000L).coerceAtLeast(0L)
        val h = totalSec / 3600L
        val m = (totalSec % 3600L) / 60L
        val s = totalSec % 60L
        return if (h > 0L) {
            String.format("%d:%02d:%02d", h, m, s)
        } else {
            String.format("%02d:%02d", m, s)
        }
    }

    private fun formatSetupElapsedLabel(): String {
        val t = formatSetupElapsed(currentSetupElapsedMs())
        return when {
            setupFinished -> "ELAPSED  $t  ·  DONE"
            setupFailed -> "ELAPSED  $t  ·  STOPPED"
            setupElapsedRunning || setupElapsedStartMs > 0L -> "ELAPSED  $t"
            else -> "ELAPSED  00:00"
        }
    }

    private fun refreshSetupElapsedUi() {
        if (!::setupElapsedText.isInitialized) return
        setupElapsedText.text = formatSetupElapsedLabel()
        setupElapsedText.setTextColor(
            when {
                setupFinished -> NC.PRIMARY
                setupFailed -> NC.ERROR
                else -> NC.PRIMARY
            }
        )
    }

    /** Enter a named phase; completed weight = sum of all prior phase weights. */
    private fun enterSetupPhase(phaseId: String, detail: String? = null) {
        val idx = setupPhases.indexOfFirst { it.id == phaseId }
        if (idx < 0) return
        setupPhaseIndex = idx
        setupPhaseFraction = 0.05f
        val phase = setupPhases[idx]
        mainHandler.post {
            if (::setupStepTitleText.isInitialized) {
                setupStepTitleText.text = phase.label
                setupStepTitleText.setTextColor(NC.ON_SURFACE)
            }
            if (::setupDetailText.isInitialized) {
                setupDetailText.text = detail
                    ?: "Step ${idx + 1} of ${setupPhases.size}"
            }
            recomputeSetupPercent(capAt99 = true)
            applySetupProgressUi()
        }
    }

    private fun setSetupPhaseFraction(fraction: Float, detail: String? = null) {
        setupPhaseFraction = fraction.coerceIn(0f, 1f)
        mainHandler.post {
            if (detail != null && ::setupDetailText.isInitialized) {
                setupDetailText.text = detail
            }
            recomputeSetupPercent(capAt99 = true)
            applySetupProgressUi()
        }
    }

    private fun recomputeSetupPercent(capAt99: Boolean) {
        if (setupFinished) {
            setupOverallPercent = 100
            return
        }
        if (setupPhases.isEmpty()) {
            setupOverallPercent = 0
            return
        }
        val completed = setupPhases.take(setupPhaseIndex).sumOf { it.weight }
        val curW = setupPhases.getOrNull(setupPhaseIndex)?.weight ?: 0
        val raw = completed + curW * setupPhaseFraction
        val total = setupPhases.sumOf { it.weight }.coerceAtLeast(1)
        val pct = ((raw * 100f) / total).toInt()
        setupOverallPercent = if (capAt99) min(99, pct) else pct.coerceIn(0, 100)
    }

    private fun finishSetupProgressSuccess() {
        setupFinished = true
        setupFailed = false
        setupOverallPercent = 100
        setupPhaseFraction = 1f
        stopSetupElapsedTimer()
        mainHandler.post {
            stopProgressPulse()
            if (::setupStepTitleText.isInitialized) {
                setupStepTitleText.text = "Full environment setup complete"
                setupStepTitleText.setTextColor(NC.PRIMARY)
            }
            if (::setupDetailText.isInitialized) {
                setupDetailText.text = "Ready to continue"
            }
            applySetupProgressUi(animate = true, onBarSettled = { punchPercentSuccess() })
            if (::baseNextBtn.isInitialized) {
                baseNextBtn.isEnabled = true
                baseNextBtn.alpha = 1f
            }
        }
    }

    private fun failSetupProgress(message: String) {
        setupFailed = true
        stopSetupElapsedTimer()
        mainHandler.post {
            stopProgressPulse()
            if (::setupStepTitleText.isInitialized) {
                setupStepTitleText.text = message
                setupStepTitleText.setTextColor(NC.ERROR)
            }
            if (::setupDetailText.isInitialized) {
                setupDetailText.text = "Open setup log for details"
            }
            applySetupProgressUi(animate = true)
        }
    }

    private fun cancelSetupProgressAnimators() {
        progressBarAnimator?.cancel()
        progressBarAnimator = null
        percentTextAnimator?.cancel()
        percentTextAnimator = null
        percentPunchAnimator?.cancel()
        percentPunchAnimator = null
        stopProgressPulse()
    }

    private fun startProgressPulse() {
        if (!::setupProgressFill.isInitialized) return
        if (setupFinished || setupFailed) return
        stopProgressPulse()
        // Soft terminal-green breathe while work runs (not blur — opacity only)
        progressPulseAnimator = ObjectAnimator.ofFloat(setupProgressFill, View.ALPHA, 1f, 0.72f, 1f).apply {
            duration = 1100
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun stopProgressPulse() {
        progressPulseAnimator?.cancel()
        progressPulseAnimator = null
        if (::setupProgressFill.isInitialized) {
            setupProgressFill.alpha = 1f
        }
    }

    private fun punchPercentSuccess() {
        if (!::setupPercentText.isInitialized) return
        percentPunchAnimator?.cancel()
        setupPercentText.scaleX = 1f
        setupPercentText.scaleY = 1f
        percentPunchAnimator = ObjectAnimator.ofFloat(setupPercentText, View.SCALE_X, 1f, 1.12f, 1f).apply {
            duration = 420
            interpolator = OvershootInterpolator(1.4f)
            start()
        }
        ObjectAnimator.ofFloat(setupPercentText, View.SCALE_Y, 1f, 1.12f, 1f).apply {
            duration = 420
            interpolator = OvershootInterpolator(1.4f)
            start()
        }
        // Brief fill flash
        if (::setupProgressFill.isInitialized) {
            ObjectAnimator.ofFloat(setupProgressFill, View.ALPHA, 1f, 0.55f, 1f).apply {
                duration = 280
                start()
            }
        }
    }

    /**
     * Apply progress UI. When [animate], bar width (scaleX) and % label ease toward target.
     * Mechanical decelerate curve — matches cyber-brutalist “hardware” feel.
     */
    private fun applySetupProgressUi(
        animate: Boolean = true,
        onBarSettled: (() -> Unit)? = null
    ) {
        if (!::setupPercentText.isInitialized) return
        setupPercentText.contentDescription = "Setup progress $setupOverallPercent percent"
        if (::setupPhaseMetaText.isInitialized) {
            setupPhaseMetaText.text = setupPhaseMetaLabel()
        }

        val target = setupOverallPercent.toFloat().coerceIn(0f, 100f)

        if (!::setupProgressFill.isInitialized) {
            setupPercentText.text = "${setupOverallPercent}%"
            setupDisplayedPercentInt = setupOverallPercent
            setupAnimatedPercent = target
            onBarSettled?.invoke()
            return
        }

        // Ensure scale pivots from left edge of track after layout
        setupProgressTrack.post {
            if (!::setupProgressFill.isInitialized) return@post
            setupProgressFill.pivotX = 0f
            setupProgressFill.pivotY = setupProgressFill.height / 2f
        }

        if (!animate || kotlin.math.abs(target - setupAnimatedPercent) < 0.15f) {
            progressBarAnimator?.cancel()
            percentTextAnimator?.cancel()
            setupAnimatedPercent = target
            setupDisplayedPercentInt = setupOverallPercent
            setupProgressFill.scaleX = (target / 100f).coerceIn(0.001f, 1f).let {
                if (target <= 0f) 0f else it
            }
            setupPercentText.text = "${setupOverallPercent}%"
            if (!setupFinished && !setupFailed) startProgressPulse()
            onBarSettled?.invoke()
            return
        }

        progressBarAnimator?.cancel()
        percentTextAnimator?.cancel()

        val fromBar = setupAnimatedPercent
        val durationMs = (280L + (kotlin.math.abs(target - fromBar) * 8f).toLong()).coerceIn(280L, 720L)

        progressBarAnimator = ValueAnimator.ofFloat(fromBar, target).apply {
            duration = durationMs
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener { va ->
                val v = va.animatedValue as Float
                setupAnimatedPercent = v
                if (::setupProgressFill.isInitialized) {
                    setupProgressFill.scaleX = if (v <= 0f) 0f else (v / 100f).coerceIn(0.001f, 1f)
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    setupAnimatedPercent = target
                    if (::setupProgressFill.isInitialized) {
                        setupProgressFill.scaleX = if (target <= 0f) 0f else (target / 100f).coerceIn(0.001f, 1f)
                    }
                    if (!setupFinished && !setupFailed) startProgressPulse()
                    onBarSettled?.invoke()
                }
            })
            start()
        }

        val fromPct = setupDisplayedPercentInt
        val toPct = setupOverallPercent
        percentTextAnimator = ValueAnimator.ofInt(fromPct, toPct).apply {
            duration = durationMs
            interpolator = DecelerateInterpolator(1.4f)
            addUpdateListener { va ->
                val n = va.animatedValue as Int
                setupDisplayedPercentInt = n
                if (::setupPercentText.isInitialized) {
                    setupPercentText.text = "$n%"
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    setupDisplayedPercentInt = toPct
                    if (::setupPercentText.isInitialized) {
                        setupPercentText.text = "$toPct%"
                    }
                }
            })
            start()
        }

        // Keep pulse alive during progress (restart if cancelled by alpha anim)
        if (!setupFinished && !setupFailed) startProgressPulse()
    }

    override fun onDestroy() {
        setupElapsedRunning = false
        mainHandler.removeCallbacks(setupElapsedTicker)
        cancelSetupProgressAnimators()
        super.onDestroy()
    }

    private fun runDebianBaseSetup() {
        // ── CHROOT path (KernelSU / Magisk root required) ────────────────────
        if (selectedIsolationMethod == "chroot") {
            beginSetupPhases("chroot")
            enterSetupPhase("R0", "Verifying KernelSU / Magisk root…")
            updateBaseStatus("[CHROOT] Checking root access...")
            executor.execute {
                val rootOk = RootShell.isRootAvailable()
                if (!rootOk) {
                    updateBaseStatus("[CHROOT] ERROR: Root not available. Grant superuser to NativeCode in KernelSU/Magisk manager, then retry.")
                    failSetupProgress("Root not available — grant superuser, then retry")
                    return@execute
                }
                enterSetupPhase("R1", "Running setup_debian13_chroot.sh…")
                updateBaseStatus("[CHROOT] Root confirmed. Running setup_debian13_chroot.sh...")
                RootShell.executeScriptAsset(
                    context = this,
                    assetName = "scripts/chroot/setup_debian13_chroot.sh",
                    onLine = { line -> appendSetupLog(line) },
                    onDone = { code ->
                        // Proceed if exit 0 OR base marker present (false exit 1 from am start, etc.)
                        val installed = ProjectPathResolver.isChrootInstalled()
                        if (code != 0 && installed) {
                            updateBaseStatus(
                                "[CHROOT] Base exit $code but .flux_configured present — continuing guest setup..."
                            )
                        }
                        if (code == 0 || installed) {
                            setSetupPhaseFraction(1f, "Base chroot installed")
                            updateBaseStatus("[CHROOT] Base complete — starting guest chain (E→H)...")
                            // Step E: Provision Debian packages (runs inside chroot as root)
                            enterSetupPhase("E", "Installing guest packages…")
                            updateBaseStatus("[CHROOT] E. Provisioning Debian packages...")
                            copyAndRunInChroot(
                                assetName = "scripts/setup_debian_family.sh",
                                scriptName = "setup_debian_family.sh",
                                cmd = "bash /tmp/setup_debian_family.sh"
                            ) { codeE ->
                                if (codeE != 0) {
                                    updateBaseStatus("[CHROOT] Debian family setup failed (exit $codeE).")
                                    failSetupProgress("Debian package setup failed (exit $codeE)")
                                    return@copyAndRunInChroot
                                }
                                setSetupPhaseFraction(1f)
                                // Step F: Hardware acceleration (Adreno→turnip, else virgl)
                                val gpuDetect = GpuAccelDetector.detect()
                                enterSetupPhase(
                                    "F",
                                    "${gpuDetect.mode} · ${gpuDetect.vendorHint}"
                                )
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
                                    setSetupPhaseFraction(1f)
                                    // Step G: Customization (optional) → Step H: AI CLIs → finish
                                    if (enableDebianCustomization) {
                                        enterSetupPhase("G", "Applying desktop theme…")
                                        updateBaseStatus("[CHROOT] G. Customizing Guest Environment...")
                                        copyAndRunInChroot(
                                            assetName = "scripts/setup_customization_debian.sh",
                                            scriptName = "setup_customization_debian.sh",
                                            cmd = "env FLUX_THEME=dark bash /tmp/setup_customization_debian.sh"
                                        ) { codeG ->
                                            if (codeG != 0) {
                                                updateBaseStatus("[CHROOT] Customization failed (exit $codeG). Continuing...")
                                            }
                                            setSetupPhaseFraction(1f)
                                            enterSetupPhase("H", "NVM, Node, opencode, codex…")
                                            runCliToolsSetupChroot { finishChrootBaseSetup() }
                                        }
                                    } else {
                                        updateBaseStatus("[CHROOT] G. Skipping Guest Customization (Toggle Off)...")
                                        enterSetupPhase("H", "NVM, Node, opencode, codex…")
                                        runCliToolsSetupChroot { finishChrootBaseSetup() }
                                    }
                                }
                            }
                        } else {
                            updateBaseStatus("[CHROOT] Setup failed with exit code $code. Check logs above.")
                            failSetupProgress("Chroot setup failed (exit $code)")
                        }
                    }
                )
            }
            return
        }

        // ── PROOT path (default, rootless) ────────────────────────────────────
        beginSetupPhases("proot")
        executor.execute {
            try {
                enterSetupPhase("A", "Creating prefix directories…")
                updateBaseStatus("A. Preparing Directories...")
                val usrDir = File(filesDir, "usr")
                val tmpDir = File(usrDir, "tmp")
                val etcDir = File(usrDir, "etc")
                val varDir = File(usrDir, "var")
                val homeDir = File(filesDir, "home")
                tmpDir.mkdirs(); etcDir.mkdirs(); homeDir.mkdirs()
                File(varDir, "log/apt").mkdirs()
                File(varDir, "lib/dpkg").mkdirs()

                enterSetupPhase("B", "Unpacking bootstrap.tar…")
                updateBaseStatus("B. Extracting Bootstrap Assets...")
                val tarFile = File(filesDir, "bootstrap.tar")
                if (!tarFile.exists()) {
                    copyAssetWithProgress("bootstrap.tar", tarFile) { frac, detail ->
                        setSetupPhaseFraction(frac * 0.5f, detail)
                    }
                } else {
                    setSetupPhaseFraction(0.5f, "bootstrap.tar ready")
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
                setSetupPhaseFraction(1f, "Bootstrap extracted")
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

                enterSetupPhase("C", "Scripts + DNS…")
                updateBaseStatus("C. Deploying Host Configs...")
                deployScripts()
                File(etcDir, "resolv.conf").writeText("nameserver 8.8.8.8\nnameserver 8.8.4.4\n")
                setSetupPhaseFraction(1f)

                enterSetupPhase("D", "Running setup_termux.sh…")
                updateBaseStatus("D. Initializing Host Environment...")
                // Always re-validate host deps after bootstrap; clear stale marker
                HostCommandBuilder.clearSetupMarker(this@OnboardingActivity)
                val proot = TermuxHostPaths.libProot(this@OnboardingActivity).absolutePath
                val bash = TermuxHostPaths.libBash(this@OnboardingActivity).absolutePath
                check(
                    runShellCommand(
                        arrayOf(proot, bash, File(homeDir, "setup_termux.sh").absolutePath),
                        forceHostSetup = true
                    ) == 0
                ) { "Host setup failed" }
                setSetupPhaseFraction(1f)

                enterSetupPhase("E", "Debian rootfs + packages…")
                updateBaseStatus("E. Provisioning Debian Guest Container...")
                val debBytes = assets.open("scripts/setup_debian_family.sh").use { it.readBytes() }
                val debPayload = Base64.encodeToString(debBytes, Base64.NO_WRAP)
                check(runShellCommand(arrayOf(bash, File(homeDir, "flux_install.sh").absolutePath, "debian", debPayload)) == 0) { "Debian guest install failed" }
                setSetupPhaseFraction(1f)

                val gpuDetect = GpuAccelDetector.detect()
                enterSetupPhase("F", "${gpuDetect.mode} · ${gpuDetect.vendorHint}")
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
                setSetupPhaseFraction(1f)

                if (enableDebianCustomization) {
                    enterSetupPhase("G", "Applying desktop theme…")
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
                    setSetupPhaseFraction(1f)
                } else {
                    updateBaseStatus("G. Skipping Guest Customization (Toggle Off)...")
                }

                // Step H: AI CLI tools (was separate onboarding page — now end of main setup)
                enterSetupPhase("H", "NVM, Node, opencode, codex…")
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
                finishSetupProgressSuccess()
            } catch (e: Exception) {
                Log.e("Onboarding", "Debian Base Setup failed", e)
                updateBaseStatus("Error: ${e.message}")
                failSetupProgress("Error: ${e.message}")
            }
        }
    }

    /** Copy asset to file with approximate byte progress for setup UI. */
    private fun copyAssetWithProgress(
        assetName: String,
        dest: File,
        onProgress: (fraction: Float, detail: String) -> Unit
    ) {
        val total = try {
            assets.openFd(assetName).use { it.length }
        } catch (_: Exception) {
            -1L
        }
        assets.open(assetName).use { input ->
            FileOutputStream(dest).use { output ->
                val buf = ByteArray(64 * 1024)
                var readTotal = 0L
                var lastUi = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    output.write(buf, 0, n)
                    readTotal += n
                    val now = System.currentTimeMillis()
                    if (now - lastUi < 100) continue
                    lastUi = now
                    if (total > 0) {
                        val frac = (readTotal.toFloat() / total).coerceIn(0f, 1f)
                        onProgress(
                            frac,
                            "Copied ${formatBytes(readTotal)} / ${formatBytes(total)}"
                        )
                    } else {
                        onProgress(0.3f, "Copied ${formatBytes(readTotal)}…")
                    }
                }
            }
        }
        if (total > 0) onProgress(1f, "Copied ${formatBytes(total)}")
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format("%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format("%.1f MB", mb)
        return String.format("%.2f GB", mb / 1024.0)
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
        // Phase title stays under enterSetupPhase / failSetupProgress.
        // All status strings still go into the on-demand log.
        appendSetupLog(">>> $msg\n")
    }

    private fun appendSetupLog(chunk: String) {
        if (chunk.isEmpty()) return
        synchronized(setupLogBuffer) {
            setupLogBuffer.append(chunk)
            // Cap ~200k chars to avoid OOM on huge apt streams
            val max = 200_000
            if (setupLogBuffer.length > max) {
                setupLogBuffer.delete(0, setupLogBuffer.length - max)
            }
        }
        mainHandler.post {
            if (!::baseLogText.isInitialized) return@post
            baseLogText.append(chunk)
            // Keep TextView from growing without bound if buffer was trimmed
            val text = baseLogText.text
            if (text != null && text.length > 220_000) {
                baseLogText.text = text.subSequence(text.length - 200_000, text.length)
            }
            if (setupLogVisible && ::baseLogScroll.isInitialized) {
                baseLogScroll.post { baseLogScroll.fullScroll(View.FOCUS_DOWN) }
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

    // ── Page 6: Complete ──────────────────────────────────────────────────────
    private fun buildCompletePage(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
        }

        val spacer1 = View(this).apply { layoutParams = LinearLayout.LayoutParams(MATCH, 0, 1f) }
        root.addView(spacer1)

        // Brand logo (high-res) — replaces check-circle hero
        val logoCard = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = cyberBrutalistBg(
                fillColor = NC.SURFACE_CONTAINER,
                strokeColor = NC.PRIMARY,
                shadowColor = NC.SURFACE_BRIGHT,
                offsetDp = 6,
                cornerRadiusDp = 0,
                rightFaceColor = NC.OUTLINE_VAR
            )
            setPadding(dp(20), dp(20), dp(20), dp(20))
            layoutParams = LinearLayout.LayoutParams(dp(168), dp(168)).apply {
                bottomMargin = dp(24)
            }
        }
        val logoIv = ImageView(this).apply {
            setImageResource(R.drawable.logo_highres)
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(dp(128), dp(128))
            contentDescription = "NativeCode logo"
        }
        logoCard.addView(logoIv)
        root.addView(logoCard)

        val title = TextView(this).apply {
            text = "Setup Successful!"
            textSize = 28f
            setTextColor(NC.PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            letterSpacing = -0.02f
            setPadding(0, 0, 0, dp(8))
        }
        root.addView(title)

        val subtitle = TextView(this).apply {
            text = "Linux container & AI harness fully provisioned"
            textSize = 12f
            setTextColor(NC.ON_SURF_VAR)
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(20))
        }
        root.addView(subtitle)

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
        finishSetupProgressSuccess()
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
                onLine = { line -> appendSetupLog(line + "\n") },
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
                "start_gui_chroot.sh",
                "stop_gui_chroot.sh",
                "start_debian13_gui.sh",
                "stop_debian13_gui.sh",
                "setup_cli_tools.sh",
                "setup_debian13_chroot.sh",
                "uninstall_debian13_chroot.sh"
            )
            for (script in scripts) {
                val assetPath = when {
                    script.contains("chroot") ||
                        script == "start_debian13_gui.sh" ||
                        script == "stop_debian13_gui.sh" -> "scripts/chroot/$script"
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
            appendSetupLog(String(buf, 0, read))
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
