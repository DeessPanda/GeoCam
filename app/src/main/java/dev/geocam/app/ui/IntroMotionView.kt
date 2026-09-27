package dev.geocam.app.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.min

class IntroMotionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var phase = 0f
    private var animator: ValueAnimator? = null

    fun startMotion() {
        if (animator?.isRunning == true) return
        animator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 8_000L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                phase = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun stopMotion() {
        animator?.cancel()
        animator = null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val shortEdge = min(width, height).toFloat()
        val centerX = width * 0.5f
        val centerY = height * 0.48f
        drawShape(canvas, centerX - shortEdge * 0.34f, centerY - shortEdge * 0.22f, shortEdge * 0.19f, 0xFF8FD3C7.toInt(), phase)
        drawShape(canvas, centerX + shortEdge * 0.32f, centerY + shortEdge * 0.24f, shortEdge * 0.13f, 0xFFFFC6A8.toInt(), -phase * 0.72f)
        drawShape(canvas, centerX + shortEdge * 0.36f, centerY - shortEdge * 0.31f, shortEdge * 0.075f, 0xFFFFD166.toInt(), phase * 1.18f)
    }

    private fun drawShape(canvas: Canvas, cx: Float, cy: Float, size: Float, color: Int, rotation: Float) {
        canvas.save()
        canvas.rotate(rotation, cx, cy)
        paint.color = color
        paint.alpha = 42
        paint.style = Paint.Style.FILL
        val radius = size * 0.36f
        canvas.drawRoundRect(RectF(cx - size, cy - size * 0.58f, cx + size, cy + size * 0.58f), radius, radius, paint)
        paint.alpha = 68
        canvas.drawCircle(cx + size * 0.47f, cy - size * 0.38f, size * 0.55f, paint)
        canvas.restore()
    }

    override fun onDetachedFromWindow() {
        stopMotion()
        super.onDetachedFromWindow()
    }
}