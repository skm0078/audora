// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/units/UnitTextTest.kt
// Purpose: A unit's text with whitespace collapsed, and its bold terms as spans of that text.

package io.github.skm0078.audora.pdf.units

import io.github.skm0078.audora.core.model.TermSpan
import org.junit.Assert.assertEquals
import org.junit.Test

class UnitTextTest {
    /** [marked] with bold written in square brackets: "The [Krebs cycle] makes ATP." */
    private fun read(marked: String): Pair<String, List<String>> {
        val raw = StringBuilder()
        val bold = ArrayList<Boolean>()
        var inBold = false
        for (c in marked) {
            when (c) {
                '[' -> inBold = true

                ']' -> inBold = false

                else -> {
                    raw.append(c)
                    bold.add(inBold)
                }
            }
        }
        val (text, terms) = UnitText.of(raw.toString(), MIN_LETTERS, LEADER_RUN) { bold[it] }
        return text to terms.map { text.substring(it.start, it.end) }
    }

    @Test
    fun `bold words become spans of the collapsed text`() {
        val cases = listOf(
            "plain  text\nhere" to ("plain text here" to emptyList()),
            "The [Krebs cycle] makes ATP." to ("The Krebs cycle makes ATP." to listOf("Krebs cycle")),
            "The [Krebs]\n  [cycle] makes" to ("The Krebs cycle makes" to listOf("Krebs cycle")),
            "[Note:] read this" to ("Note: read this" to listOf("Note")),
            "an [osmosis], a [diffusion]." to ("an osmosis, a diffusion." to listOf("osmosis", "diffusion")),
            "[(a)] item" to ("(a) item" to emptyList()),
            "  [ ] spaces[ ]only [—] " to ("spaces only —" to emptyList()),
            "[All of it bold.]" to ("All of it bold." to listOf("All of it bold")),
            "जल को [पानी] कहते हैं।" to ("जल को पानी कहते हैं।" to listOf("पानी")),
            "[প্রকাশ সংশ্লেষণ] হলো" to ("প্রকাশ সংশ্লেষণ হলো" to listOf("প্রকাশ সংশ্লেষণ")),
            "[3.5]%" to ("3.5%" to emptyList()),
            "page [z] here" to ("page z here" to emptyList()),
            "[e c] spaced, [f f i d]" to ("e c spaced, f f i d" to emptyList()),
            "[Layer5] and [को]" to ("Layer5 and को" to listOf("Layer5", "को")),
        )
        for ((marked, expected) in cases) assertEquals(marked, expected, read(marked))
    }

    @Test
    fun `spans are offsets into the text, never empty`() {
        val (text, terms) = UnitText.of("a  bold\tword", MIN_LETTERS, LEADER_RUN) { it in 3..6 }
        assertEquals("a bold word", text)
        assertEquals(listOf(TermSpan(2, 6)), terms)
    }

    @Test
    fun `the text is exactly what splitting on Unicode whitespace and joining with spaces gives`() {
        val raws = listOf(
            "", " ", "\n\n", "one", " lead", "trail ", "a b", "tab\there", "vt\u000Bff\u000Cend", "x\r\ny",
            "  many   spaces  here  ", "em\u2003space", "नमस्ते  दुनिया", "no\u00A0break", "₹\u202F500",
            "thin\u2009space", "line\u2028para\u2029end", "next\u0085line", "ideo\u3000graphic",
            "zero\u200Bwidth stays",
        )
        for (raw in raws) {
            // Android's \s, and the JVM's with Unicode classes: White_Space.
            val expected = raw.split(Regex("(?U)\\s+")).filter { it.isNotEmpty() }.joinToString(" ")
            assertEquals(raw, expected, UnitText.of(raw, MIN_LETTERS, LEADER_RUN) { false }.first)
        }
    }

    @Test
    fun `dot leaders, blanks and rules read as one ellipsis, or nothing at the end, an ellipsis stays`() {
        val cases = listOf(
            "Introduction ........ 5" to "Introduction … 5",
            "भूमिका ............. ७" to "भूमिका … ७",
            "Name: ______ Date: ____________" to "Name: … Date:",
            "Chapter 2 . . . . . . . 19" to "Chapter 2 … 19",
            "Total ······ 309" to "Total … 309",
            "Accidents,------------Asthma, 2" to "Accidents,…Asthma, 2",
            // A blank keeps its place, with no space before the punctuation after it.
            "भारत की राजधानी ______ है।" to "भारत की राजधानी … है।",
            "The answer is ______." to "The answer is ….",
            "-----------" to "",
            // Shorter runs are punctuation: an ellipsis, one with the sentence's stop, a dash, a blank of four.
            "Wait... then go" to "Wait... then go",
            "Wait.... then go" to "Wait.... then go",
            "and so on…… next" to "and so on…… next",
            "up--down and a—b" to "up--down and a—b",
            "Name ____" to "Name ____",
            "well-known snake_case" to "well-known snake_case",
        )
        for ((raw, expected) in cases) {
            assertEquals(raw, expected, UnitText.of(raw, MIN_LETTERS, LEADER_RUN) { false }.first)
        }
    }

    private companion object {
        const val MIN_LETTERS = 2
        const val LEADER_RUN = 5
    }
}
