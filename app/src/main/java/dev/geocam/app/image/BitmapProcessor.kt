package dev.geocam.app.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Decodes CameraX output into a mutable, correctly-oriented bitmap and writes
 * the processed JPEG to disk without holding duplicate JPEG byte arrays.
 *
 * CameraX records orientation in EXIF rather than rotating the pixels, so
 * anything that re-encodes a capture has to apply that rotation itself —
 * otherwise the overlay card would be drawn onto a sideways photo. This object
 * is the only place that knows about it.
 *
 * Mirrored EXIF orientations (FLIP_HORIZONTAL and friends) are deliberately not
 * applied: CameraX does not emit them, and honouring them would double-flip a
 * selfie that the caller already mirrored on purpose.
 */
object BitmapProcessor {

    private const val TAG = "BitmapProcessor"

    /** Decodes to a mutable upright bitmap, or returns null on decode/memory failure. */
    fun decodeUpright(file: File): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inMutable = true
        }
        val decoded = try {
            BitmapFactory.decodeFile(file.absolutePath, options)
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "Insufficient memory to decode JPEG file (${file.length()} bytes)", e)
            return null
        }
        if (decoded == null) {
            Log.e(TAG, "Failed to decode JPEG file ${file.absolutePath}")
            return null
        }
        val orientation = try {
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } catch (e: Exception) {
            Log.w(TAG, "Could not read EXIF orientation; assuming upright", e)
            ExifInterface.ORIENTATION_NORMAL
        }
        return orientDecoded(decoded, orientationDegrees(orientation))
    }

    private fun orientDecoded(decoded: Bitmap, degrees: Int): Bitmap? {
        var upright = decoded
        if (degrees != 0) {
            val rotated = try {
                val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
                Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            } catch (e: OutOfMemoryError) {
                Log.e(TAG, "Insufficient memory to rotate JPEG; preserving original capture", e)
                decoded.recycle()
                return null
            } catch (e: Exception) {
                Log.e(TAG, "Failed to rotate by $degrees degrees; using unrotated bitmap", e)
                null
            }
            if (rotated != null && rotated !== decoded) {
                decoded.recycle()
                upright = rotated
            }
        }

        // Some devices return immutable bitmaps from BitmapFactory.
        if (upright.isMutable) return upright
        val mutable = try {
            upright.copy(Bitmap.Config.ARGB_8888, true)
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "Insufficient memory to prepare mutable JPEG bitmap", e)
            null
        }
        if (mutable == null) {
            upright.recycle()
            return null
        }
        upright.recycle()
        return mutable
    }

    /**
     * Mirrors [bitmap] horizontally and returns the new bitmap. The input is left
     * untouched and remains the caller's to recycle.
     *
    * Call it before drawing the overlay, so the card is laid out in the final
    * frame and remains readable in mirrored selfies.
     */
    fun mirrorHorizontally(bitmap: Bitmap): Bitmap {
        val matrix = Matrix().apply { postScale(-1f, 1f) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    fun encodeToFile(bitmap: Bitmap, file: File, quality: Int = 92, maxLongEdge: Int = 0) {
        val target = if (maxLongEdge > 0) downscale(bitmap, maxLongEdge) else bitmap
        try {
            FileOutputStream(file).use { output ->
                check(target.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(1, 100), output)) {
                    "Bitmap.compress returned false"
                }
                output.flush()
            }
        } finally {
            if (target !== bitmap) target.recycle()
        }
    }

    private fun downscale(bitmap: Bitmap, maxLongEdge: Int): Bitmap {
        val longEdge = max(bitmap.width, bitmap.height)
        if (longEdge <= maxLongEdge) return bitmap
        val scale = maxLongEdge.toFloat() / longEdge
        val w = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val h = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }

    private fun orientationDegrees(orientation: Int): Int = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }
}
