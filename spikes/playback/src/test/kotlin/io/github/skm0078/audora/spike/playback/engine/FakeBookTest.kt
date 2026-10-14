// File: spikes/playback/src/test/kotlin/io/github/skm0078/audora/spike/playback/engine/FakeBookTest.kt
// Purpose: The fake book must be deterministic so runs on two phones play the same sentences.

package io.github.skm0078.audora.spike.playback.engine

import io.github.skm0078.audora.spike.playback.config.SpikeConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeBookTest {
    private val config = SpikeConfig(unitCount = 45, unitsPerPage = 20, minUnitMs = 1_000, maxUnitMs = 2_000)
    private val book = FakeBook(config)

    @Test
    fun `same index gives the same unit`() {
        assertEquals(book.unit(7), FakeBook(config).unit(7))
    }

    @Test
    fun `durations stay inside the configured range`() {
        (0 until book.unitCount).map(book::unit).forEach {
            assertTrue("unit ${it.index} is ${it.durationMs} ms", it.durationMs in 1_000..2_000)
        }
    }

    @Test
    fun `pages group units and the last page is clipped`() {
        assertEquals(3, book.pageCount())
        assertEquals(0..19, book.unitsOnPage(0))
        assertEquals(40..44, book.unitsOnPage(2))
        assertEquals(2, book.unit(44).page)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `index past the end is rejected`() {
        book.unit(45)
    }
}
