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
import com.hark.sothudiary.domain.Geocoder
import com.hark.sothudiary.domain.BROWSER_USER_AGENT
import com.hark.sothudiary.domain.social.SocialPost
import com.hark.sothudiary.domain.social.fetchComments
import com.hark.sothudiary.domain.social.platformOf
import com.hark.sothudiary.domain.social.readPost
import com.hark.sothudiary.domain.social.coverCandidates
import com.hark.sothudiary.domain.social.downloadCover
import com.hark.sothudiary.domain.social.imageExt
import com.hark.sothudiary.domain.social.secondSlide
import com.hark.sothudiary.domain.factsTitle
import com.hark.sothudiary.domain.isPlaceholderTitle
import com.hark.sothudiary.domain.smartTitle
import com.hark.sothudiary.data.SourcePlatform
import com.hark.sothudiary.httpBytes
import com.hark.sothudiary.i18n.tr
import com.hark.sothudiary.ui.components.ClaimChips
import com.hark.sothudiary.ui.reader.PostReaderOverlay
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
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

    // For shares the title is made from the post (facts first, then the caption's first meaningful line) – never "Instagram".
    val initialTitle = existing?.title ?: ""
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
    var photos by remember(route) { mutableStateOf(existing?.photos ?: emptyList()) }
    var folderIds by remember(route) { mutableStateOf(existing?.folderIds ?: setOfNotNull(route.folderId)) }
    var auto by remember(route) { mutableStateOf(setOf<String>()) }
    var typeTouched by remember(route) { mutableStateOf(existing != null) }
    var reading by remember(route) { mutableStateOf(false) }
    var newBrokerPhone by remember(route) { mutableStateOf("") }
    var newBrokerName by remember(route) { mutableStateOf("") }
    var createBroker by remember(route) { mutableStateOf(true) }
    var reviews by remember(route) { mutableStateOf(existing?.reviews ?: emptyList()) }
    var claims by remember(route) { mutableStateOf(existing?.postClaims ?: emptySet()) }
    var readStep by remember(route) { mutableStateOf("") }
    var readMethods by remember(route) { mutableStateOf(listOf<String>()) }
    var captionMissing by remember(route) { mutableStateOf(false) }
    var showReader by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val ytKey = data.settings.youtubeApiKey
    val isShare = existing == null && (route.prefillUrl.isNotBlank() || route.prefillCaption.isNotBlank() || route.prefillImages.isNotEmpty())

    /** Fills only fields that are still empty, and marks them as auto-filled. */
    fun apply(pl: ParsedListing, author: String? = null) {
        val a = auto.toMutableSet()
        if (title.isBlank() || "title" in a || isPlaceholderTitle(title)) {
            val place = pl.placeText ?: pl.district
            val t = factsTitle(pl.size, pl.unit, pl.type, place, lang)
                ?: pl.title?.takeIf { it.count(Char::isLetter) >= 6 && !isPlaceholderTitle(it) }?.take(80)
                ?: factsTitle(null, null, pl.type, place, lang, requireSize = false)
            if (!t.isNullOrBlank()) { title = t; a += "title" }
        }
        if (caption.isBlank() && !pl.description.isNullOrBlank()) { caption = pl.description; a += "caption" }
        if (price.isBlank() && pl.totalPrice != null) { price = pl.totalPrice.toString(); a += "price" }
        if (!typeTouched && pl.type != null) { type = pl.type; typeTouched = true; a += "type" }
        if (size.isBlank()) {
            val bu = pl.builtUpSqft
            if (bu != null && (type == PropertyType.HOUSE || type == PropertyType.APARTMENT)) {
                size = dec(bu, 0); unit = AreaUnit.SQFT; a += "size"
            } else if (pl.size != null) {
                size = dec(pl.size, 3); unit = pl.unit ?: unit; a += "size"
            }
        }
        if (pl.placeText != null && locality.isBlank()) {
            locality = pl.placeText; a += "locality"
        }
        val d = pl.district
        if (district.isBlank() && d != null) { district = d; a += "district" }
        if (pl.claims.isNotEmpty()) { claims = claims + pl.claims.map { it.name }; a += "claims" }
        if (brokerId == null) {
            val phone = pl.phones.firstOrNull()
            if (phone != null && newBrokerPhone.isBlank()) {
                val match = data.brokers.firstOrNull { b ->
                    listOf(b.phone, b.whatsapp).any { it.filter(Char::isDigit).takeLast(10) == phone }
                }
                if (match != null) { brokerId = match.id; a += "broker" }
                else { newBrokerPhone = phone; a += "broker" }
            }
            if (newBrokerPhone.isNotBlank() && newBrokerName.isBlank()) {
                (pl.brokerName ?: author)?.let { newBrokerName = it }
            }
        }
        auto = a
    }

    val allTexts = remember(route) { mutableListOf<String>() }

    suspend fun ocrFile(file: String) {
        readStep = S.stepOcr.of(lang)
        val ocr = suspendCancellableCoroutine<String?> { cont -> ctx.platform.recognizeText(file) { if (cont.isActive) cont.resume(it) } }
        if (!ocr.isNullOrBlank()) {
            allTexts += ocr
            apply(ListingParser.parse(allTexts.joinToString("\n")))
        }
    }

    /** Uses a post read from the link or the in-app reader: text, picture (saved + OCR), comments. */
    suspend fun ingest(post: SocialPost) {
        readMethods = (readMethods + post.methods).distinct()
        val text = listOfNotNull(post.caption, post.title?.takeIf { t -> post.caption?.contains(t.take(30)) != true }).joinToString("\n")
        if (text.isNotBlank()) {
            allTexts.add(0, text)
            apply(ListingParser.parse(allTexts.joinToString("\n")), post.author)
            captionMissing = false
        }
        if (post.comments.isNotEmpty()) reviews = (post.comments + reviews).distinctBy { it.author + it.text.take(60) }.take(15)
        // Cover: YouTube maxres → hq thumbnail; Instagram / Facebook post media (carousel: first slide);
        // the reader's capture of the media as the last resort. CDN links expire, so a copy is kept.
        val candidates = coverCandidates(url.ifBlank { post.url }, post)
        if (imageFile.isBlank() && (candidates.isNotEmpty() || post.snapshot != null)) {
            readStep = S.stepImage.of(lang)
            val saved = downloadCover(candidates, post.snapshot) { b, ext -> ctx.platform.saveImage(b, ext) }
            if (saved != null) {
                imageFile = saved.file
                if (saved.url.isNotBlank()) imageUrl = saved.url
                ocrFile(saved.file)
            } else if (imageUrl.isBlank()) candidates.firstOrNull()?.let { imageUrl = it }
            secondSlide(post)?.let { second ->
                val b = runCatching { httpBytes(second, BROWSER_USER_AGENT) }.getOrNull()
                b?.let { ctx.platform.saveImage(it, imageExt(second, it)) }?.let { f -> if (f !in photos) photos = photos + f }
            }
        }
    }

    /** Finds the map pin for the place named in the post (Nominatim, anywhere in India; the named district wins). */
    suspend fun geocodeIfNeeded() {
        if (lat != null) return
        val pl = ListingParser.parse(allTexts.joinToString("\n"))
        val queries = (pl.geocodeQueries() + listOfNotNull(locality.takeIf { it.isNotBlank() }?.let { listOf(it, district).filter { x -> x.isNotBlank() }.joinToString(", ") })).distinct()
        if (queries.isEmpty()) return
        readStep = S.stepLocating.of(lang)
        val wantDistrict = pl.district ?: district
        for (q in queries.take(4)) {
            val results = Geocoder.search(q, limit = 5)
            val hit = results.firstOrNull { r -> wantDistrict.isNotBlank() && (r.district.contains(wantDistrict, true) || r.displayName.contains(wantDistrict, true)) }
                ?: results.firstOrNull { wantDistrict.isBlank() && it.state.contains("Tamil", true) }
                ?: results.firstOrNull { wantDistrict.isBlank() }
            if (hit != null && lat == null) {
                lat = hit.lat; lng = hit.lng; address = hit.displayName
                if (locality.isBlank() || "locality" in auto) { locality = q.substringBefore(",").trim(); auto = auto + "locality" }
                if (taluk.isBlank() && hit.taluk.isNotBlank()) taluk = hit.taluk
                if (district.isBlank() && hit.district.isNotBlank()) { district = hit.district; auto = auto + "district" }
                auto = auto + "pin"
                return
            }
        }
    }

    LaunchedEffect(route) {
        if (!isShare) return@LaunchedEffect
        reading = true
        allTexts += route.prefillCaption
        apply(ListingParser.parse(route.prefillCaption))
        // Poster / screenshot text (on-device OCR)
        route.prefillImages.firstOrNull()?.let { ocrFile(it) }
        // The link: YouTube description, Instagram / Facebook caption (crawler preview, embed page, mobile pages)
        if (route.prefillUrl.isNotBlank()) {
            readStep = S.stepLink.of(lang)
            val post = runCatching { readPost(route.prefillUrl, ytKey) }.getOrNull()
            ingest(post ?: SocialPost(route.prefillUrl, platformOf(route.prefillUrl)))
            if (imageFile.isBlank() && imageUrl.isBlank()) youtubeThumb(route.prefillUrl)?.let { imageUrl = it }
            val p = platformOf(route.prefillUrl)
            if ((p == SourcePlatform.INSTAGRAM || p == SourcePlatform.FACEBOOK) && post?.caption.isNullOrBlank() && route.prefillCaption.length < 40) {
                captionMissing = true
            }
            // YouTube comments as user reviews (best effort, no key needed).
            if (p == SourcePlatform.YOUTUBE && reviews.isEmpty()) {
                readStep = S.stepComments.of(lang)
                reviews = runCatching { fetchComments(route.prefillUrl, ytKey) }.getOrDefault(emptyList())
            }
        }
        geocodeIfNeeded()
        readStep = ""
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
        val autoTitle = title.trim().takeUnless { isPlaceholderTitle(it) }
            ?: smartTitle(caption, lang)
            ?: (type.tr().of(lang) + if (locality.isNotBlank()) ", " + locality.trim() else "")
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
            photos = photos,
            folderIds = folderIds,
            notes = notes.trim(),
            reviews = reviews,
            reviewsFetchedAt = if (reviews.isNotEmpty() && existing?.reviews != reviews) com.hark.sothudiary.data.nowMillis() else existing?.reviewsFetchedAt ?: 0L,
            postClaims = claims,
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
            // 2.6.0: a property sent from another phone as a .sothu file can be imported instead of typed in.
            if (existing == null && !isShare) SectionCard { ImportSothuButton(ctx, showHint = true) }
            if (isShare) {
                SectionCard(accent = AutoFillColor) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = AutoFillColor)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (reading) (readStep.ifBlank { S.readingShare.s() }) else if (auto.isEmpty()) S.sharedLinkAdded.s() else S.autoFilledBanner.f(auto.size),
                            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                        )
                        if (reading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                    if (!reading && readMethods.isNotEmpty()) {
                        Text(S.readVia.f(readMethods.joinToString(", ")), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (!reading && (captionMissing || platformOf(url).let { it == SourcePlatform.INSTAGRAM || it == SourcePlatform.FACEBOOK })) {
                        if (captionMissing) Text(S.captionMissing.s(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { showReader = true }) {
                                Icon(Icons.Filled.TravelExplore, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                                Text(S.readPost.s())
                            }
                        }
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
                    if (claims.isNotEmpty()) {
                        Text(S.postClaims.s(), style = MaterialTheme.typography.labelLarge)
                        ClaimChips(claims)
                    }
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
                    if (la != null && ln != null && com.hark.sothudiary.ui.map.isValidCoord(la, ln)) {
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

    if (showReader) {
        PostReaderOverlay(
            url = url,
            onResult = { post ->
                showReader = false
                scope.launch {
                    reading = true
                    ingest(post)
                    geocodeIfNeeded()
                    readStep = ""
                    reading = false
                }
            },
            onDismiss = { showReader = false },
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
