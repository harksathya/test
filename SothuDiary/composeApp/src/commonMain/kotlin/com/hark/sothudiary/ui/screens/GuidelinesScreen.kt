package com.hark.sothudiary.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hark.sothudiary.data.GuidelineValue
import com.hark.sothudiary.data.newId
import com.hark.sothudiary.domain.SQFT_PER_CENT
import com.hark.sothudiary.domain.dec
import com.hark.sothudiary.domain.normalizeLocation
import com.hark.sothudiary.domain.parseDouble
import com.hark.sothudiary.domain.parsePrice
import com.hark.sothudiary.domain.rupees
import com.hark.sothudiary.domain.unitPriceShort
import com.hark.sothudiary.i18n.LocalLang
import com.hark.sothudiary.i18n.S
import com.hark.sothudiary.i18n.f
import com.hark.sothudiary.i18n.s
import com.hark.sothudiary.ui.AppContext
import com.hark.sothudiary.ui.components.AppTopBar
import com.hark.sothudiary.ui.components.Field
import com.hark.sothudiary.ui.components.Pill
import com.hark.sothudiary.ui.components.SectionCard
import kotlin.math.roundToLong

@Composable
fun GuidelinesScreen(ctx: AppContext) {
    val data = ctx.repo.data
    val lang = LocalLang.current
    var editing by remember { mutableStateOf<GuidelineValue?>(null) }
    var creating by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { AppTopBar(S.guidelineValues.s(), ctx.toggleLang) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(S.addGuideline.s()) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                SectionCard(title = S.whatIsGuideline.s(), accent = MaterialTheme.colorScheme.primary) {
                    Text(S.guidelineExplain.s(), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { ctx.platform.openUrl(TN_REGINET_URL) }) {
                        Icon(Icons.Filled.Info, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(S.openTnReginet.s())
                    }
                }
            }
            if (data.guidelines.isEmpty()) {
                item { Text(S.noGuidelines.s(), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp)) }
            }
            items(data.guidelines.sortedBy { it.location.lowercase() }, key = { it.id }) { g ->
                val used = data.properties.count { normalizeLocation(it.locality) == normalizeLocation(g.location) }
                Card(
                    onClick = { editing = g },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(g.location, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            if (g.sample) Pill(S.sample.s(), Color(0xFF8D6E63))
                        }
                        Row(Modifier.padding(top = 6.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(S.perCentLabel.s(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(g.perCent?.let { unitPriceShort(it, lang) } ?: g.perSqft?.let { "≈ " + unitPriceShort(it * SQFT_PER_CENT, lang) } ?: "-", fontWeight = FontWeight.SemiBold)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(S.perSqftLabel.s(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(g.perSqft?.let { rupees(it.roundToLong()) } ?: g.perCent?.let { "≈ " + rupees((it / SQFT_PER_CENT).roundToLong()) } ?: "-", fontWeight = FontWeight.SemiBold)
                            }
                        }
                        if (g.note.isNotBlank()) Text(g.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                        Text(S.usedByN.f(used), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }

    val target = editing
    if (creating || target != null) {
        GuidelineDialog(
            existing = target,
            onDismiss = { creating = false; editing = null },
            onSave = { ctx.repo.upsertGuideline(it); creating = false; editing = null },
            onDelete = { ctx.repo.deleteGuideline(it); creating = false; editing = null },
        )
    }
}

@Composable
private fun GuidelineDialog(existing: GuidelineValue?, onDismiss: () -> Unit, onSave: (GuidelineValue) -> Unit, onDelete: (String) -> Unit) {
    var location by remember { mutableStateOf(existing?.location ?: "") }
    var perCent by remember { mutableStateOf(existing?.perCent?.let { dec(it, 0) } ?: "") }
    var perSqft by remember { mutableStateOf(existing?.perSqft?.let { dec(it, 0) } ?: "") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) S.addGuideline.s() else S.editGuideline.s()) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(location, { location = it }, S.locality.s(), supporting = S.guidelineLocHint.s())
                Field(perCent, { perCent = it }, S.perCentRupees.s(), keyboard = KeyboardType.Text, supporting = S.priceInputHint.s())
                Field(perSqft, { perSqft = it }, S.perSqftRupees.s(), keyboard = KeyboardType.Number)
                Field(note, { note = it }, S.notes.s())
            }
        },
        confirmButton = {
            TextButton(
                enabled = location.isNotBlank() && (parsePrice(perCent) != null || parseDouble(perSqft) != null),
                onClick = {
                    onSave(
                        (existing ?: GuidelineValue(id = newId(), location = location.trim())).copy(
                            location = location.trim(),
                            perCent = parsePrice(perCent)?.toDouble(),
                            perSqft = parseDouble(perSqft),
                            note = note.trim(),
                        ),
                    )
                },
            ) { Text(S.save.s()) }
        },
        dismissButton = {
            Row {
                if (existing != null) TextButton(onClick = { onDelete(existing.id) }) { Text(S.delete.s(), color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text(S.cancel.s()) }
            }
        },
    )
}
