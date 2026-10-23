// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/epub/BookPages.kt
// Purpose: Reads every page's sentences for a copy, with playback's failure rules, and why a copy can fail.

package io.github.skm0078.audora.playback.epub

import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.pdf.document.PdfPageError

/** Why there is no copy. */
sealed interface CopyError {
    /** No page has any text, even after OCR. */
    data object NothingToCopy : CopyError

    /** The PDF could no longer be read (moved, deleted, the reopen failed). */
    data class BookUnreadable(val page: Int) : CopyError

    /** Shared storage refused or failed the file. */
    data class Storage(val detail: String) : CopyError

    /** The talking book's voice failed too many sentences in a row. */
    data class VoiceFailed(val detail: String) : CopyError

    /** The book could not be opened; [reason] is safe to show. */
    data class NotOpened(val reason: String) : CopyError
}

/** Each readable page's units by page index, and how many pages could not be extracted. */
data class LoadedPages(val units: Map<Int, List<ReadingUnit>>, val skippedPages: Int)

/**
 * Blocking, on the caller's extraction thread: this is the slow step of an accessible copy, since a scanned page goes
 * through OCR (about 3–6 s a page on the Moto). A page that cannot be extracted is skipped, a book that can no
 * longer be opened stops it, as in playback. Cancel by interrupting the thread.
 */
class BookPages(private val onPage: (done: Int, total: Int) -> Unit = { _, _ -> }) {
    fun read(
        pageCount: Int,
        units: (Int) -> Outcome<List<ReadingUnit>, PdfPageError>,
        logContext: String,
    ): Outcome<LoadedPages, CopyError> {
        val loaded = LinkedHashMap<Int, List<ReadingUnit>>()
        var skipped = 0
        for (page in 0 until pageCount) {
            if (Thread.interrupted()) throw InterruptedException("Copy cancelled")
            when (val result = units(page)) {
                is Outcome.Success -> loaded[page] = result.value

                is Outcome.Failure -> if (result.error.isPageOnly()) {
                    AudoraLog.w(TAG, "Page skipped, $logContext page=$page: ${result.error}")
                    skipped++
                } else {
                    return Outcome.Failure(CopyError.BookUnreadable(page))
                }
            }
            onPage(page + 1, pageCount)
        }
        return Outcome.Success(LoadedPages(loaded, skipped))
    }

    private companion object {
        const val TAG = "Copy.Pages"

        fun PdfPageError.isPageOnly(): Boolean =
            this is PdfPageError.ExtractionFailed || this is PdfPageError.PageOutOfRange
    }
}
