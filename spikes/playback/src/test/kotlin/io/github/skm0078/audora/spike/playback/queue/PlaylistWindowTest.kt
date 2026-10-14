// File: spikes/playback/src/test/kotlin/io/github/skm0078/audora/spike/playback/queue/PlaylistWindowTest.kt
// Purpose: The playlist is topped up a page at a time and stops growing at the end of the book.

package io.github.skm0078.audora.spike.playback.queue

import io.github.skm0078.audora.spike.playback.config.SpikeConfig
import io.github.skm0078.audora.spike.playback.engine.FakeBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistWindowTest {
    private val book = FakeBook(SpikeConfig(unitCount = 45, unitsPerPage = 20))
    private val window = PlaylistWindow(book, minItemsAhead = 20)

    @Test
    fun `empty playlist gets the first page`() {
        assertEquals(0..19, window.nextAppend(lastQueuedUnit = -1, currentUnit = 0))
    }

    @Test
    fun `enough items ahead means no append`() {
        assertNull(window.nextAppend(lastQueuedUnit = 39, currentUnit = 19))
    }

    @Test
    fun `running low appends the next page`() {
        assertEquals(20..39, window.nextAppend(lastQueuedUnit = 19, currentUnit = 1))
    }

    @Test
    fun `last page is clipped and then nothing more`() {
        assertEquals(40..44, window.nextAppend(lastQueuedUnit = 39, currentUnit = 30))
        assertNull(window.nextAppend(lastQueuedUnit = 44, currentUnit = 44))
    }
}
