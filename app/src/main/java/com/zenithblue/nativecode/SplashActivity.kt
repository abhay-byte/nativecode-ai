package com.ivarna.nativecode

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

class SplashActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var pulseAnimator: ObjectAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // versionCode gate: restage host scripts on install/upgrade
        AppUpgrade.runIfNeeded(this)

        // Make window status/navigation bar transparent over obsidian background
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
        }

        // Obsidian dark container (#131313)
        val rootLayout = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#131313"))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Center Sharp Cyber-Brutalist Card
        val cardWrapper = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
                setMargins(dpToPx(24), 0, dpToPx(24), 0)
            }
        }

        // Sharp 0dp corner radius L-Shape Offset Shadow + Main Card
        val shadowDrawable = GradientDrawable().apply {
            setColor(Color.parseColor("#3C4A3F")) // Shadow offset color
            cornerRadius = 0f // Completely sharp corners!
        }
        val mainCardDrawable = GradientDrawable().apply {
            setColor(Color.parseColor("#1C1B1B")) // Obsidian surface
            setStroke(dpToPx(2), Color.parseColor("#3DDC84")) // Cyber green stroke
            cornerRadius = 0f // Completely sharp corners!
        }

        val cardBg = LayerDrawable(arrayOf(shadowDrawable, mainCardDrawable)).apply {
            setLayerInset(0, dpToPx(6), dpToPx(6), 0, 0) // L-shape 6px offset shadow
            setLayerInset(1, 0, 0, dpToPx(6), dpToPx(6)) // Main sharp card
        }
        cardWrapper.background = cardBg

        // Inner Card Layout
        val cardContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dpToPx(24), dpToPx(28), dpToPx(24), dpToPx(28))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Sharp Logo Box
        val logoBox = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0E0E0E")) // Dark recessed surface
                setStroke(dpToPx(2), Color.parseColor("#3DDC84"))
                cornerRadius = 0f // Sharp 0dp corners!
            }
            layoutParams = LinearLayout.LayoutParams(dpToPx(110), dpToPx(110))
        }

        val logoIv = ImageView(this).apply {
            setImageResource(R.drawable.logo_highres)
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = FrameLayout.LayoutParams(dpToPx(88), dpToPx(88)).apply {
                gravity = Gravity.CENTER
            }
        }
        logoBox.addView(logoIv)
        cardContent.addView(logoBox)

        // Subtle Logo Pulse Animation
        val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f, 1.03f, 1.0f)
        val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f, 1.03f, 1.0f)
        pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(logoBox, scaleX, scaleY).apply {
            duration = 1600
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            start()
        }

        // Space
        cardContent.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(1, dpToPx(20))
        })

        // App Title (Single Line)
        val titleTv = TextView(this).apply {
            text = "NATIVECODE"
            textSize = 24f
            isSingleLine = true
            maxLines = 1
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setTextColor(Color.parseColor("#60F99E"))
            letterSpacing = 0.18f
            gravity = Gravity.CENTER
        }
        cardContent.addView(titleTv)

        // Subtitle
        val subtitleTv = TextView(this).apply {
            text = "AI DEVELOPER ENVIRONMENT"
            textSize = 10f
            isSingleLine = true
            maxLines = 1
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#869587"))
            letterSpacing = 0.12f
            gravity = Gravity.CENTER
        }
        cardContent.addView(subtitleTv)

        // Space
        cardContent.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(1, dpToPx(24))
        })

        // Minimal Status Text
        val statusTv = TextView(this).apply {
            text = "> INITIALIZING ENVIRONMENT..."
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#3DDC84"))
            gravity = Gravity.CENTER
        }
        cardContent.addView(statusTv)

        // Space
        cardContent.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(1, dpToPx(10))
        })

        // Minimal Neon Progress Line
        val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            indeterminateTintList = ColorStateList.valueOf(Color.parseColor("#3DDC84"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(3)
            )
        }
        cardContent.addView(progressBar)

        cardWrapper.addView(cardContent)
        rootLayout.addView(cardWrapper)

        setContentView(rootLayout)

        // Status update & transition
        handler.postDelayed({
            statusTv.text = "> SYSTEM READY."
        }, 800)

        handler.postDelayed({
            proceedToNextActivity()
        }, 1200)
    }

    private fun proceedToNextActivity() {
        if (isFinishing || isDestroyed) return

        val prefs = getSharedPreferences("nativecode_prefs", MODE_PRIVATE)
        val onboardingCompleted = prefs.getBoolean("onboarding_completed", false)

        val nextIntent = if (onboardingCompleted) {
            Intent(this, MainActivity::class.java)
        } else {
            Intent(this, OnboardingActivity::class.java)
        }

        startActivity(nextIntent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        pulseAnimator?.cancel()
        handler.removeCallbacksAndMessages(null)
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }
}
