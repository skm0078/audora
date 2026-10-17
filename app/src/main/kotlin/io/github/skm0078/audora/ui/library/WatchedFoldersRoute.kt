// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/WatchedFoldersRoute.kt
// Purpose: Watched folders in the library: the scan when it opens, the folder list and the folder picker (item 26).

package io.github.skm0078.audora.ui.library

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.library.WatchedFolder
import io.github.skm0078.audora.playback.documents.WatchResult
import io.github.skm0078.audora.playback.documents.WatchScan
import io.github.skm0078.audora.playback.documents.WatchedFolders
import kotlinx.coroutines.launch

/**
 * Scans the watched folders when the library is shown (at most every few minutes) and says how many books joined and
 * whether a folder needs picking again; while [open], shows the folder list. A folder picked is watched and scanned
 * at once; picking an unreachable folder again restores it with what it had seen.
 */
@Composable
fun WatchedFoldersRoute(watched: WatchedFolders, open: Boolean, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var folders by remember { mutableStateOf<List<WatchedFolder>?>(null) }
    val tell: (WatchScan) -> Unit = { scan ->
        if (scan.added > 0) {
            val text = context.resources.getQuantityString(R.plurals.watched_added, scan.added, scan.added)
            Toast.makeText(context, text, Toast.LENGTH_LONG).show()
        }
        if (scan.unreachable > 0) {
            val text = context.resources.getQuantityString(
                R.plurals.watched_unreachable,
                scan.unreachable,
                scan.unreachable,
            )
            Toast.makeText(context, text, Toast.LENGTH_LONG).show()
        }
    }
    LaunchedEffect(watched) { tell(watched.scan()) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) {
            scope.launch {
                when (watched.watch(tree)) {
                    WatchResult.WATCHED -> {
                        folders = watched.folders()
                        tell(watched.scan())
                        folders = watched.folders() // The scan may have found it unreachable.
                    }

                    WatchResult.NO_GRANT -> Toast.makeText(context, R.string.watched_no_grant, Toast.LENGTH_LONG).show()

                    WatchResult.NOT_SAVED -> Toast.makeText(
                        context,
                        R.string.watched_not_saved,
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }
    if (open) {
        LaunchedEffect(Unit) { folders = watched.folders() }
        WatchedFoldersDialog(
            folders = folders,
            onAdd = { picker.launch(null) },
            onPickAgain = { folder -> picker.launch(Uri.parse(folder.treeUri)) },
            onRemove = { folder ->
                scope.launch {
                    watched.unwatch(folder)
                    folders = watched.folders()
                }
            },
            onDismiss = onClose,
        )
    }
}

/** The watched folders, each removable; [folders] is null while they load. */
@Composable
private fun WatchedFoldersDialog(
    folders: List<WatchedFolder>?,
    onAdd: () -> Unit,
    onPickAgain: (WatchedFolder) -> Unit,
    onRemove: (WatchedFolder) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_watched_folders)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.watched_explain), style = MaterialTheme.typography.bodyMedium)
                if (folders?.isEmpty() == true) {
                    Text(
                        stringResource(R.string.watched_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                folders.orEmpty().forEach { folder -> FolderRow(folder, { onPickAgain(folder) }) { onRemove(folder) } }
            }
        },
        confirmButton = { TextButton(onClick = onAdd) { Text(stringResource(R.string.watched_add)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.watched_done)) } },
    )
}

@Composable
private fun FolderRow(folder: WatchedFolder, onPickAgain: () -> Unit, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                folder.name.ifBlank { stringResource(R.string.watched_unnamed) },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!folder.reachable) {
                Text(
                    stringResource(R.string.watched_unreachable_row),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        if (!folder.reachable) TextButton(onClick = onPickAgain) { Text(stringResource(R.string.watched_pick_again)) }
        TextButton(onClick = onRemove) { Text(stringResource(R.string.watched_remove)) }
    }
}
