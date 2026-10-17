// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/WatchedFoldersRoute.kt
// Purpose: Watched folders in the library: the scan when it opens, the folder list and the folder picker (item 26).

package io.github.skm0078.audora.ui.library

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
import io.github.skm0078.audora.playback.documents.WatchedFolders
import kotlinx.coroutines.launch

/**
 * Scans the watched folders each time the library is shown and says how many books joined; while [open], shows the
 * folder list. A folder picked is watched and scanned at once.
 */
@Composable
fun WatchedFoldersRoute(watched: WatchedFolders, open: Boolean, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var folders by remember { mutableStateOf<List<WatchedFolder>?>(null) }
    val tell: (Int) -> Unit = { added ->
        if (added > 0) {
            val text = context.resources.getQuantityString(R.plurals.watched_added, added, added)
            Toast.makeText(context, text, Toast.LENGTH_LONG).show()
        }
    }
    LaunchedEffect(watched) { tell(watched.scan()) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) {
            scope.launch {
                if (watched.watch(tree)) {
                    folders = watched.folders()
                    tell(watched.scan())
                } else {
                    Toast.makeText(context, R.string.watched_no_grant, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    if (open) {
        LaunchedEffect(Unit) { folders = watched.folders() }
        WatchedFoldersDialog(
            folders = folders,
            onAdd = { picker.launch(null) },
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
                folders.orEmpty().forEach { folder -> FolderRow(folder) { onRemove(folder) } }
            }
        },
        confirmButton = { TextButton(onClick = onAdd) { Text(stringResource(R.string.watched_add)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.watched_done)) } },
    )
}

@Composable
private fun FolderRow(folder: WatchedFolder, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            folder.name.ifBlank { stringResource(R.string.watched_unnamed) },
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        TextButton(onClick = onRemove) { Text(stringResource(R.string.watched_remove)) }
    }
}
