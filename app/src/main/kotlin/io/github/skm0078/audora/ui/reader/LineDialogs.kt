// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/LineDialogs.kt
// Purpose: Dialogs about one sentence: "This sounded wrong" (RI-8b) and why a greyed line is skipped (RI-9).

package io.github.skm0078.audora.ui.reader

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.skm0078.audora.R
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

@Preview
@Composable
private fun SoundedWrongDialogPreview() {
    AudoraTheme { SoundedWrongDialog(onReason = {}, onDismiss = {}) }
}
