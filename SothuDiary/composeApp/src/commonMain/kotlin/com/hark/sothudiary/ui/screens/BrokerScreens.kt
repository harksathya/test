package com.hark.sothudiary.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hark.sothudiary.data.Broker
import com.hark.sothudiary.data.SourcePlatform
import com.hark.sothudiary.data.newId
import com.hark.sothudiary.domain.formatDate
import com.hark.sothudiary.domain.pricing.checkAll
import com.hark.sothudiary.i18n.LocalLang
import com.hark.sothudiary.i18n.S
import com.hark.sothudiary.i18n.f
import com.hark.sothudiary.i18n.s
import com.hark.sothudiary.i18n.tr
import com.hark.sothudiary.ui.AppContext
import com.hark.sothudiary.ui.Route
import com.hark.sothudiary.ui.components.AppTopBar
import com.hark.sothudiary.ui.components.ChoiceChips
import com.hark.sothudiary.ui.components.EmptyState
import com.hark.sothudiary.ui.components.Field
import com.hark.sothudiary.ui.components.Pill
import com.hark.sothudiary.ui.components.PrimaryButton
import com.hark.sothudiary.ui.components.PropertyCard
import com.hark.sothudiary.ui.components.SectionCard
import com.hark.sothudiary.ui.components.platformColor

@Composable
private fun Avatar(name: String, size: Int = 44) {
    val initials = name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }
    Box(
        Modifier.size(size.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(50)),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials.ifBlank { "?" }, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = (size / 2.6).sp)
    }
}

@Composable
fun BrokerListScreen(ctx: AppContext, selectedId: String?) {
    val data = ctx.repo.data
    val lang = LocalLang.current
    val brokers = data.brokers.sortedBy { it.name.lowercase() }
    Scaffold(
        topBar = { AppTopBar(S.brokers.s(), ctx.toggleLang, subtitle = S.nBrokers.f(brokers.size)) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { ctx.nav.select(Route.BrokerEdit(null)) },
                icon = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
                text = { Text(S.addBroker.s()) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (brokers.isEmpty()) {
            Box(Modifier.padding(padding)) { EmptyState(Icons.Filled.Groups, S.noBrokersYet.s(), S.noBrokersBody.s()) }
        } else {
            LazyColumn(
                Modifier.padding(padding),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(brokers, key = { it.id }) { b ->
                    val count = data.properties.count { it.brokerId == b.id }
                    val last = data.interactions.filter { it.brokerId == b.id }.maxOfOrNull { it.at }
                    val selected = ctx.wide && b.id == selectedId
                    Card(
                        onClick = { ctx.nav.select(Route.BrokerDetail(b.id)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface),
                        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(b.name)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(b.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                    if (b.platform != SourcePlatform.NONE) {
                                        Spacer(Modifier.width(6.dp))
                                        Pill(b.platform.tr().s(), platformColor(b.platform))
                                    }
                                }
                                if (b.area.isNotBlank()) Text(b.area, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                Text(
                                    S.nPropertiesShort.f(count) + (last?.let { "  •  " + S.lastContact.f(formatDate(it, lang)) } ?: ""),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { ctx.platform.call(b.phone) }, enabled = b.phone.isNotBlank()) {
                                Icon(Icons.Filled.Call, contentDescription = S.call.s(), tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { ctx.platform.whatsapp(b.whatsapp.ifBlank { b.phone }) }, enabled = b.whatsapp.isNotBlank() || b.phone.isNotBlank()) {
                                Icon(Icons.Filled.Chat, contentDescription = S.whatsapp.s(), tint = Color(0xFF25A244))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BrokerDetailScreen(ctx: AppContext, id: String) {
    val data = ctx.repo.data
    val b = data.brokers.firstOrNull { it.id == id }
    if (b == null) {
        Scaffold(topBar = { AppTopBar(S.broker.s(), ctx.toggleLang, onBack = { ctx.nav.pop() }) }) { pad ->
            Box(Modifier.padding(pad)) { EmptyState(Icons.Filled.Groups, S.notFound.s(), "") }
        }
        return
    }
    val checks = remember(data) { checkAll(data) }
    val props = data.properties.filter { it.brokerId == b.id }
    val history = data.interactions.filter { it.brokerId == b.id }.sortedByDescending { it.at }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            AppTopBar(
                b.name, ctx.toggleLang, onBack = { ctx.nav.pop() },
                actions = {
                    IconButton(onClick = { ctx.nav.push(Route.BrokerEdit(b.id)) }) { Icon(Icons.Filled.Edit, contentDescription = S.edit.s()) }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = S.delete.s()) }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { ctx.nav.push(Route.InteractionEdit(null, props.firstOrNull()?.id, b.id)) },
                icon = { Icon(Icons.Filled.PhoneInTalk, contentDescription = null) },
                text = { Text(S.logInteraction.s()) },
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(b.name, 56)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(b.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        if (b.area.isNotBlank()) Text(b.area, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.padding(top = 4.dp)) {
                            if (b.platform != SourcePlatform.NONE) Pill(b.platform.tr().s(), platformColor(b.platform), filled = true)
                            if (b.sample) { Spacer(Modifier.width(6.dp)); Pill(S.sample.s(), Color(0xFF8D6E63)) }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(S.phone.s() + ": " + b.phone.ifBlank { "-" })
                if (b.whatsapp.isNotBlank() && b.whatsapp != b.phone) Text(S.whatsapp.s() + ": " + b.whatsapp)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { ctx.platform.call(b.phone) }, enabled = b.phone.isNotBlank()) {
                        Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(S.call.s())
                    }
                    FilledTonalButton(onClick = { ctx.platform.whatsapp(b.whatsapp.ifBlank { b.phone }) }, enabled = b.whatsapp.isNotBlank() || b.phone.isNotBlank()) {
                        Icon(Icons.Filled.Chat, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(S.whatsapp.s())
                    }
                }
                if (b.notes.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(b.notes, style = MaterialTheme.typography.bodyMedium)
                }
            }

            Text(S.propertiesFromBroker.f(props.size), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (props.isEmpty()) {
                Text(S.noPropertiesForBroker.s(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            props.forEach { p ->
                PropertyCard(p, b, checks[p.id], nextFollowUp(p.id, data), selected = false) { ctx.nav.push(Route.PropertyDetail(p.id)) }
            }

            SectionCard(title = S.callHistory.s() + " (" + history.size + ")") {
                if (history.isEmpty()) Text(S.noInteractions.s(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                else InteractionTimeline(ctx, history, data, showProperty = true)
            }
            Spacer(Modifier.height(80.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(S.deleteBrokerQ.s()) },
            text = { Text(S.deleteBrokerBody.s()) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; ctx.repo.deleteBroker(b.id); ctx.nav.pop() }) {
                    Text(S.delete.s(), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(S.cancel.s()) } },
        )
    }
}

@Composable
fun BrokerEditScreen(ctx: AppContext, route: Route.BrokerEdit) {
    val existing = route.id?.let { id -> ctx.repo.data.brokers.firstOrNull { it.id == id } }
    var name by remember(route) { mutableStateOf(existing?.name ?: "") }
    var phone by remember(route) { mutableStateOf(existing?.phone ?: "") }
    var whatsapp by remember(route) { mutableStateOf(existing?.whatsapp ?: "") }
    var platform by remember(route) { mutableStateOf(existing?.platform ?: SourcePlatform.NONE) }
    var area by remember(route) { mutableStateOf(existing?.area ?: "") }
    var notes by remember(route) { mutableStateOf(existing?.notes ?: "") }

    fun save() {
        if (name.isBlank()) return
        val b = (existing ?: Broker(id = newId(), name = name.trim())).copy(
            name = name.trim(), phone = phone.trim(), whatsapp = whatsapp.trim().ifBlank { phone.trim() },
            platform = platform, area = area.trim(), notes = notes.trim(),
        )
        ctx.repo.upsertBroker(b)
        if (existing == null) ctx.nav.replaceTop(Route.BrokerDetail(b.id)) else ctx.nav.pop()
    }

    Scaffold(
        topBar = {
            AppTopBar(
                if (existing == null) S.addBroker.s() else S.editBroker.s(), ctx.toggleLang, onBack = { ctx.nav.pop() },
                actions = { TextButton(onClick = { save() }, enabled = name.isNotBlank()) { Text(S.save.s(), color = MaterialTheme.colorScheme.onPrimary) } },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard(title = S.brokerDetails.s()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(name, { name = it }, S.name.s())
                    Field(phone, { phone = it }, S.phone.s(), keyboard = androidx.compose.ui.text.input.KeyboardType.Phone)
                    Field(whatsapp, { whatsapp = it }, S.whatsappNumber.s(), keyboard = androidx.compose.ui.text.input.KeyboardType.Phone, supporting = S.whatsappHint.s())
                    Field(area, { area = it }, S.brokerArea.s())
                    Text(S.platform.s(), style = MaterialTheme.typography.labelLarge)
                    ChoiceChips(SourcePlatform.entries, platform, { it.tr().s() }) { platform = it }
                    Field(notes, { notes = it }, S.notes.s(), singleLine = false, minLines = 3)
                }
            }
            PrimaryButton(S.save.s(), { save() }, Modifier.fillMaxWidth(), enabled = name.isNotBlank(), icon = Icons.Filled.Check)
        }
    }
}
