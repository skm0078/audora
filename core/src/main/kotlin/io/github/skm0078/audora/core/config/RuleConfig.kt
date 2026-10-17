// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/RuleConfig.kt
// Purpose: Tunables for finding a scanned page's ruled lines, the grid of a ruled table (RI-4a part 2, ADR-035).

package io.github.skm0078.audora.core.config

/**
 * A scanned page is OCR'd as words only; the lines ruled between a table's cells are found in the page image so its
 * cells can be rebuilt. Lengths are in em of the page's text: the median height of its OCR'd lines.
 *
 * @property enabled off finds no rules, so scanned tables are read as OCR orders their words.
 * @property maxDarkBelow ink is told from paper by the page's own grey levels (Otsu's threshold: a scan's rules are
 *   often faint grey), but never above this (0–255), so a page on grey paper does not turn its paper into ink.
 * @property smearEm ink this far apart across a rule counts as one row of it: a scan skewed by a degree drifts a
 *   pixel every 60 along a rule, and a thin rule breaks into steps without it.
 * @property gapEm gaps along a rule up to this long are bridged (a broken or dotted scan line).
 * @property maxThicknessEm a stroke thicker than this is a filled bar or a picture, not a rule.
 * @property minInkShare a stroke too thick to be a rule is split into its bands of rows inked at least this share as
 *   much as its most inked row: a double rule (a table's bottom right above a footer's box) joins into one thick
 *   stroke otherwise. A filled bar stays one band, too thick.
 * @property minHorizontalEm a horizontal rule is at least this long. Devanagari and Bengali words hang from a
 *   headline as long as the word, so this is longer than any word.
 * @property minVerticalEm a vertical rule is at least this long: longer than any letter's stem.
 */
data class RuleConfig(
    val enabled: Boolean = true,
    val maxDarkBelow: Int = 200,
    val smearEm: Float = 0.2f,
    val gapEm: Float = 0.15f,
    val maxThicknessEm: Float = 0.5f,
    val minInkShare: Float = 0.5f,
    val minHorizontalEm: Float = 8f,
    val minVerticalEm: Float = 2f,
) {
    init {
        require(maxDarkBelow in 1..MAX_GREY) { "maxDarkBelow must be in 1..255, was $maxDarkBelow" }
        require(smearEm >= 0f && gapEm >= 0f) { "smearEm and gapEm must be >= 0" }
        require(minInkShare > 0f && minInkShare <= 1f) { "minInkShare must be in (0, 1], was $minInkShare" }
        require(maxThicknessEm > 0f && minHorizontalEm > 0f && minVerticalEm > 0f) { "rule sizes must be > 0" }
    }

    private companion object {
        const val MAX_GREY = 255
    }
}
