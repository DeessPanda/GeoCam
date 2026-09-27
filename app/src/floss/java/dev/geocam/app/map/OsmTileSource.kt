package dev.geocam.app.map

import dev.geocam.app.AppInfo

/**
 * FLOSS-flavor tile source using OpenStreetMap's standard tile layer, which is
 * free, open data.
 *
 * Two constraints from the OSM tile usage policy are respected here: an
 * identifying User-Agent is always sent, and the server is only asked for the
 * single tile a photo needs rather than pre-fetched in bulk. The service publishes
 * tiles up to zoom 19, which covers every mini-map zoom level this app offers
 * (15, 17 and 18).
 */
class OsmTileSource : TileSource {

    /** OSM has no free satellite layer, so the FLOSS build is always street style. */
    override val supportsSatellite = false
    override val userAgent = AppInfo.contactUserAgent
    override val maxZoom = 19

    override fun urlFor(zoom: Int, x: Int, y: Int, satellite: Boolean): String =
        "https://tile.openstreetmap.org/$zoom/$x/$y.png"
}
