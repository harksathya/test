package com.hark.sothudiary.ui.screens

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

/** 2.3.0: the link for [p] with the chosen options (https page link with the data in the fragment). */
fun shareLinkFor(ctx: AppContext, p: Property, o: ShareOptions): String {
    val data = ctx.repo.data
    val land = data.landChecks.firstOrNull { it.propertyId == p.id }
    val broker = p.brokerId?.let { id -> data.brokers.firstOrNull { it.id == id } }
    return ShareLink.httpsLink(encodeShare(sharedCopy(p, land, broker, o)))
}

/** 2.3.0 "Share property" dialog: what to include, then the system share sheet (WhatsApp …) or copy the link. */
@Composable
fun SharePropertyDialog(ctx: AppContext, p: Property, onDismiss: () -> Unit) {
    val lang = LocalLang.current
    val clipboard = LocalClipboardManager.current
    val data = ctx.repo.data
    val hasLand = data.landChecks.any { it.propertyId == p.id && it.osmOk }
    val hasBroker = p.brokerId != null && data.brokers.any { it.id == p.brokerId }
    var notes by remember { mutableStateOf(false) }
    var land by remember { mutableStateOf(hasLand) }
    var broker by remember { mutableStateOf(false) }
    var by by remember { mutableStateOf("") }
    val opts = ShareOptions(notes = notes && p.notes.isNotBlank(), landCheck = land && hasLand, broker = broker && hasBroker, sharedBy = by.trim())
    val link = remember(p, opts) { shareLinkFor(ctx, p, opts) }
    val sizeText = if (p.size > 0) sizeLine(p) else ""
    fun message(): String = shareMessage(
        p, link,
        priceText = if (p.askingPrice > 0) priceShort(p.askingPrice, lang) else "",
        sizeText = sizeText,
        placeText = listOf(p.locality, p.taluk, p.district).filter { it.isNotBlank() }.distinct().joinToString(", "),
        intro = S.shareIntro.of(lang), openHint = S.shareOpenHint.of(lang),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(S.shareTitle.s()) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(S.shareBody.s(), style = MaterialTheme.typography.bodyMedium)
                if (p.notes.isNotBlank()) CheckRow(S.shareIncludeNotes.s(), notes) { notes = it }
                if (hasLand) CheckRow(S.shareIncludeLand.s(), land) { land = it }
                if (hasBroker) CheckRow(S.shareIncludeBroker.s(), broker) { broker = it }
                Field(by, { by = it.take(60) }, S.shareYourName.s())
                Text(S.shareLinkSize.f(link.length), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(onClick = { ctx.platform.shareText(p.title, message()); onDismiss() }) { Text(S.shareSend.s(), fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = { clipboard.setText(AnnotatedString(link)); ctx.platform.toast(S.copied.of(lang)) }) { Text(S.shareCopyLink.s()) }
        },
    )
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
        title = { Text(S.importTitle.s()) },
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
                if (p.surveyNumber.isNotBlank()) Text("S.F. No. " + p.surveyNumber, style = MaterialTheme.typography.bodySmall)
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
