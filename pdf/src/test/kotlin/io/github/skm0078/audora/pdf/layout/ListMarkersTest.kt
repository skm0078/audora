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
            "(क) पहला", "(ख) दूसरा", "(ক) প্রথম", "(अ) पहला", "(औ) आठवाँ", "(অ) প্রথম",
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
    fun `a lone small letter with a full stop opens an item only in sequence`() {
        val cases = listOf(
            Triple("a.", null, true),
            Triple("i.", null, true),
            Triple("b.", "a.", true),
            Triple("c.", "(b)", true),
            Triple("v.", "iv.", true),
            Triple("x.", "ix)", true),
            Triple("iv.", null, true),
            Triple("b)", null, true),
            // A wrapped line opening with a page, verse or circa reference.
            Triple("p.", null, false),
            Triple("v.", "b.", false),
            Triple("c.", "1.", false),
            Triple("s.", "(iv)", false),
        )
        // In a list nested flush the letter before may be several items back.
        assertEquals(true, ListMarkers.inSequence("b.", listOf("a.", "(i)", "(ii)")))
        assertEquals(true, ListMarkers.inSequence("c.", listOf("a.", "(i)", "(ii)", "b.")))
        assertEquals(false, ListMarkers.inSequence("c.", listOf("(i)", "(ii)")))
        for ((label, before, opens) in cases) {
            assertEquals("$label after $before", opens, ListMarkers.inSequence(label, listOfNotNull(before)))
        }
    }

    @Test
    fun `a label alone is a label`() {
        for (text in listOf("b.", " (iv) ", "B)", "12.")) assertEquals(text, true, ListMarkers.isLabel(text))
        for (text in listOf("A.", "I.", "b. Bananas")) assertEquals(text, false, ListMarkers.isLabel(text))
    }
}
