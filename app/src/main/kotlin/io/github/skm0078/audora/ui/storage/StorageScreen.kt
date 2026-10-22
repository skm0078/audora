// File: app/src/main/kotlin/io/github/skm0078/audora/ui/storage/StorageScreen.kt
// Purpose: Settings → Storage (UI plan): how much each kind of Audora's data takes, and a way to free it.

package io.github.skm0078.audora.ui.storage

import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.skm0078.audora.R
import io.github.skm0078.audora.ui.theme.Dimens

/** Deleting a model asks first (it means a download to get it back); clearing a cache does not. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageScreen(
    rows: List<StorageRow>,
    onAction: (StorageRow) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    var confirming by rememberSaveable { mutableStateOf<StorageKind?>(null) }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.storage_title), Modifier.semantics { heading() }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigate_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            Modifier.padding(innerPadding).verticalScroll(rememberScrollState()).padding(bottom = Dimens.SpaceXl),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
        ) {
            Text(
                stringResource(R.string.storage_total, sizeText(storageTotal(rows))),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.SpaceL),
            )
            rows.forEach { row ->
                StorageItem(row) {
                    if (row.action == StorageAction.DELETE) confirming = row.kind else onAction(row)
                }
            }
        }
    }
    rows.firstOrNull { it.kind == confirming }?.let { row ->
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text(stringResource(R.string.storage_delete_title, stringResource(kindLabel(row.kind)))) },
            text = { Text(stringResource(R.string.storage_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirming = null
                    onAction(row)
                }) { Text(stringResource(R.string.storage_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = null }) { Text(stringResource(R.string.storage_cancel)) }
            },
        )
    }
}

@Composable
private fun StorageItem(row: StorageRow, onAction: () -> Unit) {
    val size = row.bytes
    val label = stringResource(kindLabel(row.kind))
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = {
            Text(
                when {
                    size != null -> sizeText(size)
                    row.failed -> stringResource(R.string.storage_measure_failed)
                    else -> stringResource(R.string.storage_measuring)
                },
            )
        },
        trailingContent = row.action?.takeIf { (size ?: 0L) > 0L || it == StorageAction.MANAGE_VOICES }?.let {
            {
                // TalkBack moving between buttons hears which row this one frees, not a bare "Delete".
                val action = stringResource(actionLabel(it))
                val described = stringResource(R.string.storage_action_on, action, label)
                TextButton(onClick = onAction, modifier = Modifier.semantics { contentDescription = described }) {
                    Text(action)
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun sizeText(bytes: Long): String = Formatter.formatShortFileSize(LocalContext.current, bytes)

@StringRes
internal fun kindLabel(kind: StorageKind): Int = when (kind) {
    StorageKind.VOICES -> R.string.storage_voices
    StorageKind.OCR -> R.string.storage_ocr
    StorageKind.PUNCTUATION -> R.string.storage_punctuation
    StorageKind.VOICE_COMMANDS -> R.string.storage_voice_commands
    StorageKind.PAGE_PICTURES -> R.string.storage_page_pictures
    StorageKind.OCR_TEXT -> R.string.storage_ocr_text
    StorageKind.COVERS -> R.string.storage_covers
    StorageKind.VOICE_BLENDS -> R.string.storage_voice_blends
    StorageKind.SHARED_PDFS -> R.string.storage_shared_pdfs
}

@StringRes
private fun actionLabel(action: StorageAction): Int = when (action) {
    StorageAction.MANAGE_VOICES -> R.string.storage_manage
    StorageAction.DELETE -> R.string.storage_delete
    StorageAction.CLEAR -> R.string.storage_clear
}
