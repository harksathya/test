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
) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        @Suppress("NAME_SHADOWING")
        val pins = pins.filter { isValidCoord(it.lat, it.lng) }
        if (pins.isNotEmpty()) {
            val minLat = pins.minOf { it.lat }; val maxLat = pins.maxOf { it.lat }
            val minLng = pins.minOf { it.lng }; val maxLng = pins.maxOf { it.lng }
            Canvas(Modifier.fillMaxSize().padding(24.dp)) {
                val dLat = (maxLat - minLat).takeIf { it > 1e-6 } ?: 1.0
                val dLng = (maxLng - minLng).takeIf { it > 1e-6 } ?: 1.0
                pins.forEach { p ->
                    val x = if (pins.size == 1) size.width / 2 else ((p.lng - minLng) / dLng * size.width).toFloat()
                    val y = if (pins.size == 1) size.height / 2 else ((maxLat - p.lat) / dLat * size.height).toFloat()
                    drawCircle(Color(p.color), radius = if (p.small) 6f else 10f, center = Offset(x, y))
                    drawCircle(Color.White, radius = 3f, center = Offset(x, y))
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
