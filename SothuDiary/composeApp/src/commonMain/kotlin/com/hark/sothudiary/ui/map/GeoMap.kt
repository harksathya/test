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
    /** 2.3.0: drawn as a small round handle (plot corner editing) instead of a teardrop pin. */
    val handle: Boolean = false,
)

/**
 * 2.3.0: an image (FMB sketch) drawn on the map with its four corners pinned to map points (top-left, top-right,
 * bottom-right, bottom-left), so it moves and zooms with the map. Android only.
 */
data class MapSketch(val path: String, val corners: List<Pair<Double, Double>>, val alpha: Float)

/**
 * 2.2.0: a polygon (plot outline, water body) or a line drawn on the map. Colours are ARGB; [fill] is ignored for
 * lines. Shapes are drawn under the pins and never take taps.
 */
data class MapShape(
    val id: String,
    val points: List<Pair<Double, Double>>,
    val stroke: Long,
    val fill: Long = 0x00000000,
    val closed: Boolean = true,
    val widthDp: Float = 2f,
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
    /** 2.2.0: polygons / lines drawn under the pins. */
    shapes: List<MapShape> = emptyList(),
    /**
     * 2.3.0: draggable pins follow the finger at once (no long-press) and report every move here; [onPinDragged]
     * still gets the final position and [onPinClick] a tap on a draggable pin.
     */
    onPinDragging: ((String, Double, Double) -> Unit)? = null,
    /** 2.3.0: an FMB sketch image laid over the map (Android). */
    sketch: MapSketch? = null,
)
