package dev.geocam.app.geocoding

/**
 * Encapsulates reverse-geocoded location data.
 */
data class PlaceName(
    val areaName: String?,
    val cityState: String?,
    val formattedAddress: String? = null,
    val fromCache: Boolean,
    val localArea: String? = null,
    val stateName: String? = null
) {
    val displayName: String
        get() = listOfNotNull(areaName, cityState).joinToString(" - ")

    val primaryName: String
        get() = listOfNotNull(areaName, localArea, stateName)
            .distinctBy { it.trim().lowercase() }
            .joinToString(", ")
}
