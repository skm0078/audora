// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/OpeningLine.kt
// Purpose: The opening line card (personal touch 14): the first time a book is opened, its first line shows big, in
//          its own script's bookish font, for a few seconds over the page, then fades.

package io.github.skm0078.audora.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.ui.share.QuoteFonts
import io.github.skm0078.audora.ui.theme.Dimens
import kotlinx.coroutines.delay

/**
 * [line] over the page for [shownMs] (longer when the listener asked Android for more time), then [onShown]. It takes
 * no touches: the page and its buttons under it work as ever, so it never stands in the way. TalkBack reads it once
 * as one phrase, not as the page's sentence (which a search by text would then find twice). The words are never
 * logged.
 */
@Composable
fun OpeningLineCard(line: ReadingUnit?, onShown: () -> Unit, modifier: Modifier = Modifier, shownMs: Long = SHOWN_MS) {
    var shown by remember { mutableStateOf(false) }
    // Kept while the card fades out, after the line itself is gone.
    var last by remember { mutableStateOf<ReadingUnit?>(null) }
    val accessibility = LocalAccessibilityManager.current
    LaunchedEffect(line) {
        shown = line != null
        // Gone before its time (the page turned): it has been seen.
        if (line == null) {
            if (last != null) onShown()
            return@LaunchedEffect
        }
        last = line
        delay(
            accessibility?.calculateRecommendedTimeoutMillis(shownMs, containsIcons = false, containsText = true)
                ?: shownMs,
        )
        shown = false
        onShown()
    }
    AnimatedVisibility(shown, modifier, enter = fadeIn(), exit = fadeOut()) {
        last?.let { OpeningLineText(it) }
    }
}

/** The reader or the full player ([content]) with the opening line floating over its middle, taking no touches. */
@Composable
fun WithOpeningLine(line: ReadingUnit?, onShown: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        content()
        OpeningLineCard(line, onShown, Modifier.align(Alignment.Center))
    }
}

/** A plain box, not a Surface: a Surface takes every touch on it, and this card must let them through. */
@Composable
private fun OpeningLineText(unit: ReadingUnit) {
    val label = stringResource(R.string.reader_opening_line)
    val text = unit.text.trim()
    val shape = MaterialTheme.shapes.extraLarge
    Column(
        Modifier
            .padding(Dimens.ScreenPadding)
            .widthIn(max = MAX_WIDTH)
            .shadow(ELEVATION, shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, shape)
            .padding(Dimens.SpaceXl)
            .clearAndSetSemantics {
                contentDescription = "$label: $text"
                liveRegion = LiveRegionMode.Polite
            },
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text,
            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = QuoteFonts.of(unit.lang)),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}

/** Long enough to read a line twice. */
private const val SHOWN_MS = 6_000L
private val MAX_WIDTH = 560.dp
private val ELEVATION = 6.dp
