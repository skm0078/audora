// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/LineDialogs.kt
// Purpose: Dialogs about one sentence: "This sounded wrong" (RI-8b) with the fixes it offers, and why a greyed line
//          is skipped (RI-9).

package io.github.skm0078.audora.ui.reader

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.model.FootnoteMode
import io.github.skm0078.audora.core.model.Pace
import io.github.skm0078.audora.core.model.TableMode
import io.github.skm0078.audora.playback.report.SoundedWrongReason
import io.github.skm0078.audora.ui.theme.AudoraTheme

/** One tap picks a reason and sends the report; it goes to the app's log, which Settings → Share logs sends. */
@Composable
fun SoundedWrongDialog(onReason: (SoundedWrongReason) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.report_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.report_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SoundedWrongReason.entries.forEach { reason ->
                    TextButton(onClick = { onReason(reason) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(labelOf(reason)), modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.password_cancel)) } },
    )
}

/**
 * After a reason is picked: the report is noted, and each fix that would change something is one tap; a setting
 * applies at once and the dialog stays, so "Read this sentence again" can check it.
 */
@Composable
fun SoundedWrongFixDialog(offer: SoundedWrongOffer, onFix: (SoundedWrongFix) -> Unit, onDone: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(stringResource(R.string.sounded_wrong_fix_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                val settings = offer.fixes.any { it is SoundedWrongFix.Setting }
                val wordFix = SoundedWrongFix.FixWord in offer.fixes
                val explain = when {
                    settings -> stringResource(
                        R.string.sounded_wrong_fix_settings,
                        stringResource(R.string.speech_sheet_title),
                    )

                    !wordFix -> stringResource(R.string.sounded_wrong_fix_nothing)

                    else -> null
                }
                Text(
                    listOfNotNull(stringResource(R.string.report_noted), explain).joinToString("\n\n"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                offer.fixes.forEach { fix ->
                    TextButton(onClick = { onFix(fix) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(labelOf(fix)), modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text(stringResource(R.string.notes_done)) } },
    )
}

/** A tap on a greyed sentence: why it is skipped, and reading it anyway, which turns the skipping off. */
@Composable
fun UnreadableLineDialog(onReadAnyway: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.unreadable_title)) },
        text = { Text(stringResource(R.string.unreadable_body)) },
        confirmButton = { TextButton(onClick = onReadAnyway) { Text(stringResource(R.string.unreadable_read)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.password_cancel)) } },
    )
}

@StringRes
private fun labelOf(reason: SoundedWrongReason): Int = when (reason) {
    SoundedWrongReason.JUNK_READ -> R.string.report_junk_read
    SoundedWrongReason.WRONG_WORD -> R.string.report_wrong_word
    SoundedWrongReason.TABLE -> R.string.report_table
    SoundedWrongReason.NOTE -> R.string.report_note
    SoundedWrongReason.PAUSE -> R.string.report_pause
    SoundedWrongReason.OTHER -> R.string.report_other
}

@StringRes
private fun labelOf(fix: SoundedWrongFix): Int = when (fix) {
    SoundedWrongFix.FixWord -> R.string.sounded_wrong_fix_word

    SoundedWrongFix.HearAgain -> R.string.sounded_wrong_fix_hear_again

    SoundedWrongFix.SkipUnreadable -> R.string.sounded_wrong_fix_skip_unreadable

    SoundedWrongFix.SkipBrackets -> R.string.sounded_wrong_fix_skip_brackets

    is SoundedWrongFix.Tables -> tablesLabel(fix.mode)

    is SoundedWrongFix.Footnotes -> when (fix.mode) {
        FootnoteMode.READ -> R.string.sounded_wrong_fix_footnotes_read
        FootnoteMode.SKIP -> R.string.sounded_wrong_fix_footnotes_skip
    }

    is SoundedWrongFix.ClausePauses ->
        if (fix.on) R.string.sounded_wrong_fix_clause_on else R.string.sounded_wrong_fix_clause_off

    is SoundedWrongFix.SentencePause -> paceLabel(fix.pace)
}

@StringRes
private fun tablesLabel(mode: TableMode): Int = when (mode) {
    TableMode.SUMMARY -> R.string.sounded_wrong_fix_tables_summary
    TableMode.ROWS -> R.string.sounded_wrong_fix_tables_rows
    TableMode.SKIP -> R.string.sounded_wrong_fix_tables_skip
}

@StringRes
private fun paceLabel(pace: Pace): Int = when (pace) {
    Pace.TIGHT -> R.string.sounded_wrong_fix_pace_tight
    Pace.NORMAL -> R.string.sounded_wrong_fix_pace_normal
    Pace.RELAXED -> R.string.sounded_wrong_fix_pace_relaxed
}

@Preview
@Composable
private fun SoundedWrongDialogPreview() {
    AudoraTheme { SoundedWrongDialog(onReason = {}, onDismiss = {}) }
}
