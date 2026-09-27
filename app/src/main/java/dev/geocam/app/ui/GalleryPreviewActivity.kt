package dev.geocam.app.ui

import android.os.Bundle
import android.net.Uri
import android.content.ClipData
import android.content.ContentUris
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.bumptech.glide.Glide
import dev.geocam.app.R
import androidx.exifinterface.media.ExifInterface
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class GalleryPreviewActivity : AppCompatActivity() {
    private var currentPhotoUri: Uri? = null
    private val photoUris = mutableListOf<Uri>()
    private var currentPhotoIndex = 0
    private lateinit var imageView: ZoomableImageView
    private lateinit var fileNameText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_gallery_preview)

        // The photo itself stays full-bleed, but the action buttons must clear the
        // system navigation bar (3-button nav overlaps them, gesture nav does not).
        val navBarViews = listOf(
            findViewById<View>(R.id.infoButton),
            findViewById<View>(R.id.deleteButton),
            findViewById<View>(R.id.shareButton)
        )
        val baseBottomMargins = navBarViews.map { view ->
            (view.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin
        }
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            navBarViews.forEachIndexed { index, view ->
                view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    bottomMargin = baseBottomMargins[index] + bars.bottom
                }
            }
            insets
        }

        val requestedPhotoUri = intent.getStringExtra("photo_uri")?.let {
            runCatching { Uri.parse(it) }.getOrNull()
        }
        imageView = findViewById(R.id.previewPhoto)
        val infoBtn = findViewById<ImageButton>(R.id.infoButton)
        val deleteBtn = findViewById<ImageButton>(R.id.deleteButton)
        val shareBtn = findViewById<ImageButton>(R.id.shareButton)
        fileNameText = findViewById(R.id.fileNameText)

        if (requestedPhotoUri == null || requestedPhotoUri.scheme != "content") {
            Toast.makeText(this, R.string.photo_unavailable, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        loadPhotoList()
        if (photoUris.isEmpty()) {
            Toast.makeText(this, R.string.photo_unavailable, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        currentPhotoIndex = photoUris.indexOf(requestedPhotoUri).takeIf { it >= 0 } ?: 0
        showPhotoAt(currentPhotoIndex)
        imageView.onSwipeLeft = { showPhotoAt(currentPhotoIndex + 1) }
        imageView.onSwipeRight = { showPhotoAt(currentPhotoIndex - 1) }

        infoBtn.setOnClickListener {
            fileNameText.visibility = if (fileNameText.visibility == View.GONE) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }

        deleteBtn.setOnClickListener {
            val deletingUri = currentPhotoUri ?: return@setOnClickListener
            AlertDialog.Builder(this)
                .setTitle(R.string.delete_photo_title)
                .setMessage(R.string.delete_photo_confirmation)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.delete_photo) { _, _ ->
                    val deleted = runCatching { contentResolver.delete(deletingUri, null, null) > 0 }
                        .getOrDefault(false)
                    if (deleted) {
                        photoUris.removeAt(currentPhotoIndex)
                        if (photoUris.isNotEmpty()) {
                            currentPhotoIndex = currentPhotoIndex.coerceAtMost(photoUris.lastIndex)
                            showPhotoAt(currentPhotoIndex)
                            Toast.makeText(this, R.string.photo_deleted, Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, R.string.photo_deleted, Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    } else {
                        Toast.makeText(this, R.string.photo_delete_failed, Toast.LENGTH_SHORT).show()
                    }
                }
                .show()
        }

        shareBtn.setOnClickListener {
            val shareUri = currentPhotoUri ?: return@setOnClickListener
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, shareUri)
                clipData = ClipData.newUri(contentResolver, getString(R.string.photo_details), shareUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_photo)))
        }
    }

    private fun showPhotoAt(index: Int) {
        if (index !in photoUris.indices) return
        currentPhotoIndex = index
        val uri = photoUris[index]
        currentPhotoUri = uri
        Glide.with(this).clear(imageView)
        Glide.with(this).load(uri).into(imageView)
        fileNameText.text = readPhotoDetails(uri)
        fileNameText.visibility = View.GONE
    }

    private fun loadPhotoList() {
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val selection = "${MediaStore.Images.Media.RELATIVE_PATH} = ?"
        val args = arrayOf("${android.os.Environment.DIRECTORY_PICTURES}/GeoCam/")
        val sort = "${MediaStore.Images.Media.DATE_TAKEN} DESC, ${MediaStore.Images.Media.DATE_ADDED} DESC, ${MediaStore.Images.Media._ID} DESC"
        runCatching {
            contentResolver.query(collection, projection, selection, args, sort)?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                while (cursor.moveToNext()) {
                    photoUris += ContentUris.withAppendedId(collection, cursor.getLong(idColumn))
                }
            }
        }.onFailure { android.util.Log.e(TAG, "Failed to load GeoCam photo list", it) }
    }

    private fun readPhotoDetails(uri: Uri): String {
        var fileName = uri.lastPathSegment ?: getString(R.string.photo_details)
        var capturedAt: String? = null
        var dateAddedMs: Long? = null
        var location: String? = null

        runCatching {
            contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, MediaStore.Images.Media.DATE_TAKEN, MediaStore.Images.Media.DATE_ADDED),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let {
                        fileName = cursor.getString(it) ?: fileName
                    }
                    cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN).takeIf { it >= 0 }?.let {
                        capturedAt = cursor.getLong(it).takeIf { value -> value > 0L }?.let(::formatDate)
                    }
                    cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED).takeIf { it >= 0 }?.let {
                        dateAddedMs = cursor.getLong(it).takeIf { value -> value > 0L }?.times(1000L)
                    }
                }
            }
        }

        runCatching {
            contentResolver.openInputStream(uri)?.use { input ->
                val exif = ExifInterface(input)
                val coordinates = FloatArray(2)
                if (exif.getLatLong(coordinates)) {
                    location = "%.6f, %.6f".format(Locale.US, coordinates[0], coordinates[1])
                }
                if (capturedAt == null) {
                    capturedAt = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                        ?.let { parseExifDate(it) }
                }
            }
        }
        if (capturedAt == null) capturedAt = dateAddedMs?.let(::formatDate)

        return buildString {
            append(getString(R.string.photo_info_filename, fileName))
            append('\n')
            append(getString(R.string.photo_info_captured, capturedAt ?: getString(R.string.photo_info_unknown)))
            append('\n')
            append(getString(R.string.photo_info_location, location ?: getString(R.string.photo_info_not_recorded)))
        }
    }

    private fun formatDate(timeMs: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.getDefault()).format(Date(timeMs))

    private fun parseExifDate(value: String): String? = runCatching {
        val parser = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
        parser.timeZone = TimeZone.getDefault()
        formatDate(parser.parse(value)?.time ?: return null)
    }.getOrNull()

    private companion object {
        const val TAG = "GalleryPreview"
    }
}
