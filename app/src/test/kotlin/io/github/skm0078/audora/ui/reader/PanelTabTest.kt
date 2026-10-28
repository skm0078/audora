// File: app/src/test/kotlin/io/github/skm0078/audora/ui/reader/PanelTabTest.kt
// Purpose: The reader's side panel offers only the tabs a book has, in the mockup's order, and only on an expanded
//          window (ADR 2026-10-28-tablet-panes).

package io.github.skm0078.audora.ui.reader

import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.ui.theme.WindowWidths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelTabTest {
    @Test
    fun `a book with chapters and the guide gets all three tabs in order`() {
        assertEquals(
            listOf(PanelTab.CONTENTS, PanelTab.NOTES, PanelTab.PEOPLE),
            panelTabs(hasChapters = true, hasPeople = true),
        )
    }

    @Test
    fun `a book without chapters keeps Notes first`() {
        assertEquals(listOf(PanelTab.NOTES, PanelTab.PEOPLE), panelTabs(hasChapters = false, hasPeople = true))
    }

    @Test
    fun `Notes is always offered`() {
        assertEquals(listOf(PanelTab.NOTES), panelTabs(hasChapters = false, hasPeople = false))
    }

    @Test
    fun `the panel shows from the expanded width only`() {
        assertTrue(showsSidePanel(WindowWidths.EXPANDED))
        assertTrue(showsSidePanel(1280.dp))
        assertFalse(showsSidePanel(WindowWidths.EXPANDED - 1.dp))
        assertFalse(showsSidePanel(411.dp))
    }
}
