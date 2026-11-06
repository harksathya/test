package com.hark.sothudiary.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import com.hark.sothudiary.data.Folder
import com.hark.sothudiary.data.Property
import com.hark.sothudiary.data.SourcePlatform
import com.hark.sothudiary.data.newId
import com.hark.sothudiary.domain.priceShort
import com.hark.sothudiary.domain.pricing.PriceCheck
import com.hark.sothudiary.domain.pricing.checkAll
import com.hark.sothudiary.i18n.LocalLang
import com.hark.sothudiary.i18n.S
import com.hark.sothudiary.i18n.f
import com.hark.sothudiary.i18n.s
import com.hark.sothudiary.i18n.tr
import com.hark.sothudiary.ui.AppContext
import com.hark.sothudiary.ui.Route
import com.hark.sothudiary.ui.components.AppTopBar
import com.hark.sothudiary.ui.components.ConfirmDialog
import com.hark.sothudiary.ui.components.EmptyState
import com.hark.sothudiary.ui.components.FOLDER_COLORS
import com.hark.sothudiary.ui.components.Field
import com.hark.sothudiary.ui.components.FolderTile
import com.hark.sothudiary.ui.components.PropertyCard
import com.hark.sothudiary.ui.components.PropertyImage
import com.hark.sothudiary.ui.components.bucket
import com.hark.sothudiary.ui.components.FolderGlyph
import com.hark.sothudiary.ui.components.SourceLogo
import com.hark.sothudiary.ui.components.ThumbStack
import com.hark.sothudiary.ui.components.propertyImageModel

val SOURCE_BUCKETS = listOf(SourcePlatform.YOUTUBE, SourcePlatform.INSTAGRAM, SourcePlatform.FACEBOOK, SourcePlatform.WHATSAPP, SourcePlatform.OTHER)

@Composable
fun sourceName(p: SourcePlatform): String = if (p.bucket() == SourcePlatform.OTHER) S.othersSource.s() else p.tr().s()

/** Compact grid (2 columns, 3 when wide) of fixed-width cells. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CompactGrid(wide: Boolean, content: @Composable (Modifier) -> Unit) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cols = if (wide || maxWidth > 600.dp) 3 else 2
        val gap = 10.dp
        val cell = (maxWidth - gap * (cols - 1)) / cols
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap), verticalArrangement = Arrangement.spacedBy(gap)) {
            content(Modifier.width(cell))
        }
    }
}

/** Compact source tiles (real brand logos) for YouTube, Instagram, … */
@Composable
fun SourceTiles(ctx: AppContext) {
    val data = ctx.repo.data
    CompactGrid(ctx.wide) { cell ->
        SOURCE_BUCKETS.forEach { src ->
            val props = data.properties.filter { it.sourcePlatform.bucket() == src }.sortedByDescending { it.createdAt }
            FolderTile(
                title = sourceName(src), count = props.size,
                thumbs = props.mapNotNull { propertyImageModel(ctx.platform, it) }.take(3),
                onClick = { ctx.nav.select(Route.FolderView(source = src.name)) },
                modifier = cell,
            ) { SourceLogo(src, 20.dp) }
        }
    }
}

/** Source tiles followed by the user's own folders – all compact. */
@Composable
fun SourcesAndFolders(ctx: AppContext, checks: Map<String, PriceCheck>, showFolders: Boolean = true) {
    val data = ctx.repo.data
    var creating by remember { mutableStateOf(false) }
    Text(S.bySource.s(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp, top = 4.dp))
    SourceTiles(ctx)
    if (!showFolders) return
    Spacer(Modifier.height(18.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(S.myFolders.s(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        TextButton(onClick = { creating = true }) {
            Icon(Icons.Filled.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(S.newFolder.s())
        }
    }
    Spacer(Modifier.height(4.dp))
    CompactGrid(ctx.wide) { cell ->
        data.folders.forEach { folder ->
            val props = data.properties.filter { folder.id in it.folderIds }.sortedByDescending { it.createdAt }
            FolderTile(
                title = folder.name, count = props.size,
                thumbs = props.mapNotNull { propertyImageModel(ctx.platform, it) }.take(3),
                onClick = { ctx.nav.select(Route.FolderView(folderId = folder.id)) },
                modifier = cell,
            ) { FolderGlyph(folder.color) }
        }
        // "New folder" tile
        Row(
            cell.height(64.dp).clip(RoundedCornerShape(16.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                .clickable { creating = true }.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.CreateNewFolder, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(S.newFolder.s(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text(S.newFolderHint.s(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    if (creating) {
        FolderDialog(null, onDismiss = { creating = false }) { name, color ->
            ctx.repo.upsertFolder(Folder(newId(), name, color))
            creating = false
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FolderDialog(existing: Folder?, onDismiss: () -> Unit, onSave: (String, Long) -> Unit) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var color by remember { mutableStateOf(existing?.color ?: FOLDER_COLORS.first()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) S.newFolder.s() else S.renameFolder.s()) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Field(name, { name = it }, S.folderName.s(), supporting = S.folderNameHint.s())
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FOLDER_COLORS.forEach { c ->
                        Box(
                            Modifier.size(32.dp).clip(CircleShape).background(Color(c))
                                .border(if (c == color) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                .clickable { color = c },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name.trim(), color) }, enabled = name.isNotBlank()) { Text(S.save.s()) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(S.cancel.s()) } },
    )
}

/** Properties of one source or one user folder. */
@Composable
fun FolderViewScreen(ctx: AppContext, route: Route.FolderView) {
    val data = ctx.repo.data
    val checks = remember(data) { checkAll(data) }
    val brokers = data.brokers.associateBy { it.id }
    val folder = route.folderId?.let { id -> data.folders.firstOrNull { it.id == id } }
    val source = route.source?.let { n -> SourcePlatform.entries.firstOrNull { it.name == n } }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }

    val props: List<Property> = when {
        folder != null -> data.properties.filter { folder.id in it.folderIds }
        source != null -> data.properties.filter { it.sourcePlatform.bucket() == source }
        else -> emptyList()
    }.sortedByDescending { it.createdAt }
    val title = folder?.name ?: source?.let { sourceName(it) } ?: S.notFound.s()

    Scaffold(
        topBar = {
            AppTopBar(
                title, ctx.toggleLang, onBack = { ctx.nav.pop() },
                actions = {
                    if (folder != null) {
                        IconButton(onClick = { renaming = true }) { Icon(Icons.Filled.Edit, contentDescription = S.renameFolder.s()) }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = S.delete.s()) }
                    }
                },
            )
        },
        floatingActionButton = {
            if (folder != null) {
                ExtendedFloatingActionButton(
                    onClick = { adding = true },
                    icon = { Icon(Icons.Filled.PlaylistAdd, contentDescription = null) },
                    text = { Text(S.addToFolder.s()) },
                    shape = RoundedCornerShape(18.dp),
                )
            } else {
                ExtendedFloatingActionButton(
                    onClick = { ctx.nav.push(Route.PropertyEdit(null)) },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(S.addProperty.s()) },
                    shape = RoundedCornerShape(18.dp),
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Calm header: logo / folder glyph, name, count and total
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                    if (folder != null) FolderGlyph(folder.color, 26.dp) else SourceLogo(source ?: SourcePlatform.OTHER, 26.dp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    val total = props.sumOf { it.askingPrice }
                    Text(com.hark.sothudiary.i18n.propertiesCount(props.size) + if (total > 0) " • " + priceShort(total, LocalLang.current) else "",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                }
                ThumbStack(props.mapNotNull { propertyImageModel(ctx.platform, it) }.take(3), size = 30.dp)
            }
            androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (props.isEmpty()) {
                EmptyState(Icons.Filled.FolderOpen, S.folderEmpty.s(), if (folder != null) S.folderEmptyBody.s() else S.sourceEmptyBody.s())
            } else {
                LazyColumn(contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(props, key = { it.id }) { p ->
                        PropertyCard(
                            p, p.brokerId?.let { brokers[it] }, checks[p.id], nextFollowUp(p.id, data), selected = false,
                            image = propertyImageModel(ctx.platform, p),
                        ) { ctx.nav.push(Route.PropertyDetail(p.id)) }
                    }
                }
            }
        }
    }

    if (renaming && folder != null) {
        FolderDialog(folder, onDismiss = { renaming = false }) { name, color ->
            ctx.repo.upsertFolder(folder.copy(name = name, color = color)); renaming = false
        }
    }
    if (confirmDelete && folder != null) {
        ConfirmDialog(
            S.deleteFolderQ.s(), S.deleteFolderBody.s(), S.delete.s(), destructive = true,
            onConfirm = { confirmDelete = false; ctx.repo.deleteFolder(folder.id); ctx.nav.pop() },
            onDismiss = { confirmDelete = false },
        )
    }
    if (adding && folder != null) {
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text(S.addToFolderTitle.f(folder.name)) },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(data.properties.sortedByDescending { it.createdAt }, key = { it.id }) { p ->
                        val inIt = folder.id in p.folderIds
                        Row(
                            Modifier.fillMaxWidth().clickable { ctx.repo.setInFolder(p.id, folder.id, !inIt) }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = inIt, onCheckedChange = { ctx.repo.setInFolder(p.id, folder.id, it) })
                            PropertyImage(propertyImageModel(ctx.platform, p), p.type, Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)))
                            Spacer(Modifier.width(8.dp))
                            Text(p.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { adding = false }) { Text(S.done.s()) } },
        )
    }
}

/** Folder membership chips on the property detail screen (tap to add / remove; long list is fine). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FolderChips(ctx: AppContext, p: Property) {
    val data = ctx.repo.data
    var creating by remember { mutableStateOf(false) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        data.folders.forEach { f ->
            val inIt = f.id in p.folderIds
            androidx.compose.material3.FilterChip(
                selected = inIt,
                onClick = { ctx.repo.setInFolder(p.id, f.id, !inIt) },
                label = { Text(f.name) },
                leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(Color(f.color))) },
                shape = RoundedCornerShape(50),
            )
        }
        androidx.compose.material3.AssistChip(
            onClick = { creating = true },
            label = { Text(S.newFolder.s()) },
            leadingIcon = { Icon(Icons.Filled.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp)) },
            shape = RoundedCornerShape(50),
        )
    }
    if (creating) {
        FolderDialog(null, onDismiss = { creating = false }) { name, color ->
            val f = Folder(newId(), name, color)
            ctx.repo.upsertFolder(f)
            ctx.repo.setInFolder(p.id, f.id, true)
            creating = false
        }
    }
}
