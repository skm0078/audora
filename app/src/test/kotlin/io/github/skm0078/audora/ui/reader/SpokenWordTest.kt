// File: app/src/test/kotlin/io/github/skm0078/audora/ui/reader/SpokenWordTest.kt
// Purpose: Table-driven checks of the word highlight's timing (plan item 14): by letters, and from the voice (CF-10).

package io.github.skm0078.audora.ui.reader

import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.playback.contract.SentenceTiming
import org.junit.Assert.assertEquals
import org.junit.Test

class SpokenWordTest {
    private fun word(text: String, fraction: Float?) =
        spokenWord(text, fraction)?.let { text.substring(it.first, it.last + 1) }

    @Test
    fun `the speech is spread over the sentence's letters`() {
        // "ab cdef": 6 letters, a sixth of the speech each.
        val cases = listOf(0f to "ab", 0.33f to "ab", 0.34f to "cdef", 0.99f to "cdef", 1f to "cdef", 2f to "cdef")
        cases.forEach { (at, expected) -> assertEquals("at $at", expected, word("ab cdef", at)) }
    }

    @Test
    fun `Hindi and Bengali words stay whole, punctuation rides with its word`() {
        val cases = listOf(
            Triple("प्रत्येक व्यक्ति", 0f, "प्रत्येक"),
            Triple("प्रत्येक व्यक्ति", 0.99f, "व्यक्ति"),
            Triple("কৃষি নির্দেশিকা।", 0.99f, "নির্দেশিকা।"),
            Triple("  Hello,   world.  ", 0f, "Hello,"),
        )
        cases.forEach { (text, at, expected) -> assertEquals("'$text' at $at", expected, word(text, at)) }
    }

    @Test
    fun `an unknown fraction or a sentence without letters marks nothing`() {
        val cases = listOf("a b" to null, "   " to 0.5f, "a" to Float.NaN)
        cases.forEach { (text, at) -> assertEquals("'$text' at $at", null, spokenWord(text, at)) }
    }

    @Test
    fun `the timing counts only for its own sentence, and not during the cue`() {
        val timing =
            SentenceTiming(page = 3, charStart = 40, unitIndex = 2, leadMs = 200, speechMs = 1_000, totalMs = 1_500)
        val here = unit(page = 3, index = 2, charStart = 40)
        val cases = listOf(
            Triple(here, 700L, 0.5f),
            Triple(here, 200L, 0f),
            Triple(here, 1_400L, 1f),
            Triple(here, 100L, null),
            Triple(here, null, null),
            Triple(unit(page = 3, index = 3, charStart = 60), 700L, null),
            Triple(unit(page = 4, index = 2, charStart = 40), 700L, null),
            // A continued table's empty summary shares its first row's charStart, not its index.
            Triple(unit(page = 3, index = 1, charStart = 40), 700L, null),
            Triple(null, 700L, null),
        )
        cases.forEach { (unit, ms, expected) ->
            assertEquals("$unit at $ms", expected, speechFraction(timing, unit, ms))
        }
        assertEquals(null, speechFraction(null, here, 700L))
    }

    @Test
    fun `word starts read off the voice place the highlight, after the cue (CF-10)`() {
        val timing = SentenceTiming(3, 40, 2, leadMs = 200, speechMs = 1_000, totalMs = 1_500, listOf(0, 600))
        val here = unit(page = 3, index = 2, charStart = 40)
        // By letters "words" would take over at 4/9 of the speech (644 ms); the voice says it from 800 ms.
        val cases = listOf(100L to null, 300L to "Some", 700L to "Some", 799L to "Some", 800L to "words")
        cases.forEach { (ms, expected) ->
            assertEquals("at $ms", expected, spokenWordOf(timing, here, ms)?.let { here.text.substring(it) })
        }
    }

    @Test
    fun `word starts that do not match the sentence's words fall back to the letters`() {
        val here = unit(page = 3, index = 2, charStart = 40)
        val wrongCount = SentenceTiming(3, 40, 2, leadMs = 0, speechMs = 900, totalMs = 900, listOf(0, 100, 200))
        val noWords = wrongCount.copy(wordStartsMs = emptyList())

        listOf(wrongCount, noWords).forEach { timing ->
            assertEquals("Some", spokenWordOf(timing, here, 300L)?.let { here.text.substring(it) })
            assertEquals("words", spokenWordOf(timing, here, 500L)?.let { here.text.substring(it) })
        }
        assertEquals(null, spokenWordOf(wrongCount, unit(page = 3, index = 3, charStart = 60), 500L))
    }

    @Test
    fun `the nth word is what whitespace separates`() {
        val text = " “Yes,”\u00a0she\tsaid "
        val words = (0..3).map { i -> nthWord(text, i)?.let { text.substring(it) } }
        assertEquals(listOf("“Yes,”", "she", "said", null), words)
    }

    private fun unit(page: Int, index: Int, charStart: Int) =
        ReadingUnit(page, index, charStart, charStart + 10, "Some words", Lang.EN, 0, null)
}
