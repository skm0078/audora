// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/kws/VoiceCommandTest.kt
// Purpose: Voice commands (plan item 38): one utterance acts once, and the book's own voice never gives commands.

package io.github.skm0078.audora.speech.kws

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCommandTest {
    @Test
    fun `the same command inside the gap acts once, another command always acts`() {
        val repeats = CommandRepeats(gapMs = 1_500)
        assertTrue(repeats.accept(VoiceCommand.NEXT, 10_000))
        assertFalse(repeats.accept(VoiceCommand.NEXT, 11_000))
        assertTrue(repeats.accept(VoiceCommand.PAUSE, 11_100))
        assertTrue(repeats.accept(VoiceCommand.NEXT, 11_200))
        assertTrue(repeats.accept(VoiceCommand.NEXT, 12_700))
    }

    @Test
    fun `commands are ignored while the book says the app's name and for the hold after`() {
        val echo = NarratorEcho(holdMs = 2_000)
        assertFalse(echo.suppresses(0))
        echo.sentenceStarted("Then LISTENLOCK said: pause the music.", 1_000)
        assertTrue(echo.suppresses(1_500))
        echo.sentenceStarted("The next sentence.", 4_000)
        assertTrue(echo.suppresses(5_999))
        assertFalse(echo.suppresses(6_000))
        echo.sentenceStarted("লিসেন লক বলল", 7_000)
        echo.stopped(8_000)
        assertTrue(echo.suppresses(9_000))
        assertFalse(echo.suppresses(10_000))
    }
}
