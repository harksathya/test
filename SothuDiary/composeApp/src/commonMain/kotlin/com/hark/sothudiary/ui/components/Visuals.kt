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
import androidx.compose.foundation.Image
import androidx.compose.material3.Surface
import com.hark.sothudiary.ui.theme.LocalBrand
import androidx.compose.material3.Card
import androidx.compose.foundation.Image
import androidx.compose.material3.Surface
import androidx.compose.ui.unit.Dp
import com.hark.sothudiary.ui.theme.LocalBrand
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

fun sourceIcon(p: SourcePlatform): ImageVector = brandLogo(p.bucket()) ?: Icons.Filled.Link

/** Brand mark (real logo, own colours) or a neutral link glyph for "Others". */
@Composable
fun SourceLogo(p: SourcePlatform, size: Dp = 18.dp, modifier: Modifier = Modifier) {
    val logo = brandLogo(p.bucket())
    if (logo != null) Image(logo, contentDescription = p.tr().s(), modifier = modifier.size(size))
    else Icon(Icons.Filled.Link, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier.size(size))
}

/** Small white disc with the brand mark – sits on top of photos. */
@Composable
fun SourceBadge(p: SourcePlatform, modifier: Modifier = Modifier, size: Dp = 28.dp) {
    Surface(modifier = modifier.size(size), shape = CircleShape, color = Color.White, shadowElevation = 2.dp) {
        Box(contentAlignment = Alignment.Center) { SourceLogo(p, size * 0.6f) }
    }
}

/** Calm placeholder (neutral surface + faint type glyph) under the photo, shown while loading or when there is none. */
@Composable
fun PropertyImage(model: String?, type: PropertyType, modifier: Modifier = Modifier, overlay: @Composable BoxScope.() -> Unit = {}) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        Icon(typeIcon(type), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.32f),
            modifier = Modifier.fillMaxSize(0.34f).align(Alignment.Center))
        if (model != null) {
            AsyncImage(model = model, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        overlay()
    }
}

/** Two or three overlapping rounded thumbnails; draws nothing when there are none. */
@Composable
fun ThumbStack(thumbs: List<String>, size: Dp = 28.dp, modifier: Modifier = Modifier) {
    val list = thumbs.take(3)
    if (list.isEmpty()) return
    val step = size * 0.58f
    Box(modifier.width(size + step * (list.size - 1)).height(size)) {
        list.forEachIndexed { i, t ->
            Box(
                Modifier.offset(x = step * i).size(size).clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface).padding(1.5.dp).clip(RoundedCornerShape(7.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) { AsyncImage(model = t, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        }
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
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = if (LocalBrand.current.dark) 0.dp else 1.dp),
    ) {
        PropertyImage(image, p.type, Modifier.fillMaxWidth().height(158.dp)) {
            // dark fade at the bottom for the price
            if (image != null) Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.62f))))
            Row(Modifier.align(Alignment.TopStart).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GlassPill(p.type.tr().s())
                if (check != null && check.verdict != Verdict.UNKNOWN) Pill(check.verdict.shortTr().s(), verdictColor(check.verdict), filled = true)
            }
            if (p.sourcePlatform != SourcePlatform.NONE) SourceBadge(p.sourcePlatform, Modifier.align(Alignment.TopEnd).padding(10.dp))
            Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 14.dp, vertical = 10.dp)) {
                val fg = if (image != null) Color.White else MaterialTheme.colorScheme.onSurface
                Text(
                    if (p.askingPrice > 0) priceShort(p.askingPrice, lang) else S.priceOnRequest.s(),
                    color = fg, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                )
                Text(unitPriceLine(p), color = fg.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    Card(onClick = onClick, modifier = modifier.width(196.dp), shape = CardShape, elevation = CardDefaults.cardElevation(defaultElevation = if (LocalBrand.current.dark) 0.dp else 1.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        PropertyImage(image, p.type, Modifier.fillMaxWidth().height(112.dp)) {
            if (image != null) Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.62f))))
            if (p.sourcePlatform != SourcePlatform.NONE) SourceBadge(p.sourcePlatform, Modifier.align(Alignment.TopStart).padding(8.dp), size = 24.dp)
            Text(
                if (p.askingPrice > 0) priceShort(p.askingPrice, lang) else "—",
                color = if (image != null) Color.White else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 17.sp,
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
 * Compact folder: small logo / folder glyph, name, count and (only when there are photos) a tiny stack of thumbnails.
 */
@Composable
fun FolderTile(
    title: String,
    count: Int,
    thumbs: List<String>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = if (LocalBrand.current.dark) 0.dp else 1.dp,
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                leading()
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(com.hark.sothudiary.i18n.propertiesCount(count), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            ThumbStack(thumbs, size = 26.dp)
        }
    }
}

/** Glyph for a user folder: a folder icon in the folder's (muted) colour. */
@Composable
fun FolderGlyph(argb: Long, size: Dp = 20.dp) {
    Icon(Icons.Filled.Folder, contentDescription = null, tint = Color(argb), modifier = Modifier.size(size))
}

/** Muted folder colours (used only for the small folder glyph). */
val FOLDER_COLORS = listOf(0xFF2F6F68, 0xFF3F6C9A, 0xFF6F5E9A, 0xFFB0700E, 0xFFA2545E, 0xFF4D7A45, 0xFF5E6668, 0xFF85654A)
