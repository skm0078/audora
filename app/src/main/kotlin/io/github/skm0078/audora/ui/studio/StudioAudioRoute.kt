// File: app/src/main/kotlin/io/github/skm0078/audora/ui/studio/StudioAudioRoute.kt
// Purpose: One book's studio mode in one place (ADR-062): what it does, its live progress, what is ready, where it is
//          kept, and Start, Stop, Listen, Save as a file and Delete.

package io.github.skm0078.audora.ui.studio

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.playback.jobs.LongJob
import io.github.skm0078.audora.playback.jobs.LongJobKind
import io.github.skm0078.audora.playback.jobs.LongJobs
import io.github.skm0078.audora.playback.studio.StudioBookAudio
import io.github.skm0078.audora.playback.studio.StudioMode
import io.github.skm0078.audora.playback.studio.studioDuration
import io.github.skm0078.audora.ui.jobs.JobProgressBar
import io.github.skm0078.audora.ui.library.titleOrUntitled
import io.github.skm0078.audora.ui.theme.Dimens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * While [book] is set, its studio mode. [saving] is true while its audio is being saved as a file. What is ready is
 * read again whenever the run's progress or end changes, so the numbers move while the dialog is open.
 */
@Composable
fun StudioAudioRoute(book: Book?, saving: Boolean, actions: StudioAudioActions) {
    if (book == null) return
    val context = LocalContext.current.applicationContext
    val jobs by LongJobs.jobs.collectAsStateWithLifecycle()
    val studioJob = jobs[LongJobKind.STUDIO]
    val job = studioJob?.takeIf { it.bookId == book.id }
    val otherBookRunning = studioJob?.running == true && job == null
    // Bumped by a delete, so what is ready is read again.
    var changes by rememberSaveable(book.id) { mutableIntStateOf(0) }
    val ready by produceState<StudioBookAudio?>(null, book.id, job?.percent, job?.end, changes) {
        value = withContext(Dispatchers.IO) { StudioMode.bookAudio(context, book.id) }
    }
    var message by rememberSaveable(book.id) { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = actions::close,
        title = { Text(stringResource(R.string.studio_sheet_title, titleOrUntitled(book.title))) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
            ) {
                Text(stringResource(R.string.studio_sheet_what), style = MaterialTheme.typography.bodyMedium)
                RunSection(job, otherBookRunning)
                ReadySection(ready)
                Text(stringResource(R.string.studio_sheet_where), style = MaterialTheme.typography.bodySmall)
                message?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                ready?.let { audio ->
                    ReadyActions(book, audio, saving, rendering = job?.running == true, actions) { deleted ->
                        message = context.getString(
                            if (deleted) R.string.studio_sheet_deleted else R.string.studio_sheet_delete_refused,
                        )
                        changes++
                    }
                }
            }
        },
        confirmButton = { MainButton(book, job, otherBookRunning, hasReady = ready != null, actions) },
        dismissButton = { TextButton(onClick = actions::close) { Text(stringResource(R.string.guide_close)) } },
    )
}

/** Start (or Render more), Stop while it renders, Start now while it waits for the charger. */
@Composable
private fun MainButton(
    book: Book,
    job: LongJob?,
    otherBookRunning: Boolean,
    hasReady: Boolean,
    actions: StudioAudioActions,
) {
    val context = LocalContext.current.applicationContext
    val running = job?.running == true
    when {
        running && job?.waitingForCharger == true ->
            TextButton(onClick = { LongJobs.studioStartNow(context) }) {
                Text(stringResource(R.string.long_job_start_now))
            }

        running -> TextButton(onClick = { LongJobs.cancel(context, LongJobKind.STUDIO) }) {
            Text(stringResource(R.string.long_job_stop))
        }

        else -> TextButton(onClick = { actions.start(book) }, enabled = !otherBookRunning) {
            Text(stringResource(if (hasReady) R.string.studio_sheet_more else R.string.studio_sheet_start))
        }
    }
}

/** Listen, Save as a file and Delete (asked once more, since rendering it again takes hours). */
@Composable
private fun ReadyActions(
    book: Book,
    audio: StudioBookAudio,
    saving: Boolean,
    rendering: Boolean,
    actions: StudioAudioActions,
    onDeleted: (Boolean) -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var confirmDelete by rememberSaveable(book.id) { mutableStateOf(false) }
    ActionButton(R.string.studio_sheet_listen) { actions.listen(book) }
    ActionButton(if (saving) R.string.studio_sheet_saving else R.string.studio_sheet_save, enabled = !saving) {
        actions.saveAsFile(book)
    }
    if (!confirmDelete) {
        ActionButton(R.string.studio_sheet_delete, enabled = !rendering) { confirmDelete = true }
        return
    }
    Text(
        stringResource(R.string.studio_sheet_delete_question, context.studioDuration(audio.durationMs)),
        style = MaterialTheme.typography.bodyMedium,
    )
    ActionButton(R.string.studio_sheet_delete_confirm) {
        confirmDelete = false
        scope.launch { onDeleted(withContext(Dispatchers.IO) { StudioMode.deleteBook(context, book.id) }) }
    }
    ActionButton(R.string.confirm_keep) { confirmDelete = false }
}

/** The run's live progress for this book, or a line saying another book is being prepared. */
@Composable
private fun RunSection(job: LongJob?, otherBookRunning: Boolean) {
    when {
        job?.running == true -> {
            SectionHeading(R.string.studio_sheet_running)
            JobProgressBar(job, Modifier.padding(vertical = Dimens.SpaceXs))
            Text(job.detail, style = MaterialTheme.typography.bodyMedium)
            // Start now takes the dialog's main button while it waits for the charger; Stop stays reachable here.
            if (job.waitingForCharger) {
                val context = LocalContext.current
                ActionButton(R.string.long_job_stop) { LongJobs.cancel(context, LongJobKind.STUDIO) }
            }
        }

        job != null -> {
            SectionHeading(R.string.studio_sheet_last_run)
            Text(job.detail, style = MaterialTheme.typography.bodyMedium)
        }

        otherBookRunning ->
            Text(stringResource(R.string.studio_sheet_other_book), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ReadySection(ready: StudioBookAudio?) {
    val context = LocalContext.current
    SectionHeading(R.string.studio_sheet_on_phone)
    val text = ready?.let {
        stringResource(
            R.string.studio_sheet_ready,
            context.studioDuration(it.durationMs),
            it.firstPage + 1,
            it.lastPage + 1,
            Formatter.formatShortFileSize(context, it.bytes),
        )
    } ?: stringResource(R.string.studio_sheet_nothing_ready)
    Text(text, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun SectionHeading(label: Int) {
    Text(
        stringResource(label),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun ActionButton(label: Int, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.heightIn(min = Dimens.TouchTarget)) {
        Text(stringResource(label))
    }
}
