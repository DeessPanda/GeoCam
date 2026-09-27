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
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

class LocationProvider(private val context: Context) {

    enum class Status { OFF, SEARCHING, LOCKED }

    private val lm: LocationManager? = context.getSystemService()
    private val fusedClient by lazy { LocationServices.getFusedLocationProviderClient(context.applicationContext) }

    var status: Status = Status.OFF
        private set

    var onStatusChanged: ((Status) -> Unit)? = null
    var onLocationUpdated: ((GpsSnapshot) -> Unit)? = null

    var currentSnapshot: GpsSnapshot? = null
        private set

    private var isRunning = false
    private var fusedUpdatesRequested = false
    private var bypassCachedLocationOnce = false

    private val fusedCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach { location ->
                val current = currentSnapshot
                if (current == null || !current.isValid || location.time >= current.fixTimeMs || location.accuracy < current.accuracy) {
                    updateSnapshot(location)
                }
            }
        }
    }

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

    @SuppressLint("MissingPermission")
    fun start() {
        if (isRunning) return
        val manager = lm ?: return

        isRunning = true
        setStatus(Status.SEARCHING)
        val skipCachedLocation = bypassCachedLocationOnce
        bypassCachedLocationOnce = false

        try {
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, MIN_INTERVAL_MS)
                .setMinUpdateIntervalMillis(MIN_FASTEST_INTERVAL_MS)
                .setMaxUpdateDelayMillis(MAX_BATCH_DELAY_MS)
                .setMinUpdateDistanceMeters(MIN_DISTANCE_M)
                .setWaitForAccurateLocation(true)
                .build()

            if (!skipCachedLocation) {
                fusedClient.lastLocation
                    .addOnSuccessListener { location ->
                        if (isRunning && location != null) updateSnapshot(location)
                    }
                    .addOnFailureListener { error -> Log.w(TAG, "Fused last location unavailable", error) }
            }

            fusedClient.requestLocationUpdates(request, fusedCallback, Looper.getMainLooper())
                .addOnSuccessListener {
                    if (isRunning) {
                        fusedUpdatesRequested = true
                    } else {
                        fusedClient.removeLocationUpdates(fusedCallback)
                    }
                }
                .addOnFailureListener { error -> Log.w(TAG, "Fused updates unavailable; using platform providers", error) }
        } catch (e: Exception) {
            Log.w(TAG, "Fused location unavailable; using platform providers", e)
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
        if (fusedUpdatesRequested) {
            try {
                fusedClient.removeLocationUpdates(fusedCallback)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to stop fused location updates", e)
            }
            fusedUpdatesRequested = false
        }
        try {
            lm?.removeUpdates(gpsListener)
            lm?.removeUpdates(networkListener)
        } catch (e: Exception) {}
        setStatus(Status.OFF)
    }

    @SuppressLint("MissingPermission")
    private fun lastKnown(provider: String): Location? = try {
        lm?.getLastKnownLocation(provider)
    } catch (e: SecurityException) { null }

    private fun updateSnapshot(location: Location) {
        val mock = location.isMock
        val snap = GpsSnapshot(
            latitude = location.latitude,
            longitude = location.longitude,
            altitude = if (location.hasAltitude()) location.altitude else null,
            accuracy = location.accuracy,
            bearing = if (location.hasBearing()) location.bearing else null,
            speed = if (location.hasSpeed()) location.speed else null,
            fixTimeMs = location.time,
            isMock = mock
        )
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
