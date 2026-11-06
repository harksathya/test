package com.hark.sothudiary.ui.map

import com.hark.sothudiary.domain.haversineKm
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln

/**
 * A usable map coordinate: finite, in range, and not the (0, 0) "null island" that unset values turn into.
 * Maps are never centred on, or fitted to, anything else.
 */
fun isValidCoord(lat: Double?, lng: Double?): Boolean {
    if (lat == null || lng == null) return false
    if (lat.isNaN() || lng.isNaN() || lat.isInfinite() || lng.isInfinite()) return false
    if (lat < -85.0 || lat > 85.0 || lng < -180.0 || lng > 180.0) return false
    return !(kotlin.math.abs(lat) < 1e-6 && kotlin.math.abs(lng) < 1e-6)
}

data class MapCamera(val lat: Double, val lng: Double, val zoom: Double)

/**
 * Camera that shows every valid point. With a valid anchor (the property's pin) the map is centred on it and zoomed
 * so the farthest point still fits; otherwise it is centred on the middle of the points.
 * [halfExtentDp] is half of the map's smaller side minus a margin. Zoom is clamped to [minZoom]..[maxZoom], so a
 * single point (or points on top of each other) gives a close street-level view instead of a degenerate box.
 * Null when there is no valid coordinate at all (then the map must not be shown or moved).
 *
 * Replaces osmdroid's zoomToBoundingBox: a zero-size box (the property and a comparable at the same spot) made
 * BoundingBox.increaseByScale() return a box spanning longitudes -165.7..-39.7, so the map opened over Central
 * America ("Ciudad de Guatemala") at the minimum zoom.
 */
fun fitCamera(
    points: List<Pair<Double, Double>>,
    anchorLat: Double? = null,
    anchorLng: Double? = null,
    halfExtentDp: Double = 70.0,
    minZoom: Double = 9.0,
    maxZoom: Double = 16.0,
): MapCamera? {
    val valid = points.filter { isValidCoord(it.first, it.second) }
    val cLat: Double
    val cLng: Double
    if (isValidCoord(anchorLat, anchorLng)) {
        cLat = anchorLat!!; cLng = anchorLng!!
    } else {
        if (valid.isEmpty()) return null
        cLat = (valid.minOf { it.first } + valid.maxOf { it.first }) / 2
        cLng = (valid.minOf { it.second } + valid.maxOf { it.second }) / 2
    }
    val radiusM = (valid.maxOfOrNull { haversineKm(cLat, cLng, it.first, it.second) } ?: 0.0) * 1000.0 * 1.15
    val zoom = if (radiusM < 50.0) maxZoom else {
        // Web Mercator: metres per dp at zoom z = 156543.03 * cos(lat) / 2^z (tiles scaled to dp).
        val z = ln(156543.03 * cos(cLat * PI / 180.0) * halfExtentDp.coerceAtLeast(20.0) / radiusM) / ln(2.0)
        if (z.isNaN()) maxZoom else z
    }
    return MapCamera(cLat, cLng, zoom.coerceIn(minZoom, maxZoom))
}
