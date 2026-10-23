// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/HighlightConfig.kt
// Purpose: Tunables for finding a highlight's sentence again after the :pdf pipeline re-cut a page (plan item 30).

package io.github.skm0078.audora.core.config

/**
 * A highlight is placed again only where its sentence certainly is: a band on a sentence the listener never marked
 * would be wrong, and its Remove would delete their highlight.
 *
 * @property maxShiftChars a sentence found away from its saved offset is accepted at most this far from it (a dropped
 *   running header or margin slug moves a page's offsets by a line or two, never by a page).
 * @property minQuoteChars a highlight found inside a merged sentence or across split parts away from its saved offset
 *   holds at least this many letters and digits; shorter ones ("Yes.") are taken only where they cover the offset.
 */
data class HighlightConfig(
    val maxShiftChars: Int = DEFAULT_MAX_SHIFT_CHARS,
    val minQuoteChars: Int = DEFAULT_MIN_QUOTE_CHARS,
) {
    init {
        require(maxShiftChars >= 0) { "maxShiftChars must be >= 0, was $maxShiftChars" }
        require(minQuoteChars > 0) { "minQuoteChars must be > 0, was $minQuoteChars" }
    }

    companion object {
        const val DEFAULT_MAX_SHIFT_CHARS = 400
        const val DEFAULT_MIN_QUOTE_CHARS = 6
    }
}
