package com.hark.sothudiary.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import com.hark.sothudiary.data.DocAnalysis
import com.hark.sothudiary.data.DocFlag
import com.hark.sothudiary.data.DocKind
import com.hark.sothudiary.data.FlagLevel
import com.hark.sothudiary.data.Lang
import com.hark.sothudiary.data.OwnershipLink
import com.hark.sothudiary.data.Property
import com.hark.sothudiary.data.PropertyDocument
import com.hark.sothudiary.data.newId
import com.hark.sothudiary.data.nowMillis
import com.hark.sothudiary.domain.CheckRunner
import com.hark.sothudiary.domain.ai.llm
import com.hark.sothudiary.domain.docs.label
import com.hark.sothudiary.domain.formatDateTime
import com.hark.sothudiary.domain.rupees
import com.hark.sothudiary.i18n.LocalLang
import com.hark.sothudiary.i18n.S
import com.hark.sothudiary.i18n.S8
import com.hark.sothudiary.i18n.Tr
import com.hark.sothudiary.i18n.s
import com.hark.sothudiary.ui.AppContext
import com.hark.sothudiary.ui.components.AppTopBar
import com.hark.sothudiary.ui.components.EmptyState
import com.hark.sothudiary.ui.components.Pill
import com.hark.sothudiary.ui.components.SectionCard
import com.hark.sothudiary.ui.theme.LocalBrand

private fun DocKind.tr(): Tr = label().let { Tr(it.first, it.second) }

@Composable
private fun levelColor(l: FlagLevel): Color = when (l) {
    FlagLevel.HIGH -> MaterialTheme.colorScheme.error
    FlagLevel.MEDIUM -> LocalBrand.current.warnText
    FlagLevel.LOW -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun FlagLevel.tr(): Tr = when (this) { FlagLevel.HIGH -> S8.levelHigh; FlagLevel.MEDIUM -> S8.levelMedium; FlagLevel.LOW -> S8.levelLow }

/** One line for the property screen's tile: "3 documents · 2 high, 1 medium flags". */
fun docsLine(p: Property, lang: Lang): String {
    if (p.documents.isEmpty()) return S8.docsNone.of(lang).substringBefore(" –")
    val a = p.docAnalysis
    val state = when {
        a == null -> S8.notAnalysed.of(lang)
        a.flags.none { it.level != FlagLevel.LOW } -> S8.noFlags.of(lang)
        else -> S8.flagsCount.format(lang, a.flags.count { it.level == FlagLevel.HIGH }, a.flags.count { it.level == FlagLevel.MEDIUM })
    }
    return S8.docsCount.format(lang, p.documents.size, state)
}

/** 2.8.0: documents of a property and the EC / ownership check. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DocumentsScreen(ctx: AppContext, propertyId: String) {
    val p = ctx.repo.data.properties.firstOrNull { it.id == propertyId }
    val lang = LocalLang.current
    Scaffold(
        topBar = { AppTopBar(S8.docsTitle.s(), ctx.toggleLang, onBack = { ctx.nav.pop() }, subtitle = p?.title) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        if (p == null) { Box(Modifier.padding(pad)) { EmptyState(Icons.Filled.Description, S.notFound.s(), "") }; return@Scaffold }
        var picking by remember { mutableStateOf(false) }
        val llm = ctx.repo.data.settings.llm()
        val running = CheckRunner.isRunning("docs", p.id)
        val error = CheckRunner.docErrors[p.id]
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionCard(S8.docsTitle.s(), icon = Icons.Filled.UploadFile) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(S8.docsHint.s(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (p.land.surveyNo.isBlank() && p.land.pattaNo.isBlank())
                        Text(S8.landFirst.s(), style = MaterialTheme.typography.bodySmall, color = LocalBrand.current.warnText)
                    if (p.documents.isEmpty()) Text(S8.docsNone.s(), style = MaterialTheme.typography.bodyMedium)
                    p.documents.forEach { d -> DocRow(d) {
                        ctx.repo.removeDocument(p.id, d.id)
                        ctx.platform.deleteImage(d.fileName)
                    } }
                    OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(S8.addDoc.s())
                    }
                    HorizontalDivider()
                    if (!llm.hasKey) Text(S8.needKey.s(), style = MaterialTheme.typography.bodySmall, color = LocalBrand.current.warnText)
                    else Text(S8.privacy.format(lang, llm.provider.name), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(
                        enabled = llm.hasKey && p.documents.isNotEmpty() && !running,
                        onClick = { CheckRunner.docs(ctx.repo, p, ctx.platform) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.FactCheck, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                        Text(S8.analyse.format(lang, llm.provider.name))
                    }
                    if (running) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        var now by remember { mutableStateOf(nowMillis()) }
                        androidx.compose.runtime.LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(1000); now = nowMillis() } }
                        val since = CheckRunner.running["docs:${p.id}"] ?: now
                        val step = CheckRunner.docProgress[p.id]
                        val secs = ((now - since) / 1000).coerceAtLeast(0)
                        Text(if (step != null && step != "analysis") com.hark.sothudiary.i18n.S9.reading.format(lang, step, secs) else S8.analysing.format(lang, secs),
                            style = MaterialTheme.typography.bodySmall)
                    }
                    if (error != null && !running) Text(S8.failed.format(lang, error), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            if (p.documents.isNotEmpty() && llm.hasKey) EcChatCard(ctx, p)
            val a = p.docAnalysis
            if (a != null) {
                if (a.docIds.toSet() != p.documents.map { it.id }.toSet())
                    Text(S8.changedSince.s(), style = MaterialTheme.typography.bodySmall, color = LocalBrand.current.warnText)
                AnalysisView(a, lang)
            }
            LandDetailsSection(ctx, p)
        }
        if (picking) KindPicker(onDismiss = { picking = false }) { kind ->
            picking = false
            ctx.platform.pickDocument { f ->
                if (f != null) ctx.repo.addDocument(p.id, PropertyDocument(newId(), kind, f.fileName, f.mime, nowMillis(), f.displayName))
            }
        }
    }
}

@Composable
private fun DocRow(d: PropertyDocument, onDelete: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (d.mime == "application/pdf") Icons.Filled.PictureAsPdf else Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(d.kind.tr().s(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text(d.displayName.ifBlank { d.fileName }, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { confirm = true }) { Icon(Icons.Filled.Delete, contentDescription = S8.delete.s()) }
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        text = { Text(S8.delete.s() + ": " + d.kind.tr().s() + "?") },
        confirmButton = { TextButton(onClick = { confirm = false; onDelete() }) { Text(S8.delete.s()) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(S.cancel.s()) } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KindPicker(onDismiss: () -> Unit, onPick: (DocKind) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(S8.pickKind.s()) },
        text = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DocKind.entries.forEach { k -> OutlinedButton(onClick = { onPick(k) }) { Text(k.tr().s()) } }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(S.cancel.s()) } },
    )
}

@Composable
private fun AnalysisView(a: DocAnalysis, lang: Lang) {
    val ta = lang == Lang.TA
    SectionCard(S8.summary.s(), icon = Icons.Filled.FactCheck) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val summary = if (ta) a.summaryTa.ifBlank { a.summaryEn } else a.summaryEn.ifBlank { a.summaryTa }
            if (summary.isNotBlank()) Text(summary, style = MaterialTheme.typography.bodyMedium)
            if (a.surveyNo.isNotBlank() || a.pattaNo.isNotBlank())
                Text(S8.forLand.format(lang, a.surveyNo.ifBlank { "–" }, a.pattaNo.ifBlank { "–" }), style = MaterialTheme.typography.labelMedium)
            if (a.ecFrom.isNotBlank() || a.ecTo.isNotBlank())
                Text(S8.ecPeriod.format(lang, a.ecFrom.ifBlank { "?" }, a.ecTo.ifBlank { "?" }), style = MaterialTheme.typography.labelMedium)
            Text(S8.checkedBy.format(lang, formatDateTime(a.analysedAt, lang), a.provider, a.model), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    SectionCard(S8.flagsTitle.s() + " (${a.flags.size})") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (a.flags.isEmpty()) Text(S8.noFlags.s(), style = MaterialTheme.typography.bodyMedium)
            a.flags.forEach { f -> FlagRow(f, ta) }
        }
    }
    SectionCard(S8.chainTitle.s()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (a.chain.isEmpty()) Text(S8.chainEmpty.s(), style = MaterialTheme.typography.bodyMedium)
            a.chain.forEachIndexed { i, c -> ChainRow(i + 1, c) }
        }
    }
    if (a.docs.isNotEmpty()) SectionCard(S8.docsReadTitle.s()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            a.docs.forEach { d ->
                val lines = listOfNotNull(
                    d.holders.takeIf { it.isNotEmpty() }?.joinToString(", "),
                    d.pattaNo.ifBlank { null }?.let { "Patta $it" },
                    d.surveyNos.takeIf { it.isNotEmpty() }?.let { "S.F. " + it.joinToString(", ") },
                    d.extent.ifBlank { null }, d.landClass.ifBlank { null }, d.notes.ifBlank { null },
                )
                Text((if (ta) d.kind.label().second else d.kind.label().first) + ": " + lines.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    if (a.entries.isNotEmpty()) SectionCard(S8.entriesTitle.format(lang, a.entries.size)) {
        Column(Modifier.horizontalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            a.entries.forEach { e ->
                Text(
                    listOf(e.docNo.ifBlank { "–" } + (e.yearOrBlank()), e.date, e.nature, e.executants.joinToString(", ") + " → " + e.claimants.joinToString(", "),
                        e.value?.let { rupees(it) } ?: "", e.surveyNos.joinToString(", ")).filter { it.isNotBlank() }.joinToString(" | "),
                    style = MaterialTheme.typography.labelSmall, modifier = Modifier.widthIn(min = 300.dp),
                )
            }
        }
    }
    Text(S8.disclaimer.s(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun com.hark.sothudiary.data.EcEntry.yearOrBlank(): String = year?.let { "/$it" } ?: ""

@Composable
private fun FlagRow(f: DocFlag, ta: Boolean) {
    val c = levelColor(f.level)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill(f.level.tr().s(), c, filled = f.level == FlagLevel.HIGH)
            if (f.byAi) Pill(S8.aiFlag.s(), MaterialTheme.colorScheme.onSurfaceVariant)
            if (f.ref.isNotBlank()) Text(f.ref, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(if (ta) f.ta else f.en, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ChainRow(n: Int, c: OwnershipLink) {
    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("$n. " + listOfNotNull(c.year?.toString(), c.nature.lowercase().replaceFirstChar { it.uppercase() },
                c.docNo.ifBlank { null }?.let { S8.docNo.s() + " " + it }).joinToString(" · "),
                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text(S8.seller.s() + ": " + c.from.joinToString(", ").ifBlank { "–" }, style = MaterialTheme.typography.bodySmall)
            Text(S8.buyer.s() + ": " + c.to.joinToString(", ").ifBlank { "–" }, style = MaterialTheme.typography.bodySmall)
            val extra = listOfNotNull(c.value?.let { S8.value.s() + " " + rupees(it) }, c.date.ifBlank { null },
                S8.viaPower.s().takeIf { c.viaPowerAgent }, c.surveyNos.takeIf { it.isNotEmpty() }?.let { "S.F. " + it.joinToString(", ") })
            if (extra.isNotEmpty()) Text(extra.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 2.9.0: ChatGPT-like questions about the uploaded documents, answered from their saved text. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EcChatCard(ctx: AppContext, p: Property) {
    val lang = LocalLang.current
    val S9 = com.hark.sothudiary.i18n.S9
    var q by remember(p.id) { mutableStateOf("") }
    val asking = CheckRunner.isRunning("ask", p.id)
    val busy = asking || CheckRunner.isRunning("docs", p.id)
    val error = CheckRunner.chatErrors[p.id]
    fun ask(text: String) { if (text.isNotBlank() && !busy) { CheckRunner.askDocs(ctx.repo, p, text, ctx.platform); q = "" } }
    SectionCard(S9.chatTitle.s(), icon = Icons.Filled.FactCheck) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(S9.chatHint.s(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val read = p.docTexts.filter { t -> p.documents.any { it.id == t.docId } }
            if (read.size < p.documents.size) Text(S9.firstRead.s(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else Text(S9.readDone.format(lang, read.sumOf { it.pages }, read.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                com.hark.sothudiary.domain.docs.quickQuestions(p.land, lang == Lang.TA).forEach { s ->
                    androidx.compose.material3.SuggestionChip(onClick = { ask(s) }, enabled = !busy, label = { Text(s, style = MaterialTheme.typography.labelSmall) })
                }
            }
            p.docChat.forEach { m -> ChatBubble(m, lang) }
            if (asking) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                var now by remember { mutableStateOf(nowMillis()) }
                androidx.compose.runtime.LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(1000); now = nowMillis() } }
                val secs = (((now - (CheckRunner.running["ask:${p.id}"] ?: now))) / 1000).coerceAtLeast(0)
                val step = CheckRunner.docProgress[p.id]
                Text(if (step != null && step != "answer") S9.reading.format(lang, step, secs) else S9.answering.format(lang, secs), style = MaterialTheme.typography.bodySmall)
            }
            if (error != null && !asking) Text(S9.failed.format(lang, error), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.OutlinedTextField(q, { q = it }, modifier = Modifier.weight(1f), placeholder = { Text(S9.placeholder.s()) }, maxLines = 4)
                Spacer(Modifier.width(6.dp))
                Button(onClick = { ask(q) }, enabled = q.isNotBlank() && !busy) { Text(S9.send.s()) }
            }
            Row {
                if (p.docChat.isNotEmpty()) TextButton(onClick = { ctx.repo.clearDocChat(p.id); CheckRunner.chatErrors.remove(p.id) }, enabled = !busy) { Text(S9.clear.s()) }
                if (p.docTexts.isNotEmpty()) TextButton(onClick = { ctx.repo.clearDocTexts(p.id) }, enabled = !busy) { Text(S9.reread.s()) }
            }
            Text(S9.note.s(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ChatBubble(m: com.hark.sothudiary.data.DocChatMsg, lang: Lang) {
    val S9 = com.hark.sothudiary.i18n.S9
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.fromUser) Arrangement.End else Arrangement.Start) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (m.fromUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 520.dp),
        ) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(if (m.fromUser) S9.you.s() else S9.ai.format(lang, m.model), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(m.text.replace("**", ""), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
