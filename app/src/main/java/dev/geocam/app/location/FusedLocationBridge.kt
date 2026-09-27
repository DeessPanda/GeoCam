package dev.geocam.app.location

import android.content.Context
import android.location.Location

/**
 * Google Play fused location, isolated behind an interface.
 *
 * The library is proprietary, so it only exists in the google flavor. The FLOSS
 * build supplies a no-op implementation and falls back to the platform
 * [android.location.LocationManager], which is part of AOSP and fully free.
 */
interface FusedLocationBridge {

    /**
     * Begins location updates.
     *
     * @param skipCached when true, do not deliver the provider's cached fix, which
     *   is what a manual "refresh location" needs so a stale position is not reused.
     * @param onLocation called for each new fix. May be called on any thread.
     */
    fun start(skipCached: Boolean, onLocation: (Location) -> Unit)

    /** Stops updates. Safe to call when not started. */
    fun stop()
}

/**
 * Builds the flavor-appropriate bridge. One of these exists per product flavor:
 * `PlayServicesFusedBridge` for google, `NoFusedLocationBridge` for floss.
 */
interface FusedLocationBridgeFactory {
    fun create(context: Context): FusedLocationBridge
}
