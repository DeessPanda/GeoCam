package dev.geocam.app.map

import dev.geocam.app.AppInfo

/**
 * Google-flavor tile source. Uses the legacy Google Maps web tile endpoint, which
 * is undocumented and outside Google's published APIs, so treat it as the fragile
 * part of this build and be ready to fall back to OpenStreetMap if it stops
 * answering.
 */
class GoogleTileSource : TileSource {
    override val supportsSatellite = true
    override val userAgent = AppInfo.contactUserAgent
    override val maxZoom = 21

    override fun urlFor(zoom: Int, x: Int, y: Int, satellite: Boolean): String {
        val layer = if (satellite) "s" else "m"
        return "https://mt1.google.com/vt/lyrs=$layer&x=$x&y=$y&z=$zoom"
    }
}
