// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/golden/listening/ReadingUnitTsvTest.kt
// Purpose: The units TSV keeps every field :playback's listening test reads, with text escaped onto one line.

package io.github.skm0078.audora.pdf.golden.listening

import io.github.skm0078.audora.core.model.CellSpan
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.model.TableRowInfo
import io.github.skm0078.audora.core.model.UnitKind
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingUnitTsvTest {
    @Test
    fun `units are written one per line in column order`() {
        val body = ReadingUnit(0, 0, 0, 9, "tab\there", Lang.EN, 400, null)
        val row = ReadingUnit(
            0, 1, 10, 16, "Ram 34", Lang.EN, 0, null, UnitKind.TABLE_ROW, speakScore = 0.25f,
            table = TableRowInfo(0, 1, 3, 2, listOf("Name", "Age"), listOf(CellSpan(0, 3), CellSpan(4, 6))),
        )
        val lines = ReadingUnitTsv.write(listOf(body, row)).lines()
        assertEquals(ReadingUnitTsv.COLUMNS.joinToString("\t"), lines[0])
        assertEquals("0\t0\t0\t9\tBODY\tEN\t400\t\t\t\t\t\t\t\t\ttab\\there", lines[1])
        assertEquals("0\t1\t10\t16\tTABLE_ROW\tEN\t0\t0.25\t\t0\t1\t3\t2\tName\u001FAge\t0-3,4-6\tRam 34", lines[2])
    }

    @Test
    fun `line breaks, backslashes and the header separator are escaped`() {
        assertEquals("a\\nb\\\\c\\ud\\r", ReadingUnitTsv.escape("a\nb\\c\u001Fd\r"))
    }
}
