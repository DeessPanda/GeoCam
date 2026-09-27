package dev.geocam.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class RuleOfThirdsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x55FFFFFF
        strokeWidth = resources.displayMetrics.density
    }

    init {
        isClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val thirdX = width / 3f
        val thirdY = height / 3f
        canvas.drawLine(thirdX, 0f, thirdX, height.toFloat(), paint)
        canvas.drawLine(thirdX * 2f, 0f, thirdX * 2f, height.toFloat(), paint)
        canvas.drawLine(0f, thirdY, width.toFloat(), thirdY, paint)
        canvas.drawLine(0f, thirdY * 2f, width.toFloat(), thirdY * 2f, paint)
    }
}