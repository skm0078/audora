// File: app/src/main/kotlin/io/github/skm0078/audora/ui/made/MadeSetActions.kt
// Purpose: What one made set (an audiobook, a studio audio file, a copy) says about itself and what can be done with
//          it: Play or Open, Share, and Delete in its ⋮ menu, as icon buttons (ADR-068, ADR 2026-10-28-made-tab).

package io.github.skm0078.audora.ui.made

import android.app.Activity
import android.text.format.DateUtils
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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

/** Play for audio, Open for a document: what the cover and the filled button do; nothing once the files are gone. */
@Composable
internal fun primaryAction(made: MadeSet, onPlay: (String) -> Unit): (() -> Unit)? {
    val context = LocalContext.current
    return when {
        made.allMissing -> null
        made.set.kind.isAudio -> ({ onPlay(made.set.key) })
        else -> ({ MadeIntents.open(context, made.present[0]) })
    }
}

/** The filled Play or Open; TalkBack hears what it plays, "Play Gitanjali, Audiobook". */
@Composable
internal fun PrimaryButton(made: MadeSet, named: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val (icon, label) = if (made.set.kind.isAudio) {
        R.drawable.ic_play to stringResource(R.string.made_play_named, named)
    } else {
        kindIcon(made.set.kind) to stringResource(R.string.made_open_named, named)
    }
    FilledIconButton(onClick = onClick, modifier = modifier.size(Dimens.PlayButtonSmall - Dimens.SpaceXs)) {
        Icon(painterResource(icon), label)
    }
}

/**
 * Share, then ⋮ with Delete; a set whose files are all gone offers only Remove from the list, as words, since
 * there is nothing left to play or share.
 */
@Composable
internal fun ShareAndMore(made: MadeSet, named: String) {
    val context = LocalContext.current
    val remover = rememberSetRemover(made)
    var menu by remember { mutableStateOf(false) }
    var confirming by rememberSaveable(made.set.key) { mutableStateOf(false) }
    if (made.allMissing) {
        TextButton(onClick = remover.forget) { Text(stringResource(R.string.made_forget)) }
        return
    }
    IconButton(onClick = { MadeIntents.share(context, made.present) }) {
        Icon(painterResource(R.drawable.ic_share), stringResource(R.string.made_share_named, named))
    }
    Box {
        IconButton(onClick = { menu = true }) {
            Icon(painterResource(R.drawable.ic_more_vert), stringResource(R.string.made_more_named, named))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.made_delete), color = MaterialTheme.colorScheme.error) },
                leadingIcon = {
                    Icon(painterResource(R.drawable.ic_delete), null, tint = MaterialTheme.colorScheme.error)
                },
                onClick = {
                    menu = false
                    confirming = true
                },
            )
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

/** The kind's own icon, on the cover's badge and on Open. */
@DrawableRes
internal fun kindIcon(kind: OutputKind): Int = when (kind) {
    OutputKind.AUDIOBOOK -> R.drawable.ic_headphones
    OutputKind.STUDIO_AUDIO -> R.drawable.ic_studio
    OutputKind.ACCESSIBLE_COPY -> R.drawable.ic_article
    OutputKind.TALKING_BOOK -> R.drawable.ic_talking_book
}

/**
 * "12 chapters · 5 h 20 min · 310 MB", and with [full] the date and pitch too ("· 24 Oct 2026 · Pitch: 10% higher"),
 * leaving out what is not known. A tile under a cover has room for the short line only.
 */
@Composable
internal fun detailLine(made: MadeSet, full: Boolean): String {
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
        if (full) {
            add(DateUtils.formatDateTime(context, set.createdAtMillis, DATE_FLAGS))
            set.files.firstNotNullOfOrNull {
                it.pitch
            }?.let { add(stringResource(R.string.made_pitch, pitchLabel(it))) }
        }
    }
    return parts.joinToString(DETAIL_SEPARATOR)
}

@Composable
internal fun missingLine(made: MadeSet): String? = when {
    made.missing == 0 -> null
    made.allMissing -> stringResource(R.string.made_missing)
    else -> stringResource(R.string.made_partly_missing, made.missing, made.set.files.size)
}

private const val TAG = "Library.Made"
private const val DATE_FLAGS = DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR
