// File: app/src/test/kotlin/io/github/skm0078/audora/ui/library/ShelvesTest.kt
// Purpose: Library shelves (plan item 23): each book counts on the shelf its reading progress puts it on.

package io.github.skm0078.audora.ui.library

import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.BookProgress
import io.github.skm0078.audora.core.library.LibraryFilter
import org.junit.Assert.assertEquals
import org.junit.Test

class ShelvesTest {
    private fun item(id: String, page: Int?) = LibraryItem(
        book = Book(BookId(id), "content://$id", id, 1L, "fp-$id", 10, 0L, null),
        availability = null,
        progress = page?.let { BookProgress(it, pageCount = 10, savedAtMillis = 1L) },
    )

    @Test
    fun `books count on the shelf their progress puts them on`() {
        val books = listOf(item("a", null), item("b", 3), item("c", 9), item("d", 0), item("e", null))
        val expected = mapOf(
            LibraryFilter.ALL to 5,
            LibraryFilter.IN_PROGRESS to 2,
            LibraryFilter.NOT_STARTED to 2,
            LibraryFilter.FINISHED to 1,
        )
        assertEquals(expected, shelfCounts(books))
    }
}
