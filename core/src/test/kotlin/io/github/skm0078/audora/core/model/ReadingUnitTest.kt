// File: core/src/test/kotlin/io/github/skm0078/audora/core/model/ReadingUnitTest.kt
// Purpose: ReadingUnit invariants (offsets, speakability fields, table info) and TextBox geometry.

package io.github.skm0078.audora.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingUnitTest {
    private fun unit(charStart: Int, charEnd: Int) = ReadingUnit(0, 0, charStart, charEnd, "x", Lang.EN, 0, null)

    @Test(expected = IllegalArgumentException::class)
    fun `charEnd before charStart is rejected`() {
        unit(charStart = 5, charEnd = 4)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative offsets are rejected`() {
        unit(charStart = -1, charEnd = 4)
    }

    @Test
    fun `existing call sites get unscored body units with no table or note`() {
        val plain = unit(0, 1)
        assertEquals(UnitKind.BODY, plain.kind)
        assertNull(plain.speakScore)
        assertNull(plain.table)
        assertNull(plain.noteRef)
    }

    @Test
    fun `speakScore outside 0 to 1 is rejected`() {
        listOf(-0.01f, 1.01f, Float.NaN).forEach { score ->
            assertThrows("score $score", IllegalArgumentException::class.java) { unit(0, 1).copy(speakScore = score) }
        }
        listOf(0f, 0.5f, 1f).forEach { score -> assertEquals(score, unit(0, 1).copy(speakScore = score).speakScore) }
    }

    @Test
    fun `table info is required on table units and refused on every other kind`() {
        val row = "Ram 34"
        val info = TableRowInfo(0, 1, 3, 2, listOf("Name", "Age"), listOf(CellSpan(0, 3), CellSpan(4, 6)))
        val tableRow = ReadingUnit(0, 0, 0, row.length, row, Lang.EN, 0, null, UnitKind.TABLE_ROW, table = info)
        assertEquals(info, tableRow.table)

        assertThrows(IllegalArgumentException::class.java) { tableRow.copy(table = null) }
        assertThrows(IllegalArgumentException::class.java) { tableRow.copy(kind = UnitKind.BODY) }
        assertThrows(IllegalArgumentException::class.java) { tableRow.copy(text = "Ram") }
    }

    @Test
    fun `table info checks its own shape`() {
        assertThrows(IllegalArgumentException::class.java) { TableRowInfo(0, 3, 3, 2, emptyList(), emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { TableRowInfo(0, null, 3, 2, listOf("A"), emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { TableRowInfo(0, null, 0, 2, emptyList(), emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { CellSpan(4, 3) }
        assertEquals(null, TableRowInfo(0, null, 3, 2, emptyList(), emptyList()).row)
    }

    @Test
    fun `noteRef belongs to footnotes only`() {
        val note = unit(0, 1).copy(kind = UnitKind.FOOTNOTE, noteRef = 2)
        assertEquals(2, note.noteRef)
        assertThrows(IllegalArgumentException::class.java) { unit(0, 1).copy(noteRef = 2) }
    }

    @Test
    fun `only the two table kinds are table kinds`() {
        assertTrue(UnitKind.TABLE_SUMMARY.isTable)
        assertTrue(UnitKind.TABLE_ROW.isTable)
        UnitKind.entries.filterNot { it == UnitKind.TABLE_SUMMARY || it == UnitKind.TABLE_ROW }
            .forEach { assertFalse(it.name, it.isTable) }
    }

    @Test
    fun `text box union covers both boxes`() {
        val a = TextBox(left = 0f, top = 20f, right = 10f, bottom = 10f)
        val b = TextBox(left = 5f, top = 25f, right = 30f, bottom = 12f)
        assertEquals(TextBox(0f, 25f, 30f, 10f), a.union(b))
        assertEquals(30f, a.union(b).width, 0f)
        assertEquals(15f, a.union(b).height, 0f)
    }
}
