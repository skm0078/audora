// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/units/PageCarryTest.kt
// Purpose: A table or sentence printed over a page break is carried on to the next page (RI-4a, plan item 7).

package io.github.skm0078.audora.pdf.units

import io.github.skm0078.audora.core.config.PageCarryConfig
import io.github.skm0078.audora.core.model.CellSpan
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.model.TableRowInfo
import io.github.skm0078.audora.core.model.TextBox
import io.github.skm0078.audora.core.model.UnitKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageCarryTest {
    private val carry = PageCarry(PageCarryConfig())
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
        val joined = carry.join(next, carry.tailOf(firstPage)).units
        assertEquals(List(3) { true }, joined.map { it.table!!.continued })
        assertEquals(List(3) { 2 }, joined.map { it.table!!.rowsBefore })
        assertEquals(next.map { it.kind to it.text }, joined.map { it.kind to it.text })
    }

    @Test
    fun `a first row of data on the next page is read as a row under a continued summary`() {
        val next = page(head = listOf("3", "Mohan", "6100"), rows = listOf(listOf("4", "Gita", "7200")))
        val joined = carry.join(next, carry.tailOf(firstPage))
        assertEquals(TableCarry.DATA_FIRST, joined.table)
        val units = joined.units
        assertEquals(listOf(UnitKind.TABLE_SUMMARY, UnitKind.TABLE_ROW, UnitKind.TABLE_ROW), units.map { it.kind })
        assertEquals(listOf(null, 0, 1), units.map { it.table!!.row })
        assertEquals(List(3) { 2 }, units.map { it.table!!.rowCount })
        assertEquals(List(3) { header }, units.map { it.table!!.headers })
        assertEquals(List(3) { true }, units.map { it.table!!.continued })
        assertEquals(List(3) { 2 }, units.map { it.table!!.rowsBefore })
        assertEquals(
            "" to (next[0].charStart to next[0].charStart),
            units[0].text to (units[0].charStart to units[0].charEnd),
        )
        assertEquals(next.map { it.charStart to it.text }, units.drop(1).map { it.charStart to it.text })
        assertEquals(listOf(0, 1, 2), units.map { it.index })
    }

    @Test
    fun `the rows read so far add up over several pages`() {
        val second = carry.join(
            page(rows = listOf(listOf("3", "Mohan", "6100"))),
            carry.tailOf(firstPage),
        ).units
        val third = page(rows = listOf(listOf("4", "Gita", "7200")))
        assertEquals(3, carry.join(third, carry.tailOf(second)).units.first().table!!.rowsBefore)
    }

    @Test
    fun `a table whose own first row is data hands on no header and numbers on from the rows heard`() {
        val headless =
            page(head = listOf("68", "Raj Kumar", "37600"), rows = listOf(listOf("69", "Ram Singh", "28400")))
        val next = page(head = listOf("70", "Mohan", "6100"), rows = listOf(listOf("71", "Gita", "7200")))
        val units = carry.join(next, carry.tailOf(headless)).units
        assertEquals(List(3) { emptyList<String>() }, units.map { it.table!!.headers })
        assertEquals(listOf(null, 0, 1), units.map { it.table!!.row })
        assertEquals(List(3) { 1 }, units.map { it.table!!.rowsBefore })
    }

    @Test
    fun `a header of years is a header, carried and repeated`() {
        val years = listOf("State", "2018-19", "2019-20")
        val before = page(head = years, rows = listOf(listOf("Assam", "120", "130"), listOf("Bihar", "140", "150")))
        val tail = carry.tailOf(before)
        val next = page(head = years, rows = listOf(listOf("Goa", "160", "170")))
        val joined = carry.join(next, tail)
        assertEquals(TableCarry.HEADER_REPEATED, joined.table)
        assertEquals(List(2) { years }, joined.units.map { it.table!!.headers })
        val ownYears = page(head = listOf("District", "2020", "2021"), rows = listOf(listOf("Pune", "10", "12")))
        assertEquals(ownYears, carry.join(ownYears, tail).units)
        val allowances = page(rows = listOf(listOf("1", "Ram", "2940"), listOf("2", "Sita", "1463")))
        val yearLike = page(head = listOf("9", "Gita", "1988"), rows = listOf(listOf("10", "Hari", "2072")))
        assertEquals(TableCarry.DATA_FIRST, carry.join(yearLike, carry.tailOf(allowances)).table)
    }

    @Test
    fun `a caption that does not say the table continues opens a new table`() {
        val rest = listOf(listOf("3", "Mohan", "6100"), listOf("4", "Gita", "7200"))
        val captioned =
            page(prose = listOf(UnitKind.CAPTION to "Table 3: Pensions"), head = rest[0], rows = rest.drop(1))
        assertEquals(captioned, carry.join(captioned, carry.tailOf(firstPage)).units)
        for (caption in listOf("Table 2 (contd.)", "तालिका 2 (जारी)", "সারণি ২ (ক্রমশ)")) {
            val continued = page(prose = listOf(UnitKind.CAPTION to caption), head = rest[0], rows = rest.drop(1))
            assertEquals(caption, TableCarry.DATA_FIRST, carry.join(continued, carry.tailOf(firstPage)).table)
        }
    }

    @Test
    fun `one shared cell or letters alike without their vowel signs is not a repeated header`() {
        val names = page(head = listOf("S.No.", "Name"), rows = listOf(listOf("1", "Ram"), listOf("2", "Sita")))
        val villages = page(head = listOf("S.No.", "Village"), rows = listOf(listOf("1", "Rampur")))
        assertEquals(villages, carry.join(villages, carry.tailOf(names)).units)
        val hindi = page(head = listOf("नाम", "राशि"), rows = listOf(listOf("राम", "सौ"), listOf("सीता", "दो सौ")))
        val other = page(head = listOf("नमी", "रशि"), rows = listOf(listOf("गीता", "तीन सौ")))
        assertEquals(other, carry.join(other, carry.tailOf(hindi)).units)
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
            val joined = carry.join(next, carry.tailOf(before)).units
            assertEquals(name, next, joined)
        }
    }

    @Test
    fun `a table followed only by footnotes still runs to the page end, a caption may come before its rest`() {
        val before = firstPage + page(head = null, after = listOf(UnitKind.FOOTNOTE to "Figures in rupees."))
            .map { it.copy(charStart = 900, charEnd = 918, index = 3) }
        val next =
            page(prose = listOf(UnitKind.CAPTION to "Table 2 (continued)"), rows = listOf(listOf("3", "Mohan", "6100")))
        val joined = carry.join(next, carry.tailOf(before)).units
        assertTrue(joined.drop(1).all { it.table!!.continued })
    }

    @Test
    fun `a page that opens inside the sentence the page before broke off continues it`() {
        val before =
            page(prose = listOf(UnitKind.BODY to "He turned and said, “We will meet again when the"), head = null)
        val next = page(prose = listOf(UnitKind.BODY to "rains come.” Then he left."), head = null)
        assertTrue(carry.join(next, carry.tailOf(before)).continuesPreviousPage)
    }

    @Test
    fun `a finished sentence, another kind, or no page before does not continue`() {
        val ended = page(prose = listOf(UnitKind.BODY to "He left at dawn.”"), head = null)
        val broken = page(prose = listOf(UnitKind.BODY to "He turned and said that the"), head = null)
        val body = page(prose = listOf(UnitKind.BODY to "rains came late."), head = null)
        val heading = page(prose = listOf(UnitKind.HEADING to "Chapter Two"), head = null)
        assertFalse(carry.join(body, carry.tailOf(ended)).continuesPreviousPage)
        assertFalse(carry.join(heading, carry.tailOf(broken)).continuesPreviousPage)
        assertFalse(carry.join(body, null).continuesPreviousPage)
        assertNull(carry.tailOf(ended).openSentence)
    }

    /** A Hindi page of one sentence over [lines], each a line's left and right edge. */
    private fun hindi(text: String, lines: List<Pair<Float, Float>>): List<ReadingUnit> {
        val bounds = lines.mapIndexed { i, (left, right) ->
            TextBox(left, LINE_TOP - i * LINE_PITCH, right, LINE_TOP - i * LINE_PITCH - LINE_HEIGHT)
        }
        return listOf(ReadingUnit(PAGE, 0, 0, text.length, text, Lang.HI, PAUSE, bounds, UnitKind.BODY))
    }

    @Test
    fun `in Hindi a page continues the sentence only when the layout agrees`() {
        val full = listOf(MARGIN to RIGHT, MARGIN to RIGHT)
        val before = hindi("वह बोला कि हम फिर मिलेंगे जब", full)
        val after = hindi("बारिश आएगी। फिर वह चला गया और नहीं लौटा।", full)
        assertTrue(carry.join(after, carry.tailOf(before)).continuesPreviousPage)
        val indented = hindi("बारिश आएगी। फिर वह चला गया और नहीं लौटा।", listOf(INDENT to RIGHT, MARGIN to RIGHT))
        assertFalse(carry.join(indented, carry.tailOf(before)).continuesPreviousPage)
        val shortLast = hindi("वह बोला कि हम फिर मिलेंगे जब", listOf(MARGIN to RIGHT, MARGIN to SHORT))
        assertFalse(carry.join(after, carry.tailOf(shortLast)).continuesPreviousPage)
    }

    private companion object {
        const val LINE_TOP = 700f
        const val LINE_PITCH = 16f
        const val LINE_HEIGHT = 12f
        const val MARGIN = 50f
        const val INDENT = 80f
        const val RIGHT = 550f
        const val SHORT = 250f
        const val PAGE = 7
        const val PAUSE = 300
    }
}
