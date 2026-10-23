// File: app/src/test/kotlin/io/github/skm0078/audora/ui/notes/NotesFilterTest.kt
// Purpose: The Notes tab: the book filter (UI plan, wave 4), and every kind of note grouped by book in reading order.

package io.github.skm0078.audora.ui.notes

import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.resume.ResumeAnchor
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

    @Test
    fun `merges every kind in reading order and leaves out books without notes`() {
        val a = groups[0].book
        val c = group("c").book
        fun at(book: Book, page: Int, char: Int) = ResumeAnchor(book.id, page, char, 1L, 2L, 1, 0L)
        val mark = NoteEntry.Bookmark(1, at(a, 2, 0), "")
        val quote = NoteEntry.Highlight(1, at(a, 0, 40), "quote", "")
        val voice = NoteEntry.VoiceNote(1, at(a, 0, 10), 3_000)
        val result = groupNotes(listOf(a, c), mapOf(a.id to listOf(mark)), listOf(quote, voice))
        assertEquals(listOf(BookNotes(a, listOf(voice, quote, mark))), result)
    }
}
