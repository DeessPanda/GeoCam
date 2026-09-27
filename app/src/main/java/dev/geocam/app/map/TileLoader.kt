package dev.geocam.app.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.LruCache
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.math.*

class TileLoader(context: Context) {
    private val cacheDir = File(context.cacheDir, "map_tiles").also { it.mkdirs() }
    private val executor = ThreadPoolExecutor(
        TILE_WORKERS,
        TILE_WORKERS,
        0L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(MAX_QUEUED_FETCHES),
        { runnable -> Thread(runnable, "map-tile-bg").also { it.isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy()
    )
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var isClosed = false
    private val memoryCache = object : LruCache<String, Bitmap>(8 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.byteCount / 1024).coerceAtLeast(1)
    }
    private val centeredCache = object : LruCache<String, Bitmap>(4 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.byteCount / 1024).coerceAtLeast(1)
    }
    private val pendingFetches = ConcurrentHashMap<String, Boolean>()

    // Provided by the product flavor: Google tiles for google, OpenStreetMap for floss.
    private val source: TileSource = TileSourceFactory.create()

    /** Satellite is silently downgraded to street style when the source has no aerial layer. */
    private fun effectiveSatellite(satellite: Boolean): Boolean =
        satellite && source.supportsSatellite

    private fun getCacheKey(zoom: Int, x: Int, y: Int, satellite: Boolean) = "${if (satellite) 's' else 'r'}_${zoom}_${x}_${y}"

    fun getTileAsync(lat: Double, lon: Double, zoom: Int = DEFAULT_ZOOM, satellite: Boolean = false, onTileLoaded: ((Bitmap?) -> Unit)? = null): Bitmap? {
        if (isClosed) return null
        val satellite = effectiveSatellite(satellite)
        val zoom = zoom.coerceIn(MIN_ZOOM, source.maxZoom)
        val key = centeredKey(lat, lon, zoom, satellite)
        centeredCache.get(key)?.let { return it }
        if (onTileLoaded != null && pendingFetches.putIfAbsent(key, true) == null) {
            try {
                executor.execute {
                    val bitmap = buildCenteredTile(lat, lon, zoom, satellite)
                    if (bitmap != null) centeredCache.put(key, bitmap)
                    pendingFetches.remove(key)
                    if (!isClosed) mainHandler.post { if (!isClosed) onTileLoaded(bitmap) }
                }
            } catch (e: java.util.concurrent.RejectedExecutionException) {
                pendingFetches.remove(key)
                Log.d(TAG, "Dropped map tile request because the bounded queue is full")
            }
        }
        return null
    }

    fun getCachedTile(lat: Double, lon: Double, zoom: Int = DEFAULT_ZOOM, satellite: Boolean = false): Bitmap? =
        centeredCache.get(
            centeredKey(lat, lon, zoom.coerceIn(MIN_ZOOM, source.maxZoom), effectiveSatellite(satellite))
        )?.takeUnless { it.isRecycled }

    fun shutdown() {
        isClosed = true
        executor.shutdownNow()
        pendingFetches.clear()
        mainHandler.removeCallbacksAndMessages(null)
    }

    private fun centeredKey(lat: Double, lon: Double, zoom: Int, satellite: Boolean): String {
        val n = 2.0.pow(zoom)
        val x = ((lon.coerceIn(-180.0, 180.0) + 180.0) / 360.0 * n * TILE_SIZE).toLong()
        val safeLat = lat.coerceIn(-MAX_MERCATOR_LAT, MAX_MERCATOR_LAT)
        val sinLat = kotlin.math.sin(Math.toRadians(safeLat))
        val y = ((0.5 - ln((1.0 + sinLat) / (1.0 - sinLat)) / (4.0 * Math.PI)) * n * TILE_SIZE).toLong()
        return "${if (satellite) 's' else 'r'}_${zoom}_${x}_${y}"
    }

    private fun buildCenteredTile(lat: Double, lon: Double, zoom: Int, satellite: Boolean): Bitmap? {
        val n = 2.0.pow(zoom).toInt()
        val safeLat = lat.coerceIn(-MAX_MERCATOR_LAT, MAX_MERCATOR_LAT)
        val sinLat = kotlin.math.sin(Math.toRadians(safeLat))
        val worldX = (lon.coerceIn(-180.0, 180.0) + 180.0) / 360.0 * n * TILE_SIZE
        val worldY = (0.5 - ln((1.0 + sinLat) / (1.0 - sinLat)) / (4.0 * Math.PI)) * n * TILE_SIZE
        val left = worldX.toInt() - OUTPUT_SIZE / 2
        val top = worldY.toInt() - OUTPUT_SIZE / 2
        val firstTileX = Math.floorDiv(left, TILE_SIZE)
        val firstTileY = Math.floorDiv(top, TILE_SIZE)
        val lastTileX = Math.floorDiv(left + OUTPUT_SIZE - 1, TILE_SIZE)
        val lastTileY = Math.floorDiv(top + OUTPUT_SIZE - 1, TILE_SIZE)
        val output = Bitmap.createBitmap(OUTPUT_SIZE, OUTPUT_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(output)
        canvas.drawColor(OFFLINE_COLOR)
        val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)

        for (tileY in firstTileY..lastTileY) {
            if (tileY !in 0 until n) continue
            for (tileX in firstTileX..lastTileX) {
                val wrappedX = Math.floorMod(tileX, n)
                val tile = loadTile(zoom, wrappedX, tileY, satellite) ?: continue
                val destinationX = tileX * TILE_SIZE - left
                val destinationY = tileY * TILE_SIZE - top
                canvas.drawBitmap(tile, destinationX.toFloat(), destinationY.toFloat(), paint)
            }
        }
        return output
    }

    private fun loadTile(zoom: Int, x: Int, y: Int, satellite: Boolean): Bitmap? {
        val key = getCacheKey(zoom, x, y, satellite)
        memoryCache[key]?.takeUnless { it.isRecycled }?.let { return it }
        val cacheFile = File(cacheDir, "$key.png")
        if (cacheFile.exists() && cacheFile.length() > 0) {
            try {
                BitmapFactory.decodeFile(cacheFile.absolutePath)?.let {
                    memoryCache.put(key, it)
                    return it
                }
            } catch (e: Exception) {
                cacheFile.delete()
            }
        }
        val bitmap = fetchTile(zoom, x, y, satellite) ?: return null
        try {
            FileOutputStream(cacheFile).use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) { "Tile compression failed" }
                it.flush()
            }
            memoryCache.put(key, bitmap)
            pruneCache()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cache tile", e)
        }
        return bitmap
    }

    private fun fetchTile(zoom: Int, x: Int, y: Int, satellite: Boolean = false): Bitmap? {
        val urlStr = source.urlFor(zoom, x, y, effectiveSatellite(satellite))
        return downloadTile(urlStr)
    }

    private fun downloadTile(urlStr: String): Bitmap? {
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(urlStr).openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("User-Agent", source.userAgent)
            }
            if (connection.responseCode != 200) null
            else connection.inputStream.use { BitmapFactory.decodeStream(it) }
        } catch (e: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun pruneCache() {
        val files = cacheDir.listFiles() ?: return
        var totalBytes = files.sumOf { it.length() }
        if (totalBytes <= MAX_CACHE_BYTES) return
        val sorted = files.sortedBy { it.lastModified() }
        for (file in sorted) {
            if (totalBytes <= MAX_CACHE_BYTES) break
            totalBytes -= file.length()
            file.delete()
        }
    }

    companion object {
        private const val TAG = "TileLoader"
        private const val DEFAULT_ZOOM = 17
        private const val MIN_ZOOM = 1
        private const val TILE_SIZE = 256
        private const val OUTPUT_SIZE = 256
        private const val MAX_MERCATOR_LAT = 85.05112878
        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val READ_TIMEOUT_MS = 5_000
        private const val MAX_CACHE_BYTES = 10L * 1024 * 1024
        private const val OFFLINE_COLOR = 0xFF353535.toInt()
        private const val TILE_WORKERS = 2
        private const val MAX_QUEUED_FETCHES = 4
    }
}
