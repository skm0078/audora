// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/ocr/RuledTables.kt
// Purpose: Rebuilds a scanned ruled table from its rules: rows and columns between them, OCR'd words in the cells.

package io.github.skm0078.audora.pdf.ocr

import io.github.skm0078.audora.core.config.TableConfig
import io.github.skm0078.audora.core.model.TextBox
import io.github.skm0078.audora.pdf.cleanup.headerRowCount
import io.github.skm0078.audora.pdf.extract.PageText
import io.github.skm0078.audora.pdf.layout.TextSpan
import kotlin.math.abs

/**
 * Tesseract reads a ruled table cell by cell, often a column at a time and out of order, so the table comes from its
 * rules instead (in the table frame: PDF points, y up, text upright).
 *
 * 1. **Edges.** Rules within `TableConfig.ruleSnapEm` of each other across their length are one edge (a rule broken
 *    by the scan, a double rule).
 * 2. **Grids.** A horizontal and a vertical edge meet when each reaches the other (give or take the snap). Edges that
 *    meet, directly or through others, are one grid; a grid of at least three of each (two rows, two columns) is a
 *    table. A box around a title has two of each and is not.
 * 3. **Cells.** Neighbouring slots between the edges are one cell where no rule runs between them (a cell spanning
 *    columns or rows); slots that join into anything but a rectangle are the space around a box inside a frame, not a
 *    cell. Each word goes to the cell holding its centre; a cell spanning rows belongs to its first.
 * 4. **Rule glyphs.** A word no wider than the snap on a vertical edge is a rule Tesseract read as "|" or "।": taken
 *    from the text but kept out of the cells.
 * 5. **Runs of rows.** Rows go where no word is (the gap of a double border), and the grid is cut where a row is split
 *    differently from the one above (`MIN_SAME_WALLS`): a footer or title across the grid is text, not a row.
 * 6. **Header.** The rows before the first with a number ([headerRowCount]). Fewer than `minFilledShare` of a run's
 *    slots filled is a picture's lines, not a table.
 */
internal class RuledTables(private val config: TableConfig, em: Float) {
    private val snap = config.ruleSnapEm * em

    /** The ruled tables on [page], and the words of every row of cells side by side in a ruled grid. */
    fun find(page: PageText, words: List<TableWord>, rules: List<TextBox>): RuledPage {
        val horizontal = edges(
            rules.filter { it.width >= it.height }.map {
                Edge.Part(
                    (it.top + it.bottom) / 2,
                    it.left..it.right,
                )
            },
        )
        val vertical = edges(
            rules.filter { it.width < it.height }.map {
                Edge.Part(
                    (it.left + it.right) / 2,
                    it.bottom..it.top,
                )
            },
        )
        val found = grids(horizontal, vertical).map { (rows, columns) -> tables(page, words, rows, columns) }
        return RuledPage(found.flatMap { it.tables }, found.flatMapTo(HashSet()) { it.claimed })
    }

    /** [parts] grouped into edges: parts whose positions are within the snap of the edge's last one. */
    private fun edges(parts: List<Edge.Part>): List<Edge> {
        val edges = ArrayList<MutableList<Edge.Part>>()
        for (part in parts.sortedBy { it.at }) {
            val last = edges.lastOrNull()
            if (last != null && part.at - last.last().at <= snap) last.add(part) else edges.add(mutableListOf(part))
        }
        return edges.map { Edge(it.map { p -> p.at }.average().toFloat(), it.map { p -> p.span }) }
    }

    /** Groups of edges that meet: each its horizontal edges top to bottom and vertical ones left to right. */
    private fun grids(horizontal: List<Edge>, vertical: List<Edge>): List<Pair<List<Edge>, List<Edge>>> {
        val parent = IntArray(horizontal.size + vertical.size) { it }
        fun root(i: Int): Int = if (parent[i] == i) i else root(parent[i]).also { parent[i] = it }
        for ((h, row) in horizontal.withIndex()) {
            for ((v, column) in vertical.withIndex()) {
                if (row.covers(column.at, snap) && column.covers(row.at, snap)) {
                    parent[root(horizontal.size + v)] = root(h)
                }
            }
        }
        val groups = parent.indices.groupBy(::root).values
        return groups.map { group ->
            val rows = group.filter { it < horizontal.size }.map { horizontal[it] }.sortedByDescending { it.at }
            val columns = group.filter {
                it >= horizontal.size
            }.map { vertical[it - horizontal.size] }.sortedBy { it.at }
            rows to columns
        }.filter { (rows, columns) -> rows.size >= MIN_EDGES && columns.size >= MIN_EDGES }
    }

    /**
     * The tables between [rows] edges and [columns] edges: one per run of rows split the same way (a footer or title
     * row across the grid is not one of its rows), each holding enough words to be a table. A row of two or more
     * filled cells that makes no table (one tall row of ruled columns) is claimed too: its words keep OCR's order, a
     * column at a time, instead of being cut into rows line by line.
     */
    private fun tables(page: PageText, words: List<TableWord>, rows: List<Edge>, columns: List<Edge>): RuledPage {
        val slots = Slots(rows, columns, snap)
        val inGrid = words.filter { slots.cellOf(it.box) != null }
        val placed = inGrid.filterNot { isRuleGlyph(it.box, columns) }.groupBy { requireNotNull(slots.cellOf(it.box)) }
        val usedRows = (0 until slots.rows).filter { r ->
            (0 until slots.columns).any { c -> placed.containsKey(slots.anchorOf(r, c)) }
        }
        val tables = runsOf(usedRows, slots).mapNotNull { run -> table(page, slots, placed, inGrid, run) }
        val sideBySide = usedRows.filter { r ->
            (0 until slots.columns).count { c -> placed.containsKey(slots.anchorOf(r, c)) } >=
                2
        }
        val claimed = inGrid.filter { slots.rowOf(requireNotNull(slots.cellOf(it.box))) in sideBySide }
        return RuledPage(tables, tables.flatMapTo(claimed.mapTo(HashSet()) { it.span }) { it.words })
    }

    /** [rows] cut where a row's walls differ from the row above's (`MIN_SAME_WALLS`, Jaccard). */
    private fun runsOf(rows: List<Int>, slots: Slots): List<List<Int>> {
        val runs = ArrayList<MutableList<Int>>()
        var walls: Set<Int>? = null
        for (r in rows) {
            val current = slots.wallsOf(r)
            val previous = walls
            if (previous != null &&
                similarity(previous, current) >= MIN_SAME_WALLS
            ) {
                runs.last().add(r)
            } else {
                runs.add(mutableListOf(r))
            }
            walls = current
        }
        return runs
    }

    private fun similarity(a: Set<Int>, b: Set<Int>): Float {
        val union = (a + b).size
        return if (union == 0) 1f else (a intersect b).size.toFloat() / union
    }

    /**
     * A "|", "।" or "!" Tesseract read from a rule: no wider than the snap, its centre within the snap of a vertical
     * edge. It is still taken from the text, but kept out of the cells.
     */
    private fun isRuleGlyph(box: TextBox, columns: List<Edge>): Boolean {
        val x = (box.left + box.right) / 2
        val y = (box.top + box.bottom) / 2
        return box.width <= snap && columns.any { abs(it.at - x) <= snap && it.covers(y, snap) }
    }

    /** The table of [rows] of [slots], or null when it holds too few words to be one. */
    private fun table(
        page: PageText,
        slots: Slots,
        placed: Map<Int, List<TableWord>>,
        inGrid: List<TableWord>,
        rows: List<Int>,
    ): OcrTable? {
        val usedColumns = (0 until slots.columns).filter { c ->
            rows.any { r -> placed.containsKey(slots.anchorOf(r, c)) }
        }
        val filled = rows.sumOf { r -> usedColumns.count { c -> placed.containsKey(slots.cellAt(r, c)) } }
        val isTable = rows.size >= 2 && usedColumns.size >= 2 &&
            filled >= rows.size * usedColumns.size * config.minFilledShare
        if (!isTable) return null
        val cells = rows.map { r ->
            usedColumns.map { c -> placed[slots.anchorOf(r, c)].orEmpty().sortedWith(READING).map { it.span } }
        }
        val texts = cells.map { row ->
            row.map { spans -> spans.joinToString(" ") { page.text.substring(it.start, it.end) } }
        }
        val headerRows = headerRowCount(texts, config.maxHeaderRows)
        val header = cells.take(headerRows).reduce { a, b -> a.zip(b) { x, y -> x + y } }
        // Rule glyphs too: they are read from the table, not part of the text around it.
        val taken = inGrid.filter { slots.rowOf(requireNotNull(slots.cellOf(it.box))) in rows }
        return OcrTable(header, cells.drop(headerRows), taken.mapTo(HashSet()) { it.span })
    }

    /** A rule edge: where it sits across ([at]) and the stretches it runs along. */
    private class Edge(val at: Float, val spans: List<ClosedFloatingPointRange<Float>>) {
        fun covers(value: Float, snap: Float): Boolean = spans.any {
            value >= it.start - snap &&
                value <= it.endInclusive + snap
        }

        class Part(val at: Float, val span: ClosedFloatingPointRange<Float>)
    }

    /** The slots between consecutive edges, joined into cells where no rule separates them. */
    private class Slots(
        private val rowEdges: List<Edge>,
        private val columnEdges: List<Edge>,
        private val snap: Float,
    ) {
        val rows = rowEdges.size - 1
        val columns = columnEdges.size - 1
        private val parent = IntArray(rows * columns) { it }

        init {
            for (r in 0 until rows) {
                for (c in 0 until columns) {
                    val midY = (rowEdges[r].at + rowEdges[r + 1].at) / 2
                    val midX = (columnEdges[c].at + columnEdges[c + 1].at) / 2
                    if (c + 1 < columns && !columnEdges[c + 1].covers(midY, snap)) join(index(r, c), index(r, c + 1))
                    if (r + 1 < rows && !rowEdges[r + 1].covers(midX, snap)) join(index(r, c), index(r + 1, c))
                }
            }
        }

        /** The cells whose slots fill a rectangle; the others (space around a box inside a frame) are not cells. */
        private val rectangular: Set<Int> by lazy {
            val slots = (0 until rows * columns).groupBy { root(it) }
            slots.filter { (_, members) ->
                val height = members.maxOf { it / columns } - members.minOf { it / columns } + 1
                val width = members.maxOf { it % columns } - members.minOf { it % columns } + 1
                height * width == members.size
            }.keys
        }

        /** The cell (its root slot) holding [box]'s centre; null outside the grid or in a space that is not a cell. */
        fun cellOf(box: TextBox): Int? {
            val x = (box.left + box.right) / 2
            val y = (box.top + box.bottom) / 2
            val r = (0 until rows).firstOrNull { y <= rowEdges[it].at && y >= rowEdges[it + 1].at }
            val c = (0 until columns).firstOrNull { x >= columnEdges[it].at && x <= columnEdges[it + 1].at }
            return if (r == null || c == null) null else cellAt(r, c).takeIf { it in rectangular }
        }

        fun cellAt(r: Int, c: Int): Int = root(index(r, c))

        /** The row of [cell]'s first slot. */
        fun rowOf(cell: Int): Int = cell / columns

        /** The inner vertical edges that run through row [r]: how the row is split into cells. */
        fun wallsOf(r: Int): Set<Int> {
            val midY = (rowEdges[r].at + rowEdges[r + 1].at) / 2
            return (1 until columns).filterTo(HashSet()) { c -> columnEdges[c].covers(midY, snap) }
        }

        /**
         * The cell whose first slot (top row, left column) is ([r], [c]); -1 when that slot is inside another. A
         * cell's root is its first slot: joining keeps the smaller root.
         */
        fun anchorOf(r: Int, c: Int): Int = index(r, c).takeIf { cellAt(r, c) == it } ?: NOT_ANCHOR

        private fun index(r: Int, c: Int) = r * columns + c

        private fun root(i: Int): Int = if (parent[i] == i) i else root(parent[i]).also { parent[i] = it }

        private fun join(a: Int, b: Int) {
            val ra = root(a)
            val rb = root(b)
            if (ra != rb) parent[maxOf(ra, rb)] = minOf(ra, rb)
        }

        private companion object {
            const val NOT_ANCHOR = -1
        }
    }

    private companion object {
        /** Two edges each way bound one row or column; a table has at least two of each. */
        const val MIN_EDGES = 3

        /** Rows whose walls overlap less than this (Jaccard) are not rows of one table. */
        const val MIN_SAME_WALLS = 0.5f

        /** A cell's words line by line, top to bottom, then left to right. */
        val READING = compareByDescending<TableWord> { it.lineTop }.thenBy { it.box.left }
    }
}

/** A word OCR read: its span of the page text, its box in the table frame and the top of its OCR line. */
internal class TableWord(val span: TextSpan, val box: TextBox, val lineTop: Float)

/** A scanned page's ruled tables, and every word they took or that sits side by side in ruled cells ([claimed]). */
internal class RuledPage(val tables: List<OcrTable>, val claimed: Set<TextSpan>)

/** A table found on a scanned page: its header and rows of cells (word spans), and every word it took. */
internal class OcrTable(
    val header: List<List<TextSpan>>,
    val rows: List<List<List<TextSpan>>>,
    val words: Set<TextSpan>,
)
