// File: core/src/main/kotlin/io/github/skm0078/audora/core/files/Leftovers.kt
// Purpose: What an unfinished job leaves behind, when it may be deleted, and what a clean-up freed (ADR
//          2026-10-29-jobs-card-and-leftovers).

package io.github.skm0078.audora.core.files

import io.github.skm0078.audora.core.config.LeftoverConfig

/** Who left it: a download, an audiobook export or a talking book that stopped before the end. */
enum class LeftoverKind { DOWNLOAD, AUDIOBOOK, TALKING_BOOK }

/**
 * What a clean-up deleted, or what is waiting to be: how many unfinished jobs of each kind and their [bytes].
 * A job's files count once, however many there are (an audiobook's chapters are one audiobook).
 */
data class LeftoverTally(val counts: Map<LeftoverKind, Int> = emptyMap(), val bytes: Long = 0L) {
    val isEmpty: Boolean get() = counts.values.sum() == 0 && bytes == 0L

    fun count(kind: LeftoverKind): Int = counts[kind] ?: 0

    operator fun plus(other: LeftoverTally) = LeftoverTally(
        counts = (counts.keys + other.counts.keys).associateWith { count(it) + other.count(it) },
        bytes = bytes + other.bytes,
    )

    companion object {
        val NONE = LeftoverTally()

        fun of(kind: LeftoverKind, jobs: Int, bytes: Long) =
            if (jobs == 0) NONE else LeftoverTally(mapOf(kind to jobs), bytes)
    }
}

/**
 * Decides whether a leftover may go. One rule for every owner, so the listener can be told it in one sentence:
 * kept for [LeftoverConfig.keepDays] after it was last written, unless its book is gone (nothing could ever
 * continue it) or the listener asked for it now.
 */
class LeftoverPolicy(private val config: LeftoverConfig, private val nowMs: Long) {
    /**
     * [touchedAtMs] is when the job last wrote to it. A time ahead of [nowMs] (the clock was moved back) counts as
     * just written: a wrong clock can delay a clean-up, never delete work the listener could still continue.
     */
    fun mayDelete(touchedAtMs: Long, orphan: Boolean = false, asked: Boolean = false): Boolean =
        asked || orphan || nowMs - touchedAtMs >= config.keepMs
}
