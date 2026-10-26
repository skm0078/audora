// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/ReaderSentences.kt
// Purpose: The text view's sentences: tap to read, follow-along centring and highlight styles (plan item 13), the
//          spoken word (plan item 14), Focus view and bold word starts (plan item 77).

package io.github.skm0078.audora.ui.reader

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.ui.shell.readableSidePadding
import io.github.skm0078.audora.ui.theme.Dimens
import io.github.skm0078.audora.ui.theme.readingBand

/**
 * @param spokenWord the characters of the word being spoken in the spoken sentence (plan item 14); null when its
 *   timing is unknown or the word highlight is off. Read inside each row, so only the spoken row redraws per word.
 * @param isHighlighted the listener highlighted this sentence (plan item 30); it gets a soft band, in both views.
 */
@Composable
internal fun Sentences(
    state: ReaderState,
    units: List<ReadingUnit>,
    actions: ReaderActions,
    look: ReadingLook,
    spokenWord: () -> IntRange? = { null },
    isHighlighted: (ReadingUnit) -> Boolean = { false },
) {
    // A new list state per page, so every page opens at its top.
    val listState = remember(state.page) { LazyListState() }
    val spoken = state.spokenUnitIndex
    val paragraphs = remember(units) { paragraphs(units) }
    val drawn = remember(units) { drawnUnits(units) }
    LaunchedEffect(spoken, state.page, look.followAlong, look.reflow) {
        if (spoken == null || !state.following || !look.followAlong) return@LaunchedEffect
        val item = if (look.reflow) paragraphs.indexOfFirst { spoken in it } else drawn.indexOf(spoken)
        if (item >= 0) listState.centre(item + HEADER_ITEMS)
    }
    // On a tablet or in landscape the lines keep a readable length in the middle; the paper still fills the screen.
    BoxWithConstraints(Modifier.fillMaxSize().background(look.tone.paper ?: Color.Transparent)) {
        val side = readableSidePadding(maxWidth, Dimens.SpaceL)
        SentenceList(units, paragraphs, drawn, state, look, spokenWord, isHighlighted, actions, listState, side)
    }
}

@Composable
private fun SentenceList(
    units: List<ReadingUnit>,
    paragraphs: List<IntRange>,
    drawn: List<Int>,
    state: ReaderState,
    look: ReadingLook,
    spokenWord: () -> IntRange?,
    isHighlighted: (ReadingUnit) -> Boolean,
    actions: ReaderActions,
    listState: LazyListState,
    side: Dp,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = side, vertical = Dimens.SpaceS),
        verticalArrangement = Arrangement.spacedBy(SENTENCE_GAP),
    ) {
        if (look.reflow) {
            items(paragraphs, key = { units[it.first].index }) { range ->
                Paragraph(units, range, state, look, spokenWord().takeIf { look.wordHighlight }, actions, isHighlighted)
            }
        } else {
            items(drawn, key = { units[it].index }) { index ->
                val unit = units[index]
                Sentence(
                    unit,
                    sentenceLook(state, unit, index, look, spokenWord()).copy(marked = isHighlighted(unit)),
                    enabled = state.playerReady,
                    onTap = actions::onSentenceTapped,
                    onLongPress = actions::onSentenceLongPressed,
                )
            }
        }
    }
}

/** How the sentence at [index] of the page is drawn, given what is being read. */
internal fun sentenceLook(state: ReaderState, unit: ReadingUnit, index: Int, look: ReadingLook, word: IntRange?) =
    SentenceLook(
        spoken = index == state.spokenUnitIndex,
        unreadable = state.isUnreadable(unit),
        dimmed = look.focus && state.spokenUnitIndex != null && index != state.spokenUnitIndex,
        look = look,
        word = word.takeIf { index == state.spokenUnitIndex && look.wordHighlight },
    )

/**
 * [unreadable]: playback skips it as junk (RI-2b), so it is greyed (RI-9). [dimmed]: Focus view fades every sentence
 * but the one being read. [word]: the spoken word's characters, in the spoken sentence only. [marked]: the
 * listener's own highlight (plan item 30), under the spoken sentence's band when both apply.
 */
internal data class SentenceLook(
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
    val background = when {
        highlight == HighlightStyle.FILL -> colors.readingBand(look.look.tone.paper ?: colors.surface)
        look.marked -> colors.tertiaryContainer
        else -> Color.Transparent
    }
    val color = sentenceColor(look, colors)
    val now = stringResource(R.string.chapters_current)
    Text(
        text = sentenceText(unit.text, look.look.boldWordStarts, look.word, wordStyle(highlight, colors)),
        style = ReadingTextStyles.forLook(look.look),
        color = color,
        fontStyle = if (look.unreadable) FontStyle.Italic else null,
        fontWeight = if (highlight == HighlightStyle.BOLD) FontWeight.Bold else null,
        textDecoration = if (highlight == HighlightStyle.UNDERLINE) TextDecoration.Underline else null,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.SpaceS))
            .background(background)
            .combinedClickable(
                onClickLabel = stringResource(R.string.reader_read_from_here),
                onLongClickLabel = stringResource(R.string.reader_sentence_options),
                onClick = { if (enabled) onTap(unit) },
                onLongClick = { onLongPress(unit) },
            )
            // TalkBack finds the sentence being read by its state, without the voice being talked over.
            .semantics { if (look.spoken) stateDescription = now }
            .padding(horizontal = Dimens.SpaceS, vertical = SENTENCE_PADDING),
    )
}

/**
 * A sentence's text colour: the spoken highlight's, then the listener's own highlight's, greyed if unreadable, faded in
 * Focus view, else the page's ink.
 */
internal fun sentenceColor(look: SentenceLook, colors: ColorScheme): Color {
    val ink = look.look.tone.ink ?: colors.onSurface
    return when {
        look.spoken && look.look.highlight == HighlightStyle.FILL -> ink
        look.spoken && look.look.highlight == HighlightStyle.BOLD -> colors.primary
        look.marked -> colors.onTertiaryContainer
        look.unreadable -> ink.copy(alpha = UNREADABLE_ALPHA)
        look.dimmed -> ink.copy(alpha = FOCUS_DIM_ALPHA)
        else -> ink
    }
}

/** The spoken word stands out against its sentence's own highlight: solid accent on the band, the band elsewhere. */
internal fun wordStyle(highlight: HighlightStyle?, colors: ColorScheme): SpanStyle = when (highlight) {
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

/**
 * A greyed (unreadable) sentence's text. Still text a low-vision reader may need, so it keeps 4.5:1 contrast (WCAG
 * 1.4.3) on every reading theme; Material's 0.38 disabled opacity gave about 2.3:1.
 */
internal const val UNREADABLE_ALPHA = 0.6f

/**
 * Focus view's faded sentences: still readable to find a place, clearly behind the spoken one. 0.6 keeps 4.5:1 on
 * the light, sepia and dark papers; 0.3 gave 1.6 to 2.4:1.
 */
internal const val FOCUS_DIM_ALPHA = 0.6f

private val SENTENCE_GAP = Dimens.SpaceXs / 2
private val SENTENCE_PADDING = Dimens.SpaceXs + Dimens.SpaceXs / 2
