// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/cleanup/SideBySideGridTest.kt
// Purpose: Two tables side by side where one side is too sparse for a grid: no words are lost either way.

package io.github.skm0078.audora.pdf.cleanup

import io.github.skm0078.audora.core.config.LayoutConfig
import io.github.skm0078.audora.core.config.TableConfig
import io.github.skm0078.audora.core.model.TextBox
import io.github.skm0078.audora.pdf.extract.PageText
import io.github.skm0078.audora.pdf.layout.PageSketch
import io.github.skm0078.audora.pdf.layout.TextLine
import io.github.skm0078.audora.pdf.layout.TextSpan
import org.junit.Assert.assertEquals
import org.junit.Test

/** Cells are 10 pt type, rows far enough apart that none wraps onto the row above. */
class SideBySideGridTest {
    private val grids = TableGrid(TableConfig(), LayoutConfig().lineOverlapRatio)

    private class Cell(val text: String, val left: Float, val top: Float)

    /**
     * Left: three columns, each row below the first holding one cell only (too sparse for a grid). Right: a table of
     * labels with one row more than the left, on its side only.
     */
    private val rows: List<Pair<List<Cell>, List<Cell>>> = (0..ROWS).map { r ->
        val top = TOP - r * PITCH
        val left = when {
            r == 0 -> listOf("A1", "B1", "C1").mapIndexed { c, text -> Cell(text, LEFT + c * COLUMN, top) }
            r < ROWS -> listOf(Cell("k$r", LEFT + (r % 3) * COLUMN, top))
            else -> emptyList()
        }
        val right = listOf(if (r == 0) "Site" else "Ward$r", if (r == 0) "Head" else "Name$r")
            .mapIndexed { c, text -> Cell(text, RIGHT + c * COLUMN, top) }
        left to right
    }

    /** The page and the table's lines: each row one line across the gutter ([shared]), or one line per side. */
    private fun lines(shared: Boolean): Pair<PageText, List<TextLine>> {
        val sketch = PageSketch()
        rows.forEach { (left, right) -> (left + right).forEach { sketch.line(it.text, it.left, it.top) } }
        val page = sketch.build()
        var cursor = 0
        fun line(cells: List<Cell>): TextLine {
            val spans = cells.map { cell ->
                val start = page.text.indexOf(cell.text, cursor)
                cursor = start + cell.text.length
                TextSpan(start, cursor)
            }
            val box = spans.flatMap { (it.start until it.end).mapNotNull(page::boxAt) }.reduce(TextBox::union)
            return TextLine(spans, box, PageSketch.BODY, bold = false)
        }
        val lines = rows.flatMap { (left, right) ->
            if (shared) listOf(line(left + right)) else listOfNotNull(left.ifEmpty { null }?.let(::line), line(right))
        }
        return page to lines
    }

    @Test
    fun `a side too sparse for a grid stays text when it shares no line with the table beside it`() {
        val (page, lines) = lines(shared = false)
        val built = grids.build(page, lines, PageSketch.BODY)
        assertEquals(1, built.size)
        assertEquals(2, built.single().columnCount)
        // Only the right side's lines are the table's: the left side's words are read as text.
        assertEquals(rows.size, built.single().lines.size)
    }

    @Test
    fun `a side too sparse for a grid sharing lines with the table beside it is built into one table, nothing lost`() {
        val (page, lines) = lines(shared = true)
        val built = grids.build(page, lines, PageSketch.BODY)
        assertEquals(1, built.size)
        assertEquals(lines.toSet(), built.single().lines)
        assertEquals(lines.flatMap { it.spans }.toSet(), built.single().spans)
    }

    private companion object {
        const val ROWS = 5
        const val LEFT = 50f
        const val RIGHT = 380f
        const val TOP = 700f
        const val PITCH = 24f
        const val COLUMN = 60f
    }
}
