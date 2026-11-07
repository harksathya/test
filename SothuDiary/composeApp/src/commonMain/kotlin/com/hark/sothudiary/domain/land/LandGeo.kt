package com.hark.sothudiary.domain.land

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Small plane geometry for the land check (2.2.0). Everything is done in metres on a local equirectangular
 * projection around one origin – accurate to well under 1 % within a few kilometres, which is all a plot check needs.
 */
data class XY(val x: Double, val y: Double)

class LocalProjection(val lat0: Double, val lng0: Double) {
    private val mPerDegLat = 111_320.0
    private val mPerDegLng = 111_320.0 * cos(lat0 * PI / 180.0)
    fun toXY(lat: Double, lng: Double) = XY((lng - lng0) * mPerDegLng, (lat - lat0) * mPerDegLat)
    fun toLatLng(p: XY): Pair<Double, Double> = (lat0 + p.y / mPerDegLat) to (lng0 + p.x / mPerDegLng)
}

/** Area of a simple polygon (shoelace), m². The ring may or may not repeat its first point. */
fun polygonAreaSqm(ring: List<XY>): Double {
    if (ring.size < 3) return 0.0
    var s = 0.0
    for (i in ring.indices) {
        val a = ring[i]
        val b = ring[(i + 1) % ring.size]
        s += a.x * b.y - b.x * a.y
    }
    return abs(s) / 2.0
}

/** Area of a lat/lng polygon in m². */
fun polygonAreaSqm(points: List<Pair<Double, Double>>, proj: LocalProjection = centroidProjection(points)): Double =
    polygonAreaSqm(points.map { proj.toXY(it.first, it.second) })

fun centroidProjection(points: List<Pair<Double, Double>>): LocalProjection =
    if (points.isEmpty()) LocalProjection(0.0, 0.0)
    else LocalProjection(points.sumOf { it.first } / points.size, points.sumOf { it.second } / points.size)

/** A regular polygon (default 48 sides) with the same area as a circle of [areaSqm] – "the plot" when no corners are drawn. */
fun circleOfArea(center: XY, areaSqm: Double, sides: Int = 48): List<XY> {
    val r = sqrt(areaSqm / PI)
    return (0 until sides).map { i ->
        val t = 2 * PI * i / sides
        XY(center.x + r * cos(t), center.y + r * sin(t))
    }
}

/** Shortest distance from [p] to the segment [a]–[b], metres. */
fun distToSegment(p: XY, a: XY, b: XY): Double {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val len2 = dx * dx + dy * dy
    val t = if (len2 <= 0.0) 0.0 else (((p.x - a.x) * dx + (p.y - a.y) * dy) / len2).coerceIn(0.0, 1.0)
    val cx = a.x + t * dx - p.x
    val cy = a.y + t * dy - p.y
    return sqrt(cx * cx + cy * cy)
}

/** Ray-casting point-in-polygon for one ring. */
fun pointInRing(p: XY, ring: List<XY>): Boolean {
    var inside = false
    var j = ring.size - 1
    for (i in ring.indices) {
        val a = ring[i]; val b = ring[j]
        if ((a.y > p.y) != (b.y > p.y)) {
            val x = (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x
            if (p.x < x) inside = !inside
        }
        j = i
    }
    return inside
}

/**
 * A water body (or any shape) in projected metres: [rings] are areas (closed polygons; a multipolygon's outer and
 * inner rings together – even-odd, so islands are holes), [lines] are centre lines (a river or canal mapped as a line)
 * with a half-width [lineHalfWidthM].
 */
data class Shape(val rings: List<List<XY>> = emptyList(), val lines: List<List<XY>> = emptyList(), val lineHalfWidthM: Double = 0.0) {
    fun isEmpty() = rings.all { it.size < 3 } && lines.all { it.size < 2 }
}

/** True when [p] is inside the area of [s] (even-odd over all rings). */
fun insideShape(p: XY, s: Shape): Boolean {
    var crossings = 0
    for (r in s.rings) if (r.size >= 3 && pointInRing(p, r)) crossings++
    return crossings % 2 == 1
}

/** Distance (m) from [p] to the edge of [s]; 0 when [p] is inside its area or on a line (within its half-width). */
fun distanceToShape(p: XY, s: Shape): Double {
    if (insideShape(p, s)) return 0.0
    var best = Double.MAX_VALUE
    for (r in s.rings) {
        if (r.size < 2) continue
        for (i in r.indices) best = minOf(best, distToSegment(p, r[i], r[(i + 1) % r.size]))
    }
    for (l in s.lines) {
        if (l.size == 1) best = minOf(best, distToSegment(p, l[0], l[0]) - s.lineHalfWidthM)
        for (i in 0 until l.size - 1) best = minOf(best, distToSegment(p, l[i], l[i + 1]) - s.lineHalfWidthM)
    }
    return best.coerceAtLeast(0.0)
}

/** Result of overlaying a plot with water bodies and their buffer. Percentages are of the plot area (0–100). */
data class Overlap(
    /** Share of the plot on the water body itself. */
    val insideWaterPct: Double,
    /** Share of the plot on the water body or within [bufferM] of it. */
    val inBufferPct: Double,
    /** Closest any part of the plot comes to water (m). */
    val nearestM: Double?,
    val samples: Int,
)

/**
 * Overlays [plot] with [shapes] by sampling a regular grid of about [targetSamples] points inside the plot.
 * Grid sampling keeps the code simple and robust for any polygon (concave plots, multipolygon lakes, rivers as lines);
 * with ~1,000 samples the percentage is accurate to about ±1 %.
 */
fun overlapWithBuffer(plot: List<XY>, shapes: List<Shape>, bufferM: Double, targetSamples: Int = 1000): Overlap {
    if (plot.size < 3) return Overlap(0.0, 0.0, null, 0)
    val minX = plot.minOf { it.x }; val maxX = plot.maxOf { it.x }
    val minY = plot.minOf { it.y }; val maxY = plot.maxOf { it.y }
    val area = polygonAreaSqm(plot).coerceAtLeast(1.0)
    val step = sqrt(area / targetSamples).coerceAtLeast(0.5)
    val nx = ceil((maxX - minX) / step).toInt().coerceIn(1, 400)
    val ny = ceil((maxY - minY) / step).toInt().coerceIn(1, 400)
    val usable = shapes.filterNot { it.isEmpty() }
    var total = 0
    var inWater = 0
    var inBuf = 0
    var nearest = Double.MAX_VALUE
    for (i in 0 until nx) for (j in 0 until ny) {
        val p = XY(minX + (i + 0.5) * (maxX - minX) / nx, minY + (j + 0.5) * (maxY - minY) / ny)
        if (!pointInRing(p, plot)) continue
        total++
        val d = usable.minOfOrNull { distanceToShape(p, it) } ?: continue
        nearest = minOf(nearest, d)
        if (d <= 0.0) inWater++
        if (d <= bufferM) inBuf++
    }
    // The plot edge can come closer to water than any sample point.
    for (v in plot) usable.minOfOrNull { distanceToShape(v, it) }?.let { nearest = minOf(nearest, it) }
    if (total == 0) return Overlap(0.0, 0.0, nearest.takeIf { it < Double.MAX_VALUE }, 0)
    return Overlap(inWater * 100.0 / total, inBuf * 100.0 / total, nearest.takeIf { it < Double.MAX_VALUE }, total)
}

/** Shortest distance (m) from [p] to a polyline / point list. */
fun distanceToPath(p: XY, path: List<XY>): Double? {
    if (path.isEmpty()) return null
    if (path.size == 1) return distToSegment(p, path[0], path[0])
    var best = Double.MAX_VALUE
    for (i in 0 until path.size - 1) best = minOf(best, distToSegment(p, path[i], path[i + 1]))
    return best
}

/** Keeps at most [max] points of a ring / line (every n-th point; the first and last stay). For drawing only. */
fun <T> thin(points: List<T>, max: Int): List<T> {
    if (points.size <= max || max < 3) return points
    val step = points.size.toDouble() / (max - 1)
    return (0 until max - 1).map { points[(it * step).toInt()] } + points.last()
}

/**
 * Joins paths that share end points into rings (an OSM multipolygon's outer ring often comes in several member
 * ways). Closed results end with their first point; paths that cannot be closed are returned as they are.
 */
fun <T> assembleRings(paths: List<List<T>>): List<List<T>> {
    val pool = paths.filter { it.isNotEmpty() }.map { it.toMutableList() }.toMutableList()
    val out = mutableListOf<List<T>>()
    while (pool.isNotEmpty()) {
        val cur = pool.removeAt(0)
        var grown = true
        while (cur.first() != cur.last() && grown) {
            grown = false
            val i = pool.indexOfFirst { it.first() == cur.last() || it.last() == cur.last() }
            if (i >= 0) {
                val next = pool.removeAt(i)
                cur.addAll((if (next.first() == cur.last()) next else next.reversed()).drop(1))
                grown = true
            }
        }
        out += cur
    }
    return out
}
