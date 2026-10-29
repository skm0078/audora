// File: app/src/test/kotlin/io/github/skm0078/audora/ui/library/LibraryPanelRowsTest.kt
// Purpose: The library's tools that used to sit behind ⋮ are all side panel rows now, Add a book first, each with
//          its own icon, so nothing was lost when the menu went (ADR 2026-10-29-tools-in-the-panel).

package io.github.skm0078.audora.ui.library

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryPanelRowsTest {
    @Test
    fun everyToolTheMenuHeldIsAPanelRow() {
        val expected = listOf(
            LibraryAction.ADD_BOOK,
            LibraryAction.MAKE_IT_YOURS,
            LibraryAction.EDIT_COLLECTIONS,
            LibraryAction.WATCHED_FOLDERS,
            LibraryAction.SYNC_FOLDER,
            LibraryAction.BACKUP,
            LibraryAction.QUIZ,
            LibraryAction.LISTENING_STATS,
        )
        assertEquals(expected, LibraryAction.inPanel)
    }

    @Test
    fun everyToolHasAPlaceToBeFound() {
        val placed = LibraryAction.inPanel + AddBookSource.all.filterIsInstance<AddBookSource.Tool>().map { it.action }
        val buttons = LibraryAction.entries.filter { it.place == LibraryActionPlace.BUTTON }
        assertEquals(LibraryAction.entries.toSet(), (placed + buttons).toSet())
        assertEquals(listOf(LibraryAction.TRY_SAMPLE), buttons)
    }

    @Test
    fun panelRowsEachHaveTheirOwnIcon() {
        val icons = LibraryAction.inPanel.map { it.icon }
        assertEquals(icons.size, icons.toSet().size)
    }
}
