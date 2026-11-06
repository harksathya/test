package com.hark.sothudiary.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** A pin on the map. [color] is ARGB. */
data class MapPin(
    val id: String,
    val lat: Double,
    val lng: Double,
    val title: String,
    val snippet: String = "",
    val color: Long,
    val draggable: Boolean = false,
    val small: Boolean = false,
)

/** True where a real interactive map is available (Android: OpenStreetMap via osmdroid). */
expect val platformHasMap: Boolean

/**
 * Map view. The camera moves to [centerLat]/[centerLng]/[zoom] (or fits all pins when [fitPins])
 * whenever [cameraKey] changes – not on every recomposition, so the user can pan freely.
 */
@Composable
expect fun GeoMap(
    modifier: Modifier,
    pins: List<MapPin>,
    centerLat: Double,
    centerLng: Double,
    zoom: Double,
    cameraKey: Any?,
    fitPins: Boolean = false,
    onTap: ((Double, Double) -> Unit)? = null,
    onPinDragged: ((String, Double, Double) -> Unit)? = null,
    onPinClick: ((String) -> Unit)? = null,
    /** With [fitPins]: keep [centerLat]/[centerLng] (the property's pin) in the middle and zoom so all pins fit. */
    fitAroundCenter: Boolean = false,
)
