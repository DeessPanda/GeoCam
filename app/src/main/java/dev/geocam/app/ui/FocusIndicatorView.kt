package dev.geocam.app.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator

class FocusIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFD54F.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }
    private var focusX = 0f
    private var focusY = 0f
    private var ringScale = 0.7f
    private var ringAlpha = 255
    private var animator: ValueAnimator? = null

    init {
        isClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun showAt(x: Float, y: Float) {
        focusX = x
        focusY = y
        ringScale = 0.7f
        ringAlpha = 255
        visibility = VISIBLE
        alpha = 1f
        animate().cancel()
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 520L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val progress = it.animatedValue as Float
                ringScale = 0.7f + 0.3f * progress
                ringAlpha = (255 * (1f - progress * 0.4f)).toInt()
                invalidate()
            }
            start()
        }
        animate().alpha(0f).setStartDelay(450L).setDuration(350L).withEndAction {
            visibility = INVISIBLE
            alpha = 1f
        }.start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paint.alpha = ringAlpha
        val radius = 34f * density * ringScale
        canvas.drawCircle(focusX, focusY, radius, paint)
        val inset = 9f * density
        canvas.drawLine(focusX - radius, focusY - radius, focusX - radius + inset, focusY - radius, paint)
        canvas.drawLine(focusX - radius, focusY - radius, focusX - radius, focusY - radius + inset, paint)
        canvas.drawLine(focusX + radius, focusY - radius, focusX + radius - inset, focusY - radius, paint)
        canvas.drawLine(focusX + radius, focusY - radius, focusX + radius, focusY - radius + inset, paint)
        canvas.drawLine(focusX - radius, focusY + radius, focusX - radius + inset, focusY + radius, paint)
        canvas.drawLine(focusX - radius, focusY + radius, focusX - radius, focusY + radius - inset, paint)
        canvas.drawLine(focusX + radius, focusY + radius, focusX + radius - inset, focusY + radius, paint)
        canvas.drawLine(focusX + radius, focusY + radius, focusX + radius, focusY + radius - inset, paint)
    }
}