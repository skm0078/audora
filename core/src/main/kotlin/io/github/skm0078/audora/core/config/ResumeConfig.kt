// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/ResumeConfig.kt
// Purpose: Tunables for exact resume: how a sentence is fingerprinted and how often the position is saved (P1-06).

package io.github.skm0078.audora.core.config

/**
 * @property fingerprintChars how many normalized characters (letters, digits, combining marks) from each end of a
 *   sentence go into its fingerprints. ~48 per Build Spec §4: long enough that two sentences on one page almost
 *   never share it, short enough that a later pipeline fixing something mid-sentence keeps it.
 * @property minWriteIntervalMs at most one database write per this interval while reading (Build Spec §6); the
 *   newest position is written when the interval ends, and at once on pause or stop.
 */
data class ResumeConfig(
    val fingerprintChars: Int = DEFAULT_FINGERPRINT_CHARS,
    val minWriteIntervalMs: Long = DEFAULT_MIN_WRITE_INTERVAL_MS,
) {
    init {
        require(fingerprintChars > 0) { "fingerprintChars must be > 0, was $fingerprintChars" }
        require(minWriteIntervalMs >= 0) { "minWriteIntervalMs must be >= 0, was $minWriteIntervalMs" }
    }

    private companion object {
        const val DEFAULT_FINGERPRINT_CHARS = 48
        const val DEFAULT_MIN_WRITE_INTERVAL_MS = 2_000L
    }
}
