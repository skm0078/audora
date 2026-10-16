// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/cleanup/UnicodeRepairTest.kt
// Purpose: Table-driven tests for NFC with offsets kept and for "|" typed as a danda.

package io.github.skm0078.audora.pdf.cleanup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class UnicodeRepairTest {
    private data class Case(val name: String, val input: String, val expected: String)

    private fun check(cases: List<Case>, repair: (MappedText) -> MappedText) = cases.forEach { case ->
        assertEquals(case.name, case.expected, repair(MappedText.plain(case.input)).text)
    }

    @Test
    fun `NFC composes and decomposes as Unicode says`() = check(
        listOf(
            Case("Bengali o in two parts", "\u0995\u09C7\u09BE", "\u0995\u09CB"),
            Case("Bengali au in two parts", "\u0995\u09C7\u09D7", "\u0995\u09CC"),
            Case("Devanagari nnna composes", "\u0928\u093C", "\u0929"),
            Case("Devanagari qa decomposes (composition exclusion)", "\u0958", "\u0915\u093C"),
            Case("Bengali yya decomposes", "\u09DF", "\u09AF\u09BC"),
            Case("Latin e acute", "cafe\u0301", "caf\u00E9"),
            Case("ZWJ and ZWNJ stay", JOINERS, JOINERS),
            Case("already NFC", "हिन्दी বাংলা", "हिन्दी বাংলা"),
        ),
        UnicodeRepair::compose,
    )

    @Test
    fun `NFC keeps every unit mapped to its cluster`() {
        // "a", Bengali ক + ে + া (composes to two units), " ", क़ (decomposes to two units).
        val input = MappedText("a\u0995\u09C7\u09BE \u0958", intArrayOf(10, 11, 12, 13, 14, 15))
        val out = UnicodeRepair.compose(input)
        assertEquals("a\u0995\u09CB \u0915\u093C", out.text)
        assertArrayEquals(intArrayOf(10, 11, 11, 14, 15, 15), out.sources)
    }

    @Test
    fun `NFC text is returned as it is`() {
        val input = MappedText.plain("पहले से NFC")
        assertSame(input, UnicodeRepair.compose(input))
    }

    @Test
    fun `a pipe after Hindi or Bengali becomes a danda`() = check(
        listOf(
            Case("Hindi, no space", "यह वाक्य है| अगला", "यह वाक्य है। अगला"),
            Case("Hindi, space before", "यह वाक्य है | अगला", "यह वाक्य है । अगला"),
            Case("at the end", "वाक्य है|", "वाक्य है।"),
            Case("before Indic text", "है|अगला", "है।अगला"),
            Case("double pipe", "श्लोक||", "श्लोक॥"),
            Case("Bengali", "এটা বাক্য| পরের", "এটা বাক্য। পরের"),
            Case("before a closing quote", "\"है|\"", "\"है।\""),
            Case("English pipe stays", "a | b", "a | b"),
            Case("after a digit stays", "1|2", "1|2"),
            Case("between Hindi and Latin stays", "है|x", "है|x"),
        ),
        UnicodeRepair::dandasFromPipes,
    )

    @Test
    fun `a double pipe keeps the first pipe's source`() {
        val out = UnicodeRepair.dandasFromPipes(MappedText("है||", intArrayOf(5, 6, 7, 8)))
        assertEquals("है॥", out.text)
        assertArrayEquals(intArrayOf(5, 6, 7), out.sources)
    }

    private companion object {
        const val JOINERS = "\u0915\u094D\u200D\u0937 \u0915\u094D\u200C\u0937"
    }
}
