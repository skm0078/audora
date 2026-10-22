// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/VoiceNotesUi.kt
// Purpose: Voice notes on screen (plan item 32): the microphone permission, the recording dialog, the notes list rows.

package io.github.skm0078.audora.ui.reader

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.notes.VoiceNote
import kotlinx.coroutines.delay

/**
 * Returns a function that records on a sentence, first asking for RECORD_AUDIO if it is not granted. A refusal is
 * said once ([VoiceNoteFailure.NO_MICROPHONE_PERMISSION]); Android stops asking after two, and Settings can grant it.
 */
@Composable
fun rememberRecordWithPermission(actions: VoiceNotesActions): (ReadingUnit) -> Unit {
    val context = LocalContext.current
    val pending = remember { arrayOfNulls<ReadingUnit>(1) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        AudoraLog.i(TAG, "Microphone permission granted=$granted")
        val unit = pending[0]
        pending[0] = null
        if (granted && unit != null) {
            actions.record(unit)
        } else if (!granted) {
            actions.permissionRefused()
        }
    }
    return remember(launcher, actions) {
        { unit ->
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            if (granted) {
                actions.record(unit)
            } else {
                pending[0] = unit
                launcher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }
}

/** The recording dialog and the failure notice, each shown while its state is set. */
@Composable
fun VoiceNotesOverlays(voice: VoiceNotesBinding) {
    voice.state.recordingFor?.let { unit -> RecordingDialog(unit, voice.actions) }
    voice.state.failure?.let { failure ->
        AlertDialog(
            onDismissRequest = voice.actions::clearFailure,
            text = { Text(stringResource(failure.message)) },
            confirmButton = {
                TextButton(onClick = voice.actions::clearFailure) { Text(stringResource(R.string.notes_ok)) }
            },
        )
    }
}

@Composable
private fun RecordingDialog(unit: ReadingUnit, actions: VoiceNotesActions) {
    var elapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(unit) {
        while (true) {
            elapsed = actions.recordingMs()
            delay(CLOCK_TICK_MS)
        }
    }
    AlertDialog(
        onDismissRequest = {}, // A stray tap must not lose the recording: Save or Discard only.
        title = { Text(stringResource(R.string.voice_note_recording, unit.page + 1)) },
        text = { Text(clock(elapsed), style = MaterialTheme.typography.displaySmall) },
        confirmButton = {
            TextButton(onClick = actions::finishRecording) { Text(stringResource(R.string.voice_note_save)) }
        },
        dismissButton = {
            TextButton(onClick = actions::cancelRecording) { Text(stringResource(R.string.voice_note_discard)) }
        },
    )
}

/** The voice-notes part of the bookmarks sheet: a heading, then one row per note. */
fun LazyListScope.voiceNoteItems(voice: VoiceNotesBinding, onOpen: (VoiceNote) -> Unit) {
    if (voice.state.notes.isEmpty()) return
    item(key = "voice-notes") {
        Text(
            stringResource(R.string.voice_notes_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 4.dp),
        )
    }
    items(voice.state.notes, key = { "voice-${it.id}" }) { note ->
        VoiceNoteRow(note, playing = voice.state.playing == note.id, voice.actions) { onOpen(note) }
        HorizontalDivider()
    }
}

@Composable
private fun VoiceNoteRow(note: VoiceNote, playing: Boolean, actions: VoiceNotesActions, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen)
            .padding(start = 24.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.bookmarks_page, note.anchor.page + 1),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                clock(note.durationMs),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (playing) {
            TextButton(onClick = actions::stopPlaying) { Text(stringResource(R.string.voice_note_stop)) }
        } else {
            TextButton(onClick = { actions.play(note) }) { Text(stringResource(R.string.voice_note_play)) }
        }
        TextButton(onClick = { actions.delete(note) }) { Text(stringResource(R.string.notes_delete)) }
    }
}

private val VoiceNoteFailure.message: Int
    get() = when (this) {
        VoiceNoteFailure.MICROPHONE -> R.string.voice_note_failed_microphone
        VoiceNoteFailure.NO_MICROPHONE_PERMISSION -> R.string.voice_note_failed_permission
        VoiceNoteFailure.TOO_SHORT -> R.string.voice_note_failed_short
        VoiceNoteFailure.NOT_SAVED -> R.string.notes_save_failed
        VoiceNoteFailure.NOT_PLAYED -> R.string.voice_note_failed_play
    }

private fun clock(ms: Long): String {
    val seconds = ms / MS_PER_SECOND
    return "%d:%02d".format(seconds / SECONDS_PER_MINUTE, seconds % SECONDS_PER_MINUTE)
}

private const val TAG = "VoiceNotes"
private const val CLOCK_TICK_MS = 250L
private const val MS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
