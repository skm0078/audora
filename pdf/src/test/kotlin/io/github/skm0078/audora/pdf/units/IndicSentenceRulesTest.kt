// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/units/IndicSentenceRulesTest.kt
// Purpose: The sentence-break corrections given the breaks ICU (Android) makes, which the JDK in these tests does not.

package io.github.skm0078.audora.pdf.units

import io.github.skm0078.audora.core.config.ReadingUnitConfig
import io.github.skm0078.audora.core.config.SentenceConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class IndicSentenceRulesTest {
    private val rules = IndicSentenceRules(SentenceConfig(), ReadingUnitConfig().minLeaderRun)

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
            Case("Hindi kilometre", "दूरी ५ कि.मी. है। चलिए।", listOf("कि.", "मी. ", "है। "), listOf("है। ")),
            Case(
                "Hindi states",
                "वह उ.प्र. और म.प्र. गया। बस।",
                listOf("उ.", "प्र. ", "म.", "प्र. ", "गया। "),
                listOf("गया। "),
            ),
            Case("Bengali kilometre", "দূরত্ব ৫ কি.মি. হয়। চলুন।", listOf("কি.", "মি. ", "হয়। "), listOf("হয়। ")),
            Case("Bengali era", "খ্রি.পূ. যুগে তৈরি। বসুন।", listOf("খ্রি.", "পূ. ", "তৈরি। "), listOf("তৈরি। ")),
            // Punctuation stuck to the dotted word: ICU still breaks inside it.
            Case(
                "states before a comma",
                "वे उ.प्र., म.प्र. और बिहार गए। ठीक।",
                listOf("उ.", "म.", "प्र. ", "गए। "),
                listOf("गए। "),
            ),
            Case("kilometre before a danda", "दूरी ५ कि.मी.। चलिए।", listOf("कि.", "मी.। "), listOf("मी.। ")),
            Case("kilometres an hour", "गति ६० कि.मी./घंटा थी। ठीक।", listOf("कि.", "थी। "), listOf("थी। ")),
            Case(
                "Bengali before a comma",
                "দূরত্ব ৫ কি.মি., তারপর হাঁটা। বসুন।",
                listOf("কি.", "হাঁটা। "),
                listOf("হাঁটা। "),
            ),
            Case("in brackets", "दूरी (कि.मी.) में लिखें। ठीक।", listOf("कि.", "मी.) ", "लिखें। "), listOf("लिखें। ")),
            Case("in quotes", "\"कि.मी.\" लिखें। ठीक।", listOf("कि.", "मी.\" ", "लिखें। "), listOf("लिखें। ")),
            Case(
                "Hindi initials without spaces",
                "ए.पी.जे. अब्दुल कलाम और पी.एच.डी. छात्र आए। ठीक।",
                listOf("ए.", "पी.", "जे. ", "पी.", "एच.", "डी. ", "आए। "),
                listOf("आए। "),
            ),
            Case(
                "rupees",
                "Pay Rs. 500 (Rupees Five Hundred). Done.",
                listOf("Rs. ", "Hundred). "),
                listOf("Hundred). "),
            ),
            Case("rupee", "Pay Re. 1 (One) only. Done.", listOf("Re. ", "only. "), listOf("only. ")),
            Case(
                "offices",
                "Sri. Ram Rao spoke. The Jt. Secretary and the Spl. Officer came. Done.",
                listOf("Sri. ", "spoke. ", "Jt. ", "Spl. ", "came. "),
                listOf("spoke. ", "came. "),
            ),
            Case(
                "initials before a comma",
                "Written by S. K., the author. Done.",
                listOf("S. ", "author. "),
                listOf("author. "),
            ),
            // A full stop inside an unlisted word still ends the sentence there (ICU breaks "है.|अगला").
            Case("unlisted", "यह है.अगला वाक्य।", listOf("है."), listOf("है.")),
        )
        for (case in cases) {
            assertEquals(case.name, after(case.text, case.kept), rules.refine(case.text, after(case.text, case.icu)))
        }
    }

    @Test
    fun `a contents line stays one sentence where ICU breaks before its page numbers`() {
        val cases = listOf(
            // ICU breaks between a dot leader and its page number; the contents line stays one sentence.
            Case(
                "contents",
                "Introduction ........ 5 Methods . . . . . . 9 भूमिका ..... ७",
                listOf("........ ", ". . . . . . ", "..... "),
                emptyList(),
            ),
            // An ellipsis before a number is no leader: the sentence ends there.
            Case("ellipsis", "Wait... 5 more came.", listOf("Wait... "), listOf("Wait... ")),
        )
        for (case in cases) {
            assertEquals(case.name, after(case.text, case.kept), rules.refine(case.text, after(case.text, case.icu)))
        }
    }
}
