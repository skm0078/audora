// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/BackupRoute.kt
// Purpose: Back up and restore from the library's ⋮ menu: save one file, or restore one and restart (item 28).

package io.github.skm0078.audora.ui.library

import android.content.Context
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.playback.backup.BackupError
import io.github.skm0078.audora.playback.backup.Backups
import java.time.LocalDate
import kotlinx.coroutines.launch

/** What the dialog shows: the two choices, the restore warning, or progress while a file is written or read. */
private enum class BackupStep { CHOOSE, CONFIRM_RESTORE, WORKING }

/**
 * While [open], offers saving a backup and restoring one. A restore is staged, then [onRestart] restarts the app,
 * which puts it in place before anything opens the database.
 */
@Composable
fun BackupRoute(backups: Backups, open: Boolean, onClose: () -> Unit, onRestart: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableStateOf(BackupStep.CHOOSE) }
    val close = {
        step = BackupStep.CHOOSE
        onClose()
    }
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ZIP_TYPE)) { target ->
        if (target == null) return@rememberLauncherForActivityResult
        step = BackupStep.WORKING
        scope.launch {
            val saved = backups.save(target)
            toast(context, if (saved is Outcome.Failure) R.string.backup_save_failed else R.string.backup_saved)
            close()
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { source ->
        if (source == null) {
            step = BackupStep.CHOOSE
            return@rememberLauncherForActivityResult
        }
        step = BackupStep.WORKING
        scope.launch {
            when (val staged = backups.stage(source)) {
                is Outcome.Success -> onRestart()

                is Outcome.Failure -> {
                    toast(context, messageOf(staged.error))
                    close()
                }
            }
        }
    }
    if (open) {
        BackupDialog(
            step = step,
            onSave = { saver.launch(context.getString(R.string.backup_file_name, LocalDate.now().toString())) },
            onRestore = { step = BackupStep.CONFIRM_RESTORE },
            onConfirmRestore = { picker.launch(PICKABLE_TYPES) },
            onDismiss = { if (step != BackupStep.WORKING) close() },
        )
    }
}

@Composable
private fun BackupDialog(
    step: BackupStep,
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
                if (step == BackupStep.WORKING) LinearProgressIndicator()
            }
        },
        confirmButton = {
            when (step) {
                BackupStep.CHOOSE -> TextButton(onClick = onSave) { Text(stringResource(R.string.backup_save)) }

                BackupStep.CONFIRM_RESTORE ->
                    TextButton(onClick = onConfirmRestore) { Text(stringResource(R.string.backup_restore_pick)) }

                BackupStep.WORKING -> Unit
            }
        },
        dismissButton = {
            when (step) {
                BackupStep.CHOOSE -> TextButton(onClick = onRestore) { Text(stringResource(R.string.backup_restore)) }

                BackupStep.CONFIRM_RESTORE ->
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.backup_cancel)) }

                BackupStep.WORKING -> Unit
            }
        },
    )
}

private fun messageOf(error: BackupError): Int = when (error) {
    BackupError.NOT_A_BACKUP -> R.string.backup_not_a_backup
    BackupError.FROM_NEWER_APP -> R.string.backup_from_newer_app
    BackupError.TOO_LARGE -> R.string.backup_too_large
    BackupError.IO_FAILED -> R.string.backup_restore_failed
}

private fun toast(context: Context, message: Int) = Toast.makeText(context, message, Toast.LENGTH_LONG).show()

private const val ZIP_TYPE = "application/zip"

// Some file managers label a zip as a generic binary file.
private val PICKABLE_TYPES = arrayOf(ZIP_TYPE, "application/x-zip-compressed", "application/octet-stream")
