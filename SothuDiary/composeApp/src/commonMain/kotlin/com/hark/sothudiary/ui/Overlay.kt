package com.hark.sothudiary.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex

/**
 * Full-screen layers drawn by the app root, inside the activity window (not a separate Dialog window).
 * The location picker uses this: an Android map view inside a Dialog window could draw over the search
 * field until the map was touched, and a Dialog window does not resize for the keyboard like the activity does.
 */
object Overlays {
    internal val stack = mutableStateListOf<Layer>()

    class Layer(val content: @Composable () -> Unit)
}

/** Shows [content] full screen above everything while this composable is in the composition. */
@Composable
fun FullScreenOverlay(content: @Composable () -> Unit) {
    val latest by rememberUpdatedState(content)
    val layer = remember { Overlays.Layer { latest() } }
    DisposableEffect(layer) {
        Overlays.stack.add(layer)
        onDispose { Overlays.stack.remove(layer) }
    }
}

/** Draws the overlay layers; call last inside the root Box so they sit on top. */
@Composable
fun OverlayHost() {
    Overlays.stack.toList().forEachIndexed { i, layer ->
        Box(
            Modifier.fillMaxSize().zIndex(100f + i).background(MaterialTheme.colorScheme.background)
                // Swallow taps so nothing underneath reacts.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) { layer.content() }
    }
}
