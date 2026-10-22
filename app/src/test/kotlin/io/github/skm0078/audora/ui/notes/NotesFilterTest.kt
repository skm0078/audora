// File: app/src/test/kotlin/io/github/skm0078/audora/ui/notes/NotesFilterTest.kt
// Purpose: The Notes tab's book filter (UI plan, wave 4): one book's notes, or all when that book has none left.

package io.github.skm0078.audora.ui.notes

import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookId
import org.junit.Assert.assertEquals
import org.junit.Test

class NotesFilterTest {
    private fun group(id: String) =
        BookNotes(Book(BookId(id), "content://$id", id, 1L, "fp-$id", 10, 0L, null), emptyList())

    private val groups = listOf(group("a"), group("b"))

    @Test
    fun `filters to one book and falls back to all`() {
        assertEquals(groups, notesOfBook(groups, null))
        assertEquals(listOf(groups[1]), notesOfBook(groups, "b"))
        assertEquals(groups, notesOfBook(groups, "gone"))
    }
}
