// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/ExtractionConfig.kt
// Purpose: Tunables for PDF text extraction and the /ActualText rebuild (Build Spec §5 step 1, ADR-011).

package io.github.skm0078.audora.core.config

/**
 * @property reopenEveryPages close and reopen the document after this many page reads. PDFium's document
 *   cache grows ~25–80 KB per page visited; reopening every 100 pages halved it in S2 (ADR-011 §5).
 * @property actualTextSearchAhead how far (in non-space characters) past the last matched text object the
 *   rebuild looks for the next one. PDFium reorders glyphs within a cluster (Bengali reph), never far.
 * @property actualTextSearchBehind how far back the rebuild looks, for objects PDFium placed earlier.
 * @property zeroWidthObjectPt a text object narrower than this is a space glyph (Chromium/Skia output, S2).
 * @property maxFormDepth how deep the native reader descends into nested form XObjects.
 * @property outlineChapterDepth how many outline levels are searched for chapters: a book's outline often
 *   wraps everything in one title entry, or splits into parts before chapters.
 */
data class ExtractionConfig(
    val reopenEveryPages: Int = 100,
    val actualTextSearchAhead: Int = 64,
    val actualTextSearchBehind: Int = 16,
    val zeroWidthObjectPt: Float = 0.01f,
    val maxFormDepth: Int = 8,
    val outlineChapterDepth: Int = 3,
) {
    init {
        require(reopenEveryPages > 0) { "reopenEveryPages must be > 0, was $reopenEveryPages" }
        require(actualTextSearchAhead >= 0 && actualTextSearchBehind >= 0) { "search windows must be >= 0" }
        require(maxFormDepth >= 0) { "maxFormDepth must be >= 0, was $maxFormDepth" }
        require(outlineChapterDepth > 0) { "outlineChapterDepth must be > 0, was $outlineChapterDepth" }
    }
}
