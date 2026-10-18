// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/units/IndicSentenceRulesTest.kt
// Purpose: The sentence-break corrections given the breaks ICU (Android) makes, which the JDK in these tests does not.

package io.github.skm0078.audora.pdf.units

import io.github.skm0078.audora.core.config.SentenceConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class IndicSentenceRulesTest {
    private val rules = IndicSentenceRules(SentenceConfig())

    /** A text, the breaks ICU makes in it (after each of [icu], in order) and the breaks that should stay. */
    private data class Case(val name: String, val text: String, val icu: List<String>, val kept: List<String>)

    /** The offsets just after each piece, each found after the one before. */
    private fun after(text: String, pieces: List<String>): List<Int> {
        var from = 0
        return pieces.map { piece ->
            val at = text.indexOf(piece, from)
            require(at >= 0) { "\"$piece\" is not in \"$text\" after $from" }
            (at + piece.length).also { from = it }
        }
    }

    @Test
    fun `dotted words ICU breaks inside and words ICU breaks after are kept whole`() {
        val cases = listOf(
            Case("Hindi kilometre", "दूरी ५ कि.मी. है। चलिए।", listOf("कि.", "है। "), listOf("है। ")),
            Case("Hindi states", "वह उ.प्र. और म.प्र. गया। बस।", listOf("उ.", "म.", "गया। "), listOf("गया। ")),
            Case("Bengali kilometre", "দূরত্ব ৫ কি.মি. হয়। চলুন।", listOf("কি.", "হয়। "), listOf("হয়। ")),
            Case("Bengali era", "খ্রি.পূ. যুগে তৈরি। বসুন।", listOf("খ্রি.", "পূ. ", "তৈরি। "), listOf("তৈরি। ")),
            Case(
                "rupees",
                "Pay Rs. 500 (Rupees Five Hundred). Done.",
                listOf("Rs. ", "Hundred). "),
                listOf("Hundred). "),
            ),
            // A full stop inside an unlisted word followed by a capital still ends the sentence there.
            Case("unlisted", "It ends.Then more.", listOf("ends."), listOf("ends.")),
        )
        for (case in cases) {
            assertEquals(case.name, after(case.text, case.kept), rules.refine(case.text, after(case.text, case.icu)))
        }
    }
}
