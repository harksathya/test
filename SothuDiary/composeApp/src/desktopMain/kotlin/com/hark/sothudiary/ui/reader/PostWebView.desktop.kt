package com.hark.sothudiary.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

actual val platformHasWebView: Boolean = false

@Composable
actual fun PostWebView(
    url: String,
    modifier: Modifier,
    extractTick: Int,
    onExtracted: (json: String?, mediaSnapshot: ByteArray?) -> Unit,
    onPageLoaded: (String) -> Unit,
    onProgress: (Int) -> Unit,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Text("In-app browser is available on Android. Paste the caption below.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
    }
}
