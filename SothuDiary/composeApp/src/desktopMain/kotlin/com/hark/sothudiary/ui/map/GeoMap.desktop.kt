package com.hark.sothudiary.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

actual val platformHasMap: Boolean = false

/** Desktop has no tile map: pins are drawn relative to each other so their spread is still visible. */
@Composable
actual fun GeoMap(
    modifier: Modifier,
    pins: List<MapPin>,
    centerLat: Double,
    centerLng: Double,
    zoom: Double,
    cameraKey: Any?,
    fitPins: Boolean,
    onTap: ((Double, Double) -> Unit)?,
    onPinDragged: ((String, Double, Double) -> Unit)?,
    onPinClick: ((String) -> Unit)?,
    fitAroundCenter: Boolean,
    shapes: List<MapShape>,
    onPinDragging: ((String, Double, Double) -> Unit)?,
    sketch: MapSketch?,
    onViewport: ((MapViewport) -> Unit)?,
    flyTo: Triple<Double, Double, Double>?,
) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        @Suppress("NAME_SHADOWING")
        val pins = pins.filter { isValidCoord(it.lat, it.lng) }
        val shapePts = shapes.flatMap { it.points }.filter { isValidCoord(it.first, it.second) }
        if (pins.isNotEmpty() || shapePts.isNotEmpty()) {
            val lats = pins.map { it.lat } + shapePts.map { it.first }
            val lngs = pins.map { it.lng } + shapePts.map { it.second }
            val minLat = lats.min(); val maxLat = lats.max()
            val minLng = lngs.min(); val maxLng = lngs.max()
            Canvas(Modifier.fillMaxSize().padding(24.dp)) {
                val dLat = (maxLat - minLat).takeIf { it > 1e-6 } ?: 1.0
                val dLng = (maxLng - minLng).takeIf { it > 1e-6 } ?: 1.0
                // 2.2.0: shapes (plot outline, water) drawn with the same relative projection, under the pins.
                fun pt(la: Double, ln: Double) = Offset(((ln - minLng) / dLng * size.width).toFloat(), ((maxLat - la) / dLat * size.height).toFloat())
                shapes.forEach { sh ->
                    val pts = sh.points.filter { isValidCoord(it.first, it.second) }.map { pt(it.first, it.second) }
                    if (pts.size < 2) return@forEach
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(pts[0].x, pts[0].y)
                        pts.drop(1).forEach { lineTo(it.x, it.y) }
                        if (sh.closed) close()
                    }
                    if (sh.closed) drawPath(path, Color(sh.fill))
                    drawPath(path, Color(sh.stroke), style = androidx.compose.ui.graphics.drawscope.Stroke(width = sh.widthDp * density))
                }
                pins.forEach { p ->
                    val x = if (pins.size == 1 && shapePts.isEmpty()) size.width / 2 else ((p.lng - minLng) / dLng * size.width).toFloat()
                    val y = if (pins.size == 1 && shapePts.isEmpty()) size.height / 2 else ((maxLat - p.lat) / dLat * size.height).toFloat()
                    if (p.handle) {
                        drawCircle(Color.White, radius = 5f, center = Offset(x, y))
                        drawCircle(Color(p.color), radius = 5f, center = Offset(x, y), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
                    } else {
                        drawCircle(Color(p.color), radius = if (p.small) 6f else 10f, center = Offset(x, y))
                        drawCircle(Color.White, radius = 3f, center = Offset(x, y))
                    }
                }
            }
        }
        Text(
            "Map preview is available in the Android app",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.BottomCenter).padding(6.dp),
        )
    }
}
