// File: app/src/test/kotlin/io/github/skm0078/audora/ui/whatsnew/WhatsNewStartTest.kt
// Purpose: "What's new" shows once to someone updating with books, never to a new listener.

package io.github.skm0078.audora.ui.whatsnew

import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.ui.library.LibraryItem
import io.github.skm0078.audora.ui.library.LibraryListState
import org.junit.Assert.assertEquals
import org.junit.Test

class WhatsNewStartTest {
    private val withBooks = LibraryListState.Loaded(
        items = listOf(LibraryItem(Book(BookId("a"), "content://a", "A", 1L, "fp", 10, 0L, null), null)),
    )
    private val empty = LibraryListState.Loaded(items = emptyList())

    @Test
    fun `someone with books who has not seen this list sees it`() {
        assertEquals(WhatsNewStart.SHOW, whatsNewStart(seen = 0, current = 1, library = withBooks))
    }

    @Test
    fun `a list already seen is not shown again`() {
        assertEquals(WhatsNewStart.NOTHING, whatsNewStart(seen = 1, current = 1, library = withBooks))
    }

    @Test
    fun `a new listener with no books is marked as having seen it`() {
        assertEquals(WhatsNewStart.MARK_SEEN, whatsNewStart(seen = 0, current = 1, library = empty))
    }

    @Test
    fun `nothing is decided from a library that failed to load`() {
        assertEquals(WhatsNewStart.NOTHING, whatsNewStart(seen = 0, current = 1, library = LibraryListState.Failed))
    }
}
