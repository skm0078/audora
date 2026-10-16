// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/route/ScriptRouterTest.kt
// Purpose: Which voice reads each part of a mixed-script sentence (P3-01 rules, ADR-026), table-driven.

package io.github.skm0078.audora.speech.route

import io.github.skm0078.audora.core.config.ScriptRoutingConfig
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.Lang.BN
import io.github.skm0078.audora.core.model.Lang.EN
import io.github.skm0078.audora.core.model.Lang.HI
import org.junit.Assert.assertEquals
import org.junit.Test

class ScriptRouterTest {
    private val router = ScriptRouter()

    private class Case(val text: String, val fallback: Lang, vararg val expected: Pair<String, Lang>)

    @Test
    fun `table of sentences`() {
        val cases = listOf(
            Case("Hello world.", HI, "Hello world." to EN),
            Case("मानव अधिकारों की घोषणा ।", EN, "मानव अधिकारों की घोषणा ।" to HI),
            Case("মানবাধিকার সবার জন্য।", EN, "মানবাধিকার সবার জন্য।" to BN),
            // From the bilingual UDHR test PDF: an English clause runs into a Hindi sentence without a full stop.
            Case(
                "Whereas recognition of the inherent dignity is the foundation of peace in the world, " +
                    "इसी घोषणा का सरकारी पाठ प्राप्य है ।",
                EN,
                "Whereas recognition of the inherent dignity is the foundation of peace in the world," to EN,
                "इसी घोषणा का सरकारी पाठ प्राप्य है ।" to HI,
            ),
            // A heading pair: three English words split out of a mostly Hindi line.
            Case(
                "Universal Declaration of Human Rights मानव अधिकारों की सार्वभौम घोषणा और प्रस्तावना",
                EN,
                "Universal Declaration of Human Rights" to EN,
                "मानव अधिकारों की सार्वभौम घोषणा और प्रस्तावना" to HI,
            ),
            // Fewer than three English words inside Hindi stay with the Hindi voice.
            Case("यह PDF फ़ाइल है ।", HI, "यह PDF फ़ाइल है ।" to HI),
            Case("Article 12 के अनुसार हस्तक्षेप नहीं होगा ।", HI, "Article 12 के अनुसार हस्तक्षेप नहीं होगा ।" to HI),
            Case("UN और WHO ने कहा ।", EN, "UN और WHO ने कहा ।" to HI),
            // ...but three or more get the English voice.
            Case(
                "उन्होंने कहा the rule of law और चले गए ।",
                HI,
                "उन्होंने कहा" to HI,
                "the rule of law" to EN,
                "और चले गए ।" to HI,
            ),
            // Indic text inside an English sentence always switches, even one word.
            Case(
                "The word नमस्ते means hello.",
                EN,
                "The word" to EN,
                "नमस्ते" to HI,
                "means hello." to EN,
            ),
            Case(
                "Bengali for water is জল, she said.",
                EN,
                "Bengali for water is" to EN,
                "জল," to BN,
                "she said." to EN,
            ),
            // Hindi and Bengali in one sentence.
            Case("पानी और জল", EN, "पानी और" to HI, "জল" to BN),
            // An opening quote travels with the words it opens, a closing one with the words it closes.
            Case(
                "He wrote \"मानव अधिकार\" in 1948 and left.",
                EN,
                "He wrote" to EN,
                "\"मानव अधिकार\"" to HI,
                "in 1948 and left." to EN,
            ),
            // Devanagari digits count as Hindi; leading punctuation joins the first run.
            Case("(१९४८) Paris", EN, "(१९४८)" to HI, "Paris" to EN),
            // No letters at all: the sentence's own language.
            Case("1948 — 2024", HI, "1948 — 2024" to HI),
            Case("   ", EN),
        )
        cases.forEach { case ->
            val expected = case.expected.map { (text, lang) -> ScriptSegment(text, lang) }
            assertEquals(case.text, expected, router.route(case.text, case.fallback))
        }
    }

    @Test
    fun `mostly English sentence keeps a short English run`() {
        // English-dominant, so "of the" is not handed to the Hindi voice although it is under three words.
        val segments = router.route("The rights of the मनुष्य and of the nations", EN)

        assertEquals(listOf(EN, HI, EN), segments.map { it.lang })
    }

    @Test
    fun `threshold is configurable`() {
        val strict = ScriptRouter(ScriptRoutingConfig(minEnglishWordsInIndic = 1))

        assertEquals(
            listOf(ScriptSegment("यह", HI), ScriptSegment("PDF", EN), ScriptSegment("फ़ाइल है ।", HI)),
            strict.route("यह PDF फ़ाइल है ।", HI),
        )
    }

    @Test
    fun `parts always add up to the sentence`() {
        val text = "Universal Declaration मानव अधिकार, the rule of law (१० दिसम्बर) and \"জল\" ok."
        val joined = router.route(text, EN).joinToString("") { it.text }

        assertEquals(text.filterNot(Char::isWhitespace), joined.filterNot(Char::isWhitespace))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero threshold is rejected`() {
        ScriptRoutingConfig(minEnglishWordsInIndic = 0)
    }
}
