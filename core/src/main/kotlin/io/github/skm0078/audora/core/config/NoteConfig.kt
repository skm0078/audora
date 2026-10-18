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
 * @property ocrFootnoteBandShare on a scanned page, footnotes end within this share of the page's height from its
 *   foot: OCR's sizes vary more than a font's, and small lines ending a short page mid-sheet are text, not notes.
 * @property ocrFootnoteMinConfidence and their letters were read with at least this mean OCR confidence (0–100):
 *   lower, the small strip is a stamp or a garbled print slug.
 * @property ocrSureNoteLines on a scanned page, a run of small lines this long or longer, or opening with a marker,
 *   is small enough by `footnoteSizeRatio`;
 * @property ocrFewNoteLinesSizeRatio a shorter run must average at most this share of the body size: one OCR size is
 *   a rough measure, and a body line measured a little small must not be taken for a note.
 */
data class NoteConfig(
    val silenceMarkers: Boolean = true,
    val markerSizeRatio: Float = 0.85f,
    val markerRaiseEm: Float = 0.25f,
    val maxMarkerDigits: Int = 3,
    val footnoteSizeRatio: Float = 0.92f,
    val footnoteGapEm: Float = 0.5f,
    val minCarriedNoteWidthShare: Float = 0.5f,
    val ocrFootnoteBandShare: Float = 0.12f,
    val ocrFootnoteMinConfidence: Int = 70,
    val ocrSureNoteLines: Int = 3,
    val ocrFewNoteLinesSizeRatio: Float = 0.88f,
) {
    init {
        val shares = listOf(
            markerSizeRatio,
            footnoteSizeRatio,
            minCarriedNoteWidthShare,
            ocrFootnoteBandShare,
            ocrFewNoteLinesSizeRatio,
        )
        require(shares.all { it in 0f..1f }) { "note shares must be in 0..1, were $shares" }
        require(markerRaiseEm > 0f) { "markerRaiseEm must be > 0, was $markerRaiseEm" }
        require(footnoteGapEm >= 0f) { "footnoteGapEm must be >= 0, was $footnoteGapEm" }
        require(ocrFootnoteMinConfidence in 0..OcrConfig.MAX_CONFIDENCE) {
            "ocrFootnoteMinConfidence must be in 0..100, was $ocrFootnoteMinConfidence"
        }
        require(ocrSureNoteLines >= 1) { "ocrSureNoteLines must be >= 1, was $ocrSureNoteLines" }
        require(maxMarkerDigits >= 1) { "maxMarkerDigits must be >= 1, was $maxMarkerDigits" }
    }
}
