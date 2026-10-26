// File: core/src/main/kotlin/io/github/skm0078/audora/core/stats/ListeningTally.kt
// Purpose: Turns "reading" and "not reading" into listening time per local day, split at midnight (CF-8).

package io.github.skm0078.audora.core.stats

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The clocks a tally reads: a monotonic one for how long, the wall clock and zone for which day. */
interface ListeningClock {
    /** Milliseconds that only go forward (Android's elapsedRealtime): a clock change never adds or removes time. */
    fun elapsedMillis(): Long

    fun wallMillis(): Long

    fun zone(): ZoneId
}

/**
 * Listening time between a start and each checkpoint or stop, by local day. How long comes from the monotonic clock,
 * so moving the phone's clock never counts hours that were not listened; which day comes from the wall clock at the
 * checkpoint, so a session over midnight counts on both days. One caller at a time (the playback service's thread).
 */
class ListeningTally(
    private val clock: ListeningClock,
    private val config: ListeningStatsConfig = ListeningStatsConfig(),
) {
    private var since: Long? = null

    val listening: Boolean get() = since != null

    /**
     * Listening goes on, or starts now: the time since the last checkpoint, by day. Empty when it just started, so
     * a reading report sent on every page change both starts the tally and saves it as it goes.
     */
    fun reading(): Map<LocalDate, Long> {
        if (since == null) {
            since = clock.elapsedMillis()
            return emptyMap()
        }
        return checkpoint()
    }

    /** Listening stopped: the time since the last checkpoint, by day. Empty if it was not listening. */
    fun stopped(): Map<LocalDate, Long> = checkpoint().also { since = null }

    private fun checkpoint(): Map<LocalDate, Long> {
        val start = since ?: return emptyMap()
        val now = clock.elapsedMillis()
        since = now
        // A session that ran longer than a day without a single report is a stuck report, not a listener.
        val spent = (now - start).coerceIn(0, config.maxSegmentMs)
        return splitByDay(clock.wallMillis(), spent, clock.zone())
    }

    companion object {
        /** [durationMs] that ended at [endWallMs], by the local day each part of it fell on. */
        fun splitByDay(endWallMs: Long, durationMs: Long, zone: ZoneId): Map<LocalDate, Long> {
            if (durationMs <= 0) return emptyMap()
            val parts = LinkedHashMap<LocalDate, Long>()
            var end = endWallMs
            var left = durationMs
            while (left > 0) {
                val day = Instant.ofEpochMilli(end - 1).atZone(zone).toLocalDate()
                val dayStart = day.atStartOfDay(zone).toInstant().toEpochMilli()
                val part = minOf(left, end - dayStart)
                parts[day] = (parts[day] ?: 0) + part
                left -= part
                end -= part
            }
            return parts
        }
    }
}
