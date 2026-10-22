// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/ocr/OcrTablesTest.kt
// Purpose: Tables on scanned pages become table units: ruled ones from their rules, borderless ones from OCR's rows.

package io.github.skm0078.audora.pdf.ocr

import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.pdf.render.PageTransform
import io.github.skm0078.audora.pdf.render.ViewRect
import io.github.skm0078.audora.pdf.units.ReadingUnitBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pages are 600 × 800 points; words 12 points high, so the page's em is 12 points and a rule snaps within 6. */
class OcrTablesTest {
    private val builder = ReadingUnitBuilder()
    private val transform = PageTransform.upright(PAGE_WIDTH, PAGE_HEIGHT)

    private fun word(text: String, left: Float, top: Float, width: Float = WORD_WIDTH) =
        OcrWord(text, ViewRect(left, top, left + width, top + WORD_HEIGHT), CONFIDENT)

    /** One OCR line per word: Tesseract reads a ruled table's cells one by one. */
    private fun cells(vararg words: OcrWord): List<OcrLine> = words.mapIndexed { i, word -> OcrLine(listOf(word), i) }

    private fun horizontal(top: Float, left: Float, right: Float) = ViewRect(left, top, right, top + RULE)

    private fun vertical(left: Float, top: Float, bottom: Float) = ViewRect(left, top, left + RULE, bottom)

    private fun units(ocr: OcrText): List<ReadingUnit> =
        builder.build(builder.ocrReadingText(PAGE, ocr) { transform.toPage(it) })

    /** Each table unit as its cells' texts, header first; other units as their text. */
    private fun rows(units: List<ReadingUnit>): List<Any> = units.map { unit ->
        unit.table?.let { table -> table.cells.map { unit.text.substring(it.start, it.end) } } ?: unit.text
    }

    /** A ruled grid: rows between [ys] and columns between [xs], every rule running the whole grid. */
    private fun grid(xs: List<Float>, ys: List<Float>): List<ViewRect> =
        ys.map { horizontal(it, xs.first(), xs.last()) } + xs.map { vertical(it, ys.first(), ys.last()) }

    @Test
    fun `a ruled table read cell by cell is rebuilt from its rules, rule glyphs left out`() {
        val ocr = OcrText(
            OcrLine(listOf(word("Results", 0.1f, 0.1f), word("follow.", 0.2f, 0.1f)), 0).let(::listOf) +
                // Read a column at a time, as Tesseract often does.
                cells(
                    word("Name", 0.12f, 0.205f),
                    word("Asha", 0.12f, 0.255f),
                    word("Ravi", 0.12f, 0.305f),
                    word("Score", 0.42f, 0.205f),
                    word("91", 0.42f, 0.255f),
                    word("|", 0.399f, 0.305f, width = 0.004f),
                    word("78", 0.42f, 0.305f),
                ).map { OcrLine(it.words, it.paragraph + 1) },
            rules = grid(listOf(0.1f, 0.4f, 0.7f), listOf(0.2f, 0.25f, 0.3f, 0.35f)),
        )
        val expected = listOf("Results follow.", listOf("Name", "Score"), listOf("Asha", "91"), listOf("Ravi", "78"))
        // The last cell read, a lone number, is inside the rules: a cell, not the page number.
        assertEquals(expected, rows(units(ocr)))
    }

    @Test
    fun `a row across the whole grid is text after the table, not one of its rows`() {
        val xs = listOf(0.1f, 0.4f, 0.7f)
        val rules = grid(xs, listOf(0.2f, 0.25f, 0.3f)) +
            listOf(horizontal(0.35f, 0.1f, 0.7f), vertical(0.1f, 0.3f, 0.35f), vertical(0.7f, 0.3f, 0.35f))
        val footer = listOf(word("Prices", 0.12f, 0.305f), word("include", 0.22f, 0.305f), word("tax.", 0.32f, 0.305f))
        val ocr = OcrText(
            cells(word("Item", 0.12f, 0.205f), word("Cost", 0.42f, 0.205f), word("Pens", 0.12f, 0.255f)) +
                listOf(OcrLine(listOf(word("12", 0.42f, 0.255f)), 3), OcrLine(footer, 4)),
            rules = rules,
        )
        assertEquals(listOf(listOf("Item", "Cost"), listOf("Pens", "12"), "Prices include tax."), rows(units(ocr)))
    }

    @Test
    fun `one tall row of ruled columns keeps OCR's order a column at a time`() {
        // The top row is the empty gap of a double border, so the grid has one row with words: not a table.
        val left = listOf("alpha", "beta", "gamma").mapIndexed { i, w -> word(w, 0.12f, 0.25f + i * 0.03f) }
        val right = listOf("delta", "epsilon", "zeta").mapIndexed { i, w -> word(w, 0.42f, 0.25f + i * 0.03f) }
        val ocr = OcrText(
            listOf(OcrLine(left, 0), OcrLine(right, 1)).flatMap { line ->
                line.words.map { OcrLine(listOf(it), line.paragraph) }
            },
            rules = grid(listOf(0.1f, 0.4f, 0.7f), listOf(0.2f, 0.22f, 0.4f)),
        )
        val units = units(ocr)
        assertEquals(listOf("alpha beta gamma", "delta epsilon zeta"), units.map { it.text })
        units.forEach { assertNull(it.table) }
    }

    @Test
    fun `a borderless table read as two blocks of columns is joined back into rows, prose below left alone`() {
        val items = listOf("Item", "Pens", "Paper", "Ink")
        val costs = listOf("Cost", "12", "30", "45")
        val prose = "The prices above were checked against the shop list last week and found right".split(' ')
        fun proseLine(top: Float, words: List<String>) =
            words.mapIndexed { i, w -> word(w, 0.1f + i * PROSE_PITCH, top, width = PROSE_WORD) }
        val ocr = OcrText(
            listOf(
                *items.mapIndexed { i, w -> OcrLine(listOf(word(w, 0.1f, 0.2f + i * ROW_PITCH)), 0) }.toTypedArray(),
                *costs.mapIndexed { i, w -> OcrLine(listOf(word(w, 0.5f, 0.2f + i * ROW_PITCH)), 1) }.toTypedArray(),
                OcrLine(proseLine(0.4f, prose.take(PROSE_WORDS)), 2),
                OcrLine(proseLine(0.43f, prose.drop(PROSE_WORDS)), 2),
            ),
        )
        val expected = listOf(
            listOf("Item", "Cost"),
            listOf("Pens", "12"),
            listOf("Paper", "30"),
            listOf("Ink", "45"),
            prose.joinToString(" "),
        )
        assertEquals(expected, rows(units(ocr)))
    }

    @Test
    fun `two columns of prose side by side are not a table`() {
        val words = "every line of this column runs wide like prose does".split(' ')
        fun column(left: Float, paragraph: Int) = (0 until PROSE_LINES).map { line ->
            OcrLine(words.mapIndexed { i, w -> word(w, left + i * 0.035f, 0.2f + line * ROW_PITCH, 0.03f) }, paragraph)
        }
        val units = units(OcrText(column(0.05f, 0) + column(0.55f, 1)))
        units.forEach { assertNull(it.table) }
        assertEquals(words.size * PROSE_LINES * 2, units.sumOf { it.text.split(' ').size })
    }

    private companion object {
        const val PAGE = 2
        const val PAGE_WIDTH = 600f
        const val PAGE_HEIGHT = 800f
        const val WORD_HEIGHT = 0.015f
        const val WORD_WIDTH = 0.06f
        const val RULE = 0.002f
        const val CONFIDENT = 95
        const val ROW_PITCH = 0.03f
        const val PROSE_PITCH = 0.06f
        const val PROSE_WORD = 0.055f
        const val PROSE_WORDS = 8
        const val PROSE_LINES = 4
    }
}
