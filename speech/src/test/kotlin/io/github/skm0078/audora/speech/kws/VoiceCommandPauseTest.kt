// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/kws/VoiceCommandPauseTest.kt
// Purpose: Another feature's hold pauses voice commands until every hold is closed, and waits for the microphone.

package io.github.skm0078.audora.speech.kws

import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCommandPauseTest {
    @Test
    fun `holds nest, and listening resumes only when the last is closed, once`() = runTest {
        val pause = VoiceCommandPause(releaseWaitMs = WAIT_MS)
        val tutor = pause.hold("reading tutor")
        val notes = pause.hold("voice note")
        var free = false
        launch {
            pause.awaitFree()
            free = true
        }

        tutor.close()
        tutor.close()
        runCurrent()
        assertTrue(pause.isPaused)
        assertFalse(free)
        notes.close()
        runCurrent()
        assertFalse(pause.isPaused)
        assertTrue(free)
    }

    @Test
    fun `a hold returns once the listener lets the microphone go, or after the wait`() = runTest {
        val pause = VoiceCommandPause(releaseWaitMs = WAIT_MS)
        pause.microphone(open = true)

        val held = async { pause.hold("reading tutor") }
        advanceTimeBy(RELEASE_MS)
        runCurrent()
        assertFalse(held.isCompleted)
        pause.microphone(open = false)
        runCurrent()
        assertTrue(held.isCompleted)
        held.await().close()

        pause.microphone(open = true)
        val start = currentTime
        pause.hold("stuck listener").close()
        assertEquals(WAIT_MS, currentTime - start)
    }

    @Test
    fun `with no listener attached a hold returns at once`() = runTest {
        val start = currentTime
        VoiceCommandPause().hold("reading tutor").close()
        assertEquals(start, currentTime)
    }

    private companion object {
        const val WAIT_MS = 1_000L
        const val RELEASE_MS = 300L
    }
}
