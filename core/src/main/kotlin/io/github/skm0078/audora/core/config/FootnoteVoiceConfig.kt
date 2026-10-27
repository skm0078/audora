// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/FootnoteVoiceConfig.kt
// Purpose: How a footnote sounds next to the text (Q-4): a little quicker and quieter, the way a reader says an aside.

package io.github.skm0078.audora.core.config

/**
 * Applied on top of whatever the sentence's delivery already is (ADR-056), so a footnote in a story keeps the book's
 * pace, only slightly faster. Small on purpose: the footnote must stay as clear as the text.
 *
 * @property rate the voice's speed, relative to the text around it (1 changes nothing).
 * @property gainDb how much quieter, in decibels (0 changes nothing).
 */
data class FootnoteVoiceConfig(val rate: Float = DEFAULT_RATE, val gainDb: Float = DEFAULT_GAIN_DB) {
    init {
        require(rate in MIN_RATE..MAX_RATE) { "rate must be in $MIN_RATE..$MAX_RATE, was $rate" }
        require(gainDb in MIN_GAIN_DB..0f) { "gainDb must be in $MIN_GAIN_DB..0, was $gainDb" }
    }

    companion object {
        const val DEFAULT_RATE = 1.08f
        const val DEFAULT_GAIN_DB = -3f
        const val MIN_RATE = 1f
        const val MAX_RATE = 1.3f
        const val MIN_GAIN_DB = -9f
    }
}
