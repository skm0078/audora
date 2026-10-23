// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/quality/SpeakabilityTest.kt
// Purpose: The speakability signals, the blend, the word reader and the letter model, on small hand-made lists.

package io.github.skm0078.audora.pdf.quality

import io.github.skm0078.audora.core.config.Signal
import io.github.skm0078.audora.core.config.SignalRange
import io.github.skm0078.audora.core.config.SpeakabilityConfig
import io.github.skm0078.audora.core.model.Lang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeakabilityTest {
    private val english = LanguageModel(
        listOf(
            "the", "cat", "sat", "on", "mat", "and", "dog", "ran", "to", "a", "house", "in", "town", "there", "was",
            "an", "old", "man", "who", "lived", "near", "river", "with", "his", "daughter", "don't", "know",
        ).associateWith { 50 },
    )
    private val hindi =
        LanguageModel(listOf("भारत", "एक", "देश", "है", "और", "यह", "बड़ा", "में", "लोग", "रहते").associateWith { 50 })
    private val models = WordModels { lang -> mapOf(Lang.EN to english, Lang.HI to hindi)[lang] }
    private val scorer = SpeakabilityScorer(SpeakabilityConfig(), models)

    @Test
    fun `words are letter runs, lower-cased, joiners dropped, inner apostrophes kept`() {
        listOf(
            "The cat, sat." to listOf("the", "cat", "sat"),
            "Don’t stop; rock'n" to listOf("don't", "stop", "rock'n"),
            "'quoted' word" to listOf("quoted", "word"),
            "T2 and R1-x" to listOf("t", "and", "r", "x"),
            "क्‍ष और ক‌া" to listOf("क्ष", "और", "কা"),
            "भारत। देश॥" to listOf("भारत", "देश"),
            "|| ;: =~ 12" to emptyList(),
        ).forEach { (text, words) -> assertEquals(text, words, Words.of(text)) }
    }

    @Test
    fun `a word's script is null when it mixes scripts`() {
        listOf(
            "भारत" to Character.UnicodeScript.DEVANAGARI,
            "भारतl" to null,
            "cat" to Character.UnicodeScript.LATIN,
            "αβ" to Character.UnicodeScript.GREEK,
        ).forEach { (word, script) -> assertEquals(word, script, Words.scriptOf(word)) }
        assertEquals(Lang.HI, Words.langOf("भारत"))
        assertNull(Words.langOf("αβ"))
    }

    @Test
    fun `the letter model scores real spelling above noise`() {
        listOf("cat", "mat", "near").forEach { assertTrue(it, english.letterScore(it) > 0.6f) }
        listOf("xqzv", "zzkq", "qqqq").forEach { assertTrue(it, english.letterScore(it) < 0.3f) }
        // Unseen words spelled like the list's still score well.
        assertTrue(english.letterScore("there") > english.letterScore("thxqe"))
    }

    @Test
    fun `signals per kind of line`() {
        data class Case(
            val text: String,
            val dictionary: Float?,
            val letters: Boolean,
            val soup: Float,
            val script: Float?,
        )
        listOf(
            Case("The old man lived near the river.", 1f, true, 1f, 1f),
            Case("Trqqrd sfrsmq vkxqz pqqtr.", 0f, true, 1f, 1f),
            Case("|| ;: =~ 1l", null, false, 2f / 8, null),
            Case("The cat", null, true, 1f, 1f),
            Case("भारत एक बड़ा देश है।", 1f, true, 1f, 1f),
            Case("PTfuqT rirrdE (2.1) —", null, true, 5f / 17, 1f),
            Case("6०08 क07 8प्रो", null, true, 0f, 1f),
            Case("αβγ δεζ ηθι", null, false, 1f, 1f),
        ).forEach { case ->
            val signals = scorer.signals(case.text)
            assertEquals("${case.text} dictionary", case.dictionary, signals.dictionary)
            assertEquals("${case.text} letters", case.letters, signals.letterModel != null)
            assertEquals("${case.text} soup", case.soup, signals.symbolSoup, 1e-4f)
            assertEquals("${case.text} script", case.script, signals.scriptMix)
            assertNull(signals.ocrConfidence)
        }
        // Length-weighted: the unknown long word outweighs the two short known ones.
        assertEquals(6f / 13, scorer.signals("the cat xqzvbkw").dictionary!!, 1e-6f)
    }

    @Test
    fun `with the bundled lists real sentences pass and junk fails the default gate`() {
        val bundled = SpeakabilityScorer()
        val skipBelow = SpeakabilityConfig().skipBelow
        listOf(
            "The old man lived near the river with his daughter.",
            "भारत एक बड़ा देश है और यहाँ लोग रहते हैं।",
            "সকল মানুষ স্বাধীনভাবে সমান মর্যাদা এবং অধিকার নিয়ে জন্মগ্রহণ করে।",
            "Fig. 2",
            "1867",
            "3.",
        ).forEach { assertTrue(it, bundled.score(it) >= skipBelow) }
        listOf(
            "Trqqrd sfrsmq vkxqz pqqtr.",
            "|| ;: =~ 1l",
            "~~ ## %% ^^ ** {{",
            "rirrd{ ol ffftfuqi, Ee o-d-q",
        ).forEach { assertTrue(it, bundled.score(it) < skipBelow) }
        // An English page read with the Hindi model: Devanagari that looks plausible, very low OCR confidence.
        assertTrue(bundled.score("क07 8७6 ४७ शे.", ocrConfidence = 0.2f) < skipBelow)
        // A list number OCR read with a low confidence is still read.
        assertTrue(bundled.score("4.", ocrConfidence = 0.3f) >= skipBelow)
    }

    @Test
    fun `low OCR confidence lowers the score and abstaining signals are re-weighted`() {
        val text = "The old man lived near the river."
        assertTrue(scorer.score(text, ocrConfidence = 0.2f) < scorer.score(text, ocrConfidence = 0.95f))
        val only = SpeakabilityScorer(
            SpeakabilityConfig(
                ocrConfidence = Signal(1f, SignalRange(0f, 1f)),
                dictionary = OFF,
                letterModel = OFF,
                symbolSoup = OFF,
                scriptMix = OFF,
            ),
            models,
        )
        assertEquals(0.3f, only.score(text, ocrConfidence = 0.3f), 1e-6f)
        // Every present signal switched off: nothing to judge by, so the sentence is read.
        assertEquals(1f, only.score(text), 1e-6f)
    }

    @Test
    fun `without a word list the score uses the other signals`() {
        val bare = SpeakabilityScorer(SpeakabilityConfig(), WordModels { null })
        val signals = bare.signals("The old man lived near the river.")
        assertNull(signals.dictionary)
        assertNull(signals.letterModel)
        assertTrue(bare.score("The old man lived near the river.") >= SpeakabilityConfig().skipBelow)
        assertTrue(bare.score("~~ ## %% ^^ ** {{") < SpeakabilityConfig().skipBelow)
    }

    @Test
    fun `bundled list format`() {
        val parsed = BundledWordModels.parse(
            listOf("# audora-words 1 en wordfreq 3.1.1 CC-BY-SA-4.0", "the\t78", "cat\t52", "bad"),
        )
        assertEquals(mapOf("the" to 78, "cat" to 52), parsed)
        assertNull(BundledWordModels.parse(listOf("the\t78")))
    }

    @Test
    fun `bundled lists load for every language`() {
        Lang.entries.forEach { lang ->
            val model = BundledWordModels.of(lang)
            assertTrue("$lang has no bundled word list", model != null && model.words.size > MIN_BUNDLED_WORDS)
        }
        assertTrue("the" in BundledWordModels.of(Lang.EN)!!)
        assertTrue("है" in BundledWordModels.of(Lang.HI)!!)
        assertTrue("এবং" in BundledWordModels.of(Lang.BN)!!)
    }

    private companion object {
        val OFF = Signal(0f, SignalRange(0f, 1f))
        const val MIN_BUNDLED_WORDS = 20_000
    }
}
