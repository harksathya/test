package com.hark.sothudiary.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hark.sothudiary.data.Lang
import com.hark.sothudiary.data.nowMillis
import com.hark.sothudiary.domain.pricing.LookupErrorKind
import com.hark.sothudiary.domain.pricing.classifyLookupError
import com.hark.sothudiary.domain.pricing.rawErrorText
import com.hark.sothudiary.i18n.LocalLang
import com.hark.sothudiary.i18n.S
import com.hark.sothudiary.i18n.Tr
import com.hark.sothudiary.i18n.s

/** "37 seconds" / "5 min" / "2 h" in the given language. */
fun waitText(ms: Long, lang: Lang): String {
    val sec = ((ms + 999) / 1000).coerceAtLeast(1)
    return when {
        sec < 90 -> S.durSeconds.format(lang, sec)
        sec < 90 * 60 -> S.durMinutes.format(lang, (sec + 59) / 60)
        else -> S.durHours.format(lang, (sec + 1800) / 3600)
    }
}

/** The short friendly message for a stored lookup error, in [lang]. */
fun lookupErrorMessage(stored: String, retryAt: Long, lang: Lang, now: Long = nowMillis()): String {
    val t: Tr = when (classifyLookupError(stored)) {
        LookupErrorKind.QUOTA -> if (retryAt > now) return S.errQuotaAfter.format(lang, waitText(retryAt - now, lang)) else S.errQuota
        LookupErrorKind.BAD_KEY -> S.errBadKey
        LookupErrorKind.NETWORK -> S.errNetwork
        LookupErrorKind.MODEL_UNAVAILABLE -> S.errModel
        LookupErrorKind.NO_KEY -> S.geminiNeedKey
        LookupErrorKind.OTHER -> S.errOther
    }
    return t.of(lang)
}

/**
 * ONE short, friendly, bilingual message for a failed live lookup (the current language first, the other below it),
 * with the raw API text behind a small "Details" expander.
 */
@Composable
fun LookupErrorNote(stored: String, retryAt: Long, modifier: Modifier = Modifier) {
    val lang = LocalLang.current
    val other = if (lang == Lang.TA) Lang.EN else Lang.TA
    var open by remember(stored) { mutableStateOf(false) }
    val warn = Color(0xFFB26A00)
    Column(modifier.fillMaxWidth().background(warn.copy(alpha = 0.08f), RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 8.dp)) {
        Text(lookupErrorMessage(stored, retryAt, lang), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = warn)
        Text(lookupErrorMessage(stored, retryAt, other), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val raw = rawErrorText(stored)
        if (raw.isNotBlank() && raw != "no_key") {
            TextButton(onClick = { open = !open }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp, vertical = 0.dp)) {
                Text(if (open) S.errHideDetails.s() else S.errDetails.s(), style = MaterialTheme.typography.labelSmall)
            }
            if (open) Text(raw, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
