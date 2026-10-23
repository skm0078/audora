// File: app/src/test/kotlin/io/github/skm0078/audora/ui/reader/WhatDidItSayTest.kt
// Purpose: "What did it say?" (plan item 5): which sentence the card shows, and when it goes away.

package io.github.skm0078.audora.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatDidItSayTest {
    private val book = BOOK
    private val units = unitsOf(page = 2)

    private fun at(unitIndex: Int) = ReadingPosition(book, 2, units[unitIndex].charStart)

    @Test
    fun `the sentence just heard replays, and the top of the page shows no card`() {
        val cases = mapOf(2 to 1, 1 to 0)
        cases.forEach { (spoken, replayed) ->
            val replay = Replay.start(units, spoken, at(spoken))
            assertEquals("spoken $spoken", units[replayed], replay?.unit)
        }
        assertNull(Replay.start(units, 0, at(0)))
        assertNull(Replay.start(units, null, null))
    }

    @Test
    fun `on a continued table's first row the sentence before the table replays, not the empty summary`() {
        val table = continuedTableOf(page = 2)
        val replay = Replay.start(table, 2, ReadingPosition(book, 2, table[2].charStart))
        assertEquals(table[0], replay?.unit)
    }

    @Test
    fun `the replay starts on its sentence and ends when the player moves past it`() {
        val waiting = Replay.start(units, 2, at(2))!!
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
        val waiting = Replay.start(units, 2, at(2))!!
        assertNull(waiting.advance(ReadingPosition(book, 5, 0)))
    }
}
