package dev.geocam.app.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.IOException

/**
 * Saves photos via MediaStore to Pictures/GeoCam.
 * Uses IS_PENDING pattern for atomic writes — no partial files appear in the gallery.
 * No storage permission required on Android 10+ (minSdk 29).
 * LEGACY: On API 23-28 use WRITE_EXTERNAL_STORAGE + file-based save + MediaScannerConnection.
 */
class MediaStorePhotoSaver(context: Context) : PhotoSaver {

    private val context = context.applicationContext

    override fun save(jpegFile: File, displayName: String): Uri? {
        if (!jpegFile.isFile || jpegFile.length() <= 0L) return null
        val resolver = context.contentResolver
        val values = createValues(displayName)
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        var uri: Uri? = null
        return try {
            uri = resolver.insert(collection, values)
                ?: return null.also { Log.e(TAG, "MediaStore insert returned null") }
            val output = resolver.openOutputStream(uri, "w")
                ?: throw IOException("Failed to open output stream for $uri")
            jpegFile.inputStream().buffered(BUFFER_SIZE).use { input ->
                output.buffered(BUFFER_SIZE).use { input.copyTo(it, BUFFER_SIZE) }
            }
            publish(resolver, uri, values)
            uri
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save photo file", e)
            uri?.let { resolver.delete(it, null, null) }
            null
        }
    }

    private fun createValues(displayName: String) = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/GeoCam")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }

    private fun publish(resolver: android.content.ContentResolver, uri: Uri, values: ContentValues) {
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        if (resolver.update(uri, values, null, null) != 1) {
            throw IOException("MediaStore did not publish photo $uri")
        }
    }

    companion object {
        private const val TAG = "MediaStorePhotoSaver"
        private const val BUFFER_SIZE = 64 * 1024
    }
}
