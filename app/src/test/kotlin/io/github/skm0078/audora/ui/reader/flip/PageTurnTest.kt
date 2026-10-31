// File: app/src/test/kotlin/io/github/skm0078/audora/ui/reader/flip/PageTurnTest.kt
// Purpose: How long each page turn takes (ADR 2026-10-29-page-curl), and that a reader starts with a curl and with
//          text books turned a screenful at a time.

package io.github.skm0078.audora.ui.reader.flip

import io.github.skm0078.audora.core.config.PageFlipConfig
import io.github.skm0078.audora.ui.reader.ReadingLook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageTurnTest {
    private val config = PageFlipConfig()

    @Test
    fun `a new reader curls its pages and turns text books a screenful at a time`() {
        val look = ReadingLook()
        assertEquals(TurnStyle.CURL, look.turn.style)
        assertTrue(look.turnPages)
    }

    @Test
    fun `a turn by hand follows the speed, a turn by reading keeps its own short time`() {
        val quick = PageTurn(speed = TurnSpeed.QUICK)
        val slow = PageTurn(speed = TurnSpeed.SLOW)
        assertTrue(quick.handMs(config) < PageTurn().handMs(config))
        assertTrue(slow.handMs(config) > PageTurn().handMs(config))
        assertEquals(config.readingTurnMs, quick.readingMs(config))
        assertEquals(config.readingTurnMs, slow.readingMs(config))
    }

    @Test
    fun `a slide is quicker than a curl, and none takes no time at all`() {
        assertTrue(PageTurn(style = TurnStyle.SLIDE).handMs(config) < PageTurn().handMs(config))
        assertEquals(0, PageTurn(style = TurnStyle.NONE).handMs(config))
        assertEquals(0, PageTurn(style = TurnStyle.NONE).readingMs(config))
    }
}
