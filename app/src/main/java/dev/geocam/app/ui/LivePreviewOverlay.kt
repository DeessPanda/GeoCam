package dev.geocam.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import dev.geocam.app.geocoding.PlaceName
import dev.geocam.app.location.GpsSnapshot
import dev.geocam.app.overlay.OverlayRenderer
import dev.geocam.app.settings.SettingsRepository

class LivePreviewOverlay @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var snapshot: GpsSnapshot? = null
    private var placeName: PlaceName? = null
    private var mapTile: Bitmap? = null
    private var settings: SettingsRepository? = null
    private var deviceRotationDegrees: Int = 0
    private var headingDegrees: Float? = null

    fun updateData(
        snapshot: GpsSnapshot?,
        placeName: PlaceName?,
        mapTile: Bitmap?,
        settings: SettingsRepository,
        deviceRotationDegrees: Int,
        headingDegrees: Float? = null
    ) {
        this.snapshot = snapshot
        this.placeName = placeName
        this.mapTile = mapTile
        this.settings = settings
        this.deviceRotationDegrees = deviceRotationDegrees
        this.headingDegrees = headingDegrees
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val s = settings ?: return
        OverlayRenderer.drawOnCanvas(
            canvas = canvas,
            width = width.toFloat(),
            height = height.toFloat(),
            rotationDegrees = deviceRotationDegrees,
            snapshot = snapshot,
            placeName = placeName,
            mapTile = mapTile,
            settings = s,
            headingDegrees = headingDegrees
        )
    }
}
