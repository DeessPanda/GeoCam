package dev.geocam.app.location

/**
 * Snapshot of a GPS fix, passed around the app.
 * All fields after [latitude]/[longitude] are optional — check before using.
 */
data class GpsSnapshot(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,        // metres, null if not available
    val accuracy: Float,          // metres horizontal accuracy
    val bearing: Float?,          // degrees true north, null if not available
    val speed: Float?,            // m/s, null if not available
    val fixTimeMs: Long,          // GPS time (Unix ms) — prefer over device clock
    val isMock: Boolean           // true if detected as a mock/simulated location
) {
    /** Reject malformed, mocked, inaccurate, or stale fixes before display or persistence. */
    val isValid: Boolean
        get() {
            val ageMs = System.currentTimeMillis() - fixTimeMs
            return latitude.isFinite() && latitude in -90.0..90.0 &&
                longitude.isFinite() && longitude in -180.0..180.0 &&
                accuracy.isFinite() && accuracy in 0.1f..100f &&
                !isMock && ageMs in -10_000L..120_000L
        }
}
