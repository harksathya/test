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
import androidx.compose.material.icons.filled.EditLocationAlt
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.text.font.FontWeight
import com.hark.sothudiary.PlatformBackHandler
import com.hark.sothudiary.ui.components.ConfirmDialog
import com.hark.sothudiary.ui.map.GeoMap
import com.hark.sothudiary.ui.map.LocationPickerDialog
import com.hark.sothudiary.ui.map.MapPin
import com.hark.sothudiary.ui.map.PickedLocation
import com.hark.sothudiary.ui.theme.LocalBrand
import com.hark.sothudiary.domain.ListingParser
import com.hark.sothudiary.domain.ParsedListing
import com.hark.sothudiary.domain.fetchLinkMeta
import com.hark.sothudiary.domain.youtubeThumb
import com.hark.sothudiary.ui.components.AutoFillColor
import com.hark.sothudiary.ui.components.PropertyImage
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
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
    var district by remember(route) { mutableStateOf(existing?.district ?: "") }
    var lat by remember(route) { mutableStateOf(existing?.lat) }
    var lng by remember(route) { mutableStateOf(existing?.lng) }
    var address by remember(route) { mutableStateOf(existing?.address ?: "") }
    var picking by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var triedSave by remember { mutableStateOf(false) }
    var size by remember(route) { mutableStateOf(existing?.size?.let { dec(it, 3) } ?: "") }
    var unit by remember(route) { mutableStateOf(existing?.unit ?: AreaUnit.CENTS) }
    var price by remember(route) { mutableStateOf(existing?.askingPrice?.takeIf { it > 0 }?.toString() ?: "") }
    var url by remember(route) { mutableStateOf(existing?.sourceUrl ?: route.prefillUrl) }
    var caption by remember(route) { mutableStateOf(existing?.sourceCaption ?: route.prefillCaption) }
    var brokerId by remember(route) { mutableStateOf(existing?.brokerId) }
    var status by remember(route) { mutableStateOf(existing?.status ?: DealStatus.NEW) }
    var notes by remember(route) { mutableStateOf(existing?.notes ?: "") }
    var quickBroker by remember { mutableStateOf(false) }
    var imageFile by remember(route) { mutableStateOf(existing?.imageFile ?: route.prefillImages.firstOrNull() ?: "") }
    var imageUrl by remember(route) { mutableStateOf(existing?.imageUrl ?: "") }
    var folderIds by remember(route) { mutableStateOf(existing?.folderIds ?: setOfNotNull(route.folderId)) }
    var auto by remember(route) { mutableStateOf(setOf<String>()) }
    var typeTouched by remember(route) { mutableStateOf(existing != null) }
    var reading by remember(route) { mutableStateOf(false) }
    var newBrokerPhone by remember(route) { mutableStateOf("") }
    var newBrokerName by remember(route) { mutableStateOf("") }
    var createBroker by remember(route) { mutableStateOf(true) }
    val isShare = existing == null && (route.prefillUrl.isNotBlank() || route.prefillCaption.isNotBlank() || route.prefillImages.isNotEmpty())

    /** Fills only fields that are still empty, and marks them as auto-filled. */
    fun apply(pl: ParsedListing, author: String? = null) {
        val a = auto.toMutableSet()
        if (title.isBlank() && !pl.title.isNullOrBlank()) { title = pl.title; a += "title" }
        if (caption.isBlank() && !pl.description.isNullOrBlank()) { caption = pl.description; a += "caption" }
        if (price.isBlank() && pl.totalPrice != null) { price = pl.totalPrice.toString(); a += "price" }
        if (size.isBlank() && pl.size != null) {
            size = dec(pl.size, 3); unit = pl.unit ?: unit; a += "size"
        }
        if (!typeTouched && pl.type != null) { type = pl.type; typeTouched = true; a += "type" }
        val loc = pl.locality
        if (loc != null && locality.isBlank()) {
            locality = loc.name.substringBefore("(").trim(); a += "locality"
            if (lat == null && loc.lat != null && loc.lng != null) { lat = loc.lat; lng = loc.lng; a += "pin" }
        }
        if (district.isBlank() && (pl.district != null || loc != null)) { district = pl.district ?: "Madurai"; a += "district" }
        if (brokerId == null && newBrokerPhone.isBlank()) {
            val phone = pl.phones.firstOrNull()
            if (phone != null) {
                val match = data.brokers.firstOrNull { b ->
                    listOf(b.phone, b.whatsapp).any { it.filter(Char::isDigit).takeLast(10) == phone }
                }
                if (match != null) { brokerId = match.id; a += "broker" }
                else { newBrokerPhone = phone; newBrokerName = author ?: ""; a += "broker" }
            }
        }
        auto = a
    }

    LaunchedEffect(route) {
        if (!isShare) return@LaunchedEffect
        reading = true
        val texts = mutableListOf(route.prefillCaption)
        apply(ListingParser.parse(route.prefillCaption))
        // Poster / screenshot text (on-device OCR)
        route.prefillImages.firstOrNull()?.let { img ->
            val ocr = suspendCancellableCoroutine<String?> { cont -> ctx.platform.recognizeText(img) { if (cont.isActive) cont.resume(it) } }
            if (!ocr.isNullOrBlank()) {
                texts += ocr
                apply(ListingParser.parse(texts.joinToString("\n")))
            }
        }
        // Link preview (YouTube oEmbed / Open Graph)
        if (route.prefillUrl.isNotBlank()) {
            val meta = fetchLinkMeta(route.prefillUrl)
            if (meta != null) {
                if (imageFile.isBlank() && imageUrl.isBlank() && !meta.image.isNullOrBlank()) imageUrl = meta.image
                val metaText = listOfNotNull(meta.title, meta.description).joinToString("\n")
                if (metaText.isNotBlank()) apply(ListingParser.parse(metaText + "\n" + texts.joinToString("\n")), meta.author)
            }
            if (imageFile.isBlank() && imageUrl.isBlank()) youtubeThumb(route.prefillUrl)?.let { imageUrl = it }
        }
        reading = false
    }

    val platform = detectPlatform(url)
    val sizeVal = parseDouble(size)
    val priceVal = parsePrice(price)
    val sizeError = when {
        size.isBlank() -> null
        sizeVal == null -> S.errNumber.s()
        sizeVal <= 0 -> S.errPositive.s()
        else -> null
    }
    val priceError = when {
        price.isBlank() -> null
        priceVal == null -> S.errPrice.s()
        priceVal <= 0 -> S.errPositive.s()
        else -> null
    }
    val needNameError = if (triedSave && title.isBlank() && locality.isBlank()) S.errTitleOrArea.s() else null
    val dirty = existing?.let {
        it.title != title || it.type != type || it.locality != locality || it.taluk != taluk || it.district != district ||
            it.lat != lat || it.lng != lng || (it.askingPrice.takeIf { a -> a > 0 }?.toString() ?: "") != price ||
            it.sourceUrl != url || it.notes != notes || it.brokerId != brokerId || it.status != status
    } ?: (title.isNotBlank() || locality.isNotBlank() || size.isNotBlank() || price.isNotBlank() || notes.isNotBlank() || lat != null)

    fun leave() { if (dirty) confirmDiscard = true else ctx.nav.pop() }
    PlatformBackHandler(enabled = true) { leave() }

    fun save() {
        triedSave = true
        if (sizeError != null || priceError != null || (title.isBlank() && locality.isBlank())) {
            ctx.platform.toast(S.fixErrors.of(lang))
            return
        }
        val autoTitle = title.trim().ifBlank {
            type.tr().of(lang) + if (locality.isNotBlank()) " – " + locality.trim() else ""
        }
        val newBrokerId: String? = if (brokerId == null && newBrokerPhone.isNotBlank() && createBroker) {
            val b = Broker(
                id = newId(), name = newBrokerName.trim().ifBlank { S.brokerDefaultName.of(lang) + " " + newBrokerPhone.takeLast(4) },
                phone = newBrokerPhone, whatsapp = newBrokerPhone, platform = platform, area = locality.trim(),
            )
            ctx.repo.upsertBroker(b)
            b.id
        } else null
        // Images shared in but not used are removed.
        route.prefillImages.filter { it != imageFile }.forEach { ctx.platform.deleteImage(it) }
        val base = existing ?: Property(id = newId(), title = autoTitle)
        val saved = base.copy(
            title = autoTitle,
            type = type,
            locality = locality.trim(),
            taluk = taluk.trim(),
            district = district.trim(),
            lat = lat,
            lng = lng,
            address = address,
            size = sizeVal ?: 0.0,
            unit = unit,
            askingPrice = priceVal ?: 0L,
            sourceUrl = url.trim(),
            sourcePlatform = platform,
            sourceCaption = caption.trim(),
            brokerId = newBrokerId ?: brokerId,
            status = status,
            imageFile = imageFile,
            imageUrl = imageUrl,
            folderIds = folderIds,
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
                onBack = { leave() },
                actions = { TextButton(onClick = { save() }) { Text(S.save.s(), color = LocalBrand.current.onBar, fontWeight = FontWeight.Bold) } },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (isShare) {
                SectionCard(accent = AutoFillColor) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = AutoFillColor)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (reading) S.readingShare.s() else if (auto.isEmpty()) S.sharedLinkAdded.s() else S.autoFilledBanner.f(auto.size),
                            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                        )
                        if (reading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                }
            }
            // ---- Photo ----
            SectionCard(title = S.photo.s(), icon = Icons.Filled.Image) {
                val model = ctx.platform.imagePath(imageFile) ?: imageUrl.ifBlank { null } ?: youtubeThumb(url)
                if (model != null) {
                    PropertyImage(model, type, Modifier.fillMaxWidth().height(180.dp).clip(MaterialTheme.shapes.large))
                    Spacer(Modifier.height(8.dp))
                }
                if (route.prefillImages.size > 1) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        route.prefillImages.forEach { f ->
                            PropertyImage(ctx.platform.imagePath(f), type, Modifier.size(64.dp).clip(MaterialTheme.shapes.small)
                                .border(if (f == imageFile) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                                .clickable { imageFile = f })
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedButton(onClick = { ctx.platform.pickImage { n -> if (n != null) imageFile = n } }) {
                    Icon(Icons.Filled.AddAPhoto, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                    Text(if (model == null) S.addPhoto.s() else S.changePhoto.s())
                }
            }
            SectionCard(title = S.basics.s()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(title, { title = it }, S.title.s(), supporting = S.titleHint.s(), error = needNameError, autoFilled = "title" in auto)
                    Text(S.type.s(), style = MaterialTheme.typography.labelLarge)
                    ChoiceChips(PropertyType.entries, type, { it.tr().s() }) { type = it; typeTouched = true; auto = auto - "type" }
                    if ("type" in auto) Text("✨ " + S.autoFilledCheck.s(), color = AutoFillColor, style = MaterialTheme.typography.labelMedium)
                    Text(S.status.s(), style = MaterialTheme.typography.labelLarge)
                    ChoiceChips(DealStatus.entries, status, { it.tr().s() }) { status = it }
                }
            }
            SectionCard(title = S.sizeAndPrice.s()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row {
                        Field(size, { size = it }, S.sizeLabel.s(), Modifier.weight(1f), keyboard = KeyboardType.Decimal, error = sizeError, autoFilled = "size" in auto)
                    }
                    ChoiceChips(AreaUnit.entries, unit, { it.tr().s() }) { unit = it }
                    if (sizeVal != null && sizeVal > 0) {
                        Text(
                            S.sizeConverted.f(dec(toCents(sizeVal, unit), 2), dec(toCents(sizeVal, unit) / 100.0, 3), dec(toSqft(sizeVal, unit), 0)),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Field(price, { price = it }, S.askingPrice.s(), keyboard = KeyboardType.Text, supporting = S.priceInputHint.s(), error = priceError, autoFilled = "price" in auto)
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
            SectionCard(title = S.location.s(), icon = Icons.Filled.Place) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val la = lat
                    val ln = lng
                    if (la != null && ln != null) {
                        Box(Modifier.fillMaxWidth().height(170.dp).clip(MaterialTheme.shapes.medium)) {
                            GeoMap(
                                modifier = Modifier.fillMaxSize(),
                                pins = listOf(MapPin("p", la, ln, title, color = 0xFFD32F2F)),
                                centerLat = la, centerLng = ln, zoom = 15.0, cameraKey = la to ln,
                                onTap = { _, _ -> picking = true },
                            )
                        }
                        if (address.isNotBlank()) Text(address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(dec(la, 6) + ", " + dec(ln, 6), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { picking = true }) {
                                Icon(Icons.Filled.EditLocationAlt, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(S.changeOnMap.s())
                            }
                            TextButton(onClick = { lat = null; lng = null; address = "" }) { Text(S.removePin.s()) }
                        }
                    } else {
                        Button(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(S.pickOnMap.s())
                        }
                        Text(S.pinWhy.s(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Field(locality, { locality = it }, S.locality.s(), supporting = S.localityHint.s(), autoFilled = "locality" in auto)
                    Row {
                        Field(taluk, { taluk = it }, S.taluk.s(), Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        Field(district, { district = it }, S.district.s(), Modifier.weight(1f), autoFilled = "district" in auto)
                    }
                }
            }
            SectionCard(title = S.source.s()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(url, { url = it }, S.sourceLink.s(), keyboard = KeyboardType.Uri)
                    Row { Pill(S.detected.s() + ": " + platform.tr().s(), platformColor(platform)) }
                    Field(caption, { caption = it }, S.caption.s(), singleLine = false, minLines = 2, autoFilled = "caption" in auto)
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
                if (brokerId == null && newBrokerPhone.isNotBlank()) {
                    Column(
                        Modifier.fillMaxWidth().background(AutoFillColor.copy(alpha = 0.08f), MaterialTheme.shapes.medium).padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("✨ " + S.phoneFound.f(newBrokerPhone), fontWeight = FontWeight.SemiBold, color = AutoFillColor)
                        Field(newBrokerName, { newBrokerName = it }, S.brokerNameOptional.s())
                        Row(Modifier.clickable { createBroker = !createBroker }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = createBroker, onCheckedChange = { createBroker = it })
                            Text(S.createBrokerOnSave.s(), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                } else if ("broker" in auto && brokerId != null) {
                    Text("✨ " + S.brokerMatched.s(), color = AutoFillColor, style = MaterialTheme.typography.labelMedium)
                }
            }
            SectionCard(title = S.notes.s()) {
                Field(notes, { notes = it }, S.notes.s(), singleLine = false, minLines = 3)
            }
            PrimaryButton(S.save.s(), { save() }, Modifier.fillMaxWidth(), icon = Icons.Filled.Check)
            Spacer(Modifier.height(40.dp))
        }
    }

    if (picking) {
        val la = lat
        val ln = lng
        LocationPickerDialog(
            platform = ctx.platform,
            initial = if (la != null && ln != null) PickedLocation(la, ln, locality, taluk, district, address) else null,
            initialQuery = listOf(locality, taluk, district).filter { it.isNotBlank() }.joinToString(" "),
            onDismiss = { picking = false },
            onPicked = { pl ->
                lat = pl.lat
                lng = pl.lng
                address = pl.address
                if (pl.locality.isNotBlank()) locality = pl.locality
                if (pl.taluk.isNotBlank()) taluk = pl.taluk
                if (pl.district.isNotBlank()) district = pl.district
                picking = false
            },
        )
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = S.discardQ.s(), body = S.discardBody.s(), confirm = S.discard.s(), destructive = true,
            onConfirm = {
                confirmDiscard = false
                if (existing == null) route.prefillImages.forEach { ctx.platform.deleteImage(it) }
                ctx.nav.pop()
            },
            onDismiss = { confirmDiscard = false },
        )
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
        confirmButton = {
            val phoneOk = phone.isBlank() || phone.count { it.isDigit() } >= 10
            TextButton(onClick = { if (name.isNotBlank() && phoneOk) onSave(name.trim(), phone.trim()) }, enabled = name.isNotBlank() && phoneOk) { Text(S.save.s()) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(S.cancel.s()) } },
    )
}
