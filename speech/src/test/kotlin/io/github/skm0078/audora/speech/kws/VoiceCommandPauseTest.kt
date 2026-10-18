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
        assertTrue(pause.claimMicrophone())

        val held = async { pause.hold("reading tutor") }
        advanceTimeBy(RELEASE_MS)
        runCurrent()
        assertFalse(held.isCompleted)
        pause.microphoneClosed()
        runCurrent()
        assertTrue(held.isCompleted)
        held.await().close()

        assertTrue(pause.claimMicrophone())
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

    @Test
    fun `a hold cancelled while it waits is closed, so commands resume`() = runTest {
        val pause = VoiceCommandPause(releaseWaitMs = WAIT_MS)
        assertTrue(pause.claimMicrophone())
        val held = launch { pause.hold("voice note") }
        runCurrent()
        assertTrue(pause.isPaused)

        held.cancel()
        runCurrent()
        assertFalse(pause.isPaused)
    }

    @Test
    fun `a listener cannot claim the microphone while held, and a claim makes the next hold wait`() = runTest {
        val pause = VoiceCommandPause(releaseWaitMs = WAIT_MS)
        val tutor = pause.hold("reading tutor")
        assertFalse(pause.claimMicrophone())
        tutor.close()

        assertTrue(pause.claimMicrophone())
        val held = async { pause.hold("voice note") }
        runCurrent()
        assertFalse(held.isCompleted)
        assertFalse(pause.claimMicrophone())
        pause.microphoneClosed()
        runCurrent()
        assertTrue(held.isCompleted)
        held.await().close()
    }

    @Test
    fun `two listeners both let go before a hold returns`() = runTest {
        val pause = VoiceCommandPause(releaseWaitMs = WAIT_MS)
        assertTrue(pause.claimMicrophone())
        assertTrue(pause.claimMicrophone())
        val held = async { pause.hold("reading tutor") }
        pause.microphoneClosed()
        runCurrent()
        assertFalse(held.isCompleted)
        pause.microphoneClosed()
        runCurrent()
        assertTrue(held.isCompleted)
        held.await().close()
    }

    @Test
    fun `holding closes its hold when the block ends or throws`() = runTest {
        val pause = VoiceCommandPause(releaseWaitMs = WAIT_MS)
        assertEquals(1, pause.holding("voice note") { if (pause.isPaused) 1 else 0 })
        assertFalse(pause.isPaused)
        runCatching { pause.holding("reading tutor") { error("recorder failed") } }
        assertFalse(pause.isPaused)
    }

    private companion object {
        const val WAIT_MS = 1_000L
        const val RELEASE_MS = 300L
    }
}
