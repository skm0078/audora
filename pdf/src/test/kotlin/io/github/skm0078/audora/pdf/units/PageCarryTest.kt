// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/units/PageCarryTest.kt
// Purpose: A table or sentence printed over a page break is carried on to the next page (RI-4a, plan item 7).

package io.github.skm0078.audora.pdf.units

import io.github.skm0078.audora.core.model.CellSpan
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.model.TableRowInfo
import io.github.skm0078.audora.core.model.UnitKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageCarryTest {
    private val header = listOf("No.", "Name", "Salary")

    /** One page's units: [prose] (kind to text) first, then a table of [rows] under [head] (null: no table). */
    private fun page(
        prose: List<Pair<UnitKind, String>> = emptyList(),
        head: List<String>? = header,
        rows: List<List<String>> = emptyList(),
        after: List<Pair<UnitKind, String>> = emptyList(),
    ): List<ReadingUnit> {
        val units = ArrayList<ReadingUnit>()
        fun add(kind: UnitKind, text: String, table: TableRowInfo? = null) {
            val start = units.lastOrNull()?.charEnd?.plus(2) ?: 0
            units.add(
                ReadingUnit(
                    PAGE, units.size, start, start + text.length, text, Lang.EN, PAUSE, null, kind, table = table,
                ),
            )
        }
        for ((kind, text) in prose) add(kind, text)
        if (head != null) {
            val headerTexts = head
            add(UnitKind.TABLE_SUMMARY, head.joinToString(" "), info(head, null, rows.size, headerTexts))
            rows.forEachIndexed { i, cells ->
                add(UnitKind.TABLE_ROW, cells.joinToString(" "), info(cells, i, rows.size, headerTexts))
            }
        }
        for ((kind, text) in after) add(kind, text)
        return units
    }

    private fun info(cells: List<String>, row: Int?, rowCount: Int, headers: List<String>): TableRowInfo {
        var at = 0
        val spans = cells.map { cell -> CellSpan(at, at + cell.length).also { at += cell.length + 1 } }
        return TableRowInfo(0, row, maxOf(rowCount, 1), cells.size, headers, spans)
    }

    private val firstPage = page(
        prose = listOf(UnitKind.BODY to "Salaries paid in March are listed below."),
        rows = listOf(listOf("1", "Ram Lal", "5739"), listOf("2", "Sita Devi", "28400")),
    )

    @Test
    fun `a table that repeats its header on the next page continues there`() {
        val next = page(rows = listOf(listOf("3", "Mohan", "6100"), listOf("4", "Gita", "7200")))
        val joined = PageCarry.join(next, PageCarry.tailOf(firstPage)).units
        assertEquals(List(3) { true }, joined.map { it.table!!.continued })
        assertEquals(List(3) { 2 }, joined.map { it.table!!.rowsBefore })
        assertEquals(next.map { it.kind to it.text }, joined.map { it.kind to it.text })
    }

    @Test
    fun `a first row of data on the next page is read as a row under the carried header`() {
        val next = page(head = listOf("3", "Mohan", "6100"), rows = listOf(listOf("4", "Gita", "7200")))
        val joined = PageCarry.join(next, PageCarry.tailOf(firstPage)).units
        assertEquals(listOf(UnitKind.TABLE_ROW, UnitKind.TABLE_ROW), joined.map { it.kind })
        assertEquals(listOf(0, 1), joined.map { it.table!!.row })
        assertEquals(listOf(2, 2), joined.map { it.table!!.rowCount })
        assertEquals(List(2) { header }, joined.map { it.table!!.headers })
        assertEquals(listOf(2, 2), joined.map { it.table!!.rowsBefore })
        assertEquals(next.map { it.charStart to it.text }, joined.map { it.charStart to it.text })
    }

    @Test
    fun `the rows read so far add up over several pages`() {
        val second = PageCarry.join(
            page(rows = listOf(listOf("3", "Mohan", "6100"))),
            PageCarry.tailOf(firstPage),
        ).units
        val third = page(rows = listOf(listOf("4", "Gita", "7200")))
        assertEquals(3, PageCarry.join(third, PageCarry.tailOf(second)).units.first().table!!.rowsBefore)
    }

    @Test
    fun `a table whose own first row is data hands on no header`() {
        val headless =
            page(head = listOf("68", "Raj Kumar", "37600"), rows = listOf(listOf("69", "Ram Singh", "28400")))
        val next = page(head = listOf("70", "Mohan", "6100"), rows = listOf(listOf("71", "Gita", "7200")))
        val joined = PageCarry.join(next, PageCarry.tailOf(headless)).units
        assertEquals(listOf(emptyList<String>(), emptyList()), joined.map { it.table!!.headers })
        assertEquals(listOf(0, 1), joined.map { it.table!!.row })
    }

    @Test
    fun `tables that do not carry over stay new tables`() {
        val newTable = page(head = listOf("Code", "Item", "Rate"), rows = listOf(listOf("A1", "Rice", "40")))
        val cases = listOf(
            "the page before ends with text" to
                (page(prose = listOf(UnitKind.BODY to "No table here."), head = null) to newTable),
            "text before the table" to
                (
                    firstPage to
                        page(prose = listOf(UnitKind.BODY to "Next list."), rows = listOf(listOf("5", "Hari", "100")))
                    ),
            "a different column count" to (firstPage to page(head = listOf("A", "B"), rows = listOf(listOf("1", "2")))),
            "a header of its own" to (firstPage to newTable),
        )
        for ((name, pages) in cases) {
            val (before, next) = pages
            val joined = PageCarry.join(next, PageCarry.tailOf(before)).units
            assertEquals(name, next, joined)
        }
    }

    @Test
    fun `a table followed only by footnotes still runs to the page end, a caption may come before its rest`() {
        val before = firstPage + page(head = null, after = listOf(UnitKind.FOOTNOTE to "Figures in rupees."))
            .map { it.copy(charStart = 900, charEnd = 918, index = 3) }
        val next =
            page(prose = listOf(UnitKind.CAPTION to "Table 2 (continued)"), rows = listOf(listOf("3", "Mohan", "6100")))
        val joined = PageCarry.join(next, PageCarry.tailOf(before)).units
        assertTrue(joined.drop(1).all { it.table!!.continued })
    }

    @Test
    fun `a page that opens inside the sentence the page before broke off continues it`() {
        val before =
            page(prose = listOf(UnitKind.BODY to "He turned and said, “We will meet again when the"), head = null)
        val next = page(prose = listOf(UnitKind.BODY to "rains come.” Then he left."), head = null)
        assertTrue(PageCarry.join(next, PageCarry.tailOf(before)).continuesPreviousPage)
    }

    @Test
    fun `a finished sentence, another kind, or no page before does not continue`() {
        val ended = page(prose = listOf(UnitKind.BODY to "He left at dawn.”"), head = null)
        val broken = page(prose = listOf(UnitKind.BODY to "He turned and said that the"), head = null)
        val body = page(prose = listOf(UnitKind.BODY to "rains came late."), head = null)
        val heading = page(prose = listOf(UnitKind.HEADING to "Chapter Two"), head = null)
        assertFalse(PageCarry.join(body, PageCarry.tailOf(ended)).continuesPreviousPage)
        assertFalse(PageCarry.join(heading, PageCarry.tailOf(broken)).continuesPreviousPage)
        assertFalse(PageCarry.join(body, null).continuesPreviousPage)
        assertNull(PageCarry.tailOf(ended).openSentence)
    }

    private companion object {
        const val PAGE = 7
        const val PAUSE = 300
    }
}
