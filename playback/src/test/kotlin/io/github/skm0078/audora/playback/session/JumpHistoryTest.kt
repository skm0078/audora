// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/session/JumpHistoryTest.kt
// Purpose: Undo jump (plan item 78): bursts undo as one, undo walks back, the history stays bounded.

package io.github.skm0078.audora.playback.session

import io.github.skm0078.audora.core.config.JumpConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JumpHistoryTest {
    private val history = JumpHistory(JumpConfig(mergeWindowMs = 5_000, keep = 3))

    private fun spot(page: Int) = Spot(page, 0)

    @Test
    fun `undo goes back to where reading was, one jump at a time`() {
        history.record(spot(1), spot(9), JumpOrigin.START, atMillis = 0)
        history.record(spot(9), spot(20), JumpOrigin.START, atMillis = 60_000)
        assertEquals(spot(9), history.undo())
        assertEquals(spot(1), history.undo())
        assertNull(history.undo())
    }

    @Test
    fun `a burst of skips is one jump`() {
        history.record(spot(3), spot(4), JumpOrigin.SKIP, atMillis = 0)
        history.record(spot(4), spot(5), JumpOrigin.SKIP, atMillis = 1_000)
        history.record(spot(5), spot(6), JumpOrigin.SKIP, atMillis = 5_500)
        assertEquals(Jump(spot(3), spot(6), JumpOrigin.SKIP, 5_500), history.last)
        assertEquals(spot(3), history.undo())
        assertNull(history.last)
    }

    @Test
    fun `skipping back to where a burst began leaves nothing to undo, and staying put is no jump`() {
        history.record(spot(3), spot(4), JumpOrigin.SKIP, atMillis = 0)
        history.record(spot(4), spot(3), JumpOrigin.SKIP, atMillis = 1_000)
        assertNull(history.last)
        history.record(spot(7), spot(7), JumpOrigin.START, atMillis = 60_000)
        assertNull(history.last)
    }

    @Test
    fun `only the latest jumps are kept, and clear forgets them`() {
        repeat(5) { history.record(spot(it), spot(it + 10), JumpOrigin.START, atMillis = it * 60_000L) }
        assertEquals(spot(4), history.undo())
        assertEquals(spot(3), history.undo())
        assertEquals(spot(2), history.undo())
        assertNull(history.undo())
        history.record(spot(1), spot(2), JumpOrigin.START, atMillis = 0)
        history.clear()
        assertNull(history.last)
    }
}
