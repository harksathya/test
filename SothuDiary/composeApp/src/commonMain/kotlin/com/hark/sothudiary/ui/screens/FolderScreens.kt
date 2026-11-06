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
import com.hark.sothudiary.ui.components.folderBrush
import com.hark.sothudiary.ui.components.propertyImageModel
import com.hark.sothudiary.ui.components.sourceBrush
import com.hark.sothudiary.ui.components.sourceIcon

val SOURCE_BUCKETS = listOf(SourcePlatform.YOUTUBE, SourcePlatform.INSTAGRAM, SourcePlatform.FACEBOOK, SourcePlatform.WHATSAPP, SourcePlatform.OTHER)

@Composable
fun sourceName(p: SourcePlatform): String = if (p.bucket() == SourcePlatform.OTHER) S.othersSource.s() else p.tr().s()

/** Grid of source tiles (YouTube, Instagram, …) followed by the user's own folders. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SourcesAndFolders(ctx: AppContext, checks: Map<String, PriceCheck>, showFolders: Boolean = true) {
    val data = ctx.repo.data
    var creating by remember { mutableStateOf(false) }
    val tileWidth = if (ctx.wide) 180.dp else 160.dp
    Text(S.bySource.s(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp, top = 4.dp))
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SOURCE_BUCKETS.forEach { src ->
            val props = data.properties.filter { it.sourcePlatform.bucket() == src }.sortedByDescending { it.createdAt }
            FolderTile(
                title = sourceName(src), count = props.size, brush = sourceBrush(src), icon = sourceIcon(src),
                thumbs = props.take(3).map { propertyImageModel(ctx.platform, it) },
                onClick = { ctx.nav.select(Route.FolderView(source = src.name)) },
                modifier = Modifier.width(tileWidth),
            )
        }
    }
    if (!showFolders) return
    Spacer(Modifier.height(20.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(S.myFolders.s(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        TextButton(onClick = { creating = true }) {
            Icon(Icons.Filled.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(S.newFolder.s())
        }
    }
    Spacer(Modifier.height(8.dp))
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        data.folders.forEach { folder ->
            val props = data.properties.filter { folder.id in it.folderIds }.sortedByDescending { it.createdAt }
            FolderTile(
                title = folder.name, count = props.size, brush = folderBrush(folder.color), icon = Icons.Filled.Folder,
                thumbs = props.take(3).map { propertyImageModel(ctx.platform, it) },
                onClick = { ctx.nav.select(Route.FolderView(folderId = folder.id)) },
                modifier = Modifier.width(tileWidth),
            )
        }
        // "New folder" tile
        Box(
            Modifier.width(tileWidth).aspectRatio(1.05f).clip(RoundedCornerShape(20.dp))
                .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                .clickable { creating = true },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.CreateNewFolder, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                Spacer(Modifier.height(6.dp))
                Text(S.newFolder.s(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                Text(S.newFolderHint.s(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp), maxLines = 2)
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
                title, ctx.toggleLang, onBack = { ctx.nav.pop() }, subtitle = S.nResults.f(props.size),
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
            // Hero band in the folder / brand colour
            Box(
                Modifier.fillMaxWidth().height(86.dp)
                    .background(folder?.let { folderBrush(it.color) } ?: source?.let { sourceBrush(it) } ?: folderBrush(0xFF0B5CAD)),
            ) {
                Row(Modifier.align(Alignment.CenterStart).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (folder != null) Icons.Filled.FolderOpen else sourceIcon(source ?: SourcePlatform.OTHER), contentDescription = null,
                        tint = Color.White, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(title, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        val total = props.sumOf { it.askingPrice }
                        Text(S.nResults.f(props.size) + if (total > 0) " • " + priceShort(total, LocalLang.current) else "",
                            color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
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
