// File: app/src/main/kotlin/io/github/skm0078/audora/ui/made/MadeSetCard.kt
// Purpose: One set of made files (an audiobook, a studio audio file, a copy) with what it is, how long and large,
//          and Play or Open, Share and Delete (ADR-068).

package io.github.skm0078.audora.ui.made

import android.app.Activity
import android.text.format.DateUtils
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.library.OutputKind
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.di.LibraryEntryPoint
import io.github.skm0078.audora.playback.outputs.MadePositions
import io.github.skm0078.audora.playback.outputs.OutputFiles
import io.github.skm0078.audora.playback.studio.studioDuration
import io.github.skm0078.audora.ui.reader.pitchLabel
import io.github.skm0078.audora.ui.theme.Dimens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * [bookTitle] is shown on the Made screen, where sets of many books are listed; a book's own details leave it out.
 * A set whose files are all gone offers only Remove from the list: there is nothing left to play or share.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MadeSetCard(made: MadeSet, bookTitle: String?, onPlay: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val remover = rememberSetRemover(made)
    var confirming by rememberSaveable(made.set.key) { mutableStateOf(false) }
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(Dimens.SpaceL), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
            Text(
                bookTitle ?: kindLabel(made.set.kind),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            if (bookTitle != null) Text(kindLabel(made.set.kind), style = MaterialTheme.typography.bodyMedium)
            Text(
                detailLine(made),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            missingLine(made)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
                if (made.allMissing) {
                    TextButton(onClick = remover.forget) { Text(stringResource(R.string.made_forget)) }
                } else {
                    SetButtons(made, onPlay, onDelete = { confirming = true })
                }
            }
        }
    }
    if (confirming) {
        DeleteDialog(
            what = kindLabel(made.set.kind),
            size = made.set.sizeBytes?.let { Formatter.formatShortFileSize(context, it) }
                ?: pluralStringResource(R.plurals.made_files, made.set.files.size, made.set.files.size),
            onConfirm = {
                confirming = false
                remover.delete()
            },
            onDismiss = { confirming = false },
        )
    }
}

/** Play for audio, Open for a document; then Share and Delete. */
@Composable
private fun SetButtons(made: MadeSet, onPlay: (String) -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    if (made.set.kind.isAudio) {
        Button(onClick = { onPlay(made.set.key) }) { Text(stringResource(R.string.made_play)) }
    } else {
        Button(onClick = { MadeIntents.open(context, made.present[0]) }) { Text(stringResource(R.string.made_open)) }
    }
    OutlinedButton(onClick = { MadeIntents.share(context, made.present) }) { Text(stringResource(R.string.made_share)) }
    TextButton(onClick = onDelete) {
        Text(stringResource(R.string.made_delete), color = MaterialTheme.colorScheme.error)
    }
}

/** Deletes a set's files, asking Android for the listener's consent when it must, then forgets the set. */
internal class SetRemover(val delete: () -> Unit, val forget: () -> Unit)

@Composable
private fun rememberSetRemover(made: MadeSet): SetRemover {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val outputs = remember { LibraryEntryPoint.of(context).outputs() }
    val forget: () -> Unit = {
        scope.launch {
            MadePositions(context).clear(made.set.key)
            outputs.deleteSet(made.set.key)
        }
    }
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val agreed = result.resultCode == Activity.RESULT_OK
        AudoraLog.i(TAG, "Delete consent given=$agreed")
        if (agreed) {
            forget()
            Toast.makeText(context, R.string.made_deleted, Toast.LENGTH_SHORT).show()
        }
    }
    return remember(made) {
        SetRemover(
            delete = {
                scope.launch {
                    val files = made.present.map { it.location }
                    val result = withContext(Dispatchers.IO) { OutputFiles(context).delete(files) }
                    val needs = result.needsConsent
                    when {
                        needs != null -> consent.launch(IntentSenderRequest.Builder(needs.intentSender).build())

                        result.deleted == files.size -> {
                            forget()
                            Toast.makeText(context, R.string.made_deleted, Toast.LENGTH_SHORT).show()
                        }

                        else -> Toast.makeText(context, R.string.made_not_deleted, Toast.LENGTH_LONG).show()
                    }
                }
            },
            forget = forget,
        )
    }
}

@Composable
private fun DeleteDialog(what: String, size: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.made_delete_title)) },
        text = { Text(stringResource(R.string.made_delete_text, what, size)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.made_delete_confirm), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.made_delete_cancel)) } },
    )
}

@Composable
internal fun kindLabel(kind: OutputKind): String = stringResource(
    when (kind) {
        OutputKind.AUDIOBOOK -> R.string.made_kind_audiobook
        OutputKind.STUDIO_AUDIO -> R.string.made_kind_studio
        OutputKind.ACCESSIBLE_COPY -> R.string.made_kind_accessible
        OutputKind.TALKING_BOOK -> R.string.made_kind_talking
    },
)

/** "12 chapters · 5 h 20 min · 310 MB · 24 Oct 2026 · Pitch: 10% higher", leaving out what is not known. */
@Composable
private fun detailLine(made: MadeSet): String {
    val context = LocalContext.current
    val set = made.set
    val count = set.files.size
    val parts = buildList {
        add(
            if (set.kind == OutputKind.AUDIOBOOK) {
                pluralStringResource(R.plurals.made_chapters, count, count)
            } else {
                pluralStringResource(R.plurals.made_files, count, count)
            },
        )
        set.durationMs?.let { add(context.studioDuration(it)) }
        set.sizeBytes?.let { add(Formatter.formatShortFileSize(context, it)) }
        add(
            DateUtils.formatDateTime(
                context,
                set.createdAtMillis,
                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR,
            ),
        )
    }
    val pitch = set.files.firstNotNullOfOrNull { it.pitch }?.let { stringResource(R.string.made_pitch, pitchLabel(it)) }
    return (parts + listOfNotNull(pitch)).joinToString(SEPARATOR)
}

@Composable
private fun missingLine(made: MadeSet): String? = when {
    made.missing == 0 -> null
    made.allMissing -> stringResource(R.string.made_missing)
    else -> stringResource(R.string.made_partly_missing, made.missing, made.set.files.size)
}

private const val TAG = "Library.Made"
private const val SEPARATOR = " · "
