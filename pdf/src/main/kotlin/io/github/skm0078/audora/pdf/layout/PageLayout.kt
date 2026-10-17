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
    /** Set exactly on [ParagraphKind.TABLE_HEADER] and [ParagraphKind.TABLE_ROW] paragraphs (RI-4a). */
    val table: TableCells? = null,
) {
    init {
        require((table != null) == kind.isTable) { "table cells must be set exactly on table paragraphs, was $kind" }
    }
}

/**
 * One row of a table (RI-4a): its cells, one per column, each the spans of its text in reading order (empty for an
 * empty cell). A table row paragraph's single line holds the same spans, cell after cell.
 *
 * @property row the data row, from 0; null for the header row.
 * @property rowCount data rows in the table, the header not counted.
 */
data class TableCells(
    val tableIndex: Int,
    val row: Int?,
    val rowCount: Int,
    val columnCount: Int,
    val cells: List<List<TextSpan>>,
) {
    init {
        require(tableIndex >= 0 && rowCount > 0 && columnCount > 0) { "a table has an index, rows and columns" }
        require(row == null || row in 0 until rowCount) { "row ($row) must be in 0 until $rowCount" }
        require(cells.size == columnCount) { "one cell per column: ${cells.size} cells for $columnCount columns" }
    }
}

enum class ParagraphKind {
    BODY,
    HEADING,

    /** Starts with a bullet or a label ("1.", "(a)"); its continuation lines belong to it (P2-04). */
    LIST_ITEM,

    /** A figure or table caption ("Fig. 2. …", "सारणी 3: …"), tagged after cleanup (RI-7a, ADR-035). */
    CAPTION,

    /** A display equation line (RI-7a, ADR-035). */
    EQUATION,

    /** A table's header row (RI-4a): becomes its `TABLE_SUMMARY` unit. */
    TABLE_HEADER,

    /** One data row of a table (RI-4a). */
    TABLE_ROW,
    ;

    val isTable: Boolean get() = this == TABLE_HEADER || this == TABLE_ROW
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
