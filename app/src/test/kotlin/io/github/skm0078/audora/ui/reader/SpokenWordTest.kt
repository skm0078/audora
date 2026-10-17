// File: app/src/test/kotlin/io/github/skm0078/audora/ui/reader/SpokenWordTest.kt
// Purpose: Table-driven checks of the word highlight's timing (plan item 14, version one).

package io.github.skm0078.audora.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class SpokenWordTest {
    private fun word(text: String, positionMs: Long, durationMs: Long?) =
        spokenWord(text, positionMs, durationMs)?.let { text.substring(it.first, it.last + 1) }

    @Test
    fun `the sentence's time is spread over its letters`() {
        // "ab cdef": 6 letters over 600 ms, 100 ms each.
        val cases = listOf(0L to "ab", 199L to "ab", 200L to "cdef", 599L to "cdef", 600L to "cdef", 5_000L to "cdef")
        cases.forEach { (at, expected) -> assertEquals("at $at ms", expected, word("ab cdef", at, 600L)) }
    }

    @Test
    fun `Hindi and Bengali words stay whole, punctuation rides with its word`() {
        val cases = listOf(
            Triple("प्रत्येक व्यक्ति", 0L, "प्रत्येक"),
            Triple("प्रत्येक व्यक्ति", 999L, "व्यक्ति"),
            Triple("কৃষি নির্দেশিকা।", 999L, "নির্দেশিকা।"),
            Triple("  Hello,   world.  ", 0L, "Hello,"),
        )
        cases.forEach { (text, at, expected) -> assertEquals("'$text' at $at", expected, word(text, at, 1_000L)) }
    }

    @Test
    fun `unknown timing, an empty sentence or a negative position mark nothing`() {
        val cases =
            listOf(Triple("a b", 0L, null), Triple("a b", 0L, 0L), Triple("   ", 0L, 100L), Triple("a", -1L, 100L))
        cases.forEach { (text, at, duration) -> assertEquals(null, spokenWord(text, at, duration)) }
    }
}
