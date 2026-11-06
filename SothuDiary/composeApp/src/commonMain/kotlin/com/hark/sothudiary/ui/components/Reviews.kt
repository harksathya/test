package com.hark.sothudiary.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hark.sothudiary.data.Review
import com.hark.sothudiary.domain.PostClaim
import com.hark.sothudiary.i18n.S
import com.hark.sothudiary.i18n.f
import com.hark.sothudiary.i18n.s
import com.hark.sothudiary.i18n.tr

/** "Claimed in the post – verify" chips (DTCP, patta, road …). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClaimChips(keys: Set<String>) {
    val claims = keys.mapNotNull { k -> PostClaim.entries.firstOrNull { it.name == k } }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        claims.forEach { Pill(it.tr().s(), Color(0xFF6D4C41)) }
    }
    Text(S.claimsVerify.s(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * "User reviews / கருத்துகள்": comments read from the source post, with a refresh button.
 * [onRefresh] re-reads them (YouTube API / innertube, Instagram embed); [onReadInApp] opens the in-app reader
 * for Instagram / Facebook comments that need a login.
 */
@Composable
fun ReviewsSection(
    reviews: List<Review>,
    loading: Boolean,
    fetchedLabel: String?,
    onRefresh: (() -> Unit)?,
    onReadInApp: (() -> Unit)?,
) {
    var expanded by remember { mutableStateOf(false) }
    SectionCard(
        title = S.userReviews.s() + if (reviews.isNotEmpty()) " (${reviews.size})" else "",
        icon = Icons.Filled.Forum,
        trailing = {
            if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else if (onRefresh != null) IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, contentDescription = S.refresh.s()) }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (reviews.isEmpty()) {
                Text(if (loading) S.reviewsLoading.s() else S.noReviews.s(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val shown = if (expanded) reviews else reviews.take(5)
            shown.forEachIndexed { i, r ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.size(30.dp).background(platformColor(r.source).copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(sourceIcon(r.source), contentDescription = null, tint = platformColor(r.source), modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(r.author.ifBlank { "—" }, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            if (r.date.isNotBlank()) {
                                Spacer(Modifier.width(6.dp))
                                Text("· " + r.date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text(r.text, style = MaterialTheme.typography.bodyMedium, maxLines = if (expanded) 12 else 4, overflow = TextOverflow.Ellipsis)
                        val likes = r.likes
                        if (likes != null && likes > 0) Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.ThumbUp, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(4.dp))
                            Text(likes.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (reviews.size > 5) TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) S.showLess.s() else S.showAllN.f(reviews.size)) }
            if (fetchedLabel != null) Text(fetchedLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (onReadInApp != null) {
                TextButton(onClick = onReadInApp, modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
                    Icon(Icons.Filled.TravelExplore, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                    Text(S.readCommentsInApp.s())
                }
            }
            Text(S.reviewsNote.s(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
