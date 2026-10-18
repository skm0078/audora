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
    fun `a textless unit outside a table still has no fingerprint`() {
        assertEquals(SentenceFingerprint.NONE, fingerprint.head(unit.copy(text = "")))
    }
}
