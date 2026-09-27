package dev.geocam.app.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Single source of truth for all user preferences.
 * All keys are constants to avoid typos across the codebase.
 */
class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── GPS / Location ──────────────────────────────────────────────────────

    /** Whether the GPS/location feature is enabled by the user. */
    var gpsEnabled: Boolean
        get() = prefs.getBoolean(KEY_GPS_ENABLED, true)
        set(v) = prefs.edit { putBoolean(KEY_GPS_ENABLED, v) }

    // ── Overlay toggles ─────────────────────────────────────────────────────

    var showPlaceName: Boolean
        get() = prefs.getBoolean(KEY_SHOW_PLACE_NAME, true)
        set(v) = prefs.edit { putBoolean(KEY_SHOW_PLACE_NAME, v) }

    var showCoordinates: Boolean
        get() = prefs.getBoolean(KEY_SHOW_COORDINATES, true)
        set(v) = prefs.edit { putBoolean(KEY_SHOW_COORDINATES, v) }

    var showDateTime: Boolean
        get() = prefs.getBoolean(KEY_SHOW_DATE_TIME, true)
        set(v) = prefs.edit { putBoolean(KEY_SHOW_DATE_TIME, v) }

    var showAccuracy: Boolean
        get() = prefs.getBoolean(KEY_SHOW_ACCURACY, true)
        set(v) = prefs.edit { putBoolean(KEY_SHOW_ACCURACY, v) }

    var showAltitude: Boolean
        get() = prefs.getBoolean(KEY_SHOW_ALTITUDE, false)
        set(v) = prefs.edit { putBoolean(KEY_SHOW_ALTITUDE, v) }

    var showHeading: Boolean
        get() = prefs.getBoolean(KEY_SHOW_HEADING, false)
        set(v) = prefs.edit { putBoolean(KEY_SHOW_HEADING, v) }

    /** Custom note appended to the overlay card. Empty = not shown. */
    var customNote: String
        get() = prefs.getString(KEY_CUSTOM_NOTE, "") ?: ""
        set(v) = prefs.edit { putString(KEY_CUSTOM_NOTE, v) }

    // ── Coordinate format ───────────────────────────────────────────────────

    /** true = decimal degrees, false = DMS */
    var useDecimalCoords: Boolean
        get() = prefs.getBoolean(KEY_DECIMAL_COORDS, true)
        set(v) = prefs.edit { putBoolean(KEY_DECIMAL_COORDS, v) }

    // ── Mini-map ────────────────────────────────────────────────────────────

    var miniMapEnabled: Boolean
        get() = prefs.getBoolean(KEY_MINI_MAP, true)
        set(v) = prefs.edit { putBoolean(KEY_MINI_MAP, v) }

    // ── Map type ─────────────────────────────────────────────────────────────

    /** true = satellite imagery, false = road map. Default satellite. */
    var satelliteMapEnabled: Boolean
        get() = prefs.getBoolean(KEY_SATELLITE_MAP, true)
        set(v) = prefs.edit { putBoolean(KEY_SATELLITE_MAP, v) }

    /** Mini-map zoom preset: 1 = closest, 3 = widest. */
    var miniMapZoomLevel: Int
        get() = prefs.getInt(KEY_MINI_MAP_ZOOM_LEVEL, 1).coerceIn(1, 3)
        set(v) = prefs.edit { putInt(KEY_MINI_MAP_ZOOM_LEVEL, v.coerceIn(1, 3)) }

    val miniMapZoom: Int
        get() = when (miniMapZoomLevel) {
            1 -> 18
            2 -> 17
            else -> 15
        }

    /** Refresh interval in ms for live overlay tile updates */
    var overlayRefreshMs: Int
        get() = prefs.getInt(KEY_OVERLAY_REFRESH_MS, 5000)
        set(v) = prefs.edit { putInt(KEY_OVERLAY_REFRESH_MS, v) }

    // ── Image quality ───────────────────────────────────────────────────────

    /** JPEG quality 0-100 */
    var jpegQuality: Int
        get() = prefs.getInt(KEY_JPEG_QUALITY, 92)
        set(v) = prefs.edit { putInt(KEY_JPEG_QUALITY, v) }

    /** Maximum long-edge in pixels before downsampling. 0 = no cap (full res). */
    var maxLongEdge: Int
        get() = prefs.getInt(KEY_MAX_LONG_EDGE, 0)
        set(v) = prefs.edit { putInt(KEY_MAX_LONG_EDGE, v) }

    // ── Privacy ─────────────────────────────────────────────────────────────

    /** Save an additional clean (un-stamped) copy of every photo. */
    var saveCleanCopy: Boolean
        get() = prefs.getBoolean(KEY_SAVE_CLEAN_COPY, false)
        set(v) = prefs.edit { putBoolean(KEY_SAVE_CLEAN_COPY, v) }

    /** Per-shot toggle: save without location card and GPS EXIF. */
    var saveWithoutLocation: Boolean
        get() = prefs.getBoolean(KEY_SAVE_WITHOUT_LOCATION, false)
        set(v) = prefs.edit { putBoolean(KEY_SAVE_WITHOUT_LOCATION, v) }

    // ── Front camera ────────────────────────────────────────────────────────

    /**
     * If true, a selfie is stored mirrored the way the preview showed it.
     * Default = unmirrored.
     *
     * Not surfaced in SettingsActivity yet, and currently a no-op: MainActivity
     * binds only the back camera, so there is no selfie to mirror. The capture
     * pipeline applies a fixed decision (see `image.BitmapProcessor`) and
     * `OverlayRenderer` is built to never mirror the card, which is what makes
     * this safe to switch on once a front camera is wired up.
     */
    var saveMirroredSelfie: Boolean
        get() = prefs.getBoolean(KEY_SAVE_MIRRORED_SELFIE, false)
        set(v) = prefs.edit { putBoolean(KEY_SAVE_MIRRORED_SELFIE, v) }

    companion object {
        private const val PREFS_NAME = "geocam_prefs"

        const val KEY_GPS_ENABLED = "gps_enabled"
        const val KEY_SHOW_PLACE_NAME = "show_place_name"
        const val KEY_SHOW_COORDINATES = "show_coordinates"
        const val KEY_SHOW_DATE_TIME = "show_date_time"
        const val KEY_SHOW_ACCURACY = "show_accuracy"
        const val KEY_SHOW_ALTITUDE = "show_altitude"
        const val KEY_SHOW_HEADING = "show_heading"
        const val KEY_CUSTOM_NOTE = "custom_note"
        const val KEY_DECIMAL_COORDS = "decimal_coords"
        const val KEY_MINI_MAP = "mini_map"
        const val KEY_JPEG_QUALITY = "jpeg_quality"
        const val KEY_MAX_LONG_EDGE = "max_long_edge"
        const val KEY_SAVE_CLEAN_COPY = "save_clean_copy"
        const val KEY_SAVE_WITHOUT_LOCATION = "save_without_location"
        const val KEY_SAVE_MIRRORED_SELFIE = "save_mirrored_selfie"
        const val KEY_SATELLITE_MAP = "satellite_map"
        const val KEY_MINI_MAP_ZOOM_LEVEL = "mini_map_zoom_level"
        const val KEY_OVERLAY_REFRESH_MS = "overlay_refresh_ms"
    }
}
