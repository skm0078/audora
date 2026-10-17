// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/golden/GoldenPipelineTest.kt
// Purpose: The golden harness scores every unit kind the expected texts read, and leaves out table interiors.

package io.github.skm0078.audora.pdf.golden

import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.model.TableRowInfo
import io.github.skm0078.audora.core.model.UnitKind
import org.junit.Assert.assertEquals
import org.junit.Test

class GoldenPipelineTest {
    private fun unit(kind: UnitKind): ReadingUnit {
        val table = if (kind.isTable) {
            TableRowInfo(
                0,
                if (kind ==
                    UnitKind.TABLE_ROW
                ) {
                    0
                } else {
                    null
                },
                1,
                1,
                emptyList(),
                emptyList(),
            )
        } else {
            null
        }
        val noteRef = if (kind == UnitKind.FOOTNOTE) 1 else null
        return ReadingUnit(0, 0, 0, 1, "x", Lang.EN, 0, null, kind, table = table, noteRef = noteRef)
    }

    @Test
    fun `only table rows are left out of the score`() {
        val scored = UnitKind.entries.filter { GoldenPipeline.isScored(unit(it)) }
        assertEquals(UnitKind.entries - UnitKind.TABLE_ROW, scored)
    }
}
