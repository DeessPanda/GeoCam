package dev.geocam.app.overlay

import dev.geocam.app.map.MAP_ATTRIBUTION
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import dev.geocam.app.geocoding.PlaceName
import dev.geocam.app.location.GpsSnapshot
import dev.geocam.app.settings.SettingsRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object OverlayRenderer {

    // Layout configuration to cover ~ 1/5th to 1/4th of the screen
    private const val CARD_WIDTH_FRACTION = 0.90f // 90% of the short edge
    private const val MAP_FRACTION_OF_CARD = 0.28f // 28% of the width is map
    private const val CORNER_FRACTION = 0.04f
    private const val PADDING_FRACTION = 0.04f
    private const val BOTTOM_MARGIN_FRACTION = 0.04f

    // Font sizes relative to short edge
    private const val PRIMARY_SIZE = 0.045f   // Largest text
    private const val SECONDARY_SIZE = 0.026f // Medium text
    private const val TINY_SIZE = 0.020f      // Small text
    private const val MOCK_SIZE = 0.028f
    private const val ATTRIBUTION_SIZE = 0.012f

    private const val PIN_RADIUS_FRACTION = 0.008f
    private const val PIN_HALO_FRACTION = 0.016f

    private const val CARD_BG_COLOR = 0xB3000000.toInt()
    private const val OFFLINE_MAP_COLOR = 0xFF353535.toInt()

    private enum class Style { PLACE_NAME, SECONDARY, MOCK_WARNING, TINY }
    private data class CardLine(val text: String, val style: Style)

    /**
     * Draws the overlay on an existing mutable Bitmap.
     */
    fun draw(
        bitmap: Bitmap,
        snapshot: GpsSnapshot?,
        placeName: PlaceName?,
        mapTile: Bitmap?,
        settings: SettingsRepository,
        headingDegrees: Float? = null
    ) {
        if (bitmap.isRecycled || !bitmap.isMutable) return
        val canvas = Canvas(bitmap)
        drawOnCanvas(
            canvas,
            bitmap.width.toFloat(),
            bitmap.height.toFloat(),
            0, // Default orientation is 0 for the final bitmap, because we rotate the bitmap upright prior.
            snapshot,
            placeName,
            mapTile,
            settings,
            headingDegrees
        )
    }

    /**
     * Draws the overlay on any Canvas (used for both the final Bitmap and the Live Preview).
     * @param rotationDegrees Rotation offset (0, 90, 180, 270) to orient the overlay drawing inside the canvas.
     */
    fun drawOnCanvas(
        canvas: Canvas,
        width: Float,
        height: Float,
        rotationDegrees: Int,
        snapshot: GpsSnapshot?,
        placeName: PlaceName?,
        mapTile: Bitmap?,
        settings: SettingsRepository,
        headingDegrees: Float? = null
    ) {
        val lines = buildLines(snapshot, placeName, settings)
        if (lines.isEmpty()) return

        canvas.save()

        // Handle rotation so the overlay draws upright relative to physical device orientation
        val pivotX = width / 2f
        val pivotY = height / 2f
        canvas.rotate(-rotationDegrees.toFloat(), pivotX, pivotY)

        // Calculate "logical" width and height based on the rotation we just applied
        val isLandscape = rotationDegrees == 90 || rotationDegrees == 270
        val logicalWidth = if (isLandscape) height else width
        val logicalHeight = if (isLandscape) width else height
        val logicalLeft = pivotX - logicalWidth / 2f
        val logicalTop = pivotY - logicalHeight / 2f

        val shortEdge = min(logicalWidth, logicalHeight)

        val cardWidth = shortEdge * CARD_WIDTH_FRACTION
        val padding = cardWidth * PADDING_FRACTION
        val corner = cardWidth * CORNER_FRACTION

        val primaryPaint = makePaint(shortEdge * PRIMARY_SIZE, Color.WHITE, true)
        val secondaryPaint = makePaint(shortEdge * SECONDARY_SIZE, Color.WHITE, false)
        val mockPaint = makePaint(shortEdge * MOCK_SIZE, 0xFFFFEB3B.toInt(), true)
        val tinyPaint = makePaint(shortEdge * TINY_SIZE, 0xFFBBBBBB.toInt(), false)
        val attributionPaint = makePaint(shortEdge * ATTRIBUTION_SIZE, 0xFFCCCCCC.toInt(), false)

        val showMap = settings.miniMapEnabled
        val mapSize = if (showMap) cardWidth * MAP_FRACTION_OF_CARD else 0f

        val cardLeft = logicalLeft + (logicalWidth - cardWidth) / 2f
        val cardBottom = logicalTop + logicalHeight - shortEdge * BOTTOM_MARGIN_FRACTION

        val textLeft = if (showMap) cardLeft + padding + mapSize + padding else cardLeft + padding
        val textWidth = (cardLeft + cardWidth - padding) - textLeft
        val wrappedLines = lines.flatMap { line ->
            wrapText(line.text, paintFor(line.style, primaryPaint, secondaryPaint, mockPaint, tinyPaint), textWidth)
                .map { CardLine(it, line.style) }
        }

        var textHeight = 0f
        for (line in wrappedLines) {
            textHeight += paintFor(line.style, primaryPaint, secondaryPaint, mockPaint, tinyPaint).textSize + padding * 0.2f
        }

        val mapBlockHeight = if (showMap) mapSize + attributionPaint.textSize + padding * 0.30f else 0f
        val cardHeight = max(textHeight, mapBlockHeight) + padding * 2f
        val cardTop = cardBottom - cardHeight

        // Background
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = CARD_BG_COLOR
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(RectF(cardLeft, cardTop, cardLeft + cardWidth, cardBottom), corner, corner, cardPaint)

        // Mini-map
        if (showMap) {
            val mapLeft = cardLeft + padding
            val mapTop = cardTop + padding
            val mapRect = RectF(mapLeft, mapTop, mapLeft + mapSize, mapTop + mapSize)
            val mapCorner = corner * 0.5f

            if (mapTile != null && !mapTile.isRecycled) {
                // Calculate tile positioning based on actual GPS location for accuracy
                // Use the tile's center to align with map pin position
                val tileLeft = mapLeft
                val tileTop = mapTop
                val tileWidth = mapSize
                val tileHeight = mapSize

                val clip = Path()
                clip.addRoundRect(RectF(tileLeft, tileTop, tileLeft + tileWidth, tileTop + tileHeight), mapCorner, mapCorner, Path.Direction.CW)
                canvas.save()
                canvas.clipPath(clip)
                canvas.drawBitmap(mapTile, Rect(0, 0, mapTile.width, mapTile.height), RectF(tileLeft, tileTop, tileLeft + tileWidth, tileTop + tileHeight), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                canvas.restore()
                // Draw pin exactly at tile center for accurate positioning
                val pinX = tileLeft + tileWidth / 2f
                val pinY = tileTop + tileHeight / 2f
                headingDegrees?.let { drawHeadingCone(canvas, pinX, pinY, mapSize, it) }
                drawPin(canvas, pinX, pinY, shortEdge)
            } else {
                val offlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = OFFLINE_MAP_COLOR }
                canvas.drawRoundRect(mapRect, mapCorner, mapCorner, offlinePaint)
                headingDegrees?.let { drawHeadingCone(canvas, mapRect.centerX(), mapRect.centerY(), mapSize, it) }
                drawPin(canvas, mapRect.centerX(), mapRect.centerY(), shortEdge)
            }
            // Credit line for whichever tile source this build uses. The OpenStreetMap
            // variant is required by the ODbL, the Google one is required by its terms.
            canvas.drawText(
                MAP_ATTRIBUTION,
                mapLeft,
                mapTop + mapSize + attributionPaint.textSize + padding * 0.15f,
                attributionPaint
            )
        }

        // Text
        var baseline = cardTop + padding + max(0f, (mapBlockHeight - textHeight) / 2f)
        for (line in wrappedLines) {
            val paint = paintFor(line.style, primaryPaint, secondaryPaint, mockPaint, tinyPaint)
            baseline += paint.textSize
            canvas.drawText(line.text, textLeft, baseline, paint)
            baseline += padding * 0.2f
        }

        canvas.restore()
    }

    private fun buildLines(snapshot: GpsSnapshot?, placeName: PlaceName?, settings: SettingsRepository): List<CardLine> {
        val lines = mutableListOf<CardLine>()

        if (snapshot?.isMock == true) lines += CardLine("⚠ MOCK LOCATION", Style.MOCK_WARNING)

        val hasValidFix = snapshot?.isValid == true

        // Line 1: Area Name (main area + city + state)
        if (settings.showPlaceName) {
            val area = when {
                !hasValidFix && snapshot != null -> "Locating…"
                hasValidFix && !placeName?.primaryName.isNullOrBlank() -> placeName!!.primaryName
                hasValidFix -> "Area unavailable"
                else -> "No GPS fix"
            }
            lines += CardLine(area, Style.PLACE_NAME)
        }

        // Line 2: Full Address (if available)
        if (hasValidFix && placeName?.formattedAddress != null) {
            // Split long addresses to prevent overflow by breaking at natural points
            val formatted = placeName.formattedAddress
            lines += CardLine(formatted, Style.SECONDARY)
        } else if (hasValidFix && placeName?.cityState != null) {
            lines += CardLine(placeName.cityState, Style.SECONDARY)
        }

        // Line 3: Date, Time and TimeRegion
        if (settings.showDateTime) {
            val timeMs = snapshot?.fixTimeMs ?: System.currentTimeMillis()
            val tz = TimeZone.getDefault()
            val formatter = SimpleDateFormat("MMM dd, yyyy • h:mm a z", Locale.getDefault()).apply { timeZone = tz }
            lines += CardLine(formatter.format(Date(timeMs)), Style.SECONDARY)
        }

        // Line 4: Lat/Lng, Accuracy, Altitude
        if (snapshot != null) {
            val parts = mutableListOf<String>()
            if (settings.showCoordinates) {
                parts.add(if (settings.useDecimalCoords) formatDecimal(snapshot.latitude, snapshot.longitude) else formatDms(snapshot.latitude, snapshot.longitude))
            } else {
                parts.add("%.5f, %.5f".format(snapshot.latitude, snapshot.longitude))
            }
            if (settings.showAccuracy) parts.add("±${snapshot.accuracy.roundToInt()}m")
            if (settings.showAltitude && snapshot.altitude != null) parts.add("Alt ${snapshot.altitude.roundToInt()}m")
            if (settings.showHeading && snapshot.bearing != null) parts.add("${snapshot.bearing.roundToInt()}°")
            
            lines += CardLine(parts.joinToString(" • "), Style.TINY)
        }
        
        if (settings.customNote.isNotBlank()) lines += CardLine(settings.customNote, Style.TINY)

        return lines
    }

    private fun drawPin(canvas: Canvas, cx: Float, cy: Float, shortEdge: Float) {
        val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x444285F4; style = Paint.Style.FILL }
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF4285F4.toInt(); style = Paint.Style.FILL }

        canvas.drawCircle(cx, cy, shortEdge * PIN_HALO_FRACTION, halo)
        canvas.drawCircle(cx, cy, (shortEdge * PIN_RADIUS_FRACTION) * 1.5f, ring)
        canvas.drawCircle(cx, cy, shortEdge * PIN_RADIUS_FRACTION, dot)
    }

    private fun drawHeadingCone(canvas: Canvas, cx: Float, cy: Float, mapSize: Float, headingDegrees: Float) {
        val length = mapSize * 0.18f
        val halfWidth = mapSize * 0.055f
        val path = Path().apply {
            moveTo(-halfWidth, -mapSize * 0.015f)
            lineTo(0f, -length)
            lineTo(halfWidth, -mapSize * 0.015f)
            close()
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xA64285F4.toInt()
            style = Paint.Style.FILL
        }
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(headingDegrees)
        canvas.drawPath(path, paint)
        canvas.restore()
    }

    private fun makePaint(textSize: Float, color: Int, bold: Boolean): Paint {
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.textSize = textSize
            this.color = color
            this.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            setShadowLayer(textSize * 0.10f, 1f, 1f, Color.BLACK)
        }
    }

    private fun paintFor(style: Style, p1: Paint, p2: Paint, m: Paint, t: Paint) = when (style) {
        Style.PLACE_NAME -> p1
        Style.SECONDARY -> p2
        Style.MOCK_WARNING -> m
        Style.TINY -> t
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.trim().split(Regex("\\s+")).filter(String::isNotEmpty)
        if (words.isEmpty()) return listOf("")
        val lines = mutableListOf<String>()
        var current = ""
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (current.isNotEmpty() && paint.measureText(candidate) > maxWidth) {
                lines += current
                current = word
            } else {
                current = candidate
            }
        }
        if (current.isNotEmpty()) lines += current
        return lines
    }

    private fun formatDecimal(lat: Double, lon: Double): String {
        val latStr = "%.5f".format(abs(lat)) + if (lat >= 0) "°N" else "°S"
        val lonStr = "%.5f".format(abs(lon)) + if (lon >= 0) "°E" else "°W"
        return "$latStr $lonStr"
    }

    private fun formatDms(lat: Double, lon: Double): String {
        return "${toDms(lat, true)} ${toDms(lon, false)}"
    }

    private fun toDms(deg: Double, isLat: Boolean): String {
        val absDeg = abs(deg)
        val d = absDeg.toInt()
        val minutesFloat = (absDeg - d) * 60
        val m = minutesFloat.toInt()
        val s = (minutesFloat - m) * 60
        val dir = if (isLat) if (deg >= 0) "N" else "S" else if (deg >= 0) "E" else "W"
        return "%d°%02d'%04.1f\"%s".format(d, m, s, dir)
    }
}
