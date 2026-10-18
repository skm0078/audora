// File: core/src/test/kotlin/io/github/skm0078/audora/core/quiz/QuizRoundTest.kt
// Purpose: A quiz moves from card to card only after each is answered, and counts the right ones.

package io.github.skm0078.audora.core.quiz

import io.github.skm0078.audora.core.model.Lang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizRoundTest {
    private val withChoices = QuizCard(1, "a _____", "a blank", "cat", listOf("dog", "cat"), Lang.EN)
    private val plain = QuizCard(2, "b _____", "b blank", "sun", emptyList(), Lang.EN)

    @Test
    fun `a picked choice is graded once, then next moves on`() {
        val round = QuizRound(listOf(withChoices, plain))

        assertEquals(round, round.next())
        val answer = round.choose("dog")!!
        assertEquals(false, answer.right)
        assertEquals("dog", answer.round.picked)
        assertNull(answer.round.choose("cat"))
        assertEquals(plain, answer.round.next().current)
    }

    @Test
    fun `a card without choices is revealed, then graded by the listener, and the score counts it`() {
        val round = QuizRound(listOf(plain))

        assertNull(round.grade(knewIt = true))
        assertNull(round.choose("sun"))
        val answer = round.reveal().grade(knewIt = true)!!
        val done = answer.round.next()

        assertTrue(answer.right)
        assertTrue(done.finished)
        assertEquals(1, done.right)
        assertNull(done.current)
    }
}
