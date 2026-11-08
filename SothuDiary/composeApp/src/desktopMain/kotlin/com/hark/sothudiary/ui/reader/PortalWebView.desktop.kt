package com.hark.sothudiary.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hark.sothudiary.i18n.S
import com.hark.sothudiary.i18n.s

@Composable
actual fun PortalWebView(
    url: String,
    modifier: Modifier,
    script: String,
    scriptTick: Int,
    onResult: (String?) -> Unit,
    onPageLoaded: (String) -> Unit,
    onProgress: (Int) -> Unit,
    onPageError: (String) -> Unit,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(S.gvNoWebView.s(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
    }
}
