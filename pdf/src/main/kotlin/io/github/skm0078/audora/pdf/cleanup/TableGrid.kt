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
 * @property lines the table's lines: all it was built from but a title or heading across its top or bottom edge. Two
 *   tables set side by side share the lines they both run across.
 * @property spans the text the table takes: its lines' spans, or for one of two tables side by side, its cells'.
 */
internal class Grid(
    val header: List<List<TextSpan>>,
    val rows: List<List<List<TextSpan>>>,
    val lines: Set<TextLine>,
    val spans: Set<TextSpan> = lines.flatMapTo(HashSet()) { it.spans },
) {
    val columnCount: Int get() = header.size
}

/**
 * Works from geometry alone, so text-layer and scanned tables take the same path (ruled lines are not extracted).
 *
 * 1. **Cells of a line.** A line's runs closer than `TableConfig.cellGapEm` are one cell ("R1 (s" and "−1").
 * 2. **Lines into line rows.** Lines that overlap vertically (cut into blocks by XY-cut) are one line row.
 *    Cells holding two columns printed closer than `cellGapEm` are split where a gap runs down them ([ColumnChannels]).
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
 * 7. **Side by side.** Two tables set side by side on the same row heights come as one: layout joins each row across
 *    the gutter. They are split there when they show it ([SideBySide]) and built as two grids. A side too sparse
 *    for a grid stays text, unless it shares a line with the other: then they are built as one ([sideGrids]).
 */
internal class TableGrid(private val config: TableConfig, private val rowOverlapRatio: Float) {
    private val closeColumns = CloseColumns(ColumnChannels(config))
    private val sideBySide = SideBySide(config)

    /**
     * The grid of a table covering [lines], or two when it is two tables side by side; empty when they do not make at
     * least two columns and two rows.
     */
    fun build(page: PageText, lines: Collection<TextLine>, bodySize: Float): List<Grid> {
        val cells = lines.mapNotNull { cellsOf(page, it, bodySize) }.filter { it.isNotEmpty() }
        val all = closeColumns.split(page, lineRows(cells))
        val fits = all.size in 2..config.maxRowsPerTable && !isChart(page, lines)
        val columns = Columns.of(all).takeIf { fits && it.size >= 2 } ?: return emptyList()
        val lineRows = withoutEdgeTitles(all, columns)
        val halves = sideBySide.split(page, lineRows, columns)?.let { halves ->
            sideGrids(halves, halves.map { half -> gridOf(page, half, Columns.of(half), half.lines(), half.spans()) })
        }
        return halves
            ?: listOfNotNull(gridOf(page, lineRows, columns, lines.toSet() - all.minus(lineRows.toSet()).lines()))
    }

    /** The grid of [lineRows] in [columns], holding [lines]; null under two rows or when too sparse for a table. */
    private fun gridOf(
        page: PageText,
        lineRows: List<LineRow>,
        columns: Columns,
        lines: Set<TextLine>,
        spans: Set<TextSpan>? = null,
    ): Grid? {
        if (columns.size < 2) return null
        val rows = rows(lineRows, lineRows.map(columns::place))
        return if (rows.size >= 2 && isFilled(rows)) grid(page, rows, lines, spans) else null
    }

    /** The grid of [rows], its header rows joined, holding [lines] (and [spans], when given). */
    private fun grid(page: PageText, rows: List<List<List<Cell>>>, lines: Set<TextLine>, spans: Set<TextSpan>?): Grid {
        val headerRows = headerRows(rows, page)
        val header = rows.take(headerRows).reduce { a, b -> a.zip(b) { x, y -> x + y } }
        val asSpans = (listOf(header) + rows.drop(headerRows)).map { row ->
            row.map { cells -> cells.flatMap { it.spans } }
        }
        return if (spans == null) {
            Grid(asSpans.first(), asSpans.drop(1), lines)
        } else {
            Grid(asSpans.first(), asSpans.drop(1), lines, spans)
        }
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
        val runs = line.spans.map { span ->
            span to (span.start until span.end).mapNotNull(page::boxAt).reduceOrNull(TextBox::union)
        }
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

/** Splits the columns of a table that hold two columns each ([ColumnChannels]). */
private class CloseColumns(private val channels: ColumnChannels) {
    /** [rows] with each column that holds two split, one split at a time until none is left. */
    fun split(page: PageText, rows: List<LineRow>): List<LineRow> {
        var current = rows
        while (true) {
            val columns = Columns.of(current)
            val placed = current.map(columns::place)
            val words = current.flatMap { it.cells }.associateWith { wordsOf(page, it) }
            val split = (0 until columns.size).firstNotNullOfOrNull { c ->
                val cells = placed.flatMap { it[c] }
                val ems = cells.map { it.em }.sorted()
                val rowWords = placed.map { row ->
                    row[c].flatMap { words.getValue(it) }.map { it.second.left..it.second.right }.sortedBy { it.start }
                }
                channels.find(rowWords, ems[ems.size / 2])
                    ?.let { x -> cells.associateWith { splitAt(it, words.getValue(it), x) } }
                    ?.takeIf { cut -> cut.values.any { it.size > 1 } }
            } ?: return current
            current =
                current.map { row -> LineRow(row.cells.flatMap { split[it] ?: listOf(it) }.sortedBy { it.box.left }) }
        }
    }

    /** [cell] cut at [x] into the words left of it and those right of it, or kept whole when all fall on one side. */
    private fun splitAt(cell: Cell, words: List<Pair<TextSpan, TextBox>>, x: Float): List<Cell> {
        val (left, right) = words.partition { (it.second.left + it.second.right) / 2 < x }
        if (left.isEmpty() || right.isEmpty()) return listOf(cell)
        return listOf(left, right).map { part ->
            Cell(part.map { it.first }, part.map { it.second }.reduce(TextBox::union), cell.em, cell.line)
        }
    }

    /** [cell]'s words, each its span and box: its runs cut at spaces. Words without a glyph box are left out. */
    private fun wordsOf(page: PageText, cell: Cell): List<Pair<TextSpan, TextBox>> = cell.spans.flatMap { span ->
        WORD.findAll(page.text.substring(span.start, span.end)).mapNotNull { match ->
            val word = TextSpan(span.start + match.range.first, span.start + match.range.last + 1)
            (word.start until word.end).mapNotNull(page::boxAt).reduceOrNull(TextBox::union)?.let { word to it }
        }.toList()
    }

    private companion object {
        val WORD = Regex("\\S+")
    }
}

private class Cell(val spans: List<TextSpan>, val box: TextBox, val em: Float, val line: TextLine)

/**
 * The grids of two tables side by side, [grids] built from [halves]. When one side makes no grid (too sparse), the
 * other is the only table and the side that made none stays text; but when they share a line, the table would take
 * that side's words with it, so null: the whole is built as one table and nothing is lost.
 */
private fun sideGrids(halves: List<List<LineRow>>, grids: List<Grid?>): List<Grid>? {
    val built = grids.filterNotNull()
    val failed = halves.zip(grids).filter { it.second == null }.flatMapTo(HashSet()) { it.first.lines() }
    return built.takeIf { it.isNotEmpty() && it.none { grid -> grid.lines.any { line -> line in failed } } }
}

/** The lines the cells of these line rows come from. */
private fun Collection<LineRow>.lines(): Set<TextLine> = flatMapTo(HashSet()) { row -> row.cells.map { it.line } }

/** The spans of these line rows' cells. */
private fun Collection<LineRow>.spans(): Set<TextSpan> = flatMapTo(HashSet()) { row -> row.cells.flatMap { it.spans } }

/**
 * Two tables set side by side on the same row heights, which layout joined row by row into one. The gap between two
 * neighbouring columns is a gutter when it is more than `TableConfig.sideBySideGapRatio` times every other column
 * gap, at least `TableConfig.minSideColumns` columns lie on each side and no cell crosses it. The sides are two tables
 * only when they show it ([TableSides]): the same header on both (a long list set in two halves), or, unless the right
 * side is figures, a row on one side only (tables of different lengths). A single wide table with one wide gap shows
 * neither.
 */
private class SideBySide(private val config: TableConfig) {
    /** [rows] as the line rows of the left table and of the right one, or null when they are one table. */
    fun split(page: PageText, rows: List<LineRow>, columns: Columns): List<List<LineRow>>? {
        val gaps = columns.gaps()
        val k = gaps.indices.maxByOrNull { gaps[it].width() }
        val gutter = k?.takeIf { isGutter(rows, gaps, it) }?.let { gaps[it] } ?: return null
        val middle = (gutter.start + gutter.endInclusive) / 2
        val sides = rows.map { row -> row.cells.partition { it.box.left < middle } }
        val (left, right) = listOf(sides.map { it.first }, sides.map { it.second }).map { side ->
            side.filter { it.isNotEmpty() }.map(::LineRow)
        }
        fun text(cells: List<Cell>) = cells.map { cell ->
            cell.spans.joinToString(" ") { page.text.substring(it.start, it.end).trim() }
        }
        val leftText = left.map { text(it.cells) }
        val rightText = right.map { text(it.cells) }
        val rowText = sides.map { (l, r) -> text(l) to text(r) }
        val headerRows = headerRowCount(rowText.map { (l, r) -> l + r }, config.maxHeaderRows)
        val uneven = !TableSides.readsAsFigures(rightText, config.maxHeaderRows, config.minFigureRows) &&
            TableSides.hasOneSidedRow(rowText, headerRows, config.minSideColumns)
        val two = uneven || TableSides.sameHeader(leftText, rightText, config.minSideColumns)
        return if (two) listOf(left, right) else null
    }

    /** Whether gap [k] of [gaps] (the one after column k) is a gutter between two tables of [rows]. */
    private fun isGutter(rows: List<LineRow>, gaps: List<ClosedFloatingPointRange<Float>>, k: Int): Boolean {
        val gap = gaps[k]
        val widest = gaps.filterIndexed { i, _ -> i != k }.maxOfOrNull { it.width() } ?: return false
        val columnsEachSide = minOf(k + 1, gaps.size - k)
        val crossed = rows.any { row -> row.cells.any { it.box.left < gap.endInclusive && it.box.right > gap.start } }
        return columnsEachSide >= config.minSideColumns && gap.width() > widest * config.sideBySideGapRatio && !crossed
    }

    private fun ClosedFloatingPointRange<Float>.width() = endInclusive - start
}

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

    /** The gaps between neighbouring columns, left to right. */
    fun gaps(): List<ClosedFloatingPointRange<Float>> = extents.zipWithNext { a, b -> a.endInclusive..b.start }

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
