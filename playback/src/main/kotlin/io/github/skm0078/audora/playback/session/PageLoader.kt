// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/session/PageLoader.kt
// Purpose: Extracts pages off the main thread, two pages ahead of the playhead, and appends them to the playlist.

package io.github.skm0078.audora.playback.session

import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.pdf.document.PdfPageError
import io.github.skm0078.audora.playback.book.BookText
import io.github.skm0078.audora.playback.book.PageCursor
import io.github.skm0078.audora.playback.playlist.PlaylistFeeder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Main-thread object; only the PDF reads run on [extraction] (one thread: PDFium documents must not be used
 * from two threads, P1-02). Pages load while the feeder wants more (Build Spec §6: lazily, about two pages
 * ahead). [cancel] on every rebuild: a cancelled load never appends, because `withContext` re-checks
 * cancellation before returning. Every loaded page goes whole to [onPage] first, before the resume point trims it
 * (the quote carry, plan item 7, reads the sentences before the one playing).
 */
class PageLoader(
    private val scope: CoroutineScope,
    private val extraction: CoroutineDispatcher,
    private val listener: Listener,
    private val onPage: (List<ReadingUnit>) -> Unit = {},
) {
    interface Listener {
        /** A page's sentences were appended, or the playlist was finished; the render window may have changed. */
        fun onPageAppended()

        /** The PDF can no longer be read; the feeder has been finished, so the book ends after what is queued. */
        fun onBookUnreadable(page: Int, error: PdfPageError)
    }

    private var job: Job? = null

    fun cancel() {
        job?.cancel()
        job = null
    }

    fun pump(text: BookText, cursor: PageCursor, feeder: PlaylistFeeder, logContext: String) {
        if (job?.isActive == true || !feeder.wantsMore()) return
        job = scope.launch {
            var keepGoing = true
            while (keepGoing && feeder.wantsMore()) {
                val page = cursor.nextPage()
                keepGoing = if (page == null) {
                    AudoraLog.i(TAG, "All pages queued, $logContext")
                    feeder.finish()
                    // The playlist lost its "more to come" item: the end-of-item pause for the book's last
                    // sentence (a study range ending there) must be re-armed against what now follows it.
                    listener.onPageAppended()
                    false
                } else {
                    load(text, cursor, feeder, page, logContext)
                }
            }
        }
    }

    /** False when loading must stop. */
    private suspend fun load(
        text: BookText,
        cursor: PageCursor,
        feeder: PlaylistFeeder,
        page: Int,
        logContext: String,
    ): Boolean = when (val loaded = withContext(extraction) { text.units(page) }) {
        is Outcome.Success -> {
            onPage(loaded.value)
            val unreadable = feeder.append(cursor.playable(page, loaded.value))
            if (unreadable > 0) AudoraLog.i(TAG, "Unreadable units skipped, $logContext page=$page count=$unreadable")
            listener.onPageAppended()
            true
        }

        is Outcome.Failure -> when (val error = loaded.error) {
            is PdfPageError.ExtractionFailed, is PdfPageError.PageOutOfRange -> {
                AudoraLog.w(TAG, "Page skipped, $logContext page=$page: $error")
                true
            }

            is PdfPageError.ReopenFailed, PdfPageError.BookClosed -> {
                AudoraLog.e(TAG, "Book can no longer be read, $logContext page=$page: $error")
                feeder.finish()
                listener.onBookUnreadable(page, error)
                false
            }
        }
    }

    private companion object {
        const val TAG = "Playback.Pages"
    }
}
