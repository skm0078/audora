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
        val (text, terms) = UnitText.of(raw.toString(), MIN_LETTERS) { bold[it] }
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
        val (text, terms) = UnitText.of("a  bold\tword", MIN_LETTERS) { it in 3..6 }
        assertEquals("a bold word", text)
        assertEquals(listOf(TermSpan(2, 6)), terms)
    }

    @Test
    fun `the text is exactly what splitting on whitespace and joining with spaces gives`() {
        val raws = listOf(
            "", " ", "\n\n", "one", " lead", "trail ", "a b", "tab\there", "vt\u000Bff\u000Cend", "x\r\ny",
            "  many   spaces  here  ", " em space", "नमस्ते  दुनिया",
        )
        for (raw in raws) {
            val expected = raw.split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")
            assertEquals(raw, expected, UnitText.of(raw, MIN_LETTERS) { false }.first)
        }
    }

    private companion object {
        const val MIN_LETTERS = 2
    }
}
