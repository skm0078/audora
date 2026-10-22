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
    /** The card ids (highlight ids, in reading order) one quiz asks. */
    fun pick(ids: List<Long>, progress: Map<Long, CardProgress>, nowMs: Long): List<Long> {
        val due = ids.filter { id -> progress[id]?.let { it.dueAtMs <= nowMs } == true }
            .sortedBy { progress.getValue(it).dueAtMs }
        val fresh = ids.filter { it !in progress }
        return (due + fresh).take(config.sessionCards)
    }

    /** Practise when nothing is due: the cards coming back soonest, by their real due time. */
    fun practise(ids: List<Long>, progress: Map<Long, CardProgress>): List<Long> =
        ids.filter { it in progress }.sortedBy { progress.getValue(it).dueAtMs }.take(config.sessionCards)

    /**
     * [progress] after [cardId] was answered, [right] or not, at [nowMs]. A right answer on a card that was not yet
     * due (practise) changes nothing, so practising never pushes a card further out; a wrong one starts it over.
     */
    fun answered(
        progress: Map<Long, CardProgress>,
        cardId: Long,
        right: Boolean,
        nowMs: Long,
    ): Map<Long, CardProgress> {
        val before = progress[cardId]
        if (right && before != null && before.dueAtMs > nowMs) return progress
        val streak = if (right) (before?.streak ?: 0) + 1 else 0
        val waitMs = if (right) {
            config.intervalsMs[(streak - 1).coerceAtMost(config.intervalsMs.lastIndex)]
        } else {
            config.retryMs
        }
        return progress + (cardId to CardProgress(streak, nowMs + waitMs))
    }
}
