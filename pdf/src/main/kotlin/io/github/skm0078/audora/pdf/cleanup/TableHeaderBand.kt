// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/cleanup/TableHeaderBand.kt
// Purpose: Finds a table's header lines and last row that layout cut off from it into blocks of their own (RI-4a).

package io.github.skm0078.audora.pdf.cleanup

import io.github.skm0078.audora.core.config.TableConfig
import io.github.skm0078.audora.core.model.TextBox
import io.github.skm0078.audora.pdf.extract.PageText
import io.github.skm0078.audora.pdf.layout.FurnitureFinder
import io.github.skm0078.audora.pdf.layout.TextLine

/**
 * A header set over two or three lines ("State / Union" over "Territory") sits further from the first data row than
 * the rows from each other, so XY-cut puts it in blocks of its own. Its lines are short (no run wider than
 * `maxColumnCellEm`) and within the table's width (`headerOverhangEm`), each row near the one below it
 * (`edgeRowGapRatio` of the table's row pitch), up to `maxHeaderRows` rows. The band stops at the first line that is
 * not one: body text or a caption ("Table 2"), which stay text.
 */
internal class TableHeaderBand(private val config: TableConfig, private val rowOverlapRatio: Float) {
    /** The header lines among [free] (lines no table holds) right above [table]; empty when there are none. */
    fun of(page: PageText, table: Set<TextLine>, free: List<TextLine>, bodySize: Float): Set<TextLine> {
        val box = table.unionBox()
        val pitch = typicalPitch(table.mapNotNull { it.box }, rowOverlapRatio)
        if (box == null || pitch == null) return emptySet()
        val above = free.filter { line -> line.box.let { it != null && it.bottom >= box.top - it.height / 2 } }
            .sortedBy { it.box!!.bottom }
        val band = LinkedHashSet<TextLine>()
        var edge: TextBox = box
        var rows = 0
        for (line in above) {
            val lineBox = line.box!!
            val newRow = !overlapsVertically(edge, lineBox, rowOverlapRatio)
            val fits = lineBox.top - edge.top <= pitch * config.edgeRowGapRatio &&
                rows + (if (newRow) 1 else 0) <= config.maxHeaderRows &&
                isHeaderLine(page, line, box, bodySize)
            if (!fits) break
            if (newRow) rows++
            band.add(line)
            edge = lineBox
        }
        return band
    }

    /**
     * The last row, when layout left it out of [table]: right below it (within `edgeRowGapRatio` of its row pitch),
     * two or more short lines side by side, each under a line of the table (a column). One row only: further rows
     * would be more likely narrow columns of body text than table. A note under a table ("ns: not significant") is
     * one line and stays text.
     */
    fun lastRow(page: PageText, table: Set<TextLine>, free: List<TextLine>, bodySize: Float): Set<TextLine> {
        val box = table.unionBox()
        val pitch = typicalPitch(table.mapNotNull { it.box }, rowOverlapRatio)
        if (box == null || pitch == null) return emptySet()
        val row = rowsOf(free.filter { line -> line.box.let { it != null && it.top <= box.bottom + it.height / 2 } })
            .firstOrNull().orEmpty()
        val lowest = table.mapNotNull { it.box?.top }.minOrNull() ?: box.top
        val isRow = row.size >= 2 && lowest - row.maxOf { it.box!!.top } <= pitch * config.edgeRowGapRatio &&
            row.all { line -> isHeaderLine(page, line, box, bodySize) && underColumn(line, table) }
        return if (isRow) row.toSet() else emptySet()
    }

    /** Boxed [lines] grouped into rows of vertically overlapping lines, top to bottom. */
    private fun rowsOf(lines: List<TextLine>): List<List<TextLine>> {
        val rows = ArrayList<List<TextLine>>()
        for (line in lines.sortedByDescending { it.box!!.top }) {
            val last = rows.lastOrNull()
            if (last != null && overlapsVertically(last.first().box!!, line.box!!, rowOverlapRatio)) {
                rows[rows.lastIndex] = last + line
            } else {
                rows.add(listOf(line))
            }
        }
        return rows
    }

    private fun underColumn(line: TextLine, table: Set<TextLine>): Boolean {
        val box = line.box ?: return false
        return table.any { cell -> cell.box?.let { minOf(it.right, box.right) > maxOf(it.left, box.left) } == true }
    }

    private fun isHeaderLine(page: PageText, line: TextLine, table: TextBox, bodySize: Float): Boolean {
        val box = line.box ?: return false
        val em = if (line.size > 0f) line.size else bodySize
        val overhang = config.headerOverhangEm * em
        val inside = box.left >= table.left - overhang && box.right <= table.right + overhang
        val short = line.spans.all { span ->
            val boxes = (span.start until span.end).mapNotNull(page::boxAt)
            boxes.isNotEmpty() && boxes.maxOf { it.right } - boxes.minOf { it.left } <= config.maxColumnCellEm * em
        }
        val text = line.spans.joinToString(" ") { page.text.substring(it.start, it.end) }
        return inside && short && !FurnitureFinder.opensWithCaptionLabel(text)
    }
}
