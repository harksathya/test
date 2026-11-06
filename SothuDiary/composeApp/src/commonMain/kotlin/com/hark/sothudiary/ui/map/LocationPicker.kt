package com.hark.sothudiary.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditLocationAlt
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.zIndex
import com.hark.sothudiary.ui.FullScreenOverlay
import com.hark.sothudiary.PlatformActions
import com.hark.sothudiary.domain.DEFAULT_MAP_CENTER
import com.hark.sothudiary.domain.GeoPlace
import com.hark.sothudiary.domain.Geocoder
import com.hark.sothudiary.domain.dec
import com.hark.sothudiary.domain.parseDouble
import com.hark.sothudiary.i18n.S
import com.hark.sothudiary.i18n.s
import com.hark.sothudiary.i18n.f
import com.hark.sothudiary.ui.theme.LocalBrand
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What the picker returns: the point plus area / taluk / district from reverse geocoding (may be blank offline). */
data class PickedLocation(
    val lat: Double,
    val lng: Double,
    val locality: String = "",
    val taluk: String = "",
    val district: String = "",
    val address: String = "",
)

fun GeoPlace.toPicked() = PickedLocation(lat, lng, locality, taluk, district, displayName)

/**
 * Full-screen location picker: search (Nominatim), "use my current location", tap / drag the pin,
 * reverse-geocoded address, and manual latitude / longitude entry (always shown on desktop).
 */
@Composable
fun LocationPickerDialog(
    platform: PlatformActions,
    initial: PickedLocation?,
    initialQuery: String,
    onDismiss: () -> Unit,
    onPicked: (PickedLocation) -> Unit,
) {
    // Drawn in the activity window (not a Dialog window): the search field always stays on top of the
    // map view and the layout resizes for the keyboard.
    FullScreenOverlay {
        com.hark.sothudiary.PlatformBackHandler(enabled = true) { onDismiss() }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            LocationPickerContent(platform, initial, initialQuery, onDismiss, onPicked)
        }
    }
}

@Composable
private fun LocationPickerContent(
    platform: PlatformActions,
    initial: PickedLocation?,
    initialQuery: String,
    onDismiss: () -> Unit,
    onPicked: (PickedLocation) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var picked by remember { mutableStateOf(initial) }
    var query by remember { mutableStateOf(if (initial == null) initialQuery else "") }
    var results by remember { mutableStateOf<List<GeoPlace>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searchedFor by remember { mutableStateOf("") }
    var showResults by remember { mutableStateOf(initial == null && initialQuery.isNotBlank()) }
    var locating by remember { mutableStateOf(false) }
    var resolving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var cameraKey by remember { mutableIntStateOf(0) }
    var manual by remember { mutableStateOf(!platformHasMap) }
    var latText by remember { mutableStateOf(initial?.lat?.let { dec(it, 6) } ?: "") }
    var lngText by remember { mutableStateOf(initial?.lng?.let { dec(it, 6) } ?: "") }
    val msgNoResults = S.noPlacesFound.s()
    val msgOffline = S.searchOffline.s()
    val msgNoLocation = S.locationUnavailable.s()

    // Debounced search: waits for typing to pause; Geocoder itself keeps >= 1 s between requests.
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 3 || !showResults) return@LaunchedEffect
        delay(700)
        searching = true
        val r = Geocoder.search(q, nearLat = picked?.lat ?: DEFAULT_MAP_CENTER.lat, nearLng = picked?.lng ?: DEFAULT_MAP_CENTER.lng)
        searching = false
        searchedFor = q
        results = r
        message = if (r.isEmpty()) msgNoResults else null
    }

    fun movePin(lat: Double, lng: Double, recenter: Boolean, known: GeoPlace? = null) {
        picked = known?.toPicked() ?: PickedLocation(lat, lng, picked?.locality ?: "", picked?.taluk ?: "", picked?.district ?: "", "")
        latText = dec(lat, 6)
        lngText = dec(lng, 6)
        if (recenter) cameraKey++
        if (known == null) {
            // Reverse geocode to fill area / taluk / district.
            scope.launch {
                resolving = true
                val place = Geocoder.reverse(lat, lng)
                resolving = false
                val cur = picked
                if (cur != null && cur.lat == lat && cur.lng == lng) {
                    picked = if (place != null) cur.copy(locality = place.locality, taluk = place.taluk, district = place.district, address = place.displayName)
                    else cur.copy(address = "")
                    if (place == null) message = msgOffline
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        // Top bar
        Row(
            Modifier.fillMaxWidth().background(LocalBrand.current.bar).padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = S.cancel.s(), tint = LocalBrand.current.onBar) }
            Text(S.pickLocation.s(), color = LocalBrand.current.onBar, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = { picked?.let(onPicked) }, enabled = picked != null) {
                Text(S.useThisLocation.s(), color = LocalBrand.current.onBar.copy(alpha = if (picked != null) 1f else 0.5f), fontWeight = FontWeight.Bold)
            }
        }
        // Search – always above the map (own z-layer, solid background); results open below it, pushing the map down.
        Column(Modifier.zIndex(2f).background(MaterialTheme.colorScheme.surface).padding(horizontal = 12.dp, vertical = 8.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; showResults = true; message = null },
                placeholder = { Text(S.searchPlaceHint.s()) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    when {
                        searching -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        query.isNotEmpty() -> IconButton(onClick = { query = ""; results = emptyList() }) { Icon(Icons.Filled.Clear, contentDescription = S.clear.s()) }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    scope.launch {
                        searching = true
                        results = Geocoder.search(query, nearLat = picked?.lat, nearLng = picked?.lng)
                        searching = false
                        showResults = true
                        message = if (results.isEmpty()) msgNoResults else null
                    }
                }),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (platform.location.supported) {
                    OutlinedButton(
                        onClick = {
                            locating = true
                            message = null
                            platform.location.currentLocation { ll ->
                                locating = false
                                if (ll == null) message = msgNoLocation else { showResults = false; movePin(ll.lat, ll.lng, recenter = true) }
                            }
                        },
                        enabled = !locating,
                    ) {
                        if (locating) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (locating) S.locating.s() else S.useMyLocation.s())
                    }
                }
                if (platformHasMap) {
                    TextButton(onClick = { manual = !manual }) {
                        Icon(Icons.Filled.EditLocationAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(S.enterCoordinates.s())
                    }
                }
            }
            if (manual) {
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(latText, { latText = it }, label = { Text(S.latitudeShort.s()) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(6.dp))
                    OutlinedTextField(lngText, { lngText = it }, label = { Text(S.longitudeShort.s()) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(6.dp))
                    val la = parseDouble(latText)
                    val ln = parseDouble(lngText)
                    val valid = la != null && ln != null && la in -90.0..90.0 && ln in -180.0..180.0
                    Button(onClick = { if (la != null && ln != null) { showResults = false; movePin(la, ln, recenter = true) } }, enabled = valid) {
                        Text(S.ok.s())
                    }
                }
            }
            message?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            }
            if (showResults && (results.isNotEmpty() || searching)) {
                Surface(
                    Modifier.fillMaxWidth().padding(top = 6.dp).heightIn(max = 300.dp),
                    shape = MaterialTheme.shapes.medium,
                    shadowElevation = 4.dp,
                    tonalElevation = 2.dp,
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Column {
                        Text(
                            if (searching && results.isEmpty()) S.searching.s() else S.searchResults.f(results.size),
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                        LazyColumn {
                            items(results) { r ->
                                Row(
                                    Modifier.fillMaxWidth().clickable {
                                        showResults = false
                                        query = r.locality.ifBlank { r.displayName.substringBefore(',') }
                                        movePin(r.lat, r.lng, recenter = true, known = r)
                                    }.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Filled.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(r.displayName.substringBefore(','), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(r.displayName, style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }
        }
        HorizontalDivider()
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
            val p = picked?.takeIf { isValidCoord(it.lat, it.lng) }
            val center = p?.let { it.lat to it.lng } ?: (DEFAULT_MAP_CENTER.lat to DEFAULT_MAP_CENTER.lng)
            GeoMap(
                modifier = Modifier.fillMaxSize(),
                pins = if (p != null) listOf(MapPin("picked", p.lat, p.lng, p.locality.ifBlank { S.selectedPoint.s() }, color = 0xFFD32F2F, draggable = true)) else emptyList(),
                centerLat = center.first,
                centerLng = center.second,
                zoom = if (p != null) 16.0 else 7.0,
                cameraKey = cameraKey,
                onTap = { lat, lng -> showResults = false; movePin(lat, lng, recenter = false) },
                onPinDragged = { _, lat, lng -> movePin(lat, lng, recenter = false) },
            )
            if (p == null) {
                Surface(
                    Modifier.align(Alignment.TopCenter).padding(12.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    shadowElevation = 3.dp,
                ) {
                    Text(S.pickHint.s(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(10.dp))
                }
            }
        }
        // Bottom sheet with the address
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                val p = picked
                if (p == null) {
                    Text(S.noPointYet.s(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Place, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            listOf(p.locality, p.taluk, p.district).filter { it.isNotBlank() }.distinct().joinToString(", ").ifBlank { S.selectedPoint.s() },
                            fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
                        )
                        if (resolving) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    }
                    if (p.address.isNotBlank()) {
                        Text(p.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Text(dec(p.lat, 6) + ", " + dec(p.lng, 6), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (platformHasMap) Text(S.dragHint.s(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = { picked?.let(onPicked) }, enabled = picked != null, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(S.useThisLocation.s())
                }
            }
        }
    }
}
