package dev.geocam.app.location

import android.content.Context
import android.location.Location

/**
 * FLOSS-flavor bridge. This build deliberately contains no proprietary libraries,
 * so there is no fused provider to talk to. It does nothing and GeoCam relies on
 * the platform LocationManager, which is part of AOSP and needs no extra dependency.
 */
class NoFusedLocationBridge : FusedLocationBridge {
    override fun start(skipCached: Boolean, onLocation: (Location) -> Unit) = Unit
    override fun stop() = Unit
}

object DefaultFusedLocationBridgeFactory : FusedLocationBridgeFactory {
    override fun create(context: Context): FusedLocationBridge = NoFusedLocationBridge()
}
