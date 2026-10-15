// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/text/TextChunkerTest.kt
// Purpose: Table-driven cases for cutting text to an engine's input limit at natural boundaries.

package io.github.skm0078.audora.speech.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextChunkerTest {
    private data class Case(val name: String, val text: String, val max: Int, val expected: List<String>)

    private val cases = listOf(
        Case("fits", "Short sentence.", 40, listOf("Short sentence.")),
        Case("trimmed", "  padded  ", 40, listOf("padded")),
        Case("blank", "   ", 40, emptyList()),
        Case("empty", "", 40, emptyList()),
        Case("exactly the limit", "abcde", 5, listOf("abcde")),
        Case(
            "sentence end wins over later comma",
            "One two three. Four five, six seven eight",
            26,
            listOf("One two three.", "Four five, six seven eight"),
        ),
        Case(
            "clause mark when no sentence end",
            "alpha beta gamma, delta epsilon",
            20,
            listOf("alpha beta gamma,", "delta epsilon"),
        ),
        Case("space when no punctuation", "alpha beta gamma delta", 12, listOf("alpha beta", "gamma delta")),
        Case("hard cut without spaces", "abcdefghij", 4, listOf("abcd", "efgh", "ij")),
        Case(
            "early sentence end is ignored for a later space (no tiny chunks)",
            "Hi. alpha beta gamma delta",
            20,
            listOf("Hi. alpha beta gamma", "delta"),
        ),
        Case(
            "decimal point is not a sentence end",
            "Pi is 3.14159 roughly ok",
            12,
            listOf("Pi is", "3.14159", "roughly ok"),
        ),
        Case("Devanagari danda", "यह पहला वाक्य है। यह दूसरा है", 20, listOf("यह पहला वाक्य है।", "यह दूसरा है")),
        Case("surrogate pair never split", "ab😀cd", 3, listOf("ab", "😀c", "d")),
        Case("limit of one", "ab", 1, listOf("a", "b")),
    )

    @Test
    fun `chunks at the most natural boundary that fits`() {
        for (case in cases) {
            assertEquals(case.name, case.expected, TextChunker.chunk(case.text, case.max))
        }
    }

    @Test
    fun `every chunk fits and no letters are lost`() {
        val text = "The quick brown fox, which was very quick indeed, jumped over the lazy dog. ".repeat(40)
        for (max in listOf(7, 25, 64, 400, 4_000)) {
            val chunks = TextChunker.chunk(text, max)
            assertTrue("max $max", chunks.all { it.length <= max && it.isNotBlank() })
            assertEquals(
                "max $max",
                text.filterNot(Char::isWhitespace),
                chunks.joinToString("").filterNot(Char::isWhitespace),
            )
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a limit of zero is rejected`() {
        TextChunker.chunk("text", 0)
    }
}
