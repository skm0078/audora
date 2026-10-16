// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/cleanup/FoliosTest.kt
// Purpose: Table-driven tests for recognising page numbers in margin lines.

package io.github.skm0078.audora.pdf.cleanup

import org.junit.Assert.assertEquals
import org.junit.Test

class FoliosTest {
    private data class Case(val spans: List<String>, val values: List<Int>, val standalone: Boolean)

    @Test
    fun `page numbers in margin lines`() {
        val cases = listOf(
            Case(listOf("12"), listOf(12), standalone = true),
            Case(listOf("Page 3 of 50"), listOf(3), standalone = true),
            Case(listOf("p. 7"), listOf(7), standalone = true),
            Case(listOf("3 / 50"), listOf(3), standalone = true),
            Case(listOf("\u2013 7 \u2013"), listOf(7), standalone = true),
            Case(listOf("\u0967\u0968"), listOf(12), standalone = true),
            Case(listOf("\u09E7\u09E8"), listOf(12), standalone = true),
            Case(listOf("xii"), listOf(12), standalone = false),
            Case(listOf("IV"), listOf(4), standalone = false),
            Case(listOf("CHAPTER 5. CONNECTIVES", "33"), listOf(33), standalone = false),
            Case(listOf("Chapter 5 Connectives 33"), listOf(33), standalone = false),
            Case(listOf("57", "Page 2 of 50", "N. M. Gilbertson, M. Kot"), listOf(57, 2), standalone = false),
            Case(listOf("Audora S2 test corpus - en-udhr-noto"), emptyList(), standalone = false),
            Case(listOf("12345"), emptyList(), standalone = false),
            Case(listOf("IIII"), emptyList(), standalone = false),
            Case(listOf("Mix"), emptyList(), standalone = false),
            Case(listOf("36:31 And he made bars"), emptyList(), standalone = false),
        )
        for (case in cases) {
            assertEquals(case.spans.toString(), FolioInfo(case.values, case.standalone), Folios.of(case.spans, 4))
        }
    }
}
