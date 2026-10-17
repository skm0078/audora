// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/BackupRoute.kt
// Purpose: Back up and restore from the library's ⋮ menu: save one file, or restore one and restart (item 28).

package io.github.skm0078.audora.ui.library

import android.content.Context
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.skm0078.audora.R
import io.github.skm0078.audora.playback.backup.BackupError
import io.github.skm0078.audora.playback.backup.BackupEvent
import io.github.skm0078.audora.playback.backup.Backups
import io.github.skm0078.audora.playback.backup.PendingRestore
import java.time.LocalDate

/** What the dialog shows: the two choices or the restore warning; progress comes from [Backups.state]. */
private enum class BackupStep { CHOOSE, CONFIRM_RESTORE }

/**
 * While [open], offers saving a backup and restoring one. The job runs in [backups]' own scope, so turning the
 * phone keeps it; its outcome is shown once. A restore is staged, then [onRestart] restarts the app, which puts it
 * in place before anything opens the database.
 */
@Composable
fun BackupRoute(backups: Backups, open: Boolean, onClose: () -> Unit, onRestart: () -> Unit) {
    val context = LocalContext.current
    val state by backups.state.collectAsStateWithLifecycle()
    var step by rememberSaveable { mutableStateOf(BackupStep.CHOOSE) }
    var sizeBytes by remember { mutableStateOf<Long?>(null) }
    val close = {
        step = BackupStep.CHOOSE
        onClose()
    }
    LaunchedEffect(open) { if (open) sizeBytes = backups.sizeEstimate() }
    LaunchedEffect(state.event) {
        val event = state.event ?: return@LaunchedEffect
        backups.consume()
        when (event) {
            BackupEvent.RestartNeeded -> onRestart()
            is BackupEvent.Saved -> toast(context, if (event.ok) R.string.backup_saved else R.string.backup_save_failed)
            is BackupEvent.Refused -> toast(context, messageOf(event.error))
        }
        close()
    }
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ZIP_TYPE)) { target ->
        target?.let(backups::startSave)
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { source ->
        if (source == null) step = BackupStep.CHOOSE else backups.startRestore(source)
    }
    if (open || state.working) {
        BackupDialog(
            step = step,
            working = state.working,
            sizeBytes = sizeBytes,
            onSave = { saver.launch(context.getString(R.string.backup_file_name, LocalDate.now().toString())) },
            onRestore = { step = BackupStep.CONFIRM_RESTORE },
            onConfirmRestore = { picker.launch(PICKABLE_TYPES) },
            onDismiss = { if (!state.working) close() },
        )
    }
}

@Composable
private fun BackupDialog(
    step: BackupStep,
    working: Boolean,
    sizeBytes: Long?,
    onSave: () -> Unit,
    onRestore: () -> Unit,
    onConfirmRestore: () -> Unit,
    onDismiss: () -> Unit,
) {
    val confirming = step == BackupStep.CONFIRM_RESTORE
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (confirming) R.string.backup_restore_title else R.string.library_backup)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val explain = if (confirming) R.string.backup_restore_warning else R.string.backup_explain
                Text(stringResource(explain), style = MaterialTheme.typography.bodyMedium)
                if (!confirming && sizeBytes != null) {
                    val size = Formatter.formatShortFileSize(LocalContext.current, sizeBytes)
                    Text(stringResource(R.string.backup_size, size), style = MaterialTheme.typography.bodyMedium)
                }
                if (working) LinearProgressIndicator()
            }
        },
        confirmButton = {
            when {
                working -> Unit

                confirming ->
                    TextButton(onClick = onConfirmRestore) { Text(stringResource(R.string.backup_restore_pick)) }

                else -> TextButton(onClick = onSave) { Text(stringResource(R.string.backup_save)) }
            }
        },
        dismissButton = {
            when {
                working -> Unit
                confirming -> TextButton(onClick = onDismiss) { Text(stringResource(R.string.backup_cancel)) }
                else -> TextButton(onClick = onRestore) { Text(stringResource(R.string.backup_restore)) }
            }
        },
    )
}

private fun messageOf(error: BackupError): Int = when (error) {
    BackupError.NOT_A_BACKUP -> R.string.backup_not_a_backup
    BackupError.FROM_NEWER_APP -> R.string.backup_from_newer_app
    BackupError.TOO_LARGE -> R.string.backup_too_large
    BackupError.NO_SPACE -> R.string.backup_no_space
    BackupError.BUSY -> R.string.backup_busy
    BackupError.IO_FAILED -> R.string.backup_restore_failed
}

/** The one-time message after a start that applied a staged restore or rolled one back. */
fun restoreMessageOf(result: PendingRestore.Result): Int = when (result) {
    PendingRestore.Result.APPLIED -> R.string.backup_restored
    PendingRestore.Result.FAILED, PendingRestore.Result.NONE -> R.string.backup_restore_rolled_back
}

private fun toast(context: Context, message: Int) = Toast.makeText(context, message, Toast.LENGTH_LONG).show()

private const val ZIP_TYPE = "application/zip"

// Some file managers label a zip as a generic binary file.
private val PICKABLE_TYPES = arrayOf(ZIP_TYPE, "application/x-zip-compressed", "application/octet-stream")
