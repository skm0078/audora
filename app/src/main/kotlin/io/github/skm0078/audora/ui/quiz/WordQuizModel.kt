// File: app/src/main/kotlin/io/github/skm0078/audora/ui/quiz/WordQuizModel.kt
// Purpose: "Quiz me on 5 words" (personal touch 16): one round over a book's looked-up words, read aloud. Unlike the
//          highlight quiz it keeps no schedule: it is a quick check, asked again whenever the listener likes.

package io.github.skm0078.audora.ui.quiz

import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.quiz.QuizCard
import io.github.skm0078.audora.core.quiz.QuizRound
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The round over [cards] and its actions; the screen only draws [round]. [speak] reads a line aloud, a new line
 * superseding the one before: the word as each card comes up, the sentence it came from once it is answered.
 */
class WordQuizModel(
    cards: List<QuizCard>,
    private val scope: CoroutineScope,
    private val speak: suspend (String, Lang) -> Boolean,
) : QuizAnswers {
    private val mutableRound = MutableStateFlow(QuizRound(cards))
    val round: StateFlow<QuizRound> = mutableRound.asStateFlow()
    private var speaking: Job? = null

    fun start() {
        mutableRound.value.current?.let { say(it.spoken, it) }
    }

    override fun choose(choice: String) {
        mutableRound.value.choose(choice)?.let(::answered)
    }

    fun reveal() {
        mutableRound.value = mutableRound.value.reveal()
    }

    override fun grade(knewIt: Boolean) {
        mutableRound.value.grade(knewIt)?.let(::answered)
    }

    fun next() {
        val next = mutableRound.value.next()
        mutableRound.value = next
        next.current?.let { say(it.spoken, it) }
    }

    /** The word again while it is asked; the sentence once it is answered. */
    fun hearAgain() {
        val round = mutableRound.value
        val card = round.current ?: return
        say(if (round.verdict == null) card.spoken else card.line, card)
    }

    private fun answered(answer: QuizRound.Answer) {
        mutableRound.value = answer.round
        answer.round.current?.let { say(it.line, it) }
    }

    private fun say(text: String, card: QuizCard) {
        speaking?.cancel()
        speaking = scope.launch { speak(text, card.lang) }
    }
}
