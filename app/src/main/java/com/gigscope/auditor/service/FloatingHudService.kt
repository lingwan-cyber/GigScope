package com.gigscope.auditor.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class FloatingHudService : Service() {

    companion object {
        var instance: FloatingHudService? = null
            private set

        const val EXTRA_MODE = "extra_mode"
        const val MODE_TEST = "test"
        const val MODE_RECORD = "record"

        var currentSpeed: String = "Normal" // "Fast", "Normal", "Slow", "Very Slow"
        var isStepByStepActive: Boolean = false

        var onNextPhaseRequested: (() -> Unit)? = null
        var onFinishRequested: (() -> Unit)? = null
        var onCancelRequested: (() -> Unit)? = null

        var onStepConfirmed: (() -> Unit)? = null
        var onAutoRunRequested: (() -> Unit)? = null
        var onSpeedChanged: ((String) -> Unit)? = null

        private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

        fun runOnMain(block: () -> Unit) {
            if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                block()
            } else {
                mainHandler.post(block)
            }
        }

        fun start(context: Context, mode: String = MODE_TEST) {
            runOnMain {
                try {
                    val intent = Intent(context, FloatingHudService::class.java).apply {
                        putExtra(EXTRA_MODE, mode)
                    }
                    context.startService(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        fun stop(context: Context) {
            runOnMain {
                try {
                    val intent = Intent(context, FloatingHudService::class.java)
                    context.stopService(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        fun updateHud(title: String, subtitle: String, isSparkPhase1: Boolean = false) {
            runOnMain {
                instance?.updateContent(title, subtitle, isSparkPhase1)
            }
        }

        fun showTouch(x: Float, y: Float, label: String, durationMs: Long = 800L) {
            runOnMain {
                instance?.visualizerOverlay?.showTouch(x, y, label, durationMs)
            }
        }

        fun showSwipe(startX: Float, startY: Float, endX: Float, endY: Float, label: String, durationMs: Long = 900L) {
            runOnMain {
                instance?.visualizerOverlay?.showSwipe(startX, startY, endX, endY, label, durationMs)
            }
        }

        fun showStatus(message: String, durationMs: Long = 1200L) {
            runOnMain {
                instance?.visualizerOverlay?.showStatus(message, durationMs)
            }
        }

        fun requestStepConfirmation(stepDescription: String, currentStep: Int, totalSteps: Int) {
            runOnMain {
                instance?.showStepConfirmation(stepDescription, currentStep, totalSteps)
            }
        }

        fun clearStepConfirmation() {
            runOnMain {
                instance?.hideStepConfirmation()
            }
        }
    }

    private var windowManager: WindowManager? = null
    private var hudContainer: LinearLayout? = null
    private var visualizerOverlay: TouchVisualizerOverlay? = null

    private var titleView: TextView? = null
    private var subtitleView: TextView? = null
    private var speedButton: Button? = null

    // Record Mode Buttons
    private var nextButton: Button? = null
    private var finishButton: Button? = null
    private var cancelButton: Button? = null

    // Step-by-Step Confirmation View
    private var stepConfirmationContainer: LinearLayout? = null
    private var stepDescriptionView: TextView? = null

    private var layoutParams: WindowManager.LayoutParams? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        setupVisualizerOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val mode = intent?.getStringExtra(EXTRA_MODE) ?: MODE_TEST
        createOrUpdateView(mode)
        return START_NOT_STICKY
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun setupVisualizerOverlay() {
        if (visualizerOverlay == null) {
            val overlay = TouchVisualizerOverlay(this)
            val overlayParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            try {
                windowManager?.addView(overlay, overlayParams)
                visualizerOverlay = overlay
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun createOrUpdateView(mode: String) {
        if (hudContainer != null) {
            try {
                hudContainer?.let { windowManager?.removeView(it) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            hudContainer = null
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = dp(50)
        }
        layoutParams = params

        val bgDrawable = GradientDrawable().apply {
            setColor(Color.parseColor("#EE1E1E1E")) // High contrast dark
            cornerRadius = dp(14).toFloat()
            setStroke(dp(1), Color.parseColor("#44FFFFFF"))
        }

        hudContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = bgDrawable
            setPadding(dp(16), dp(10), dp(16), dp(10))
            elevation = dp(8).toFloat()
        }

        if (mode == MODE_RECORD) {
            buildRecordHud()
        } else {
            buildAutomationHud()
        }

        setupDragListener(hudContainer!!, params)

        try {
            windowManager?.addView(hudContainer, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun buildAutomationHud() {
        // Top row: Title + Speed button
        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = TextView(this).apply {
            text = "⚡ GigScope Automation"
            setTextColor(Color.parseColor("#00E5FF")) // Bright Cyan
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
        }
        titleView = title
        topRow.addView(title)

        val spacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(16), 0)
        }
        topRow.addView(spacer)

        // Speed Toggle Button: Fast -> Normal -> Slow -> Very Slow
        val btnSpeed = Button(this).apply {
            text = "Speed: $currentSpeed"
            textSize = 10f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#37474F"))
            setPadding(dp(6), dp(0), dp(6), dp(0))
            setOnClickListener {
                currentSpeed = when (currentSpeed) {
                    "Fast" -> "Normal"
                    "Normal" -> "Slow"
                    "Slow" -> "Very Slow"
                    else -> "Fast"
                }
                text = "Speed: $currentSpeed"
                onSpeedChanged?.invoke(currentSpeed)
            }
        }
        speedButton = btnSpeed
        topRow.addView(btnSpeed)

        hudContainer?.addView(topRow)

        // Subtitle status line
        val subtitle = TextView(this).apply {
            text = "Active traversal & inspection..."
            setTextColor(Color.parseColor("#E0E0E0"))
            textSize = 11f
            setPadding(0, dp(2), 0, dp(4))
        }
        subtitleView = subtitle
        hudContainer?.addView(subtitle)

        // Step Confirmation Container (Hidden by default, shown when step-by-step confirmation is needed)
        val stepContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, dp(4), 0, 0)
        }

        val stepDesc = TextView(this).apply {
            text = "Confirm next step..."
            setTextColor(Color.parseColor("#FFD54F")) // Amber
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
        }
        stepDescriptionView = stepDesc
        stepContainer.addView(stepDesc)

        val stepButtonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, 0)
        }

        val btnStep = Button(this).apply {
            text = "▶️ Step"
            textSize = 11f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#0288D1")) // Blue
            setPadding(dp(8), dp(0), dp(8), dp(0))
            setOnClickListener {
                onStepConfirmed?.invoke()
            }
        }
        stepButtonRow.addView(btnStep)

        val btnAuto = Button(this).apply {
            text = "⏩ Auto-Run"
            textSize = 11f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#43A047")) // Green
            setPadding(dp(8), dp(0), dp(8), dp(0))
            setOnClickListener {
                onAutoRunRequested?.invoke()
            }
        }
        stepButtonRow.addView(btnAuto)

        val btnStop = Button(this).apply {
            text = "⏹️ Stop"
            textSize = 11f
            setTextColor(Color.parseColor("#FF8A80"))
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(dp(8), dp(0), dp(8), dp(0))
            setOnClickListener {
                onCancelRequested?.invoke()
            }
        }
        stepButtonRow.addView(btnStop)

        stepContainer.addView(stepButtonRow)
        stepConfirmationContainer = stepContainer
        hudContainer?.addView(stepContainer)
    }

    private fun buildRecordHud() {
        val title = TextView(this).apply {
            text = "🔴 REC: Demonstrating Navigation"
            setTextColor(Color.parseColor("#FF5252"))
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
        }
        titleView = title
        hudContainer?.addView(title)

        val subtitle = TextView(this).apply {
            text = "Tap target tabs & buttons. Coordinates recorded automatically."
            setTextColor(Color.parseColor("#E0E0E0"))
            textSize = 11f
            setPadding(0, dp(2), 0, dp(6))
        }
        subtitleView = subtitle
        hudContainer?.addView(subtitle)

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val btnNext = Button(this).apply {
            text = "Next Phase"
            textSize = 11f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#1E88E5"))
            setPadding(dp(8), dp(2), dp(8), dp(2))
            visibility = View.GONE
            setOnClickListener {
                onNextPhaseRequested?.invoke()
            }
        }
        nextButton = btnNext
        buttonRow.addView(btnNext)

        val btnFinish = Button(this).apply {
            text = "Save & Finish"
            textSize = 11f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#43A047"))
            setPadding(dp(8), dp(2), dp(8), dp(2))
            setOnClickListener {
                onFinishRequested?.invoke()
            }
        }
        finishButton = btnFinish
        buttonRow.addView(btnFinish)

        val btnCancel = Button(this).apply {
            text = "Cancel"
            textSize = 11f
            setTextColor(Color.parseColor("#B0BEC5"))
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(dp(8), dp(2), dp(8), dp(2))
            setOnClickListener {
                onCancelRequested?.invoke()
            }
        }
        cancelButton = btnCancel
        buttonRow.addView(btnCancel)

        hudContainer?.addView(buttonRow)
    }

    private fun setupDragListener(view: View, params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        params.x = initialX + dx
                        params.y = initialY + dy
                        windowManager?.updateViewLayout(view, params)
                        true
                    } else {
                        false
                    }
                }
                else -> false
            }
        }
    }

    fun updateContent(title: String, subtitle: String, isSparkPhase1: Boolean) {
        runOnMain {
            titleView?.text = title
            subtitleView?.text = subtitle
            if (isSparkPhase1) {
                nextButton?.visibility = View.VISIBLE
                finishButton?.visibility = View.GONE
            } else {
                nextButton?.visibility = View.GONE
                finishButton?.visibility = View.VISIBLE
            }
        }
    }

    fun showStepConfirmation(stepDescription: String, currentStep: Int, totalSteps: Int) {
        runOnMain {
            stepDescriptionView?.text = "Step $currentStep/$totalSteps: $stepDescription"
            stepConfirmationContainer?.visibility = View.VISIBLE
        }
    }

    fun hideStepConfirmation() {
        runOnMain {
            stepConfirmationContainer?.visibility = View.GONE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        runOnMain {
            try {
                hudContainer?.let { windowManager?.removeView(it) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            hudContainer = null
            try {
                visualizerOverlay?.let { windowManager?.removeView(it) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            visualizerOverlay = null
            instance = null
        }
    }
}
