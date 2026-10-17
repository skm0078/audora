// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/normalize/TableSpeechTest.kt
// Purpose: A table's size and its rows as spoken, in English, Hindi and Bengali (RI-4b), continued tables included.

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

    @Test
    fun `sizes count data rows, in three languages`() {
        val cases = listOf(
            Triple(4, 5, Lang.EN) to "4 rows, 5 columns.",
            Triple(1, 1, Lang.EN) to "1 row, 1 column.",
            Triple(3, 2, Lang.HI) to "3 पंक्तियाँ, 2 स्तंभ।",
            Triple(1, 2, Lang.HI) to "1 पंक्ति, 2 स्तंभ।",
            Triple(5, 4, Lang.BN) to "5 সারি, 4 কলাম।",
        )
        for ((size, spoken) in cases) {
            val (rows, columns, lang) = size
            val table = TableRowInfo(0, null, rows, columns, emptyList(), emptyList())
            assertEquals(spoken, TableSpeech.summary(table, lang))
        }
    }

    @Test
    fun `rows pair each cell with its header`() {
        val cases = listOf(
            Triple(row("Ram | 34", listOf("Name", "Age")), Lang.EN, "Row 1. Name: Ram. Age: 34."),
            Triple(row("Ram |  | 34", listOf("Name:", "City", "Age"), row = 2), Lang.EN, "Row 3. Name: Ram. Age: 34."),
            Triple(row("Ram | 34", emptyList()), Lang.EN, "Row 1. Ram, 34."),
            Triple(row("Ram | 34", listOf("", "Age")), Lang.EN, "Row 1. Ram. Age: 34."),
            Triple(row("राम | 34", listOf("नाम", "आयु")), Lang.HI, "पंक्ति 1। नाम: राम। आयु: 34।"),
            Triple(row("রাম | ৩৪", listOf("নাম", "বয়স"), row = 1), Lang.BN, "সারি 2। নাম: রাম। বয়স: ৩৪।"),
        )
        for ((input, lang, spoken) in cases) {
            val (table, text) = input
            assertEquals(spoken, TableSpeech.row(table, text, lang))
        }
    }

    @Test
    fun `the header unit reads as the size`() {
        val (table, text) = row("Name | Age", listOf("Name", "Age"), row = null, rows = 2)
        assertEquals("2 rows, 2 columns.", TableSpeech.row(table, text, Lang.EN))
    }

    @Test
    fun `a table carried over from the page before says it continues and numbers its rows on`() {
        val cases = listOf(
            Lang.EN to "Table continues, 7 more rows.",
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
        assertEquals("Row 13. Name: Ram. Age: 34.", TableSpeech.row(carried, text, Lang.EN))
    }
}
