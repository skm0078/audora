// File: core/src/test/kotlin/io/github/skm0078/audora/core/home/LineOfTheDayTest.kt
// Purpose: The line of the day stays put all day, changes with the day, skips empty quotes and is cut at a word.

package io.github.skm0078.audora.core.home

import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.notes.Highlight
import io.github.skm0078.audora.core.resume.ResumeAnchor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LineOfTheDayTest {
    @Test
    fun `no highlight with words means no line`() {
        assertNull(LineOfTheDay.pick(emptyList(), DAY))
        assertNull(LineOfTheDay.pick(listOf(highlight(1, "  ")), DAY))
    }

    @Test
    fun `the pick does not depend on the order the highlights come in`() {
        val all = (1L..9L).map { highlight(it, "Line $it") }
        assertEquals(LineOfTheDay.pick(all, DAY), LineOfTheDay.pick(all.reversed(), DAY))
    }

    @Test
    fun `the next day brings another line`() {
        val all = (1L..9L).map { highlight(it, "Line $it") }
        assertNotEquals(LineOfTheDay.pick(all, DAY), LineOfTheDay.pick(all, DAY + 1))
    }

    @Test
    fun `a long quote is cut at a word with an ellipsis`() {
        val clipped = LineOfTheDay.clip("word ".repeat(80), max = 40)
        assertTrue(clipped.length <= 40)
        assertTrue(clipped.endsWith("word…"))
    }

    @Test
    fun `a quote is kept on one line`() {
        assertEquals("one two three", LineOfTheDay.clip(" one\n two\tthree "))
    }

    private fun highlight(id: Long, quote: String) = Highlight(
        id = id,
        anchor = ResumeAnchor(
            BookId("book"),
            page = 0,
            charStart = 0,
            fingerprint = 0,
            tailFingerprint = 0,
            pipelineVersion = 1,
            savedAtMillis = 0,
        ),
        endPage = 0,
        endChar = 1,
        quote = quote,
    )

    private companion object {
        const val DAY = 20_000L
    }
}
