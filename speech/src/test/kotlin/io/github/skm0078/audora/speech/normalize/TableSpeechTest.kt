// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/normalize/TableSpeechTest.kt
// Purpose: A table's summary and rows as spoken, in English, Hindi and Bengali (RI-4b, Q-4), continued tables too.

package io.github.skm0078.audora.speech.normalize

import io.github.skm0078.audora.core.model.CellSpan
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.TableRowInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class TableSpeechTest {
    /** A row whose cells are the words of [text], separated by " | ". */
    private fun row(text: String, headers: List<String>, row: Int? = 0, rows: Int = 3): Pair<TableRowInfo, String> {
        val cells = ArrayList<CellSpan>()
        var start = 0
        for (cell in text.split(" | ")) {
            cells.add(CellSpan(start, start + cell.length))
            start += cell.length + 3
        }
        return TableRowInfo(0, row, rows, cells.size, headers, cells) to text
    }

    private fun summaryOf(rows: Int, columns: Int, headers: List<String> = emptyList()) =
        TableRowInfo(0, null, rows, columns, headers, emptyList())

    @Test
    fun `a summary counts data rows and names the columns, in three languages`() {
        val cases = listOf(
            Triple(summaryOf(4, 5), Lang.EN, "A table of 4 rows and 5 columns."),
            Triple(summaryOf(1, 1), Lang.EN, "A table of 1 row and 1 column."),
            Triple(
                summaryOf(3, 2, listOf("Name", "Age:")),
                Lang.EN,
                "A table of 3 rows, with columns for Name and Age.",
            ),
            Triple(
                summaryOf(3, 3, listOf("Year", "Sales (₹ crore)", "Profit")),
                Lang.EN,
                "A table of 3 rows, with columns for Year, Sales, and Profit.",
            ),
            Triple(summaryOf(1, 1, listOf("Name")), Lang.EN, "A table of 1 row, with a column for Name."),
            // Too many to list, or some unnamed: the first few, as "including".
            Triple(
                summaryOf(6, 6, listOf("A", "B", "C", "D", "E", "F")),
                Lang.EN,
                "A table of 6 rows and 6 columns, including A, B, and C.",
            ),
            Triple(
                summaryOf(2, 3, listOf("", "2019", "2020")),
                Lang.EN,
                "A table of 2 rows and 3 columns, including 2019 and 2020.",
            ),
            // A paper's table: brackets dropped, a repeated name said once.
            Triple(
                summaryOf(4, 5, listOf("", "Mean (n = 104)", "p-value", "Follow-up mean (n = 116)", "p-value")),
                Lang.EN,
                "A table of 4 rows and 5 columns, including Mean, p-value, and Follow-up mean.",
            ),
            Triple(summaryOf(3, 2), Lang.HI, "3 पंक्तियों और 2 स्तंभों की तालिका।"),
            Triple(summaryOf(1, 2), Lang.HI, "1 पंक्ति और 2 स्तंभों की तालिका।"),
            Triple(
                summaryOf(3, 2, listOf("नाम", "आयु")),
                Lang.HI,
                "3 पंक्तियों की तालिका, जिसमें नाम और आयु के स्तंभ हैं।",
            ),
            Triple(summaryOf(5, 4), Lang.BN, "5 সারি আর 4 কলামের একটি সারণি।"),
            Triple(summaryOf(5, 2, listOf("নাম", "বয়স")), Lang.BN, "5 সারির একটি সারণি, কলামগুলো হল নাম আর বয়স।"),
        )
        for ((table, lang, spoken) in cases) assertEquals(spoken, TableSpeech.summary(table, lang))
    }

    @Test
    fun `a row is one sentence led by its name, its other cells with their headers`() {
        val cases = listOf(
            Triple(row("Ram | 34", listOf("Name", "Age")), Lang.EN, "Ram: Age 34."),
            Triple(row("Ram | 34 | Delhi", listOf("Name", "Age", "City")), Lang.EN, "Ram: Age 34 and City Delhi."),
            Triple(row("Ram |  | 34", listOf("Name:", "City", "Age"), row = 2), Lang.EN, "Ram: Age 34."),
            Triple(row("Ram | 34", emptyList()), Lang.EN, "Ram, 34."),
            Triple(row("Ram | 34", listOf("", "Age")), Lang.EN, "Ram: Age 34."),
            // A number alone does not say what it is: its header stays in front.
            Triple(
                row("2021 | 40 | 8", listOf("Year", "Sales", "Profit")),
                Lang.EN,
                "Year 2021: Sales 40 and Profit 8.",
            ),
            // An empty first cell: no name to lead with.
            Triple(row(" | 40", listOf("Year", "Sales")), Lang.EN, "Sales 40."),
            Triple(row("राम | 34 | दिल्ली", listOf("नाम", "आयु", "शहर")), Lang.HI, "राम: आयु 34 और शहर दिल्ली।"),
            Triple(row("রাম | ৩৪", listOf("নাম", "বয়স"), row = 1), Lang.BN, "রাম: বয়স ৩৪।"),
            Triple(row("রাম | ৩৪ | ঢাকা", listOf("নাম", "বয়স", "শহর")), Lang.BN, "রাম: বয়স ৩৪ আর শহর ঢাকা।"),
        )
        for ((input, lang, spoken) in cases) {
            val (table, text) = input
            assertEquals(spoken, TableSpeech.row(table, text, lang))
        }
    }

    @Test
    fun `a header's unit goes after a number, as said aloud`() {
        val headers = listOf("Item", "Weight (kg)", "Growth (%)", "Price (₹ crore)", "Sales (in lakh)", "Cost ($)")
        val (table, text) = row("Wheat | 34 | 12 | 40 | 3.5 | 1,200", headers)
        assertEquals(
            "Wheat: Weight 34 kg, Growth 12%, Price ₹40 crore, Sales 3.5 lakh. Cost $1,200.",
            TableSpeech.row(table, text, Lang.EN),
        )
        // A value that is not a number keeps the header's name only; a bracket with a year is not a unit.
        val (other, otherText) = row("Rice | n/a | 50", listOf("Item", "Weight (kg)", "Population (2011)"))
        assertEquals("Rice: Weight n/a and Population (2011) 50.", TableSpeech.row(other, otherText, Lang.EN))
    }

    @Test
    fun `a wide row is split into sentences so it has places to breathe`() {
        val headers = listOf("Name", "A", "B", "C", "D", "E", "F")
        val (table, text) = row("Ram | 1 | 2 | 3 | 4 | 5 | 6", headers)
        assertEquals("Ram: A 1, B 2, C 3, D 4. E 5 and F 6.", TableSpeech.row(table, text, Lang.EN))
    }

    @Test
    fun `a cell ruled through instead of filled reads as nil`() {
        val cases = listOf(
            Triple(row("2019 | –", listOf("Year", "Loss")), Lang.EN, "Year 2019: Loss nil."),
            Triple(row("2019 | -----", emptyList()), Lang.EN, "2019, nil."),
            Triple(row("2019 | −", listOf("वर्ष", "हानि")), Lang.HI, "वर्ष 2019: हानि कुछ नहीं।"),
            Triple(row("2019 | —", listOf("বছর", "ক্ষতি")), Lang.BN, "বছর 2019: ক্ষতি নেই।"),
            Triple(
                row("2019 | –* | –¹", listOf("Year", "Loss", "Tax")),
                Lang.EN,
                "Year 2019: Loss nil and Tax nil.",
            ),
            // Dots only ("not available") are skipped with their header, like an empty cell.
            Triple(row("Wheat | .. | 12", listOf("Crop", "2019", "2020")), Lang.EN, "Wheat: 2020 12."),
            Triple(row("Wheat | …", emptyList()), Lang.EN, "Wheat."),
            // A header ruled through gives the cell no header.
            Triple(row("Revenue | 5", listOf("–", "Units")), Lang.EN, "Revenue: Units 5."),
            // So does a header of dots only.
            Triple(row("Wheat | 12", listOf("Crop", "...")), Lang.EN, "Wheat: 12."),
            // A dash inside a value stays as written.
            Triple(row("2019 | 10–12", listOf("Year", "Range")), Lang.EN, "Year 2019: Range 10–12."),
        )
        for ((input, lang, spoken) in cases) {
            val (table, text) = input
            assertEquals(spoken, TableSpeech.row(table, text, lang))
        }
    }

    @Test
    fun `a row with nothing to say falls back to its number`() {
        val (table, text) = row(" | ..", listOf("Name", "Age"), row = 2)
        assertEquals("Row 3.", TableSpeech.row(table, text, Lang.EN))
    }

    @Test
    fun `the header unit reads as the summary`() {
        val (table, text) = row("Name | Age", listOf("Name", "Age"), row = null, rows = 2)
        assertEquals("A table of 2 rows, with columns for Name and Age.", TableSpeech.row(table, text, Lang.EN))
    }

    @Test
    fun `a table carried over from the page before says it continues`() {
        val cases = listOf(
            Lang.EN to "The table continues, with 7 more rows.",
            Lang.HI to "तालिका जारी है, और 7 पंक्तियाँ।",
            Lang.BN to "সারণি চলছে, আরও 7 সারি।",
        )
        for ((lang, spoken) in cases) {
            val table =
                TableRowInfo(0, null, 7, 2, listOf("Name", "Age"), emptyList(), continued = true, rowsBefore = 12)
            assertEquals(spoken, TableSpeech.summary(table, lang))
        }
        val (first, text) = row("Ram | 34", listOf("Name", "Age"))
        val carried = first.copy(continued = true, rowsBefore = 12)
        assertEquals("Ram: Age 34.", TableSpeech.row(carried, text, Lang.EN))
    }
}
