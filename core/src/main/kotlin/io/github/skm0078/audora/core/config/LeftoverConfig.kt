// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/LeftoverConfig.kt
// Purpose: Tunables for cleaning up what unfinished jobs leave behind (half downloads, half-made audiobooks and
//          talking books; ADR 2026-10-29-jobs-card-and-leftovers).

package io.github.skm0078.audora.core.config

/**
 * @property keepMs how long an unfinished job's leftovers are kept after they were last written, so Continue, a
 *   resumed download or saving the book again still goes on from them. Past this they are deleted at the next
 *   app start. A week matches Android's own expiry of hidden half-written shared files.
 * @property startDelayMs how long after the app starts the clean-up waits, so it never competes with the first
 *   screen or a book being opened.
 */
data class LeftoverConfig(val keepMs: Long = DEFAULT_KEEP_MS, val startDelayMs: Long = DEFAULT_START_DELAY_MS) {
    init {
        require(keepMs > 0) { "keepMs must be > 0, was $keepMs" }
        require(startDelayMs >= 0) { "startDelayMs must be >= 0, was $startDelayMs" }
    }

    /** [keepMs] in whole days, for the listener's wording ("kept for 7 days"). */
    val keepDays: Int get() = (keepMs / DAY_MS).toInt().coerceAtLeast(1)

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
        const val DEFAULT_KEEP_MS = 7 * DAY_MS
        const val DEFAULT_START_DELAY_MS = 20_000L
    }
}
