// File: app/src/test/kotlin/io/github/skm0078/audora/ui/library/BookMenuChoicesTest.kt
// Purpose: The book menu keeps every action a home, never shows more than the sheet's eight lines, and offers only
//          Details and Remove for a book whose file is gone (ADR 2026-10-28-book-menu-sort-sheet).

package io.github.skm0078.audora.ui.library

import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookAvailability
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.BookProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookMenuChoicesTest {
    private fun item(
        page: Int? = null,
        pageCount: Int? = 10,
        availability: BookAvailability = BookAvailability.AVAILABLE,
    ) = LibraryItem(
        book = Book(BookId("a"), "content://a", "A", 1L, "fp", pageCount, 0L, null),
        availability = availability,
        progress = page?.let { BookProgress(it, requireNotNull(pageCount), savedAtMillis = 1L) },
    )

    @Test
    fun `a book being read gets Play, Up next, Share and an unpressed Finished`() {
        assertEquals(
            listOf(QuickChoice.Play, QuickChoice.UpNext, SHARE, QuickChoice.Finished(finished = false)),
            BookMenuChoices.of(item(page = 4)).quick,
        )
    }

    @Test
    fun `a finished book's Finished button is pressed and marks it not started`() {
        val finished = BookMenuChoices.of(item(page = 9)).quick.filterIsInstance<QuickChoice.Finished>().single()
        assertTrue(finished.finished)
        assertEquals(BookAction.MARK_NOT_STARTED, finished.action)
    }

    @Test
    fun `a book never opened has no Finished button, since its length is unknown`() {
        assertEquals(listOf(QuickChoice.Play, QuickChoice.UpNext, SHARE), BookMenuChoices.of(item(pageCount = null)).quick)
    }

    @Test
    fun `Save as holds the audiobook and the three exports, in the menu's order`() {
        assertEquals(
            listOf(
                SaveChoice.Audiobook,
                SaveChoice.Action(BookAction.ACCESSIBLE_COPY),
                SaveChoice.Action(BookAction.TALKING_BOOK),
                SaveChoice.Action(BookAction.EXPORT_NOTES),
            ),
            BookMenuChoices.of(item()).saveAs,
        )
    }

    @Test
    fun `a book whose file is gone offers only Details and Remove`() {
        listOf(BookAvailability.MISSING, BookAvailability.ACCESS_LOST).forEach { gone ->
            val choices = BookMenuChoices.of(item(page = 4, availability = gone))
            assertEquals(gone.name, BookMenuChoices(emptyList(), emptyList(), emptyList()), choices)
        }
    }

    @Test
    fun `every action has a home in the menu or the details sheet`() {
        val choices = BookMenuChoices.of(item(page = 4))
        val inMenu = choices.rows + choices.saveAs.filterIsInstance<SaveChoice.Action>().map { it.action } +
            choices.quick.filterIsInstance<QuickChoice.Action>().map { it.action }
        BookAction.entries.forEach { action ->
            val housed = action in inMenu || action.place == MenuPlace.DETAILS || action.place == MenuPlace.FINISHED
            assertTrue("$action has no place", housed)
        }
    }

    @Test
    fun `the sheet stays within eight lines under the big buttons`() {
        val choices = BookMenuChoices.of(item(page = 4))
        // Details, Save as and Remove, plus a row per action placed there.
        val lines = 3 + choices.rows.size
        assertTrue("$lines lines", lines <= MAX_LINES)
    }

    private companion object {
        const val MAX_LINES = 8
        val SHARE = QuickChoice.Action(BookAction.SHARE_CARD)
    }
}
