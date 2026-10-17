// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/cleanup/TableFinderTest.kt
// Purpose: Runs of rows in blocks side by side: one table XY-cut cut apart, or two tables set side by side.

package io.github.skm0078.audora.pdf.cleanup

import io.github.skm0078.audora.core.config.CleanupConfig
import io.github.skm0078.audora.core.config.LayoutConfig
import io.github.skm0078.audora.core.model.TextBox
import io.github.skm0078.audora.pdf.extract.PageText
import io.github.skm0078.audora.pdf.layout.LayoutMode
import io.github.skm0078.audora.pdf.layout.PageLayout
import io.github.skm0078.audora.pdf.layout.PageSketch
import io.github.skm0078.audora.pdf.layout.Paragraph
import io.github.skm0078.audora.pdf.layout.ParagraphKind
import io.github.skm0078.audora.pdf.layout.TextBlock
import io.github.skm0078.audora.pdf.layout.TextLine
import io.github.skm0078.audora.pdf.layout.TextSpan
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The layout is built by hand, a block per group of columns, as XY-cut leaves a table whose rows it could not cut
 * across. Cells are 10 pt type, 5 pt a character.
 */
class TableFinderTest {
    private val finder = TableFinder(CleanupConfig(), LayoutConfig().lineOverlapRatio)

    private class Cell(val text: String, val left: Float, val top: Float, val size: Float = PageSketch.BODY)

    /** A two-column table of [rows] rows at [pitch], its columns at [left] and 60 points right of it. */
    private fun table(left: Float, pitch: Float, label: String, rows: Int = 4): List<List<Cell>> = (0..rows).map { r ->
        val top = TOP - r * pitch
        listOf(Cell("$label$r", left, top), Cell("${10 + r}", left + COLUMN, top))
    }

    /** The page and its layout: each of [blocks] a block of one paragraph, each row one line of its cells. */
    private fun layout(vararg blocks: List<List<Cell>>): Pair<PageText, PageLayout> {
        val sketch = PageSketch()
        blocks.forEach { rows -> rows.flatten().forEach { sketch.line(it.text, it.left, it.top, it.size) } }
        val page = sketch.build()
        var cursor = 0
        fun span(text: String): TextSpan {
            val start = page.text.indexOf(text, cursor)
            cursor = start + text.length
            return TextSpan(start, cursor)
        }
        val textBlocks = blocks.map { rows ->
            val lines = rows.map { cells ->
                val spans = cells.map { span(it.text) }
                val box = spans.flatMap { (it.start until it.end).mapNotNull(page::boxAt) }.reduce(TextBox::union)
                TextLine(spans, box, cells.first().size, bold = false)
            }
            TextBlock(listOf(Paragraph(lines, ParagraphKind.BODY)), box = null)
        }
        return page to PageLayout(0, textBlocks, PageSketch.BODY, LayoutMode.EXTRACTION_ORDER)
    }

    @Test
    fun `one table XY-cut cut into blocks of columns is one table again`() {
        val left = table(LEFT, PITCH, "P")
        // The same rows, the gutter no wider than the table's own gaps: its third and fourth columns.
        val right = table(LEFT + 2 * COLUMN, PITCH, "W")
        val (page, layout) = layout(left, right)
        assertEquals(listOf(left.size + right.size), finder.find(page, layout).map { it.size })
    }

    @Test
    fun `two tables side by side, a gutter wider than their columns apart, stay two when they show it`() {
        // Different lengths: the right table's last rows have nothing beside them.
        val (page, layout) = layout(table(LEFT, PITCH, "P", rows = 2), table(LEFT + 6 * COLUMN, PITCH, "W"))
        assertEquals(listOf(3, 5), finder.find(page, layout).map { it.size })
        // A list in two halves: the same header on both.
        val header = listOf(Cell("No.", LEFT, TOP), Cell("Name", LEFT + COLUMN, TOP))
        val halves = listOf(LEFT, LEFT + 6 * COLUMN).map { left ->
            listOf(header.map { Cell(it.text, it.left - LEFT + left, it.top) }) + table(left, PITCH, "P").drop(1)
        }
        val (page2, layout2) = layout(halves[0], halves[1])
        assertEquals(listOf(5, 5), finder.find(page2, layout2).map { it.size })
    }

    @Test
    fun `one table with a wide gap before its figures, cut by XY-cut, is one table`() {
        val (page, layout) = layout(table(LEFT, PITCH, "P"), table(LEFT + 6 * COLUMN, PITCH, "W"))
        assertEquals(listOf(10), finder.find(page, layout).map { it.size })
    }

    @Test
    fun `a block of cells spanning two rows each, beside a table, is still part of it`() {
        val left = table(LEFT, PITCH, "P", rows = 5)
        // Three cells twice as tall next to six rows: each sits beside two rows, so none pairs with one row alone.
        val tall = 2 * PageSketch.BODY
        val right = (0..2).map { r ->
            val top = TOP - r * 2 * PITCH
            listOf(Cell("Note$r", LEFT + 2 * COLUMN, top, tall), Cell("${r + 1}", LEFT + 4 * COLUMN, top, tall))
        }
        val (page, layout) = layout(left, right)
        assertEquals(listOf(left.size + right.size), finder.find(page, layout).map { it.size })
    }

    private companion object {
        const val LEFT = 50f
        const val TOP = 700f
        const val PITCH = 14f
        const val COLUMN = 60f
    }
}
