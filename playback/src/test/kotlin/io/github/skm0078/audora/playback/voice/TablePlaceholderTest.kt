// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/voice/TablePlaceholderTest.kt
// Purpose: Until RI-4b, table rows are skipped and a table's summary unit is read as its size, per language.

package io.github.skm0078.audora.playback.voice

import io.github.skm0078.audora.core.model.CellSpan
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.model.TableRowInfo
import io.github.skm0078.audora.core.model.UnitKind
import io.github.skm0078.audora.playback.unit
import org.junit.Assert.assertEquals
import org.junit.Test

class TablePlaceholderTest {
    private fun tableUnit(index: Int, row: Int?, rows: Int, columns: Int, lang: Lang = Lang.EN): ReadingUnit {
        val headers = List(columns) { "Column $it" }
        val info = TableRowInfo(0, row, rows, columns, headers, listOf(CellSpan(0, 4), CellSpan(5, 8)))
        val kind = if (row == null) UnitKind.TABLE_SUMMARY else UnitKind.TABLE_ROW
        return unit(0, index, lang).copy(text = "Name Age", kind = kind, table = info)
    }

    @Test
    fun `table rows are left out of playback, everything else stays in order`() {
        val units =
            listOf(unit(0, 0), tableUnit(1, null, 2, 2), tableUnit(2, 0, 2, 2), tableUnit(3, 1, 2, 2), unit(0, 4))
        assertEquals(listOf(0, 1, 4), TablePlaceholder.spokenUnits(units).map { it.index })
    }

    @Test
    fun `a table's summary is read as its size, other units as their text`() {
        val cases = listOf(
            tableUnit(1, null, 4, 5) to "4 rows, 5 columns.",
            tableUnit(1, null, 1, 2) to "1 row, 2 columns.",
            tableUnit(1, null, 3, 2, Lang.HI) to "3 पंक्तियाँ, 2 स्तंभ।",
            tableUnit(1, null, 1, 2, Lang.HI) to "1 पंक्ति, 2 स्तंभ।",
            tableUnit(1, null, 5, 4, Lang.BN) to "5 সারি, 4 কলাম।",
            unit(0, 0) to "Sentence 0 of page 0.",
        )
        for ((unit, spoken) in cases) assertEquals(spoken, TablePlaceholder.spokenText(unit))
    }
}
