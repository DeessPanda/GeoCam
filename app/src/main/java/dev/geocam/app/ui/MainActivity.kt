package dev.geocam.app.ui

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.AnimatorSet
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.KeyEvent
import android.view.OrientationEventListener
import android.view.Surface
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import com.google.android.material.imageview.ShapeableImageView
import dev.geocam.app.R
import dev.geocam.app.geocoding.GeocoderHelper
import dev.geocam.app.image.BitmapProcessor
import dev.geocam.app.location.GpsSnapshot
import dev.geocam.app.location.CompassHeadingProvider
import dev.geocam.app.location.LocationProvider
import dev.geocam.app.map.TileLoader
import dev.geocam.app.overlay.OverlayRenderer
import dev.geocam.app.settings.SettingsRepository
import dev.geocam.app.storage.ExifWriter
import dev.geocam.app.storage.MediaStorePhotoSaver
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var overlayPreview: LivePreviewOverlay
    private lateinit var captureBtn: ImageButton
    private lateinit var captureDimOverlay: View
    private lateinit var settingsBtn: ImageButton
    private lateinit var reloadLocationBtn: ImageButton
    private lateinit var mapZoomBtn: ImageButton
    private lateinit var flipBtn: ImageButton
    private lateinit var flashBtn: ImageButton
    private lateinit var galleryButton: ShapeableImageView
    private lateinit var previewContainer: FocusGestureFrameLayout
    private lateinit var focusIndicator: FocusIndicatorView
    private lateinit var exposureControl: ExposureControlView
    private lateinit var introScreen: View
    private lateinit var introBrand: View
    private lateinit var introMotion: IntroMotionView
    private lateinit var introCredit: View

    private lateinit var cameraController: LifecycleCameraController
    private var isCapturing = false
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var flashEnabled = false // 2-state: OFF (default), ON
    @Volatile private var torchEnabledForCapture = false
    private var torchCameraControl: androidx.camera.core.CameraControl? = null
    private var pendingTorchCapture: Runnable? = null

    private lateinit var locationProvider: LocationProvider
    private lateinit var settings: SettingsRepository
    private lateinit var executor: ExecutorService
    private lateinit var tileLoader: TileLoader
    private lateinit var geocoder: GeocoderHelper
    private lateinit var compassHeadingProvider: CompassHeadingProvider
    private val mainHandler = Handler(Looper.getMainLooper())
    
    private var orientationEventListener: OrientationEventListener? = null
    private var currentLiveRotation = 0
    @Volatile private var currentHeadingDegrees: Float? = null

    private var lastSavedPhotoUri: android.net.Uri? = null

    private lateinit var saver: MediaStorePhotoSaver

    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startCamera()
                checkLocationPermission()
            }
        }

    private val requestLocationPermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true || grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
                startLocation()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        overlayPreview = findViewById(R.id.overlayPreview)
        captureBtn = findViewById(R.id.captureButton)
        captureDimOverlay = findViewById(R.id.captureDimOverlay)
        settingsBtn = findViewById(R.id.settingsButton)
        reloadLocationBtn = findViewById(R.id.reloadLocationButton)
        mapZoomBtn = findViewById(R.id.mapZoomButton)
        flipBtn = findViewById(R.id.flipButton)
        flashBtn = findViewById(R.id.flashButton)
        galleryButton = findViewById(R.id.galleryButton)
        previewContainer = findViewById(R.id.previewContainer)
        focusIndicator = findViewById(R.id.focusIndicator)
        exposureControl = findViewById(R.id.exposureControl)
        introScreen = findViewById(R.id.introScreen)
        introBrand = findViewById(R.id.introBrand)
        introMotion = findViewById(R.id.introMotion)
        introCredit = findViewById(R.id.introCredit)
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.topBar)) { v, insets ->
            val sysBar = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, sysBar.top + 8, v.paddingRight, v.paddingBottom)
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.bottomBar)) { v, insets ->
            val sysBar = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, sysBar.bottom + 16)
            insets
        }

        settings = SettingsRepository(this)
        saver = MediaStorePhotoSaver(this)
        locationProvider = LocationProvider(this)
        compassHeadingProvider = CompassHeadingProvider(this) { heading ->
            currentHeadingDegrees = heading
            runOnUiThread { updateOnScreenOverlay() }
        }
        tileLoader = TileLoader(this)
        geocoder = GeocoderHelper(this)
        executor = ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            ArrayBlockingQueue(MAX_PENDING_PHOTO_JOBS),
            { runnable -> Thread(runnable, "photo-processing").also { it.isDaemon = true } },
            ThreadPoolExecutor.AbortPolicy()
        )

        cameraController = LifecycleCameraController(this)
        cameraController.imageCaptureFlashMode = ImageCapture.FLASH_MODE_OFF
        cameraController.bindToLifecycle(this)
        cameraController.isTapToFocusEnabled = true
        cameraController.isPinchToZoomEnabled = true
        previewView.controller = cameraController
        previewContainer.onPreviewTap = { x, y ->
            val exposureBounds = android.graphics.Rect()
            exposureControl.getHitRect(exposureBounds)
            if (!exposureBounds.contains(x.toInt(), y.toInt())) focusIndicator.showAt(x, y)
        }
        exposureControl.onExposureChanged = { index ->
            cameraController.cameraControl?.setExposureCompensationIndex(index)
        }
        locationProvider.onStatusChanged = { status ->
            runOnUiThread { updateOnScreenOverlay() }
            if (status == LocationProvider.Status.LOCKED) {
                locationProvider.currentSnapshot?.let { snap ->
                    geocoder.geocode(snap.latitude, snap.longitude) { 
                        runOnUiThread { updateOnScreenOverlay() }
                    }
                }
            }
        }
        
        locationProvider.onLocationUpdated = { snap ->
            runOnUiThread {
                if (snap.isValid) {
                    geocoder.geocode(snap.latitude, snap.longitude) {
                        runOnUiThread { updateOnScreenOverlay() }
                    }
                }
                updateOnScreenOverlay()
            }
        }

        captureBtn.setOnClickListener { takePhoto() }
        settingsBtn.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        reloadLocationBtn.setOnClickListener {
            when {
                !settings.gpsEnabled -> Toast.makeText(this, R.string.settings_gps_disabled, Toast.LENGTH_SHORT).show()
                !hasLocationPermission() -> checkLocationPermission()
                else -> {
                    locationProvider.refresh()
                    overlayPreview.updateData(null, null, null, settings, currentLiveRotation, currentHeadingDegrees)
                    Toast.makeText(this, R.string.location_refreshing, Toast.LENGTH_SHORT).show()
                }
            }
        }
        mapZoomBtn.setOnClickListener {
            settings.miniMapZoomLevel = settings.miniMapZoomLevel % 3 + 1
            updateMapZoomButton()
            updateOnScreenOverlay()
        }
        flipBtn.setOnClickListener {
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
            startCamera()
        }
        flashBtn.setOnClickListener {
            if (cameraController.cameraInfo?.hasFlashUnit() != true || lensFacing != CameraSelector.LENS_FACING_BACK) {
                Toast.makeText(this, R.string.flash_unavailable, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            flashEnabled = !flashEnabled
            flashBtn.setImageResource(if (flashEnabled) R.drawable.ic_flash_auto else R.drawable.ic_flash_off)
            flashBtn.contentDescription = getString(if (flashEnabled) R.string.flash_on else R.string.flash_off)
            flashBtn.alpha = 1f
        }
        
        galleryButton.setOnClickListener {
            val uri = lastSavedPhotoUri ?: run { loadLatestGalleryImageUri(); lastSavedPhotoUri }
            if (uri != null) startActivity(Intent(this, GalleryPreviewActivity::class.java).apply {
                putExtra("photo_uri", uri.toString())
            })
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
            checkLocationPermission()
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
        
        loadLatestGalleryImage()
        // Flash default: OFF (icon shows off)
        flashBtn.setImageResource(R.drawable.ic_flash_off)
        flashBtn.alpha = 1f
        updateMapZoomButton()
        setupOrientationListener()
        showIntroScreen()
        mainHandler.postDelayed(refreshRunnable, settings.overlayRefreshMs.toLong())
    }

    private fun showIntroScreen() {
        ViewCompat.setOnApplyWindowInsetsListener(introScreen) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            introCredit.setPadding(
                introCredit.paddingLeft,
                introCredit.paddingTop,
                introCredit.paddingRight,
                dp(16) + bars.bottom
            )
            insets
        }
        introMotion.startMotion()
        mainHandler.postDelayed({
            if (isFinishing || isDestroyed || introScreen.visibility != View.VISIBLE) return@postDelayed
            val brandScale = ObjectAnimator.ofFloat(introBrand, View.SCALE_X, 1f, 0.78f)
            val brandScaleY = ObjectAnimator.ofFloat(introBrand, View.SCALE_Y, 1f, 0.78f)
            val brandFade = ObjectAnimator.ofFloat(introBrand, View.ALPHA, 1f, 0f)
            val creditFade = ObjectAnimator.ofFloat(introCredit, View.ALPHA, introCredit.alpha, 0f)
            val overlayFade = ObjectAnimator.ofFloat(introScreen, View.ALPHA, 1f, 0f)
            AnimatorSet().apply {
                playTogether(brandScale, brandScaleY, brandFade, creditFade, overlayFade)
                duration = 380L
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        introScreen.visibility = View.GONE
                        introMotion.stopMotion()
                    }
                })
                start()
            }
        }, 900L)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    
    /**
     * Maps a raw sensor orientation (0..359) onto the rotation used by the on-screen
     * controls and by saved photos.
     *
     * The rotation only changes once the device has travelled a full quarter turn away
     * from the rotation currently in effect, in either direction. So while the app sits
     * at 90 it stays at 90 through 80, 45 and 10, and only becomes upright when the
     * device reaches 0 (or carries on past it). The same rule applies rotating the other
     * way, which is why a tilt of 50 or 60 degrees leaves everything upright instead of
     * flipping it to landscape.
     *
     * The wide dead band around [current] also means a hand held steady at a quarter
     * turn cannot flicker, since small sensor wobble never travels far enough.
     */
    private fun snapOrientation(orientation: Int, current: Int): Int {
        val travel = ((orientation - current + 180) % 360 + 360) % 360 - 180
        return when {
            travel >= 90 -> (current + 90) % 360
            travel <= -90 -> (current - 90 + 360) % 360
            else -> current
        }
    }

    private fun setupOrientationListener() {
        orientationEventListener = object : OrientationEventListener(this) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return

                val rot = snapOrientation(orientation, currentLiveRotation)
                
                if (currentLiveRotation != rot) {
                    currentLiveRotation = rot
                    val sensorRotation = when (rot) {
                        90 -> Surface.ROTATION_90
                        180 -> Surface.ROTATION_180
                        270 -> Surface.ROTATION_270
                        else -> Surface.ROTATION_0
                    }
                    compassHeadingProvider.updateDisplayRotation(sensorRotation)
                    val degrees = when(rot) {
                        90 -> -90f
                        180 -> -180f
                        270 -> 90f
                        else -> 0f
                    }
                    
                    val viewsToRotate = listOf(settingsBtn, reloadLocationBtn, mapZoomBtn, flashBtn, flipBtn, captureBtn, galleryButton)
                    for (v in viewsToRotate) {
                        v.animate().rotation(degrees).setDuration(250).start()
                    }
                    positionExposureControl(rot, degrees)
                    updateOnScreenOverlay()
                }
            }
        }
        orientationEventListener?.enable()
    }

    private val refreshRunnable = object : Runnable {
        override fun run() {
            updateOnScreenOverlay()
            updateExposureControl()
            updateFlashAvailability(cameraController.cameraInfo?.hasFlashUnit() == true)
            mainHandler.postDelayed(this, settings.overlayRefreshMs.toLong())
        }
    }

    private fun checkLocationPermission() {
        if (hasLocationPermission()) {
            startLocation()
        } else if (settings.gpsEnabled) {
            requestLocationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            takePhoto()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun loadLatestGalleryImageUri(): android.net.Uri? {
        try {
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL) else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(MediaStore.Images.Media._ID)
            val selection = "${MediaStore.Images.Media.RELATIVE_PATH} = ?"
            val selectionArgs = arrayOf("${android.os.Environment.DIRECTORY_PICTURES}/GeoCam/")
            val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC, ${MediaStore.Images.Media.DATE_ADDED} DESC, ${MediaStore.Images.Media._ID} DESC"
            contentResolver.query(collection, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                    val uri = ContentUris.withAppendedId(collection, id)
                    lastSavedPhotoUri = uri
                    galleryButton.setImageURI(uri)
                    return uri
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load gallery URI", e)
            return null
        }
        lastSavedPhotoUri = null
        galleryButton.setImageDrawable(null)
        return null
    }

    private fun loadLatestGalleryImage() {
        loadLatestGalleryImageUri()
    }

    private fun updateOnScreenOverlay() {
        if (!settings.gpsEnabled || locationProvider.currentSnapshot == null) {
            overlayPreview.updateData(null, null, null, settings, currentLiveRotation, currentHeadingDegrees)
            return
        }
        
        val snap = locationProvider.currentSnapshot?.takeIf { it.isValid }
        if (snap == null) {
            overlayPreview.updateData(null, null, null, settings, currentLiveRotation, currentHeadingDegrees)
            return
        }
        val place = geocoder.getOrNull(snap.latitude, snap.longitude)
        
        // Ask for the tile asynchronously, this returns immediately if it's not cached,
        // and callbacks when loaded!
        val cachedTile = tileLoader.getTileAsync(
            snap.latitude,
            snap.longitude,
            zoom = settings.miniMapZoom,
            satellite = settings.satelliteMapEnabled
        ) { newTile ->
            // Network fetch finished
            overlayPreview.updateData(
                locationProvider.currentSnapshot, 
                geocoder.getOrNull(snap.latitude, snap.longitude), 
                newTile, 
                settings, 
                currentLiveRotation,
                currentHeadingDegrees
            )
        }
        
        // Update immediately with whatever we have right now
        overlayPreview.updateData(snap, place, cachedTile, settings, currentLiveRotation, currentHeadingDegrees)
    }

    private fun hasLocationPermission() = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun startCamera() {
        disableTorchForCapture()
        cameraController.cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        cameraController.imageCaptureFlashMode = ImageCapture.FLASH_MODE_OFF
        settings.saveMirroredSelfie = (lensFacing == CameraSelector.LENS_FACING_FRONT)
        previewView.post {
            updateExposureControl()
            updateFlashAvailability(cameraController.cameraInfo?.hasFlashUnit() == true)
        }
    }

    private fun updateFlashAvailability(hasFlash: Boolean) {
        val available = hasFlash && lensFacing == CameraSelector.LENS_FACING_BACK
        flashBtn.isEnabled = available
        flashBtn.alpha = if (available) 1f else 0.45f
        if (!available && flashEnabled) {
            flashEnabled = false
            flashBtn.setImageResource(R.drawable.ic_flash_off)
        }
    }

    private fun updateExposureControl() {
        val exposureState = cameraController.cameraInfo?.exposureState
        val range = exposureState?.exposureCompensationRange
        if (exposureState == null || range == null) {
            exposureControl.setRange(0, 0, 0, 0f)
            return
        }
        val step = exposureState.exposureCompensationStep
        val stepEv = if (step.denominator == 0) 0f else step.numerator.toFloat() / step.denominator
        exposureControl.setRange(
            range.lower,
            range.upper,
            exposureState.exposureCompensationIndex,
            stepEv
        )
    }

    private fun updateMapZoomButton() {
        mapZoomBtn.contentDescription = getString(R.string.map_zoom_level, settings.miniMapZoomLevel)
    }

    private fun startLocation() {
        if (settings.gpsEnabled && hasLocationPermission()) locationProvider.start() else locationProvider.stop()
    }

    private fun positionExposureControl(rotation: Int, degrees: Float) {
        exposureControl.post {
            val parent = exposureControl.parent as? View ?: return@post
            val width = exposureControl.width
            val height = exposureControl.height
            if (width == 0 || height == 0 || parent.width == 0 || parent.height == 0) return@post

            val margin = dp(12)
            val centerX: Float
            val centerY: Float
            val rotatedHalfWidth: Float
            val rotatedHalfHeight: Float
            when (rotation) {
                90 -> {
                    rotatedHalfWidth = height / 2f
                    rotatedHalfHeight = width / 2f
                    centerX = parent.width / 2f
                    centerY = rotatedHalfHeight + margin
                }
                180 -> {
                    rotatedHalfWidth = width / 2f
                    rotatedHalfHeight = height / 2f
                    centerX = width / 2f + margin
                    centerY = parent.height / 2f
                }
                270 -> {
                    rotatedHalfWidth = height / 2f
                    rotatedHalfHeight = width / 2f
                    centerX = parent.width / 2f
                    centerY = parent.height - rotatedHalfHeight - margin
                }
                else -> {
                    rotatedHalfWidth = width / 2f
                    rotatedHalfHeight = height / 2f
                    centerX = parent.width - rotatedHalfWidth - margin
                    centerY = parent.height / 2f
                }
            }

            exposureControl.animate().cancel()
            exposureControl.translationX = 0f
            exposureControl.translationY = 0f
            val params = exposureControl.layoutParams as android.widget.FrameLayout.LayoutParams
            params.gravity = android.view.Gravity.TOP or android.view.Gravity.LEFT
            params.leftMargin = (centerX - width / 2f).toInt()
            params.topMargin = (centerY - height / 2f).toInt()
            exposureControl.layoutParams = params
            exposureControl.rotation = degrees
        }
    }

    private fun takePhoto() {
        if (isCapturing) return
        isCapturing = true
        captureBtn.alpha = 0.5f
        captureDimOverlay.animate().cancel()
        captureDimOverlay.alpha = 0f
        captureDimOverlay.visibility = View.VISIBLE
        captureDimOverlay.animate()
            .alpha(CAPTURE_DIM_ALPHA)
            .setDuration(CAPTURE_DIM_IN_MS)
            .start()

        val captureTimeMs = System.currentTimeMillis()
        val captureHeading = currentHeadingDegrees
        val captureSnapshot = if (settings.gpsEnabled && !settings.saveWithoutLocation) {
            locationProvider.currentSnapshot?.takeIf { it.isValid }
        } else {
            null
        }
        if (flashEnabled) {
            val cameraControl = cameraController.cameraControl
            if (cameraControl == null || cameraController.cameraInfo?.hasFlashUnit() != true) {
                Toast.makeText(this, R.string.flash_unavailable, Toast.LENGTH_SHORT).show()
                capturePhoto(captureTimeMs, captureSnapshot, captureHeading)
                return
            }
            try {
                val torchFuture = cameraControl.enableTorch(true)
                torchFuture.addListener({
                    val enabled = runCatching { torchFuture.get(); true }.getOrDefault(false)
                    if (enabled) {
                        torchCameraControl = cameraControl
                        torchEnabledForCapture = true
                        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                            disableTorchForCapture()
                            onCaptureFinished()
                            return@addListener
                        }
                        queueCapture(captureTimeMs, captureSnapshot, captureHeading, TORCH_WARMUP_MS)
                    } else {
                        Toast.makeText(this, R.string.flash_unavailable, Toast.LENGTH_SHORT).show()
                        capturePhoto(captureTimeMs, captureSnapshot, captureHeading)
                    }
                }, ContextCompat.getMainExecutor(this))
            } catch (e: Exception) {
                Log.e(TAG, "Could not enable capture torch", e)
                capturePhoto(captureTimeMs, captureSnapshot, captureHeading)
            }
        } else {
            capturePhoto(captureTimeMs, captureSnapshot, captureHeading)
        }
    }

    private fun queueCapture(
        captureTimeMs: Long,
        captureSnapshot: GpsSnapshot?,
        captureHeading: Float?,
        delayMs: Long
    ) {
        pendingTorchCapture?.let(mainHandler::removeCallbacks)
        pendingTorchCapture = Runnable {
            pendingTorchCapture = null
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                capturePhoto(captureTimeMs, captureSnapshot, captureHeading)
            } else {
                disableTorchForCapture()
                onCaptureFinished()
            }
        }.also { mainHandler.postDelayed(it, delayMs) }
    }

    private fun capturePhoto(captureTimeMs: Long, captureSnapshot: GpsSnapshot?, captureHeading: Float?) {
        val tempFile = File(externalCacheDir ?: cacheDir, "GC_%015d.jpg".format(captureTimeMs))
        try {
            val options = ImageCapture.OutputFileOptions.Builder(tempFile).build()
            cameraController.takePicture(options, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(results: ImageCapture.OutputFileResults) {
                disableTorchForCapture()
                onCaptureFinished()
                try {
                    executor.execute {
                        try {
                            processAndSave(tempFile, captureTimeMs, captureSnapshot, captureHeading)
                        } catch (e: OutOfMemoryError) {
                            Log.e(TAG, "Insufficient memory processing capture; saving original photo", e)
                            runCatching {
                                val snapshot = captureSnapshot?.takeIf {
                                    settings.gpsEnabled && !settings.saveWithoutLocation && it.isValid
                                }
                                ExifWriter.writeExifToFile(tempFile.absolutePath, snapshot, captureTimeMs)
                                saver.save(tempFile, "GC_%015d.jpg".format(captureTimeMs))?.let { uri ->
                                    lastSavedPhotoUri = uri
                                    runOnUiThread { galleryButton.setImageURI(uri) }
                                }
                            }.onFailure { Log.e(TAG, "Could not save original capture after memory pressure", it) }
                        } catch (e: Exception) {
                            Log.e(TAG, "Capture processing failed", e)
                        } finally {
                            tempFile.delete()
                        }
                    }
                } catch (e: java.util.concurrent.RejectedExecutionException) {
                    Log.e(TAG, "Capture processing executor is unavailable", e)
                    tempFile.delete()
                    Toast.makeText(this@MainActivity, R.string.capture_processing_busy, Toast.LENGTH_SHORT).show()
                }
            }
            override fun onError(exception: ImageCaptureException) {
                Log.e(TAG, "Camera capture failed", exception)
                disableTorchForCapture()
                tempFile.delete()
                onCaptureFinished()
                runOnUiThread { Toast.makeText(this@MainActivity, R.string.capture_failed, Toast.LENGTH_SHORT).show() }
            }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Could not start camera capture", e)
            disableTorchForCapture()
            tempFile.delete()
            onCaptureFinished()
            runOnUiThread { Toast.makeText(this, R.string.capture_failed, Toast.LENGTH_SHORT).show() }
        }
    }

    private fun disableTorchForCapture() {
        val requestedControl = torchCameraControl
        val shouldDisableTorch = torchEnabledForCapture || requestedControl != null
        torchEnabledForCapture = false
        torchCameraControl = null
        if (!shouldDisableTorch) return

        mainHandler.post {
            val cameraControl = requestedControl ?: cameraController.cameraControl
            val offFuture = runCatching { cameraControl?.enableTorch(false) }
                .onFailure { Log.w(TAG, "Could not request capture torch shutdown", it) }
                .getOrNull() ?: return@post
            offFuture.addListener({
                runCatching { offFuture.get() }
                    .onFailure { Log.w(TAG, "Camera did not switch off capture torch", it) }
            }, ContextCompat.getMainExecutor(this))
        }
    }

    private fun onCaptureFinished() {
        runOnUiThread {
            isCapturing = false
            captureBtn.alpha = 1f
            captureDimOverlay.animate()
                .alpha(0f)
                .setDuration(CAPTURE_DIM_OUT_MS)
                .withEndAction { captureDimOverlay.visibility = View.GONE }
                .start()
        }
    }

    private fun processAndSave(source: File, captureTimeMs: Long, captureSnapshot: GpsSnapshot?, captureHeading: Float?) {
        val stampLocation = settings.gpsEnabled && !settings.saveWithoutLocation
        val snapshot = if (stampLocation) captureSnapshot?.takeIf { it.isValid } else null
        var outputFile = source
        val name = if (stampLocation) source.name else source.name.replace(".jpg", "_noloc.jpg")

        if (stampLocation) {
            val renderedFile = File(cacheDir, "${source.nameWithoutExtension}_overlay.jpg")
            if (renderOverlay(source, renderedFile, snapshot, captureHeading)) {
                outputFile = renderedFile
            } else {
                renderedFile.delete()
            }
        }

        try {
            ExifWriter.writeExifToFile(outputFile.absolutePath, if (stampLocation) snapshot else null, captureTimeMs)
            val saved = saver.save(outputFile, name)
            saved?.let { uri ->
                lastSavedPhotoUri = uri
                runOnUiThread {
                    galleryButton.setImageURI(null)
                    galleryButton.setImageURI(uri)
                }
            }
        } finally {
            if (outputFile != source) outputFile.delete()
        }
    }

    private fun renderOverlay(
        source: File,
        output: File,
        snapshot: GpsSnapshot?,
        headingDegrees: Float?
    ): Boolean {
        var bitmap = BitmapProcessor.decodeUpright(source) ?: return false
        return try {
            if (settings.saveMirroredSelfie) {
                val mirrored = BitmapProcessor.mirrorHorizontally(bitmap)
                bitmap.recycle()
                bitmap = mirrored
            }
            val place = snapshot?.let { geocoder.getOrNull(it.latitude, it.longitude) }
            val tile = snapshot?.let {
                tileLoader.getCachedTile(
                    it.latitude,
                    it.longitude,
                    zoom = settings.miniMapZoom,
                    satellite = settings.satelliteMapEnabled
                )
            }

            OverlayRenderer.draw(bitmap, snapshot, place, tile, settings, headingDegrees)
            BitmapProcessor.encodeToFile(bitmap, output, settings.jpegQuality, settings.maxLongEdge)
            true
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "Insufficient memory rendering photo overlay; preserving original", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to render overlay", e)
            false
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    override fun onResume() {
        super.onResume()
        settings = SettingsRepository(this)
        loadLatestGalleryImageUri()
        compassHeadingProvider.start(windowManager.defaultDisplay.rotation)
        startLocation()
        updateOnScreenOverlay()
        updateExposureControl()
        orientationEventListener?.enable()
        mainHandler.removeCallbacks(refreshRunnable)
        mainHandler.postDelayed(refreshRunnable, settings.overlayRefreshMs.toLong())
    }

    override fun onPause() {
        mainHandler.removeCallbacks(refreshRunnable)
        compassHeadingProvider.stop()
        pendingTorchCapture?.let {
            mainHandler.removeCallbacks(it)
            pendingTorchCapture = null
            onCaptureFinished()
        }
        disableTorchForCapture()
        super.onPause()
        locationProvider.stop()
        orientationEventListener?.disable()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(refreshRunnable)
        disableTorchForCapture()
        super.onDestroy()
        locationProvider.stop()
        tileLoader.shutdown()
        executor.shutdown()
        geocoder.shutdown()
        orientationEventListener?.disable()
    }

    private companion object {
        const val TAG = "GeoCam"
        const val TORCH_WARMUP_MS = 120L
        const val MAX_PENDING_PHOTO_JOBS = 2
        const val CAPTURE_DIM_ALPHA = 0.18f
        const val CAPTURE_DIM_IN_MS = 70L
        const val CAPTURE_DIM_OUT_MS = 180L
    }
}
