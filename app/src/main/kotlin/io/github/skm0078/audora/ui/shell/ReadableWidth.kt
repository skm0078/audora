// File: app/src/main/kotlin/io/github/skm0078/audora/ui/shell/ReadableWidth.kt
// Purpose: Keeps list and settings screens to a readable column, centred, on tablets, foldables and landscape.

package io.github.skm0078.audora.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.ui.theme.WindowWidths

/**
 * On a phone in portrait this changes nothing. Wider, [content] keeps [WindowWidths.READABLE] in the middle and the
 * sides take the screen's background, so rows and lines never stretch across a tablet.
 */
@Composable
fun ReadableWidth(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(Modifier.widthIn(max = WindowWidths.READABLE).fillMaxSize()) { content() }
    }
}

/**
 * Side padding for a full-width scrolling list in a window [width] wide: at least [min], and on wide windows enough
 * to keep the items in a centred [WindowWidths.READABLE] column while the list still scrolls from anywhere.
 */
fun readableSidePadding(width: Dp, min: Dp = 0.dp): Dp = maxOf(min, (width - WindowWidths.READABLE) / 2)
