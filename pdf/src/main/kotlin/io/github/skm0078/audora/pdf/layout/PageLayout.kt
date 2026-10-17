// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/layout/PageLayout.kt
// Purpose: A page's rebuilt layout: blocks in reading order, their paragraphs and lines (P2-02, ADR-018).

package io.github.skm0078.audora.pdf.layout

import io.github.skm0078.audora.core.model.TextBox

/**
 * The structure later stages work on. Header and footer removal (P2-03) drops [TextLine]s by position and
 * repetition; de-hyphenation (P2-03) joins a line's end to the next line, [Paragraph.continuesPrevious] included.
 * All offsets point into the page's extracted text (`PageText.text`), which layout never changes.
 */
data class PageLayout(
    val pageIndex: Int,
    /** In reading order: XY-cut leaves, top to bottom within a column, columns left to right. */
    val blocks: List<TextBlock>,
    /** The most common glyph size on the page, by glyph count (GlyphStyles); 0 when nothing is sized. */
    val bodySize: Float,
    val mode: LayoutMode,
    /**
     * How the page's text was turned to run left to right before layout (a table set sideways, ADR-032). Line and
     * block boxes are in that turned frame; glyph boxes in `PageText` stay in page space, for highlighting.
     */
    val turn: TextTurn = TextTurn.NONE,
)

enum class LayoutMode {
    /** Lines, columns and paragraphs rebuilt from glyph boxes. */
    GEOMETRIC,

    /** Too few glyph boxes (OCR'd or converted text) or vertical text: extraction order, one paragraph. */
    EXTRACTION_ORDER,
}

data class TextBlock(val paragraphs: List<Paragraph>, val box: TextBox?)

data class Paragraph(
    val lines: List<TextLine>,
    val kind: ParagraphKind,
    /** True when this paragraph carries on the previous block's last one (a sentence running into the next column). */
    val continuesPrevious: Boolean = false,
)

enum class ParagraphKind {
    BODY,
    HEADING,

    /** Starts with a bullet or a label ("1.", "(a)"); its continuation lines belong to it (P2-04). */
    LIST_ITEM,
}

/**
 * One visual line. [spans] are the runs of glyphs on it, left to right; a line split by a large gap (a table
 * cell, a tab stop) has several.
 *
 * @property box union of the glyph boxes; null in [LayoutMode.EXTRACTION_ORDER].
 * @property size the size most of the line's glyphs have, in points: the font size, or the glyph box height where
 *   PDFium's font size is meaningless (GlyphStyles). 0 when unknown.
 * @property bold most of the line's glyphs use a bold font, judged by its name (`LayoutConfig.boldFontMarkers`).
 */
data class TextLine(val spans: List<TextSpan>, val box: TextBox?, val size: Float, val bold: Boolean)

/** [start] inclusive to [end] exclusive, offsets into `PageText.text`. Never starts or ends with whitespace. */
data class TextSpan(val start: Int, val end: Int) {
    init {
        require(start in 0..end) { "start ($start) must be in 0..end ($end)" }
    }
}
