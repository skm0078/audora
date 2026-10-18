// File: core/src/test/kotlin/io/github/skm0078/audora/core/quiz/QuizCardsTest.kt
// Purpose: Highlights become fill-in-the-blank cards: the rare word is blanked, choices share its language.

package io.github.skm0078.audora.core.quiz

import io.github.skm0078.audora.core.config.QuizConfig
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.notes.Highlight
import io.github.skm0078.audora.core.resume.ResumeAnchor
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

    private fun highlight(id: Long, quote: String) = Highlight(
        id = id,
        anchor = ResumeAnchor(BookId("book"), 0, 0, fingerprint = 0, tailFingerprint = 0, 1, savedAtMillis = 0),
        endPage = 0,
        endChar = quote.length,
        quote = quote,
    )
}
