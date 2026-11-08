package com.hark.sothudiary.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Point
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
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
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
    var shapesSignature: List<MapShape>? = null
    // 2.3.0
    var onPinDragging: ((String, Double, Double) -> Unit)? = null
    var pins: List<MapPin> = emptyList()
    var sketchSignature: MapSketch? = null
    // 2.4.0
    var onViewport: ((MapViewport) -> Unit)? = null
    var lastFly: Triple<Double, Double, Double>? = null
}

/** 2.4.0: reports the visible box after scrolling / zooming (debounced by osmdroid's own listener). */
private fun reportViewport(map: MapView, holder: MapHolder) {
    val cb = holder.onViewport ?: return
    val b = map.boundingBox ?: return
    if (b.latNorth.isNaN() || b.lonEast.isNaN()) return
    cb(MapViewport(b.latSouth, b.lonWest, b.latNorth, b.lonEast, map.zoomLevelDouble))
}

private val tagCache = HashMap<String, Drawable>()

/** 2.4.0: a rounded price tag with a small pointer, e.g. "₹9.8k", in [color] (selected: bigger with a dark ring). */
private fun tagDrawable(context: Context, text: String, color: Long, selected: Boolean): Drawable {
    val key = "t$text|$color|$selected"
    tagCache[key]?.let { return it }
    val d = context.resources.displayMetrics.density
    val tp = android.text.TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = (if (selected) 14f else 12f) * d; this.color = 0xFFFFFFFF.toInt(); typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    val padX = 7 * d; val padY = 4 * d; val tail = 5 * d
    val tw = tp.measureText(text)
    val w = (tw + 2 * padX).toInt().coerceAtLeast((18 * d).toInt())
    val bh = (tp.textSize + 2 * padY)
    val h = (bh + tail).toInt() + 2
    val bmp = Bitmap.createBitmap(w + 4, h + 2, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.toInt() }
    val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = if (selected) 0xFF1D1C1A.toInt() else 0xFFFFFFFF.toInt(); style = Paint.Style.STROKE; strokeWidth = (if (selected) 2.5f else 1.5f) * d }
    val r = android.graphics.RectF(2f, 1f, w + 2f, bh)
    c.drawRoundRect(r, bh / 2, bh / 2, fill)
    c.drawRoundRect(r, bh / 2, bh / 2, ring)
    val path = Path().apply { moveTo((w + 4) / 2f - tail, bh - 1); lineTo((w + 4) / 2f, bh + tail); lineTo((w + 4) / 2f + tail, bh - 1); close() }
    c.drawPath(path, fill)
    c.drawText(text, (w + 4 - tw) / 2f, padY + tp.textSize * 0.82f + 1f, tp)
    return BitmapDrawable(context.resources, bmp).also { tagCache[key] = it }
}

/** 2.4.0: a round cluster bubble with the number of pins inside. */
private fun clusterDrawable(context: Context, count: Int, color: Long): Drawable {
    val key = "c$count|$color"
    tagCache[key]?.let { return it }
    val d = context.resources.displayMetrics.density
    val size = ((if (count >= 100) 46 else if (count >= 10) 40 else 34) * d).toInt()
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val r = size / 2f
    c.drawCircle(r, r, r - 1, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = ((color and 0x00FFFFFFL) or 0x55000000L).toInt() })
    c.drawCircle(r, r, r * 0.74f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.toInt() })
    val tp = android.text.TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 13 * d; this.color = 0xFFFFFFFF.toInt(); typeface = android.graphics.Typeface.DEFAULT_BOLD }
    val t = count.toString()
    c.drawText(t, r - tp.measureText(t) / 2, r + tp.textSize * 0.36f, tp)
    return BitmapDrawable(context.resources, bmp).also { tagCache[key] = it }
}

/**
 * 2.3.0: drags draggable pins as soon as the finger touches them (osmdroid's own marker drag needs a long-press, which
 * people did not find – "corners can only be undone"). Reports every move ([MapHolder.onPinDragging]), the final
 * position ([MapHolder.onPinDragged]) and a tap without movement ([MapHolder.onPinClick]). Only active when the screen
 * asked for live dragging; other maps keep the long-press drag.
 */
private class PinDragOverlay(private val holder: MapHolder) : Overlay() {
    private var dragging: String? = null
    private var offX = 0f
    private var offY = 0f
    private var downX = 0f
    private var downY = 0f
    private var moved = false
    private var last: GeoPoint? = null

    private fun hit(e: MotionEvent, map: MapView): MapPin? {
        val d = map.context.resources.displayMetrics.density
        val pt = Point()
        var best: MapPin? = null
        var bestD = Float.MAX_VALUE
        for (p in holder.pins) {
            if (!p.draggable) continue
            map.projection.toPixels(GeoPoint(p.lat, p.lng), pt)
            // Teardrop pins are drawn above their point; handles are centred on it.
            val cy = if (p.handle) pt.y.toFloat() else pt.y - 16 * d
            val dx = e.x - pt.x; val dy = e.y - cy
            val dist = kotlin.math.sqrt(dx * dx + dy * dy)
            val limit = (if (p.handle) 22 else 26) * d
            // Corners win over midpoint handles when both are under the finger.
            val score = dist + (if (p.handle) 6 * d else 0f)
            if (dist <= limit && score < bestD) { best = p; bestD = score }
        }
        return best
    }

    override fun onTouchEvent(e: MotionEvent, map: MapView): Boolean {
        if (holder.onPinDragging == null) return false
        if (e.pointerCount > 1) { if (dragging != null) finish(map, cancel = true); return false }
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val p = hit(e, map) ?: return false
                val pt = Point()
                map.projection.toPixels(GeoPoint(p.lat, p.lng), pt)
                dragging = p.id; downX = e.x; downY = e.y; offX = e.x - pt.x; offY = e.y - pt.y; moved = false; last = null
                map.parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val id = dragging ?: return false
                val slop = 6 * map.context.resources.displayMetrics.density
                if (!moved && kotlin.math.abs(e.x - downX) < slop && kotlin.math.abs(e.y - downY) < slop) return true
                moved = true
                val g = map.projection.fromPixels((e.x - offX).toInt(), (e.y - offY).toInt()) as GeoPoint
                last = g
                holder.onPinDragging?.invoke(id, g.latitude, g.longitude)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging == null) return false
                finish(map, cancel = e.actionMasked == MotionEvent.ACTION_CANCEL)
                return true
            }
        }
        return dragging != null
    }

    private fun finish(map: MapView, cancel: Boolean) {
        val id = dragging ?: return
        val g = last
        dragging = null
        if (moved && g != null) holder.onPinDragged?.invoke(id, g.latitude, g.longitude)
        else if (!cancel) holder.onPinClick?.invoke(id)
        moved = false; last = null
        map.invalidate()
    }
}

/** 2.3.0: an image pinned to four map points (FMB sketch), drawn with a perspective matrix under the pins. */
private class SketchImageOverlay(val path: String) : Overlay() {
    var bitmap: Bitmap? = null
    var corners: List<Pair<Double, Double>> = emptyList()
    var alpha: Float = 0.6f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val matrix = android.graphics.Matrix()

    init {
        bitmap = runCatching {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, o)
            var sample = 1
            while (o.outWidth / sample > 2048 || o.outHeight / sample > 2048) sample *= 2
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull()
    }

    // Both draw entry points (osmdroid calls one or the other depending on the version / snapshot), drawn once.
    override fun draw(c: Canvas, osmv: MapView, shadow: Boolean) {
        if (!shadow) drawWith(c, osmv.projection)
    }

    override fun draw(c: Canvas, pj: org.osmdroid.views.Projection) = drawWith(c, pj)

    private fun drawWith(c: Canvas, pj: org.osmdroid.views.Projection) {
        val bmp = bitmap ?: return
        if (corners.size != 4) return
        val dst = FloatArray(8)
        val pt = Point()
        corners.forEachIndexed { i, (la, ln) ->
            pj.toPixels(GeoPoint(la, ln), pt)
            dst[i * 2] = pt.x.toFloat(); dst[i * 2 + 1] = pt.y.toFloat()
        }
        val w = bmp.width.toFloat(); val h = bmp.height.toFloat()
        val src = floatArrayOf(0f, 0f, w, 0f, w, h, 0f, h)
        if (!matrix.setPolyToPoly(src, 0, dst, 0, 4)) return
        paint.alpha = (alpha.coerceIn(0.05f, 1f) * 255).toInt()
        c.drawBitmap(bmp, matrix, paint)
    }
}

private val handleCache = HashMap<String, Drawable>()

/** 2.3.0: a small round handle (white with a coloured ring) for plot corner midpoints. */
private fun handleDrawable(context: Context, color: Long): Drawable {
    val key = "h$color"
    handleCache[key]?.let { return it }
    val d = context.resources.displayMetrics.density
    val size = (18 * d).toInt()
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val r = size / 2f
    c.drawCircle(r, r, r - 2 * d, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xEEFFFFFF.toInt() })
    c.drawCircle(r, r, r - 3 * d, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.toInt(); style = Paint.Style.STROKE; strokeWidth = 2.5f * d })
    return BitmapDrawable(context.resources, bmp).also { handleCache[key] = it }
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
    fitAroundCenter: Boolean,
    shapes: List<MapShape>,
    onPinDragging: ((String, Double, Double) -> Unit)?,
    sketch: MapSketch?,
    onViewport: ((MapViewport) -> Unit)?,
    flyTo: Triple<Double, Double, Double>?,
) {
    // Never place or fit anything at an invalid coordinate (NaN, out of range, or 0,0).
    @Suppress("NAME_SHADOWING")
    val pins = pins.filter { isValidCoord(it.lat, it.lng) }
    val context = LocalContext.current
    val holder = remember { MapHolder() }
    holder.onTap = onTap
    holder.onPinDragged = onPinDragged
    holder.onPinClick = onPinClick
    holder.onPinDragging = onPinDragging
    holder.pins = pins
    holder.onViewport = onViewport

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
                // 2.3.0: live pin dragging (above the tap overlay so a touch on a pin never adds a corner).
                overlays.add(PinDragOverlay(holder))
                // 2.4.0: the visible area for clustering / "N in view" (after the user stops panning or zooming).
                addMapListener(org.osmdroid.events.DelayedMapListener(object : org.osmdroid.events.MapListener {
                    override fun onScroll(event: org.osmdroid.events.ScrollEvent?): Boolean { reportViewport(this@apply, holder); return false }
                    override fun onZoom(event: org.osmdroid.events.ZoomEvent?): Boolean { reportViewport(this@apply, holder); return false }
                }, 250))
                holder.map = this
            }
        },
        update = { map ->
            if (holder.shapesSignature != shapes) {
                holder.shapesSignature = shapes
                map.overlays.removeAll { it is Polygon || it is Polyline }
                // Shapes go at the bottom, but above an FMB sketch image.
                val base = map.overlays.count { it is SketchImageOverlay }
                val density = map.context.resources.displayMetrics.density
                // Added at the bottom (index 0): drawn under the pins, and the tap overlay above them still gets every tap.
                shapes.asReversed().forEach { sh ->
                    val pts = sh.points.filter { isValidCoord(it.first, it.second) }.map { GeoPoint(it.first, it.second) }
                    if (pts.size < 2) return@forEach
                    if (sh.closed && pts.size >= 3) {
                        val poly = Polygon()
                        poly.setPoints(pts)
                        poly.fillPaint.color = sh.fill.toInt()
                        poly.outlinePaint.color = sh.stroke.toInt()
                        poly.outlinePaint.strokeWidth = sh.widthDp * density
                        poly.setOnClickListener { _, _, _ -> false }
                        map.overlays.add(base, poly)
                    } else {
                        val line = Polyline()
                        line.setPoints(pts)
                        line.outlinePaint.color = sh.stroke.toInt()
                        line.outlinePaint.strokeWidth = sh.widthDp * density
                        line.setOnClickListener { _, _, _ -> false }
                        map.overlays.add(base, line)
                    }
                }
            }
            if (holder.sketchSignature != sketch) {
                val old = map.overlays.filterIsInstance<SketchImageOverlay>().firstOrNull()
                if (sketch == null) map.overlays.removeAll { it is SketchImageOverlay }
                else {
                    val ov = old?.takeIf { it.path == sketch.path } ?: SketchImageOverlay(sketch.path).also { n ->
                        map.overlays.removeAll { it is SketchImageOverlay }
                        map.overlays.add(0, n)
                    }
                    ov.corners = sketch.corners
                    ov.alpha = sketch.alpha
                }
                holder.sketchSignature = sketch
            }
            if (holder.pinsSignature != pins) {
                holder.pinsSignature = pins
                map.overlays.removeAll { it is Marker }
                pins.forEach { pin ->
                    val m = Marker(map)
                    m.position = GeoPoint(pin.lat, pin.lng)
                    m.title = pin.title
                    m.snippet = pin.snippet
                    if (pin.handle) {
                        m.icon = handleDrawable(map.context, pin.color)
                        m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    } else if (pin.count > 1) {
                        m.icon = clusterDrawable(map.context, pin.count, pin.color)
                        m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    } else if (pin.label.isNotBlank()) {
                        m.icon = tagDrawable(map.context, pin.label, pin.color, pin.selected)
                        m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    } else {
                        m.icon = pinDrawable(map.context, pin.color, pin.small)
                        m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    }
                    // With live dragging the drag overlay moves the pin; osmdroid's long-press drag stays for the others.
                    m.isDraggable = pin.draggable && onPinDragging == null
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
            if (flyTo != null && holder.lastFly != flyTo) {
                holder.lastFly = flyTo
                map.controller.animateTo(GeoPoint(flyTo.first, flyTo.second), flyTo.third, 400L)
            }
            if (holder.lastKey != cameraKey) {
                holder.lastKey = cameraKey
                fun move() {
                    val density = map.context.resources.displayMetrics.density
                    val half = if (map.width > 0 && map.height > 0) minOf(map.width, map.height) / density / 2.0 - 28.0 else 70.0
                    val cam = if (fitPins) {
                        fitCamera(
                            pins.map { it.lat to it.lng },
                            anchorLat = if (fitAroundCenter) centerLat else null,
                            anchorLng = if (fitAroundCenter) centerLng else null,
                            halfExtentDp = half,
                            minZoom = if (fitAroundCenter) 9.0 else 5.0,
                            maxZoom = 16.0,
                        )
                    } else null
                    when {
                        cam != null -> {
                            map.controller.setZoom(cam.zoom)
                            map.controller.setCenter(GeoPoint(cam.lat, cam.lng))
                        }
                        isValidCoord(centerLat, centerLng) -> {
                            map.controller.setZoom(zoom)
                            map.controller.setCenter(GeoPoint(centerLat, centerLng))
                        }
                        pins.isNotEmpty() -> {
                            map.controller.setZoom(zoom)
                            map.controller.setCenter(GeoPoint(pins[0].lat, pins[0].lng))
                        }
                    }
                }
                if (map.isLayoutOccurred) { move(); reportViewport(map, holder) }
                else map.addOnFirstLayoutListener { _, _, _, _, _ -> move(); reportViewport(map, holder) }
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
