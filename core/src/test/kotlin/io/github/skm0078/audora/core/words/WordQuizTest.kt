// File: core/src/test/kotlin/io/github/skm0078/audora/core/words/WordQuizTest.kt
// Purpose: "Quiz me on 5 words": the newest five, each with its meaning among three others of its language.

package io.github.skm0078.audora.core.words

import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.model.Lang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WordQuizTest {
    private val book = BookId("udhr")

    private fun word(text: String, at: Long, lang: Lang = Lang.EN, book: BookId = this.book) =
        LookedUpWord(book, text, "meaning of $text", 3, "A sentence with $text.", lang, at)

    private val six = (6 downTo 1).map { word("w$it", it.toLong()) }

    @Test
    fun `the newest five words, each answered by its meaning`() {
        val cards = WordQuiz.cards(six)

        assertEquals(listOf("w6", "w5", "w4", "w3", "w2"), cards.map { it.shown })
        cards.forEach { card ->
            assertEquals(4, card.choices.size)
            assertTrue(card.answer in card.choices)
            assertEquals(4, card.choices.distinct().size)
        }
        assertEquals("A sentence with w6.", cards.first().line)
    }

    @Test
    fun `the same words always give the same cards`() {
        assertEquals(WordQuiz.cards(six), WordQuiz.cards(six))
    }

    @Test
    fun `choices are of the word's own language`() {
        val mixed = six + word("कर्तव्य", 0, Lang.HI)

        val hindi = WordQuiz.cards(listOf(mixed.last()), pool = mixed).single()

        assertEquals(emptyList<String>(), hindi.choices)
    }

    @Test
    fun `other books fill in the choices when this book has too few words`() {
        val two = listOf(word("a", 2), word("b", 1))
        val elsewhere = (1..3).map { word("x$it", 0, book = BookId("other")) }

        val cards = WordQuiz.cards(two, pool = two + elsewhere)

        assertEquals(4, cards.first().choices.size)
        assertTrue("meaning of b" in cards.first().choices)
    }

    @Test
    fun `with too few meanings a card is answered by showing it`() {
        assertEquals(emptyList<String>(), WordQuiz.cards(listOf(word("a", 1))).single().choices)
    }
}
