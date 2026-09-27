package dev.geocam.app.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.core.content.getSystemService

class LocationProvider(private val context: Context) {

    enum class Status { OFF, SEARCHING, LOCKED }

    private val lm: LocationManager? = context.getSystemService()

    // Provided by the product flavor: Play fused location for google, a no-op for floss.
    private val fused: FusedLocationBridge = DefaultFusedLocationBridgeFactory.create(context)

    var status: Status = Status.OFF
        private set

    var onStatusChanged: ((Status) -> Unit)? = null
    var onLocationUpdated: ((GpsSnapshot) -> Unit)? = null

    var currentSnapshot: GpsSnapshot? = null
        private set

    private var isRunning = false
    private var bypassCachedLocationOnce = false

    private val gpsListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            updateSnapshot(location)
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

        override fun onProviderEnabled(provider: String) {
            if (currentSnapshot == null) setStatus(Status.SEARCHING)
        }

        override fun onProviderDisabled(provider: String) {
            if (provider == LocationManager.GPS_PROVIDER) setStatus(Status.SEARCHING)
        }
    }

    private val networkListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val current = currentSnapshot
            if (current == null || !current.isValid || location.accuracy < current.accuracy) {
                updateSnapshot(location)
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    /**
     * Begins location updates.
     *
     * Called from the main thread, and the platform has been observed to throw
     * unexpected exceptions from several of the calls below on some OEM builds. A
     * failure to acquire location must never take the app down, so anything that
     * escapes the individual guards is caught here and the app simply keeps
     * whatever it managed to register.
     */
    @SuppressLint("MissingPermission")
    fun start() {
        if (isRunning) return
        val manager = lm ?: return
        try {
            startUpdates(manager)
        } catch (e: Exception) {
            Log.e(TAG, "Location updates could not be started; continuing without a fix", e)
            setStatus(if (currentSnapshot?.isValid == true) Status.LOCKED else Status.SEARCHING)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startUpdates(manager: LocationManager) {
        isRunning = true
        setStatus(Status.SEARCHING)
        val skipCachedLocation = bypassCachedLocationOnce
        bypassCachedLocationOnce = false

        fused.start(skipCachedLocation) { location ->
            val current = currentSnapshot
            if (current == null || !current.isValid ||
                location.time >= current.fixTimeMs || location.accuracy < current.accuracy
            ) {
                updateSnapshot(location)
            }
        }

        val lastGps = if (skipCachedLocation) null else lastKnown(LocationManager.GPS_PROVIDER)
        val lastNetwork = if (skipCachedLocation) null else lastKnown(LocationManager.NETWORK_PROVIDER)
        val best = when {
            lastGps != null && lastNetwork != null ->
                if (lastGps.accuracy <= lastNetwork.accuracy) lastGps else lastNetwork
            lastGps != null -> lastGps
            lastNetwork != null -> lastNetwork
            else -> null
        }
        best?.let {
            if (System.currentTimeMillis() - it.time in 0L..60_000L) {
                updateSnapshot(it)
            }
        }

        if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            try {
                manager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    MIN_INTERVAL_MS,
                    MIN_DISTANCE_M,
                    gpsListener,
                    Looper.getMainLooper()
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register GPS listener", e)
            }
        }

        if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            try {
                manager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    MIN_INTERVAL_MS,
                    MIN_DISTANCE_M,
                    networkListener,
                    Looper.getMainLooper()
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to register network location listener", e)
            }
        }
    }

    fun refresh() {
        stop()
        currentSnapshot = null
        bypassCachedLocationOnce = true
        start()
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        fused.stop()
        try {
            lm?.removeUpdates(gpsListener)
            lm?.removeUpdates(networkListener)
        } catch (e: Exception) {}
        setStatus(Status.OFF)
    }

    /**
     * Last cached fix for a provider, or null if unavailable.
     *
     * [LocationManager.getLastKnownLocation] is only documented to throw
     * [SecurityException], but it is also documented to throw
     * [IllegalArgumentException] for an unknown provider, and some OEM ROMs throw
     * further exceptions while the provider is still transitioning after the user
     * flips the system location switch. A cached fix is only an optimisation, so
     * every failure here is downgraded to "no cached fix" instead of being allowed
     * to propagate and take the app down.
     */
    @SuppressLint("MissingPermission")
    private fun lastKnown(provider: String): Location? = try {
        lm?.getLastKnownLocation(provider)
    } catch (e: Exception) {
        Log.w(TAG, "No cached fix available from $provider", e)
        null
    }

    private fun updateSnapshot(location: Location) {
        val snap = try {
            val mock = location.isMock
            GpsSnapshot(
                latitude = location.latitude,
                longitude = location.longitude,
                altitude = if (location.hasAltitude()) location.altitude else null,
                accuracy = location.accuracy,
                bearing = if (location.hasBearing()) location.bearing else null,
                speed = if (location.hasSpeed()) location.speed else null,
                fixTimeMs = location.time,
                isMock = mock
            )
        } catch (e: Exception) {
            // A malformed Location from the platform must be ignored, not fatal.
            Log.w(TAG, "Discarding an unreadable location fix", e)
            return
        }
        currentSnapshot = snap
        onLocationUpdated?.invoke(snap)

        setStatus(if (snap.isValid) Status.LOCKED else Status.SEARCHING)
    }

    private fun setStatus(s: Status) {
        if (status != s) {
            status = s
            onStatusChanged?.invoke(s)
        }
    }

    companion object {
        private const val TAG = "LocationProvider"
        private const val MIN_INTERVAL_MS = 2_000L
        private const val MIN_FASTEST_INTERVAL_MS = 1_000L
        private const val MAX_BATCH_DELAY_MS = 3_000L
        private const val MIN_DISTANCE_M = 1f  // 1 meter, so it updates coordinates when walking!
    }
}
