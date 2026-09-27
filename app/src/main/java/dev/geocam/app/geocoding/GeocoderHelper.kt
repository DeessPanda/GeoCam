package dev.geocam.app.geocoding

import dev.geocam.app.AppInfo
import android.content.Context
import android.location.Geocoder
import android.os.Build
import android.util.Log
import android.util.LruCache
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

class GeocoderHelper(context: Context) {

    private val context = context.applicationContext
    private val cache = object : LruCache<String, PlaceName>(MAX_CACHE_ENTRIES) {}
    private val executor = ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(MAX_QUEUED_REQUESTS),
        { runnable -> Thread(runnable, "geocoder-bg").also { it.isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy()
    )

    private val inFlightKeys = ConcurrentHashMap.newKeySet<String>()
    private val lastAttemptMs = object : LruCache<String, Long>(MAX_CACHE_ENTRIES) {}
    private val lastNominatimRequestMs = AtomicLong(0L)

    fun geocode(lat: Double, lon: Double, onResult: (PlaceName) -> Unit) {
        val cacheKey = cacheKey(lat, lon)
        val cached = cache.get(cacheKey)
        if (cached != null) {
            onResult(cached)
            if (hasUsableArea(cached) || System.currentTimeMillis() - (lastAttemptMs.get(cacheKey) ?: 0L) < INCOMPLETE_RETRY_MS) return
        }
        if (!inFlightKeys.add(cacheKey)) return
        lastAttemptMs.put(cacheKey, System.currentTimeMillis())

        try {
            executor.execute {
                try {
                    val result = doGeocode(lat, lon)
                    cache.put(cacheKey, result)
                    onResult(result)
                } catch (e: Exception) {
                    Log.w(TAG, "Reverse geocoding failed", e)
                } finally {
                    inFlightKeys.remove(cacheKey)
                }
            }
        } catch (e: java.util.concurrent.RejectedExecutionException) {
            inFlightKeys.remove(cacheKey)
            Log.d(TAG, "Dropped geocode request because the bounded queue is full")
        }
    }

    fun getOrNull(lat: Double, lon: Double): PlaceName? = cache.get(cacheKey(lat, lon))

    fun geocodeNow(lat: Double, lon: Double): PlaceName {
        val cacheKey = cacheKey(lat, lon)
        cache.get(cacheKey)?.let { return it }
        val result = doGeocode(lat, lon)
        cache.put(cacheKey, result)
        return result
    }

    private fun doGeocode(lat: Double, lon: Double): PlaceName {
        if (Geocoder.isPresent()) {
            val place = tryAndroidGeocoder(lat, lon)
            if (place != null) return place.copy(fromCache = false)
        }

        val place2 = tryNominatim(lat, lon)
        if (place2 != null) return place2.copy(fromCache = false)

        return PlaceName(formatCoords(lat, lon), null, null, fromCache = false)
    }

    private fun tryAndroidGeocoder(lat: Double, lon: Double): PlaceName? {
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            var result: PlaceName? = null

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val latch = java.util.concurrent.CountDownLatch(1)
                geocoder.getFromLocation(lat, lon, 1) { addresses ->
                    result = extractName(addresses)
                    latch.countDown()
                }
                latch.await(GEOCODER_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(lat, lon, 1)
                result = extractName(addresses ?: emptyList())
            }
            result
        } catch (e: Exception) {
            Log.w(TAG, "Android Geocoder failed: ${e.message}")
            null
        }
    }

    private fun extractName(addresses: List<android.location.Address>): PlaceName? {
        val addr = addresses.firstOrNull() ?: return null

        // Exclude generic numbers matching street numbers (e.g. "101")
        val feature = addr.featureName?.takeIf {
            !it.matches(Regex("\\d+.*")) &&
                !it.equals(addr.locality, true) &&
                !it.equals(addr.subAdminArea, true) &&
                !it.equals(addr.adminArea, true)
        }

        val area = addr.premises
            ?: addr.subThoroughfare?.let { number -> listOfNotNull(number, addr.thoroughfare).joinToString(" ") }
            ?: addr.thoroughfare
            ?: feature
            ?: addr.subLocality
            ?: addr.locality
            ?: addr.subAdminArea
            ?: addr.getAddressLine(0)?.substringBefore(',')?.takeIf { it.isNotBlank() }

        val city = addr.locality ?: addr.subAdminArea
        val state = addr.adminArea
        val cityState = listOfNotNull(city, state).joinToString(", ").takeIf { it.isNotBlank() }
        val localArea = addr.subLocality ?: addr.subAdminArea

        return PlaceName(area, cityState, addr.getAddressLine(0), false, localArea, state)
    }

    private fun tryNominatim(lat: Double, lon: Double): PlaceName? {
        val now = System.currentTimeMillis()
        val last = lastNominatimRequestMs.get()
        if (now - last < NOMINATIM_RATE_LIMIT_MS) {
            Thread.sleep(NOMINATIM_RATE_LIMIT_MS - (now - last))
        }
        lastNominatimRequestMs.set(System.currentTimeMillis())

        var connection: HttpURLConnection? = null
        return try {
            val url = URL("https://nominatim.openstreetmap.org/reverse?format=json&lat=$lat&lon=$lon&zoom=16&addressdetails=1")
            connection = url.openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                connectTimeout = NOMINATIM_CONNECT_TIMEOUT_MS
                readTimeout = NOMINATIM_READ_TIMEOUT_MS
                setRequestProperty("User-Agent", AppInfo.contactUserAgent)
                setRequestProperty("Accept-Language", Locale.getDefault().toLanguageTag())
            }

            if (connection.responseCode != 200) return null
            val json = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
            parseNominatim(json)
        } catch (e: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun parseNominatim(json: String): PlaceName? {
        return try {
            val obj = JSONObject(json)
            val address = obj.optJSONObject("address") ?: return null

            val namedPlace = jsonText(obj, "name")
            val houseNumber = jsonText(address, "house_number")
            val road = sequenceOf("road", "pedestrian", "residential", "footway")
                .map { jsonText(address, it) }
                .firstOrNull { it != null }
            val numberedStreet = if (houseNumber != null && road != null) "$houseNumber $road" else null
            val area = namedPlace ?: sequenceOf("building", "amenity", "shop", "tourism", "leisure", "historic")
                .map { jsonText(address, it)?.takeIf { value -> value !in GENERIC_PLACE_VALUES } }
                .firstOrNull { it != null }
                ?: numberedStreet
                ?: road
                ?: jsonText(address, "neighbourhood")
                ?: jsonText(address, "suburb")
                ?: jsonText(address, "city_district")
                ?: jsonText(address, "locality")
                ?: jsonText(address, "village")

            val city = jsonText(address, "city")
                ?: jsonText(address, "town")
                ?: jsonText(address, "municipality")
                ?: jsonText(address, "village")
                ?: jsonText(address, "county")

            val state = jsonText(address, "state") ?: jsonText(address, "state_district")

            val cityState = listOfNotNull(city, state).joinToString(", ").takeIf { it.isNotBlank() }
            val localArea = jsonText(address, "neighbourhood")
                ?: jsonText(address, "suburb")
                ?: jsonText(address, "city_district")
                ?: jsonText(address, "locality")

            val fullAddress = jsonText(obj, "display_name")
            val resolvedArea = area ?: fullAddress?.substringBefore(',')?.takeIf { it.isNotBlank() }

            PlaceName(resolvedArea, cityState, fullAddress, false, localArea, state)
        } catch (e: Exception) {
            null
        }
    }

    private fun cacheKey(lat: Double, lon: Double): String {
        val rLat = (lat * 1000).roundToInt() / 1000.0
        val rLon = (lon * 1000).roundToInt() / 1000.0
        return "$rLat,$rLon"
    }

    private fun formatCoords(lat: Double, lon: Double): String {
        return "%.5f, %.5f".format(lat, lon)
    }

    private fun jsonText(json: JSONObject, key: String): String? =
        json.optString(key).trim().takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }

    private fun hasUsableArea(place: PlaceName): Boolean =
        !place.areaName.isNullOrBlank() && !COORDINATE_AREA.matches(place.areaName)

    fun shutdown() {
        executor.shutdownNow()
        inFlightKeys.clear()
    }

    companion object {
        private const val TAG = "GeocoderHelper"
        private const val GEOCODER_TIMEOUT_MS = 3_000L
        private const val NOMINATIM_RATE_LIMIT_MS = 1_000L
        private const val NOMINATIM_CONNECT_TIMEOUT_MS = 5_000
        private const val NOMINATIM_READ_TIMEOUT_MS = 5_000
        private const val INCOMPLETE_RETRY_MS = 15_000L
        private const val MAX_CACHE_ENTRIES = 256
        private const val MAX_QUEUED_REQUESTS = 4
        private val GENERIC_PLACE_VALUES = setOf("yes", "no", "house", "apartments", "residential", "commercial")
        private val COORDINATE_AREA = Regex("-?\\d+\\.\\d+, -?\\d+\\.\\d+")
    }
}
