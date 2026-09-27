package dev.geocam.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import java.util.Locale
import kotlin.math.roundToInt

class ExposureControlView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var minIndex = 0
    private var maxIndex = 0
    private var selectedIndex = 0
    private var evPerStep = 1f / 3f
    var onExposureChanged: ((Int) -> Unit)? = null

    init {
        contentDescription = "Exposure compensation"
        isClickable = true
    }

    fun setRange(min: Int, max: Int, selected: Int, stepEv: Float) {
        minIndex = min
        maxIndex = max
        selectedIndex = selected.coerceIn(min, max)
        evPerStep = stepEv
        isEnabled = min < max
        contentDescription = if (isEnabled) "Exposure ${exposureLabel()}" else "Exposure compensation unavailable"
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerX = width / 2f
        val top = 24f * density
        val bottom = height - 42f * density
        val middle = (top + bottom) / 2f

        paint.color = 0x99FFFFFF.toInt()
        paint.strokeWidth = 3f * density
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(centerX, top, centerX, bottom, paint)
        canvas.drawLine(centerX - 9f * density, middle, centerX + 9f * density, middle, paint)

        val fraction = if (maxIndex == minIndex) 0.5f else (selectedIndex - minIndex).toFloat() / (maxIndex - minIndex)
        val thumbY = bottom - fraction * (bottom - top)
        paint.color = if (isEnabled) 0xFFFFD54F.toInt() else 0xFF888888.toInt()
        paint.style = Paint.Style.FILL
        canvas.drawCircle(centerX, thumbY, 10f * density, paint)
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 12f * density
        canvas.drawText(exposureLabel(), centerX, height - 12f * density, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val top = 24f * density
                val bottom = height - 42f * density
                val fraction = (1f - (event.y - top) / (bottom - top)).coerceIn(0f, 1f)
                val next = (minIndex + fraction * (maxIndex - minIndex)).roundToInt()
                if (next != selectedIndex) {
                    selectedIndex = next
                    onExposureChanged?.invoke(next)
                    contentDescription = "Exposure ${exposureLabel()}"
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> return true
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun exposureLabel(): String = String.format(Locale.getDefault(), "%+.1f EV", selectedIndex * evPerStep)
}