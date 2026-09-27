package dev.geocam.app.map

/**
 * Where mini-map tiles are fetched from.
 *
 * Implemented once per product flavor so the FLOSS build can use OpenStreetMap
 * while the Google build keeps its existing tile endpoint. Only one implementation
 * is ever compiled into a given APK.
 */
interface TileSource {

    /**
     * Whether this source can serve aerial imagery. OpenStreetMap has no free
     * satellite layer, so the FLOSS build reports false and callers fall back to
     * the standard street style.
     */
    val supportsSatellite: Boolean

    /** Identifying User-Agent, as required by the tile source's usage policy. */
    val userAgent: String

    /**
     * Highest zoom level this source actually has tiles for. Requests above this
     * would return nothing, so callers clamp to it.
     */
    val maxZoom: Int

    /** URL of a single 256x256 tile. [x] and [y] are expected to already be wrapped to range. */
    fun urlFor(zoom: Int, x: Int, y: Int, satellite: Boolean): String
}
