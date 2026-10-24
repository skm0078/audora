// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/SentenceMenuUi.kt
// Purpose: The sentence menu with a line under each item saying what it is for, and the bar and notice of a
//          highlight over several sentences (phone test issues 5, 6 and 8).

package io.github.skm0078.audora.ui.reader

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.config.HighlightConfig
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.notes.Highlight
import io.github.skm0078.audora.ui.theme.Dimens

/** One row of the sentence menu: what it does, and a line saying what for. */
private class MenuEntry(@StringRes val label: Int, @StringRes val hint: Int, val onClick: () -> Unit)

/** "Share as audio": the highlighted passage when the sentence has one, else to the end of the paragraph. */
private fun shareEntry(highlight: Highlight?, onClick: () -> Unit) = MenuEntry(
    R.string.sentence_menu_share_clip,
    if (highlight != null) R.string.sentence_hint_share_highlight else R.string.sentence_hint_share_clip,
    onClick,
)

/** What the menu's rows act through. */
private data class MenuSources(
    val actions: BookNotesActions,
    val reader: ReaderActions,
    val voice: VoiceNotesBinding,
    val lookUp: LookUpBinding,
)

/**
 * The rows for [unit]. On a highlighted sentence, Remove and Share act on the whole highlighted passage (the first,
 * when two merged into this sentence); a sentence with no words ("* * *") can never show a band, so it offers none.
 */
private fun sentenceMenu(unit: ReadingUnit, notes: NotesState, sources: MenuSources): List<MenuEntry> {
    val (actions, reader, voice, lookUp) = sources
    val highlight = notes.highlightsOn(unit).firstOrNull()
    val canHighlight = highlight == null && unit.text.any(Char::isLetterOrDigit)
    fun closing(action: () -> Unit): () -> Unit = {
        actions.dismissMenu()
        action()
    }
    val record = closing { voice.askToRecord(unit) }
    val define = closing { lookUp.actions.open(unit) }
    val report = closing { reader.onReportSentence(unit) }
    val share = closing { if (highlight != null) reader.onShareHighlight(highlight) else reader.onShareClip(unit) }
    return listOfNotNull(
        MenuEntry(R.string.sentence_menu_bookmark, R.string.sentence_hint_bookmark) { actions.bookmark(unit) },
        MenuEntry(R.string.sentence_menu_highlight, R.string.sentence_hint_highlight) { actions.toggleHighlight(unit) }
            .takeIf { canHighlight },
        MenuEntry(R.string.sentence_menu_highlight_range, R.string.sentence_hint_highlight_range) {
            actions.startRange(unit)
        }.takeIf { canHighlight },
        MenuEntry(R.string.sentence_menu_unhighlight, R.string.sentence_hint_unhighlight) {
            actions.toggleHighlight(unit)
        }.takeIf { highlight != null },
        MenuEntry(R.string.sentence_menu_voice_note, R.string.sentence_hint_voice_note, record)
            .takeIf { voice.enabled },
        MenuEntry(R.string.sentence_menu_look_up, R.string.sentence_hint_look_up, define)
            .takeIf { lookUp.enabled },
        MenuEntry(R.string.sentence_menu_fix, R.string.sentence_hint_fix) { actions.fix(unit) },
        MenuEntry(R.string.report_title, R.string.sentence_hint_report, report),
        shareEntry(highlight, share),
    )
}

/** The menu a long-press on [unit] opens. */
@Composable
internal fun SentenceMenu(
    unit: ReadingUnit,
    notes: NotesState,
    actions: BookNotesActions,
    reader: ReaderActions,
    voice: VoiceNotesBinding,
    lookUp: LookUpBinding,
) {
    val entries = sentenceMenu(unit, notes, MenuSources(actions, reader, voice, lookUp))
    SentenceMenuDialog(entries, onDismiss = actions::dismissMenu)
}

/**
 * While a passage's end is picked, its bar at the bottom of the reader (the box around it takes no touches, so the
 * page is still tapped through it); then why a passage was refused, once.
 */
@Composable
internal fun PassageOverlays(notes: NotesState, actions: BookNotesActions) {
    notes.rangeFrom?.let { from ->
        Box(Modifier.fillMaxSize()) {
            HighlightRangeBar(from, actions::cancelRange, Modifier.align(Alignment.BottomCenter))
        }
    }
    notes.rangeRefused?.let { RangeRefusedDialog(it, actions::dismissRangeRefusal) }
}

@Composable
private fun SentenceMenuDialog(rows: List<MenuEntry>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sentence_menu_title)) },
        text = {
            // Eight rows with their hints are taller than a small phone's dialog: it scrolls.
            Column(Modifier.verticalScroll(rememberScrollState())) {
                rows.forEach { MenuRow(it.label, it.hint, it.onClick) }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.password_cancel)) } },
    )
}

@Composable
private fun MenuRow(@StringRes label: Int, @StringRes hint: Int, onClick: () -> Unit) {
    // One button to TalkBack: the label, then the hint, read together.
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.TouchTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = Dimens.SpaceS),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
        Text(
            stringResource(hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Shown while the end of a highlighted passage is picked: where it starts, what to do, and Cancel. Polite live region,
 * so TalkBack says it once it appears.
 */
@Composable
fun HighlightRangeBar(from: ReadingUnit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier
                .padding(horizontal = Dimens.SpaceM, vertical = Dimens.SpaceXs)
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.highlight_range_prompt, from.page + 1),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onCancel) { Text(stringResource(R.string.password_cancel)) }
        }
    }
}

@Composable
private fun RangeRefusedDialog(why: RangeRefusal, onDismiss: () -> Unit) {
    val text = when (why) {
        RangeRefusal.TOO_LONG -> stringResource(R.string.highlight_range_too_long, HighlightConfig().maxPassagePages)
        RangeRefusal.PAGE_UNREADABLE -> stringResource(R.string.highlight_range_unreadable)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.notes_ok)) } },
    )
}
