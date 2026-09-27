package dev.geocam.app.storage

import java.io.File
import android.net.Uri

/**
 * Abstraction over the photo saving mechanism.
 * Main version uses MediaStore; a legacy version can swap in a file-based impl.
 * LEGACY: If adding a legacy implementation, create a FilePhotoSaver that uses
 * Environment.getExternalStoragePublicDirectory + MediaScannerConnection.
 */
interface PhotoSaver {
    /**
     * Save a JPEG file into the gallery under Pictures/GeoCam using bounded-memory streaming.
     */
    fun save(jpegFile: File, displayName: String): Uri?
}
