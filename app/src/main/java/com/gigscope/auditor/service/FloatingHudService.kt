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

        var onNextPhaseRequested: (() -> Unit)? = null
        var onFinishRequested: (() -> Unit)? = null
        var onCancelRequested: (() -> Unit)? = null

        fun start(context: Context, mode: String = MODE_TEST) {
            val intent = Intent(context, FloatingHudService::class.java).apply {
                putExtra(EXTRA_MODE, mode)
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatingHudService::class.java)
            context.stopService(intent)
        }

        fun updateHud(title: String, subtitle: String, isSparkPhase1: Boolean = false) {
            instance?.updateContent(title, subtitle, isSparkPhase1)
        }
    }

    private var windowManager: WindowManager? = null
    private var hudContainer: LinearLayout? = null
    private var titleView: TextView? = null
    private var subtitleView: TextView? = null
    private var nextButton: Button? = null
    private var finishButton: Button? = null
    private var cancelButton: Button? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val mode = intent?.getStringExtra(EXTRA_MODE) ?: MODE_TEST
        createOrUpdateView(mode)
        return START_NOT_STICKY
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun createOrUpdateView(mode: String) {
        if (hudContainer != null) {
            hudContainer?.let { windowManager?.removeView(it) }
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
            y = dp(60)
        }
        layoutParams = params

        val bgDrawable = GradientDrawable().apply {
            setColor(Color.parseColor("#E6212121")) // Semi-transparent dark
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
            buildTestHud()
        }

        setupDragListener(hudContainer!!, params)

        try {
            windowManager?.addView(hudContainer, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun buildTestHud() {
        val title = TextView(this).apply {
            text = "GigScope: Active Test Traversal..."
            setTextColor(Color.WHITE)
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
        }
        titleView = title
        hudContainer?.addView(title)
    }

    private fun buildRecordHud() {
        // Title row: 🔴 REC + App Name
        val title = TextView(this).apply {
            text = "🔴 REC: Demonstrating Navigation"
            setTextColor(Color.parseColor("#FF5252")) // Bright red
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
        }
        titleView = title
        hudContainer?.addView(title)

        // Subtitle row: Phase + step count
        val subtitle = TextView(this).apply {
            text = "Tap target tabs/buttons & scroll once"
            setTextColor(Color.parseColor("#E0E0E0"))
            textSize = 12f
            setPadding(0, dp(2), 0, dp(6))
        }
        subtitleView = subtitle
        hudContainer?.addView(subtitle)

        // Button row
        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val btnNext = Button(this).apply {
            text = "Next: Earnings"
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
            setBackgroundColor(Color.parseColor("#43A047")) // Green
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

    override fun onDestroy() {
        super.onDestroy()
        hudContainer?.let { windowManager?.removeView(it) }
        hudContainer = null
        instance = null
    }
}

