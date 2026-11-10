package com.hark.sothudiary.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.hark.sothudiary.domain.ai.AUTO_MODEL
import com.hark.sothudiary.domain.ai.hasAiKey
import com.hark.sothudiary.domain.ai.listModels
import com.hark.sothudiary.domain.ai.llm
import com.hark.sothudiary.i18n.LocalLang
import com.hark.sothudiary.i18n.S
import com.hark.sothudiary.i18n.S31
import com.hark.sothudiary.i18n.f
import com.hark.sothudiary.i18n.s
import com.hark.sothudiary.ui.AppContext
import com.hark.sothudiary.ui.components.SectionCard
import kotlinx.coroutines.launch

/** 3.0.1: "••••" + the last 4 characters of a key ("" when there is no key). */
fun maskKey(key: String): String {
    val k = key.trim()
    if (k.isEmpty()) return ""
    return "••••" + (if (k.length > 8) k.takeLast(4) else "")
}

/** 3.0.1: Settings → AI key: provider, masked key, model, Test key, Change (opens [editor]), and where AI is used. */
@Composable
fun AiKeyCard(ctx: AppContext, editor: @Composable (onSaved: () -> Unit) -> Unit) {
    val st = ctx.repo.data.settings
    val lang = LocalLang.current
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf(false) }
    var result by remember(st.llm()) { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    SectionCard(title = S31.title.s()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (st.hasAiKey) {
                val c = st.llm()
                Text(S31.provider.f(c.provider.name), style = MaterialTheme.typography.bodyMedium)
                Text(S31.key.f(maskKey(c.apiKey)), style = MaterialTheme.typography.bodyMedium)
                Text(S31.model.f(if (c.model == AUTO_MODEL) S.geminiAuto.s() else c.model), style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        testing = true
                        result = S31.testing.of(lang)
                        scope.launch {
                            val models = runCatching { listModels(c.provider, c.apiKey) }.getOrNull()
                            result = if (!models.isNullOrEmpty()) S31.works.format(lang, models.size) else S31.refused.of(lang)
                            testing = false
                        }
                    }, enabled = !testing) { Text(S31.test.s()) }
                    TextButton(onClick = { editing = !editing }) { Text(if (editing) S31.close.s() else S31.change.s()) }
                }
                if (result.isNotBlank()) Text(result, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            } else {
                Text(S31.none.s(), style = MaterialTheme.typography.bodySmall)
            }
            if (editing || !st.hasAiKey) editor { editing = false }
            Text(S31.usedFor.s(), style = MaterialTheme.typography.labelLarge)
            listOf(S31.use1, S31.use2, S31.use3).forEach { Text("• " + it.s(), style = MaterialTheme.typography.bodySmall) }
            Text(S31.manual.s(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
