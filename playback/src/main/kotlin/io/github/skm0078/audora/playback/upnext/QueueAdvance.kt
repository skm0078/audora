// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/upnext/QueueAdvance.kt
// Purpose: When a book ends, starts the first book in Up next (plan item 25) at its chapter or where it was left.

package io.github.skm0078.audora.playback.upnext

import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.upnext.QueuedBook
import io.github.skm0078.audora.playback.book.OpenFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The listener started reading and queued the books, so the next one plays on without another tap (like an
 * audiobook's next chapter). An entry leaves the queue when its turn comes; one that cannot be opened (moved,
 * password protected, or a start that throws on a damaged file) is logged and the next is tried, so one bad entry
 * never ends the service.
 *
 * @param start plays a queued book: from its chapter's page, or from its resume position when it has none.
 */
class QueueAdvance(
    private val store: UpNextStore,
    private val scope: CoroutineScope,
    private val start: suspend (QueuedBook) -> Outcome<Unit, OpenFailure>,
) {
    fun onBookFinished(finished: BookId) {
        if (!store.queue.value.thenPlayQueue) return
        scope.launch {
            var started = false
            while (!started) {
                val next = store.queue.value.entries.firstOrNull() ?: break
                store.update { it.remove(next.entryId) }
                started = tryStart(next, finished)
            }
        }
    }

    private suspend fun tryStart(next: QueuedBook, finished: BookId): Boolean = when (val started = startCaught(next)) {
        null -> false

        is Outcome.Success -> {
            AudoraLog.i(TAG, "Up next after book=${finished.logLabel}: ${next.logLabel}")
            true
        }

        is Outcome.Failure -> {
            AudoraLog.w(TAG, "Up next could not start ${next.logLabel}: ${started.error.code}, trying the next")
            false
        }
    }

    /** [start]'s answer, or null (logged) when it threw: one more entry that could not be opened. */
    @Suppress("TooGenericExceptionCaught") // Any failure of one entry must not end the service.
    private suspend fun startCaught(next: QueuedBook): Outcome<Unit, OpenFailure>? = try {
        start(next)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AudoraLog.e(TAG, "Up next start threw for ${next.logLabel}, trying the next", e)
        null
    }

    private companion object {
        const val TAG = "Playback.UpNext"
    }
}
