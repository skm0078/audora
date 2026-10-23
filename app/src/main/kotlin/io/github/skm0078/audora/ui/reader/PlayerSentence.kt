// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/PlayerSentence.kt
// Purpose: The full player's sentence: the one being read in large type with its spoken word marked (plan item 14),
//          between its faded neighbours.

package io.github.skm0078.audora.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.ui.theme.Dimens

/**
 * The previous and next sentences frame the spoken one, faded, so the listener can follow the text without the page.
 * Scrolls when a long sentence at a large font scale needs more room than the screen has (item 39).
 */
@Composable
internal fun SentencesAround(state: ReaderState, spokenWord: () -> IntRange?, modifier: Modifier = Modifier) {
    val units = (state.content as? PageContent.Ready)?.units.orEmpty()
    val spoken = state.spokenUnitIndex
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceL, Alignment.CenterVertically),
    ) {
        when {
            state.content == PageContent.Loading || state.document == DocumentState.Loading -> {
                CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                SlowPageLine(state.page)
            }

            spoken == null -> Text(
                stringResource(R.string.player_idle),
                style = ReadingTextStyles.focus(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            else -> {
                nearestWithText(units, spoken, step = -1)?.let { NeighbourSentence(it) }
                SpokenSentence(units[spoken].text, spokenWord)
                nearestWithText(units, spoken, step = 1)?.let { NeighbourSentence(it) }
            }
        }
    }
}

/** The sentence being read; the only part that redraws on each spoken word, since only it reads [spokenWord]. */
@Composable
private fun SpokenSentence(text: String, spokenWord: () -> IntRange?) {
    Text(
        markedWord(text, spokenWord()),
        style = ReadingTextStyles.spoken(),
        color = MaterialTheme.colorScheme.onSurface,
    )
}

/** The nearest unit with text from [from] in the direction of [step] (±1), skipping a continued table's summary. */
internal fun nearestWithText(units: List<ReadingUnit>, from: Int, step: Int): ReadingUnit? {
    var i = from + step
    while (i in units.indices && units[i].text.isBlank()) i += step
    return units.getOrNull(i)
}

/** [text] with the [word] being said on the accent band (plan item 14). */
@Composable
private fun markedWord(text: String, word: IntRange?): AnnotatedString {
    val range = word?.takeIf { it.first >= 0 && it.last < text.length } ?: return AnnotatedString(text)
    val colors = MaterialTheme.colorScheme
    return buildAnnotatedString {
        append(text)
        addStyle(
            SpanStyle(color = colors.onPrimaryContainer, background = colors.primaryContainer),
            range.first,
            range.last + 1,
        )
    }
}

@Composable
private fun NeighbourSentence(unit: ReadingUnit) {
    Text(
        unit.text,
        style = ReadingTextStyles.body(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = NEIGHBOUR_LINES,
        overflow = TextOverflow.Ellipsis,
    )
}

/** The faded neighbours stay short, so the spoken sentence keeps the middle of the screen. */
private const val NEIGHBOUR_LINES = 3
