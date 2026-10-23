// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/ReadingModesSection.kt
// Purpose: The speech sheet's "What to read" controls: skim, unreadable text, tables, notes, equations, skips.

package io.github.skm0078.audora.ui.reader

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.model.CaptionMode
import io.github.skm0078.audora.core.model.EquationMode
import io.github.skm0078.audora.core.model.FootnoteMode
import io.github.skm0078.audora.core.model.ReadingModes
import io.github.skm0078.audora.core.model.TableMode

/**
 * Plain Material controls for now (restyled with the UI pass). Each change goes to the playback service at once
 * and restarts the sentence playing, like a speed change, so the listener hears the difference straight away.
 */
@Composable
fun ReadingModesSection(modes: ReadingModes, onChange: (ReadingModes) -> Unit) {
    Text(stringResource(R.string.reading_modes_title), style = MaterialTheme.typography.titleMedium)
    ModeSwitch(R.string.reading_skim, R.string.reading_skim_detail, modes.skim) { onChange(modes.copy(skim = it)) }
    ModeSwitch(R.string.reading_skip_unreadable, R.string.reading_skip_unreadable_detail, modes.skipUnreadable) {
        onChange(modes.copy(skipUnreadable = it))
    }
    ModeChoice(R.string.reading_tables, TableMode.entries, modes.tables, ::tableLabel) {
        onChange(modes.copy(tables = it))
    }
    ModeChoice(R.string.reading_footnotes, FootnoteMode.entries, modes.footnotes, ::footnoteLabel) {
        onChange(modes.copy(footnotes = it))
    }
    ModeChoice(R.string.reading_equations, EquationMode.entries, modes.equations, ::equationLabel) {
        onChange(modes.copy(equations = it))
    }
    ModeChoice(R.string.reading_captions, CaptionMode.entries, modes.captions, ::captionLabel) {
        onChange(modes.copy(captions = it))
    }
    SkipSwitches(modes, onChange)
}

/** Plan item 2: one switch each for asides, bracketed notes, links and page numbers. */
@Composable
private fun SkipSwitches(modes: ReadingModes, onChange: (ReadingModes) -> Unit) {
    ModeSwitch(R.string.reading_skip_parentheses, R.string.reading_skip_parentheses_detail, modes.skipParentheses) {
        onChange(modes.copy(skipParentheses = it))
    }
    ModeSwitch(R.string.reading_skip_brackets, R.string.reading_skip_brackets_detail, modes.skipBrackets) {
        onChange(modes.copy(skipBrackets = it))
    }
    ModeSwitch(R.string.reading_skip_links, R.string.reading_skip_links_detail, modes.skipLinks) {
        onChange(modes.copy(skipLinks = it))
    }
    ModeSwitch(R.string.reading_announce_pages, R.string.reading_announce_pages_detail, modes.announcePages) {
        onChange(modes.copy(announcePages = it))
    }
}

@Composable
private fun ModeSwitch(@StringRes title: Int, @StringRes detail: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    // The whole row toggles and TalkBack reads it as one switch with its title (ADR-036, item 39).
    Row(
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(detail),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ModeChoice(
    @StringRes title: Int,
    options: List<T>,
    selected: T,
    label: (T) -> Int,
    onPick: (T) -> Unit,
) {
    Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onPick(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) { Text(stringResource(label(option))) }
        }
    }
}

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
