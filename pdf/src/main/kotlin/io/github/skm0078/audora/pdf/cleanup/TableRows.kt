// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/cleanup/TableRows.kt
// Purpose: What text-layer and scanned tables share once their cells are known: the header rule and the paragraphs.

package io.github.skm0078.audora.pdf.cleanup

import io.github.skm0078.audora.core.model.TextBox
import io.github.skm0078.audora.pdf.layout.Paragraph
import io.github.skm0078.audora.pdf.layout.ParagraphKind
import io.github.skm0078.audora.pdf.layout.TableCells
import io.github.skm0078.audora.pdf.layout.TextLine
import io.github.skm0078.audora.pdf.layout.TextSpan

/** A cell that is a number: "12", "-2.98", "4.00", "98,62,767", "৩.৯১", "45%", "12." (a serial number). */
internal val NUMBER_CELL = Regex("[-−+]?\\p{Nd}[\\p{Nd},]*(?:\\.\\p{Nd}+)?[.%]?")

/**
 * How many leading rows of [rows] (each its cells' text) make the header: the rows before the first one with a
 * number for a cell, at most [maxHeaderRows] (a header set over two lines: "State / Union" above "Territory"). The
 * first row when no row has a number (a truth table) or the first one does.
 */
internal fun headerRowCount(rows: List<List<String>>, maxHeaderRows: Int): Int {
    val firstWithNumber = rows.indexOfFirst { row -> row.any { NUMBER_CELL.matches(it.trim()) } }
    return if (firstWithNumber < 1) 1 else minOf(firstWithNumber, maxHeaderRows, rows.size - 1)
}

/**
 * A table as layout paragraphs (RI-4a): its [header], then its [rows], each one line of its cells' spans, cell after
 * cell, carrying the cells in [TableCells]. [box] and [size] describe the lines (null and 0 when unknown).
 */
internal fun tableParagraphs(
    header: List<List<TextSpan>>,
    rows: List<List<List<TextSpan>>>,
    tableIndex: Int,
    box: TextBox?,
    size: Float,
): List<Paragraph> {
    fun paragraph(cells: List<List<TextSpan>>, row: Int?): Paragraph {
        val kind = if (row == null) ParagraphKind.TABLE_HEADER else ParagraphKind.TABLE_ROW
        return Paragraph(
            listOf(TextLine(cells.flatten(), box, size, bold = false)),
            kind,
            table = TableCells(tableIndex, row, rows.size, header.size, cells),
        )
    }
    return listOf(paragraph(header, null)) + rows.mapIndexed { i, cells -> paragraph(cells, i) }
}
