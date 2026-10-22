// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/NoteConfig.kt
// Purpose: Tunables for finding note markers, footnotes and "Note:" asides on a page (RI-5a, ADR-035).

package io.github.skm0078.audora.core.config

/**
 * `:pdf` silences note markers ("both.¹", "Abbott,³") and tags footnotes and "Note:" paragraphs so playback can read
 * or skip them (ADR-035). Sizes are shares of a line's or the page's body size.
 *
 * @property silenceMarkers leave note markers out of the reading text, the way citation markers are
 *   (`CleanupConfig.skipCitationMarkers`), with offsets still mapped to the page (ADR-018).
 * @property markerSizeRatio a marker's glyphs are at most this share of its line's size.
 * @property markerRaiseEm and sit at least this many line sizes above the line's baseline: a subscript ("B₁", "S₂")
 *   sits on or below it.
 * @property maxMarkerDigits a marker's numbers have at most this many digits ("12", "8,16,17").
 * @property footnoteSizeRatio footnote lines are at most this share of the page's body size.
 * @property footnoteGapEm and stand at least this many body sizes below the line above them (the space where a short
 *   rule usually sits); body lines follow each other much closer.
 * @property minCarriedNoteWidthShare footnote lines that open without a number carry on a note from the page before,
 *   so their first line runs at least this share of the width of the line above (a stray table cell does not).
 */
data class NoteConfig(
    val silenceMarkers: Boolean = true,
    val markerSizeRatio: Float = 0.85f,
    val markerRaiseEm: Float = 0.25f,
    val maxMarkerDigits: Int = 3,
    val footnoteSizeRatio: Float = 0.92f,
    val footnoteGapEm: Float = 0.5f,
    val minCarriedNoteWidthShare: Float = 0.5f,
) {
    init {
        val shares = listOf(markerSizeRatio, footnoteSizeRatio, minCarriedNoteWidthShare)
        require(shares.all { it in 0f..1f }) { "note shares must be in 0..1, were $shares" }
        require(markerRaiseEm > 0f) { "markerRaiseEm must be > 0, was $markerRaiseEm" }
        require(footnoteGapEm >= 0f) { "footnoteGapEm must be >= 0, was $footnoteGapEm" }
        require(maxMarkerDigits >= 1) { "maxMarkerDigits must be >= 1, was $maxMarkerDigits" }
    }
}
