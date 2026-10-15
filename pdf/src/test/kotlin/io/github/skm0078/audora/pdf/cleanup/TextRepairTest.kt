// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/cleanup/TextRepairTest.kt
// Purpose: Table-driven tests for paragraph repair: hyphens, ligatures, junk characters, bullets, citations.

package io.github.skm0078.audora.pdf.cleanup

import io.github.skm0078.audora.core.config.CleanupConfig
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class TextRepairTest {
    private fun repair(text: String, lexicon: Set<String> = emptySet(), config: CleanupConfig = CleanupConfig()) =
        TextRepair(config).repair(MappedText(text, IntArray(text.length) { it }), lexicon)

    private data class Case(
        val name: String,
        val input: String,
        val expected: String,
        val lexicon: Set<String> = emptySet(),
    )

    private fun check(cases: List<Case>) = cases.forEach { case ->
        assertEquals(case.name, case.expected, repair(case.input, case.lexicon).text)
    }

    @Test
    fun `line-end hyphens are joined or kept`() = check(
        listOf(
            Case("hyphenation point", "nega\u0002tion", "negation"),
            Case("capital first part only at its start", "Oth\u0002ers", "Others"),
            Case("compound printed elsewhere nearby", "block\u0002pulse", "block-pulse", setOf("block-pulse")),
            Case("lexicon ignores case", "Block\u0002pulse", "Block-pulse", setOf("block-pulse")),
            Case("second part capitalised", "Cape\u0002Horner", "Cape-Horner"),
            Case("acronym in the first part", "qMRI\u0002maps", "qMRI-maps"),
            Case(
                "Devanagari: vowel sign before the mark",
                "\u0938\u0902\u0930\u0915\u094D\u0937\u093F\u0002\u0924",
                "\u0938\u0902\u0930\u0915\u094D\u0937\u093F\u0924",
            ),
            Case("unmapped glyph between spaces", "x = \u0002 k", "x =  k"),
            Case("soft hyphen", "malig\u00ADnancy", "malignancy"),
            Case("real hyphen at a line end", "well\u2010\nbeing", "well\u2010being"),
            Case("double hyphen at a line end", "me--\nTHAT", "me--THAT"),
            Case("em dash glued to a word", "through\u2014\nit", "through\u2014it"),
            Case("spaced dash keeps the break", "word \u2013\nnext", "word \u2013\nnext"),
            Case("ordinary line break", "one\ntwo", "one\ntwo"),
        ),
    )

    @Test
    fun `junk characters fold and ligatures expand`() = check(
        listOf(
            Case("ligatures", "\uFB01nd the \uFB02ow, e\uFB00ort, o\uFB03ce", "find the flow, effort, office"),
            Case("zero-width space, word joiner, BOM", "zero\u200Bwidth\u2060 \uFEFFtext", "zerowidth text"),
            Case("control characters from unmapped glyphs", "ab\u0006c\u001Bd", "abcd"),
            Case("tabs and line breaks stay", "a\tb\r\nc", "a\tb\r\nc"),
            Case("ZWNJ stays (Bengali)", "\u0995\u09CD\u200C\u09B7", "\u0995\u09CD\u200C\u09B7"),
            Case("ZWJ stays (Devanagari)", "\u0915\u094D\u200D\u0937", "\u0915\u094D\u200D\u0937"),
        ),
    )

    @Test
    fun `bullets at the start of a line go`() = check(
        listOf(
            Case("bullet and its space", "\u2022 first\n\u25C6 second", "first\nsecond"),
            Case("Symbol-font bullet", "\uF0B7 item", "item"),
            Case("bullet inside a line stays", "a \u2022 b", "a \u2022 b"),
        ),
    )

    @Test
    fun `numeric citation markers go with the space before them`() = check(
        listOf(
            Case("one", "after six to 12 months [1].", "after six to 12 months."),
            Case("range", "been suggested [4\u20137]. Another", "been suggested. Another"),
            Case("list", "staining [17,18]. A major", "staining. A major"),
            Case("after a line break", "oedema\n[21,22]. Recent", "oedema. Recent"),
            Case("after closing punctuation", "(\u2018Gliomas\u2019) [3].", "(\u2018Gliomas\u2019)."),
            Case("interval with a zero stays", "on the interval [0, 1].", "on the interval [0, 1]."),
            Case("after a symbol stays", "x \u2208 [1, 2]", "x \u2208 [1, 2]"),
            Case("author-year citations stay", "(Kot and Schaffer 1986)", "(Kot and Schaffer 1986)"),
        ),
    )

    @Test
    fun `citation markers stay when the setting is off`() {
        val text = "after six to 12 months [1]."
        assertEquals(text, repair(text, config = CleanupConfig(skipCitationMarkers = false)).text)
    }

    @Test
    fun `every repaired character maps back to an ascending source offset`() {
        val repaired = repair("\uFB01ne nega\u0002tion\u200B [2].")
        assertEquals("fine negation.", repaired.text)
        // "fi" both come from the ligature at 0; the hyphen marker (8), the ZWSP (13) and " [2]" (14-17) are gone.
        assertArrayEquals(intArrayOf(0, 0, 1, 2, 3, 4, 5, 6, 7, 9, 10, 11, 12, 18), repaired.sources)
    }
}
