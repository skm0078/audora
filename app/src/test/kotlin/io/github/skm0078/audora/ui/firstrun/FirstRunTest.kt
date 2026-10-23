// File: app/src/test/kotlin/io/github/skm0078/audora/ui/firstrun/FirstRunTest.kt
// Purpose: The first-run screens (UI plan, wave 4) show once, only to someone with an empty library.

package io.github.skm0078.audora.ui.firstrun

import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.ui.library.LibraryItem
import io.github.skm0078.audora.ui.library.LibraryListState
import org.junit.Assert.assertEquals
import org.junit.Test

class FirstRunTest {
    private val book = LibraryItem(Book(BookId("a"), "content://a", "a", 1L, "fp-a", 10, 0L, null), null)

    @Test
    fun `shows once to an empty library and waits while it loads`() {
        val cases = listOf(
            Triple(false, LibraryListState.Loaded(emptyList()), true),
            Triple(false, LibraryListState.Loaded(listOf(book)), false),
            Triple(false, LibraryListState.Failed, false),
            Triple(false, LibraryListState.Loading, null),
            Triple(true, LibraryListState.Loaded(emptyList()), false),
            Triple(true, LibraryListState.Loading, false),
        )
        cases.forEach { (done, library, expected) ->
            assertEquals("$done $library", expected, shouldShowFirstRun(done, library))
        }
    }
}
