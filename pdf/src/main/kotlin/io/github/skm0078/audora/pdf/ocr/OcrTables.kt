// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/ocr/OcrTables.kt
// Purpose: Finds the tables on a scanned page, ruled ones from their rules and borderless ones from OCR's rows.

package io.github.skm0078.audora.pdf.ocr

import io.github.skm0078.audora.core.config.CleanupConfig
import io.github.skm0078.audora.core.model.TextBox
import io.github.skm0078.audora.pdf.cleanup.TableFinder
import io.github.skm0078.audora.pdf.cleanup.TableGrid
import io.github.skm0078.audora.pdf.cleanup.overlapsVertically
import io.github.skm0078.audora.pdf.extract.PageText
import io.github.skm0078.audora.pdf.layout.LayoutMode
import io.github.skm0078.audora.pdf.layout.PageLayout
import io.github.skm0078.audora.pdf.layout.Paragraph
import io.github.skm0078.audora.pdf.layout.ParagraphKind
import io.github.skm0078.audora.pdf.layout.TextBlock
import io.github.skm0078.audora.pdf.layout.TextLine
import io.github.skm0078.audora.pdf.layout.TextSpan

/**
 * RI-4a part 2 (ADR-035). Works in the table frame: [PageText] boxes in PDF points with y up, turned so the text
 * runs upright. The page's em is the median height of its OCR lines.
 *
 * 1. **Ruled tables** ([RuledTables]) from the rules found in the page image: a government form or list is ruled,
 *    and Tesseract reads its cells one by one, out of order.
 * 2. **Borderless tables** from the words left: Tesseract reads a row of an unruled table as one line with wide gaps,
 *    or as pieces in blocks of columns, so OCR lines side by side are joined into rows (one run per word) and the
 *    text-layer table path ([TableFinder], [TableGrid]) finds and builds them. Prose rows (a Tesseract paragraph
 *    of lines of running text, or two columns of it side by side) are left out and end a run, as does a gap wider than
 *    `TableConfig.maxRowGapEm`; a grid whose cells are mostly wider than `TableConfig.maxColumnCellEm` is prose
 *    too, not a table.
 */
internal class OcrTables(private val config: CleanupConfig, private val rowOverlapRatio: Float) {
    private val finder = TableFinder(config, rowOverlapRatio)
    private val grids = TableGrid(config.tables, rowOverlapRatio)

    /** The tables on [page] whose OCR lines are [lines] (each its words) and whose rules are [rules]. */
    fun find(page: PageText, lines: List<TableLine>, rules: List<TextBox>): List<OcrTable> {
        val heights = lines.mapNotNull { line -> line.box?.height }.sorted()
        val em = heights.getOrNull(heights.size / 2)?.takeIf { it > 0f } ?: return emptyList()
        val ruled = RuledTables(config.tables, em).find(page, lines.flatMap { it.words }, rules)
        val free = lines.map { line -> line.copy(words = line.words.filter { it.span !in ruled.claimed }) }
        return ruled.tables + borderless(page, free.filter { it.words.isNotEmpty() }, em)
    }

    private fun borderless(page: PageText, lines: List<TableLine>, em: Float): List<OcrTable> {
        val blocks = ArrayList<TextBlock>()
        var block = ArrayList<TextLine>()
        fun close() {
            if (block.isNotEmpty()) blocks.add(TextBlock(listOf(Paragraph(block, ParagraphKind.BODY)), box = null))
            block = ArrayList()
        }
        val prose = proseLines(lines, em)
        for (row in lineRows(lines)) {
            val box = row.map { it.box }.reduce(TextBox::union)
            val above = block.lastOrNull()?.box
            if (above != null && above.bottom - box.top > config.tables.maxRowGapEm * em) close()
            if (row.any { it in prose } || isProseRow(row, em)) {
                close()
            } else {
                block.add(TextLine(row.map { it.span }, box, em, bold = false))
            }
        }
        close()
        val layout = PageLayout(page.pageIndex, blocks, bodySize = em, mode = LayoutMode.EXTRACTION_ORDER)
        val boxes = lines.flatMap { it.words }.associate { it.span to it.box }
        return finder.find(page, layout).flatMap { region -> grids.build(page, region, em) }
            .filterNot { grid -> isProse(listOf(grid.header) + grid.rows, boxes, em) }
            .map { grid -> OcrTable(grid.header, grid.rows, grid.spans) }
    }

    /**
     * The words of prose lines: lines with a prose stretch ([isProse]) in a paragraph with at least
     * `TableConfig.minProseLines` such lines. Tesseract sets a column of prose, or a cell's text wrapped over lines, as
     * one paragraph of lines, and a table's rows as lines of their own; a row holding prose ends a run of rows instead
     * of joining it, so a wrapped cell is read whole in OCR's order rather than cut line by line.
     */
    private fun proseLines(lines: List<TableLine>, em: Float): Set<TableWord> {
        val prose = lines.filter { line -> stretches(line.words.sortedBy { it.box.left }, em).any { isProse(it, em) } }
        return prose.groupBy { it.paragraph }.values
            .filter { it.size >= config.tables.minProseLines }
            .flatMapTo(HashSet()) { paragraph -> paragraph.flatMap { it.words } }
    }

    private fun cellEm(em: Float) = config.tables.maxColumnCellEm * em

    /** Running text, not a cell: wider than a table cell, or at least `TableConfig.minProseWords` words. */
    private fun isProse(stretch: List<TableWord>, em: Float): Boolean =
        stretch.size >= config.tables.minProseWords || stretch.last().box.right - stretch.first().box.left > cellEm(em)

    /** [row]'s stretches of words: words closer than `CleanupConfig.tableGapEm`, left to right. */
    private fun stretches(row: List<TableWord>, em: Float): List<List<TableWord>> {
        val out = ArrayList<MutableList<TableWord>>()
        for (word in row) {
            val last = out.lastOrNull()?.last()
            if (last == null || word.box.left - last.box.right >= config.tableGapEm * em) {
                out.add(mutableListOf(word))
            } else {
                out.last().add(word)
            }
        }
        return out
    }

    /**
     * OCR lines side by side joined into rows, top to bottom, each row's words left to right: Tesseract often reads a
     * borderless table as two blocks of columns, and a row is whole again only across them.
     */
    private fun lineRows(lines: List<TableLine>): List<List<TableWord>> {
        val rows = ArrayList<MutableList<TableWord>>()
        var rowBox: TextBox? = null
        for (line in lines.sortedByDescending { it.box?.top ?: Float.NEGATIVE_INFINITY }) {
            val box = line.box ?: continue
            val current = rowBox
            if (current != null && overlapsVertically(current, box, rowOverlapRatio)) {
                rows.last().addAll(line.words)
                rowBox = current.union(box)
            } else {
                rows.add(line.words.toMutableList())
                rowBox = box
            }
        }
        return rows.map { row -> row.sortedBy { it.box.left } }
    }

    /**
     * A row of two or more stretches of words, every one prose ([isProse]): lines of two prose columns side by side.
     * It ends a run of rows instead of joining one.
     */
    private fun isProseRow(row: List<TableWord>, em: Float): Boolean {
        val stretches = stretches(row, em)
        return stretches.size >= 2 && stretches.all { isProse(it, em) }
    }

    /** Most cells wider than a table cell runs: the columns of a page Tesseract read across as one line each. */
    private fun isProse(rows: List<List<List<TextSpan>>>, boxes: Map<TextSpan, TextBox>, em: Float): Boolean {
        val widths = rows.flatten().mapNotNull { cell ->
            cell.mapNotNull(boxes::get).reduceOrNull(TextBox::union)?.width
        }.sorted()
        return widths.isNotEmpty() && widths[widths.size / 2] > cellEm(em)
    }
}

/** One OCR line in the table frame: its words, its Tesseract paragraph, and its box (null without words). */
internal data class TableLine(val words: List<TableWord>, val paragraph: Int) {
    val box: TextBox? get() = words.map { it.box }.reduceOrNull(TextBox::union)
}
