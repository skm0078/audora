// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/voice/ReplayRenderTest.kt
// Purpose: Only the replayed sentence renders with the slower style, and only until another sentence starts.

package io.github.skm0078.audora.playback.voice

import io.github.skm0078.audora.core.model.SpeechStyle
import io.github.skm0078.audora.playback.queue.UnitKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReplayRenderTest {
    private val replay = ReplayRender()
    private val slower = SpeechStyle(rate = 1.5f)

    @Test
    fun `the replayed sentence gets the slower style, others none`() {
        replay.set(UnitKey(2, 4), slower)
        assertEquals(slower, replay.styleFor(UnitKey(2, 4)))
        assertNull(replay.styleFor(UnitKey(2, 5)))
    }

    @Test
    fun `it lasts while the replayed sentence plays and ends with the next one`() {
        replay.set(UnitKey(2, 4), slower)
        assertNull(replay.onSentence(UnitKey(2, 4)))
        assertEquals(slower, replay.styleFor(UnitKey(2, 4)))
        // Ending it names the replayed sentence, once, so its slower audio can be dropped.
        assertEquals(UnitKey(2, 4), replay.onSentence(UnitKey(2, 5)))
        assertNull(replay.styleFor(UnitKey(2, 4)))
        assertNull(replay.onSentence(UnitKey(2, 6)))
    }
}
