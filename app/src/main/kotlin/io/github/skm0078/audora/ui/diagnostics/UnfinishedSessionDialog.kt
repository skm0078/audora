// File: app/src/main/kotlin/io/github/skm0078/audora/ui/diagnostics/UnfinishedSessionDialog.kt
// Purpose: "Reading stopped unexpectedly — resume?" shown when the previous process died while reading.

package io.github.skm0078.audora.ui.diagnostics

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.diagnostics.ReadingSession
import io.github.skm0078.audora.ui.theme.AudoraTheme

/** Tapping outside counts as "Not now", so the prompt never blocks the app. */
@Composable
fun UnfinishedSessionDialog(session: ReadingSession, onResume: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.unfinished_title)) },
        // Pages are 0-based inside the app and 1-based for people.
        text = { Text(stringResource(R.string.unfinished_body, session.page + 1)) },
        confirmButton = { TextButton(onClick = onResume) { Text(stringResource(R.string.unfinished_resume)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.unfinished_not_now)) } },
    )
}

@Preview
@Composable
private fun UnfinishedSessionDialogPreview() {
    AudoraTheme { UnfinishedSessionDialog(ReadingSession("book", 13, 0), onResume = {}, onDismiss = {}) }
}
