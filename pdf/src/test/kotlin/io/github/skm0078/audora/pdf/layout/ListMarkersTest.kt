// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/layout/ListMarkersTest.kt
// Purpose: Which line openings are list labels: numbers, letters and Roman numerals, and what is prose instead.

package io.github.skm0078.audora.pdf.layout

import org.junit.Assert.assertEquals
import org.junit.Test

class ListMarkersTest {
    private fun opensWithLabel(line: String) = ListMarkers.startsWithLabel(line, 0, line.length)

    @Test
    fun `numbers, letters and Roman numerals with a stop or bracket are labels`() {
        val labels = listOf(
            "1. Open the box", "12) Close it", "(3) Wait", "(a) Apples", "b) Bananas", "c. Cherries", "(iv) Fourth",
            "iv. Fourth", "ii) Second", "(A) First", "B) Second", "(IV) Fourth", "१. पहला", "(२) दूसरा",
        )
        for (line in labels) assertEquals(line, true, opensWithLabel(line))
    }

    @Test
    fun `initials, headings, years and abbreviations are not labels`() {
        val prose = listOf(
            "A. Smith wrote it", "I. Introduction", "IV. Results", "1947. The year", "e.g. apples", "a.m. on Monday",
            "ab) not a label", "(ab) nor this", "b.", "iv.Fourth", "",
        )
        for (line in prose) assertEquals(line, false, opensWithLabel(line))
    }

    @Test
    fun `a label alone is a label`() {
        for (text in listOf("b.", " (iv) ", "B)", "12.")) assertEquals(text, true, ListMarkers.isLabel(text))
        for (text in listOf("A.", "I.", "b. Bananas")) assertEquals(text, false, ListMarkers.isLabel(text))
    }
}
