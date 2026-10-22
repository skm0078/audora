// File: app/src/test/kotlin/io/github/skm0078/audora/widget/ResumeWidgetBookTest.kt
// Purpose: Which book the home-screen widget offers (plan item 35), and when it keeps what it shows.

package io.github.skm0078.audora.widget

import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookAvailability
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.BookProgress
import io.github.skm0078.audora.core.library.BookTitles
import io.github.skm0078.audora.core.library.LibraryQuery
import io.github.skm0078.audora.ui.library.LibraryItem
import io.github.skm0078.audora.ui.library.LibraryListState
import org.junit.Assert.assertEquals
import org.junit.Test

class ResumeWidgetBookTest {
    private fun item(id: String, title: String, savedAt: Long?, availability: BookAvailability? = null) = LibraryItem(
        book = Book(BookId(id), "content://$id", title, 1L, "fp-$id", 10, 0L, null),
        availability = availability,
        progress = savedAt?.let { BookProgress(page = 4, pageCount = 10, savedAtMillis = it) },
    )

    private fun loaded(vararg items: LibraryItem) =
        LibraryListState.Loaded(items = items.toList(), all = items.toList(), query = LibraryQuery())

    @Test
    fun `the most recently read book whose file is still there`() {
        val update = resumeWidgetBook(
            loaded(
                item("a", "Old", savedAt = 1L),
                item("b", "Gone", savedAt = 3L, availability = BookAvailability.MISSING),
                item("c", "Latest", savedAt = 2L),
                item("d", "Never read", savedAt = null),
            ),
        )
        assertEquals(ResumeWidgetUpdate.Show(ResumeWidgetBook("c", "Latest", page = 4, pageCount = 10)), update)
    }

    @Test
    fun `nothing read shows the invitation, a loading library keeps the widget as it is`() {
        assertEquals(ResumeWidgetUpdate.Show(null), resumeWidgetBook(loaded(item("d", "Never read", savedAt = null))))
        assertEquals(ResumeWidgetUpdate.Unchanged, resumeWidgetBook(LibraryListState.Loading))
    }

    @Test
    fun `an untitled book has no title, so the widget names it the usual way`() {
        val update = resumeWidgetBook(loaded(item("e", BookTitles.UNTITLED, savedAt = 1L)))
        assertEquals(null, (update as ResumeWidgetUpdate.Show).book?.title)
    }
}
