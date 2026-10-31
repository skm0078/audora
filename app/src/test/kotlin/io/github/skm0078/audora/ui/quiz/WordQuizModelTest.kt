// File: app/src/test/kotlin/io/github/skm0078/audora/ui/quiz/WordQuizModelTest.kt
// Purpose: The word quiz reads each word as it comes up and its sentence once answered, and keeps the score.

package io.github.skm0078.audora.ui.quiz

import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.words.LookedUpWord
import io.github.skm0078.audora.core.words.WordQuiz
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WordQuizModelTest {
    private val said = mutableListOf<String>()
    private val words = (5 downTo 1).map {
        LookedUpWord(BookId("b"), "w$it", "meaning $it", 1, "Sentence $it.", Lang.EN, it.toLong())
    }

    @Test
    fun `each word is read as it comes up and its sentence once answered`() = runTest(UnconfinedTestDispatcher()) {
        val model = WordQuizModel(WordQuiz.cards(words), backgroundScope) { text, _ -> said.add(text) }

        model.start()
        model.choose("meaning 5")
        model.next()

        assertEquals(listOf("w5", "Sentence 5.", "w4"), said)
        assertEquals(1, model.round.value.right)
    }

    @Test
    fun `a wrong meaning is marked wrong and the round goes on to the score`() = runTest(UnconfinedTestDispatcher()) {
        val model = WordQuizModel(WordQuiz.cards(words), backgroundScope) { _, _ -> true }

        repeat(5) {
            val card = requireNotNull(model.round.value.current)
            model.choose(card.choices.first { it != card.answer })
            model.next()
        }

        assertTrue(model.round.value.finished)
        assertEquals(0, model.round.value.right)
    }
}
