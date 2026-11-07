package com.hark.sothudiary.domain.land

import com.hark.sothudiary.data.GeoPt
import com.hark.sothudiary.data.SketchOverlay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 2.3.0: editing the plot corners (drag a corner, tap to select, delete the selected one, drag / tap a midpoint
 * handle to insert a corner). Pure functions on the corner list, so the map code only reports touches.
 */
object PlotEdit {
    /** Map pin ids: "c3" = corner 3, "m3" = the handle in the middle of the edge from corner 3 to the next one. */
    fun cornerId(i: Int) = "c$i"
    fun midId(i: Int) = "m$i"
    fun cornerIndex(id: String): Int? = id.takeIf { it.startsWith("c") }?.drop(1)?.toIntOrNull()
    fun midIndex(id: String): Int? = id.takeIf { it.startsWith("m") }?.drop(1)?.toIntOrNull()

    /** Moves corner [i] (out of range: unchanged). */
    fun move(corners: List<GeoPt>, i: Int, to: GeoPt): List<GeoPt> =
        if (i !in corners.indices) corners else corners.toMutableList().also { it[i] = to }

    /** Removes corner [i]. */
    fun delete(corners: List<GeoPt>, i: Int): List<GeoPt> =
        if (i !in corners.indices) corners else corners.toMutableList().also { it.removeAt(i) }

    /** Inserts [p] after corner [i] (on the edge i → i+1); returns the new list and the new corner's index. */
    fun insertAfter(corners: List<GeoPt>, i: Int, p: GeoPt): Pair<List<GeoPt>, Int> {
        if (corners.isEmpty()) return listOf(p) to 0
        val at = (i.coerceIn(-1, corners.size - 1)) + 1
        return corners.toMutableList().also { it.add(at, p) } to at
    }

    /** The middle of each edge (the closing edge too once there are 3 corners); 2 corners: one midpoint. */
    fun midpoints(corners: List<GeoPt>): List<GeoPt> {
        if (corners.size < 2) return emptyList()
        val n = if (corners.size >= 3) corners.size else 1
        return (0 until n).map { i ->
            val a = corners[i]; val b = corners[(i + 1) % corners.size]
            GeoPt((a.lat + b.lat) / 2, (a.lng + b.lng) / 2)
        }
    }

    /** Selection after deleting corner [deleted]: the previous corner (or none when the list is empty). */
    fun selectionAfterDelete(selected: Int?, deleted: Int, newSize: Int): Int? = when {
        newSize == 0 -> null
        selected == null -> null
        else -> (deleted - 1).coerceIn(0, newSize - 1)
    }

    /**
     * A drag of a midpoint handle: the first move inserts a corner after edge [edge] at [to]; later moves of the same
     * drag move that new corner. [inserted] is the index from an earlier move of this drag (null at the start).
     * Returns the corners and the index being dragged.
     */
    fun dragMid(corners: List<GeoPt>, edge: Int, to: GeoPt, inserted: Int?): Pair<List<GeoPt>, Int> =
        if (inserted != null && inserted in corners.indices) move(corners, inserted, to) to inserted
        else insertAfter(corners, edge, to)

    /** Area (m²) of the corners, 0 for fewer than 3. */
    fun areaSqm(corners: List<GeoPt>): Double = if (corners.size < 3) 0.0 else polygonAreaSqm(corners.map { it.lat to it.lng })
}

/**
 * 2.3.0: the four map corners (top-left, top-right, bottom-right, bottom-left) of an FMB sketch image placed with
 * [SketchOverlay] (centre, width in metres, height = width × aspect, turned clockwise by rotationDeg).
 */
fun sketchCorners(s: SketchOverlay): List<GeoPt> {
    val proj = LocalProjection(s.lat, s.lng)
    val w = s.widthM / 2
    val h = s.widthM * s.aspect.coerceIn(0.05, 20.0) / 2
    val t = -s.rotationDeg * PI / 180.0 // clockwise on the map = negative in the x/y (east/north) plane
    fun r(x: Double, y: Double): GeoPt {
        val rx = x * cos(t) - y * sin(t)
        val ry = x * sin(t) + y * cos(t)
        return proj.toLatLng(XY(rx, ry)).let { (a, b) -> GeoPt(a, b) }
    }
    return listOf(r(-w, h), r(w, h), r(w, -h), r(-w, -h))
}

/** Moves a sketch by [eastM] / [northM] metres. */
fun moveSketch(s: SketchOverlay, eastM: Double, northM: Double): SketchOverlay {
    val p = LocalProjection(s.lat, s.lng).toLatLng(XY(eastM, northM))
    return s.copy(lat = p.first, lng = p.second)
}
