package dev.geocam.app.storage

import android.util.Log
import androidx.exifinterface.media.ExifInterface
import dev.geocam.app.location.GpsSnapshot
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

/**
 * Writes EXIF tags into a JPEG byte array.
 *
 * - Uses Jetpack ExifInterface for reliable OEM compatibility.
 * - GPS EXIF only written when a real fix exists and GPS is not disabled.
 * - Orientation tag baked in (rotation applied to bitmap before encode,
 *   so we set ORIENTATION_NORMAL here).
 * - Never double-rotates: we handle rotation in the image pipeline,
 *   then always mark the file as upright.
 *
 * LEGACY: ExifInterface is available from API 1+ (platform); Jetpack
 * version adds OEM bug fixes and is safe to keep as-is.
 */
object ExifWriter {

    private const val TAG = "ExifWriter"

    /** Local formatter: SimpleDateFormat is mutable and not thread-safe. */
    private fun formatExifDate(timeMs: Long): String =
        SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
            .apply { timeZone = TimeZone.getDefault() }
            .format(Date(timeMs))

    /**
     * Writes EXIF into [file] at [path] after saving.
     * This is the preferred approach since ExifInterface.saveAttributes()
     * works most reliably on a file path.
     */
    fun writeExifToFile(
        path: String,
        snapshot: GpsSnapshot?,
        captureTimeMs: Long = System.currentTimeMillis()
    ) {
        try {
            val exif = ExifInterface(path)

            exif.setAttribute(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL.toString()
            )

            val timeMs = snapshot?.fixTimeMs ?: captureTimeMs
            val dateStr = formatExifDate(timeMs)
            exif.setAttribute(ExifInterface.TAG_DATETIME, dateStr)
            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, dateStr)
            exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, dateStr)

            clearGpsExif(exif)
            if (snapshot?.isValid == true) {
                writeGpsExif(exif, snapshot)
            }

            exif.saveAttributes()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write EXIF to file $path", e)
        }
    }

    private fun writeGpsExif(exif: ExifInterface, snapshot: GpsSnapshot) {
        // Latitude
        val lat = snapshot.latitude
        val latRef = if (lat >= 0) "N" else "S"
        exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, decimalToDms(abs(lat)))
        exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, latRef)

        // Longitude
        val lon = snapshot.longitude
        val lonRef = if (lon >= 0) "E" else "W"
        exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, decimalToDms(abs(lon)))
        exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, lonRef)

        // Altitude
        snapshot.altitude?.let { alt ->
            val altRef = if (alt >= 0) "0" else "1"
            exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, "${abs(alt).toLong()}/1")
            exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF, altRef)
        }

        // GPS timestamp (UTC)
        val utcFormat = SimpleDateFormat("HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val dateFormat = SimpleDateFormat("yyyy:MM:dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val date = Date(snapshot.fixTimeMs)
        exif.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, utcFormat.format(date))
        exif.setAttribute(ExifInterface.TAG_GPS_DATESTAMP, dateFormat.format(date))

        // Horizontal accuracy, in metres. This deliberately does NOT go into
        // TAG_GPS_DOP: DOP is a dimensionless dilution-of-precision ratio, so
        // writing a metre value there produces metadata readers misreport.
        exif.setAttribute(ExifInterface.TAG_GPS_H_POSITIONING_ERROR, "${snapshot.accuracy.toInt()}/1")
    }

    private fun clearGpsExif(exif: ExifInterface) {
        listOf(
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_TIMESTAMP,
            ExifInterface.TAG_GPS_DATESTAMP,
            ExifInterface.TAG_GPS_PROCESSING_METHOD,
            ExifInterface.TAG_GPS_SPEED,
            ExifInterface.TAG_GPS_SPEED_REF,
            ExifInterface.TAG_GPS_TRACK,
            ExifInterface.TAG_GPS_TRACK_REF,
            ExifInterface.TAG_GPS_IMG_DIRECTION,
            ExifInterface.TAG_GPS_IMG_DIRECTION_REF,
            ExifInterface.TAG_GPS_DOP,
            ExifInterface.TAG_GPS_H_POSITIONING_ERROR
        ).forEach { exif.setAttribute(it, null) }
    }

    /**
     * Convert decimal degrees to EXIF DMS rational format: "DD/1,MM/1,SSSSSS/1000000"
     */
    private fun decimalToDms(decimal: Double): String {
        val degrees = decimal.toInt()
        val minutesFloat = (decimal - degrees) * 60
        val minutes = minutesFloat.toInt()
        val secondsFloat = (minutesFloat - minutes) * 60
        val secondsMicro = (secondsFloat * 1_000_000).toLong()
        return "$degrees/1,$minutes/1,$secondsMicro/1000000"
    }
}
