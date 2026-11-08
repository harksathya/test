package com.hark.sothudiary.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 2.3.1: an embedded browser for a government portal page (TNREGINET guideline value). Each time [scriptTick] changes,
 * [script] is run in the page and its string result (JSON) is passed to [onResult]. Desktop: not available
 * ([platformHasWebView] is false) – the caller opens the site in the browser instead.
 */
@Composable
expect fun PortalWebView(
    url: String,
    modifier: Modifier,
    script: String,
    scriptTick: Int,
    onResult: (String?) -> Unit,
    onPageLoaded: (String) -> Unit,
    onProgress: (Int) -> Unit,
    /** 2.4.0: the main page failed to load (HTTP / network / certificate) – shown in the Status panel. */
    onPageError: (String) -> Unit = {},
)
