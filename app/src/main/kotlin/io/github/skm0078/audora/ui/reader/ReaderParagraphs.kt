// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/ReaderParagraphs.kt
// Purpose: Reflowed text (plan item 15): a page's sentences run together into paragraphs, each sentence still
//          tappable, highlighted and greyed on its own.

package io.github.skm0078.audora.ui.reader

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.config.ReadingUnitConfig
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.model.UnitKind
import io.github.skm0078.audora.ui.theme.Dimens

/**
 * The page's paragraphs, as ranges of [units] indices. A paragraph ends where `:pdf` paused for one (a pause of at
 * least [paragraphPauseMs]), at anything that is not body text (a heading, list item, table row, caption, note or
 * equation stands alone), and where the kind changes. A paragraph with no text at all is left out (see [drawnUnits]).
 */
internal fun paragraphs(
    units: List<ReadingUnit>,
    paragraphPauseMs: Int = ReadingUnitConfig().paragraphPauseMs,
): List<IntRange> {
    val ranges = mutableListOf<IntRange>()
    var start = 0
    units.forEachIndexed { i, unit ->
        val next = units.getOrNull(i + 1)
        val ends = next == null || unit.kind != UnitKind.BODY || next.kind != unit.kind ||
            unit.pauseAfterMs >= paragraphPauseMs
        if (ends) {
            ranges += start..i
            start = i + 1
        }
    }
    return ranges.filter { range -> range.any { units[it].text.isNotBlank() } }
}

/**
 * Indices of the [units] the text view draws: every one with text. A continued table opens with a summary unit that
 * has none (playback speaks "Table continues" from its table info); drawn, it would be a blank row.
 */
internal fun drawnUnits(units: List<ReadingUnit>): List<Int> = units.indices.filter { units[it].text.isNotBlank() }

/** Where each sentence of a paragraph sits in its text, in order: sentence `i` is `spans[i]`. */
internal fun sentenceSpans(texts: List<String>): List<IntRange> {
    var offset = 0
    return texts.map { text ->
        (offset until offset + text.length).also { offset += text.length + SEPARATOR.length }
    }
}

/** The sentence under [offset] in the paragraph text: the one containing it, or the one before a gap. */
internal fun sentenceAt(spans: List<IntRange>, offset: Int): Int? =
    spans.indexOfLast { offset >= it.first }.takeIf { it >= 0 }

/** One paragraph: [range] of the page's [units], tap or long-press any sentence in it. */
@Composable
internal fun Paragraph(
    units: List<ReadingUnit>,
    range: IntRange,
    state: ReaderState,
    look: ReadingLook,
    spokenWord: IntRange?,
    actions: ReaderActions,
) {
    val sentences = units.slice(range)
    val spans = remember(sentences) { sentenceSpans(sentences.map { it.text }) }
    val colors = MaterialTheme.colorScheme
    val text = buildAnnotatedString {
        sentences.forEachIndexed { i, unit ->
            if (i > 0) append(SEPARATOR)
            val sentence = sentenceLook(state, unit, range.first + i, look, spokenWord)
            append(sentenceText(unit.text, sentence, colors))
        }
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val enabled = state.playerReady
    val readLabel = stringResource(R.string.reader_read_from_here)
    val optionsLabel = stringResource(R.string.reader_sentence_options)
    Text(
        text = text,
        style = ReadingTextStyles.forLook(look),
        onTextLayout = { layout = it },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.SpaceS, vertical = PARAGRAPH_PADDING)
            // TalkBack reads the paragraph as one block and cannot aim at a sentence inside it, so its double tap
            // reads from the paragraph's start and its long press opens the first sentence's menu.
            .semantics {
                if (enabled) {
                    onClick(readLabel) {
                        actions.onSentenceTapped(sentences.first())
                        true
                    }
                }
                onLongClick(optionsLabel) {
                    actions.onSentenceLongPressed(sentences.first())
                    true
                }
            }
            .pointerInput(sentences, enabled) {
                fun sentenceUnder(position: Offset): ReadingUnit? =
                    layout?.getOffsetForPosition(position)?.let { sentenceAt(spans, it) }?.let(sentences::getOrNull)
                detectTapGestures(
                    onTap = { position -> if (enabled) sentenceUnder(position)?.let(actions::onSentenceTapped) },
                    onLongPress = { position -> sentenceUnder(position)?.let(actions::onSentenceLongPressed) },
                )
            },
    )
}

/** One sentence of a paragraph: its colour, the spoken highlight, unreadable italics, bold starts and the word. */
private fun sentenceText(text: String, look: SentenceLook, colors: ColorScheme): AnnotatedString {
    val highlight = look.look.highlight.takeIf { look.spoken }
    val style = SpanStyle(
        color = sentenceColor(look, colors),
        background = if (highlight == HighlightStyle.FILL) colors.primaryContainer else Color.Unspecified,
        fontWeight = if (highlight == HighlightStyle.BOLD) FontWeight.Bold else null,
        fontStyle = if (look.unreadable) FontStyle.Italic else null,
        textDecoration = if (highlight == HighlightStyle.UNDERLINE) TextDecoration.Underline else null,
    )
    return buildAnnotatedString {
        append(if (look.look.boldWordStarts) boldWordStarts(text) else AnnotatedString(text))
        addStyle(style, 0, text.length)
        look.word?.takeIf { it.first >= 0 && it.last < text.length }?.let {
            addStyle(wordStyle(highlight, colors), it.first, it.last + 1)
        }
    }
}

/** Between a paragraph's sentences. */
private const val SEPARATOR = " "

private val PARAGRAPH_PADDING = Dimens.SpaceS
