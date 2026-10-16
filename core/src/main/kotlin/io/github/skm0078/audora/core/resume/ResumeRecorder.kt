// File: core/src/main/kotlin/io/github/skm0078/audora/core/resume/ResumeRecorder.kt
// Purpose: Saves the sentence being read as the book's anchor: at most one write per interval, at once on pause.

package io.github.skm0078.audora.core.resume

import io.github.skm0078.audora.core.config.ResumeConfig
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.result.Outcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The playback service calls [onUnitStarted] each time a sentence starts and [flush] on pause, stop, sleep timer
 * and end of book (Build Spec §6 "State").
 *
 * The first sentence is written at once; after that the newest sentence is written when
 * [ResumeConfig.minWriteIntervalMs] has passed since the last write, so skipping through ten sentences costs one
 * write and a kill loses at most that interval. The anchor is the sentence's start: a kill mid-sentence resumes
 * at the start of that sentence.
 *
 * @param clock wall-clock milliseconds; also the time base for the interval.
 * @param scope where the delayed write runs; cancelling it drops an unwritten position, so the service flushes
 *   before cancelling.
 */
// One constructor parameter per seam the tests replace.
@Suppress("LongParameterList")
class ResumeRecorder(
    private val store: ResumeAnchorStore,
    private val scope: CoroutineScope,
    private val pipelineVersion: Int,
    private val clock: () -> Long,
    private val config: ResumeConfig = ResumeConfig(),
    private val fingerprint: SentenceFingerprint = SentenceFingerprint(config),
) {
    private val writeLock = Mutex()
    private val stateLock = Any()
    private var pending: ResumeAnchor? = null
    private var lastWritten: ResumeAnchor? = null
    private var lastAttemptAtMillis: Long? = null

    /**
     * True from scheduling a write until that write takes [pending]. Not `Job.isActive`: a job still finishing
     * its database write is active, and a sentence started meanwhile would never be scheduled.
     */
    private var writeScheduled = false
    private var scheduled: Job? = null

    fun onUnitStarted(bookId: BookId, unit: ReadingUnit) {
        val anchor = ResumeAnchor(
            bookId = bookId,
            page = unit.page,
            charStart = unit.charStart,
            fingerprint = fingerprint.head(unit.text),
            tailFingerprint = fingerprint.tail(unit.text),
            pipelineVersion = pipelineVersion,
            savedAtMillis = clock(),
        )
        synchronized(stateLock) {
            // A rebuffer or a seek to the same sentence restarts it: nothing new to save.
            if (pending == null && lastWritten?.samePlace(anchor) == true) return
            pending = anchor
            if (writeScheduled) return
            writeScheduled = true
            val wait = lastAttemptAtMillis?.let { config.minWriteIntervalMs - (clock() - it) }?.coerceAtLeast(0) ?: 0
            scheduled = scope.launch {
                delay(wait)
                writePending()
            }
        }
    }

    /** Writes the newest position now. Call on pause, stop, sleep timer and before the service goes away. */
    suspend fun flush() {
        synchronized(stateLock) {
            scheduled?.cancel()
            writeScheduled = false
        }
        writePending()
    }

    private suspend fun writePending() = withContext(NonCancellable) {
        writeLock.withLock {
            val anchor = synchronized(stateLock) {
                writeScheduled = false
                pending?.also {
                    pending = null
                    lastAttemptAtMillis = clock()
                }
            } ?: return@withLock
            when (val written = store.put(anchor)) {
                is Outcome.Success -> synchronized(stateLock) { lastWritten = anchor }

                is Outcome.Failure -> {
                    AudoraLog.w(TAG, "Could not save position ${anchor.logLabel}: ${written.error}")
                    // A failed write saved nothing, so the next sentence retries at once rather than waiting.
                    synchronized(stateLock) { lastAttemptAtMillis = null }
                }
            }
        }
    }

    private fun ResumeAnchor.samePlace(other: ResumeAnchor) =
        bookId == other.bookId && page == other.page && charStart == other.charStart

    private companion object {
        const val TAG = "Audora.Resume"
    }
}
