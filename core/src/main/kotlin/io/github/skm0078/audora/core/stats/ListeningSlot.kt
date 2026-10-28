// File: core/src/main/kotlin/io/github/skm0078/audora/core/stats/ListeningSlot.kt
// Purpose: One hour of listening to one book in one language, and what was heard in it (listening stats screen).

package io.github.skm0078.audora.core.stats

import io.github.skm0078.audora.core.model.Lang
import java.time.LocalDate

/**
 * Where a stretch of listening is counted. Stats from before 2026-10-28 were kept by day alone, so those slots have
 * no [hour], [book] or [lang]: they count in every total and streak, and nowhere that needs the missing part.
 *
 * @property hour the local hour it fell in, 0 to 23.
 * @property book the library's id for the book (never its title), so a renamed book keeps its time.
 * @property lang the voice language of the sentences heard.
 */
data class ListeningSlot(val day: LocalDate, val hour: Int? = null, val book: String? = null, val lang: Lang? = null) {
    init {
        require(hour == null || hour in 0..LAST_HOUR) { "hour must be 0 to 23, was $hour" }
    }

    private companion object {
        const val LAST_HOUR = 23
    }
}

/** Listening time in milliseconds, and the words of the sentences that started in it. */
data class Heard(val ms: Long = 0, val words: Long = 0) {
    operator fun plus(other: Heard) = Heard(ms + other.ms, words + other.words)

    val isEmpty: Boolean get() = ms <= 0 && words <= 0
}

/** Adds [more] into these slots. */
fun Map<ListeningSlot, Heard>.plusHeard(more: Map<ListeningSlot, Heard>): Map<ListeningSlot, Heard> =
    (entries.map { it.toPair() } + more.entries.map { it.toPair() }).sumHeard()

/** One entry per slot, the slots that repeat added up. */
fun Iterable<Pair<ListeningSlot, Heard>>.sumHeard(): Map<ListeningSlot, Heard> {
    val sum = HashMap<ListeningSlot, Heard>()
    forEach { (slot, heard) -> sum[slot] = (sum[slot] ?: Heard()) + heard }
    return sum
}

/** Milliseconds listened on each day. */
fun Map<ListeningSlot, Heard>.msByDay(): Map<LocalDate, Long> {
    val days = HashMap<LocalDate, Long>()
    forEach { (slot, heard) -> days[slot.day] = (days[slot.day] ?: 0) + heard.ms }
    return days
}
