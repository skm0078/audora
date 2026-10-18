// File: core/src/test/kotlin/io/github/skm0078/audora/core/quiz/QuizCardsTest.kt
// Purpose: Highlights become fill-in-the-blank cards: the rare word is blanked, choices share its language.

package io.github.skm0078.audora.core.quiz

import io.github.skm0078.audora.core.config.QuizConfig
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.notes.Highlight
import io.github.skm0078.audora.core.resume.ResumeAnchor
import java.text.Normalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizCardsTest {
    private val cards = QuizCards(QuizConfig(choices = 3, maxPromptChars = 40))
    private val blank = { lang: Lang -> if (lang == Lang.EN) "blank" else "रिक्त" }

    @Test
    fun `the rarest long word is blanked, shown and spoken`() {
        val built = cards.build(
            listOf(
                highlight(1, "The river carries silt to the delta."),
                highlight(2, "The delta floods every monsoon season."),
                highlight(3, "Silt makes the delta soil fertile."),
            ),
            blank,
        )

        val first = built.first { it.highlightId == 1L }
        // "river" and "carries" appear once each; the longer wins the tie. "delta" (three times) never would.
        assertEquals("carries", first.answer)
        assertTrue(first.shown.contains(QuizCard.BLANK))
        assertTrue(first.spoken.contains("blank"))
        assertTrue(first.answer !in first.shown)
    }

    @Test
    fun `choices hold the answer and same-language words from other highlights, the same every time`() {
        val highlights = listOf(
            highlight(1, "Photosynthesis needs sunlight."),
            highlight(2, "Chlorophyll absorbs light."),
            highlight(3, "Mitochondria release energy."),
            highlight(4, "किसान खेत में धान बोते हैं।"),
        )

        val card = cards.build(highlights, blank).first { it.highlightId == 1L }

        assertEquals(3, card.choices.size)
        assertTrue(card.answer in card.choices)
        assertTrue(card.choices.all { it.first().code < 0x0900 })
        assertEquals(card, cards.build(highlights, blank).first { it.highlightId == 1L })
    }

    @Test
    fun `a Hindi word keeps its vowel signs, and too few same-language words means no choices`() {
        val card = cards.build(listOf(highlight(7, "किसान खेत में धान बोते हैं।")), blank).single()

        assertEquals(Lang.HI, card.lang)
        assertEquals("किसान", card.answer)
        assertEquals(emptyList<String>(), card.choices)
        assertTrue(card.spoken.startsWith("रिक्त"))
    }

    @Test
    fun `a long highlight is cut at spaces around the blank, and one without a long word gives no card`() {
        val long = "word ".repeat(20) + "extraordinary " + "word ".repeat(20)
        val built = cards.build(listOf(highlight(1, long.trim()), highlight(2, "A cat sat.")), blank)

        val card = built.single()
        assertTrue(card.shown.startsWith("…word"))
        assertTrue(card.shown.endsWith("word…"))
        assertTrue(card.shown.length <= 40 + 2)
    }

    @Test
    fun `a Bengali word with a joiner inside is blanked whole`() {
        val rally = "\u09B0\u200D\u09CD\u09AF\u09BE\u09B2\u09BF"
        val card = cards.build(listOf(highlight(1, "$rally হবে")), blank).single()

        assertEquals(rally, card.answer)
        assertEquals("${QuizCard.BLANK} হবে", card.shown)
    }

    @Test
    fun `two Unicode spellings are one word, counted together and never a look-alike distractor`() {
        val composed = "\u09AC\u09CB\u09A8\u099F\u09BF" // বোনটি with ো as one code point
        val decomposed = "\u09AC\u09C7\u09BE\u09A8\u099F\u09BF" // the same word, ে + া
        val highlights = listOf(
            highlight(1, "$composed এসেছিল"),
            highlight(2, "$decomposed বাড়িতে"),
            highlight(3, "কলকাতায় থাকে"),
            highlight(4, "পাঠশালায় পড়ে"),
        )

        val card = cards.build(highlights, blank).first { it.highlightId == 1L }

        assertEquals("এসেছিল", card.answer) // বোনটি appears twice, so it is no longer the rarest
        assertTrue(card.choices.none { Normalizer.normalize(it, Normalizer.Form.NFC) == composed })
    }

    @Test
    fun `a repeated answer is blanked everywhere, distractors are cased like it and not from the card`() {
        val highlights = listOf(
            highlight(1, "Volcanoes erupt; volcanoes cool."),
            highlight(2, "erupt glaciers"),
            highlight(3, "meander rivers cool"),
            highlight(4, "mountains erode"),
        )

        val card = QuizCards(QuizConfig(choices = 3)).build(highlights, blank).first { it.highlightId == 1L }

        assertEquals("Volcanoes", card.answer)
        assertEquals("${QuizCard.BLANK} erupt; ${QuizCard.BLANK} cool.", card.shown)
        assertTrue(card.choices.all { it.first().isUpperCase() })
        assertTrue(card.choices.none { it.equals("erupt", ignoreCase = true) })
        assertEquals("Volcanoes erupt; volcanoes cool.", card.line)
    }

    @Test
    fun `a mixed-script token is never the blank, and a cut without spaces keeps conjuncts whole`() {
        val mixed = cards.build(listOf(highlight(1, "Rangeरंग is long and also here")), blank).single()
        assertTrue(mixed.answer != "Rangeरंग")

        val run = "\u0915\u094D\u0937".repeat(30)
        val noSpaces = "$run,अद्भुत,$run"
        val card = QuizCards(QuizConfig(choices = 3, maxPromptChars = 20))
            .build(listOf(highlight(2, noSpaces)), blank).single()
        val shown = card.shown.removePrefix("…").removeSuffix("…")
        assertTrue(!shown.startsWith("\u094D") && !shown.startsWith("\u0937"))
        assertTrue(!shown.endsWith("\u094D"))
    }

    private fun highlight(id: Long, quote: String) = Highlight(
        id = id,
        anchor = ResumeAnchor(BookId("book"), 0, 0, fingerprint = 0, tailFingerprint = 0, 1, savedAtMillis = 0),
        endPage = 0,
        endChar = quote.length,
        quote = quote,
    )
}
