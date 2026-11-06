package com.hark.sothudiary.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import java.io.File

actual val platformHasMap: Boolean = true

private object OsmSetup {
    @Volatile private var done = false
    fun init(context: Context) {
        if (done) return
        val app = context.applicationContext
        val cfg = Configuration.getInstance()
        cfg.load(app, app.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        // OSM tile usage policy: identify the app.
        cfg.userAgentValue = app.packageName
        val base = File(app.cacheDir, "osmdroid")
        cfg.osmdroidBasePath = base
        cfg.osmdroidTileCache = File(base, "tiles")
        done = true
    }
}

private class MapHolder {
    var map: MapView? = null
    var lastKey: Any? = Unit
    var onTap: ((Double, Double) -> Unit)? = null
    var onPinDragged: ((String, Double, Double) -> Unit)? = null
    var onPinClick: ((String) -> Unit)? = null
    var pinsSignature: List<MapPin>? = null
}

private val pinCache = HashMap<String, Drawable>()

/** A classic teardrop pin in the given colour. */
private fun pinDrawable(context: Context, color: Long, small: Boolean): Drawable {
    val key = "$color-$small"
    pinCache[key]?.let { return it }
    val d = context.resources.displayMetrics.density
    val w = ((if (small) 22 else 32) * d).toInt()
    val h = ((if (small) 30 else 44) * d).toInt()
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val r = w / 2f
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.toInt(); style = Paint.Style.FILL }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xFFFFFFFF.toInt(); style = Paint.Style.STROKE; strokeWidth = 2f * d }
    val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0x33000000; style = Paint.Style.FILL }
    val path = Path().apply {
        moveTo(r, h.toFloat() - 1)
        lineTo(r - r * 0.62f, r * 1.55f)
        lineTo(r + r * 0.62f, r * 1.55f)
        close()
    }
    c.drawCircle(r, r + d, r - 2 * d, shadow)
    c.drawPath(path, fill)
    c.drawCircle(r, r, r - 2 * d, fill)
    c.drawCircle(r, r, r - 2 * d, stroke)
    c.drawCircle(r, r, r * 0.32f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xFFFFFFFF.toInt() })
    return BitmapDrawable(context.resources, bmp).also { pinCache[key] = it }
}

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
) {
    val context = LocalContext.current
    val holder = remember { MapHolder() }
    holder.onTap = onTap
    holder.onPinDragged = onPinDragged
    holder.onPinClick = onPinClick

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            OsmSetup.init(ctx)
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                isTilesScaledToDpi = true
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
                minZoomLevel = 4.0
                maxZoomLevel = 19.0
                // Keep pans / pinches on the map instead of the scrolling screen around it.
                setOnTouchListener { v, e ->
                    if (e.action == MotionEvent.ACTION_DOWN || e.action == MotionEvent.ACTION_MOVE) {
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    false
                }
                overlays.add(
                    MapEventsOverlay(object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                            val cb = holder.onTap ?: return false
                            cb(p.latitude, p.longitude)
                            return true
                        }
                        override fun longPressHelper(p: GeoPoint): Boolean = singleTapConfirmedHelper(p)
                    }),
                )
                holder.map = this
            }
        },
        update = { map ->
            if (holder.pinsSignature != pins) {
                holder.pinsSignature = pins
                map.overlays.removeAll { it is Marker }
                pins.forEach { pin ->
                    val m = Marker(map)
                    m.position = GeoPoint(pin.lat, pin.lng)
                    m.title = pin.title
                    m.snippet = pin.snippet
                    m.icon = pinDrawable(map.context, pin.color, pin.small)
                    m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    m.isDraggable = pin.draggable
                    if (pin.draggable) {
                        m.setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                            override fun onMarkerDrag(marker: Marker) {}
                            override fun onMarkerDragStart(marker: Marker) {}
                            override fun onMarkerDragEnd(marker: Marker) {
                                holder.onPinDragged?.invoke(pin.id, marker.position.latitude, marker.position.longitude)
                            }
                        })
                    }
                    m.setOnMarkerClickListener { marker, _ ->
                        val cb = holder.onPinClick
                        if (cb != null) cb(pin.id) else marker.showInfoWindow()
                        true
                    }
                    map.overlays.add(m)
                }
            }
            if (holder.lastKey != cameraKey) {
                holder.lastKey = cameraKey
                val pts = pins.map { GeoPoint(it.lat, it.lng) }
                fun move() {
                    if (fitPins && pts.size >= 2) {
                        val box = BoundingBox.fromGeoPointsSafe(pts).increaseByScale(1.35f)
                        map.zoomToBoundingBox(box, false, (24 * map.context.resources.displayMetrics.density).toInt())
                        if (map.zoomLevelDouble > 16.0) map.controller.setZoom(16.0)
                    } else {
                        map.controller.setZoom(zoom)
                        map.controller.setCenter(if (fitPins && pts.size == 1) pts[0] else GeoPoint(centerLat, centerLng))
                    }
                }
                if (map.isLayoutOccurred) move() else map.addOnFirstLayoutListener { _, _, _, _, _ -> move() }
            }
            map.invalidate()
        },
    )

    DisposableEffect(Unit) {
        holder.map?.onResume()
        onDispose {
            holder.map?.onPause()
            holder.map?.onDetach()
            holder.map = null
        }
    }
}
