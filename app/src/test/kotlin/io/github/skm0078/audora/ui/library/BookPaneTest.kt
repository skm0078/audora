// File: app/src/test/kotlin/io/github/skm0078/audora/ui/library/BookPaneTest.kt
// Purpose: The wide screen's book pane shows the picked book, else the one being read, else the first, and only
//          where the books keep four covers a row beside it (ADR 2026-10-28-tablet-panes).

package io.github.skm0078.audora.ui.library

import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookAvailability
import io.github.skm0078.audora.core.library.BookId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookPaneTest {
    private fun item(id: String) = LibraryItem(
        book = Book(BookId(id), "content://$id", id.uppercase(), 1L, "fp-$id", 10, 0L, null),
        availability = BookAvailability.AVAILABLE,
        progress = null,
    )

    private val a = item("a")
    private val b = item("b")
    private val c = item("c")

    @Test
    fun `the picked book wins`() {
        val list = LibraryListState.Loaded(listOf(a, b, c), continueReading = b)
        assertEquals(c, paneBook(list, pickedId = "c"))
    }

    @Test
    fun `with nothing picked the pane shows the book being read`() {
        val list = LibraryListState.Loaded(listOf(a, b, c), continueReading = b)
        assertEquals(b, paneBook(list, pickedId = null))
    }

    @Test
    fun `a picked book filtered out falls back, so the pane never shows a book the grid hides`() {
        val list = LibraryListState.Loaded(listOf(a, b), continueReading = c)
        assertEquals(a, paneBook(list, pickedId = "c"))
    }

    @Test
    fun `an empty library shows no pane`() {
        assertNull(paneBook(LibraryListState.Loaded(emptyList()), pickedId = "a"))
    }

    @Test
    fun `the pane needs 880 dp of library and never shows in kids mode`() {
        assertTrue(showsBookPane(BOOK_PANE_MIN_LIBRARY, kids = false))
        assertTrue(showsBookPane(1000.dp, kids = false))
        assertFalse(showsBookPane(BOOK_PANE_MIN_LIBRARY - 1.dp, kids = false))
        assertFalse(showsBookPane(1000.dp, kids = true))
    }

    @Test
    fun `a 1280 dp tablet beside its sidebar shows the pane and a phone in landscape does not`() {
        val sidebar = 280.dp
        assertTrue(showsBookPane(1280.dp - sidebar, kids = false))
        assertFalse(showsBookPane(915.dp - 80.dp, kids = false))
    }
}
