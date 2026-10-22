// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/ReaderSentences.kt
// Purpose: The text view's sentences: tap to read, follow-along centring and highlight styles (plan item 13), the
//          spoken word (plan item 14), Focus view and bold word starts (plan item 77).

package io.github.skm0078.audora.ui.reader

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.ui.theme.Dimens

/**
 * @param spokenWord the characters of the word being spoken in the spoken sentence (plan item 14); null when its
 *   timing is unknown or the word highlight is off.
 * @param isHighlighted the listener highlighted this sentence (plan item 30); it gets a soft band.
 */
@Composable
internal fun Sentences(
    state: ReaderState,
    units: List<ReadingUnit>,
    actions: ReaderActions,
    look: ReadingLook,
    spokenWord: IntRange? = null,
    isHighlighted: (ReadingUnit) -> Boolean = { false },
) {
    // A new list state per page, so every page opens at its top.
    val listState = remember(state.page) { LazyListState() }
    val spoken = state.spokenUnitIndex
    LaunchedEffect(spoken, state.page, look.followAlong) {
        if (spoken != null && state.following && look.followAlong) listState.centre(spoken + HEADER_ITEMS)
    }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = Dimens.SpaceL, vertical = Dimens.SpaceS),
        verticalArrangement = Arrangement.spacedBy(SENTENCE_GAP),
    ) {
        item {
            Text(
                stringResource(R.string.reader_tap_hint),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Dimens.SpaceS, vertical = Dimens.SpaceXs),
            )
        }
        // index is unique on a page; charStart is not (a continued table's empty summary shares row 0's).
        itemsIndexed(units, key = { _, unit -> unit.index }) { index, unit ->
            if (unit.text.isBlank()) return@itemsIndexed
            Sentence(
                unit,
                SentenceLook(
                    spoken = index == spoken,
                    unreadable = state.isUnreadable(unit),
                    dimmed = look.focus && spoken != null && index != spoken,
                    look = look,
                    word = spokenWord.takeIf { index == spoken && look.wordHighlight },
                    marked = isHighlighted(unit),
                ),
                enabled = state.playerReady,
                onTap = actions::onSentenceTapped,
                onLongPress = actions::onSentenceLongPressed,
            )
        }
    }
}

/**
 * [unreadable]: playback skips it as junk (RI-2b), so it is greyed (RI-9). [dimmed]: Focus view fades every sentence
 * but the one being read. [word]: the spoken word's characters, in the spoken sentence only. [marked]: the
 * listener's own highlight (plan item 30), under the spoken sentence's band when both apply.
 */
private data class SentenceLook(
    val spoken: Boolean,
    val unreadable: Boolean,
    val dimmed: Boolean,
    val look: ReadingLook,
    val word: IntRange?,
    val marked: Boolean = false,
)

/** Tap reads from the sentence (once the player is ready); a long-press opens the sentence menu. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Sentence(
    unit: ReadingUnit,
    look: SentenceLook,
    enabled: Boolean,
    onTap: (ReadingUnit) -> Unit,
    onLongPress: (ReadingUnit) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val highlight = look.look.highlight.takeIf { look.spoken }
    val (background, color) = sentenceColors(highlight, look, colors)
    Text(
        text = sentenceText(unit.text, look.look.boldWordStarts, look.word, wordStyle(highlight, colors)),
        style = if (look.look.focus) ReadingTextStyles.focus() else ReadingTextStyles.body(),
        color = color,
        fontStyle = if (look.unreadable) FontStyle.Italic else null,
        fontWeight = if (highlight == HighlightStyle.BOLD) FontWeight.Bold else null,
        textDecoration = if (highlight == HighlightStyle.UNDERLINE) TextDecoration.Underline else null,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.SpaceS))
            .background(background)
            .combinedClickable(
                onClick = { if (enabled) onTap(unit) },
                onLongClick = { onLongPress(unit) },
            )
            .padding(horizontal = Dimens.SpaceS, vertical = SENTENCE_PADDING),
    )
}

/** The sentence's band and text colour: the spoken band first, then the listener's highlight, unreadable, Focus. */
private fun sentenceColors(highlight: HighlightStyle?, look: SentenceLook, colors: ColorScheme): Pair<Color, Color> {
    val background = when {
        highlight == HighlightStyle.FILL -> colors.primaryContainer
        look.marked -> colors.tertiaryContainer
        else -> Color.Transparent
    }
    val text = when {
        highlight == HighlightStyle.FILL -> colors.onPrimaryContainer
        highlight == HighlightStyle.BOLD -> colors.primary
        look.marked -> colors.onTertiaryContainer
        look.unreadable -> colors.onSurface.copy(alpha = UNREADABLE_ALPHA)
        look.dimmed -> colors.onSurface.copy(alpha = FOCUS_DIM_ALPHA)
        else -> colors.onSurface
    }
    return background to text
}

/** The spoken word stands out against its sentence's own highlight: solid accent on the band, the band elsewhere. */
private fun wordStyle(highlight: HighlightStyle?, colors: ColorScheme): SpanStyle = when (highlight) {
    HighlightStyle.FILL -> SpanStyle(color = colors.onPrimary, background = colors.primary)

    HighlightStyle.UNDERLINE, HighlightStyle.BOLD, null ->
        SpanStyle(color = colors.onPrimaryContainer, background = colors.primaryContainer)
}

/** [text] with bold word starts (plan item 77) and the spoken [word] (plan item 14) marked. */
private fun sentenceText(text: String, boldStarts: Boolean, word: IntRange?, wordStyle: SpanStyle): AnnotatedString {
    val base = if (boldStarts) boldWordStarts(text) else AnnotatedString(text)
    val range = word?.takeIf { it.first >= 0 && it.last < text.length } ?: return base
    return AnnotatedString.Builder(base).apply { addStyle(wordStyle, range.first, range.last + 1) }.toAnnotatedString()
}

/** Scrolls so the item at [index] sits in the middle of the list (plan item 13); a far item is jumped to first. */
private suspend fun LazyListState.centre(index: Int) {
    if (layoutInfo.visibleItemsInfo.none { it.index == index }) scrollToItem(index)
    val info = layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return
    val viewportCentre = (info.viewportStartOffset + info.viewportEndOffset) / 2
    animateScrollBy((item.offset + item.size / 2 - viewportCentre).toFloat())
}

/** The hint above the sentences. */
private const val HEADER_ITEMS = 1

/** A greyed (unreadable) sentence's text: Material's disabled-content opacity. */
private const val UNREADABLE_ALPHA = 0.38f

/** Focus view's faded sentences: still readable to find a place, clearly behind the spoken one. */
private const val FOCUS_DIM_ALPHA = 0.3f

private val SENTENCE_GAP = Dimens.SpaceXs / 2
private val SENTENCE_PADDING = Dimens.SpaceXs + Dimens.SpaceXs / 2
