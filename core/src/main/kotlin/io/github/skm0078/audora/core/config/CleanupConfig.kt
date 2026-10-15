// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/CleanupConfig.kt
// Purpose: Tunables for removing page clutter and repairing text before speech (P2-03, Build Spec §5 steps 5-6).

package io.github.skm0078.audora.core.config

/**
 * Spec §5 steps 5 and 6, ADR-020.
 *
 * @property marginBandShare a line is a header or footer candidate when it lies within this share of the text
 *   area's height from its top or bottom. The text area is the union of the line boxes on the neighbouring
 *   pages (the page size is not extracted), so the band follows where the text actually is.
 * @property maxMarginLines at most this many lines from each end of a page are header or footer candidates.
 * @property neighbourPages pages on each side looked at for repeated headers and running page numbers. 3 keeps
 *   both pages of the same parity two steps away in the window, so alternating left/right headers still repeat.
 * @property minRepeatPages a margin line is a running header when it repeats on at least this many pages of the
 *   window, itself included. A book shorter than this needs every page (never fewer than 2).
 * @property minSignatureLetters a repeated margin line needs at least this many letters; shorter lines are left
 *   to the page-number rules.
 * @property maxFolioDigits a page number has at most this many digits.
 * @property tableGapEm a line whose runs are at least this far apart (in em of the line's size) may be a table row.
 *   Above the layout's run gap (`LayoutConfig.columnGapEm`): Bengali glyph boxes leave gaps of up to ~1.2 em.
 * @property tableAlignEm two rows share a column when cells start within this distance of each other.
 * @property minTableRows a run of at least this many aligned rows is a table interior. Its caption is kept.
 * @property skipCitationMarkers drop bracketed numeric citations ("[4–7]"): the spec's default. A setting in
 *   :app may turn it off later; the expected text of the golden corpus skips them.
 * @property profileCachePages page summaries a book keeps for its neighbours; about a kilobyte each.
 * @property analyzedCachePages extracted and analysed pages a book keeps, so a page read as a neighbour is
 *   not extracted again when it is read itself. Must exceed [neighbourPages] + 1, or reading on page by page
 *   extracts every page twice.
 */
data class CleanupConfig(
    val marginBandShare: Float = 0.08f,
    val maxMarginLines: Int = 3,
    val neighbourPages: Int = 3,
    val minRepeatPages: Int = 3,
    val minSignatureLetters: Int = 3,
    val maxFolioDigits: Int = 4,
    val tableGapEm: Float = 1.5f,
    val tableAlignEm: Float = 1f,
    val minTableRows: Int = 3,
    val skipCitationMarkers: Boolean = true,
    val profileCachePages: Int = 64,
    val analyzedCachePages: Int = 6,
) {
    init {
        require(marginBandShare in 0f..1f) { "marginBandShare must be in 0..1, was $marginBandShare" }
        require(maxMarginLines >= 0 && neighbourPages >= 0) { "maxMarginLines and neighbourPages must be >= 0" }
        require(minRepeatPages >= 2) { "minRepeatPages must be >= 2, was $minRepeatPages" }
        require(minSignatureLetters > 0 && maxFolioDigits > 0) { "minSignatureLetters and maxFolioDigits must be > 0" }
        require(tableGapEm > 0f && tableAlignEm >= 0f) { "tableGapEm must be > 0 and tableAlignEm >= 0" }
        require(minTableRows >= 2) { "minTableRows must be >= 2, was $minTableRows" }
        require(profileCachePages > 2 * neighbourPages) { "profileCachePages must hold a whole window" }
        require(analyzedCachePages > neighbourPages + 1) { "analyzedCachePages must exceed neighbourPages + 1" }
    }
}
