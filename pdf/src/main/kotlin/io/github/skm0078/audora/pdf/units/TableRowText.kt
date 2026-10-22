// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/units/TableRowText.kt
// Purpose: A table row unit's spoken text and where each of its cells lies in it (RI-4a, ADR-035).

package io.github.skm0078.audora.pdf.units

import io.github.skm0078.audora.core.model.CellSpan
import io.github.skm0078.audora.pdf.layout.ReadingText
import io.github.skm0078.audora.pdf.layout.TableCells

/**
 * The unit text is the reading text of the row with runs of whitespace folded to one space, like every unit's.
 * A cell's span covers the characters whose source offset lies in one of the cell's spans; an empty cell (or one
 * the repair emptied) is an empty span where the next cell starts.
 */
internal class TableRowText(val text: String, val cells: List<CellSpan>) {
    companion object {
        fun of(reading: ReadingText, start: Int, end: Int, table: TableCells): TableRowText {
            val columnOf = HashMap<Int, Int>()
            table.cells.forEachIndexed { column, spans ->
                spans.forEach { span -> for (offset in span.start until span.end) columnOf[offset] = column }
            }
            val out = StringBuilder()
            val first = IntArray(table.columnCount) { -1 }
            val last = IntArray(table.columnCount) { -1 }
            var pendingSpace = false
            for (offset in start until end) {
                val ch = reading.text[offset]
                if (ch.isWhitespace()) {
                    pendingSpace = out.isNotEmpty()
                    continue
                }
                if (pendingSpace) out.append(' ')
                pendingSpace = false
                val source = reading.sourceOffset(offset)
                columnOf[source]?.let { column ->
                    if (first[column] < 0) first[column] = out.length
                    last[column] = out.length + 1
                }
                out.append(ch)
            }
            val spans = ArrayList<CellSpan>(table.columnCount)
            for (column in table.columnCount - 1 downTo 0) {
                val next = spans.lastOrNull()?.start ?: out.length
                spans.add(if (first[column] < 0) CellSpan(next, next) else CellSpan(first[column], last[column]))
            }
            return TableRowText(out.toString(), spans.reversed())
        }
    }
}
