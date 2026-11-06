package com.hark.sothudiary.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hark.sothudiary.data.AreaUnit
import com.hark.sothudiary.data.Broker
import com.hark.sothudiary.data.DealStatus
import com.hark.sothudiary.data.Property
import com.hark.sothudiary.data.PropertyType
import com.hark.sothudiary.data.newId
import com.hark.sothudiary.domain.dec
import com.hark.sothudiary.domain.detectPlatform
import com.hark.sothudiary.domain.parseDouble
import com.hark.sothudiary.domain.parsePrice
import com.hark.sothudiary.domain.priceShort
import com.hark.sothudiary.domain.rupees
import com.hark.sothudiary.domain.toCents
import com.hark.sothudiary.domain.toSqft
import com.hark.sothudiary.i18n.LocalLang
import com.hark.sothudiary.i18n.S
import com.hark.sothudiary.i18n.f
import com.hark.sothudiary.i18n.s
import com.hark.sothudiary.i18n.tr
import com.hark.sothudiary.ui.AppContext
import com.hark.sothudiary.ui.Route
import com.hark.sothudiary.ui.components.AppTopBar
import com.hark.sothudiary.ui.components.ChoiceChips
import com.hark.sothudiary.ui.components.DropdownSelector
import com.hark.sothudiary.ui.components.Field
import com.hark.sothudiary.ui.components.Pill
import com.hark.sothudiary.ui.components.PrimaryButton
import com.hark.sothudiary.ui.components.SectionCard
import com.hark.sothudiary.ui.components.platformColor

@Composable
fun PropertyEditScreen(ctx: AppContext, route: Route.PropertyEdit) {
    val data = ctx.repo.data
    val lang = LocalLang.current
    val existing = route.id?.let { id -> data.properties.firstOrNull { it.id == id } }

    val initialTitle = existing?.title ?: route.prefillCaption.lineSequence().firstOrNull()?.take(70)?.trim().orEmpty()
    var title by remember(route) { mutableStateOf(initialTitle) }
    var type by remember(route) { mutableStateOf(existing?.type ?: PropertyType.LAND) }
    var locality by remember(route) { mutableStateOf(existing?.locality ?: "") }
    var taluk by remember(route) { mutableStateOf(existing?.taluk ?: "") }
    var district by remember(route) { mutableStateOf(existing?.district ?: "Madurai") }
    var lat by remember(route) { mutableStateOf(existing?.lat?.toString() ?: "") }
    var lng by remember(route) { mutableStateOf(existing?.lng?.toString() ?: "") }
    var size by remember(route) { mutableStateOf(existing?.size?.let { dec(it, 3) } ?: "") }
    var unit by remember(route) { mutableStateOf(existing?.unit ?: AreaUnit.CENTS) }
    var price by remember(route) { mutableStateOf(existing?.askingPrice?.takeIf { it > 0 }?.toString() ?: "") }
    var url by remember(route) { mutableStateOf(existing?.sourceUrl ?: route.prefillUrl) }
    var caption by remember(route) { mutableStateOf(existing?.sourceCaption ?: route.prefillCaption) }
    var brokerId by remember(route) { mutableStateOf(existing?.brokerId) }
    var status by remember(route) { mutableStateOf(existing?.status ?: DealStatus.NEW) }
    var notes by remember(route) { mutableStateOf(existing?.notes ?: "") }
    var quickBroker by remember { mutableStateOf(false) }

    val platform = detectPlatform(url)
    val sizeVal = parseDouble(size)
    val priceVal = parsePrice(price)

    fun save() {
        val autoTitle = title.trim().ifBlank {
            type.tr().of(lang) + if (locality.isNotBlank()) " – " + locality.trim() else ""
        }
        val base = existing ?: Property(id = newId(), title = autoTitle)
        val saved = base.copy(
            title = autoTitle,
            type = type,
            locality = locality.trim(),
            taluk = taluk.trim(),
            district = district.trim(),
            lat = parseDouble(lat),
            lng = parseDouble(lng),
            size = sizeVal ?: 0.0,
            unit = unit,
            askingPrice = priceVal ?: 0L,
            sourceUrl = url.trim(),
            sourcePlatform = platform,
            sourceCaption = caption.trim(),
            brokerId = brokerId,
            status = status,
            notes = notes.trim(),
        )
        ctx.repo.upsertProperty(saved)
        if (existing == null) ctx.nav.replaceTop(Route.PropertyDetail(saved.id)) else ctx.nav.pop()
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = if (existing == null) S.newProperty.s() else S.editProperty.s(),
                onToggleLang = ctx.toggleLang,
                onBack = { ctx.nav.pop() },
                actions = { TextButton(onClick = { save() }) { Text(S.save.s(), color = MaterialTheme.colorScheme.onPrimary) } },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (route.prefillUrl.isNotBlank() && existing == null) {
                SectionCard {
                    Text(S.sharedLinkAdded.s(), style = MaterialTheme.typography.bodyMedium)
                }
            }
            SectionCard(title = S.basics.s()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(title, { title = it }, S.title.s(), supporting = S.titleHint.s())
                    Text(S.type.s(), style = MaterialTheme.typography.labelLarge)
                    ChoiceChips(PropertyType.entries, type, { it.tr().s() }) { type = it }
                    Text(S.status.s(), style = MaterialTheme.typography.labelLarge)
                    ChoiceChips(DealStatus.entries, status, { it.tr().s() }) { status = it }
                }
            }
            SectionCard(title = S.sizeAndPrice.s()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row {
                        Field(size, { size = it }, S.sizeLabel.s(), Modifier.weight(1f), keyboard = KeyboardType.Decimal)
                    }
                    ChoiceChips(AreaUnit.entries, unit, { it.tr().s() }) { unit = it }
                    if (sizeVal != null && sizeVal > 0) {
                        Text(
                            S.sizeConverted.f(dec(toCents(sizeVal, unit), 2), dec(toCents(sizeVal, unit) / 100.0, 3), dec(toSqft(sizeVal, unit), 0)),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Field(price, { price = it }, S.askingPrice.s(), keyboard = KeyboardType.Text, supporting = S.priceInputHint.s())
                    if (priceVal != null && priceVal > 0) {
                        val perCent = if (sizeVal != null && sizeVal > 0) priceVal / toCents(sizeVal, unit) else null
                        val perSqft = if (sizeVal != null && sizeVal > 0) priceVal / toSqft(sizeVal, unit) else null
                        Text(
                            rupees(priceVal) + " = " + priceShort(priceVal, lang) +
                                (perCent?.let { "  •  " + priceShort(it.toLong(), lang) + S.perCentSlash.s() } ?: "") +
                                (perSqft?.let { "  •  " + rupees(it.toLong()) + S.perSqftSlash.s() } ?: ""),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            SectionCard(title = S.location.s()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(locality, { locality = it }, S.locality.s(), supporting = S.localityHint.s())
                    Field(taluk, { taluk = it }, S.taluk.s())
                    Field(district, { district = it }, S.district.s())
                    Row {
                        Field(lat, { lat = it }, S.latitude.s(), Modifier.weight(1f), keyboard = KeyboardType.Decimal)
                        Spacer(Modifier.width(8.dp))
                        Field(lng, { lng = it }, S.longitude.s(), Modifier.weight(1f), keyboard = KeyboardType.Decimal)
                    }
                    Text(S.coordsHint.s(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            SectionCard(title = S.source.s()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(url, { url = it }, S.sourceLink.s(), keyboard = KeyboardType.Uri)
                    Row { Pill(S.detected.s() + ": " + platform.tr().s(), platformColor(platform)) }
                    Field(caption, { caption = it }, S.caption.s(), singleLine = false, minLines = 2)
                }
            }
            SectionCard(title = S.broker.s()) {
                val noBroker: Broker? = null
                val options: List<Broker?> = listOf(noBroker) + data.brokers
                DropdownSelector(
                    label = S.selectBroker.s(),
                    current = brokerId?.let { id -> data.brokers.firstOrNull { it.id == id }?.name } ?: S.noBroker.s(),
                    options = options,
                    optionLabel = { it?.name ?: S.noBroker.of(lang) },
                    onSelect = { brokerId = it?.id },
                )
                TextButton(onClick = { quickBroker = true }) { Text(S.addNewBroker.s()) }
            }
            SectionCard(title = S.notes.s()) {
                Field(notes, { notes = it }, S.notes.s(), singleLine = false, minLines = 3)
            }
            PrimaryButton(S.save.s(), { save() }, Modifier.fillMaxWidth(), icon = Icons.Filled.Check)
            Spacer(Modifier.height(40.dp))
        }
    }

    if (quickBroker) {
        QuickBrokerDialog(
            onDismiss = { quickBroker = false },
            onSave = { name, phone ->
                val b = Broker(id = newId(), name = name, phone = phone, whatsapp = phone, area = locality.trim())
                ctx.repo.upsertBroker(b)
                brokerId = b.id
                quickBroker = false
            },
        )
    }
}

@Composable
fun QuickBrokerDialog(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(S.addNewBroker.s()) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(name, { name = it }, S.name.s())
                Field(phone, { phone = it }, S.phone.s(), keyboard = KeyboardType.Phone)
            }
        },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onSave(name.trim(), phone.trim()) }, enabled = name.isNotBlank()) { Text(S.save.s()) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(S.cancel.s()) } },
    )
}
