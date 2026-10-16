// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/cleanup/TableFinder.kt
// Purpose: Finds table interiors on a laid-out page: runs of aligned cell rows and the columns paired with them.

package io.github.skm0078.audora.pdf.cleanup

import io.github.skm0078.audora.core.config.CleanupConfig
import io.github.skm0078.audora.core.model.TextBox
import io.github.skm0078.audora.pdf.extract.PageText
import io.github.skm0078.audora.pdf.layout.PageLayout
import io.github.skm0078.audora.pdf.layout.TextLine
import kotlin.math.abs

/**
 * Spec §5 step 5 ("figure/table interiors"), ADR-020. Read aloud, a table is a stream of disconnected cells; the
 * caption above it says what it holds and is kept.
 *
 * 1. **Cell rows.** Inside one layout block, a line whose runs are [CleanupConfig.tableGapEm] or more apart is a
 *    row candidate. Justified body text never has such gaps; Bengali glyph boxes leave gaps of about 1 em, which
 *    is why the bar is higher than the layout's own run gap. A line ending in an equation number ("(12)") is a
 *    display equation, not a row.
 * 2. **Runs.** At least [CleanupConfig.minTableRows] candidates in a row (one other line may sit between two, a
 *    row with empty cells) whose columns line up: each counted row shares a cell start with another row of the
 *    run, within [CleanupConfig.tableAlignEm]. Text that only happens to have wide gaps does not line up. A line
 *    split into runs right above or below a run (a header row set tighter) joins it.
 * 3. **Paired columns.** XY-cut often cuts a table into one block per column (P2-02), so a column of one-run
 *    cells sits in a block of its own. Whole paragraphs of such a block are part of the table when every line
 *    pairs with a distinct row of a run in another block. Body text beside a table does not pair line for line.
 *
 * Figures get no rule of their own: extraction has no image bounds; labels drawn as text in a grid (axis ticks,
 * legends) fall under the rules above.
 *
 * @param rowOverlapRatio a line pairs with a row when they overlap vertically by this share of the smaller height
 *   (`LayoutConfig.lineOverlapRatio`, the same test that builds lines).
 */
internal class TableFinder(private val config: CleanupConfig, private val rowOverlapRatio: Float) {
    fun find(page: PageText, layout: PageLayout): Set<TextLine> {
        val blocks = layout.blocks.map { block -> block.paragraphs.map { it.lines } }
        val runs = blocks.flatMapIndexed { index, paragraphs ->
            val rows = paragraphs.flatten().map { Row.of(page, it, layout.bodySize, config) }
            cellRuns(rows).map { Run(index, it) }
        }
        if (runs.isEmpty()) return emptySet()
        val table = runs.flatMapTo(HashSet()) { it.lines }
        blocks.forEachIndexed { index, paragraphs ->
            table.addAll(pairedColumns(paragraphs, runs.filter { it.block != index }))
        }
        return table
    }

    private class Run(val block: Int, val lines: List<TextLine>)

    /** A line with what the rules need: is it split into cells, and where do its cells (after the first) start. */
    private class Row(val line: TextLine, val isCandidate: Boolean, val cellStarts: List<Float>, val em: Float) {
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
                return Row(line, widestGap >= config.tableGapEm * em, lefts.drop(1).filterNotNull(), em)
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

    /** A header row ("symbol | what it is called") is often set closer than the body rows, so it joins the run. */
    private fun withEdgeRows(rows: List<Row>, start: Int, end: Int): List<Row> {
        val first = if (start > 0 && rows[start - 1].isSplit) start - 1 else start
        val last = if (end + 1 < rows.size && rows[end + 1].isSplit) end + 1 else end
        return rows.subList(first, last + 1)
    }

    /** How many of [candidates] share a cell start with another one. */
    private fun alignedRows(candidates: List<Row>): Int = candidates.count { row ->
        val tolerance = config.tableAlignEm * row.em
        candidates.any { other ->
            other !== row && row.cellStarts.any { x -> other.cellStarts.any { abs(it - x) <= tolerance } }
        }
    }

    /** Whole paragraphs of one block whose lines each pair with a distinct row of a run elsewhere. */
    private fun pairedColumns(paragraphs: List<List<TextLine>>, runs: List<Run>): List<TextLine> {
        if (runs.isEmpty()) return emptyList()
        return paragraphs.filter { paragraph -> runs.any { pairsLineForLine(paragraph, it.lines) } }.flatten()
    }

    private fun pairsLineForLine(column: List<TextLine>, rows: List<TextLine>): Boolean {
        if (column.size < config.minTableRows) return false
        // The one row each line overlaps; null when it overlaps none or several.
        val paired = column.map { line ->
            line.box?.let { box ->
                rows.indices.singleOrNull {
                    rows[it].box?.let { row -> overlapsVertically(box, row) } ==
                        true
                }
            }
        }
        return paired.none { it == null } && paired.toSet().size == paired.size
    }

    private fun overlapsVertically(a: TextBox, b: TextBox): Boolean {
        val overlap = minOf(a.top, b.top) - maxOf(a.bottom, b.bottom)
        return overlap >= minOf(a.height, b.height) * rowOverlapRatio
    }
}
