// File: app/src/test/kotlin/io/github/skm0078/audora/ui/reader/WhatDidItSayTest.kt
// Purpose: "What did it say?" (plan item 5): which sentence replays, at what speed, and when the speed comes back.

package io.github.skm0078.audora.ui.reader

import io.github.skm0078.audora.core.config.ReaderConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatDidItSayTest {
    private val book = BOOK
    private val units = unitsOf(page = 2)
    private val config = ReaderConfig()

    private fun at(unitIndex: Int) = ReadingPosition(book, 2, units[unitIndex].charStart)

    @Test
    fun `the sentence just heard replays, or the spoken one at the top of the page`() {
        val cases = mapOf(2 to 1, 1 to 0, 0 to 0)
        cases.forEach { (spoken, replayed) ->
            val replay = Replay.start(units, spoken, at(spoken), 1f, config)
            assertEquals("spoken $spoken", units[replayed], replay?.unit)
        }
        assertNull(Replay.start(units, null, null, 1f, config))
    }

    @Test
    fun `the replay reads slower, on a slider step, never below the slowest speed`() {
        val cases = mapOf(1f to 0.85f, 2f to 1.7f, 0.5f to 0.5f)
        cases.forEach { (speed, slower) ->
            assertEquals("at $speed", slower, Replay.start(units, 1, at(1), speed, config)!!.replaySpeed, 1e-4f)
        }
    }

    @Test
    fun `the replay starts on its sentence and ends when the player moves past it`() {
        val waiting = Replay.start(units, 2, at(2), 1f, config)!!
        assertEquals(waiting, waiting.advance(at(2)))
        assertEquals(waiting, waiting.advance(null))
        val playing = waiting.advance(at(1))!!
        assertTrue(playing.started)
        assertEquals(playing, playing.advance(at(1)))
        assertNull(playing.advance(at(2)))
        assertNull(playing.advance(null))
    }

    @Test
    fun `a move elsewhere before the replay starts cancels it`() {
        val waiting = Replay.start(units, 2, at(2), 1f, config)!!
        assertNull(waiting.advance(ReadingPosition(book, 5, 0)))
    }
}
