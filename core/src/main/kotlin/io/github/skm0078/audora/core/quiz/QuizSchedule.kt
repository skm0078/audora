// File: core/src/main/kotlin/io/github/skm0078/audora/core/quiz/QuizSchedule.kt
// Purpose: When each quiz card comes back: right answers space it out, a wrong one brings it back soon (item 70).

package io.github.skm0078.audora.core.quiz

import io.github.skm0078.audora.core.config.QuizConfig

/** A card's place in the schedule: [streak] right answers in a row, due again at [dueAtMs]. */
data class CardProgress(val streak: Int, val dueAtMs: Long) {
    init {
        require(streak >= 0) { "streak must be >= 0, was $streak" }
    }
}

/**
 * Spaced review in the Leitner style: each right answer in a row waits longer ([QuizConfig.intervalsMs]); a wrong one
 * starts the card over and brings it back after [QuizConfig.retryMs]. A quiz asks the cards that are due, longest
 * overdue first, then cards never asked, in reading order, up to [QuizConfig.sessionCards].
 */
class QuizSchedule(private val config: QuizConfig = QuizConfig()) {
    fun pick(cards: List<QuizCard>, progress: Map<Long, CardProgress>, nowMs: Long): List<QuizCard> {
        val due = cards.filter { progress[it.highlightId]?.let { state -> state.dueAtMs <= nowMs } == true }
            .sortedBy { progress.getValue(it.highlightId).dueAtMs }
        val fresh = cards.filter { it.highlightId !in progress }
        return (due + fresh).take(config.sessionCards)
    }

    /** [progress] after [cardId] was answered, [right] or not, at [nowMs]. */
    fun answered(
        progress: Map<Long, CardProgress>,
        cardId: Long,
        right: Boolean,
        nowMs: Long,
    ): Map<Long, CardProgress> {
        val streak = if (right) (progress[cardId]?.streak ?: 0) + 1 else 0
        val waitMs = if (right) {
            config.intervalsMs[(streak - 1).coerceAtMost(config.intervalsMs.lastIndex)]
        } else {
            config.retryMs
        }
        return progress + (cardId to CardProgress(streak, nowMs + waitMs))
    }
}
