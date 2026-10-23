// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/units/TableRowText.kt
// Purpose: A table row unit's spoken text and where each of its cells lies in it (RI-4a, ADR-035).

package io.github.skm0078.audora.pdf.units

import io.github.skm0078.audora.core.model.CellSpan
import io.github.skm0078.audora.pdf.layout.ReadingText
import io.github.skm0078.audora.pdf.layout.TableCells

/**
 * The unit text is the reading text of the row with runs of whitespace folded to one space, like every unit's, and
 * dot leaders read cell by cell ([Leaders]): a leader ending its cell is left out (a contents row's
 * "Introduction ........" cell is "Introduction"), one inside a cell is a pause ("…"), and a cell holding only a
 * rule ("-----" for nil) is a dash, so its column is still read. Leaders never run from one cell into the next:
 * "Wheat | .. | .. | .. | 12" keeps its three cells.
 * A cell's span covers the characters whose source offset lies in one of the cell's spans; an empty cell (or one
 * the repair emptied) is an empty span where the next cell starts.
 */
internal class TableRowText(val text: String, val cells: List<CellSpan>) {
    companion object {
        fun of(reading: ReadingText, start: Int, end: Int, table: TableCells, minLeaderRun: Int): TableRowText {
            val columnOf = HashMap<Int, Int>()
            table.cells.forEachIndexed { column, spans ->
                spans.forEach { span -> for (offset in span.start until span.end) columnOf[offset] = column }
            }
            val column = { offset: Int -> columnOf[reading.sourceOffset(offset)] }
            val (leaders, readings) = cellLeaders(reading.text, start, end, column, minLeaderRun)
            val folded = FoldedText(end - start)
            val out = folded.text
            val first = IntArray(table.columnCount) { -1 }
            val last = IntArray(table.columnCount) { -1 }
            for (offset in start until end) {
                val leader = leaders[offset - start]
                val ch = if (leader) readings[offset - start] else reading.text[offset]
                val at = folded.add(ch, leader, ch?.isWhitespace() == true)
                column(offset)?.takeIf { at >= 0 }?.let { c ->
                    if (first[c] < 0) first[c] = at
                    last[c] = at + 1
                }
            }
            val spans = ArrayList<CellSpan>(table.columnCount)
            for (c in table.columnCount - 1 downTo 0) {
                val next = spans.lastOrNull()?.start ?: out.length
                spans.add(if (first[c] < 0) CellSpan(next, next) else CellSpan(first[c], last[c]))
            }
            return TableRowText(out.toString(), spans.reversed())
        }

        /**
         * The leaders of [text] from [start] to [end] (offsets from [start]) and how each reads, found cell by cell:
         * the row is cut where the [column] of the next non-space character changes.
         */
        private fun cellLeaders(
            text: String,
            start: Int,
            end: Int,
            column: (Int) -> Int?,
            minRun: Int,
        ): Pair<BooleanArray, Map<Int, Char?>> {
            val mask = BooleanArray(end - start)
            val readings = HashMap<Int, Char?>()
            var cellStart = start
            var cellColumn: Int? = null
            fun close(cellEnd: Int) {
                val cell = text.subSequence(cellStart, cellEnd)
                val cellMask = Leaders.mask(cell, minRun)
                cellMask.copyInto(mask, cellStart - start)
                Leaders.readings(cell, cellMask, alone = Leaders.NIL).forEach { (at, reading) ->
                    readings[at + cellStart - start] = reading
                }
            }
            for (offset in start until end) {
                if (text[offset].isWhitespace()) continue
                val now = column(offset)
                if (offset > cellStart && now != cellColumn) {
                    close(offset)
                    cellStart = offset
                }
                cellColumn = now
            }
            close(end)
            return mask to readings
        }
    }
}
