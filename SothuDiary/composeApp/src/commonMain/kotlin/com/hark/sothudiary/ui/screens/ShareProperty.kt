package com.hark.sothudiary.ui.screens

import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hark.sothudiary.data.Property
import com.hark.sothudiary.domain.share.ImportMode
import com.hark.sothudiary.domain.share.ShareLink
import com.hark.sothudiary.domain.share.ShareOptions
import com.hark.sothudiary.domain.share.SharedProperty
import com.hark.sothudiary.domain.share.encodeShare
import com.hark.sothudiary.domain.share.shareMessage
import com.hark.sothudiary.domain.share.sharedCopy
import com.hark.sothudiary.i18n.LocalLang
import com.hark.sothudiary.i18n.S
import com.hark.sothudiary.i18n.f
import com.hark.sothudiary.i18n.s
import com.hark.sothudiary.domain.priceShort
import com.hark.sothudiary.ui.components.sizeLine
import com.hark.sothudiary.ui.AppContext
import com.hark.sothudiary.ui.components.Field
import com.hark.sothudiary.ui.Route
import com.hark.sothudiary.i18n.S4
import androidx.compose.foundation.layout.padding

/** 2.3.0: the link for [p] with the chosen options (https page link with the data in the fragment). */
fun shareLinkFor(ctx: AppContext, p: Property, o: ShareOptions): String {
    val data = ctx.repo.data
    val land = data.landChecks.firstOrNull { it.propertyId == p.id }
    val broker = p.brokerId?.let { id -> data.brokers.firstOrNull { it.id == id } }
    return ShareLink.httpsLink(encodeShare(sharedCopy(p, land, broker, o)))
}

/**
 * 2.4.0 "Share" sheet: what to include, then
 *  - Share text + link: the Android share sheet with a clean bilingual summary (reads well without the app),
 *  - Share as image: a card for an Instagram / Facebook post or WhatsApp status (+ the same text),
 *  - Send to another phone: a small .sothu file (+ the text); the app on the other phone opens it on an Import screen.
 * The link in the text carries the property too (it opens the app, or a preview page with the download).
 */
@Composable
fun SharePropertyDialog(ctx: AppContext, p: Property, onDismiss: () -> Unit) {
    val lang = LocalLang.current
    val clipboard = LocalClipboardManager.current
    val data = ctx.repo.data
    val hasLand = data.landChecks.any { it.propertyId == p.id && it.osmOk }
    val hasBroker = p.brokerId != null && data.brokers.any { it.id == p.brokerId }
    val broker = p.brokerId?.let { id -> data.brokers.firstOrNull { it.id == id } }
    var notes by remember { mutableStateOf(false) }
    var checksOn by remember { mutableStateOf(true) }
    var brokerOn by remember { mutableStateOf(false) }
    var by by remember { mutableStateOf("") }
    val opts = ShareOptions(notes = notes && p.notes.isNotBlank(), landCheck = checksOn && hasLand, broker = brokerOn && hasBroker, sharedBy = by.trim())
    val code = remember(p, opts) { encodeShare(sharedCopy(p, data.landChecks.firstOrNull { it.propertyId == p.id }, broker, opts)) }
    val link = ShareLink.httpsLink(code)
    val check = remember(data) { com.hark.sothudiary.domain.pricing.checkPrice(p, data) }
    val lines = checkLines(ctx, p, check, lang)
    val sizeText = if (p.size > 0) sizeLine(p) else ""
    val place = listOf(p.land.village.ifBlank { p.locality }, p.land.taluk.ifBlank { p.taluk }, p.land.district.ifBlank { p.district })
        .filter { it.isNotBlank() }.distinct().joinToString(", ")
    val score = scoreFor(ctx, p, check).total
    fun text(): String = com.hark.sothudiary.domain.share.buildShareText(com.hark.sothudiary.domain.share.ShareFacts(
        title = p.title,
        price = if (p.askingPrice > 0) priceShort(p.askingPrice, lang) else "",
        unitPrice = check.unitPrice?.let { com.hark.sothudiary.ui.components.rateText(it, check.basis, lang) }.orEmpty(),
        area = sizeText, place = place, lat = p.lat, lng = p.lng,
        land = landSummary(p.land, lang),
        checks = if (checksOn) listOfNotNull(lines.price, lines.land, lines.gv, score?.let { S4.score.of(lang) + " " + it + "/100" }) else emptyList(),
        sourceUrl = p.sourceUrl,
        broker = if (brokerOn && broker != null) listOf(broker.name, broker.phone).filter { it.isNotBlank() }.joinToString(" · ") else "",
        appLink = link,
    ))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(S4.share.s()) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CheckRow(S4.includeChecks.s(), checksOn) { checksOn = it }
                if (hasBroker) CheckRow(S4.includeBroker.s(), brokerOn) { brokerOn = it }
                if (p.notes.isNotBlank()) CheckRow(S4.includeNotes.s(), notes) { notes = it }
                Field(by, { by = it.take(60) }, S.shareYourName.s())
                ShareAction(S4.shareText.s(), S4.shareTextHint.s()) { ctx.platform.shareText(p.title, text()); onDismiss() }
                ShareAction(S4.shareImage.s(), S4.shareImageHint.s()) {
                    val cover = p.imageFile.takeIf { it.isNotBlank() }?.let { ctx.platform.imagePath(it) }
                    val card = com.hark.sothudiary.ShareCard(
                        title = p.title,
                        price = if (p.askingPrice > 0) priceShort(p.askingPrice, lang) else "",
                        facts = listOfNotNull(sizeText.ifBlank { null }, check.unitPrice?.let { com.hark.sothudiary.ui.components.rateText(it, check.basis, lang) },
                            p.land.surveyFull.ifBlank { null }?.let { "S.F. " + it }, lines.land, lines.gv).take(4),
                        place = place, coverPath = cover, appName = com.hark.sothudiary.AppName.EN + " · " + com.hark.sothudiary.AppName.TA,
                        badge = score?.let { S4.score.of(lang) + " " + it } ?: "", badgeColor = scoreArgb(score),
                    )
                    val path = ctx.platform.renderShareCard(card)
                    if (path == null) ctx.platform.toast(S4.shareFailed.of(lang))
                    else ctx.platform.shareFiles(p.title, text(), listOf(path to "image/png"))
                    onDismiss()
                }
                ShareAction(S4.shareFile.f("." + com.hark.sothudiary.domain.share.SOTHU_FILE_EXT), S4.shareFileHint.s()) {
                    val path = ctx.platform.writeShareFile(com.hark.sothudiary.domain.share.shareFileName(p.title),
                        com.hark.sothudiary.domain.share.shareFileText(code, p.title))
                    if (path == null) ctx.platform.shareText(p.title, text())
                    else ctx.platform.shareFiles(p.title, text(), listOf(path to com.hark.sothudiary.domain.share.SOTHU_MIME))
                    onDismiss()
                }
                Text(S.shareLinkSize.f(link.length), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(S.cancel.s()) } },
        dismissButton = {
            TextButton(onClick = { clipboard.setText(AnnotatedString(link)); ctx.platform.toast(S.copied.of(lang)) }) { Text(S.shareCopyLink.s()) }
        },
    )
}

@Composable
private fun ShareAction(title: String, hint: String, onClick: () -> Unit) {
    androidx.compose.material3.OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Checkbox(checked, onChange)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * 2.3.0: asks before adding a shared property – "Add to my properties", or when the same property is already
 * saved, "Update my copy" / "Add as a new copy". [sp] null = the link could not be read.
 */
@Composable
fun ImportSharedDialog(ctx: AppContext, sp: SharedProperty?, onDone: () -> Unit) {
    val lang = LocalLang.current
    if (sp == null) {
        AlertDialog(onDismissRequest = onDone, title = { Text(S.importTitle.s()) }, text = { Text(S.importBad.s()) },
            confirmButton = { TextButton(onClick = onDone) { Text(S.ok.s()) } })
        return
    }
    val p = sp.p
    val exists = ctx.repo.data.properties.any { it.id == p.id }
    fun save(mode: ImportMode) {
        val id = ctx.repo.importShared(sp, mode)
        ctx.platform.toast((if (mode == ImportMode.UPDATE_EXISTING) S.importUpdated else S.importAdded).of(lang))
        onDone()
        ctx.nav.push(Route.PropertyDetail(id))
    }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(S4.importPreview.s()) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (sp.by.isNotBlank()) S.importFrom.f(sp.by) else S.importAnon.s(), style = MaterialTheme.typography.bodyMedium)
                Text(p.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                val line = listOfNotNull(
                    p.askingPrice.takeIf { it > 0 }?.let { priceShort(it, lang) },
                    p.size.takeIf { it > 0 }?.let { sizeLine(p) },
                    listOf(p.locality, p.district).filter { it.isNotBlank() }.distinct().joinToString(", ").ifBlank { null },
                ).joinToString(" · ")
                if (line.isNotBlank()) Text(line, style = MaterialTheme.typography.bodyMedium)
                val landLine = landSummary(p.land, lang)
                if (landLine.isNotBlank()) Text(landLine, style = MaterialTheme.typography.bodySmall)
                else if (p.surveyNumber.isNotBlank()) Text("S.F. No. " + p.surveyNumber, style = MaterialTheme.typography.bodySmall)
                if (p.sourceUrl.isNotBlank()) Text("🔗 " + p.sourceUrl, style = MaterialTheme.typography.labelSmall, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.primary)
                if (p.registry.guideline != null) Text("• " + S4.gvTile.s(), style = MaterialTheme.typography.bodySmall)
                if (sp.lc != null) Text("• " + S.importHasLand.s(), style = MaterialTheme.typography.bodySmall)
                if (p.notes.isNotBlank()) Text("• " + S.importHasNotes.s(), style = MaterialTheme.typography.bodySmall)
                if (sp.bn.isNotBlank()) Text("• " + S.importHasBroker.f(sp.bn), style = MaterialTheme.typography.bodySmall)
                if (exists) Text(S.importExists.s(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            if (exists) TextButton(onClick = { save(ImportMode.UPDATE_EXISTING) }) { Text(S.importUpdate.s(), fontWeight = FontWeight.Bold) }
            else TextButton(onClick = { save(ImportMode.ADD_NEW) }) { Text(S.importAdd.s(), fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            Row {
                if (exists) TextButton(onClick = { save(ImportMode.ADD_NEW) }) { Text(S.importAddCopy.s()) }
                TextButton(onClick = onDone) { Text(S.cancel.s()) }
            }
        },
    )
}


/**
 * 2.6.0: "Import .sothu file" – opens the file picker, reads the property file and shows the same Add / Update dialog
 * as a received link (App.kt watches [PropertyImport.pending]).
 */
object PropertyImport {
    /** A share code (or "bad") waiting for the import dialog. */
    var pending by androidx.compose.runtime.mutableStateOf<String?>(null)

    fun start(ctx: AppContext) {
        ctx.platform.pickPropertyFile { text ->
            val code = com.hark.sothudiary.domain.share.importCodeForFile(text)
            if (code != null) pending = code
        }
    }
}

@Composable
fun ImportSothuButton(ctx: AppContext, modifier: Modifier = Modifier, showHint: Boolean = false) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (showHint) Text(com.hark.sothudiary.i18n.S6.importFileHint.s(), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        androidx.compose.material3.OutlinedButton(onClick = { PropertyImport.start(ctx) }, modifier = Modifier.fillMaxWidth()) {
            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.FileOpen, contentDescription = null,
                modifier = Modifier.size(18.dp))
            androidx.compose.foundation.layout.Spacer(Modifier.width(6.dp))
            Text(com.hark.sothudiary.i18n.S6.importFile.s())
        }
    }
}
