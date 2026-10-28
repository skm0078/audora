// File: core/src/test/kotlin/io/github/skm0078/audora/core/share/QuoteReadingTest.kt
// Purpose: A quote is read a sentence at a time and every card word gets its start in the whole reading.

package io.github.skm0078.audora.core.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class QuoteReadingTest {
    @Test
    fun `sentences end at full stops in all three scripts and keep every word`() {
        val quote = "All are born free. सब बराबर हैं। “সবাই সমান।” and the rest"
        val reading = QuoteReading.of(quote)
        assertEquals(
            listOf("All are born free.", "सब बराबर हैं।", "“সবাই সমান।”", "and the rest"),
            reading.sentences,
        )
        assertEquals(QuoteReading.wordsOf(quote), reading.sentences.flatMap(QuoteReading::wordsOf))
    }

    @Test
    fun `each sentence's starts move to where it begins in the reading`() {
        val reading = QuoteReading.of("One two. Three four.")
        val starts = reading.wordStarts(
            listOf(
                SpokenSentence(startMs = 0, durationMs = 1_000, wordStartsMs = listOf(100, 500)),
                SpokenSentence(startMs = 1_250, durationMs = 800, wordStartsMs = listOf(50, 400)),
            ),
        )
        assertEquals(listOf(100L, 500L, 1_300L, 1_650L), starts)
    }

    @Test
    fun `a sentence without timings shares its time by letters`() {
        val reading = QuoteReading.of("aa bb cccc")
        val starts = reading.wordStarts(listOf(SpokenSentence(1_000, 800, wordStartsMs = null)))
        assertEquals(listOf(1_000L, 1_200L, 1_400L), starts)
        // Starts for the wrong number of words are not trusted either.
        assertEquals(starts, reading.wordStarts(listOf(SpokenSentence(1_000, 800, listOf(0L)))))
    }

    @Test
    fun `starts never go back and the readings must match the sentences`() {
        val reading = QuoteReading.of("a b. c")
        val starts = reading.wordStarts(
            listOf(SpokenSentence(0, 500, listOf(0, 900)), SpokenSentence(400, 300, listOf(0))),
        )
        assertEquals(listOf(0L, 500L, 500L), starts)
        assertThrows(IllegalArgumentException::class.java) { reading.wordStarts(emptyList()) }
    }
}
