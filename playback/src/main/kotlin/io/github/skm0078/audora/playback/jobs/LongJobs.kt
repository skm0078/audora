// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/jobs/LongJobs.kt
// Purpose: The app's long jobs (audiobook export, copies, studio mode) as the screens see them: the same progress and
//          result their notifications show, so the library and the reader can show it too, with Cancel.

package io.github.skm0078.audora.playback.jobs

import android.content.Context
import android.content.Intent
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.playback.epub.BookCopies
import io.github.skm0078.audora.playback.epub.BookCopyService
import io.github.skm0078.audora.playback.export.AudiobookExport
import io.github.skm0078.audora.playback.export.AudiobookExportService
import io.github.skm0078.audora.playback.export.JobSlot
import io.github.skm0078.audora.playback.export.JobSlots
import io.github.skm0078.audora.playback.studio.StudioMode
import io.github.skm0078.audora.playback.studio.StudioService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** One kind of long job; at most one of each runs at a time ([JobSlots]). */
enum class LongJobKind {
    AUDIOBOOK,
    COPY,
    STUDIO,
    ;

    internal val slot: JobSlot
        get() = when (this) {
            AUDIOBOOK -> JobSlots.EXPORT
            COPY -> JobSlots.COPY
            STUDIO -> JobSlots.STUDIO
        }
}

/** How a finished job ended, for the screen's wording and icon. */
enum class LongJobEnd { DONE, FAILED, STOPPED }

/**
 * A job as its notification last showed it.
 *
 * @property title the notification's title, which names the book ("Saving “Gitanjali” as an audiobook").
 * @property detail the line under it: the step, chapter or minutes ready, or how it ended and where the files are.
 * @property percent 0-100; null while it prepares and its length is not known yet.
 * @property waitingForCharger studio mode waits for the charger and offers Start now.
 * @property end null while it runs.
 */
data class LongJob(
    val kind: LongJobKind,
    val bookId: BookId,
    val title: String,
    val detail: String,
    val percent: Int? = null,
    val waitingForCharger: Boolean = false,
    val end: LongJobEnd? = null,
) {
    val running: Boolean get() = end == null
}

/**
 * Process-wide, like [JobSlots]: the services run in the app's process, so the screens read the same object. Only a
 * job its service has [begin]-ed is published, so a refused second start (its notification says "busy") never
 * overwrites the job that is really running. A finished job stays until the listener dismisses it or the next job
 * of its kind begins.
 */
object LongJobs {
    private const val TAG = "Playback.LongJobs"
    private val mutableJobs = MutableStateFlow<Map<LongJobKind, LongJob>>(emptyMap())

    val jobs: StateFlow<Map<LongJobKind, LongJob>> = mutableJobs.asStateFlow()

    /** A job of [kind] runs now, even one a stopped service left behind: a new start would be refused as busy. */
    fun busy(kind: LongJobKind): Boolean = kind.slot.busy

    /** The service has taken [kind]'s slot for [bookId]; shown as preparing until its first progress. */
    internal fun begin(kind: LongJobKind, bookId: BookId, title: String, detail: String) {
        mutableJobs.update { it + (kind to LongJob(kind, bookId, title, detail)) }
    }

    /** Progress, as the running notification shows it; ignored unless a job of [kind] has begun and still runs. */
    internal fun progress(
        kind: LongJobKind,
        title: String,
        detail: String,
        percent: Int?,
        waitingForCharger: Boolean = false,
    ) = updateRunning(kind) {
        it.copy(title = title, detail = detail, percent = percent, waitingForCharger = waitingForCharger)
    }

    /** The result notification's text; ignored unless a job of [kind] has begun and still runs. */
    internal fun finish(kind: LongJobKind, end: LongJobEnd, title: String, detail: String) = updateRunning(kind) {
        it.copy(title = title, detail = detail, end = end, percent = null, waitingForCharger = false)
    }

    private fun updateRunning(kind: LongJobKind, change: (LongJob) -> LongJob) {
        mutableJobs.update { jobs ->
            val job = jobs[kind]?.takeIf { it.running } ?: return@update jobs
            jobs + (kind to change(job))
        }
    }

    /** The listener has read a finished job's result. */
    fun dismiss(kind: LongJobKind) {
        mutableJobs.update { jobs -> if (jobs[kind]?.running == false) jobs - kind else jobs }
    }

    /** Cancel (export, copy) or Stop (studio), as the notification's button does; what was done is kept. */
    fun cancel(context: Context, kind: LongJobKind) {
        AudoraLog.i(TAG, "Cancel requested from the app, job=$kind")
        val intent = when (kind) {
            LongJobKind.AUDIOBOOK ->
                Intent(context, AudiobookExportService::class.java).setAction(AudiobookExport.ACTION_CANCEL)

            LongJobKind.COPY -> Intent(context, BookCopyService::class.java).setAction(BookCopies.ACTION_CANCEL)

            LongJobKind.STUDIO -> Intent(context, StudioService::class.java).setAction(StudioMode.ACTION_STOP)
        }
        context.startService(intent)
    }

    /** Studio mode's Start now: renders on battery instead of waiting for the charger. */
    fun studioStartNow(context: Context) {
        AudoraLog.i(TAG, "Studio Start now requested from the app")
        context.startService(Intent(context, StudioService::class.java).setAction(StudioMode.ACTION_ON_BATTERY))
    }
}
