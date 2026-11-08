package com.hark.sothudiary.ui.map

import kotlin.math.floor
import kotlin.math.pow

/** 2.4.0: one thing to show on the Map tab before clustering. */
data class MapItem(
    val id: String,
    val lat: Double,
    val lng: Double,
    val label: String,
    val color: Long,
    /** Higher = better; the cluster takes the colour of its best member. */
    val rank: Double = 0.0,
    val small: Boolean = false,
)

/**
 * Grid clustering for the Map tab: items closer than about [cellPx] screen pixels at [zoom] are drawn as one bubble
 * with their count (tap = zoom in). From zoom 15 every item is drawn on its own.
 */
fun clusterItems(items: List<MapItem>, zoom: Double, cellPx: Double = 70.0): List<MapPin> {
    if (zoom >= 15.0 || items.size <= 1) return items.map { it.toPin() }
    val cellDeg = 360.0 / (256.0 * 2.0.pow(zoom)) * cellPx
    val groups = items.groupBy { floor(it.lat / cellDeg).toLong() to floor(it.lng / cellDeg).toLong() }
    return groups.values.map { g ->
        if (g.size == 1) g[0].toPin()
        else {
            val best = g.maxBy { it.rank }
            MapPin(
                id = CLUSTER_PREFIX + g.joinToString(",") { it.id },
                lat = g.sumOf { it.lat } / g.size, lng = g.sumOf { it.lng } / g.size,
                title = g.size.toString(), color = best.color, count = g.size,
            )
        }
    }
}

const val CLUSTER_PREFIX = "cluster:"

private fun MapItem.toPin() = MapPin(id, lat, lng, label, color = color, label = label, small = small)

/** "₹9.8k", "₹850", "₹1.2L" – a short rupee label for a map tag. */
fun shortRupees(v: Double): String = when {
    v >= 1_00_00_000 -> "₹" + oneDec(v / 1_00_00_000) + "Cr"
    v >= 1_00_000 -> "₹" + oneDec(v / 1_00_000) + "L"
    v >= 1_000 -> "₹" + oneDec(v / 1_000) + "k"
    else -> "₹" + v.toLong()
}

private fun oneDec(v: Double): String {
    val r = kotlin.math.round(v * 10) / 10.0
    return if (r >= 10 || r == floor(r)) r.toLong().toString() else r.toString()
}
