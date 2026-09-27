package dev.geocam.app.ui

import android.content.Context
import android.graphics.Matrix
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.abs
import kotlin.math.min

class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {

    private val imageMatrixValues = FloatArray(9)
    private val viewMatrix = Matrix()
    private val swipeDistancePx = 72f * resources.displayMetrics.density
    private var scale = 1f
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var lastX = 0f
    private var lastY = 0f
    var onSwipeRight: (() -> Unit)? = null
    var onSwipeLeft: (() -> Unit)? = null

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val nextScale = (scale * detector.scaleFactor).coerceIn(MIN_SCALE, MAX_SCALE)
            viewMatrix.getValues(imageMatrixValues)
            val appliedScale = nextScale / scale
            viewMatrix.postScale(appliedScale, appliedScale, detector.focusX, detector.focusY)
            scale = nextScale
            applyBounds()
            imageMatrix = viewMatrix
            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(event: MotionEvent) = true

        override fun onDoubleTap(event: MotionEvent): Boolean {
            val target = if (scale > 1f) 1f else 2.5f
            val factor = target / scale
            viewMatrix.postScale(factor, factor, event.x, event.y)
            scale = target
            applyBounds()
            imageMatrix = viewMatrix
            return true
        }
    })

    init {
        scaleType = ScaleType.MATRIX
        isClickable = true
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        resetImageMatrix()
    }

    override fun setImageDrawable(drawable: android.graphics.drawable.Drawable?) {
        super.setImageDrawable(drawable)
        post { resetImageMatrix() }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                downTime = event.eventTime
                lastX = event.x
                lastY = event.y
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress && scale > 1f) {
                    viewMatrix.postTranslate(event.x - lastX, event.y - lastY)
                    applyBounds()
                    imageMatrix = viewMatrix
                }
                lastX = event.x
                lastY = event.y
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    val deltaX = event.x - downX
                    val deltaY = event.y - downY
                    val durationMs = (event.eventTime - downTime).coerceAtLeast(1L)
                    val velocityX = abs(deltaX) * 1000f / durationMs
                    if (scale <= 1.01f && abs(deltaX) >= swipeDistancePx &&
                        velocityX >= SWIPE_VELOCITY_PX && abs(deltaX) >= abs(deltaY) * 1.25f
                    ) {
                        if (deltaX > 0f) onSwipeRight?.invoke() else onSwipeLeft?.invoke()
                    }
                    performClick()
                }
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun resetImageMatrix() {
        val image = drawable ?: return
        if (width == 0 || height == 0 || image.intrinsicWidth <= 0 || image.intrinsicHeight <= 0) return
        val fitScale = min(width.toFloat() / image.intrinsicWidth, height.toFloat() / image.intrinsicHeight)
        val dx = (width - image.intrinsicWidth * fitScale) / 2f
        val dy = (height - image.intrinsicHeight * fitScale) / 2f
        viewMatrix.setScale(fitScale, fitScale)
        viewMatrix.postTranslate(dx, dy)
        scale = 1f
        imageMatrix = viewMatrix
    }

    private fun applyBounds() {
        val image = drawable ?: return
        viewMatrix.getValues(imageMatrixValues)
        val imageWidth = image.intrinsicWidth * imageMatrixValues[Matrix.MSCALE_X]
        val imageHeight = image.intrinsicHeight * imageMatrixValues[Matrix.MSCALE_Y]
        var dx = imageMatrixValues[Matrix.MTRANS_X]
        var dy = imageMatrixValues[Matrix.MTRANS_Y]
        dx = if (imageWidth <= width) (width - imageWidth) / 2f else dx.coerceIn(width - imageWidth, 0f)
        dy = if (imageHeight <= height) (height - imageHeight) / 2f else dy.coerceIn(height - imageHeight, 0f)
        viewMatrix.setValues(imageMatrixValues)
        viewMatrix.postTranslate(dx - imageMatrixValues[Matrix.MTRANS_X], dy - imageMatrixValues[Matrix.MTRANS_Y])
    }

    companion object {
        private const val MIN_SCALE = 1f
        private const val MAX_SCALE = 6f
        private const val SWIPE_VELOCITY_PX = 500f
    }
}