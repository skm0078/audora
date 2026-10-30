// File: app/src/main/kotlin/io/github/skm0078/audora/ui/jobs/UnfinishedFiles.kt
// Purpose: What a stopped or failed job left for its card to name, and the card's Delete unfinished files (ADR
//          2026-10-29-jobs-card-and-leftovers).

package io.github.skm0078.audora.ui.jobs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import io.github.skm0078.audora.di.LibraryEntryPoint
import io.github.skm0078.audora.playback.jobs.JobLeftovers
import io.github.skm0078.audora.playback.jobs.LongJob
import io.github.skm0078.audora.playback.jobs.LongJobEnd
import io.github.skm0078.audora.playback.jobs.LongJobKind
import kotlinx.coroutines.launch

/**
 * A stopped or failed job's leftovers: [bytes] still kept, or [deletedBytes] once the listener deleted them, and
 * [onDelete] to do so.
 */
@Immutable
data class UnfinishedFiles(val bytes: Long, val deletedBytes: Long? = null, val onDelete: () -> Unit = {})

/** Only an audiobook or a talking book that stopped part way leaves files to continue from; studio keeps audio. */
internal fun leavesUnfinishedFiles(job: LongJob): Boolean =
    job.kind != LongJobKind.STUDIO && (job.end == LongJobEnd.FAILED || job.end == LongJobEnd.STOPPED)

/** [job]'s leftovers, measured off the main thread once it ended; null while there are none to name. */
@Composable
internal fun rememberUnfinishedFiles(job: LongJob): UnfinishedFiles? {
    val context = LocalContext.current.applicationContext
    val entry = remember(context) { LibraryEntryPoint.of(context) }
    val scope = rememberCoroutineScope()
    var deleted by remember(job.kind, job.bookId, job.end) { mutableStateOf<Long?>(null) }
    val bytes by produceState(0L, job.kind, job.bookId, job.end) {
        if (leavesUnfinishedFiles(job)) {
            value = JobLeftovers.get(context).bytesFor(job.kind, job.bookId, entry.outputs())
        }
    }
    val gone = deleted
    return when {
        gone != null -> UnfinishedFiles(bytes = 0L, deletedBytes = gone)

        bytes <= 0L -> null

        else -> UnfinishedFiles(bytes) {
            scope.launch {
                val freed = JobLeftovers.get(context).discard(job.kind, job.bookId, entry.library(), entry.outputs())
                deleted = freed.bytes
            }
        }
    }
}
