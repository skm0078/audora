// File: core/src/main/kotlin/io/github/skm0078/audora/core/quiz/QuizRound.kt
// Purpose: One quiz from first card to score, as immutable states the screen shows (plan item 70).

package io.github.skm0078.audora.core.quiz

/**
 * Where a quiz is. A card with choices is answered by picking one ([choose]); one without is revealed and then
 * graded by the listener ([reveal], [grade]). Either way [verdict] is set until [next] moves on. Each answer is
 * returned once as an [Answer], for the schedule.
 */
data class QuizRound(
    val cards: List<QuizCard>,
    val position: Int = 0,
    val picked: String? = null,
    val revealed: Boolean = false,
    val verdict: Boolean? = null,
    val right: Int = 0,
) {
    /** The card being asked, or null once the quiz is over. */
    val current: QuizCard? get() = cards.getOrNull(position)

    val finished: Boolean get() = position >= cards.size

    /** One graded card. */
    data class Answer(val round: QuizRound, val cardId: Long, val right: Boolean)

    /** Picks [choice] on a card with choices; null if this card is already answered or has no choices. */
    fun choose(choice: String): Answer? {
        val card = current?.takeIf { verdict == null && it.choices.isNotEmpty() } ?: return null
        return graded(card, choice == card.answer).let { answer ->
            answer.copy(round = answer.round.copy(picked = choice))
        }
    }

    /** Shows the answer of a card without choices. */
    fun reveal(): QuizRound = if (current != null && verdict == null) copy(revealed = true) else this

    /** The listener's own verdict on a revealed card; null unless one is waiting. */
    fun grade(knewIt: Boolean): Answer? {
        val card = current?.takeIf { revealed && verdict == null } ?: return null
        return graded(card, knewIt)
    }

    /** The next card, once this one has a verdict. */
    fun next(): QuizRound =
        if (verdict == null) this else copy(position = position + 1, picked = null, revealed = false, verdict = null)

    private fun graded(card: QuizCard, isRight: Boolean) = Answer(
        round = copy(verdict = isRight, revealed = true, right = right + if (isRight) 1 else 0),
        cardId = card.highlightId,
        right = isRight,
    )
}
