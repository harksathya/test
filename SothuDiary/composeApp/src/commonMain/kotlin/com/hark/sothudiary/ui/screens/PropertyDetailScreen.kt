package com.hark.sothudiary.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.hark.sothudiary.data.AppData
import com.hark.sothudiary.data.DealStatus
import com.hark.sothudiary.data.DocItem
import com.hark.sothudiary.data.Interaction
import com.hark.sothudiary.data.Property
import com.hark.sothudiary.data.SourcePlatform
import com.hark.sothudiary.data.nowMillis
import com.hark.sothudiary.domain.dec
import com.hark.sothudiary.domain.formatDate
import com.hark.sothudiary.domain.formatDateTime
import com.hark.sothudiary.domain.priceShort
import com.hark.sothudiary.domain.pricing.Verdict
import com.hark.sothudiary.domain.pricing.checkAll
import com.hark.sothudiary.domain.pricing.keyDocs
import com.hark.sothudiary.domain.pricing.worthIt
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
import com.hark.sothudiary.ui.components.Pill
import com.hark.sothudiary.ui.components.PriceCheckCard
import com.hark.sothudiary.domain.formatDateShort
import com.hark.sothudiary.ui.components.ReviewsSection
import com.hark.sothudiary.ui.components.ClaimChips
import com.hark.sothudiary.ui.components.GeminiKeyDialog
import com.hark.sothudiary.ui.components.AI_STUDIO_URL
import com.hark.sothudiary.ui.reader.PostReaderOverlay
import com.hark.sothudiary.domain.social.fetchComments
import com.hark.sothudiary.domain.social.platformOf
import com.hark.sothudiary.domain.pricing.liveLookup
import com.hark.sothudiary.domain.pricing.freshLookup
import com.hark.sothudiary.domain.pricing.latestLookup
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.hark.sothudiary.ui.components.SectionCard
import com.hark.sothudiary.ui.components.WorthItCard
import com.hark.sothudiary.ui.components.locationLine
import com.hark.sothudiary.ui.components.platformColor
import com.hark.sothudiary.ui.components.sizeLine
import com.hark.sothudiary.ui.components.unitPriceLine
import com.hark.sothudiary.ui.theme.statusColor
import com.hark.sothudiary.ui.theme.verdictColor
import com.hark.sothudiary.ui.theme.verdictArgb
import com.hark.sothudiary.ui.theme.LocalBrand
import com.hark.sothudiary.ui.map.GeoMap
import com.hark.sothudiary.ui.map.MapPin
import com.hark.sothudiary.ui.map.platformHasMap
import com.hark.sothudiary.ui.components.VoiceNotePlayer
import com.hark.sothudiary.domain.group
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.FilledTonalButton
import com.hark.sothudiary.ui.components.PropertyImage
import com.hark.sothudiary.ui.components.propertyImageModel
import com.hark.sothudiary.ui.components.SourceLogo
import com.hark.sothudiary.domain.social.coverCandidates
import com.hark.sothudiary.domain.social.downloadCover
import com.hark.sothudiary.domain.social.imageExt
import com.hark.sothudiary.domain.social.secondSlide
import com.hark.sothudiary.domain.smartTitle
import com.hark.sothudiary.domain.BROWSER_USER_AGENT
import com.hark.sothudiary.httpBytes

const val TN_REGINET_URL = "https://tnreginet.gov.in/portal/"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PropertyDetailScreen(ctx: AppContext, id: String) {
    val data = ctx.repo.data
    val lang = LocalLang.current
    val p = data.properties.firstOrNull { it.id == id }
    if (p == null) {
        Scaffold(topBar = { AppTopBar(S.propertyDetails.s(), ctx.toggleLang, onBack = { ctx.nav.pop() }) }) { pad ->
            Box(Modifier.padding(pad)) { EmptyState(Icons.Filled.LocationOn, S.notFound.s(), "") }
        }
        return
    }
    val checks = remember(data) { checkAll(data) }
    val check = checks[p.id] ?: return
    val now = nowMillis()
    val worth = worthIt(p, check, data, checks, now)
    val broker = p.brokerId?.let { bid -> data.brokers.firstOrNull { it.id == bid } }
    val timeline = data.interactions.filter { it.propertyId == p.id }.sortedByDescending { it.at }
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var findingOnline by remember(p.id) { mutableStateOf(false) }
    var onlineMessage by remember(p.id) { mutableStateOf<String?>(null) }
    var askGeminiKey by remember { mutableStateOf(false) }
    var loadingReviews by remember(p.id) { mutableStateOf(false) }
    var readerOpen by remember { mutableStateOf(false) }
    val msgNoReviews = S.noReviewsFound.of(lang)

    val pLat = p.lat
    val pLng = p.lng
    val group = p.type.group()
    val latest = if (pLat != null && pLng != null) latestLookup(data, pLat, pLng, group) else null

    /** Live price lookup for this property's own coordinates and type (cached for 30 days). */
    fun runLookup(force: Boolean) {
        val key = data.settings.geminiApiKey
        if (key.isBlank()) { if (force) askGeminiKey = true; return }
        if (pLat == null || pLng == null) { onlineMessage = S.rsNoPin.of(lang); return }
        if (!force && freshLookup(data, pLat, pLng, group) != null) return
        if (findingOnline) return
        findingOnline = true
        onlineMessage = null
        scope.launch {
            val hint = listOf(p.locality, p.taluk, p.district).filter { it.isNotBlank() }.joinToString(", ").ifBlank { p.address }
            val r = runCatching { liveLookup(key, data.settings.geminiModel, pLat, pLng, group, hint) }.getOrNull()
            if (r != null) ctx.repo.saveLookup(r.lookup, r.rates)
            onlineMessage = when {
                r == null -> S.onlineError.format(lang, "network")
                r.error == "no_key" -> S.geminiNeedKey.of(lang)
                // Shown once, by the friendly error note (from the saved lookup), not here as well.
                r.error != null -> null
                r.rates.isEmpty() -> S.onlineNone.format(lang, r.lookup.skipped)
                else -> S.onlineSaved.format(lang, r.rates.size, r.lookup.skipped)
            }
            findingOnline = false
        }
    }

    // Price check is a live lookup: run it when there is no recent one for this place + type.
    LaunchedEffect(p.id, pLat, pLng, group, data.settings.geminiApiKey) { runLookup(force = false) }

    fun refreshReviews() {
        if (p.sourceUrl.isBlank()) return
        loadingReviews = true
        scope.launch {
            val list = runCatching { fetchComments(p.sourceUrl, data.settings.youtubeApiKey) }.getOrDefault(emptyList())
            if (list.isNotEmpty()) ctx.repo.setReviews(p.id, list) else ctx.platform.toast(msgNoReviews)
            loadingReviews = false
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = S.propertyDetails.s(),
                onToggleLang = ctx.toggleLang,
                onBack = { ctx.nav.pop() },
                actions = {
                    IconButton(onClick = { ctx.nav.push(Route.PropertyEdit(p.id)) }) { Icon(Icons.Filled.Edit, contentDescription = S.edit.s()) }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = S.delete.s()) }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { ctx.nav.push(Route.InteractionEdit(null, p.id, p.brokerId)) },
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
            // ---- Hero photo ----
            val image = propertyImageModel(ctx.platform, p)
            PropertyImage(image, p.type, Modifier.fillMaxWidth().height(if (ctx.wide) 240.dp else 210.dp).clip(RoundedCornerShape(20.dp))) {
                if (image != null) Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.6f))))
                Row(Modifier.align(Alignment.TopEnd).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (p.sourceUrl.isNotBlank()) {
                        FilledTonalButton(onClick = { ctx.platform.openUrl(p.sourceUrl) }) {
                            SourceLogo(p.sourcePlatform, 18.dp); Spacer(Modifier.width(6.dp)); Text(S.openPost.s())
                        }
                    }
                    FilledTonalButton(onClick = {
                        ctx.platform.pickImage { name ->
                            if (name != null) {
                                if (p.imageFile.isNotBlank()) ctx.platform.deleteImage(p.imageFile)
                    p.photos.forEach { ctx.platform.deleteImage(it) }
                                ctx.repo.setPropertyImage(p.id, name, null)
                            }
                        }
                    }) {
                        Icon(Icons.Filled.AddAPhoto, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                        Text(if (p.imageFile.isBlank()) S.addPhoto.s() else S.changePhoto.s())
                    }
                }
                Text(
                    if (p.askingPrice > 0) priceShort(p.askingPrice, lang) else S.priceOnRequest.s(),
                    color = if (image != null) Color.White else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.BottomStart).padding(14.dp),
                )
            }

            // ---- Extra photos from the post (e.g. the second carousel slide); tap one to make it the cover ----
            if (p.photos.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (listOfNotNull(p.imageFile.ifBlank { null }) + p.photos).forEach { f ->
                        PropertyImage(ctx.platform.imagePath(f), p.type,
                            Modifier.size(56.dp).clip(RoundedCornerShape(12.dp))
                                .border(if (f == p.imageFile) 2.dp else 0.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                                .clickable { ctx.repo.makeCover(p.id, f) })
                    }
                }
            }

            // ---- Header ----
            SectionCard {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Pill(p.type.tr().s(), MaterialTheme.colorScheme.primary)
                    Pill(p.status.tr().s(), statusColor(p.status))
                    Pill(check.verdict.tr().s(), verdictColor(check.verdict), filled = check.verdict != Verdict.UNKNOWN)
                }
                Spacer(Modifier.height(8.dp))
                Text(p.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(p.locationLine().ifBlank { "-" }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
                Text(unitPriceLine(p), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text(S.sizeLabel.s() + ": " + sizeLine(p), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    S.addedUpdated.f(formatDate(p.createdAt, lang), formatDate(p.updatedAt, lang)),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            WorthItCard(worth)
            PriceCheckCard(
                p, check,
                onOpenProperty = { ctx.nav.push(Route.PropertyDetail(it)) },
                onOpenUrl = { ctx.platform.openUrl(it) },
                onAreaRates = { ctx.nav.push(Route.AreaRates(p.id, p.type.group())) },
                onFindOnline = { runLookup(force = true) },
                findingOnline = findingOnline,
                onlineMessage = onlineMessage,
                lookupStatus = latest?.takeIf { it.error == null }?.let { l ->
                    S.lookupStatus.format(lang, formatDateShort(l.fetchedAt, lang), l.found, l.place.ifBlank { "—" })
                },
                lookupError = latest?.takeIf { it.error != null && it.error != "no_key" },
                needsKey = data.settings.geminiApiKey.isBlank(),
                onSetupKey = { askGeminiKey = true },
            )

            // ---- User reviews (comments on the source post) ----
            if (p.sourceUrl.isNotBlank() || p.reviews.isNotEmpty()) {
                val plat = platformOf(p.sourceUrl)
                ReviewsSection(
                    reviews = p.reviews,
                    loading = loadingReviews,
                    fetchedLabel = if (p.reviewsFetchedAt > 0) S.fetchedOn.f(formatDateShort(p.reviewsFetchedAt, lang)) else null,
                    onRefresh = if (plat == SourcePlatform.YOUTUBE || plat == SourcePlatform.INSTAGRAM) ({ refreshReviews() }) else null,
                    onReadInApp = if ((plat == SourcePlatform.INSTAGRAM || plat == SourcePlatform.FACEBOOK) && ctx.platform.hasWebReader) ({ readerOpen = true }) else null,
                )
            }

            // ---- Folders ----
            SectionCard(title = S.folders.s(), icon = Icons.Filled.Folder) {
                FolderChips(ctx, p)
            }

            // ---- Status pipeline ----
            SectionCard(title = S.status.s()) {
                ChoiceChips(DealStatus.entries, p.status, { it.tr().s() }) { ctx.repo.setStatus(p.id, it) }
            }

            // ---- Broker ----
            SectionCard(title = S.broker.s()) {
                if (broker == null) {
                    Text(S.noBrokerLinked.s(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { ctx.nav.push(Route.PropertyEdit(p.id)) }) { Text(S.linkBroker.s()) }
                } else {
                    Row(
                        Modifier.fillMaxWidth().clickable { ctx.nav.push(Route.BrokerDetail(broker.id)) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(broker.name, fontWeight = FontWeight.SemiBold)
                            Text(broker.phone.ifBlank { "-" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { ctx.platform.call(broker.phone) }, enabled = broker.phone.isNotBlank()) {
                            Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(S.call.s())
                        }
                        OutlinedButton(
                            onClick = { ctx.platform.whatsapp(broker.whatsapp.ifBlank { broker.phone }, S.waAbout.of(lang).replace("{0}", p.title)) },
                            enabled = (broker.whatsapp.ifBlank { broker.phone }).isNotBlank(),
                        ) {
                            Icon(Icons.Filled.Chat, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(S.whatsapp.s())
                        }
                    }
                }
            }

            // ---- Source ----
            SectionCard(title = S.source.s()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (p.sourcePlatform != SourcePlatform.NONE) { SourceLogo(p.sourcePlatform, 20.dp); Spacer(Modifier.width(8.dp)) }
                    Text(p.sourcePlatform.tr().s(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
                if (p.sourceUrl.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.clickable { ctx.platform.openUrl(p.sourceUrl) }, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(p.sourceUrl, color = MaterialTheme.colorScheme.primary, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (p.sourceCaption.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text("“" + p.sourceCaption + "”", style = MaterialTheme.typography.bodyMedium)
                }
                if (p.postClaims.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(S.postClaims.s(), style = MaterialTheme.typography.labelLarge)
                    ClaimChips(p.postClaims)
                }
            }

            // ---- Documents ----
            val required = keyDocs(p.type)
            SectionCard(title = S.documents.s(), trailing = { Text(S.docsCount.f(p.docs.size, DocItem.entries.size), style = MaterialTheme.typography.labelLarge) }) {
                DocItem.entries.forEach { d ->
                    Row(
                        Modifier.fillMaxWidth().clickable { ctx.repo.toggleDoc(p.id, d) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = d in p.docs, onCheckedChange = { ctx.repo.toggleDoc(p.id, d) })
                        Text(d.tr().s(), modifier = Modifier.weight(1f))
                        if (d in required && d !in p.docs) Pill(S.keyDoc.s(), MaterialTheme.colorScheme.error)
                    }
                }
            }

            // ---- Location ----
            SectionCard(title = S.location.s(), icon = Icons.Filled.LocationOn) {
                Text(p.locationLine().ifBlank { "-" }, fontWeight = FontWeight.Medium)
                if (p.address.isNotBlank()) Text(p.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val lat = p.lat
                val lng = p.lng
                if (lat != null && lng != null) {
                    if (platformHasMap && com.hark.sothudiary.ui.map.isValidCoord(lat, lng)) {
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.fillMaxWidth().height(180.dp).clip(MaterialTheme.shapes.medium)) {
                            GeoMap(
                                modifier = Modifier.fillMaxSize(),
                                pins = listOf(MapPin(p.id, lat, lng, p.title, color = verdictArgb(check.verdict))),
                                centerLat = lat, centerLng = lng, zoom = 15.0, cameraKey = lat to lng,
                            )
                        }
                    }
                    Text(S.coordinates.s() + ": " + dec(lat, 5) + ", " + dec(lng, 5), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                } else {
                    Text(S.noPinYet.s(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { ctx.nav.push(Route.PropertyEdit(p.id)) }) { Text(S.pickOnMap.s()) }
                }
                TextButton(onClick = { ctx.platform.openMap(lat, lng, p.locationLine().ifBlank { p.title }) }) {
                    Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(S.openGoogleMaps.s())
                }
            }

            if (p.notes.isNotBlank()) {
                SectionCard(title = S.notes.s()) { Text(p.notes) }
            }

            // ---- Timeline ----
            SectionCard(title = S.timeline.s(), trailing = {
                TextButton(onClick = { ctx.nav.push(Route.InteractionEdit(null, p.id, p.brokerId)) }) { Text(S.add.s()) }
            }) {
                if (timeline.isEmpty()) {
                    Text(S.noInteractions.s(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    InteractionTimeline(ctx, timeline, data, showProperty = false)
                }
            }
            Spacer(Modifier.height(80.dp))
        }
    }

    if (readerOpen) {
        PostReaderOverlay(
            url = p.sourceUrl,
            onResult = { post ->
                readerOpen = false
                if (post.comments.isNotEmpty()) ctx.repo.setReviews(p.id, (post.comments + p.reviews).distinctBy { it.author + it.text.take(60) }.take(15))
                else ctx.platform.toast(msgNoReviews)
                // The logged-in reader also gives the post's picture and caption: use them for the cover and a real title.
                scope.launch {
                    val cover = if (p.imageFile.isBlank()) downloadCover(coverCandidates(p.sourceUrl, post), post.snapshot) { b, ext -> ctx.platform.saveImage(b, ext) } else null
                    val extra = secondSlide(post)?.let { u -> runCatching { httpBytes(u, BROWSER_USER_AGENT) }.getOrNull()?.let { ctx.platform.saveImage(it, imageExt(u, it)) } }
                    ctx.repo.setCover(p.id, cover?.file, cover?.url, extra, title = smartTitle(post.caption, lang), caption = post.caption)
                }
            },
            onDismiss = { readerOpen = false },
        )
    }

    if (askGeminiKey) {
        GeminiKeyDialog(
            current = data.settings.geminiApiKey,
            onDismiss = { askGeminiKey = false },
            onSave = { k ->
                ctx.repo.setApiKeys(data.settings.youtubeApiKey, k, data.settings.geminiModel)
                askGeminiKey = false
            },
            onOpenStudio = { ctx.platform.openUrl(AI_STUDIO_URL) },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(S.deletePropertyQ.s()) },
            text = { Text(S.deletePropertyBody.s()) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    data.interactions.filter { it.propertyId == p.id }.flatMap { it.voiceNotes }.forEach { ctx.platform.audio.deleteAudio(it.fileName) }
                    if (p.imageFile.isNotBlank()) ctx.platform.deleteImage(p.imageFile)
                    p.photos.forEach { ctx.platform.deleteImage(it) }
                    ctx.repo.deleteProperty(p.id)
                    ctx.nav.pop()
                }) {
                    Text(S.delete.s(), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(S.cancel.s()) } },
        )
    }
}

/**
 * Timeline of interactions (newest first). Price changes between consecutive quotes are highlighted.
 */
@Composable
fun InteractionTimeline(ctx: AppContext, items: List<Interaction>, data: AppData, showProperty: Boolean) {
    val lang = LocalLang.current
    val chronological = items.sortedBy { it.at }
    val now = nowMillis()
    items.forEachIndexed { index, i ->
        val earlier = chronological.filter { it.at < i.at && it.propertyId == i.propertyId && it.quotedPrice != null }.lastOrNull()
        val property: Property? = i.propertyId?.let { pid -> data.properties.firstOrNull { it.id == pid } }
        val broker = i.brokerId?.let { bid -> data.brokers.firstOrNull { it.id == bid } }
        Column(Modifier.fillMaxWidth().clickable { ctx.nav.push(Route.InteractionEdit(i.id, i.propertyId, i.brokerId)) }.padding(vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill(i.type.tr().s(), MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(8.dp))
                Text(formatDateTime(i.at, lang), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            }
            if (showProperty && property != null) {
                Text(property.title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            }
            if (!showProperty && broker != null) {
                Text(S.withBroker.f(broker.name), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
            }
            val q = i.quotedPrice
            if (q != null) {
                val prev = earlier?.quotedPrice
                if (earlier != null && prev != null && prev != q) {
                    val up = q > prev
                    Text(
                        S.quoteChanged.f(priceShort(prev, lang), formatDate(earlier.at, lang), priceShort(q, lang)),
                        color = if (up) verdictColor(Verdict.TOO_HIGH) else verdictColor(Verdict.FAIR),
                        fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp),
                    )
                } else {
                    Text(S.quoted.f(priceShort(q, lang)), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                }
            }
            if (i.summary.isNotBlank()) Text(i.summary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
            if (i.voiceNotes.isNotEmpty()) {
                Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    i.voiceNotes.forEach { vn -> VoiceNotePlayer(ctx, vn, onDelete = null) }
                }
            }
            if (i.promises.isNotBlank()) {
                Text(S.promisesLabel.s() + ": " + i.promises, style = MaterialTheme.typography.bodySmall, color = LocalBrand.current.warnText, modifier = Modifier.padding(top = 2.dp))
            }
            val fu = i.followUpAt
            if (fu != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Event, contentDescription = null, modifier = Modifier.size(16.dp),
                        tint = if (!i.followUpDone && fu < now) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary)
                    Text(
                        " " + S.followUpOn.f(formatDate(fu, lang)) + if (i.followUpDone) " ✓" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (!i.followUpDone && fu < now) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f),
                    )
                    Checkbox(checked = i.followUpDone, onCheckedChange = { ctx.repo.setFollowUpDone(i.id, it) })
                    Text(S.done.s(), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (index < items.size - 1) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
