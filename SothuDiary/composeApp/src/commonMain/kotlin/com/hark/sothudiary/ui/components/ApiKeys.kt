package com.hark.sothudiary.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hark.sothudiary.i18n.S
import com.hark.sothudiary.i18n.S5
import com.hark.sothudiary.i18n.f
import com.hark.sothudiary.domain.ai.AUTO_MODEL
import com.hark.sothudiary.domain.ai.ApiStyle
import com.hark.sothudiary.domain.ai.LlmProvider
import com.hark.sothudiary.domain.ai.LlmProviders
import com.hark.sothudiary.domain.ai.keyFor
import com.hark.sothudiary.domain.ai.listModels
import com.hark.sothudiary.domain.ai.llmProvider
import com.hark.sothudiary.domain.ai.modelFor
import com.hark.sothudiary.domain.ai.rankChatModels
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.LaunchedEffect
import com.hark.sothudiary.i18n.s
import com.hark.sothudiary.ui.AppContext

const val AI_STUDIO_URL = "https://aistudio.google.com/apikey"
const val YT_KEY_HELP_URL = "https://developers.google.com/youtube/v3/getting-started"

/** Numbered steps to get a free Gemini key (English / Tamil). */
@Composable
fun GeminiKeySteps() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(S.keyStep1, S.keyStep2, S.keyStep3, S.keyStep4).forEachIndexed { i, st ->
            Row {
                Text("${i + 1}.", fontWeight = FontWeight.Bold, modifier = Modifier.width(22.dp))
                Text(st.s(), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** 2.5.0: provider chips + "get a key" help for the chosen provider (Gemini keeps its numbered steps). */
@Composable
fun ProviderPicker(selected: LlmProvider, onSelect: (LlmProvider) -> Unit, ctx: AppContext) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(S5.aiProvider.s(), style = MaterialTheme.typography.titleSmall)
        Text(S5.aiProviderBody.s(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val free = S5.freeTier.s()
        ChoiceChips(LlmProviders.all, selected, { p -> if (p.freeTier) "${p.name} · $free" else p.name }) { onSelect(it) }
        if (selected.style == ApiStyle.GEMINI) GeminiKeySteps()
        OutlinedButton(onClick = { ctx.platform.openUrl(selected.keyUrl) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
            Text(S5.getKeyAt.f(selected.keyUrl.removePrefix("https://")))
        }
    }
}

/** The key field for [provider]; a pasted key that clearly belongs to another provider switches the picker. */
@Composable
fun ProviderKeyField(provider: LlmProvider, key: String, onKey: (String) -> Unit, onProvider: (LlmProvider) -> Unit) {
    Field(key, { k ->
        onKey(k)
        val guess = LlmProviders.guessFromKey(k)
        if (guess != null && guess != provider && !(provider.keyPrefix.isNotEmpty() && k.trim().startsWith(provider.keyPrefix) && provider.keyPrefix.length >= guess.keyPrefix.length)) onProvider(guess)
    }, S5.aiKey.f(provider.name), supporting = provider.keyPrefix.takeIf { it.isNotEmpty() }?.let { S5.aiKeyHint.f(it) })
}

/**
 * Prominent "Set up the AI key" card (first-run setup): pick a provider, get a key, paste it.
 * Shown on Home until a key is saved (or skipped), in Settings and in Area rates.
 */
@Composable
fun GeminiSetupCard(ctx: AppContext, onDismiss: (() -> Unit)?) {
    val data = ctx.repo.data
    var provider by remember { mutableStateOf(data.settings.llmProvider()) }
    var key by remember { mutableStateOf(data.settings.keyFor(provider)) }
    SectionCard(title = S.setUpLiveCheck.s(), icon = Icons.Filled.Key, accent = MaterialTheme.colorScheme.primary) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(S5.aiKeyBody.s(), style = MaterialTheme.typography.bodyMedium)
            ProviderPicker(provider, { provider = it; key = data.settings.keyFor(it) }, ctx)
            ProviderKeyField(provider, key, { key = it }, { provider = it })
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { ctx.repo.setLlm(provider, key, data.settings.modelFor(provider)) }, enabled = key.isNotBlank()) {
                    Text(S.save.s())
                }
                Spacer(Modifier.weight(1f))
                // 2.3.0: an explicit, friendly skip – everything that does not need AI works without a key.
                if (onDismiss != null) OutlinedButton(onClick = onDismiss) { Text(S.skipForNow.s()) }
            }
            if (onDismiss != null) Text(S.skipKeyBody.s(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Asks for an AI key (any supported provider) – used by the price and land checks when none is saved. */
@Composable
fun GeminiKeyDialog(ctx: AppContext, onDismiss: () -> Unit) {
    val settings = ctx.repo.data.settings
    var provider by remember { mutableStateOf(settings.llmProvider()) }
    var key by remember { mutableStateOf(settings.keyFor(provider)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(S5.aiKeyTitle.s()) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(S5.aiKeyBody.s(), style = MaterialTheme.typography.bodyMedium)
                ProviderPicker(provider, { provider = it; key = settings.keyFor(it) }, ctx)
                ProviderKeyField(provider, key, { key = it }, { provider = it })
            }
        },
        confirmButton = {
            TextButton(onClick = { ctx.repo.setLlm(provider, key, settings.modelFor(provider)); onDismiss() }, enabled = key.isNotBlank()) { Text(S.save.s()) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(S.skipForNow.s()) } },
    )
}

/** 2.5.0: the provider's own model list (never a hard-coded name); "Auto" first. */
@Composable
fun ModelPickerDialog(provider: LlmProvider, apiKey: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var models by remember { mutableStateOf<List<String>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var q by remember { mutableStateOf("") }
    LaunchedEffect(provider.id, apiKey) {
        val listed = runCatching { listModels(provider, apiKey) }.getOrNull()
        if (listed.isNullOrEmpty()) failed = true
        else {
            val ranked = rankChatModels(provider, listed)
            models = ranked + listed.map { it.id }.filter { it !in ranked }.sorted()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(S5.aiModel.s() + " · " + provider.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                when {
                    failed -> Text(S5.noModels.s(), style = MaterialTheme.typography.bodyMedium)
                    models == null -> Text(S5.loadingModels.s(), style = MaterialTheme.typography.bodyMedium)
                    else -> {
                        Field(q, { q = it }, S5.searchModels.s())
                        Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                            TextButton(onClick = { onPick(AUTO_MODEL) }) { Text(S.geminiAuto.s()) }
                            models.orEmpty().filter { q.isBlank() || it.contains(q.trim(), true) }.take(80).forEach { m ->
                                TextButton(onClick = { onPick(m) }) { Text(m, style = MaterialTheme.typography.bodyMedium) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(S.cancel.s()) } },
    )
}
