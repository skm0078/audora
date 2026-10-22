// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/normalize/PronunciationFixerTest.kt
// Purpose: Pronunciation fixes, table-driven: whole words, case, longest first, Indic scripts (plan item 1).

package io.github.skm0078.audora.speech.normalize

import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.notes.PronunciationRule
import org.junit.Assert.assertEquals
import org.junit.Test

class PronunciationFixerTest {
    private fun rule(written: String, spoken: String, whole: Boolean = true, lang: Lang = Lang.EN) =
        PronunciationRule(0, null, lang, written, spoken, whole)

    private val fixer = PronunciationFixer(
        listOf(
            rule("US", "you ess"),
            rule("York", "Yorrk"),
            rule("New York", "Noo Yorrk"),
            rule("ing", "ING", whole = false),
            rule("गुरु", "गुरू", lang = Lang.HI),
            rule("C++", "see plus plus"),
        ),
    )

    @Test
    fun `fixes apply as listed`() {
        val cases = listOf(
            "The US and USB." to "The you ess and USB.",
            "the us army" to "the you ess army",
            "New York, then York." to "Noo Yorrk, then Yorrk.",
            "Reading" to "ReadING",
            "मेरे गुरु जी" to "मेरे गुरू जी",
            "गुरुकुल" to "गुरुकुल", // part of a longer word: left alone
            "I like C++ a lot" to "I like see plus plus a lot",
            "Nothing here" to "Nothing here".replace("ing", "ING"),
        )
        cases.forEach { (input, expected) -> assertEquals(input, expected, fixer.apply(input)) }
    }

    @Test
    fun `no rules changes nothing`() {
        val none = PronunciationFixer(emptyList())
        assertEquals(true, none.isEmpty)
        assertEquals("As is.", none.apply("As is."))
    }
}
