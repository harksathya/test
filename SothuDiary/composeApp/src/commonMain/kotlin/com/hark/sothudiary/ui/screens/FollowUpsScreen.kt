package com.hark.sothudiary.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hark.sothudiary.data.Interaction
import com.hark.sothudiary.data.nowMillis
import com.hark.sothudiary.domain.daysUntil
import com.hark.sothudiary.domain.formatDate
import com.hark.sothudiary.domain.priceShort
import com.hark.sothudiary.i18n.LocalLang
import com.hark.sothudiary.i18n.S
import com.hark.sothudiary.i18n.f
import com.hark.sothudiary.i18n.s
import com.hark.sothudiary.i18n.tr
import com.hark.sothudiary.ui.AppContext
import com.hark.sothudiary.ui.Route
import com.hark.sothudiary.ui.components.AppTopBar
import com.hark.sothudiary.ui.components.EmptyState
import com.hark.sothudiary.ui.components.Pill

@Composable
fun FollowUpsScreen(ctx: AppContext) {
    val data = ctx.repo.data
    val now = nowMillis()
    val pending = data.interactions.filter { it.followUpAt != null && !it.followUpDone }.sortedBy { it.followUpAt }
    val overdue = pending.filter { daysUntil(it.followUpAt ?: 0, now) < 0 }
    val today = pending.filter { daysUntil(it.followUpAt ?: 0, now) == 0 }
    val upcoming = pending.filter { daysUntil(it.followUpAt ?: 0, now) > 0 }

    Scaffold(
        topBar = { AppTopBar(S.followUps.s(), ctx.toggleLang, subtitle = S.followUpSummary.f(overdue.size, today.size, upcoming.size)) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (pending.isEmpty()) {
            Box(Modifier.padding(padding)) { EmptyState(Icons.Filled.EventAvailable, S.noFollowUps.s(), S.noFollowUpsBody.s()) }
            return@Scaffold
        }
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (overdue.isNotEmpty()) {
                item { SectionHeader(S.overdue.s() + " (" + overdue.size + ")", MaterialTheme.colorScheme.error) }
                items(overdue, key = { it.id }) { FollowUpCard(ctx, it, now) }
            }
            if (today.isNotEmpty()) {
                item { SectionHeader(S.today.s() + " (" + today.size + ")", MaterialTheme.colorScheme.secondary) }
                items(today, key = { it.id }) { FollowUpCard(ctx, it, now) }
            }
            if (upcoming.isNotEmpty()) {
                item { SectionHeader(S.upcoming.s() + " (" + upcoming.size + ")", MaterialTheme.colorScheme.primary) }
                items(upcoming, key = { it.id }) { FollowUpCard(ctx, it, now) }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String, color: Color) {
    Text(text, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
}

@Composable
private fun FollowUpCard(ctx: AppContext, i: Interaction, now: Long) {
    val data = ctx.repo.data
    val lang = LocalLang.current
    val p = i.propertyId?.let { pid -> data.properties.firstOrNull { it.id == pid } }
    val b = i.brokerId?.let { bid -> data.brokers.firstOrNull { it.id == bid } }
    val fu = i.followUpAt ?: return
    val days = daysUntil(fu, now)
    val whenText = when {
        days < 0 -> S.daysOverdue.f(-days)
        days == 0 -> S.today.s()
        days == 1 -> S.tomorrow.s()
        else -> S.inNDays.f(days)
    }
    val color = when {
        days < 0 -> MaterialTheme.colorScheme.error
        days == 0 -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.primary
    }
    Card(
        onClick = { if (p != null) ctx.nav.select(Route.PropertyDetail(p.id)) else if (b != null) ctx.nav.select(Route.BrokerDetail(b.id)) },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pill(whenText, color, filled = true)
                    Spacer(Modifier.width(6.dp))
                    Text(formatDate(fu, lang), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    p?.title ?: b?.name ?: "-", fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (b != null && p != null) Text(b.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (i.promises.isNotBlank()) {
                    Text(S.promisesLabel.s() + ": " + i.promises, style = MaterialTheme.typography.bodySmall, color = com.hark.sothudiary.ui.theme.LocalBrand.current.warnText, modifier = Modifier.padding(top = 2.dp))
                } else if (i.summary.isNotBlank()) {
                    Text(i.summary, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                }
                val q = i.quotedPrice
                Text(
                    i.type.tr().s() + (if (q != null) "  •  " + S.quoted.f(priceShort(q, lang)) else ""),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Checkbox(checked = false, onCheckedChange = { ctx.repo.setFollowUpDone(i.id, true) })
                Text(S.done.s(), style = MaterialTheme.typography.labelSmall)
                if (b != null && b.phone.isNotBlank()) {
                    Row {
                        IconButton(onClick = { ctx.platform.call(b.phone) }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.Call, contentDescription = S.call.s(), tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { ctx.platform.whatsapp(b.whatsapp.ifBlank { b.phone }) }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.Chat, contentDescription = S.whatsapp.s(), tint = Color(0xFF25A244))
                        }
                    }
                }
            }
        }
    }
}
