package com.hark.sothudiary.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Facebook
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.SquareFoot
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.hark.sothudiary.PlatformActions
import com.hark.sothudiary.data.Broker
import com.hark.sothudiary.data.Property
import com.hark.sothudiary.data.PropertyType
import com.hark.sothudiary.data.SourcePlatform
import com.hark.sothudiary.domain.formatDateShort
import com.hark.sothudiary.domain.priceShort
import com.hark.sothudiary.domain.pricing.PriceCheck
import com.hark.sothudiary.domain.pricing.Verdict
import com.hark.sothudiary.domain.youtubeThumb
import com.hark.sothudiary.i18n.LocalLang
import com.hark.sothudiary.i18n.S
import com.hark.sothudiary.i18n.s
import com.hark.sothudiary.i18n.shortTr
import com.hark.sothudiary.i18n.tr
import com.hark.sothudiary.ui.theme.statusColor
import com.hark.sothudiary.ui.theme.verdictColor

val CardShape = RoundedCornerShape(20.dp)

/** The best picture for a property: its own photo, then the link preview, then a YouTube thumbnail. */
fun propertyImageModel(platform: PlatformActions, p: Property): String? =
    platform.imagePath(p.imageFile) ?: p.imageUrl.ifBlank { null } ?: youtubeThumb(p.sourceUrl)

fun typeIcon(t: PropertyType): ImageVector = when (t) {
    PropertyType.LAND -> Icons.Filled.Terrain
    PropertyType.PLOT -> Icons.Filled.Landscape
    PropertyType.HOUSE -> Icons.Filled.Home
    PropertyType.APARTMENT -> Icons.Filled.Apartment
    PropertyType.FARMLAND, PropertyType.AGRI_LAND -> Icons.Filled.Agriculture
}

/** "Others" groups OTHER and NONE together. */
fun SourcePlatform.bucket(): SourcePlatform = if (this == SourcePlatform.NONE) SourcePlatform.OTHER else this

fun sourceBrush(p: SourcePlatform): Brush = when (p.bucket()) {
    SourcePlatform.YOUTUBE -> Brush.linearGradient(listOf(Color(0xFFFF4E45), Color(0xFFB80F0A)))
    SourcePlatform.INSTAGRAM -> Brush.linearGradient(listOf(Color(0xFFFEDA75), Color(0xFFFA7E1E), Color(0xFFD62976), Color(0xFF962FBF), Color(0xFF4F5BD5)))
    SourcePlatform.FACEBOOK -> Brush.linearGradient(listOf(Color(0xFF3B8BFF), Color(0xFF0D56C9)))
    SourcePlatform.WHATSAPP -> Brush.linearGradient(listOf(Color(0xFF4AE07A), Color(0xFF128C4A)))
    else -> Brush.linearGradient(listOf(Color(0xFF5B7286), Color(0xFF2C3E50)))
}

fun sourceIcon(p: SourcePlatform): ImageVector = when (p.bucket()) {
    SourcePlatform.YOUTUBE -> Icons.Filled.PlayCircle
    SourcePlatform.INSTAGRAM -> Icons.Filled.PhotoCamera
    SourcePlatform.FACEBOOK -> Icons.Filled.Facebook
    SourcePlatform.WHATSAPP -> Icons.Filled.Chat
    else -> Icons.Filled.Link
}

fun typeBrush(t: PropertyType): Brush = when (t) {
    PropertyType.HOUSE -> Brush.linearGradient(listOf(Color(0xFF4F8DF5), Color(0xFF1F4FA8)))
    PropertyType.APARTMENT -> Brush.linearGradient(listOf(Color(0xFF7C6CF2), Color(0xFF3D2F9E)))
    PropertyType.FARMLAND, PropertyType.AGRI_LAND -> Brush.linearGradient(listOf(Color(0xFF6CC56F), Color(0xFF1E7A3A)))
    else -> Brush.linearGradient(listOf(Color(0xFF26B5A5), Color(0xFF0B6E7F)))
}

/** Photo with a gradient + type icon placeholder underneath (shown while loading or when there is no photo). */
@Composable
fun PropertyImage(model: String?, type: PropertyType, modifier: Modifier = Modifier, overlay: @Composable BoxScope.() -> Unit = {}) {
    Box(modifier.background(typeBrush(type))) {
        Icon(typeIcon(type), contentDescription = null, tint = Color.White.copy(alpha = 0.35f), modifier = Modifier.size(64.dp).align(Alignment.Center))
        if (model != null) {
            AsyncImage(model = model, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        overlay()
    }
}

/** Big, image-led property card. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PropertyCard(
    p: Property,
    broker: Broker?,
    check: PriceCheck?,
    nextFollowUp: Long?,
    selected: Boolean,
    image: String?,
    onClick: () -> Unit,
) {
    val lang = LocalLang.current
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        PropertyImage(image, p.type, Modifier.fillMaxWidth().height(158.dp)) {
            // dark fade at the bottom for the price
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.72f))))
            Row(Modifier.align(Alignment.TopStart).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GlassPill(p.type.tr().s())
                if (check != null && check.verdict != Verdict.UNKNOWN) Pill(check.verdict.shortTr().s(), verdictColor(check.verdict), filled = true)
            }
            if (p.sourcePlatform != SourcePlatform.NONE) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(10.dp).size(30.dp).clip(CircleShape).background(sourceBrush(p.sourcePlatform)),
                    contentAlignment = Alignment.Center,
                ) { Icon(sourceIcon(p.sourcePlatform), contentDescription = p.sourcePlatform.tr().s(), tint = Color.White, modifier = Modifier.size(18.dp)) }
            }
            Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    if (p.askingPrice > 0) priceShort(p.askingPrice, lang) else S.priceOnRequest.s(),
                    color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold,
                )
                Text(unitPriceLine(p), color = Color.White.copy(alpha = 0.88f), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(p.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            IconLine(Icons.Filled.LocationOn, p.locationLine().ifBlank { "-" })
            IconLine(Icons.Filled.SquareFoot, sizeLine(p))
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Pill(p.status.tr().s(), statusColor(p.status))
                if (nextFollowUp != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Event, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
                        Text(" " + formatDateShort(nextFollowUp, lang), fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (broker != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(" " + broker.name, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** Small horizontal-scroll card (dashboard, folder previews). */
@Composable
fun PropertyMiniCard(p: Property, image: String?, check: PriceCheck?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val lang = LocalLang.current
    Card(onClick = onClick, modifier = modifier.width(210.dp), shape = CardShape, elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        PropertyImage(image, p.type, Modifier.fillMaxWidth().height(112.dp)) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.7f))))
            Text(
                if (p.askingPrice > 0) priceShort(p.askingPrice, lang) else "—",
                color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp,
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
            )
            if (check != null && check.verdict != Verdict.UNKNOWN) {
                Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(12.dp).clip(CircleShape).background(verdictColor(check.verdict)))
            }
        }
        Column(Modifier.padding(10.dp)) {
            Text(p.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            Text(p.locationLine().ifBlank { p.type.tr().s() }, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun GlassPill(text: String) {
    Box(Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.38f)).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(text, color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun IconLine(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Folder-like tile: gradient background, icon, name, count and up to three stacked thumbnails.
 */
@Composable
fun FolderTile(
    title: String,
    count: Int,
    brush: Brush,
    icon: ImageVector,
    thumbs: List<String?>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Card(onClick = onClick, modifier = modifier.aspectRatio(1.05f), shape = CardShape, elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {
        Box(Modifier.fillMaxSize().background(brush)) {
            // folder tab
            Box(Modifier.padding(start = 14.dp).size(width = 46.dp, height = 8.dp).clip(RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp))
                .background(Color.White.copy(alpha = 0.35f)))
            Column(Modifier.fillMaxSize().padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    Text(count.toString(), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp)
                }
                Spacer(Modifier.weight(1f))
                // stacked thumbnails
                if (thumbs.isNotEmpty()) {
                    Box(Modifier.height(40.dp)) {
                        thumbs.take(3).forEachIndexed { i, t ->
                            Box(
                                Modifier.offset(x = (i * 26).dp).size(40.dp).clip(RoundedCornerShape(10.dp))
                                    .background(Color.White.copy(alpha = 0.3f)),
                            ) {
                                if (t != null) AsyncImage(model = t, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }
}

fun folderBrush(argb: Long): Brush {
    val c = Color(argb)
    return Brush.linearGradient(listOf(c.copy(alpha = 0.85f), c))
}

val FOLDER_COLORS = listOf(0xFF0B5CAD, 0xFF00897B, 0xFF7C4DFF, 0xFFEF6C00, 0xFFD81B60, 0xFF2E7D32, 0xFF455A64, 0xFF6D4C41)

@Suppress("unused")
private val folderIcon = Icons.Filled.Folder
