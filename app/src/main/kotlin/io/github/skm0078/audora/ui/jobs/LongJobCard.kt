// File: app/src/main/kotlin/io/github/skm0078/audora/ui/jobs/LongJobCard.kt
// Purpose: A long job (audiobook, copy, studio mode) in the Jobs card: icon, status pill, progress, what an ended
//          job kept, and its buttons (ADR 2026-10-29-jobs-card-and-leftovers).

package io.github.skm0078.audora.ui.jobs

import android.text.format.Formatter
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.config.LeftoverConfig
import io.github.skm0078.audora.playback.jobs.LongJob
import io.github.skm0078.audora.playback.jobs.LongJobEnd
import io.github.skm0078.audora.playback.jobs.LongJobKind
import io.github.skm0078.audora.playback.jobs.LongJobs

/**
 * A long job in the [JobCard]: its kind's icon, the notification's title and line, a pill for how it is going and
 * its buttons. [unfinished] is what a stopped or failed audiobook or talking book left (kept for a while for
 * Continue), offered for deletion with how large it is.
 */
@Composable
internal fun LongJobCard(job: LongJob, onDetails: (() -> Unit)? = null, unfinished: UnfinishedFiles? = null) {
    val context = LocalContext.current
    JobCard(
        icon = iconOf(job.kind),
        tone = toneOf(job),
        title = job.title,
        status = statusOf(job),
        detail = job.detail,
        bar = if (job.running) job.percent?.let { JobBar.Filled(it / PERCENT_FULL) } ?: JobBar.Moving else JobBar.None,
        notes = notesOf(job, unfinished),
        announce = !job.running,
    ) {
        onDetails?.let { JobTextButton(stringResource(R.string.long_job_details), it) }
        if (job.running) {
            if (job.waitingForCharger) {
                JobTextButton(stringResource(R.string.long_job_start_now), { LongJobs.studioStartNow(context) })
            }
            val stop = if (job.kind == LongJobKind.STUDIO) R.string.long_job_stop else R.string.long_job_cancel
            JobOutlinedButton(stringResource(stop), { LongJobs.cancel(context, job.kind) })
        } else {
            unfinished?.takeIf { it.bytes > 0L }?.let { files ->
                val size = Formatter.formatShortFileSize(context, files.bytes)
                JobConfirmedButton(
                    label = stringResource(R.string.jobs_unfinished_delete),
                    question = stringResource(R.string.jobs_unfinished_delete_question, size),
                    onConfirm = files.onDelete,
                )
            }
            JobTextButton(stringResource(R.string.long_job_dismiss), { LongJobs.dismiss(job.kind) })
        }
    }
}

private fun iconOf(kind: LongJobKind): Int = when (kind) {
    LongJobKind.AUDIOBOOK -> R.drawable.ic_headphones
    LongJobKind.COPY -> R.drawable.ic_talking_book
    LongJobKind.STUDIO -> R.drawable.ic_studio
}

internal fun toneOf(job: LongJob): JobTone = when (job.end) {
    null -> if (job.waitingForCharger) JobTone.WAITING else JobTone.RUNNING
    LongJobEnd.DONE -> JobTone.DONE
    LongJobEnd.FAILED -> JobTone.FAILED
    LongJobEnd.STOPPED -> JobTone.STOPPED
}

@Composable
private fun statusOf(job: LongJob): String = when (job.end) {
    null -> when {
        job.waitingForCharger -> stringResource(R.string.jobs_status_waiting)
        else -> job.percent?.let { stringResource(R.string.jobs_status_percent, it) }
            ?: stringResource(R.string.jobs_status_starting)
    }

    LongJobEnd.DONE -> stringResource(R.string.jobs_status_done)

    LongJobEnd.FAILED -> stringResource(R.string.jobs_status_failed)

    LongJobEnd.STOPPED -> stringResource(R.string.jobs_status_stopped)
}

/**
 * What the listener can do now. Kept leftovers replace the plain "what was done is kept" with how much and for how
 * long; a failed job keeps its hint about logs too.
 */
@Composable
private fun notesOf(job: LongJob, unfinished: UnfinishedFiles?): List<String> {
    val context = LocalContext.current
    val kept = unfinished?.bytes?.takeIf { it > 0L }?.let { bytes ->
        val days = LeftoverConfig().keepDays
        pluralStringResource(
            R.plurals.jobs_unfinished_kept,
            days,
            Formatter.formatShortFileSize(context, bytes),
            days,
        )
    }
    val deleted = unfinished?.deletedBytes?.let {
        stringResource(R.string.jobs_unfinished_deleted, Formatter.formatShortFileSize(context, it))
    }
    val next = nextStep(job.end)?.takeUnless { job.end == LongJobEnd.STOPPED && (kept != null || deleted != null) }
    return listOfNotNull(next?.let { stringResource(it) }, kept, deleted)
}

private const val PERCENT_FULL = 100f
