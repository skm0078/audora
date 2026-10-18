// File: core/src/test/kotlin/io/github/skm0078/audora/core/resume/AnchorTextTest.kt
// Purpose: Units with text anchor on it; a textless table summary anchors on its table's shape instead.

package io.github.skm0078.audora.core.resume

import io.github.skm0078.audora.core.model.TableRowInfo
import io.github.skm0078.audora.core.model.UnitKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AnchorTextTest {
    private val fingerprint = SentenceFingerprint()
    private val unit = unitsOf(2, "Revenue grew.").single()
    private val info = TableRowInfo(0, null, 3, 2, listOf("Year", "Revenue"), emptyList(), continued = true)
    private val summary = unit.copy(charEnd = unit.charStart, text = "", kind = UnitKind.TABLE_SUMMARY, table = info)

    @Test
    fun `a unit with text anchors on its text`() {
        assertEquals("Revenue grew.", unit.anchorText)
        assertEquals("Revenue grew.", unit.copy(kind = UnitKind.TABLE_ROW, table = info.copy(row = 0)).anchorText)
    }

    @Test
    fun `a textless table summary gets a fingerprint, set by its table's shape`() {
        assertNotEquals(SentenceFingerprint.NONE, fingerprint.head(summary))
        assertEquals(fingerprint.head(summary), fingerprint.head(summary.copy(charStart = 99, charEnd = 99, index = 7)))
        assertNotEquals(fingerprint.head(summary), fingerprint.head(summary.copy(table = info.copy(rowsBefore = 4))))
        assertNotEquals(
            fingerprint.head(summary),
            fingerprint.head(summary.copy(table = info.copy(headers = listOf("A", "B")))),
        )
    }

    @Test
    fun `numbers that would run together once spaces are dropped still give different fingerprints`() {
        val oneThenTwentyThree = summary.copy(table = info.copy(rowsBefore = 1, rowCount = 23))
        val twelveThenThree = summary.copy(table = info.copy(rowsBefore = 12, rowCount = 3))
        assertNotEquals(fingerprint.head(oneThenTwentyThree), fingerprint.head(twelveThenThree))

        // Unlettered, both were "12": one column headed "2", and twelve headless columns.
        val oneColumnHeadedTwo = summary.copy(table = info.copy(columnCount = 1, headers = listOf("2")))
        val twelveHeadless = summary.copy(table = info.copy(columnCount = 12, headers = emptyList()))
        assertNotEquals(fingerprint.head(oneColumnHeadedTwo), fingerprint.head(twelveHeadless))
    }

    @Test
    fun `a headless table summary anchors on its shape alone`() {
        val headless = summary.copy(table = info.copy(headers = emptyList()))
        assertEquals("table r0 n3 c2 h ", headless.anchorText)
        assertNotEquals(SentenceFingerprint.NONE, fingerprint.head(headless))
    }

    @Test
    fun `a textless unit outside a table still has no fingerprint`() {
        assertEquals(SentenceFingerprint.NONE, fingerprint.head(unit.copy(text = "")))
    }
}
