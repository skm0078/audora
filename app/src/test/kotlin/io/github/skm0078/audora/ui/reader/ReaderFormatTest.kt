// File: app/src/test/kotlin/io/github/skm0078/audora/ui/reader/ReaderFormatTest.kt
// Purpose: Table-driven checks of the reader's speed, time-left and page-number text.

package io.github.skm0078.audora.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderFormatTest {
    @Test
    fun `speed labels drop trailing zeros`() {
        val cases = mapOf(0.5f to "0.5", 0.75f to "0.75", 1f to "1", 1.25f to "1.25", 2f to "2", 3f to "3")
        cases.forEach { (speed, label) -> assertEquals(label, speedLabel(speed)) }
    }

    @Test
    fun `time left rounds up and never goes negative`() {
        val cases = mapOf(
            15 * 60_000L to "15:00",
            15 * 60_000L - 1 to "15:00",
            14 * 60_000L + 59_001 to "15:00",
            61_000L to "1:01",
            999L to "0:01",
            0L to "0:00",
            -5_000L to "0:00",
            60 * 60_000L to "1:00:00",
            61 * 60_000L + 5_000 to "1:01:05",
        )
        cases.forEach { (ms, label) -> assertEquals("for $ms ms", label, remainingLabel(ms)) }
    }

    @Test
    fun `listening time left is whole hours and minutes, rounded up`() {
        val cases = mapOf(
            0L to HoursMinutes(0, 0),
            1L to HoursMinutes(0, 1),
            60_000L to HoursMinutes(0, 1),
            60_001L to HoursMinutes(0, 2),
            (2 * 60 + 10) * 60_000L to HoursMinutes(2, 10),
            -1L to HoursMinutes(0, 0),
        )
        cases.forEach { (ms, expected) -> assertEquals("for $ms ms", expected, hoursMinutes(ms)) }
    }

    @Test
    fun `typed page numbers must exist in the book`() {
        val cases = mapOf("1" to 1, "574" to 574, "575" to null, "0" to null, "" to null, "12a" to null, "007" to 7)
        cases.forEach { (text, page) -> assertEquals("for '$text'", page, parsePageNumber(text, pageCount = 574)) }
    }
}
