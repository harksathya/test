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

/**
 * Prominent "Set up live price check" card: what it is, steps, link to Google AI Studio and the key field.
 * Shown on Home until a key is saved (or dismissed), in Settings and in Area rates.
 */
@Composable
fun GeminiSetupCard(ctx: AppContext, onDismiss: (() -> Unit)?) {
    val data = ctx.repo.data
    var key by remember { mutableStateOf("") }
    SectionCard(title = S.setUpLiveCheck.s(), icon = Icons.Filled.Key, accent = MaterialTheme.colorScheme.primary) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(S.geminiKeyBody.s(), style = MaterialTheme.typography.bodyMedium)
            GeminiKeySteps()
            OutlinedButton(onClick = { ctx.platform.openUrl(AI_STUDIO_URL) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                Text("aistudio.google.com/apikey")
            }
            Field(key, { key = it }, S.geminiKey.s())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { ctx.repo.setApiKeys(data.settings.youtubeApiKey, key, data.settings.geminiModel) }, enabled = key.isNotBlank()) {
                    Text(S.save.s())
                }
                Spacer(Modifier.weight(1f))
                // 2.3.0: an explicit, friendly skip – everything that does not need Gemini works without a key.
                if (onDismiss != null) OutlinedButton(onClick = onDismiss) { Text(S.skipForNow.s()) }
            }
            if (onDismiss != null) Text(S.skipKeyBody.s(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Asks for the (free) Gemini API key used by the live price check. */
@Composable
fun GeminiKeyDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit, onOpenStudio: () -> Unit) {
    var key by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(S.geminiKeyTitle.s()) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(S.geminiKeyBody.s(), style = MaterialTheme.typography.bodyMedium)
                GeminiKeySteps()
                TextButton(onClick = onOpenStudio) { Text(S.getFreeKey.s()) }
                Field(key, { key = it }, S.geminiKey.s())
            }
        },
        confirmButton = { TextButton(onClick = { onSave(key.trim()) }, enabled = key.isNotBlank()) { Text(S.save.s()) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(S.skipForNow.s()) } },
    )
}
