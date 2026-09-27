package dev.geocam.app.location

import android.content.Context
import android.location.Location
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/** Google-flavor bridge backed by the Play Services fused location provider. */
class PlayServicesFusedBridge(context: Context) : FusedLocationBridge {

    private val appContext = context.applicationContext
    private val client by lazy { LocationServices.getFusedLocationProviderClient(appContext) }

    private var updatesRequested = false
    private var sink: ((Location) -> Unit)? = null

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val deliver = sink ?: return
            result.locations.forEach(deliver)
        }
    }

    override fun start(skipCached: Boolean, onLocation: (Location) -> Unit) {
        sink = onLocation
        try {
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, MIN_INTERVAL_MS)
                .setMinUpdateIntervalMillis(MIN_FASTEST_INTERVAL_MS)
                .setMaxUpdateDelayMillis(MAX_BATCH_DELAY_MS)
                .setMinUpdateDistanceMeters(MIN_DISTANCE_M)
                .setWaitForAccurateLocation(true)
                .build()

            if (!skipCached) {
                client.lastLocation
                    .addOnSuccessListener { location -> if (location != null) onLocation(location) }
                    .addOnFailureListener { error -> Log.w(TAG, "Fused last location unavailable", error) }
            }

            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
                .addOnSuccessListener { updatesRequested = true }
                .addOnFailureListener { error ->
                    Log.w(TAG, "Fused updates unavailable; using platform providers", error)
                }
        } catch (e: Exception) {
            Log.w(TAG, "Fused location unavailable; using platform providers", e)
        }
    }

    override fun stop() {
        if (updatesRequested) {
            try {
                client.removeLocationUpdates(callback)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to stop fused location updates", e)
            }
            updatesRequested = false
        }
        sink = null
    }

    private companion object {
        const val TAG = "FusedLocationBridge"
        const val MIN_INTERVAL_MS = 2_000L
        const val MIN_FASTEST_INTERVAL_MS = 1_000L
        const val MAX_BATCH_DELAY_MS = 3_000L
        const val MIN_DISTANCE_M = 1f
    }
}

object DefaultFusedLocationBridgeFactory : FusedLocationBridgeFactory {
    override fun create(context: Context): FusedLocationBridge = PlayServicesFusedBridge(context)
}
