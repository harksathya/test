package com.hark.sothudiary.ui.screens

import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import com.hark.sothudiary.ui.components.GeminiSetupCard
import com.hark.sothudiary.ui.components.ProviderPicker
import com.hark.sothudiary.ui.components.ProviderKeyField
import com.hark.sothudiary.ui.components.ModelPickerDialog
import com.hark.sothudiary.domain.ai.AUTO_MODEL
import com.hark.sothudiary.domain.ai.hasAiKey
import com.hark.sothudiary.domain.ai.keyFor
import com.hark.sothudiary.domain.ai.llm
import com.hark.sothudiary.domain.ai.llmProvider
import com.hark.sothudiary.domain.ai.modelFor
import com.hark.sothudiary.domain.ai.normalizeModel
import com.hark.sothudiary.i18n.S5
import com.hark.sothudiary.i18n.groupTr
import com.hark.sothudiary.ui.components.AI_STUDIO_URL
import com.hark.sothudiary.ui.components.Field

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
import androidx.compose.material3.Slider
import androidx.compose.ui.Alignment
import com.hark.sothudiary.i18n.LocalLang
import com.hark.sothudiary.i18n.S
import com.hark.sothudiary.i18n.f
import com.hark.sothudiary.i18n.s
import com.hark.sothudiary.ui.AppContext
import com.hark.sothudiary.ui.components.AppTopBar
import com.hark.sothudiary.ui.components.ChoiceChips
import com.hark.sothudiary.ui.components.SectionCard
import com.hark.sothudiary.domain.dec

const val APP_VERSION = "3.1.1"

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
            // 3.1.1: Google sign-in + Drive backup first, so a user who skipped the first-launch screen finds it.
            DriveBackupCard(ctx)

            if (!data.settings.hasAiKey) GeminiSetupCard(ctx, onDismiss = null)

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
                    OutlinedButton(onClick = { ctx.platform.shareText(S.backupSubject.of(lang), ctx.repo.exportJson()) }) {
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

            SectionCard(title = S.bgmTitle.s()) {
                val music = ctx.platform.music
                if (!music.supported) {
                    Text(S.bgmDesktop.s(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(S.bgmBody.s(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(S.bgmOn.s(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Switch(checked = data.settings.bgmEnabled, onCheckedChange = { ctx.repo.setMusic(enabled = it) })
                    }
                    var vol by remember(data.settings.bgmVolume) { mutableStateOf(data.settings.bgmVolume) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(S.bgmVolume.s() + " " + (vol * 100).toInt() + "%", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(120.dp))
                        Slider(
                            value = vol, onValueChange = { vol = it }, valueRange = 0f..0.6f,
                            onValueChangeFinished = { ctx.repo.setMusic(volume = vol) },
                            enabled = data.settings.bgmEnabled, modifier = Modifier.weight(1f),
                        )
                    }
                    Text(
                        if (data.settings.bgmFile.isNotBlank()) S.bgmPlayingOwn.s() else S.bgmPlayingDefault.s(),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            music.pickTrack { name ->
                                if (name == null) return@pickTrack
                                val old = ctx.repo.data.settings.bgmFile
                                ctx.repo.setMusic(enabled = true, file = name)
                                if (old.isNotBlank() && old != name) music.deleteTrack(old)
                                ctx.platform.toast(S.bgmSongSaved.of(lang))
                            }
                        }) { Text(S.bgmChoose.s()) }
                        if (data.settings.bgmFile.isNotBlank() && music.hasDefaultTrack) {
                            TextButton(onClick = {
                                val old = data.settings.bgmFile
                                ctx.repo.setMusic(file = "")
                                music.deleteTrack(old)
                            }) { Text(S.bgmUseDefault.s()) }
                        }
                    }
                    if (music.hasDefaultTrack) {
                        Text(S.bgmCredit.s(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // 3.0.1: the AI key card (provider, masked key, model, Test key, Change) – the editor opens on Change.
            AiKeyCard(ctx) { onSaved ->
                // 2.5.0 (issue #1): any one AI provider with its own key; the model list comes from the provider.
                val savedProvider = data.settings.llmProvider()
                var provider by remember(savedProvider) { mutableStateOf(savedProvider) }
                var gm by remember(savedProvider, data.settings.keyFor(savedProvider)) { mutableStateOf(data.settings.keyFor(savedProvider)) }
                val autoLabel = S.geminiAuto.s()
                fun shown(m: String) = if (m == AUTO_MODEL) autoLabel else m
                var model by remember(savedProvider, data.settings.modelFor(savedProvider), autoLabel) { mutableStateOf(shown(data.settings.modelFor(savedProvider))) }
                var picking by remember { mutableStateOf(false) }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(S.onlineServicesBody.s(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (data.settings.hasAiKey) Text(S5.inUse.f(data.settings.llm().let { c -> c.provider.name + " · " + shown(c.model) }),
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    ProviderPicker(provider, { p -> provider = p; gm = data.settings.keyFor(p); model = shown(data.settings.modelFor(p)) }, ctx)
                    ProviderKeyField(provider, gm, { gm = it }, { p -> provider = p; model = shown(data.settings.modelFor(p)) })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Field(model, { model = it }, S5.aiModel.s(), modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(6.dp))
                        OutlinedButton(onClick = { picking = true }, enabled = gm.isNotBlank()) { Text(S5.chooseModel.s()) }
                    }
                    Text(S5.aiModelHint.f(provider.name), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val changed = provider != savedProvider || gm.trim() != data.settings.keyFor(provider) ||
                        normalizeModel(provider, model) != data.settings.modelFor(provider)
                    Button(onClick = { ctx.repo.setLlm(provider, gm, model); ctx.platform.toast(S.saved.of(lang)); onSaved() }, enabled = changed && gm.isNotBlank()) { Text(S.save.s()) }
                    if (picking) ModelPickerDialog(provider, gm, onDismiss = { picking = false }, onPick = { m -> model = shown(m); picking = false })
                }
            }

            SectionCard(title = S.onlineServices.s()) {
                var yt by remember(data.settings.youtubeApiKey) { mutableStateOf(data.settings.youtubeApiKey) }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(yt, { yt = it }, S.youtubeKey.s(), supporting = S.youtubeKeyHint.s())
                    Button(onClick = { ctx.repo.setYoutubeKey(yt); ctx.platform.toast(S.saved.of(lang)) }, enabled = yt != data.settings.youtubeApiKey) { Text(S5.youtubeSave.s()) }
                    HorizontalDivider()
                    Text(S.onlineRatesSaved.f(data.rateLookups.size, data.onlineRates.size), style = MaterialTheme.typography.bodyMedium)
                    if (data.rateLookups.isNotEmpty()) {
                        TextButton(onClick = { ctx.repo.clearLookups() }) { Text(S.clearOnlineRates.s()) }
                    }
                    if (data.landChecks.isNotEmpty()) {
                        Text(S.landChecksSaved.f(data.landChecks.size), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { ctx.repo.clearLandChecks() }) { Text(S.clearLandChecks.s()) }
                    }
                }
            }

            // 2.3.1: Tamil Nadu sale deed rates used by the stamp duty calculator.
            SectionCard(title = S.dutyRatesTitle.s()) {
                var editRates by remember { mutableStateOf(false) }
                Text(S.dutyRatesLine.f(dec(data.settings.stampDutyPct, 2), dec(data.settings.registrationFeePct, 2)), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { editRates = true }) { Text(S.dutyEditRates.s()) }
                if (editRates) DutyRatesDialog(ctx) { editRates = false }
            }

            // 2.4.0: weights of the 0–100 property score.
            ScoreWeightsCard(ctx)
            SectionCard(title = S.howPriceCheckWorks.s()) {
                Text(com.hark.sothudiary.i18n.S6.priceRulesText.s(), style = MaterialTheme.typography.bodyMedium)
            }

            SectionCard(title = S.about.s()) {
                Text(S.appName.s() + " • v" + APP_VERSION, style = MaterialTheme.typography.titleSmall)
                Text(S.aboutBody.s(), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                Text(S.disclaimer.s(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Text(S.bgmCredit.s(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Text(com.hark.sothudiary.i18n.S4.villageDataCredit.s(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
