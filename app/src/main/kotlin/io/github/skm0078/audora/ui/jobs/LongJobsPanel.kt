// File: app/src/main/kotlin/io/github/skm0078/audora/ui/jobs/LongJobsPanel.kt
// Purpose: Long jobs (audiobook, copies, studio mode) shown inside the app, not only in the notification shade:
//          progress with Cancel while they run, then how they ended and what to do next.

package io.github.skm0078.audora.ui.jobs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.playback.jobs.LongJob
import io.github.skm0078.audora.playback.jobs.LongJobEnd
import io.github.skm0078.audora.playback.jobs.LongJobKind
import io.github.skm0078.audora.playback.jobs.LongJobs
import io.github.skm0078.audora.ui.theme.Dimens

/** The jobs to show: every one in the library, or only [book]'s in its reader and player. */
internal fun shownJobs(jobs: Map<LongJobKind, LongJob>, book: BookId?): List<LongJob> =
    LongJobKind.entries.mapNotNull { jobs[it] }.filter { book == null || it.bookId == book }

/** One card per job, newest kinds in a fixed order; nothing at all while no job has run. */
@Composable
fun LongJobsPanel(modifier: Modifier = Modifier, book: BookId? = null) {
    val jobs by LongJobs.jobs.collectAsStateWithLifecycle()
    val shown = shownJobs(jobs, book)
    if (shown.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
        shown.forEach { LongJobCard(it) }
    }
}

/** [book]'s running job, if any, for the thin bar on its library card. */
@Composable
fun runningJobFor(book: BookId): LongJob? {
    val jobs by LongJobs.jobs.collectAsStateWithLifecycle()
    return jobs.values.firstOrNull { it.bookId == book && it.running }
}

/** A thin progress bar for a book card: filled as far as the job is, moving while it prepares. */
@Composable
fun JobProgressBar(job: LongJob, modifier: Modifier = Modifier) {
    val percent = job.percent
    if (percent == null) {
        LinearProgressIndicator(modifier.fillMaxWidth())
    } else {
        LinearProgressIndicator(progress = { percent / PERCENT }, modifier = modifier.fillMaxWidth())
    }
}

@Composable
private fun LongJobCard(job: LongJob) {
    val context = LocalContext.current
    val colors = when (job.end) {
        LongJobEnd.FAILED -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        null -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        else -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    }
    Card(colors = colors, modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(Dimens.SpaceL),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
        ) {
            Text(job.title, style = MaterialTheme.typography.titleSmall)
            if (job.running) {
                JobProgressBar(job, Modifier.padding(vertical = Dimens.SpaceXs))
                // Progress is not a live region: TalkBack reading every step would talk over the book.
                Text(job.detail, style = MaterialTheme.typography.bodyMedium)
            } else {
                // How it ended is announced once, wherever the listener is in the app.
                Column(Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
                    Text(job.detail, style = MaterialTheme.typography.bodyMedium)
                    nextStep(job.end)?.let {
                        Text(stringResource(it), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
                if (job.running) {
                    if (job.waitingForCharger) {
                        JobButton(R.string.long_job_start_now) { LongJobs.studioStartNow(context) }
                    }
                    val stop = if (job.kind == LongJobKind.STUDIO) R.string.long_job_stop else R.string.long_job_cancel
                    JobButton(stop) { LongJobs.cancel(context, job.kind) }
                } else {
                    JobButton(R.string.long_job_dismiss) { LongJobs.dismiss(job.kind) }
                }
            }
        }
    }
}

@Composable
private fun JobButton(label: Int, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.heightIn(min = Dimens.TouchTarget)) {
        Text(stringResource(label))
    }
}

/** A line under a result that says what the listener can do now; a finished job's text already says where it is. */
internal fun nextStep(end: LongJobEnd?): Int? = when (end) {
    LongJobEnd.FAILED -> R.string.long_job_failed_next
    LongJobEnd.STOPPED -> R.string.long_job_stopped_next
    LongJobEnd.DONE, null -> null
}

private const val PERCENT = 100f

/** "Saving as audiobook · 34 %": what a book's card says while one of its jobs runs. */
@Composable
fun jobStatus(job: LongJob): String {
    val what = stringResource(
        when (job.kind) {
            LongJobKind.AUDIOBOOK -> R.string.long_job_audiobook
            LongJobKind.COPY -> R.string.long_job_copy
            LongJobKind.STUDIO -> R.string.long_job_studio
        },
    )
    return job.percent?.let { stringResource(R.string.long_job_status_percent, what, it) } ?: what
}

/** [book]'s jobs over the top of the reader's page, where they are seen without covering the controls. */
@Composable
fun BoxScope.BookJobsOnTop(book: BookId) {
    LongJobsPanel(
        Modifier.align(Alignment.TopCenter).padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.SpaceS),
        book = book,
    )
}
