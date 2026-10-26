// File: app/src/test/kotlin/io/github/skm0078/audora/ui/stats/FinishedCountTest.kt
// Purpose: "Books finished" on the stats sheet counts what the library's Finished filter shows (CF-8).

package io.github.skm0078.audora.ui.stats

import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.resume.ResumeAnchor
import org.junit.Assert.assertEquals
import org.junit.Test

class FinishedCountTest {
    @Test
    fun `only books whose place is on their last page count`() {
        val books = listOf(
            book("done", pages = 10),
            book("halfway", pages = 10),
            book("never-read", pages = 10),
            book("never-opened", pages = null),
        )
        val anchors = listOf(anchor("done", page = 9), anchor("halfway", page = 4), anchor("never-opened", page = 3))

        assertEquals(1, finishedCount(books, anchors))
    }

    @Test
    fun `a place for a book no longer in the library is not counted`() {
        assertEquals(0, finishedCount(emptyList(), listOf(anchor("gone", page = 9))))
    }

    private fun book(id: String, pages: Int?) =
        Book(BookId(id), "content://test/$id", id, 1_000, "f-$id", pages, 1, null)

    private fun anchor(id: String, page: Int) = ResumeAnchor(BookId(id), page, 0, 1L, 1L, 1, 1L)
}
