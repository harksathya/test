package com.hark.sothudiary.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hark.sothudiary.domain.pricing.isFresh
import com.hark.sothudiary.domain.DEFAULT_MAP_CENTER
import com.hark.sothudiary.domain.group
import com.hark.sothudiary.domain.priceShort
import com.hark.sothudiary.domain.pricing.Verdict
import com.hark.sothudiary.domain.pricing.checkAll
import com.hark.sothudiary.i18n.LocalLang
import com.hark.sothudiary.i18n.S
import com.hark.sothudiary.i18n.f
import com.hark.sothudiary.i18n.groupTr
import com.hark.sothudiary.i18n.s
import com.hark.sothudiary.i18n.shortTr
import com.hark.sothudiary.i18n.tr
import com.hark.sothudiary.ui.AppContext
import com.hark.sothudiary.ui.Route
import com.hark.sothudiary.ui.Tab
import com.hark.sothudiary.ui.components.AppTopBar
import com.hark.sothudiary.ui.components.Pill
import com.hark.sothudiary.ui.components.PropertyImage
import com.hark.sothudiary.ui.components.locationLine
import com.hark.sothudiary.ui.components.propertyImageModel
import com.hark.sothudiary.ui.components.unitPriceLine
import com.hark.sothudiary.ui.map.GeoMap
import com.hark.sothudiary.ui.map.MapPin
import com.hark.sothudiary.ui.map.isValidCoord
import com.hark.sothudiary.ui.theme.verdictArgb
import com.hark.sothudiary.ui.theme.verdictColor

/** All properties as pins coloured by price verdict; optional live-lookup rate places. */
@Composable
fun MapScreen(ctx: AppContext) {
    val data = ctx.repo.data
    val lang = LocalLang.current
    val checks = remember(data) { checkAll(data) }
    var selected by remember { mutableStateOf<String?>(null) }
    var showMarket by remember { mutableStateOf(false) }
    var groupFilter by remember { mutableStateOf<Int?>(null) }

    val withPins = data.properties.filter { isValidCoord(it.lat, it.lng) && (groupFilter == null || it.type.group() == groupFilter) }
    val noPin = data.properties.count { !isValidCoord(it.lat, it.lng) }
    val pins = buildList {
        if (showMarket) {
            // Rates from live lookups (cache, last 30 days) – no bundled data.
            data.onlineRates.filter { isFresh(it.fetchedAt) && (groupFilter == null || it.group == groupFilter) }.forEach { r ->
                val la = r.lat; val ln = r.lng
                if (la != null && ln != null && isValidCoord(la, ln)) add(MapPin("loc:" + r.place, la, ln, r.place, color = 0xFF78909C, small = true))
            }
        }
        withPins.forEach { p ->
            add(MapPin(p.id, p.lat ?: 0.0, p.lng ?: 0.0, p.title, color = verdictArgb(checks[p.id]?.verdict ?: Verdict.UNKNOWN)))
        }
    }

    Scaffold(
        topBar = { AppTopBar(S.tabMap.s(), ctx.toggleLang, subtitle = S.mapSubtitle.f(withPins.size, noPin)) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            GeoMap(
                modifier = Modifier.fillMaxSize(),
                pins = pins,
                centerLat = DEFAULT_MAP_CENTER.lat, centerLng = DEFAULT_MAP_CENTER.lng, zoom = if (withPins.isEmpty()) 6.5 else 11.5,
                cameraKey = withPins.map { it.id } + groupFilter,
                fitPins = withPins.isNotEmpty(),
                onPinClick = { id -> selected = id },
                onTap = { _, _ -> selected = null },
            )
            // Filter chips
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                MapChip(groupFilter == null, S.allTypes.s()) { groupFilter = null }
                (0..3).forEach { g -> MapChip(groupFilter == g, groupTr(g).s()) { groupFilter = if (groupFilter == g) null else g } }
                MapChip(showMarket, S.showMarketAreas.s()) { showMarket = !showMarket }
            }
            // Legend
            Surface(
                Modifier.align(Alignment.BottomStart).padding(10.dp),
                shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f), shadowElevation = 3.dp,
            ) {
                Column(Modifier.padding(8.dp)) {
                    listOf(Verdict.SUSPICIOUS_LOW, Verdict.GOOD, Verdict.FAIR, Verdict.HIGH, Verdict.TOO_HIGH, Verdict.UNKNOWN).forEach { v ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(9.dp).clip(CircleShape).background(verdictColor(v)))
                            Spacer(Modifier.width(5.dp))
                            Text(v.shortTr().s(), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            val sel = selected
            if (sel != null && sel.startsWith("loc:")) {
                val name = sel.removePrefix("loc:")
                Card(
                    Modifier.align(Alignment.BottomCenter).padding(12.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp), elevation = CardDefaults.cardElevation(6.dp),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.QueryStats, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Text(name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        TextButton(onClick = { ctx.nav.switchTab(Tab.AREA_RATES) }) { Text(S.seeAreaRates.s()) }
                        IconButton(onClick = { selected = null }) { Icon(Icons.Filled.Close, contentDescription = S.cancel.s()) }
                    }
                }
            } else {
                val p = sel?.let { id -> data.properties.firstOrNull { it.id == id } }
                if (p != null) {
                    val check = checks[p.id]
                    Card(
                        onClick = { ctx.nav.select(Route.PropertyDetail(p.id)) },
                        modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp).fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp), elevation = CardDefaults.cardElevation(6.dp),
                    ) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            PropertyImage(propertyImageModel(ctx.platform, p), p.type, Modifier.size(84.dp).clip(RoundedCornerShape(14.dp)))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(p.locationLine(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                Text(if (p.askingPrice > 0) priceShort(p.askingPrice, lang) else "-", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                                Text(unitPriceLine(p), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                if (check != null) Pill(check.verdict.shortTr().s(), verdictColor(check.verdict), filled = true)
                            }
                            IconButton(onClick = { selected = null }) { Icon(Icons.Filled.Close, contentDescription = S.cancel.s()) }
                        }
                    }
                }
            }
            if (withPins.isEmpty() && !showMarket) {
                Surface(
                    Modifier.align(Alignment.Center).padding(24.dp), shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f), shadowElevation = 4.dp,
                ) {
                    Text(S.mapEmpty.s(), modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun MapChip(selected: Boolean, label: String, onClick: () -> Unit) {
    FilterChip(
        selected = selected, onClick = onClick, label = { Text(label) }, shape = RoundedCornerShape(50),
        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}

@Suppress("unused")
private val keepTr = Color.Unspecified
