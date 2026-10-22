// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/kws/VoiceCommandTest.kt
// Purpose: Voice commands (plan item 38): the keywords file, the tags read back, repeats and the model's pins.

package io.github.skm0078.audora.speech.kws

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCommandTest {
    @Test
    fun `every phrase is one line starting with Audora and tagged with its command`() {
        val lines = VoiceCommand.keywordsFile().trimEnd('\n').lines()
        assertEquals(VoiceCommand.entries.sumOf { it.tokens.size }, lines.size)
        lines.forEach { line ->
            assertTrue(line, line.startsWith("▁A U D OR A ▁"))
            val tag = line.substringAfterLast(' ')
            assertTrue(line, tag.startsWith("@"))
            assertTrue(line, VoiceCommand.of(tag) != null)
        }
        assertTrue("▁A U D OR A ▁ST O P @PAUSE" in lines)
    }

    @Test
    fun `no two commands share a phrase`() {
        val phrases = VoiceCommand.entries.flatMap { it.tokens.toList() }
        assertEquals(phrases.size, phrases.toSet().size)
    }

    @Test
    fun `a spotter result maps back to its command`() {
        assertEquals(VoiceCommand.NEXT, VoiceCommand.of("@NEXT"))
        assertEquals(VoiceCommand.SLOWER, VoiceCommand.of(" @SLOWER "))
        assertNull(VoiceCommand.of(""))
        assertNull(VoiceCommand.of("@SKIP"))
    }

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
        echo.sentenceStarted("Then AUDORA said: pause the music.", 1_000)
        assertTrue(echo.suppresses(1_500))
        echo.sentenceStarted("The next sentence.", 4_000)
        assertTrue(echo.suppresses(5_999))
        assertFalse(echo.suppresses(6_000))
        echo.sentenceStarted("ऑडोरा ने कहा", 7_000)
        echo.stopped(8_000)
        assertTrue(echo.suppresses(9_000))
        assertFalse(echo.suppresses(10_000))
    }

    @Test
    fun `the pins repeat speech models json`() {
        val catalog = File("models.json").takeIf { it.isFile } ?: File("speech/models.json")
        val text = catalog.readText()
        val english = KeywordModel.ENGLISH
        val entry = text.substringAfter("\"id\": \"${english.id}\"").substringBefore("\"id\":")
        listOf(
            "\"file\": \"${english.fileName}\"",
            "\"sha256\": \"${english.sha256}\"",
            "\"sizeBytes\": ${english.sizeBytes}",
            "\"model\": \"${english.encoder}\"",
            "\"tokens\": \"${english.tokens}\"",
            "\"${english.decoder}\"",
            "\"${english.joiner}\"",
        ).forEach { assertTrue("models.json lacks $it", it in entry) }
        assertTrue("\"baseUrl\": \"${english.baseUrl}\"" in text)
        assertEquals("${english.id}-${english.sha256.take(12)}.zip", english.fileName)
    }
}
