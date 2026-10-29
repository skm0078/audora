// File: app/src/test/kotlin/io/github/skm0078/audora/ui/library/AddBookSourceTest.kt
// Purpose: The Add a book sheet offers every way a book comes in, each once, and none of them is left in the ⋮ menu
//          as well (ADR 2026-10-29-add-a-book).

package io.github.skm0078.audora.ui.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AddBookSourceTest {
    private val tools = AddBookSource.all.filterIsInstance<AddBookSource.Tool>().map { it.action }

    @Test
    fun choosingAFileComesFirst() {
        assertEquals(AddBookSource.File, AddBookSource.all.first())
    }

    @Test
    fun everyWayInIsOffered() {
        val expected = listOf(
            LibraryAction.POINT_AND_READ,
            LibraryAction.READ_PICTURES,
            LibraryAction.READ_CLIPBOARD,
            LibraryAction.READ_WEB_PAGE,
            LibraryAction.BOOK_CATALOGUES,
            LibraryAction.WATCHED_FOLDERS,
        )
        assertEquals(expected, tools)
    }

    @Test
    fun noWayInStaysInTheMenu() {
        val inMenu = LibraryAction.entries.filter { it.inMenu }
        assertTrue("in both the sheet and ⋮: ${tools.intersect(inMenu.toSet())}", tools.none { it in inMenu })
    }

    @Test
    fun everyRowHasItsOwnLineAndIcon() {
        val sources = AddBookSource.all
        assertEquals("lines", sources.size, sources.map { it.line }.toSet().size)
        assertEquals("icons", sources.size, sources.map { it.icon }.toSet().size)
        sources.filterIsInstance<AddBookSource.Tool>().forEach { tool ->
            assertTrue("${tool.action} reuses its long hint as its line", tool.line != tool.action.hint)
        }
    }

    @Test
    fun theSampleIsAButtonOfItsOwn() {
        assertEquals(LibraryActionPlace.BUTTON, LibraryAction.TRY_SAMPLE.place)
        assertTrue(LibraryAction.TRY_SAMPLE !in tools)
    }
}
