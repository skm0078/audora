// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/cleanup/TableGrid.kt
// Purpose: Turns the lines of one found table into a header row and data rows of cells (RI-4a, ADR-035).

package io.github.skm0078.audora.pdf.cleanup

import io.github.skm0078.audora.core.config.TableConfig
import io.github.skm0078.audora.core.model.TextBox
import io.github.skm0078.audora.pdf.extract.PageText
import io.github.skm0078.audora.pdf.layout.TextLine
import io.github.skm0078.audora.pdf.layout.TextSpan

/**
 * A table's rows of cells, each cell the spans of its text in reading order (empty for an empty cell).
 *
 * @property header what each column holds.
 * @property rows the data rows, in order.
 * @property lines the table's lines: all it was built from but a title or heading across its top or bottom edge.
 */
internal class Grid(val header: List<List<TextSpan>>, val rows: List<List<List<TextSpan>>>, val lines: Set<TextLine>) {
    val columnCount: Int get() = header.size
}

/**
 * Works from geometry alone, so text-layer and scanned tables take the same path (ruled lines are not extracted).
 *
 * 1. **Cells of a line.** A line's runs closer than `TableConfig.cellGapEm` are one cell ("R1 (s" and "−1").
 * 2. **Lines into line rows.** Lines that overlap vertically (cut into blocks by XY-cut) are one line row.
 * 3. **Columns.** The horizontal extents of the cells of the fullest line rows, merged where they overlap. Each cell
 *    goes to the column it overlaps most (a header spanning two columns goes to one of them), or the nearest. A
 *    single cell across two or more columns at the table's top or bottom edge is a title or the next heading, left
 *    to the text.
 * 4. **Rows.** A line row filling more than `rowStartShare` of the columns starts a row. So does one with a cell in
 *    every dense column (one with a cell on nearly every line row at least that full, `denseColumnShare`: a result
 *    list's roll numbers), or in the first column when none is dense. Any other line row that starts right below the
 *    row above (`wrapGapEm`) carries that row's wrapped cells on; so does one whose cells all fall in columns empty in
 *    the row above (a cell spanning rows: "1st" beside two names).
 * 5. **Not a table.** A grid with fewer than `minFilledShare` of its cells filled, or more than
 *    `maxSingleLetterRunShare` of its runs a lone letter (rotated axis titles), is a chart's labels, dropped.
 * 6. **Header.** The rows before the first one with a number for a cell ("12", "4.00", "১০২১"), up to
 *    `maxHeaderRows`: a header set over two lines ("State / Union" above "Territory"). The first row when no row has
 *    a number (a truth table) or every row does.
 */
internal class TableGrid(private val config: TableConfig, private val rowOverlapRatio: Float) {
    /** The grid of a table covering [lines]; null when they do not make at least two columns and two rows. */
    fun build(page: PageText, lines: Collection<TextLine>, bodySize: Float): Grid? {
        val all = lineRows(lines.mapNotNull { cellsOf(page, it, bodySize) }.filter { it.isNotEmpty() })
        val fits = all.size in 2..config.maxRowsPerTable && !isChart(page, lines)
        val columns = Columns.of(all).takeIf { fits && it.size >= 2 } ?: return null
        val lineRows = withoutEdgeTitles(all, columns)
        val rows = rows(lineRows, lineRows.map(columns::place))
        return if (rows.size >= 2 && isFilled(rows)) grid(page, rows, lines.toSet(), all - lineRows.toSet()) else null
    }

    /** The grid of [rows], its header rows joined; it holds [lines] but those of the edge [titles]. */
    private fun grid(page: PageText, rows: List<List<List<Cell>>>, lines: Set<TextLine>, titles: List<LineRow>): Grid {
        val headerRows = headerRows(rows, page)
        val header = rows.take(headerRows).reduce { a, b -> a.zip(b) { x, y -> x + y } }
        val asSpans = (listOf(header) + rows.drop(headerRows)).map { row ->
            row.map { cells -> cells.flatMap { it.spans } }
        }
        return Grid(
            asSpans.first(),
            asSpans.drop(1),
            lines - titles.flatMap { row -> row.cells.map { it.line } }.toSet(),
        )
    }

    /** [lineRows] without the single cells across two or more columns at the top and bottom edges. */
    private fun withoutEdgeTitles(lineRows: List<LineRow>, columns: Columns): List<LineRow> {
        fun isTitle(row: LineRow) = row.cells.size == 1 && columns.spannedBy(row.box) >= 2
        return lineRows.dropWhile(::isTitle).dropLastWhile(::isTitle)
    }

    /** How many leading rows make the header ([headerRowCount]). */
    private fun headerRows(rows: List<List<List<Cell>>>, page: PageText): Int = headerRowCount(
        rows.map { row ->
            row.map { cell ->
                cell.joinToString("") { c -> c.spans.joinToString("") { page.text.substring(it.start, it.end) } }
            }
        },
        config.maxHeaderRows,
    )

    /** The line's cells: its runs, those closer than `cellGapEm` joined. Null when a run has no glyph box. */
    private fun cellsOf(page: PageText, line: TextLine, bodySize: Float): List<Cell>? {
        val em = if (line.size > 0f) line.size else bodySize
        val runs = line.spans.map { span -> span to boxOf(page, span) }
        if (em <= 0f || runs.any { it.second == null }) return null
        val cells = ArrayList<Cell>()
        var spans = ArrayList<TextSpan>()
        var box: TextBox? = null
        for ((span, runBox) in runs) {
            val current = box
            if (current != null && runBox!!.left - current.right >= config.cellGapEm * em) {
                cells.add(Cell(spans, current, em, line))
                spans = ArrayList()
                box = null
            }
            spans.add(span)
            box = box?.union(runBox!!) ?: runBox
        }
        box?.let { cells.add(Cell(spans, it, em, line)) }
        return cells
    }

    private fun boxOf(page: PageText, span: TextSpan): TextBox? =
        (span.start until span.end).mapNotNull(page::boxAt).reduceOrNull(TextBox::union)

    /** Cells of vertically overlapping lines, top to bottom, each line row's cells left to right. */
    private fun lineRows(lines: List<List<Cell>>): List<LineRow> {
        val sorted = lines.sortedByDescending { cells -> cells.maxOf { it.box.top } }
        val rows = ArrayList<MutableList<Cell>>()
        var rowBox: TextBox? = null
        for (cells in sorted) {
            val box = cells.map { it.box }.reduce(TextBox::union)
            val current = rowBox
            if (current != null && overlapsVertically(current, box, rowOverlapRatio)) {
                rows.last().addAll(cells)
                rowBox = current.union(box)
            } else {
                rows.add(cells.toMutableList())
                rowBox = box
            }
        }
        return rows.map { cells -> LineRow(cells.sortedBy { it.box.left }) }
    }

    /** Line rows joined into rows: wrapped cells and cells spanning rows join the row above. */
    private fun rows(lineRows: List<LineRow>, placed: List<List<List<Cell>>>): List<List<List<Cell>>> {
        val columnCount = placed.first().size
        fun filled(cells: List<List<Cell>>) = cells.count { it.isNotEmpty() }
        // Dense among the line rows at least half full: a wrapped cell's extra lines must not make its column look
        // dense, and a record with empty cells (beside a cell spanning rows) must not make the empty ones look sparse.
        val full = placed.filter { filled(it) >= columnCount * config.rowStartShare }
        val dense = (0 until columnCount).filter { c ->
            full.isNotEmpty() && full.count { it[c].isNotEmpty() } >= full.size * config.denseColumnShare
        }
        val rows = ArrayList<List<MutableList<Cell>>>()
        var lastBox: TextBox? = null
        placed.forEachIndexed { i, cells ->
            val row = rows.lastOrNull()
            val above = lastBox
            val box = lineRows[i].box
            val near = row != null && above != null && above.bottom - box.top <= config.wrapGapEm * lineRows[i].em &&
                filled(cells) <= columnCount * config.rowStartShare
            // A row has a cell in every dense column; without dense columns, rows are keyed by their first cell.
            val key = dense.ifEmpty { listOf(0) }
            val carriesOn = near && key.any { cells[it].isEmpty() }
            val spansRows = near && cells.indices.all { c -> cells[c].isEmpty() || row!![c].isEmpty() }
            if (row != null && (carriesOn || spansRows)) {
                cells.forEachIndexed { c, column -> row[c].addAll(column) }
                lastBox = above?.union(box) ?: box
            } else {
                rows.add(cells.map { it.toMutableList() })
                lastBox = box
            }
        }
        return rows
    }

    /** Charts drawn as text (axis ticks, legends, rotated labels) make a sparse grid; a table fills most cells. */
    private fun isFilled(rows: List<List<List<Cell>>>): Boolean {
        val cells = rows.sumOf { it.size }
        return rows.sumOf { row -> row.count { it.isNotEmpty() } } >= cells * config.minFilledShare
    }

    private fun isChart(page: PageText, lines: Collection<TextLine>): Boolean {
        val runs = lines.flatMap { line -> line.spans.map { page.text.substring(it.start, it.end).trim() } }
        val singleLetters = runs.count { it.codePointCount(0, it.length) == 1 && Character.isLetter(it.codePointAt(0)) }
        return runs.isNotEmpty() && singleLetters > runs.size * config.maxSingleLetterRunShare
    }
}

private class Cell(val spans: List<TextSpan>, val box: TextBox, val em: Float, val line: TextLine)

private class LineRow(val cells: List<Cell>) {
    val box: TextBox = cells.map { it.box }.reduce(TextBox::union)
    val em: Float = cells.maxOf { it.em }
}

/** A table's column extents, left to right. */
private class Columns(private val extents: List<ClosedFloatingPointRange<Float>>) {
    val size: Int get() = extents.size

    /** The line row's cells per column: each where it overlaps most (a header across two in one), or the nearest. */
    fun place(row: LineRow): List<List<Cell>> {
        val placed = List(extents.size) { ArrayList<Cell>() }
        for (cell in row.cells) placed[indexOf(cell.box)].add(cell)
        return placed
    }

    /** How many columns [box] reaches into. */
    fun spannedBy(box: TextBox): Int = extents.count { overlap(box, it) > 0f }

    private fun indexOf(box: TextBox): Int {
        fun distance(c: ClosedFloatingPointRange<Float>) = maxOf(c.start - box.right, box.left - c.endInclusive, 0f)
        val best = extents.indices.maxBy { overlap(box, extents[it]) }
        return if (overlap(box, extents[best]) > 0f) best else extents.indices.minBy { distance(extents[it]) }
    }

    private fun overlap(box: TextBox, c: ClosedFloatingPointRange<Float>) =
        minOf(box.right, c.endInclusive) - maxOf(box.left, c.start)

    companion object {
        /**
         * The cell extents of every line row but the first (a header is often centred over left-aligned cells) and
         * those with a single cell (a section title across the table), merged where they overlap.
         */
        fun of(lineRows: List<LineRow>): Columns {
            val body = lineRows.drop(1).filter { it.cells.size >= 2 }.ifEmpty { lineRows }
            val extents = body.flatMap { row -> row.cells.map { it.box.left..it.box.right } }.sortedBy { it.start }
            val merged = ArrayList<ClosedFloatingPointRange<Float>>()
            for (extent in extents) {
                val last = merged.lastOrNull()
                if (last != null && extent.start <= last.endInclusive) {
                    merged[merged.lastIndex] = last.start..maxOf(last.endInclusive, extent.endInclusive)
                } else {
                    merged.add(extent)
                }
            }
            return Columns(merged)
        }
    }
}
