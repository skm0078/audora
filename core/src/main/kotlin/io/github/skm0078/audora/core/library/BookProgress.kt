// File: core/src/main/kotlin/io/github/skm0078/audora/core/library/BookProgress.kt
// Purpose: How far into a book the listener is, from its resume anchor: what the library shows on each book.

package io.github.skm0078.audora.core.library

import io.github.skm0078.audora.core.resume.ResumeAnchor

/**
 * Progress is counted in pages: the anchor knows the page and an offset into its text, not how much text the book
 * has, and counting it would mean extracting every page.
 *
 * @property page zero-based page the listener was on.
 * @property pageCount the book's pages.
 * @property savedAtMillis when that position was saved: the most recent one is "Continue reading".
 */
data class BookProgress(val page: Int, val pageCount: Int, val savedAtMillis: Long) {
    init {
        require(pageCount > 0 && page in 0 until pageCount) { "page $page must be within $pageCount pages" }
    }

    /** 0..1: the share of pages started, so the last page shows as 100 %. */
    val fraction: Float get() = (page + 1).toFloat() / pageCount

    /** On the last page: the library's Finished. */
    val isFinished: Boolean get() = page == pageCount - 1

    companion object {
        /** Null when the book was never read or its page count is not known yet (never opened). */
        fun of(book: Book, anchor: ResumeAnchor?): BookProgress? {
            val pageCount = book.pageCount?.takeIf { it > 0 }
            return if (anchor == null || pageCount == null) {
                null
            } else {
                BookProgress(anchor.page.coerceIn(0, pageCount - 1), pageCount, anchor.savedAtMillis)
            }
        }
    }
}
