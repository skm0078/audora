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
import io.github.skm0078.audora.pdf.layout.TextTurn
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
        val own = carry.join(headless, null)
        assertEquals(null, own.table)
        val (summary, demoted, second) = own.units
        assertEquals(UnitKind.TABLE_SUMMARY, summary.kind)
        assertEquals("", summary.text)
        assertEquals(demoted.charStart to demoted.charStart, summary.charStart to summary.charEnd)
        assertEquals(null, summary.bounds)
        assertEquals(false, summary.table!!.continued)
        assertEquals(emptyList<Any>(), summary.table!!.cells)
        assertEquals(listOf(UnitKind.TABLE_ROW, UnitKind.TABLE_ROW), listOf(demoted.kind, second.kind))
        assertEquals(3, demoted.table!!.cells.size)
        assertEquals(listOf(null, 0, 1), own.units.map { it.table!!.row })
        assertEquals(List(3) { 2 }, own.units.map { it.table!!.rowCount })
        assertEquals(List(3) { emptyList<String>() }, own.units.map { it.table!!.headers })
        assertEquals(listOf(0, 1, 2), own.units.map { it.index })
        assertEquals(own.units, carry.join(own.units, null).units)
        val units = carry.join(next, carry.tailOf(own.units)).units
        assertEquals(List(3) { emptyList<String>() }, units.map { it.table!!.headers })
        assertEquals(listOf(null, 0, 1), units.map { it.table!!.row })
        assertEquals(List(3) { 2 }, units.map { it.table!!.rowsBefore })
    }

    @Test
    fun `a table with a header of its own is left as it is, on a first page too`() {
        val cases = listOf(
            "numbered" to (listOf("No.", "Name", "Pay") to listOf(listOf("1", "Asha", "37600"))),
            "words over words and a phone number" to (
                listOf("Name", "Designation", "Department", "Phone") to
                    listOf(
                        listOf("Asha Rao", "Clerk", "Stores", "2345678"),
                        listOf("Ravi", "Peon", "Accounts", "2345690"),
                    )
                ),
            "words over an age" to (
                listOf("Name", "Age", "City", "State") to
                    listOf(listOf("Ram", "34", "Pune", "MH"), listOf("Sita", "29", "Agra", "UP"))
                ),
            "Hindi" to
                (listOf("नाम", "आयु", "शहर") to listOf(listOf("राम", "३४", "पुणे"), listOf("सीता", "२९", "आगरा"))),
            "Bengali" to
                (listOf("নাম", "বয়স", "শহর") to listOf(listOf("রাম", "৩৪", "পুনে"), listOf("সীতা", "২৯", "আগ্রা"))),
            "two census years" to (
                listOf("District", "2011", "2021") to
                    listOf(listOf("Pune", "94,29,408", "1,04,12,000"), listOf("Agra", "44,18,797", "49,02,300"))
                ),
            "one year over larger figures" to
                (listOf("State", "2020") to listOf(listOf("Goa", "15352"), listOf("Assam", "27500"))),
            "subject codes over marks" to
                (listOf("Name", "041", "042") to listOf(listOf("Ram", "78", "85"), listOf("Sita", "66", "91"))),
            "column numbers" to (
                listOf("Item", "1", "2", "3") to
                    listOf(listOf("Rice", "45", "50", "61"), listOf("Dal", "30", "35", "38"))
                ),
            "columns numbered after two word columns" to (
                listOf("Name", "Class", "1", "2", "3") to
                    listOf(listOf("Ram", "VI", "45", "50", "61"), listOf("Sita", "VII", "30", "35", "38"))
                ),
            "columns numbered in brackets" to (
                listOf("(1)", "(2)", "(3)", "(4)") to
                    listOf(listOf("Pune", "45", "50", "61"), listOf("Agra", "30", "35", "38"))
                ),
            "every column numbered" to (
                listOf("1", "2", "3", "4") to
                    listOf(listOf("Pune", "45", "50", "61"), listOf("Agra", "30", "35", "38"))
                ),
        )
        for ((name, table) in cases) {
            val own = page(head = table.first, rows = table.second)
            assertEquals(name, own, carry.join(own, null).units)
        }
    }

    @Test
    fun `an unnumbered first row written like the rows in every number column is data`() {
        val headless = page(
            head = listOf("Raj Kumar", "Stores", "37,600"),
            rows = listOf(listOf("Ram Singh", "Accounts", "28,400"), listOf("Gita", "Stores", "31,250")),
        )
        assertEquals(listOf(null, 0, 1, 2), carry.join(headless, null).units.map { it.table!!.row })
        val dashed = page(
            head = listOf("New grant", "1,333", "-"),
            rows = listOf(listOf("Old grant", "2,667", "4,000"), listOf("Loans", "1,200", "3,100")),
        )
        assertEquals(listOf(null, 0, 1, 2), carry.join(dashed, null).units.map { it.table!!.row })
        // Small counts and a grid numbered row by row are data too; so is a first row saying it has no figure.
        val data = listOf(
            listOf("Ram", "1", "2") to listOf(listOf("Sita", "4", "7"), listOf("Gita", "3", "5")),
            listOf("Ram", "0", "1") to listOf(listOf("Sita", "4", "7"), listOf("Gita", "3", "5")),
            listOf("1", "2", "3", "4") to listOf(listOf("5", "6", "7", "8"), listOf("9", "10", "11", "12")),
            listOf("New grant", "Nil", "4,000") to listOf(listOf("Old grant", "2,667", "4,000")),
            listOf("New grant", "N.A.", "4,000") to listOf(listOf("Old grant", "2,667", "4,000")),
            listOf("नया अनुदान", "निरंक", "४,०००") to listOf(listOf("पुराना अनुदान", "२,६६७", "४,०००")),
        )
        for ((head, rows) in data) {
            val numbered = carry.join(page(head = head, rows = rows), null).units.map { it.table!!.row }
            assertEquals(head.toString(), listOf(null) + List(rows.size + 1) { it }, numbered)
        }
    }

    @Test
    fun `column numbers opening the next page repeat the numbering row of the table`() {
        // A word header on the first page, its numbering row opening the next: only the count says it repeats.
        val zones = listOf("East", "West", "North", "South")
        val before = page(head = zones, rows = listOf(listOf("45", "50", "61", "70"), listOf("30", "35", "38", "41")))
        for (numbers in listOf(listOf("1", "2", "3", "4"), listOf("(1)", "(2)", "(3)", "(4)"))) {
            val next = page(head = numbers, rows = listOf(listOf("52", "57", "63", "66")))
            assertEquals(numbers.toString(), TableCarry.HEADER_REPEATED, carry.join(next, carry.tailOf(before)).table)
        }
        val named = page(head = listOf("Item", "A", "B", "C"), rows = listOf(listOf("Rice", "45", "50", "61")))
        val counted = page(head = listOf("Item", "1", "2", "3"), rows = listOf(listOf("Dal", "30", "35", "38")))
        assertEquals(TableCarry.HEADER_REPEATED, carry.join(counted, carry.tailOf(named)).table)
    }

    @Test
    fun `a word header opening the next page starts a table of its own, a row with a dash carries on`() {
        val before = page(head = listOf("No.", "Item", "Qty", "Cost"), rows = listOf(listOf("1", "Rice", "10", "400")))
        val people = page(
            head = listOf("Name", "Age", "City", "State"),
            rows = listOf(listOf("Ram", "34", "Pune", "MH"), listOf("Sita", "29", "Agra", "UP")),
        )
        assertEquals(people, carry.join(people, carry.tailOf(before)).units)
        val budget = page(head = listOf("Item", "Plan", "Actual"), rows = listOf(listOf("Loans", "1,200", "3,100")))
        val grant = page(head = listOf("Grant", "-", "4,000"), rows = listOf(listOf("Old grant", "2,667", "4,000")))
        assertEquals(TableCarry.DATA_FIRST, carry.join(grant, carry.tailOf(budget)).table)
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
    fun `years in rows, a salary like a year, ID columns and an empty column are data, not a header`() {
        fun carried(before: List<List<String>>, head: List<String>, next: List<List<String>>, columns: List<String>) =
            carry.join(page(head = head, rows = next), carry.tailOf(page(head = columns, rows = before))).table
        val perYear = listOf("Year", "Receipts", "Spending")
        assertEquals(
            TableCarry.DATA_FIRST,
            carried(
                listOf(listOf("2016-17", "120", "130"), listOf("2017-18", "140", "150")),
                listOf("2018-19", "160", "170"),
                listOf(listOf("2019-20", "180", "190")),
                perYear,
            ),
        )
        val staff = listOf("Name", "Dept", "Salary")
        assertEquals(
            TableCarry.DATA_FIRST,
            carried(
                listOf(listOf("Ram", "Stores", "28400"), listOf("Sita", "Accounts", "31200")),
                listOf("Mohan", "Stores", "2000"),
                listOf(listOf("Gita", "Accounts", "29900")),
                staff,
            ),
        )
        val ids = listOf("ID", "Name", "Ward")
        assertEquals(
            TableCarry.DATA_FIRST,
            carried(
                listOf(listOf("12345", "Ram", "A"), listOf("678", "Sita", "B")),
                listOf("1999", "Mohan", "C"),
                listOf(listOf("40213", "Gita", "D")),
                ids,
            ),
        )
        // A year in a column empty on every other row says nothing.
        val empty = page(head = listOf("Paper", "2010"), rows = listOf(listOf("Pens", ""), listOf("Ink", "")))
        assertEquals(YearCue.NONE, yearCue(empty.first(), empty.drop(1)))
    }

    @Test
    fun `a year as long as its column's figures decides nothing, so only a serial number carries the table on`() {
        val cases = listOf(
            "a year over four-digit figures" to (listOf("State", "2020") to listOf(listOf("Goa", "5352"))),
            "in Devanagari digits" to (listOf("राज्य", "२०२०") to listOf(listOf("गोवा", "५३५२"))),
            "amounts that look like years" to (listOf("Item", "1850") to listOf(listOf("Rice", "4210"))),
        )
        for ((name, rows) in cases) {
            val (head, next) = rows
            val units = page(head = head, rows = next)
            assertEquals(name, YearCue.UNDECIDED, yearCue(units.first(), units.drop(1)))
        }
        // A span of years always names years; a column all years makes years data.
        val span = page(head = listOf("State", "2019-20"), rows = listOf(listOf("Goa", "5352")))
        assertEquals(YearCue.HEADER, yearCue(span.first(), span.drop(1)))
        val perYear = page(head = listOf("Total", "1998"), rows = listOf(listOf("Rice", "2004"), listOf("Dal", "2011")))
        assertEquals(YearCue.NONE, yearCue(perYear.first(), perYear.drop(1)))
        // With a serial number running on, a row of years in a four-digit column is the table's next row.
        val before = page(head = listOf("No.", "Item", "Cost"), rows = listOf(listOf("1", "Rice", "4210")))
        val next = page(head = listOf("2", "Dal", "1850"), rows = listOf(listOf("3", "Oil", "2003")))
        assertEquals(TableCarry.DATA_FIRST, carry.join(next, carry.tailOf(before)).table)
        val unnumbered = page(head = listOf("Item", "1850"), rows = listOf(listOf("Oil", "2975")))
        val priced = page(head = listOf("Item", "Cost"), rows = listOf(listOf("Rice", "4210")))
        assertEquals(unnumbered, carry.join(unnumbered, carry.tailOf(priced)).units)
    }

    @Test
    fun `a header of years over four-figure data opens a new table, not a continued one`() {
        val before = page(head = listOf("State", "Funds", "Spent"), rows = listOf(listOf("Assam", "4599", "5131")))
        val next = page(head = listOf("State", "2019", "2020"), rows = listOf(listOf("Goa", "5352", "7506")))
        assertEquals(next, carry.join(next, carry.tailOf(before)).units)
    }

    @Test
    fun `a caption that does not say the table continues opens a new table`() {
        val rest = listOf(listOf("3", "Mohan", "6100"), listOf("4", "Gita", "7200"))
        val captioned =
            page(prose = listOf(UnitKind.CAPTION to "Table 3: Pensions"), head = rest[0], rows = rest.drop(1))
        // A new table, read as if the page were read alone (its numbered first row demoted to data).
        val joined = carry.join(captioned, carry.tailOf(firstPage))
        assertNull(joined.table)
        assertEquals(carry.join(captioned, null).units, joined.units)
        assertFalse(joined.units.any { it.table?.continued == true })
        // The words within a caption are ordinary words.
        for (caption in listOf("तालिका 3: जारी किए गए प्रमाण पत्र", "সারণি ৩: ক্রমশ বাড়ছে খরচ")) {
            val ordinary = page(prose = listOf(UnitKind.CAPTION to caption), head = rest[0], rows = rest.drop(1))
            assertEquals(caption, carry.join(ordinary, null).units, carry.join(ordinary, carry.tailOf(firstPage)).units)
        }
        for (caption in listOf(
            "Table 2 (contd.)",
            "तालिका 2 (जारी)",
            "तालिका 2 (जारी है)",
            "तालिका 2 (पिछले पृष्ठ से जारी)",
            "सारणी 2 जारी है",
            "সারণি ২ (ক্রমশ)",
            "সারণি ২ ক্রমশঃ",
        )) {
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

    /**
     * A Hindi page of one sentence over [lines], each a line's left and right edge as read; [sideways]: printed
     * running up the page (a /Rotate scan), so its boxes in page space are turned a quarter.
     */
    private fun hindi(text: String, lines: List<Pair<Float, Float>>, sideways: Boolean = false): List<ReadingUnit> {
        val bounds = lines.mapIndexed { i, (left, right) ->
            val upright = TextBox(left, LINE_TOP - i * LINE_PITCH, right, LINE_TOP - i * LINE_PITCH - LINE_HEIGHT)
            if (sideways) TextTurn.COUNTER_CLOCKWISE.apply(upright) else upright
        }
        return listOf(ReadingUnit(PAGE, 0, 0, text.length, text, Lang.HI, PAUSE, bounds, UnitKind.BODY))
    }

    @Test
    fun `in Hindi a page continues the sentence only when the layout agrees`() {
        val full = List(3) { MARGIN to RIGHT }
        val broken = "वह बोला कि हम फिर मिलेंगे जब"
        val rest = "बारिश आएगी। फिर वह चला गया और नहीं लौटा।"
        val before = hindi(broken, full)
        val after = hindi(rest, full)
        assertTrue(carry.join(after, carry.tailOf(before)).continuesPreviousPage)
        val indented = hindi(rest, listOf(INDENT to RIGHT) + List(2) { MARGIN to RIGHT })
        assertFalse(carry.join(indented, carry.tailOf(before)).continuesPreviousPage)
        val shortLast = hindi(broken, List(2) { MARGIN to RIGHT } + (MARGIN to SHORT))
        assertFalse(carry.join(after, carry.tailOf(shortLast)).continuesPreviousPage)
        // Ragged right: the last line reaches the lines' usual edge, not the longest one.
        val ragged = hindi(broken, listOf(MARGIN to RIGHT + RAGGED, MARGIN to RIGHT, MARGIN to RIGHT - 2))
        assertTrue(carry.join(after, carry.tailOf(ragged)).continuesPreviousPage)
    }

    @Test
    fun `in Hindi the layout is read upright, needs other lines, and decides whatever the next page opens with`() {
        val broken = "वह बोला कि हम फिर मिलेंगे जब"
        val rest = "बारिश आएगी। फिर वह चला गया और नहीं लौटा।"
        val full = List(3) { MARGIN to RIGHT }
        val sideways = carry.join(
            hindi(rest, full, sideways = true),
            carry.tailOf(hindi(broken, full, sideways = true)),
        )
        assertTrue(sideways.continuesPreviousPage)
        // A sentence alone in its column is measured against nothing.
        assertFalse(
            carry.join(hindi(rest, full), carry.tailOf(hindi(broken, listOf(MARGIN to RIGHT)))).continuesPreviousPage,
        )
        // A Hindi sentence may go on with an acronym or a number.
        for (opening in listOf(
            "RBI ने कहा कि दरें नहीं बदलेंगी।",
            "2019 में यह कानून बना था।",
            "में 40 से कम था।",
            "15 अगस्त को झंडा फहराया गया।",
        )) {
            assertTrue(
                opening,
                carry.join(hindi(opening, full), carry.tailOf(hindi(broken, full))).continuesPreviousPage,
            )
        }
        // But a page opening with an item's number starts a new item.
        val item = hindi("2. समुदाय भर में स्वच्छता बनी रहे।", full)
        assertFalse(carry.join(item, carry.tailOf(hindi(broken, full))).continuesPreviousPage)
    }

    private companion object {
        const val LINE_TOP = 700f
        const val LINE_PITCH = 16f
        const val LINE_HEIGHT = 12f
        const val MARGIN = 50f
        const val INDENT = 80f
        const val RIGHT = 550f
        const val SHORT = 250f
        const val RAGGED = 40f
        const val PAGE = 7
        const val PAUSE = 300
    }
}
