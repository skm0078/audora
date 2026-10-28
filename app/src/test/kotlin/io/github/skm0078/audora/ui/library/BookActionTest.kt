// File: app/src/test/kotlin/io/github/skm0078/audora/ui/library/BookActionTest.kt
// Purpose: The book menu offers Mark as finished and Mark as not started only when they would change something.

package io.github.skm0078.audora.ui.library

import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.BookProgress
import org.junit.Assert.assertEquals
import org.junit.Test

class BookActionTest {
    private fun item(pageCount: Int?, page: Int?) = LibraryItem(
        book = Book(BookId("a"), "content://a", "A", 1L, "fp", pageCount, 0L, null),
        availability = null,
        progress = page?.let { BookProgress(it, requireNotNull(pageCount), savedAtMillis = 1L) },
    )

    private fun offered(item: LibraryItem) =
        listOf(BookAction.MARK_FINISHED, BookAction.MARK_NOT_STARTED).filter { it.appliesTo(item) }

    @Test
    fun `a book never read can be marked finished, not "not started"`() {
        assertEquals(listOf(BookAction.MARK_FINISHED), offered(item(pageCount = 10, page = null)))
    }

    @Test
    fun `a book being read offers both`() {
        assertEquals(listOf(BookAction.MARK_FINISHED, BookAction.MARK_NOT_STARTED), offered(item(10, page = 4)))
    }

    @Test
    fun `a finished book offers only "not started"`() {
        assertEquals(listOf(BookAction.MARK_NOT_STARTED), offered(item(10, page = 9)))
    }

    @Test
    fun `a book never opened (no page count) cannot be marked finished`() {
        assertEquals(emptyList<BookAction>(), offered(item(pageCount = null, page = null)))
    }

    @Test
    fun `the other actions always apply`() {
        assertEquals(true, BookAction.STUDIO.appliesTo(item(pageCount = null, page = null)))
        assertEquals(true, BookAction.SHARE_CARD.appliesTo(item(pageCount = null, page = null)))
    }
}
