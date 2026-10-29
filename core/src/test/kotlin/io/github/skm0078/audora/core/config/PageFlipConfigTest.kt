// File: core/src/test/kotlin/io/github/skm0078/audora/core/config/PageFlipConfigTest.kt
// Purpose: When a swipe or an edge tap turns the page, and when it settles back (ADR 2026-10-28-page-flip).

package io.github.skm0078.audora.core.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PageFlipConfigTest {
    private val config = PageFlipConfig()

    @Test
    fun `a swipe past a quarter of the page turns it that way`() {
        assertEquals("left: the next page", 1, config.swipeTurn(-300f, 0f, WIDTH, FLING))
        assertEquals("right: the page before", -1, config.swipeTurn(300f, 0f, WIDTH, FLING))
    }

    @Test
    fun `a short slow swipe settles back`() {
        assertEquals(0, config.swipeTurn(-100f, -100f, WIDTH, FLING))
        assertEquals(0, config.swipeTurn(0f, -5000f, WIDTH, FLING))
    }

    @Test
    fun `a quick flick turns even when short`() {
        assertEquals(1, config.swipeTurn(-60f, -2000f, WIDTH, FLING))
        assertEquals(-1, config.swipeTurn(60f, 2000f, WIDTH, FLING))
    }

    @Test
    fun `a flick back the other way settles back even after a long drag`() {
        assertEquals(0, config.swipeTurn(-500f, 2000f, WIDTH, FLING))
    }

    @Test
    fun `taps in the outer fifth turn, taps in the middle do not`() {
        assertEquals(-1, config.edgeTurn(0.05f))
        assertEquals(0, config.edgeTurn(0.5f))
        assertEquals(1, config.edgeTurn(0.95f))
        assertEquals("edge taps off", 0, PageFlipConfig(edgeShare = 0f).edgeTurn(0f))
    }

    @Test
    fun `nonsense settings are refused`() {
        assertThrows(IllegalArgumentException::class.java) { PageFlipConfig(edgeShare = 0.6f) }
        assertThrows(IllegalArgumentException::class.java) { PageFlipConfig(turnShare = 1f) }
        assertThrows(IllegalArgumentException::class.java) { PageFlipConfig(flingDpPerSecond = 0f) }
        assertThrows(IllegalArgumentException::class.java) { PageFlipConfig(curlMs = 0) }
        assertThrows(IllegalArgumentException::class.java) { PageFlipConfig(readingLift = 0.8f) }
        assertThrows(IllegalArgumentException::class.java) { PageFlipConfig(backAlpha = 2f) }
        assertThrows(IllegalArgumentException::class.java) { PageFlipConfig(shadowShare = 0f) }
    }

    @Test
    fun `a turn made by reading aloud is quicker than a curl by hand`() {
        assertTrue(config.readingTurnMs < config.curlMs)
    }

    private companion object {
        const val WIDTH = 1000f
        const val FLING = 1500f
    }
}
