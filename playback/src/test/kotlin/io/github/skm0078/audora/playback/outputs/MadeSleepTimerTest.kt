// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/outputs/MadeSleepTimerTest.kt
// Purpose: The saved-audio sleep timer counts down its minutes, pauses at the right chapter's end, and turns off.

package io.github.skm0078.audora.playback.outputs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MadeSleepTimerTest {
    private var now = 1_000L
    private val timer = MadeSleepTimer { now }

    @Test
    fun `a minutes timer counts down and never below zero`() {
        timer.minutes(15)
        now += 60_000

        assertEquals(14 * 60_000L, timer.remainingMs())
        now += 20 * 60_000
        assertEquals(0L, timer.remainingMs())
        assertFalse(timer.pausesAtThisChaptersEnd)
    }

    @Test
    fun `end of chapter pauses at the playing chapter's end`() {
        timer.chapters(1)

        assertTrue(timer.pausesAtThisChaptersEnd)
        assertNull(timer.remainingMs())
    }

    @Test
    fun `after three chapters the pause waits for the third chapter's end`() {
        timer.chapters(3)
        assertFalse(timer.pausesAtThisChaptersEnd)

        timer.chapterEnded()
        assertFalse(timer.pausesAtThisChaptersEnd)
        timer.chapterEnded()
        assertTrue(timer.pausesAtThisChaptersEnd)
        // The last chapter's end pauses; it is not counted past one.
        timer.chapterEnded()
        assertEquals(1, timer.chaptersLeft)
    }

    @Test
    fun `a new choice replaces the old one and off clears both`() {
        timer.chapters(2)
        timer.minutes(30)
        assertEquals(0, timer.chaptersLeft)
        assertTrue(timer.isSet)

        timer.clear()
        assertFalse(timer.isSet)
        assertNull(timer.remainingMs())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero minutes is refused`() {
        timer.minutes(0)
    }
}
