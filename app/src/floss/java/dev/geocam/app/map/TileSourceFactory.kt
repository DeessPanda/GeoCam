package dev.geocam.app.map

object TileSourceFactory {
    fun create(): TileSource = OsmTileSource()
}
