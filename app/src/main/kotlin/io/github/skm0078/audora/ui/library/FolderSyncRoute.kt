// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/FolderSyncRoute.kt
// Purpose: Sync through a folder (CF-16) in the library: a sync when the library opens, and the sheet to pick the
//          folder, sync now, turn book copies on or off, and stop.

package io.github.skm0078.audora.ui.library

import android.content.Context
import android.net.Uri
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.skm0078.audora.R
import io.github.skm0078.audora.di.FolderSyncEntryPoint
import io.github.skm0078.audora.playback.sync.FolderSync
import io.github.skm0078.audora.playback.sync.FolderSyncState
import io.github.skm0078.audora.playback.sync.SyncChoose
import io.github.skm0078.audora.playback.sync.SyncResult
import io.github.skm0078.audora.ui.theme.Dimens
import kotlinx.coroutines.launch

/**
 * Syncs when the library is shown (skipped within a minute of the last sync, or when sync is off) and says what came
 * from the other devices; while [FolderSyncUi.open], shows the sheet. A folder picked is synced through at once.
 */
@Composable
fun FolderSyncRoute() {
    val context = LocalContext.current
    val sync = remember { FolderSyncEntryPoint.of(context) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(sync) {
        sync.load()
        tell(context, sync.sync(force = false), manual = false)
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) scope.launch { choose(context, sync, tree) }
    }
    if (FolderSyncUi.open) {
        val state by sync.state.collectAsStateWithLifecycle()
        FolderSyncDialog(
            state = state,
            onPick = { picker.launch(state.settings.treeUri?.let(Uri::parse)) },
            onSyncNow = { scope.launch { tell(context, sync.sync(force = true), manual = true) } },
            onCopyBooks = { on -> scope.launch { sync.setCopyBooks(on) } },
            onStop = {
                scope.launch {
                    sync.stop()
                    Toast.makeText(context, R.string.sync_stopped, Toast.LENGTH_LONG).show()
                }
            },
            onDismiss = { FolderSyncUi.open = false },
        )
    }
}

private suspend fun choose(context: Context, sync: FolderSync, tree: Uri) {
    when (sync.choose(tree)) {
        SyncChoose.CHOSEN -> tell(context, sync.sync(force = true), manual = true)
        SyncChoose.NO_GRANT -> Toast.makeText(context, R.string.sync_no_grant, Toast.LENGTH_LONG).show()
        SyncChoose.NOT_SAVED -> Toast.makeText(context, R.string.sync_not_saved, Toast.LENGTH_LONG).show()
    }
}

/** Says what a sync did: a sync the app started alone speaks only when something changed or it failed. */
private fun tell(context: Context, result: SyncResult, manual: Boolean) {
    val text = when (result) {
        is SyncResult.Synced -> syncedText(context, result, manual)
        SyncResult.FolderLost -> context.getString(R.string.sync_lost)
        SyncResult.NotSaved -> context.getString(R.string.sync_not_saved)
        SyncResult.NoFolder, SyncResult.Skipped -> null
    }
    text?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
}

private fun syncedText(context: Context, result: SyncResult.Synced, manual: Boolean): String? {
    val parts = buildList {
        if (result.changes > 0) {
            add(context.resources.getQuantityString(R.plurals.sync_changes, result.changes, result.changes))
        }
        if (result.added > 0) {
            add(context.resources.getQuantityString(R.plurals.sync_books_added, result.added, result.added))
        }
    }
    return when {
        parts.isNotEmpty() -> parts.joinToString(" ")
        manual -> context.getString(R.string.sync_up_to_date)
        else -> null
    }
}

@Composable
private fun FolderSyncDialog(
    state: FolderSyncState,
    onPick: () -> Unit,
    onSyncNow: () -> Unit,
    onCopyBooks: (Boolean) -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit,
) {
    val settings = state.settings
    val chosen = settings.treeUri != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_sync_folder)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
            ) {
                Text(stringResource(R.string.sync_explain), style = MaterialTheme.typography.bodyMedium)
                FolderStatus(state)
                CopyBooksRow(settings.copyBooks, onCopyBooks)
                OutlinedButton(onClick = onPick, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (chosen) R.string.sync_change else R.string.sync_pick))
                }
                if (chosen) {
                    OutlinedButton(onClick = onSyncNow, enabled = !state.running, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.sync_now))
                    }
                    TextButton(onClick = onStop, enabled = !state.running, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.sync_stop))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.sync_done)) } },
    )
}

/** The folder's name and when it last synced, or that none is picked. */
@Composable
private fun FolderStatus(state: FolderSyncState) {
    val settings = state.settings
    if (settings.treeUri == null) {
        Text(
            stringResource(R.string.sync_no_folder),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val name = settings.folderName.ifBlank { stringResource(R.string.sync_unnamed) }
    Text(
        stringResource(R.string.sync_folder_label, name),
        style = MaterialTheme.typography.bodyLarge,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
    val last = settings.lastSyncMillis
    val status = when {
        state.running -> stringResource(R.string.sync_running)
        settings.lastFailed -> stringResource(R.string.sync_last_failed)
        last == null -> stringResource(R.string.sync_never)
        else -> stringResource(R.string.sync_last, DateUtils.getRelativeTimeSpanString(last).toString())
    }
    Text(
        status,
        style = MaterialTheme.typography.bodyMedium,
        color = if (settings.lastFailed && !state.running) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}

@Composable
private fun CopyBooksRow(on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = on, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.sync_copy_books), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.sync_copy_books_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = on, onCheckedChange = null)
    }
}
