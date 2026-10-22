// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/ocr/OcrTablesTest.kt
// Purpose: Tables on scanned pages become table units: ruled ones from their rules, borderless ones from OCR's rows.

package io.github.skm0078.audora.pdf.ocr

import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.model.UnitKind
import io.github.skm0078.audora.pdf.render.PageTransform
import io.github.skm0078.audora.pdf.render.ViewPoint
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

    private fun units(ocr: OcrText, on: PageTransform = transform): List<ReadingUnit> =
        builder.build(builder.ocrReadingText(PAGE, ocr) { on.toPage(it) })

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
    fun `two ruled tables side by side on the same row heights stay two tables`() {
        val ys = listOf(0.2f, 0.25f, 0.3f, 0.35f)
        val rules = grid(listOf(0.05f, 0.2f, 0.35f), ys) + grid(listOf(0.55f, 0.7f, 0.85f), ys)
        val ocr = OcrText(
            // Tesseract reads the two header cells as one line across the gutter: both tables start on it.
            listOf(OcrLine(listOf(word("Name", 0.07f, 0.205f), word("City", 0.57f, 0.205f)), 0)) +
                cells(
                    word("Asha", 0.07f, 0.255f),
                    word("Ravi", 0.07f, 0.305f),
                    word("Score", 0.22f, 0.205f),
                    word("91", 0.22f, 0.255f),
                    word("78", 0.22f, 0.305f),
                    word("Pune", 0.57f, 0.255f),
                    word("Agra", 0.57f, 0.305f),
                    word("Rank", 0.72f, 0.205f),
                    word("8", 0.72f, 0.255f),
                    word("19", 0.72f, 0.305f),
                ).map { OcrLine(it.words, it.paragraph + 1) },
            rules = rules,
        )
        val expected = listOf(
            listOf("Name", "Score"),
            listOf("Asha", "91"),
            listOf("Ravi", "78"),
            listOf("City", "Rank"),
            listOf("Pune", "8"),
            listOf("Agra", "19"),
        )
        assertEquals(expected, rows(units(ocr)))
    }

    @Test
    fun `a one-character cell beside a rule is a cell, not a rule read as text`() {
        // A narrow middle column: its one-character cells sit within the snap of the rule on their left.
        val narrow = 0.008f
        val ocr = OcrText(
            cells(
                word("Name", 0.12f, 0.205f),
                word("#", 0.403f, 0.205f, width = narrow),
                word("Score", 0.44f, 0.205f),
                word("Asha", 0.12f, 0.255f),
                word("5", 0.403f, 0.255f, width = narrow),
                word("91", 0.44f, 0.255f),
                word("Ravi", 0.12f, 0.305f),
                word("|", 0.399f, 0.305f, width = 0.004f),
                word("7", 0.403f, 0.305f, width = narrow),
                word("78", 0.44f, 0.305f),
            ),
            rules = grid(listOf(0.1f, 0.4f, 0.425f, 0.7f), listOf(0.2f, 0.25f, 0.3f, 0.35f)),
        )
        val expected = listOf(listOf("Name", "#", "Score"), listOf("Asha", "5", "91"), listOf("Ravi", "7", "78"))
        assertEquals(expected, rows(units(ocr)))
    }

    @Test
    fun `a page with Rotate 90 or 270 reads its ruled and borderless tables as an upright page does`() {
        val ruled = OcrText(
            cells(
                word("Name", 0.12f, 0.205f),
                word("Asha", 0.12f, 0.255f),
                word("Ravi", 0.12f, 0.305f),
                word("Score", 0.42f, 0.205f),
                word("91", 0.42f, 0.255f),
                word("78", 0.42f, 0.305f),
            ),
            rules = grid(listOf(0.1f, 0.4f, 0.7f), listOf(0.2f, 0.25f, 0.3f, 0.35f)),
        )
        val borderless = OcrText(
            listOf("Item" to "Cost", "Pens" to "12", "Paper" to "30", "Ink" to "45").mapIndexed { i, (item, cost) ->
                OcrLine(listOf(word(item, 0.1f, 0.2f + i * ROW_PITCH), word(cost, 0.5f, 0.2f + i * ROW_PITCH)), i)
            },
        )
        // The page as stored is 600 × 800 points; shown turned, 800 wide and 600 tall.
        for (quarterTurns in listOf(1, 3)) {
            val turned = rotated(quarterTurns)
            assertEquals(rows(units(ruled)), rows(units(ruled, turned)))
            assertEquals(rows(units(borderless)), rows(units(borderless, turned)))
        }
        assertEquals(listOf(listOf("Name", "Score"), listOf("Asha", "91"), listOf("Ravi", "78")), rows(units(ruled)))
    }

    /**
     * A page stored [PAGE_WIDTH] × [PAGE_HEIGHT] shown with `/Rotate` of [quarterTurns] × 90°: at 90 the page's x runs
     * down the view and its y to the right; at 270, up and to the left.
     */
    private fun rotated(quarterTurns: Int): PageTransform {
        val step = 1f
        val down = step / PAGE_WIDTH
        val across = step / PAGE_HEIGHT
        return if (quarterTurns == 1) {
            PageTransform.fromSamples(ViewPoint(0f, 0f), ViewPoint(0f, down), ViewPoint(across, 0f), step)
        } else {
            PageTransform.fromSamples(ViewPoint(1f, 1f), ViewPoint(1f, 1f - down), ViewPoint(1f - across, 1f), step)
        }
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
    fun `a wide scanned table with section rows and blank figures beyond a wide gap stays one table`() {
        val rows = listOf(
            listOf("Item", "Head", "Budget", "Actual"),
            listOf("1", "Taxes", "120", "135"),
            listOf("A.", "Receipts"),
            listOf("2", "Fees", "–", "–"),
            listOf("B.", "Capital"),
        )
        val xs = listOf(0.05f, 0.15f, 0.6f, 0.75f)
        val ocr = OcrText(
            rows.mapIndexed { r, cells ->
                OcrLine(cells.mapIndexed { c, w -> word(w, xs[c], 0.2f + r * ROW_PITCH) }, r)
            },
        )
        val units = units(ocr)
        assertEquals(1, units.count { it.kind == UnitKind.TABLE_SUMMARY })
        assertEquals(rows.first(), rows(units).filterIsInstance<List<*>>().first())
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
