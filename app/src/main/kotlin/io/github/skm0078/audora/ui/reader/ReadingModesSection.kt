// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/ReadingModesSection.kt
// Purpose: The speech sheet's "What to read" options: skim, unreadable text, tables, notes, equations, skips.

package io.github.skm0078.audora.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.model.CaptionMode
import io.github.skm0078.audora.core.model.EquationMode
import io.github.skm0078.audora.core.model.FootnoteMode
import io.github.skm0078.audora.core.model.ReadingModes
import io.github.skm0078.audora.core.model.TableMode
import io.github.skm0078.audora.ui.theme.ChoiceOption
import io.github.skm0078.audora.ui.theme.OptionDivider
import io.github.skm0078.audora.ui.theme.OptionGroup
import io.github.skm0078.audora.ui.theme.SwitchOption

/**
 * Each change goes to the playback service at once and restarts the sentence playing, like a speed change, so the
 * listener hears the difference straight away. Choices show what is set at the row's end (ADR-062).
 */
@Composable
fun ReadingModesSection(modes: ReadingModes, onChange: (ReadingModes) -> Unit) {
    OptionGroup(title = null) {
        SwitchOption(
            stringResource(R.string.reading_skim),
            modes.skim,
            { onChange(modes.copy(skim = it)) },
            subtitle = stringResource(R.string.reading_skim_short),
            help = stringResource(R.string.reading_skim_detail),
        )
        OptionDivider()
        SwitchOption(
            stringResource(R.string.reading_skip_unreadable),
            modes.skipUnreadable,
            { onChange(modes.copy(skipUnreadable = it)) },
            subtitle = stringResource(R.string.reading_skip_unreadable_short),
            help = stringResource(R.string.reading_skip_unreadable_detail),
        )
    }
    OptionGroup(title = null) {
        ModeChoice(R.string.reading_tables, TableMode.entries, modes.tables, ::tableLabel) {
            onChange(modes.copy(tables = it))
        }
        OptionDivider()
        ModeChoice(R.string.reading_footnotes, FootnoteMode.entries, modes.footnotes, ::footnoteLabel) {
            onChange(modes.copy(footnotes = it))
        }
        OptionDivider()
        ModeChoice(R.string.reading_equations, EquationMode.entries, modes.equations, ::equationLabel) {
            onChange(modes.copy(equations = it))
        }
        OptionDivider()
        ModeChoice(R.string.reading_captions, CaptionMode.entries, modes.captions, ::captionLabel) {
            onChange(modes.copy(captions = it))
        }
    }
    SkipSwitches(modes, onChange)
}

/** Plan item 2: one switch each for asides, bracketed notes, links and page numbers; each title says what it does. */
@Composable
private fun SkipSwitches(modes: ReadingModes, onChange: (ReadingModes) -> Unit) {
    OptionGroup(title = null) {
        SwitchOption(
            stringResource(R.string.reading_skip_parentheses),
            modes.skipParentheses,
            { onChange(modes.copy(skipParentheses = it)) },
            subtitle = stringResource(R.string.reading_skip_parentheses_detail),
        )
        OptionDivider()
        SwitchOption(
            stringResource(R.string.reading_skip_brackets),
            modes.skipBrackets,
            { onChange(modes.copy(skipBrackets = it)) },
            subtitle = stringResource(R.string.reading_skip_brackets_detail),
        )
        OptionDivider()
        SwitchOption(
            stringResource(R.string.reading_skip_links),
            modes.skipLinks,
            { onChange(modes.copy(skipLinks = it)) },
            subtitle = stringResource(R.string.reading_skip_links_detail),
        )
        OptionDivider()
        SwitchOption(
            stringResource(R.string.reading_announce_pages),
            modes.announcePages,
            { onChange(modes.copy(announcePages = it)) },
            subtitle = stringResource(R.string.reading_announce_pages_detail),
        )
    }
}

@Composable
private fun <T> ModeChoice(title: Int, options: List<T>, selected: T, label: (T) -> Int, onPick: (T) -> Unit) {
    ChoiceOption(stringResource(title), options, selected, label = { stringResource(label(it)) }, onPick = onPick)
}

/** The tables choice as the speech sheet's summary row names it. */
@Composable
internal fun tableModeLabel(modes: ReadingModes): String = stringResource(tableLabel(modes.tables))

/** The footnotes choice as the speech sheet's summary row names it. */
@Composable
internal fun footnoteModeLabel(modes: ReadingModes): String = stringResource(footnoteLabel(modes.footnotes))

private fun tableLabel(mode: TableMode): Int = when (mode) {
    TableMode.SUMMARY -> R.string.reading_tables_summary
    TableMode.ROWS -> R.string.reading_tables_rows
    TableMode.SKIP -> R.string.reading_skip
}

private fun footnoteLabel(mode: FootnoteMode): Int = when (mode) {
    FootnoteMode.READ -> R.string.reading_footnotes_read
    FootnoteMode.SKIP -> R.string.reading_skip
}

private fun equationLabel(mode: EquationMode): Int = when (mode) {
    EquationMode.ANNOUNCE -> R.string.reading_announce
    EquationMode.AS_WRITTEN -> R.string.reading_equations_as_written
    EquationMode.SKIP -> R.string.reading_skip
}

private fun captionLabel(mode: CaptionMode): Int = when (mode) {
    CaptionMode.ANNOUNCE -> R.string.reading_announce
    CaptionMode.SKIP -> R.string.reading_skip
}
