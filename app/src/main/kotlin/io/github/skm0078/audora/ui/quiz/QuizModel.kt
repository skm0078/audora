// File: app/src/main/kotlin/io/github/skm0078/audora/ui/quiz/QuizModel.kt
// Purpose: Runs "Quiz me": builds cards from highlights, asks the due ones aloud and saves the schedule (item 70).

package io.github.skm0078.audora.ui.quiz

import io.github.skm0078.audora.core.config.QuizConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.notes.HighlightStore
import io.github.skm0078.audora.core.quiz.CardProgress
import io.github.skm0078.audora.core.quiz.QuizCard
import io.github.skm0078.audora.core.quiz.QuizCards
import io.github.skm0078.audora.core.quiz.QuizProgressStore
import io.github.skm0078.audora.core.quiz.QuizRound
import io.github.skm0078.audora.core.quiz.QuizSchedule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the quiz screen shows. */
sealed interface QuizState {
    data object Loading : QuizState

    /** No highlight has a word long enough to blank: highlight a few passages first. */
    data object NoCards : QuizState

    /** Every card is scheduled for later; [Practise][QuizModel.practise] asks the soonest anyway. */
    data object NothingDue : QuizState

    data class Asking(val round: QuizRound) : QuizState
}

/**
 * The quiz's state and actions; the screen only draws [state]. Each answer is saved at once, so leaving the quiz or
 * turning the phone (which starts a new quiz) loses nothing. [speak] reads a line aloud, false if it could not.
 */
class QuizModel(
    private val highlights: HighlightStore,
    private val progressStore: QuizProgressStore,
    private val scope: CoroutineScope,
    private val speak: suspend (String, Lang) -> Boolean,
    private val now: () -> Long = System::currentTimeMillis,
    config: QuizConfig = QuizConfig(),
) {
    private val builder = QuizCards(config)
    private val schedule = QuizSchedule(config)
    private val mutableState = MutableStateFlow<QuizState>(QuizState.Loading)
    private var cards: List<QuizCard> = emptyList()
    private var progress: Map<Long, CardProgress> = emptyMap()

    val state: StateFlow<QuizState> = mutableState.asStateFlow()

    fun start() {
        scope.launch {
            val (built, saved) = withContext(Dispatchers.IO) {
                builder.build(highlights.observeAll().first()) { spokenBlank(it) } to progressStore.load()
            }
            cards = built
            progress = saved
            AudoraLog.i(TAG, "Quiz opened: ${built.size} cards, ${saved.size} scheduled")
            ask(schedule.pick(built, saved, now()))
        }
    }

    /** Asks the soonest cards even though none is due. */
    fun practise() = ask(schedule.pick(cards, progress.mapValues { it.value.copy(dueAtMs = 0) }, now()))

    fun choose(choice: String) = asking?.choose(choice)?.let(::record)

    fun reveal() {
        asking?.let { mutableState.value = QuizState.Asking(it.reveal()) }
    }

    fun grade(knewIt: Boolean) = asking?.grade(knewIt)?.let(::record)

    fun next() {
        val round = asking?.next() ?: return
        mutableState.value = QuizState.Asking(round)
        round.current?.let { say(it.spoken, it) }
    }

    /** Reads the current card again: the question, or the whole line once answered. */
    fun hearAgain() {
        val round = asking ?: return
        val card = round.current ?: return
        say(if (round.verdict == null) card.spoken else card.answer, card)
    }

    private fun ask(picked: List<QuizCard>) {
        mutableState.value = when {
            cards.isEmpty() -> QuizState.NoCards
            picked.isEmpty() -> QuizState.NothingDue
            else -> QuizState.Asking(QuizRound(picked)).also { say(picked.first().spoken, picked.first()) }
        }
    }

    private fun record(answer: QuizRound.Answer) {
        mutableState.value = QuizState.Asking(answer.round)
        progress = schedule.answered(progress, answer.cardId, answer.right, now())
        val snapshot = progress
        val known = cards.mapTo(HashSet()) { it.highlightId }
        scope.launch { withContext(Dispatchers.IO) { progressStore.save(snapshot, known) } }
        answer.round.current?.let { say(it.answer, it) }
    }

    private fun say(text: String, card: QuizCard) {
        scope.launch { speak(text, card.lang) }
    }

    private val asking: QuizRound? get() = (mutableState.value as? QuizState.Asking)?.round

    private companion object {
        const val TAG = "Quiz"

        /** What the voice says in place of the blank: spoken content in the card's language, not interface text. */
        fun spokenBlank(lang: Lang): String = when (lang) {
            Lang.HI -> "रिक्त स्थान"
            Lang.BN -> "শূন্যস্থান"
            Lang.EN -> "blank"
        }
    }
}
