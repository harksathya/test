package com.hark.sothudiary.ui.screens

import com.hark.sothudiary.domain.ai.hasAiKey
import com.hark.sothudiary.ui.components.GeminiSetupCard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddHomeWork
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hark.sothudiary.data.DealStatus
import com.hark.sothudiary.data.nowMillis
import com.hark.sothudiary.domain.daysUntil
import com.hark.sothudiary.domain.formatDateShort
import com.hark.sothudiary.domain.priceShort
import com.hark.sothudiary.domain.pricing.Verdict
import com.hark.sothudiary.domain.pricing.checkAll
import com.hark.sothudiary.i18n.LocalLang
import com.hark.sothudiary.i18n.S
import com.hark.sothudiary.i18n.f
import com.hark.sothudiary.i18n.s
import com.hark.sothudiary.i18n.tr
import com.hark.sothudiary.ui.AppContext
import com.hark.sothudiary.ui.Route
import com.hark.sothudiary.ui.Tab
import com.hark.sothudiary.ui.components.LangToggle
import com.hark.sothudiary.ui.components.Pill
import com.hark.sothudiary.ui.components.PropertyMiniCard
import com.hark.sothudiary.ui.components.SectionCard
import com.hark.sothudiary.ui.components.StatTile
import com.hark.sothudiary.ui.components.bucket
import com.hark.sothudiary.ui.components.propertyImageModel
import com.hark.sothudiary.ui.theme.LocalBrand
import com.hark.sothudiary.ui.theme.statusColor
import com.hark.sothudiary.ui.theme.verdictColor

@Composable
fun HomeScreen(ctx: AppContext) {
    val data = ctx.repo.data
    val lang = LocalLang.current
    val checks = remember(data) { checkAll(data) }
    val now = nowMillis()
    val active = data.properties.count { it.status != DealStatus.BOUGHT && it.status != DealStatus.DROPPED }
    val due = data.interactions.filter { !it.followUpDone && it.followUpAt != null && daysUntil(it.followUpAt, now) <= 0 }
        .sortedBy { it.followUpAt }
    val suspicious = data.properties.filter { checks[it.id]?.verdict == Verdict.SUSPICIOUS_LOW && it.status != DealStatus.DROPPED }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState())) {
        // ---- Hero ----
        Box(
            Modifier.fillMaxWidth()
                .shadow(if (LocalBrand.current.dark) 0.dp else 2.dp, RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                .background(LocalBrand.current.hero),
        ) {
            Column(Modifier.statusBarsPadding().padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(S.appName.s(), color = MaterialTheme.colorScheme.onSurface, fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp)
                        Text(S.appTagline.s(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    }
                    com.hark.sothudiary.ui.components.MusicToggle()
                    LangToggle(ctx.toggleLang)
                }
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HeroStat(data.properties.size.toString(), S.statProperties.s(), Modifier.weight(1f)) { ctx.nav.switchTab(Tab.PROPERTIES) }
                    HeroStat(active.toString(), S.statActive.s(), Modifier.weight(1f)) { ctx.nav.switchTab(Tab.PROPERTIES) }
                    HeroStat(due.size.toString(), S.statDue.s(), Modifier.weight(1f), alert = due.isNotEmpty()) { ctx.nav.switchTab(Tab.FOLLOWUPS) }
                }
            }
        }

        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // ---- Quick actions ----
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                QuickAction(Icons.Filled.AddHomeWork, S.addProperty.s(), MaterialTheme.colorScheme.primary) { ctx.nav.select(Route.PropertyEdit(null)) }
                QuickAction(Icons.Filled.PhoneInTalk, S.logInteraction.s(), MaterialTheme.colorScheme.primary) { ctx.nav.select(Route.InteractionEdit(null, null, null)) }
                QuickAction(Icons.Filled.QueryStats, S.tabAreaRates.s(), MaterialTheme.colorScheme.primary) { ctx.nav.switchTab(Tab.AREA_RATES) }
                QuickAction(Icons.Filled.Map, S.tabMap.s(), MaterialTheme.colorScheme.primary) { ctx.nav.switchTab(Tab.MAP) }
            }

            if (!data.settings.hasAiKey && !data.settings.keyCardDismissed) {
                GeminiSetupCard(ctx, onDismiss = { ctx.repo.dismissKeyCard() })
            }

            if (data.properties.isEmpty()) {
                WelcomeCard(ctx)
            }

            // ---- Follow-ups due ----
            if (due.isNotEmpty()) {
                SectionCard(title = S.dueNow.f(due.size), icon = Icons.Filled.Event, accent = MaterialTheme.colorScheme.error,
                    trailing = { TextButton(onClick = { ctx.nav.switchTab(Tab.FOLLOWUPS) }) { Text(S.seeAll.s()) } }) {
                    due.take(3).forEach { i ->
                        val p = i.propertyId?.let { pid -> data.properties.firstOrNull { it.id == pid } }
                        val b = i.brokerId?.let { bid -> data.brokers.firstOrNull { it.id == bid } }
                        val d = daysUntil(i.followUpAt ?: now, now)
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .clickable { if (p != null) ctx.nav.select(Route.PropertyDetail(p.id)) else ctx.nav.switchTab(Tab.FOLLOWUPS) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Pill(if (d < 0) S.daysOverdue.f(-d) else S.today.s(), if (d < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary, filled = true)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p?.title ?: b?.name ?: "-", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val sub = i.promises.ifBlank { i.summary }
                                if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (b != null && b.phone.isNotBlank()) {
                                androidx.compose.material3.IconButton(onClick = { ctx.platform.call(b.phone) }) {
                                    Icon(Icons.Filled.PhoneInTalk, contentDescription = S.call.s(), tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }

            // ---- Price alerts ----
            if (suspicious.isNotEmpty()) {
                SectionCard(title = S.priceAlerts.f(suspicious.size), icon = Icons.Filled.WarningAmber, accent = verdictColor(Verdict.SUSPICIOUS_LOW)) {
                    suspicious.take(3).forEach { p ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { ctx.nav.select(Route.PropertyDetail(p.id)) }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(p.title, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(priceShort(p.askingPrice, lang), fontWeight = FontWeight.Bold, color = verdictColor(Verdict.SUSPICIOUS_LOW))
                            Icon(Icons.Filled.ChevronRight, contentDescription = null)
                        }
                    }
                    Text(S.priceAlertsHint.s(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // ---- Pipeline ----
            if (data.properties.isNotEmpty()) {
                SectionTitle(S.pipeline.s())
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DealStatus.entries.forEach { st ->
                        val n = data.properties.count { it.status == st }
                        StatTile(n.toString(), st.tr().s(), statusColor(st), Modifier.width(112.dp)) {
                            ctx.filters.clear(); ctx.filters.statuses = setOf(st); ctx.nav.switchTab(Tab.PROPERTIES)
                        }
                    }
                }

                // ---- Recently added ----
                SectionTitle(S.recentlyAdded.s()) { ctx.nav.switchTab(Tab.PROPERTIES) }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    data.properties.sortedByDescending { it.createdAt }.take(8).forEach { p ->
                        PropertyMiniCard(p, propertyImageModel(ctx.platform, p), checks[p.id], onClick = { ctx.nav.select(Route.PropertyDetail(p.id)) })
                    }
                }

                // ---- Sources ----
                SectionTitle(S.bySource.s()) { ctx.filters.mode = com.hark.sothudiary.ui.ListMode.FOLDERS; ctx.nav.switchTab(Tab.PROPERTIES) }
                SourceTiles(ctx)
            }

            // ---- Recent activity ----
            val recent = data.interactions.sortedByDescending { it.at }.take(5)
            if (recent.isNotEmpty()) {
                SectionCard(title = S.recentActivity.s(), icon = Icons.Filled.PhoneInTalk) {
                    recent.forEach { i ->
                        val p = i.propertyId?.let { pid -> data.properties.firstOrNull { it.id == pid } }
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .clickable { ctx.nav.select(Route.InteractionEdit(i.id, i.propertyId, i.brokerId)) }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondary))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(i.type.tr().s() + " • " + (p?.title ?: "-"), fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val sub = i.summary.ifBlank { i.quotedPrice?.let { S.quoted.f(priceShort(it, lang)) } ?: "" }
                                if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(formatDateShort(i.at, lang), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (i.voiceNotes.isNotEmpty()) Text("  🎙" + i.voiceNotes.size, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            Text(S.disclaimer.s(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun HeroStat(value: String, label: String, modifier: Modifier, alert: Boolean = false, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(value, color = if (alert) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Column(
        Modifier.width(80.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
fun SectionTitle(text: String, onMore: (() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (onMore != null) TextButton(onClick = onMore) { Text(S.seeAll.s()) }
    }
}

@Composable
private fun WelcomeCard(ctx: AppContext) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(S.welcomeTitle.s(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.height(4.dp))
            Text(S.welcomeBody.s(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.height(12.dp))
            listOf(
                Icons.Filled.Share to S.welcomeStep1.s(),
                Icons.Filled.Map to S.welcomeStep2.s(),
                Icons.Filled.QueryStats to S.welcomeStep3.s(),
                Icons.Filled.PhoneInTalk to S.welcomeStep4.s(),
            ).forEachIndexed { idx, (icon, text) ->
                Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                        Text((idx + 1).toString(), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(10.dp))
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { ctx.nav.select(Route.PropertyEdit(null)) }) { Text(S.addFirstProperty.s()) }
                OutlinedButton(onClick = { ctx.nav.switchTab(Tab.AREA_RATES) }) { Text(S.checkAreaRates.s()) }
            }
        }
    }
}
