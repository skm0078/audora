// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/cleanup/TableFinder.kt
// Purpose: Finds the tables on a laid-out page: runs of aligned cell rows and the columns paired with them.

package io.github.skm0078.audora.pdf.cleanup

import io.github.skm0078.audora.core.config.CleanupConfig
import io.github.skm0078.audora.core.config.TableConfig
import io.github.skm0078.audora.core.model.TextBox
import io.github.skm0078.audora.pdf.extract.PageText
import io.github.skm0078.audora.pdf.layout.PageLayout
import io.github.skm0078.audora.pdf.layout.TextLine
import kotlin.math.abs

/**
 * Spec §5 step 5 ("figure/table interiors"), ADR-020. Read as running text, a table is a stream of disconnected
 * cells; [TableGrid] turns each table found here into rows of cells instead (RI-4a, ADR-035).
 *
 * 1. **Cell rows.** Inside one layout block, a line whose runs are [CleanupConfig.tableGapEm] or more apart is a
 *    row candidate. Justified body text never has such gaps; Bengali glyph boxes leave gaps of about 1 em, which
 *    is why the bar is higher than the layout's own run gap. A line ending in an equation number ("(12)") is a
 *    display equation, not a row.
 * 2. **Runs.** At least [CleanupConfig.minTableRows] candidates in a row (one other line may sit between two, a
 *    row with empty cells) whose columns line up: each counted row shares a cell start with another row of the
 *    run, within [CleanupConfig.tableAlignEm]. Text that only happens to have wide gaps does not line up. A line
 *    split into runs right above or below a run (a header row set tighter) joins it when it is near
 *    (`TableConfig.edgeRowGapRatio`).
 * 3. **Paired columns.** XY-cut often cuts a table into one block per column (P2-02), so a column of one-run
 *    cells sits in a block of its own. Whole paragraphs of such a block are part of the table when every line
 *    pairs with a distinct row of a run in another block, and so are short lines within the run's height of which
 *    enough pair (`TableConfig.maxColumnCellEm`, `minColumnPairShare`). Body text beside a table does neither.
 * 4. **One table per region.** Runs that share lines are one table, and so are runs over the same rows that overlap
 *    across, or whose lines mostly sit beside each other: XY-cut cut one table into blocks. They stay two tables set
 *    side by side when the gutter is wider than `TableConfig.sideBySideGapRatio` times their own widest cell gap,
 *    unless the right one is the table's figures ([TableSides]). (Joined row by row by layout, two such tables are
 *    split by [TableGrid].)
 * 5. **Header band.** Short lines right above a table, within its width, are its header set apart in blocks of
 *    their own (a two-line header: "State / Union" over "Territory"), see [TableHeaderBand]; so are short lines side
 *    by side right below it, under its columns (a last row of one-run cells, a total).
 *
 * Figures get no rule of their own: extraction has no image bounds; labels drawn as text in a grid (axis ticks,
 * legends) fall under the rules above.
 *
 * @param rowOverlapRatio a line pairs with a row when they overlap vertically by this share of the smaller height
 *   (`LayoutConfig.lineOverlapRatio`, the same test that builds lines).
 */
internal class TableFinder(private val config: CleanupConfig, private val rowOverlapRatio: Float) {
    private val headerBand = TableHeaderBand(config.tables, rowOverlapRatio)

    /** The page's tables, each the lines it covers (possibly across blocks), in reading order of their first line. */
    fun find(page: PageText, layout: PageLayout): List<Set<TextLine>> {
        val blocks = layout.blocks.map { block -> block.paragraphs.map { it.lines } }
        val cellGaps = HashMap<TextLine, Float>()
        val runs = blocks.flatMapIndexed { index, paragraphs ->
            val rows = paragraphs.flatten().map { Row.of(page, it, layout.bodySize, config) }
            rows.forEach { cellGaps[it.line] = it.widestGap }
            cellRuns(rows).map { Run(index, it) }
        }
        if (runs.isEmpty()) return emptyList()
        // Rows of another run are no paired column: whether two runs are one table is step 4's call.
        val inRuns = runs.flatMapTo(HashSet()) { it.lines }
        val regions = runs.map { run ->
            val columns = blocks.flatMapIndexed { index, paragraphs ->
                val free = paragraphs.map { lines -> lines.filterNot { it in inRuns } }.filter { it.isNotEmpty() }
                if (index == run.block) emptyList() else pairedColumns(free, run.lines, layout.bodySize)
            }
            (run.lines + columns).toMutableSet()
        }
        val lines = blocks.flatten().flatten()
        val merged = mergeOverlapping(regions, page, cellGaps)
        val taken = merged.flatten().toSet()
        return merged.map { table ->
            val free = lines - taken
            table + headerBand.of(page, table, free, layout.bodySize) +
                headerBand.lastRow(page, table, free, layout.bodySize)
        }
            .sortedBy { region -> lines.indexOfFirst { it in region } }
    }

    /** Regions sharing a line, or one table cut by XY-cut into blocks ([oneTableCut]), are one table. */
    private fun mergeOverlapping(
        regions: List<MutableSet<TextLine>>,
        page: PageText,
        cellGaps: Map<TextLine, Float>,
    ): List<Set<TextLine>> {
        val merged = ArrayList<MutableSet<TextLine>>()
        for (region in regions) {
            val joined = merged.filter { other ->
                other.any { it in region } ||
                    oneTableCut(other, region, page, cellGaps)
            }
            joined.forEach { region.addAll(it) }
            merged.removeAll(joined.toSet())
            merged.add(region)
        }
        return merged
    }

    /**
     * Whether regions [a] and [b], side by side over the same rows, are one table XY-cut cut apart: they overlap
     * across, or at least `TableConfig.minColumnPairShare` of the smaller one's lines sit beside lines of the other (a
     * cell spanning rows sits beside several) and either the gutter between them is no wider than
     * `TableConfig.sideBySideGapRatio` times the widest gap between cells of their lines, or, across a wider gutter,
     * they do not show they are two tables ([TableSides.twoTables], the grid's rule too).
     */
    private fun oneTableCut(
        a: Set<TextLine>,
        b: Set<TextLine>,
        page: PageText,
        cellGaps: Map<TextLine, Float>,
    ): Boolean {
        val boxA = a.unionBox()
        val boxB = b.unionBox()
        if (boxA == null || boxB == null || !overlapsVertically(boxA, boxB, rowOverlapRatio)) return false
        val (fewer, more) = if (a.size <= b.size) a to b else b to a
        val beside = fewer.count { line -> besideAny(line, more, rowOverlapRatio) }
        val gutter = maxOf(boxB.left - boxA.right, boxA.left - boxB.right)
        val ownGap = (a + b).maxOf { cellGaps[it] ?: 0f }
        val besideRows = beside >= fewer.size * config.tables.minColumnPairShare
        val closeAsColumns = gutter <= ownGap * config.tables.sideBySideGapRatio
        val (left, right) = if (boxA.left <= boxB.left) a to b else b to a
        // Overlapping across (a header over both halves), they cannot be two tables side by side.
        return gutter <= 0f ||
            (besideRows && (closeAsColumns || !TableSides.twoTables(sideRows(left, right, page), config.tables)))
    }

    private class Run(val block: Int, val lines: List<TextLine>)

    /** A line with what the rules need: is it split into cells, and where do its cells (after the first) start. */
    private class Row(
        val line: TextLine,
        val isCandidate: Boolean,
        val cellStarts: List<Float>,
        val em: Float,
        val widestGap: Float = 0f,
    ) {
        /** Several runs, however close: a table's header or last row joins a run next to it. */
        val isSplit: Boolean get() = line.spans.size >= 2

        companion object {
            fun of(page: PageText, line: TextLine, bodySize: Float, config: CleanupConfig): Row {
                val em = if (line.size > 0f) line.size else bodySize
                val splittable = line.spans.size >= 2 && line.box != null
                if (!splittable || em <= 0f || endsInEquationNumber(page, line)) {
                    return Row(line, isCandidate = false, cellStarts = emptyList(), em = em)
                }
                val boxes = line.spans.map { span -> (span.start until span.end).mapNotNull(page::boxAt) }
                val lefts = boxes.map { b -> b.minOfOrNull { it.left } }
                val rights = boxes.map { b -> b.maxOfOrNull { it.right } }
                val widestGap = (1 until boxes.size).maxOf { j ->
                    val right = rights[j - 1]
                    val left = lefts[j]
                    if (right != null && left != null) left - right else 0f
                }
                return Row(line, widestGap >= config.tableGapEm * em, lefts.drop(1).filterNotNull(), em, widestGap)
            }

            private fun endsInEquationNumber(page: PageText, line: TextLine): Boolean {
                val last = line.spans.last()
                return EQUATION_NUMBER.matches(page.text.substring(last.start, last.end).trim())
            }

            private val EQUATION_NUMBER = Regex("\\(\\p{Nd}+(?:\\.\\p{Nd}+)*[a-z]?\\)")
        }
    }

    /** Runs of aligned row candidates in one block, in order. */
    private fun cellRuns(rows: List<Row>): List<List<TextLine>> {
        val runs = ArrayList<List<TextLine>>()
        var i = 0
        while (i < rows.size) {
            if (rows[i].isCandidate) {
                val end = runEnd(rows, i)
                if (alignedRows(rows.subList(i, end + 1).filter { it.isCandidate }) >= config.minTableRows) {
                    runs.add(withEdgeRows(rows, i, end).map { it.line })
                }
                i = end + 1
            } else {
                i++
            }
        }
        return runs
    }

    /** Last candidate of the run starting at [start]; one other line may sit between two candidates. */
    private fun runEnd(rows: List<Row>, start: Int): Int {
        fun candidate(index: Int) = index < rows.size && rows[index].isCandidate
        var end = start
        var j = start + 1
        while (candidate(j) || candidate(j + 1)) {
            if (candidate(j)) end = j
            j++
        }
        return end
    }

    /**
     * A header row ("symbol | what it is called") is often set closer than the body rows, so it joins the run, if it
     * is near: a split line far below a table (a list of numbers after it) is not its last row.
     */
    private fun withEdgeRows(rows: List<Row>, start: Int, end: Int): List<Row> {
        val pitch = typicalPitch(rows.subList(start, end + 1).mapNotNull { it.line.box }, rowOverlapRatio)
        fun near(a: Row, b: Row): Boolean {
            val top = a.line.box?.top
            val other = b.line.box?.top
            return pitch == null || top == null || other == null ||
                abs(top - other) <= pitch * config.tables.edgeRowGapRatio
        }
        val first = if (start > 0 && rows[start - 1].isSplit && near(rows[start - 1], rows[start])) start - 1 else start
        val last = if (end + 1 < rows.size && rows[end + 1].isSplit && near(rows[end + 1], rows[end])) end + 1 else end
        return rows.subList(first, last + 1)
    }

    /** How many of [candidates] share a cell start with another one. */
    private fun alignedRows(candidates: List<Row>): Int = candidates.count { row ->
        val tolerance = config.tableAlignEm * row.em
        candidates.any { other ->
            other !== row && row.cellStarts.any { x -> other.cellStarts.any { abs(it - x) <= tolerance } }
        }
    }

    /**
     * Lines of one block that are columns of the table whose rows are [rows]: whole paragraphs whose every line pairs
     * with a distinct row; and short cells lying within the table's height of which enough pair with a row (a cell
     * spanning two rows sits between them: "1st" beside two names).
     */
    private fun pairedColumns(paragraphs: List<List<TextLine>>, rows: List<TextLine>, bodySize: Float): List<TextLine> {
        val lineForLine = paragraphs.filter { pairsLineForLine(it, rows) }.flatten()
        val span = rows.unionBox() ?: return lineForLine
        val inside = paragraphs.flatten().filter { line ->
            val box = line.box
            box != null && box.top <= span.top + box.height / 2 && box.bottom >= span.bottom - box.height / 2
        }
        return if (isCellColumn(inside, rows, bodySize)) (lineForLine + inside).distinct() else lineForLine
    }

    private fun pairsLineForLine(column: List<TextLine>, rows: List<TextLine>): Boolean {
        if (column.size < config.minTableRows) return false
        val paired = column.map { line -> pairedRow(line, rows, rowOverlapRatio) }
        return paired.none { it == null } && paired.toSet().size == paired.size
    }

    private fun isCellColumn(lines: List<TextLine>, rows: List<TextLine>, bodySize: Float): Boolean {
        if (lines.isEmpty()) return false
        val em = lines.map { if (it.size > 0f) it.size else bodySize }.average().toFloat()
        val narrow = em > 0f && lines.mapNotNull { it.box?.width }.average() <= config.tables.maxColumnCellEm * em
        val paired = lines.mapNotNull { pairedRow(it, rows, rowOverlapRatio) }.toSet().size
        return narrow && paired >= config.minTableRows - 1 && paired >= lines.size * config.tables.minColumnPairShare
    }
}

/**
 * The lines of [left] and [right] as rows across both, top to bottom: a line starts a new row unless its middle lies
 * within the first line of the row; each row is its left lines' cells and its right lines' cells.
 */
private fun sideRows(
    left: Set<TextLine>,
    right: Set<TextLine>,
    page: PageText,
): List<Pair<List<String>, List<String>>> {
    val boxed = (left + right).mapNotNull { line -> line.box?.let { line to it } }.sortedByDescending { it.second.top }
    val rows = ArrayList<MutableList<Pair<TextLine, TextBox>>>()
    for (entry in boxed) {
        val first = rows.lastOrNull()?.first()?.second
        val middle = (entry.second.top + entry.second.bottom) / 2
        if (first != null && middle in first.bottom..first.top) rows.last().add(entry) else rows.add(arrayListOf(entry))
    }
    fun cells(row: List<Pair<TextLine, TextBox>>, side: Set<TextLine>) = row.filter { it.first in side }
        .sortedBy { it.second.left }
        .flatMap { (line, _) -> line.spans.map { page.text.substring(it.start, it.end).trim() } }
    return rows.map { row -> cells(row, left) to cells(row, right) }
}
