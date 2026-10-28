// File: core/src/main/kotlin/io/github/skm0078/audora/core/stats/ListeningTally.kt
// Purpose: Turns "reading this book", "this sentence started" and "not reading" into listening by hour, book and
//          language, split at each hour's turn (CF-8, stats screen).

package io.github.skm0078.audora.core.stats

import io.github.skm0078.audora.core.model.Lang
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** The clocks a tally reads: a monotonic one for how long, the wall clock and zone for which day and hour. */
interface ListeningClock {
    /** Milliseconds that only go forward (Android's elapsedRealtime): a clock change never adds or removes time. */
    fun elapsedMillis(): Long

    fun wallMillis(): Long

    fun zone(): ZoneId
}

/**
 * How long comes from the monotonic clock, so moving the phone's clock never counts hours that were not listened;
 * which hour comes from the wall clock at each checkpoint, so a session over midnight counts on both days. Time is
 * counted to the book and language heard before each change, and gathered until [drain], so the caller saves at its
 * own pace (a page, a stop) rather than at every sentence. One caller at a time (the playback service's thread).
 */
class ListeningTally(
    private val clock: ListeningClock,
    private val config: ListeningStatsConfig = ListeningStatsConfig(),
) {
    private var since: Long? = null
    private var book: String? = null
    private var lang: Lang? = null
    private val gathered = HashMap<ListeningSlot, Heard>()

    val listening: Boolean get() = since != null

    /** Reading [bookId] goes on, or starts now. A different book than before starts its own time. */
    fun reading(bookId: String) {
        checkpoint()
        if (since == null) since = clock.elapsedMillis()
        if (bookId != book) {
            book = bookId
            lang = null
        }
    }

    /** A sentence of [words] in [sentenceLang] started: the time before it goes to the language heard until now. */
    fun sentence(bookId: String, sentenceLang: Lang, words: Int) {
        if (bookId != book || sentenceLang != lang) {
            checkpoint()
            book = bookId
            lang = sentenceLang
        }
        if (since == null) since = clock.elapsedMillis()
        if (words > 0) gather(slotAt(nowHour()), Heard(words = words.toLong()))
    }

    /** Listening stopped: the time since the last checkpoint is gathered. Nothing happens if it was not listening. */
    fun stopped() {
        checkpoint()
        since = null
    }

    /** What was gathered since the last drain, by slot; empty when there is nothing new. */
    fun drain(): Map<ListeningSlot, Heard> = HashMap(gathered).also { gathered.clear() }

    private fun checkpoint() {
        val start = since ?: return
        val now = clock.elapsedMillis()
        since = now
        // A session that ran longer than a day without a single report is a stuck report, not a listener.
        val spent = (now - start).coerceIn(0, config.maxSegmentMs)
        splitByHour(clock.wallMillis(), spent, clock.zone()).forEach { (hour, ms) -> gather(slotAt(hour), Heard(ms)) }
    }

    private fun nowHour(): LocalDateTime =
        Instant.ofEpochMilli(clock.wallMillis()).atZone(clock.zone()).toLocalDateTime().truncatedTo(ChronoUnit.HOURS)

    private fun slotAt(hour: LocalDateTime) = ListeningSlot(hour.toLocalDate(), hour.hour, book, lang)

    private fun gather(slot: ListeningSlot, heard: Heard) {
        gathered[slot] = (gathered[slot] ?: Heard()) + heard
    }

    companion object {
        /** [durationMs] that ended at [endWallMs], by the local hour (its start) each part of it fell in. */
        fun splitByHour(endWallMs: Long, durationMs: Long, zone: ZoneId): Map<LocalDateTime, Long> {
            if (durationMs <= 0) return emptyMap()
            val parts = LinkedHashMap<LocalDateTime, Long>()
            var end = endWallMs
            var left = durationMs
            while (left > 0) {
                val hour = Instant.ofEpochMilli(end - 1).atZone(zone).truncatedTo(ChronoUnit.HOURS)
                val part = minOf(left, end - hour.toInstant().toEpochMilli())
                val key = hour.toLocalDateTime()
                parts[key] = (parts[key] ?: 0) + part
                left -= part
                end -= part
            }
            return parts
        }
    }
}
