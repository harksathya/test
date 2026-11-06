package com.hark.sothudiary.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hark.sothudiary.data.AppData
import com.hark.sothudiary.data.DealStatus
import com.hark.sothudiary.data.Property
import com.hark.sothudiary.data.PropertyType
import com.hark.sothudiary.domain.parsePrice
import com.hark.sothudiary.domain.pricePerCent
import com.hark.sothudiary.domain.pricing.PriceCheck
import com.hark.sothudiary.domain.pricing.Verdict
import com.hark.sothudiary.domain.pricing.checkAll
import com.hark.sothudiary.i18n.S
import com.hark.sothudiary.i18n.f
import com.hark.sothudiary.i18n.s
import com.hark.sothudiary.i18n.shortTr
import com.hark.sothudiary.i18n.tr
import com.hark.sothudiary.ui.AppContext
import com.hark.sothudiary.ui.FilterState
import com.hark.sothudiary.ui.Route
import com.hark.sothudiary.ui.SortBy
import com.hark.sothudiary.ui.components.AppTopBar
import com.hark.sothudiary.ui.components.EmptyState
import com.hark.sothudiary.ui.components.MultiChips
import com.hark.sothudiary.ui.components.PropertyCard
import com.hark.sothudiary.ui.components.PrimaryButton
import com.hark.sothudiary.ui.components.bucket
import com.hark.sothudiary.ui.components.propertyImageModel
import com.hark.sothudiary.ui.components.SourceLogo
import com.hark.sothudiary.ui.components.ThumbStack
import com.hark.sothudiary.ui.GroupBy
import com.hark.sothudiary.ui.ListMode
import com.hark.sothudiary.data.SourcePlatform
import com.hark.sothudiary.domain.normalizeLocation
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material3.FilterChip
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow

fun nextFollowUp(propertyId: String, data: AppData): Long? =
    data.interactions.filter { it.propertyId == propertyId && !it.followUpDone && it.followUpAt != null }
        .mapNotNull { it.followUpAt }.minOrNull()

fun filterAndSort(data: AppData, checks: Map<String, PriceCheck>, f: FilterState): List<Property> {
    val q = f.query.trim().lowercase()
    val brokers = data.brokers.associateBy { it.id }
    val min = parsePrice(f.minPrice)
    val max = parsePrice(f.maxPrice)
    val filtered = data.properties.filter { p ->
        val broker = p.brokerId?.let { brokers[it] }
        val matchesQuery = q.isEmpty() || listOf(
            p.title, p.locality, p.taluk, p.district, p.notes, p.sourceCaption, broker?.name ?: "",
        ).any { it.lowercase().contains(q) }
        matchesQuery &&
            (f.types.isEmpty() || p.type in f.types) &&
            (f.statuses.isEmpty() || p.status in f.statuses) &&
            (f.verdicts.isEmpty() || checks[p.id]?.verdict?.let { it in f.verdicts } == true) &&
            (min == null || p.askingPrice >= min) &&
            (max == null || p.askingPrice <= max)
    }
    return when (f.sort) {
        SortBy.NEWEST -> filtered.sortedByDescending { it.createdAt }
        SortBy.PRICE_LOW -> filtered.sortedBy { it.askingPrice }
        SortBy.PRICE_HIGH -> filtered.sortedByDescending { it.askingPrice }
        SortBy.PER_CENT -> filtered.sortedBy { it.pricePerCent() ?: Double.MAX_VALUE }
        SortBy.FOLLOW_UP -> filtered.sortedBy { nextFollowUp(it.id, data) ?: Long.MAX_VALUE }
    }
}

@Composable
fun SortBy.label(): String = when (this) {
    SortBy.NEWEST -> S.sortNewest.s()
    SortBy.PRICE_LOW -> S.sortPriceLow.s()
    SortBy.PRICE_HIGH -> S.sortPriceHigh.s()
    SortBy.PER_CENT -> S.sortPerCent.s()
    SortBy.FOLLOW_UP -> S.sortFollowUp.s()
}

@Composable
fun GroupBy.label(): String = when (this) {
    GroupBy.NONE -> S.groupNone.s()
    GroupBy.SOURCE -> S.groupSource.s()
    GroupBy.LOCATION -> S.groupLocation.s()
    GroupBy.TYPE -> S.groupType.s()
    GroupBy.STATUS -> S.groupStatus.s()
}

/** Group key + display title for a property under a grouping. Keys are stable across languages. */
@Composable
fun groupOf(p: Property, g: GroupBy): Pair<String, String> = when (g) {
    GroupBy.NONE -> "" to ""
    GroupBy.SOURCE -> p.sourcePlatform.bucket().name to (if (p.sourcePlatform.bucket() == SourcePlatform.OTHER) S.othersSource.s() else p.sourcePlatform.tr().s())
    GroupBy.LOCATION -> {
        val loc = p.locality.ifBlank { p.taluk }.ifBlank { p.district }.trim()
        (if (loc.isBlank()) "~" else normalizeLocation(loc)) to loc.ifBlank { S.noLocation.s() }
    }
    GroupBy.TYPE -> p.type.name to p.type.tr().s()
    GroupBy.STATUS -> p.status.ordinal.toString() to p.status.tr().s()
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun PropertyListScreen(ctx: AppContext, selectedId: String?) {
    val data = ctx.repo.data
    val f = ctx.filters
    val checks = remember(data) { checkAll(data) }
    val list = filterAndSort(data, checks, f)
    val brokers = data.brokers.associateBy { it.id }
    var sortOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            AppTopBar(
                title = S.tabProperties.s(),
                subtitle = S.nResults.f(data.properties.size),
                onToggleLang = ctx.toggleLang,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { ctx.nav.select(Route.PropertyEdit(null)) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(S.addProperty.s()) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(18.dp),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    // List / Folders switch
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        ListMode.entries.forEachIndexed { i, m ->
                            SegmentedButton(
                                selected = f.mode == m,
                                onClick = { f.mode = m },
                                shape = SegmentedButtonDefaults.itemShape(i, ListMode.entries.size),
                                icon = { Icon(if (m == ListMode.LIST) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.Folder, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            ) { Text(if (m == ListMode.LIST) S.modeList.s() else S.modeFolders.s()) }
                        }
                    }
                    if (f.mode == ListMode.LIST) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = f.query,
                                onValueChange = { f.query = it },
                                placeholder = { Text(S.searchHint.s(), maxLines = 1) },
                                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                                trailingIcon = {
                                    if (f.query.isNotEmpty()) IconButton(onClick = { f.query = "" }) { Icon(Icons.Filled.Clear, contentDescription = S.clear.s()) }
                                },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(28.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            IconButton(onClick = { f.showFilters = !f.showFilters }) {
                                BadgedBox(badge = { if (f.activeCount() > 0) Badge { Text(f.activeCount().toString()) } }) {
                                    Icon(Icons.Filled.FilterList, contentDescription = S.filters.s(), tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Box {
                                IconButton(onClick = { sortOpen = true }) {
                                    Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = S.sort.s(), tint = MaterialTheme.colorScheme.primary)
                                }
                                DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                                    SortBy.entries.forEach { s ->
                                        DropdownMenuItem(
                                            text = { Text(s.label(), fontWeight = if (s == f.sort) FontWeight.Bold else FontWeight.Normal) },
                                            onClick = { f.sort = s; sortOpen = false },
                                        )
                                    }
                                }
                            }
                        }
                        if (f.showFilters) FilterPanel(f)
                        // Group-by chips
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Workspaces, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                            GroupBy.entries.forEach { g ->
                                FilterChip(
                                    selected = f.group == g,
                                    onClick = { f.group = g },
                                    label = { Text(g.label()) },
                                    shape = RoundedCornerShape(50),
                                )
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(S.nResults.f(list.size), style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            Text(S.sortedBy.f(f.sort.label()), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            if (f.mode == ListMode.FOLDERS) {
                Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
                    Column {
                        SourcesAndFolders(ctx, checks)
                        Spacer(Modifier.height(96.dp))
                    }
                }
                return@Column
            }
            if (list.isEmpty()) {
                EmptyState(
                    Icons.Filled.HomeWork,
                    if (data.properties.isEmpty()) S.noPropertiesYet.s() else S.noMatches.s(),
                    if (data.properties.isEmpty()) S.noPropertiesBody.s() else S.noMatchesBody.s(),
                ) {
                    if (data.properties.isNotEmpty()) TextButton(onClick = { f.clear(); f.query = "" }) { Text(S.clearFilters.s()) }
                    else PrimaryButton(S.addProperty.s(), { ctx.nav.select(Route.PropertyEdit(null)) }, icon = Icons.Filled.Add)
                }
            } else {
                val groups: List<Triple<String, String, List<Property>>> = if (f.group == GroupBy.NONE) listOf(Triple("", "", list)) else {
                    val keyed = list.map { p -> groupOf(p, f.group) to p }
                    keyed.groupBy({ it.first.first }, { it }).map { (k, v) -> Triple(k, v.first().first.second, v.map { it.second }) }
                        .sortedWith(compareBy({ if (f.group == GroupBy.LOCATION && it.first == "~") 1 else 0 }, { if (f.group == GroupBy.STATUS) it.first else it.second.lowercase() }))
                }
                LazyColumn(
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    groups.forEach { (key, title, items) ->
                        if (f.group != GroupBy.NONE) {
                            stickyHeader(key = "h_$key") {
                                GroupHeader(
                                    title, items.size, collapsed = !f.isExpanded(key),
                                    source = if (f.group == GroupBy.SOURCE) items.first().sourcePlatform else null,
                                    thumbs = items.mapNotNull { propertyImageModel(ctx.platform, it) }.take(3),
                                ) { f.toggleGroup(key) }
                            }
                        }
                        if (f.group == GroupBy.NONE || f.isExpanded(key)) {
                            items(items, key = { "p_" + it.id }) { p ->
                                Box(Modifier.animateItem()) {
                                    PropertyCard(
                                        p = p,
                                        broker = p.brokerId?.let { brokers[it] },
                                        check = checks[p.id],
                                        nextFollowUp = nextFollowUp(p.id, data),
                                        selected = ctx.wide && p.id == selectedId,
                                        image = propertyImageModel(ctx.platform, p),
                                        onClick = { ctx.nav.select(Route.PropertyDetail(p.id)) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(title: String, count: Int, collapsed: Boolean, source: SourcePlatform?, thumbs: List<String>, onToggle: () -> Unit) {
    Surface(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = if (com.hark.sothudiary.ui.theme.LocalBrand.current.dark) 0.dp else 1.dp,
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                if (source != null) SourceLogo(source, 18.dp)
                else Icon(Icons.Filled.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(com.hark.sothudiary.i18n.propertiesCount(count), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (collapsed) ThumbStack(thumbs, size = 26.dp)
            Spacer(Modifier.width(6.dp))
            Icon(if (collapsed) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FilterPanel(f: FilterState) {
    Column(
        Modifier.padding(top = 8.dp).heightIn(max = 340.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(S.type.s(), style = MaterialTheme.typography.labelLarge)
        MultiChips(PropertyType.entries, f.types, { it.tr().s() }) { t -> f.types = if (t in f.types) f.types - t else f.types + t }
        Text(S.status.s(), style = MaterialTheme.typography.labelLarge)
        MultiChips(DealStatus.entries, f.statuses, { it.tr().s() }) { t -> f.statuses = if (t in f.statuses) f.statuses - t else f.statuses + t }
        Text(S.priceVerdict.s(), style = MaterialTheme.typography.labelLarge)
        MultiChips(Verdict.entries, f.verdicts, { it.shortTr().s() }) { t -> f.verdicts = if (t in f.verdicts) f.verdicts - t else f.verdicts + t }
        Text(S.priceRange.s(), style = MaterialTheme.typography.labelLarge)
        Row {
            OutlinedTextField(
                value = f.minPrice, onValueChange = { f.minPrice = it }, label = { Text(S.minPrice.s()) },
                singleLine = true, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            )
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = f.maxPrice, onValueChange = { f.maxPrice = it }, label = { Text(S.maxPrice.s()) },
                singleLine = true, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            )
        }
        Text(S.priceInputHint.s(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row {
            TextButton(onClick = { f.clear() }) { Text(S.clearFilters.s()) }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { f.showFilters = false }) { Text(S.done.s()) }
        }
        HorizontalDivider()
        Spacer(Modifier.height(2.dp))
    }
}

