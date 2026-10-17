// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/LayoutConfig.kt
// Purpose: Tunables for rebuilding a page's layout: lines, columns, reading order, paragraphs, headings (P2-02).

package io.github.skm0078.audora.core.config

/**
 * Lengths ending in `Em` are multiples of the glyph size (the font size, or the glyph box height where that is
 * unusable, see [minFontSize]), of the page's body text for page-wide decisions. So one setting fits 8 pt
 * footnotes and 14 pt children's books alike. Spec §5 step 4, ADR-018.
 *
 * @property minFontSize PDFium reports the `Tf` operand as the font size and ignores the text matrix, so PDFs that
 *   draw everything at `1 Tf` and scale with `Tm` report 1 pt; below this the glyph box height is used instead.
 *
 * @property lineOverlapRatio two glyph boxes are on the same line when their vertical overlap is at least this
 *   share of the smaller height.
 * @property columnGapEm a horizontal gap at least this wide ends a run of glyphs, and an empty vertical strip at
 *   least this wide is a column gutter. LaTeX's default gutter is 1 em; justified word spaces stay below ~0.6 em.
 * @property backJumpEm a glyph further left than this behind the previous one starts a new run. Large enough that
 *   Indic pre-base vowel signs, drawn left of their consonant, never split a word.
 * @property minColumnWidthEm both sides of a column cut must be at least this wide, so a margin of line numbers
 *   or list bullets is not read as a column of its own.
 * @property minColumnHeightEm a column cut needs text at least this tall: a single line is never split into columns.
 * @property yCutShare a horizontal cut is made at every vertical gap at least this share of the largest one, so
 *   evenly spaced lines are cut in one step instead of one recursion level per line.
 * @property maxCutDepth recursion guard for the XY-cut; deeper groups keep top-to-bottom order.
 * @property paragraphPitchRatio a line further below the previous one than this multiple of the block's usual
 *   line pitch starts a paragraph.
 * @property defaultLineSpacing line pitch, in em of the line's own size, assumed for blocks with too few lines
 *   to measure one, and the least pitch a line of that size is expected to have.
 * @property indentEm a line starting at least this far right of its block's left edge, after a line that is not
 *   indented, starts a paragraph (first-line indents).
 * @property sizeToleranceRatio font sizes within this share of each other count as the same size.
 * @property headingSizeRatio a line at least this multiple of the body size is heading-styled.
 * @property maxHeadingLines a heading has at most this many lines; a longer run of large text is body text.
 * @property maxHeadingChars a heading has at most this many characters.
 * @property boldFontMarkers lower-case fragments of a font name that mean bold. Font weight is unreliable in
 *   PDFium (ADR-011 §1), so the name decides: "Times-Bold", "CMBX10" (TeX bold), "NimbusRomNo9L-Medi".
 * @property minBoxedShare below this share of glyphs with a box (OCR'd or converted text), the page keeps the
 *   extraction order instead of a geometric layout.
 * @property minRunShare below this share of glyphs in runs of two or more (vertical or rotated text), the page
 *   keeps the extraction order as well, unless its text is turned (below).
 * @property minDirectionSteps a page's text direction is judged from the steps between neighbouring glyphs inside
 *   words; with fewer steps than this it is not judged (ADR-032).
 * @property minTurnedShare a page is laid out turned when at least this share of its steps go up, down or left.
 * @property maxGlyphStepEm a step longer than this many glyph sizes is a jump to another line or cell, not counted.
 * @property furniture captions and display equations, tagged after cleanup (RI-7a).
 */
data class LayoutConfig(
    val minFontSize: Float = 2f,
    val lineOverlapRatio: Float = 0.5f,
    val columnGapEm: Float = 0.75f,
    val backJumpEm: Float = 2f,
    val minColumnWidthEm: Float = 5f,
    val minColumnHeightEm: Float = 2f,
    val yCutShare: Float = 0.8f,
    val maxCutDepth: Int = 32,
    val paragraphPitchRatio: Float = 1.3f,
    val defaultLineSpacing: Float = 1.1f,
    val indentEm: Float = 0.8f,
    val sizeToleranceRatio: Float = 0.05f,
    val headingSizeRatio: Float = 1.15f,
    val maxHeadingLines: Int = 3,
    val maxHeadingChars: Int = 200,
    val boldFontMarkers: List<String> = DEFAULT_BOLD_MARKERS,
    val minBoxedShare: Float = 0.5f,
    val minRunShare: Float = 0.5f,
    val minDirectionSteps: Int = 20,
    val minTurnedShare: Float = 0.6f,
    val maxGlyphStepEm: Float = 2f,
    val furniture: FurnitureConfig = FurnitureConfig(),
) {
    init {
        require(minFontSize >= 0f) { "minFontSize must be >= 0, was $minFontSize" }
        require(lineOverlapRatio in 0f..1f) { "lineOverlapRatio must be in 0..1, was $lineOverlapRatio" }
        require(columnGapEm > 0f && backJumpEm > 0f) { "columnGapEm and backJumpEm must be > 0" }
        require(minColumnWidthEm >= 0f && minColumnHeightEm >= 0f) { "column minimums must be >= 0" }
        require(yCutShare in 0f..1f) { "yCutShare must be in 0..1, was $yCutShare" }
        require(maxCutDepth > 0) { "maxCutDepth must be > 0, was $maxCutDepth" }
        require(paragraphPitchRatio > 1f) { "paragraphPitchRatio must be > 1, was $paragraphPitchRatio" }
        require(defaultLineSpacing > 0f && indentEm > 0f) { "defaultLineSpacing and indentEm must be > 0" }
        require(sizeToleranceRatio >= 0f) { "sizeToleranceRatio must be >= 0, was $sizeToleranceRatio" }
        require(headingSizeRatio > 1f) { "headingSizeRatio must be > 1, was $headingSizeRatio" }
        require(maxHeadingLines > 0 && maxHeadingChars > 0) { "heading limits must be > 0" }
        require(boldFontMarkers.all { it.isNotEmpty() && it == it.lowercase() }) { "bold markers must be lower case" }
        require(minBoxedShare in 0f..1f && minRunShare in 0f..1f) { "shares must be in 0..1" }
        require(minDirectionSteps > 0 && minTurnedShare in 0f..1f) { "direction thresholds out of range" }
        require(maxGlyphStepEm > 0f) { "maxGlyphStepEm must be > 0, was $maxGlyphStepEm" }
    }

    private companion object {
        val DEFAULT_BOLD_MARKERS = listOf("bold", "black", "heavy", "demi", "cmbx", "-medi", "-bd", ",bd")
    }
}
