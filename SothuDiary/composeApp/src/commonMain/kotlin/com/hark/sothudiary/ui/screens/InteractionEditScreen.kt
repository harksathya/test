package com.hark.sothudiary.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Translate
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.hark.sothudiary.PlatformBackHandler
import com.hark.sothudiary.SPEECH_BUSY
import com.hark.sothudiary.SPEECH_LANGUAGE
import com.hark.sothudiary.SPEECH_NETWORK
import com.hark.sothudiary.SPEECH_NO_MATCH
import com.hark.sothudiary.SPEECH_PERMISSION
import com.hark.sothudiary.SPEECH_UNAVAILABLE
import com.hark.sothudiary.data.Lang
import com.hark.sothudiary.ui.components.ConfirmDialog
import com.hark.sothudiary.ui.components.VoiceNotesSection
import com.hark.sothudiary.ui.theme.LocalBrand
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hark.sothudiary.data.Broker
import com.hark.sothudiary.data.Interaction
import com.hark.sothudiary.data.InteractionType
import com.hark.sothudiary.data.Property
import com.hark.sothudiary.data.newId
import com.hark.sothudiary.data.nowMillis
import com.hark.sothudiary.domain.daysFromToday
import com.hark.sothudiary.domain.parsePrice
import com.hark.sothudiary.domain.priceShort
import com.hark.sothudiary.i18n.LocalLang
import com.hark.sothudiary.i18n.S
import com.hark.sothudiary.i18n.f
import com.hark.sothudiary.i18n.s
import com.hark.sothudiary.i18n.tr
import com.hark.sothudiary.ui.AppContext
import com.hark.sothudiary.ui.Route
import com.hark.sothudiary.ui.components.AppTopBar
import com.hark.sothudiary.ui.components.ChoiceChips
import com.hark.sothudiary.ui.components.DateField
import com.hark.sothudiary.ui.components.DropdownSelector
import com.hark.sothudiary.ui.components.Field
import com.hark.sothudiary.ui.components.PrimaryButton
import com.hark.sothudiary.ui.components.SectionCard

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InteractionEditScreen(ctx: AppContext, route: Route.InteractionEdit) {
    val data = ctx.repo.data
    val lang = LocalLang.current
    val existing = route.id?.let { id -> data.interactions.firstOrNull { it.id == id } }
    val now = remember { nowMillis() }

    var propertyId by remember(route) { mutableStateOf(existing?.propertyId ?: route.propertyId) }
    var brokerId by remember(route) {
        mutableStateOf(existing?.brokerId ?: route.brokerId ?: route.propertyId?.let { pid -> data.properties.firstOrNull { it.id == pid }?.brokerId })
    }
    var type by remember(route) { mutableStateOf(existing?.type ?: InteractionType.CALL) }
    var at by remember(route) { mutableStateOf(existing?.at ?: now) }
    var quoted by remember(route) { mutableStateOf(existing?.quotedPrice?.toString() ?: "") }
    var summary by remember(route) { mutableStateOf(existing?.summary ?: "") }
    var promises by remember(route) { mutableStateOf(existing?.promises ?: "") }
    var followUp by remember(route) { mutableStateOf(existing?.followUpAt) }
    var updateAsking by remember(route) { mutableStateOf(existing == null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var notes by remember(route) { mutableStateOf(existing?.voiceNotes ?: emptyList()) }
    val added = remember(route) { mutableStateListOf<String>() }
    val removed = remember(route) { mutableStateListOf<String>() }
    var saved by remember(route) { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }
    var partial by remember { mutableStateOf("") }
    val speechLang = data.settings.speechLang ?: lang
    val audio = ctx.platform.audio

    // Clips recorded in this screen but never saved are removed when it closes.
    DisposableEffect(route) {
        onDispose {
            audio.stopSpeech()
            if (!saved) added.forEach { audio.deleteAudio(it) }
        }
    }

    val property = propertyId?.let { pid -> data.properties.firstOrNull { it.id == pid } }
    val quotedVal = parsePrice(quoted)

    val quotedError = if (quoted.isNotBlank() && (quotedVal == null || quotedVal <= 0)) S.errPrice.s() else null
    val dirty = existing?.let {
        it.summary != summary || it.promises != promises || it.followUpAt != followUp || it.type != type || it.at != at ||
            (it.quotedPrice?.toString() ?: "") != quoted || it.voiceNotes != notes || it.propertyId != propertyId || it.brokerId != brokerId
    } ?: (summary.isNotBlank() || promises.isNotBlank() || quoted.isNotBlank() || notes.isNotEmpty() || followUp != null)

    fun leave() { if (dirty) confirmDiscard = true else ctx.nav.pop() }
    PlatformBackHandler(enabled = true) { leave() }

    fun speechError(code: String): String = when (code) {
        SPEECH_PERMISSION -> S.micDenied.of(lang)
        SPEECH_NO_MATCH -> S.speechNoMatch.of(lang)
        SPEECH_NETWORK -> S.speechNetwork.of(lang)
        SPEECH_LANGUAGE -> S.speechLanguage.of(lang)
        SPEECH_BUSY -> S.speechBusy.of(lang)
        SPEECH_UNAVAILABLE -> S.speechUnavailable.of(lang)
        else -> S.speechOther.of(lang)
    }

    fun toggleSpeech() {
        if (listening) {
            audio.stopSpeech(); listening = false; partial = ""
            return
        }
        if (!audio.speechSupported) {
            ctx.platform.toast(S.speechUnavailable.of(lang))
            return
        }
        listening = true
        partial = ""
        audio.startSpeech(
            if (speechLang == Lang.TA) "ta-IN" else "en-IN",
            onPartial = { partial = it },
            onFinal = { text ->
                summary = (summary.trimEnd() + (if (summary.isBlank()) "" else " ") + text.trim()).trim()
                partial = ""
                listening = false
            },
            onError = { code -> listening = false; partial = ""; ctx.platform.toast(speechError(code)) },
        )
    }

    fun save() {
        if (quotedError != null) {
            ctx.platform.toast(S.fixErrors.of(lang))
            return
        }
        audio.stopSpeech()
        removed.forEach { audio.deleteAudio(it) }
        saved = true
        if (followUp != null && data.settings.reminders) ctx.platform.setReminders(true)
        val i = (existing ?: Interaction(id = newId())).copy(
            voiceNotes = notes,
            propertyId = propertyId,
            brokerId = brokerId,
            at = at,
            type = type,
            quotedPrice = quotedVal?.takeIf { it > 0 },
            summary = summary.trim(),
            promises = promises.trim(),
            followUpAt = followUp,
            followUpDone = if (existing != null && existing.followUpAt == followUp) existing.followUpDone else false,
        )
        ctx.repo.upsertInteraction(i, updateAskingPrice = updateAsking)
        ctx.nav.pop()
    }

    Scaffold(
        topBar = {
            AppTopBar(
                if (existing == null) S.logInteraction.s() else S.editInteraction.s(), ctx.toggleLang, onBack = { leave() },
                actions = {
                    if (existing != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = S.delete.s()) }
                    TextButton(onClick = { save() }) { Text(S.save.s(), color = LocalBrand.current.onBar, fontWeight = FontWeight.Bold) }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard(title = S.whoAndWhat.s()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val noProp: Property? = null
                    DropdownSelector(
                        label = S.property.s(),
                        current = property?.title ?: S.noProperty.s(),
                        options = listOf(noProp) + data.properties,
                        optionLabel = { it?.title ?: S.noProperty.of(lang) },
                        onSelect = { p ->
                            propertyId = p?.id
                            if (p?.brokerId != null) brokerId = p.brokerId
                        },
                    )
                    val noBroker: Broker? = null
                    DropdownSelector(
                        label = S.broker.s(),
                        current = brokerId?.let { id -> data.brokers.firstOrNull { it.id == id }?.name } ?: S.noBroker.s(),
                        options = listOf(noBroker) + data.brokers,
                        optionLabel = { it?.name ?: S.noBroker.of(lang) },
                        onSelect = { brokerId = it?.id },
                    )
                    Text(S.type.s(), style = MaterialTheme.typography.labelLarge)
                    ChoiceChips(InteractionType.entries, type, { it.tr().s() }) { type = it }
                    DateField(S.date.s(), at, { if (it != null) at = it }, allowClear = false)
                }
            }
            SectionCard(title = S.priceAndTalk.s()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(
                        quoted, { quoted = it }, S.quotedPrice.s(), keyboard = KeyboardType.Text,
                        supporting = property?.takeIf { it.askingPrice > 0 }?.let { S.currentAsking.f(priceShort(it.askingPrice, lang)) } ?: S.priceInputHint.s(),
                        error = quotedError,
                    )
                    if (quotedVal != null && quotedVal > 0) {
                        Text("= " + priceShort(quotedVal, lang), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                    }
                    if (property != null) {
                        Row(Modifier.fillMaxWidth().clickable { updateAsking = !updateAsking }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = updateAsking, onCheckedChange = { updateAsking = it })
                            Text(S.updateAsking.s(), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Field(
                        summary, { summary = it }, S.summary.s(), singleLine = false, minLines = 3,
                        trailingIcon = {
                            IconButton(onClick = { toggleSpeech() }) {
                                Icon(
                                    if (listening) Icons.Filled.Stop else Icons.Filled.Mic,
                                    contentDescription = S.speakToType.s(),
                                    tint = if (listening) Color(0xFFD32F2F) else MaterialTheme.colorScheme.primary,
                                )
                            }
                        },
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (listening) S.listening.s() else S.speakToTypeHint.s(),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (listening) Color(0xFFD32F2F) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        FilterChip(
                            selected = speechLang == Lang.TA,
                            onClick = { ctx.repo.setSpeechLang(if (speechLang == Lang.TA) Lang.EN else Lang.TA) },
                            label = { Text(if (speechLang == Lang.TA) "தமிழ்" else "English") },
                            leadingIcon = { Icon(Icons.Filled.Translate, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        )
                    }
                    if (partial.isNotBlank()) {
                        Text("“$partial”", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Field(promises, { promises = it }, S.promises.s(), singleLine = false, minLines = 2, supporting = S.promisesHint.s())
                }
            }
            SectionCard(title = S.voiceNotes.s(), icon = Icons.Filled.Mic) {
                VoiceNotesSection(
                    ctx, notes,
                    onAdd = { vn -> notes = notes + vn; added += vn.fileName },
                    onRemove = { vn ->
                        notes = notes.filterNot { it.id == vn.id }
                        if (vn.fileName in added) { audio.deleteAudio(vn.fileName); added -= vn.fileName } else removed += vn.fileName
                    },
                )
                Text(S.voiceNotExported.s(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            }
            SectionCard(title = S.nextFollowUp.s()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AssistChip(onClick = { followUp = daysFromToday(1, now) }, label = { Text(S.tomorrow.s()) })
                        AssistChip(onClick = { followUp = daysFromToday(3, now) }, label = { Text(S.in3Days.s()) })
                        AssistChip(onClick = { followUp = daysFromToday(7, now) }, label = { Text(S.in1Week.s()) })
                        AssistChip(onClick = { followUp = null }, label = { Text(S.noFollowUp.s()) })
                    }
                    DateField(S.followUpDate.s(), followUp, { followUp = it })
                }
            }
            PrimaryButton(S.save.s(), { save() }, Modifier.fillMaxWidth(), icon = Icons.Filled.Check)
            Spacer(Modifier.height(40.dp))
        }
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = S.discardQ.s(), body = S.discardBody.s(), confirm = S.discard.s(), destructive = true,
            onConfirm = { confirmDiscard = false; ctx.nav.pop() }, onDismiss = { confirmDiscard = false },
        )
    }

    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(S.deleteInteractionQ.s()) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    (existing.voiceNotes.map { it.fileName } + added).forEach { audio.deleteAudio(it) }
                    saved = true
                    ctx.repo.deleteInteraction(existing.id)
                    ctx.nav.pop()
                }) {
                    Text(S.delete.s(), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(S.cancel.s()) } },
        )
    }
}
