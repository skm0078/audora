// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/session/ReplaySpeedTest.kt
// Purpose: A slower replay of one sentence restores the listener's speed at the next one, and never keeps the slow one.

package io.github.skm0078.audora.playback.session

import io.github.skm0078.audora.playback.queue.UnitKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplaySpeedTest {
    private val replayed = UnitKey(3, 7)
    private val next = UnitKey(3, 8)

    @Test
    fun `the replayed sentence plays slower and the next one at the listener's speed`() {
        val speed = ReplaySpeed()

        assertEquals(1.0f, speed.start(replayed, current = 1.25f, factor = 0.8f), DELTA)
        assertNull("still the replayed sentence", speed.onSentence(replayed))
        assertEquals(1.25f, requireNotNull(speed.onSentence(next)), DELTA)
        assertFalse(speed.active)
        assertNull("restored once", speed.onSentence(UnitKey(3, 9)))
    }

    @Test
    fun `a second replay slows the listener's speed, not the slowed one`() {
        val speed = ReplaySpeed()
        val slowed = speed.start(replayed, current = 1f, factor = 0.8f)

        assertEquals(0.8f, speed.start(UnitKey(3, 6), current = slowed, factor = 0.8f), DELTA)
        assertEquals(1f, requireNotNull(speed.finish()), DELTA)
    }

    @Test
    fun `a speed change by the listener meanwhile is kept`() {
        val speed = ReplaySpeed()
        speed.start(replayed, current = 1f, factor = 0.8f)

        speed.forget()

        assertNull(speed.onSentence(next))
        assertNull(speed.finish())
    }

    @Test
    fun `moving on to a placeholder ends the replay too`() {
        val speed = ReplaySpeed()
        speed.start(replayed, current = 1f, factor = 0.5f)

        assertTrue(speed.active)
        assertEquals(1f, requireNotNull(speed.onSentence(null)), DELTA)
    }

    private companion object {
        const val DELTA = 1e-4f
    }
}
