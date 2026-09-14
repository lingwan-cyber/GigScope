package com.gigscope.auditor.service

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.View
import android.view.animation.DecelerateInterpolator

class TouchVisualizerOverlay(context: Context) : View(context) {

    enum class VisualType {
        NONE,
        TOUCH,
        SWIPE,
        STATUS
    }

    private var visualType = VisualType.NONE
    private var touchX = 0f
    private var touchY = 0f
    private var swipeStartX = 0f
    private var swipeStartY = 0f
    private var swipeEndX = 0f
    private var swipeEndY = 0f
    private var actionLabel = ""

    private var animProgress = 0f
    private var currentAnimator: ValueAnimator? = null

    // Paints
    private val touchOuterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(3f)
        color = Color.parseColor("#00E5FF") // Vibrant Cyan
    }

    private val touchInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#8000E5FF")
    }

    private val touchCenterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#FFFFFF")
    }

    private val swipePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(5f)
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#FFD600") // Vibrant Amber
    }

    private val labelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#E6212121") // Dark surface with opacity
    }

    private val labelTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = dp(13f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    fun showTouch(x: Float, y: Float, label: String, durationMs: Long = 800L) {
        post {
            currentAnimator?.cancel()
            visualType = VisualType.TOUCH
            touchX = x
            touchY = y
            actionLabel = label
            animProgress = 0f

            currentAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = durationMs
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    animProgress = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }
    }

    fun showSwipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        label: String,
        durationMs: Long = 900L
    ) {
        post {
            currentAnimator?.cancel()
            visualType = VisualType.SWIPE
            swipeStartX = startX
            swipeStartY = startY
            swipeEndX = endX
            swipeEndY = endY
            actionLabel = label
            animProgress = 0f

            currentAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = durationMs
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    animProgress = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }
    }

    fun showStatus(message: String, durationMs: Long = 1200L) {
        post {
            currentAnimator?.cancel()
            visualType = VisualType.STATUS
            actionLabel = message
            animProgress = 0f

            currentAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = durationMs
                addUpdateListener {
                    animProgress = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }
    }

    fun clear() {
        post {
            currentAnimator?.cancel()
            visualType = VisualType.NONE
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (visualType == VisualType.NONE) return

        when (visualType) {
            VisualType.TOUCH -> drawTouch(canvas)
            VisualType.SWIPE -> drawSwipe(canvas)
            VisualType.STATUS -> drawStatus(canvas)
            VisualType.NONE -> {}
        }
    }

    private fun drawTouch(canvas: Canvas) {
        val alpha = ((1f - animProgress * 0.4f) * 255).toInt().coerceIn(0, 255)
        val outerRadius = dp(20f) + animProgress * dp(25f)
        val innerRadius = dp(16f)
        val centerRadius = dp(5f)

        touchOuterPaint.alpha = alpha
        touchInnerPaint.alpha = (alpha * 0.6f).toInt()
        touchCenterPaint.alpha = alpha

        // Draw expanding outer ripple
        canvas.drawCircle(touchX, touchY, outerRadius, touchOuterPaint)
        // Draw inner circle
        canvas.drawCircle(touchX, touchY, innerRadius, touchInnerPaint)
        // Draw target center dot
        canvas.drawCircle(touchX, touchY, centerRadius, touchCenterPaint)

        // Draw label pill above target
        if (actionLabel.isNotBlank()) {
            drawLabelPill(canvas, touchX, touchY - outerRadius - dp(16f), actionLabel, alpha)
        }
    }

    private fun drawSwipe(canvas: Canvas) {
        val alpha = ((1f - animProgress * 0.3f) * 255).toInt().coerceIn(0, 255)
        swipePaint.alpha = alpha

        val currentX = swipeStartX + (swipeEndX - swipeStartX) * animProgress
        val currentY = swipeStartY + (swipeEndY - swipeStartY) * animProgress

        // Draw swipe path line
        canvas.drawLine(swipeStartX, swipeStartY, currentX, currentY, swipePaint)

        // Draw start dot and arrow head
        canvas.drawCircle(swipeStartX, swipeStartY, dp(7f), swipePaint)

        // Draw arrowhead at tip
        val angle = Math.atan2((currentY - swipeStartY).toDouble(), (currentX - swipeStartX).toDouble())
        val arrowSize = dp(18f)
        val arrowAngle = Math.toRadians(35.0)

        val x1 = (currentX - arrowSize * Math.cos(angle - arrowAngle)).toFloat()
        val y1 = (currentY - arrowSize * Math.sin(angle - arrowAngle)).toFloat()
        val x2 = (currentX - arrowSize * Math.cos(angle + arrowAngle)).toFloat()
        val y2 = (currentY - arrowSize * Math.sin(angle + arrowAngle)).toFloat()

        val arrowPath = Path().apply {
            moveTo(currentX, currentY)
            lineTo(x1, y1)
            lineTo(x2, y2)
            close()
        }
        val arrowPaint = Paint(swipePaint).apply { style = Paint.Style.FILL }
        canvas.drawPath(arrowPath, arrowPaint)

        // Draw label pill at midpoint
        if (actionLabel.isNotBlank()) {
            val midX = (swipeStartX + swipeEndX) / 2f
            val midY = (swipeStartY + swipeEndY) / 2f
            drawLabelPill(canvas, midX, midY, actionLabel, alpha)
        }
    }

    private fun drawStatus(canvas: Canvas) {
        val alpha = ((1f - animProgress * 0.2f) * 255).toInt().coerceIn(0, 255)
        val centerX = width / 2f
        val centerY = height * 0.2f
        drawLabelPill(canvas, centerX, centerY, actionLabel, alpha)
    }

    private fun drawLabelPill(canvas: Canvas, centerX: Float, centerY: Float, text: String, alpha: Int) {
        val textWidth = labelTextPaint.measureText(text)
        val paddingX = dp(14f)
        val paddingY = dp(8f)
        val pillRect = RectF(
            centerX - textWidth / 2f - paddingX,
            centerY - paddingY - dp(8f),
            centerX + textWidth / 2f + paddingX,
            centerY + paddingY + dp(8f)
        )

        // Keep inside screen bounds
        if (pillRect.left < dp(8f)) {
            pillRect.offset(dp(8f) - pillRect.left, 0f)
        }
        if (pillRect.right > width - dp(8f)) {
            pillRect.offset((width - dp(8f)) - pillRect.right, 0f)
        }

        labelBgPaint.alpha = alpha
        labelTextPaint.alpha = alpha

        canvas.drawRoundRect(pillRect, dp(12f), dp(12f), labelBgPaint)

        // Draw text vertically centered
        val fontMetrics = labelTextPaint.fontMetrics
        val textY = pillRect.centerY() - (fontMetrics.top + fontMetrics.bottom) / 2f
        canvas.drawText(text, pillRect.centerX(), textY, labelTextPaint)
    }
}
