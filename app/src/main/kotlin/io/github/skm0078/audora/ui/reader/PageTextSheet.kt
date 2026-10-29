// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/PageTextSheet.kt
// Purpose: The reader's "Page and text" sheet (ADR-062): page colours (plan item 18) in both views; in the text view,
//          follow-along, word highlight and its style (items 13, 14), Focus view and bold word starts (item 77), and
//          the type settings (item 15) with a sample line that shows each change at once; how pages turn in both views
//          (ADR 2026-10-29-page-curl).

package io.github.skm0078.audora.ui.reader

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.skm0078.audora.R
import io.github.skm0078.audora.ui.reader.flip.TurnShadow
import io.github.skm0078.audora.ui.reader.flip.TurnSpeed
import io.github.skm0078.audora.ui.reader.flip.TurnStyle
import io.github.skm0078.audora.ui.theme.ChoiceOption
import io.github.skm0078.audora.ui.theme.Dimens
import io.github.skm0078.audora.ui.theme.OptionBlock
import io.github.skm0078.audora.ui.theme.OptionDivider
import io.github.skm0078.audora.ui.theme.OptionGroup
import io.github.skm0078.audora.ui.theme.OptionSheet
import io.github.skm0078.audora.ui.theme.SwitchOption
import io.github.skm0078.audora.ui.theme.namedSlider
import kotlin.math.roundToInt

/**
 * Every look option in one place, grouped the way a reader thinks of them: how the page looks, what moves while it
 * reads, and aids. The page colour applies to both views, so the page and its text agree; the rest only exists in
 * the text view ([textView]) and is left out over the page image.
 */
@Composable
internal fun PageTextSheet(
    look: ReadingLook,
    onLookChange: (ReadingLook) -> Unit,
    textView: Boolean,
    onDismiss: () -> Unit,
) {
    OptionSheet(stringResource(R.string.page_text_title), onDismiss) {
        OptionGroup(title = null) {
            ToneBlock(look, onLookChange)
            OptionDivider()
            TurnRows(look, onLookChange)
            if (textView) {
                OptionDivider()
                TypeRows(look, onLookChange)
            }
        }
        if (textView) {
            Text(
                stringResource(R.string.text_look_sample),
                style = ReadingTextStyles.forLook(look),
                modifier = Modifier.padding(horizontal = Dimens.SpaceXs),
            )
            WhileReadingGroup(look, onLookChange)
            AidsGroup(look, onLookChange)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToneBlock(look: ReadingLook, onLookChange: (ReadingLook) -> Unit) {
    OptionBlock(stringResource(R.string.page_tone_title)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            PageTone.entries.forEachIndexed { index, tone ->
                SegmentedButton(
                    selected = look.tone == tone,
                    onClick = { onLookChange(look.copy(tone = tone)) },
                    shape = SegmentedButtonDefaults.itemShape(index, PageTone.entries.size),
                ) { Text(stringResource(tone.label)) }
            }
        }
    }
}

/** How pages turn: the style, then its speed and (for a curl) its shadow, shown only when they apply. */
@Composable
private fun TurnRows(look: ReadingLook, onLookChange: (ReadingLook) -> Unit) {
    val turn = look.turn
    ChoiceOption(
        stringResource(R.string.page_turn_style),
        TurnStyle.entries,
        turn.style,
        label = { stringResource(it.label) },
        onPick = { onLookChange(look.copy(turn = turn.copy(style = it))) },
        help = stringResource(R.string.page_turn_style_help),
    )
    if (turn.style == TurnStyle.NONE) return
    OptionDivider()
    ChoiceOption(
        stringResource(R.string.page_turn_speed),
        TurnSpeed.entries,
        turn.speed,
        label = { stringResource(it.label) },
        onPick = { onLookChange(look.copy(turn = turn.copy(speed = it))) },
    )
    if (turn.style != TurnStyle.CURL) return
    OptionDivider()
    ChoiceOption(
        stringResource(R.string.page_turn_shadow),
        TurnShadow.entries,
        turn.shadow,
        label = { stringResource(it.label) },
        onPick = { onLookChange(look.copy(turn = turn.copy(shadow = it))) },
    )
}

/** Size as a slider with its percentage; font, spacing and running paragraphs as rows that show what is set. */
@Composable
private fun TypeRows(look: ReadingLook, onLookChange: (ReadingLook) -> Unit) {
    val percent = stringResource(R.string.text_look_size_value, (look.textScale * PERCENT).roundToInt())
    val title = stringResource(R.string.text_look_size)
    OptionBlock(title, value = percent) {
        Slider(
            value = look.textScale,
            onValueChange = { onLookChange(look.copy(textScale = snapScale(it))) },
            valueRange = ReadingLook.TEXT_SCALES,
            modifier = Modifier.namedSlider(title, percent),
        )
    }
    OptionDivider()
    ChoiceOption(
        stringResource(R.string.text_look_font),
        ReadingFont.entries,
        look.font,
        label = { stringResource(it.label) },
        onPick = { onLookChange(look.copy(font = it)) },
        help = stringResource(R.string.text_look_font_help),
    )
    OptionDivider()
    ChoiceOption(
        stringResource(R.string.text_look_spacing),
        LineSpacing.entries,
        look.spacing,
        label = { stringResource(it.label) },
        onPick = { onLookChange(look.copy(spacing = it)) },
    )
    OptionDivider()
    SwitchOption(
        stringResource(R.string.text_look_reflow),
        look.reflow,
        { onLookChange(look.copy(reflow = it)) },
        subtitle = stringResource(R.string.row_line_reflow),
        help = stringResource(R.string.text_look_reflow_body),
    )
    OptionDivider()
    SwitchOption(
        stringResource(R.string.text_look_turn_pages),
        look.turnPages,
        { onLookChange(look.copy(turnPages = it)) },
        subtitle = stringResource(R.string.row_line_turn_pages),
        help = stringResource(R.string.text_look_turn_pages_body),
    )
}

@Composable
private fun WhileReadingGroup(look: ReadingLook, onLookChange: (ReadingLook) -> Unit) {
    OptionGroup(stringResource(R.string.page_text_group_reading)) {
        SwitchOption(
            stringResource(R.string.reader_follow_along),
            look.followAlong,
            { onLookChange(look.copy(followAlong = it)) },
            subtitle = stringResource(R.string.reader_follow_along_short),
        )
        OptionDivider()
        SwitchOption(
            stringResource(R.string.reader_word_highlight),
            look.wordHighlight,
            { onLookChange(look.copy(wordHighlight = it)) },
            subtitle = stringResource(R.string.row_line_word_highlight),
            help = stringResource(R.string.reader_hint_word_highlight),
        )
        OptionDivider()
        ChoiceOption(
            stringResource(R.string.highlight_style_title),
            HighlightStyle.entries,
            look.highlight,
            label = { stringResource(it.label) },
            onPick = { onLookChange(look.copy(highlight = it)) },
        )
    }
}

@Composable
private fun AidsGroup(look: ReadingLook, onLookChange: (ReadingLook) -> Unit) {
    OptionGroup(stringResource(R.string.page_text_group_aids)) {
        SwitchOption(
            stringResource(R.string.reader_focus_view),
            look.focus,
            { onLookChange(look.copy(focus = it)) },
            subtitle = stringResource(R.string.reader_focus_view_short),
            help = stringResource(R.string.reader_hint_focus_view),
        )
        OptionDivider()
        SwitchOption(
            stringResource(R.string.reader_bold_word_starts),
            look.boldWordStarts,
            { onLookChange(look.copy(boldWordStarts = it)) },
            subtitle = stringResource(R.string.row_line_bold_word_starts),
            help = stringResource(R.string.reader_hint_bold_word_starts),
        )
    }
}

/** The text size on a [SCALE_STEP] grid, within [ReadingLook.TEXT_SCALES], so saved values stay tidy. */
internal fun snapScale(scale: Float): Float {
    val range = ReadingLook.TEXT_SCALES
    val stepped = (scale / SCALE_STEP).roundToInt() * SCALE_STEP
    return stepped.coerceIn(range.start, range.endInclusive)
}

private val PageTone.label: Int
    @StringRes get() = when (this) {
        PageTone.ORIGINAL -> R.string.page_tone_original
        PageTone.SEPIA -> R.string.page_tone_sepia
        PageTone.DARK -> R.string.page_tone_dark
    }

private val TurnStyle.label: Int
    @StringRes get() = when (this) {
        TurnStyle.CURL -> R.string.page_turn_curl
        TurnStyle.SLIDE -> R.string.page_turn_slide
        TurnStyle.NONE -> R.string.page_turn_none
    }

private val TurnSpeed.label: Int
    @StringRes get() = when (this) {
        TurnSpeed.QUICK -> R.string.page_turn_quick
        TurnSpeed.NORMAL -> R.string.page_turn_normal
        TurnSpeed.SLOW -> R.string.page_turn_slow
    }

private val TurnShadow.label: Int
    @StringRes get() = when (this) {
        TurnShadow.LIGHT -> R.string.page_turn_shadow_light
        TurnShadow.NORMAL -> R.string.page_turn_normal
        TurnShadow.STRONG -> R.string.page_turn_shadow_deep
    }

private val HighlightStyle.label: Int
    @StringRes get() = when (this) {
        HighlightStyle.FILL -> R.string.highlight_style_band
        HighlightStyle.UNDERLINE -> R.string.highlight_style_underline
        HighlightStyle.BOLD -> R.string.highlight_style_bold
    }

private val ReadingFont.label: Int
    @StringRes get() = when (this) {
        ReadingFont.SERIF -> R.string.text_look_font_serif
        ReadingFont.SANS -> R.string.text_look_font_sans
        ReadingFont.ATKINSON_HYPERLEGIBLE -> R.string.text_look_font_atkinson
        ReadingFont.OPEN_DYSLEXIC -> R.string.text_look_font_opendyslexic
    }

private val LineSpacing.label: Int
    @StringRes get() = when (this) {
        LineSpacing.COMPACT -> R.string.text_look_spacing_compact
        LineSpacing.NORMAL -> R.string.text_look_spacing_normal
        LineSpacing.RELAXED -> R.string.text_look_spacing_relaxed
    }

private const val PERCENT = 100
private const val SCALE_STEP = 0.05f
