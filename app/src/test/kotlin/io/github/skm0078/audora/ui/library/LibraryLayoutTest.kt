// File: app/src/test/kotlin/io/github/skm0078/audora/ui/library/LibraryLayoutTest.kt
// Purpose: The library's layout: automatic turns to a list at large text, a picked layout holds at any text size,
//          and a saved name this version does not know falls back to automatic.

package io.github.skm0078.audora.ui.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryLayoutTest {
    @Test
    fun `automatic shows covers at normal text and a list from 150 percent`() {
        assertFalse(LibraryLayout.AUTO.showsList(1f))
        assertFalse(LibraryLayout.AUTO.showsList(1.3f))
        assertTrue(LibraryLayout.AUTO.showsList(LibraryLayout.LARGE_TEXT_SCALE))
        assertTrue(LibraryLayout.AUTO.showsList(2f))
    }

    @Test
    fun `a picked layout holds at any text size`() {
        listOf(1f, 2f).forEach { scale ->
            assertFalse(LibraryLayout.GRID.showsList(scale))
            assertTrue(LibraryLayout.LIST.showsList(scale))
        }
    }

    @Test
    fun `a saved name is read back, and an unknown or missing one is automatic`() {
        LibraryLayout.entries.forEach { assertEquals(it, LibraryLayout.named(it.name)) }
        assertEquals(LibraryLayout.AUTO, LibraryLayout.named("SHELVES"))
        assertEquals(LibraryLayout.AUTO, LibraryLayout.named(null))
    }
}
