// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/cleanup/ClutterRemoverTest.kt
// Purpose: Running headers, footers, page numbers and tables on synthetic pages, through the real unit builder.

package io.github.skm0078.audora.pdf.cleanup

import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.model.UnitKind
import io.github.skm0078.audora.pdf.layout.PageSketch
import io.github.skm0078.audora.pdf.units.AnalyzedPage
import io.github.skm0078.audora.pdf.units.ReadingUnitBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClutterRemoverTest {
    private val builder = ReadingUnitBuilder()

    /**
     * A page as tall as a real one: optional header at the top, [BODY_LINES] body lines whose words differ from
     * page to page (as real text does), optional footer at the bottom.
     */
    private fun page(
        index: Int,
        header: List<Pair<String, Float>> = emptyList(),
        footer: List<Pair<String, Float>> = emptyList(),
        body: List<String> = body(index),
    ): AnalyzedPage {
        val sketch = PageSketch()
        header.forEach { (text, left) -> sketch.line(text, left, HEADER_TOP) }
        sketch.lines(body, LEFT, BODY_TOP)
        footer.forEach { (text, left) -> sketch.line(text, left, FOOTER_TOP) }
        return builder.analyze(sketch.build(index))
    }

    private fun body(index: Int): List<String> =
        List(BODY_LINES) { line -> "Words ${WORDS[index % WORDS.size]} and ${WORDS[line % WORDS.size]} here." }

    private fun bodyText(index: Int): String = body(index).joinToString(" ")

    /** Spoken text of [target] with every other page of [pages] as its neighbours. */
    private fun spoken(pages: List<AnalyzedPage>, target: Int): String {
        val neighbours = pages.filter { it.pageIndex != target }.map { it.profile }
        return builder.build(pages.single { it.pageIndex == target }, neighbours).joinToString(" ") { it.text }
    }

    @Test
    fun `a header repeated on every page and the page numbers go`() {
        val pages = (0..4).map {
            page(it, header = listOf("The Running Title" to LEFT), footer = listOf("${it + 1}" to MID))
        }
        for (p in 0..4) assertEquals(bodyText(p), spoken(pages, p))
    }

    @Test
    fun `alternating left and right headers still repeat across the window`() {
        val pages = (0..6).map { i ->
            page(i, header = listOf((if (i % 2 == 0) "Author Name" else "Title of the Book") to LEFT))
        }
        for (p in 0..6) assertFalse(spoken(pages, p), "Author" in spoken(pages, p) || "Title" in spoken(pages, p))
    }

    @Test
    fun `a chapter header carrying the page number goes even when its title changes`() {
        val titles = listOf("CHAPTER 4. FIRST STEPS", "CHAPTER 5. CONNECTIVES", "CHAPTER 5. CONNECTIVES")
        val pages = titles.mapIndexed { i, title -> page(i, header = listOf(title to LEFT, "${31 + i}" to RIGHT)) }
        for (p in pages.indices) assertFalse(spoken(pages, p), "CHAPTER" in spoken(pages, p))
    }

    @Test
    fun `Page N of M headers run with the page index`() {
        val rightHeaders = listOf("A. Author", "Short title")
        val pages = (0..1).map { i ->
            page(i, header = listOf("57" to LEFT, "Page ${i + 2} of 50" to MID, rightHeaders[i] to RIGHT))
        }
        for (p in pages.indices) assertFalse(spoken(pages, p), "Page" in spoken(pages, p))
    }

    @Test
    fun `a lone page number goes without any neighbour`() {
        val single = page(0, footer = listOf("17" to MID))
        assertEquals(bodyText(0), spoken(listOf(single), 0))
    }

    @Test
    fun `a printer's slug at the foot goes, a date and time alone stays`() {
        val slugs = listOf("Chapter 9 Transitions.indd 71", "17-12-2019 12:54:06")
        for (slug in slugs) {
            val pages = (0..2).map { page(it, footer = if (it == 1) listOf(slug to LEFT) else emptyList()) }
            assertEquals(slug, bodyText(1), spoken(pages, 1))
        }
        // The same print time on the next page's foot marks both as slugs; a date sheet's times differ.
        val printed = (0..2).map { page(it, footer = listOf("5/13/11 6:45 AM" to LEFT)) }
        assertEquals(bodyText(1), spoken(printed, 1))
        val subjects = listOf("Physics", "Chemistry", "Biology")
        val sheet = (0..2).map { page(it, footer = listOf("${subjects[it]} 1$it/03/2024 10:30" to LEFT)) }
        assertTrue(spoken(sheet, 1), "10:30" in spoken(sheet, 1))
    }

    @Test
    fun `page numbers set above a printer's slug go`() {
        // The slug sits outside the printed page, well below its folio: the folio is still the page's own foot.
        val pages = (0..2).map { i ->
            val sketch = PageSketch()
            sketch.lines(body(i).take(SHORT_BODY_LINES), LEFT, BODY_TOP)
            sketch.line("${i + 6}", MID, RAISED_FOOTER_TOP)
            sketch.lines(listOf("Book.indd Sec1:${i + 6}", "5/13/11 6:45 AM"), LEFT, SLUG_TOP)
            builder.analyze(sketch.build(i))
        }
        for (p in pages.indices) assertEquals(body(p).take(SHORT_BODY_LINES).joinToString(" "), spoken(pages, p))
    }

    @Test
    fun `lines that only look like headers stay`() {
        // A margin line on one page only, numbers that do not run with the page, and a repeated line mid-page.
        val pages = (0..4).map { i ->
            page(
                i,
                header = if (i == 2) listOf("A unique opening line" to LEFT) else emptyList(),
                footer = listOf("in room ${7 * i + 3}" to LEFT),
                body = listOf("Amen.") + body(i),
            )
        }
        val text = spoken(pages, 2)
        assertTrue(text, "A unique opening line" in text)
        assertTrue(text, text.startsWith("A unique opening line Amen."))
        // The footer repeats its words on every page, so it is a running footer; its numbers alone would not be.
        assertFalse(text, "room" in text)
    }

    @Test
    fun `templated lines that differ only in their numbers are text, not headers`() {
        // Every line reads "Page N, sentence L of ...": the top lines repeat on every page, but so does the body.
        fun templated(i: Int) = List(BODY_LINES) { line -> "Page ${i + 1}, sentence ${line + 1} of a generated book." }
        val pages = (0..6).map { i -> page(i, body = templated(i)) }
        for (p in pages.indices) assertEquals(templated(p).joinToString(" "), spoken(pages, p))
    }

    @Test
    fun `a short book needs every page for a repeat`() {
        val pages = (0..1).map { i -> page(i, header = listOf("Journal of Things 27 (2023)" to LEFT)) }
        assertFalse(spoken(pages, 0).contains("Journal"))
        val oneWithHeader = listOf(pages[0], page(1))
        assertTrue(spoken(oneWithHeader, 0).contains("Journal"))
    }

    @Test
    fun `a table becomes a header and rows, its caption and the text around it stay`() {
        val sketch = PageSketch()
        sketch.line("Table 1. Patients and doses.", LEFT, 700f)
        sketch.line("Pat.", LEFT, 680f).line("Age", 150f, 680f).line("Dose", 250f, 680f)
        for (row in 1..4) {
            val top = 680f - row * 12f
            sketch.line("$row", LEFT, top).line("${50 + row}", 150f, top).line("2 Gy x 30", 250f, top)
        }
        sketch.lines(listOf("The table shows every patient.", "Doses were the same."), LEFT, 600f)
        val units = builder.build(sketch.build())
        val running = units.filterNot { it.kind.isTable }.joinToString(" ") { it.text }
        assertEquals("Table 1. Patients and doses. The table shows every patient. Doses were the same.", running)

        val table = units.filter { it.kind.isTable }
        assertEquals(listOf(UnitKind.TABLE_SUMMARY) + List(4) { UnitKind.TABLE_ROW }, table.map { it.kind })
        assertEquals(listOf("Pat.", "Age", "Dose"), table.first().table?.headers)
        val third = table[3]
        assertEquals(listOf("3", "53", "2 Gy x 30"), third.table?.cells?.map { third.text.substring(it.start, it.end) })
        assertEquals(listOf(null, 0, 1, 2, 3), table.map { it.table?.row })
        assertTrue(table.all { it.table?.rowCount == 4 && it.table?.columnCount == 3 })
    }

    @Test
    fun `a dot leader in a table cell is left out of the cell`() {
        val sketch = PageSketch()
        sketch.line("Pat.", LEFT, 680f).line("Age", 150f, 680f).line("Dose", 250f, 680f)
        for (row in 1..4) {
            val top = 680f - row * 12f
            sketch.line("$row ........", LEFT, top).line("${50 + row}", 150f, top).line("2 Gy x 30", 250f, top)
        }
        sketch.lines(listOf("The table shows every patient.", "Doses were the same."), LEFT, 600f)
        val third = builder.build(sketch.build()).filter { it.kind.isTable }[3]
        assertEquals("3 53 2 Gy x 30", third.text)
        assertEquals(listOf("3", "53", "2 Gy x 30"), third.table?.cells?.map { third.text.substring(it.start, it.end) })
    }

    @Test
    fun `leaders are read cell by cell, a cell holding only a rule reads as a dash, a blank as nothing`() {
        val sketch = PageSketch()
        sketch.line("Crop", LEFT, 680f).line("2019", 150f, 680f).line("2020", 250f, 680f).line("2021", 350f, 680f)
        val rows = listOf(
            listOf("Wheat", ". . .", ". . .", "12"),
            listOf("Rice", "-----", "14", "15"),
            listOf("Maize", "______", "17", "18"),
            listOf("Barley", "19", "20", "21"),
        )
        rows.forEachIndexed { i, cells ->
            val top = 668f - i * 12f
            sketch.line(
                cells[0],
                LEFT,
                top,
            ).line(cells[1], 150f, top).line(cells[2], 250f, top).line(cells[3], 350f, top)
        }
        sketch.lines(listOf("The table shows every crop.", "Yields were the same."), LEFT, 600f)
        val units = builder.build(sketch.build()).filter { it.kind == UnitKind.TABLE_ROW }
        fun cells(unit: ReadingUnit) = unit.table!!.cells.map { unit.text.substring(it.start, it.end) }
        // Six spaced dots across two cells would make a leader; each cell's three stay as they are.
        assertEquals(listOf("Wheat", ". . .", ". . .", "12"), cells(units[0]))
        assertEquals(listOf("Rice", "\u2013", "14", "15"), cells(units[1]))
        // A blank to fill in says nothing: only a rule reads as nil.
        assertEquals(listOf("Maize", "", "17", "18"), cells(units[2]))
    }

    @Test
    fun `a dated line at the foot is no slug by itself, so a number above it stays`() {
        val pages = (0..2).map { i ->
            val sketch = PageSketch()
            sketch.lines(body(i), LEFT, BODY_TOP)
            sketch.line("42", MID, NUMBER_TOP)
            sketch.line("Physics 1$i/03/2024 10:30", LEFT, FOOTER_TOP)
            builder.analyze(sketch.build(i))
        }
        for (p in pages.indices) assertTrue(spoken(pages, p), "42" in spoken(pages, p))
    }

    @Test
    fun `an intro line set close above a table is read before it, and the line after it after it`() {
        // Line spacing as tight as the rows', so layout joins intro, table and the line after into one paragraph.
        val sketch = PageSketch()
        sketch.line("The doses were as follows:", LEFT, 692f)
        sketch.line("Pat.", LEFT, 680f).line("Age", 150f, 680f).line("Dose", 250f, 680f)
        for (row in 1..4) {
            val top = 680f - row * 12f
            sketch.line("$row", LEFT, top).line("${50 + row}", 150f, top).line("2 Gy x 30", 250f, top)
        }
        sketch.line("Nobody missed a dose.", LEFT, 620f)
        val units = builder.build(sketch.build())
        val order = units.map { if (it.kind.isTable) it.kind.name else it.text }
        val summary = order.indexOf(UnitKind.TABLE_SUMMARY.name)
        assertTrue("$order", order.indexOfFirst { "as follows" in it } in 0 until summary)
        assertTrue("$order", order.indexOfFirst { "Nobody" in it } > order.lastIndexOf(UnitKind.TABLE_ROW.name))
        assertFalse("$order", order.any { "follows" in it && "Nobody" in it })
    }

    @Test
    fun `two tables keyed in one paragraph are both read, in order`() {
        // Two grids set at the rows' spacing with different columns: one paragraph to layout, two tables.
        val sketch = PageSketch()
        sketch.line("Pat.", LEFT, 700f).line("Age", 150f, 700f).line("Dose", 250f, 700f)
        for (row in 1..3) {
            val top = 700f - row * 12f
            sketch.line("$row", LEFT, top).line("${50 + row}", 150f, top).line("2 Gy x 30", 250f, top)
        }
        sketch.line("Site", LEFT, 652f).line("Beds", 200f, 652f).line("Staff", 350f, 652f)
        for (row in 1..3) {
            val top = 652f - row * 12f
            sketch.line("Ward $row", LEFT, top).line("${10 * row}", 200f, top).line("${row + 4}", 350f, top)
        }
        val units = builder.build(sketch.build())
        val headers = units.filter { it.kind == UnitKind.TABLE_SUMMARY }.map { it.table?.headers }
        assertEquals(
            "${units.map {
                it.text
            }}",
            listOf(listOf("Pat.", "Age", "Dose"), listOf("Site", "Beds", "Staff")),
            headers,
        )
        assertEquals(6, units.count { it.kind == UnitKind.TABLE_ROW })
    }

    /** The headers of the page's tables and its row count, from a sketch of two blocks of columns on shared rows. */
    private fun tablesOf(sketch: PageSketch): Pair<List<List<String>?>, Int> {
        val units = builder.build(sketch.build())
        return units.filter { it.kind == UnitKind.TABLE_SUMMARY }.map { it.table?.headers } to
            units.count { it.kind == UnitKind.TABLE_ROW }
    }

    @Test
    fun `a list set in two halves side by side on shared rows reads as two tables`() {
        val sketch = PageSketch()
        sketch.line("No.", LEFT, 700f).line("Name", 110f, 700f).line("No.", 380f, 700f).line("Name", 440f, 700f)
        for (row in 1..4) {
            val top = 700f - row * 12f
            sketch.line("$row", LEFT, top).line("Asha$row", 110f, top)
            sketch.line("${row + 4}", 380f, top).line("Ravi$row", 440f, top)
        }
        val (headers, rows) = tablesOf(sketch)
        assertEquals(listOf(listOf("No.", "Name"), listOf("No.", "Name")), headers)
        assertEquals(8, rows)
    }

    @Test
    fun `two different tables side by side, one longer, read as two tables`() {
        val sketch = PageSketch()
        sketch.line("Pat.", LEFT, 700f).line("Age", 110f, 700f).line("Site", 380f, 700f).line("Beds", 440f, 700f)
        for (row in 1..4) {
            val top = 700f - row * 12f
            sketch.line("P$row", LEFT, top).line("${50 + row}", 110f, top)
            if (row <= 2) sketch.line("W$row", 380f, top).line("${10 * row}", 440f, top)
        }
        val (headers, rows) = tablesOf(sketch)
        assertEquals(listOf(listOf("Pat.", "Age"), listOf("Site", "Beds")), headers)
        assertEquals(6, rows)
    }

    @Test
    fun `one table with a wide gap before its figures stays one table`() {
        val sketch = PageSketch()
        sketch.line("No.", LEFT, 700f).line("Item", 110f, 700f).line("2019", 380f, 700f).line("2020", 440f, 700f)
        for (row in 1..4) {
            val top = 700f - row * 12f
            sketch.line("$row", LEFT, top).line("Pens$row", 110f, top)
            sketch.line("${10 * row}", 380f, top).line("${12 * row}", 440f, top)
        }
        val (headers, rows) = tablesOf(sketch)
        assertEquals(listOf(listOf("No.", "Item", "2019", "2020")), headers)
        assertEquals(4, rows)
    }

    @Test
    fun `one wide table with section rows, blank figures and a two-line header stays one table`() {
        val sketch = PageSketch()
        sketch.line("Item", LEFT, 700f).line("Head", 110f, 700f).line("Budget", 380f, 700f).line("Actual", 440f, 700f)
        sketch.line("estimate", 380f, 688f).line("figures", 440f, 688f)
        sketch.line("A.", LEFT, 676f).line("Receipts", 110f, 676f)
        sketch.line("1", LEFT, 664f).line("Taxes", 110f, 664f).line("120", 380f, 664f).line("135", 440f, 664f)
        sketch.line("2", LEFT, 652f).line("Fees", 110f, 652f).line("–", 380f, 652f).line("–", 440f, 652f)
        sketch.line("3", LEFT, 640f).line("Duty", 110f, 640f).line("75", 380f, 640f).line("80", 440f, 640f)
        sketch.line("B.", LEFT, 628f).line("Capital", 110f, 628f)
        val (headers, _) = tablesOf(sketch)
        assertEquals("$headers", 1, headers.size)
        assertEquals(4, headers.single()?.size)
    }

    @Test
    fun `a table of figures beside a list of labels a wide gap apart, one longer, stays one table`() {
        // The labels run on below the figures: the right side is all figures, so it is this table's.
        val sketch = PageSketch()
        sketch.line("State", LEFT, 700f).line("Code", 110f, 700f).line("2019", 380f, 700f).line("2020", 440f, 700f)
        for (row in 1..4) {
            val top = 700f - row * 12f
            sketch.line("S$row", LEFT, top).line("${row * 7}", 110f, top)
            if (row <= 2) sketch.line("${row * 37}", 380f, top).line("${row * 41}", 440f, top)
        }
        val (headers, rows) = tablesOf(sketch)
        assertEquals(listOf(listOf("State", "Code", "2019", "2020")), headers)
        assertEquals(4, rows)
    }

    @Test
    fun `text with wide gaps that do not line up is not a table`() {
        val sketch = PageSketch()
        sketch.line("first", LEFT, 700f).line("words", 120f, 700f)
        sketch.line("second", LEFT, 688f).line("line", 200f, 688f)
        sketch.line("third", LEFT, 676f).line("row.", 300f, 676f)
        val words = builder.build(sketch.build()).flatMap { it.text.split(' ') }
        // Reading order is layout's business; here every word must still be spoken.
        assertEquals(listOf("first", "line", "row.", "second", "third", "words"), words.sorted())
    }

    @Test
    fun `display equations with numbers are not a table`() {
        val sketch = PageSketch()
        for (i in 1..4) sketch.line("x = y + $i", 120f, 700f - 14f * i).line("($i)", 400f, 700f - 14f * i)
        val text = builder.build(sketch.build()).joinToString(" ") { it.text }
        assertTrue(text, "x = y + 1" in text && "x = y + 4" in text)
    }

    private companion object {
        const val LEFT = 50f
        const val MID = 280f
        const val RIGHT = 480f
        const val HEADER_TOP = 800f
        const val BODY_TOP = 700f
        const val FOOTER_TOP = 40f
        const val SLUG_TOP = 20f
        const val NUMBER_TOP = 100f
        const val RAISED_FOOTER_TOP = 300f
        const val SHORT_BODY_LINES = 20
        const val BODY_LINES = 50
        val WORDS = listOf("amber", "birch", "cedar", "delta", "ember", "fjord", "grove", "heath", "inlet", "jetty")
    }
}
