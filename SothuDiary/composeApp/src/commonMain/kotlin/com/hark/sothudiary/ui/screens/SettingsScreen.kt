package com.hark.sothudiary.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hark.sothudiary.data.Lang
import com.hark.sothudiary.data.ThemeMode
import com.hark.sothudiary.i18n.tr
import androidx.compose.material3.Switch
import androidx.compose.ui.Alignment
import com.hark.sothudiary.i18n.LocalLang
import com.hark.sothudiary.i18n.S
import com.hark.sothudiary.i18n.f
import com.hark.sothudiary.i18n.s
import com.hark.sothudiary.ui.AppContext
import com.hark.sothudiary.ui.components.AppTopBar
import com.hark.sothudiary.ui.components.ChoiceChips
import com.hark.sothudiary.ui.components.SectionCard

const val APP_VERSION = "2.0"

@Composable
fun SettingsScreen(ctx: AppContext) {
    val data = ctx.repo.data
    val lang = LocalLang.current
    var pendingImport by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = { AppTopBar(S.settings.s(), ctx.toggleLang) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard(title = S.language.s()) {
                ChoiceChips(Lang.entries, data.settings.lang, { if (it == Lang.TA) "தமிழ்" else "English" }) { ctx.repo.setLang(it) }
            }

            SectionCard(title = S.yourData.s()) {
                Text(
                    S.dataCounts.f(data.properties.size, data.brokers.size, data.interactions.size, data.guidelines.size),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(S.dataLocal.s(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(S.exportNote.s(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { ctx.platform.shareText("Sothu Diary backup", ctx.repo.exportJson()) }) {
                        Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(S.exportJson.s())
                    }
                    OutlinedButton(onClick = {
                        ctx.platform.pickTextFile { text -> if (text != null) pendingImport = text }
                    }) {
                        Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(S.importJson.s())
                    }
                }
            }

            SectionCard(title = S.appearance.s()) {
                ChoiceChips(ThemeMode.entries, data.settings.theme, { it.tr().s() }) { ctx.repo.setTheme(it) }
            }

            SectionCard(title = S.reminders.s()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(S.remindersBody.s(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = data.settings.reminders, onCheckedChange = { on ->
                        ctx.repo.setReminders(on)
                        ctx.platform.setReminders(on)
                    })
                }
            }

            SectionCard(title = S.howPriceCheckWorks.s()) {
                Text(S.priceRulesText.s(), style = MaterialTheme.typography.bodyMedium)
            }

            SectionCard(title = S.about.s()) {
                Text(S.appName.s() + " • v" + APP_VERSION, style = MaterialTheme.typography.titleSmall)
                Text(S.aboutBody.s(), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                Text(S.disclaimer.s(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    val importText = pendingImport
    if (importText != null) {
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text(S.importQ.s()) },
            text = { Text(S.importBody.s()) },
            confirmButton = {
                TextButton(onClick = {
                    val ok = ctx.repo.importJson(importText)
                    pendingImport = null
                    ctx.platform.toast(if (ok) S.importDone.of(lang) else S.importFailed.of(lang))
                }) { Text(S.importJson.s()) }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text(S.cancel.s()) } },
        )
    }
}
