// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/ClipConfig.kt
// Purpose: Tunables for sharing a passage as an audio clip (plan item 41).

package io.github.skm0078.audora.core.config

/**
 * A clip starts at the sentence the listener picked and runs to the end of its paragraph, within [minMs]..[maxMs]
 * (Audible's clips are 5–45 s, short enough for a chat app's voice-note preview).
 *
 * @property minMs a paragraph that ends sooner carries on into the next one.
 * @property maxMs the clip never runs longer, except when its first sentence alone does (it is kept whole).
 * @property paragraphPauseMs a sentence followed by at least this much silence (`ReadingUnit.pauseAfterMs`) ends a
 *   paragraph.
 * @property maxPages pages searched for the clip's sentences, from the picked one.
 * @property wakeLockMs the clip's wake lock is held at most this long: a 44 s Kokoro clip renders in about 2 minutes on
 *   the Moto, so this is a safety limit for a stuck render, not a working limit.
 */
data class ClipConfig(
    val minMs: Long = 5_000L,
    val maxMs: Long = 44_000L,
    val paragraphPauseMs: Int = ReadingUnitConfig().paragraphPauseMs,
    val maxPages: Int = 2,
    val wakeLockMs: Long = 600_000L, // 10 min
) {
    init {
        require(minMs in 0..maxMs) { "minMs ($minMs) must be in 0..maxMs ($maxMs)" }
        require(paragraphPauseMs >= 0) { "paragraphPauseMs must be >= 0, was $paragraphPauseMs" }
        require(maxPages >= 1) { "maxPages must be >= 1, was $maxPages" }
        require(wakeLockMs > 0) { "wakeLockMs must be > 0, was $wakeLockMs" }
    }
}
