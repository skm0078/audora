// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/ShareCardConfig.kt
// Purpose: Tunables for sharing lines as a picture (quote cards, ADR 2026-10-27-share-cards).

package io.github.skm0078.audora.core.config

/**
 * A quote card holds a short, credited quote: Kindle, Play Books and Goodreads share the same, and keeping it short
 * keeps it on the fair-dealing side (the plan's guard rails).
 *
 * @property maxWords a longer selection is cut to this many words, at a sentence end when one is close, else with "…".
 * @property sentenceEndShare a sentence end counts as close when it keeps at least this share of [maxWords].
 * @property maxDedicationChars the optional "For Ma" line under the credit.
 * @property keptCards cards kept in the share folder; older ones are deleted when a new one is written.
 */
data class ShareCardConfig(
    val maxWords: Int = 60,
    val sentenceEndShare: Float = 0.5f,
    val maxDedicationChars: Int = 40,
    val keptCards: Int = 5,
) {
    init {
        require(maxWords >= 1) { "maxWords must be >= 1, was $maxWords" }
        require(sentenceEndShare in 0f..1f) { "sentenceEndShare must be in 0..1, was $sentenceEndShare" }
        require(maxDedicationChars >= 1) { "maxDedicationChars must be >= 1, was $maxDedicationChars" }
        require(keptCards >= 1) { "keptCards must be >= 1, was $keptCards" }
    }
}
