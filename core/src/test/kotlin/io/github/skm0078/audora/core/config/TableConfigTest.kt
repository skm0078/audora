// File: core/src/test/kotlin/io/github/skm0078/audora/core/config/TableConfigTest.kt
// Purpose: TableConfig rejects distances, shares and counts that cannot make a table grid.

package io.github.skm0078.audora.core.config

import org.junit.Assert.assertThrows
import org.junit.Test

class TableConfigTest {
    @Test
    fun `defaults are valid`() {
        TableConfig()
    }

    @Test
    fun `out of range values are rejected`() {
        val bad = listOf<() -> TableConfig>(
            { TableConfig(cellGapEm = 0f) },
            { TableConfig(edgeRowGapRatio = 0f) },
            { TableConfig(maxColumnCellEm = -1f) },
            { TableConfig(minColumnPairShare = 1.5f) },
            { TableConfig(denseColumnShare = -0.1f) },
            { TableConfig(rowStartShare = 2f) },
            { TableConfig(minFilledShare = 1.1f) },
            { TableConfig(maxSingleLetterRunShare = -1f) },
            { TableConfig(headerOverhangEm = -1f) },
            { TableConfig(wrapGapEm = -0.5f) },
            { TableConfig(maxHeaderRows = 0) },
            { TableConfig(maxRowsPerTable = 1) },
            { TableConfig(ruleSnapEm = -1f) },
            { TableConfig(minProseLines = 0) },
            { TableConfig(minProseWords = 1) },
            { TableConfig(maxRowGapEm = 0f) },
            { TableConfig(splitGapEm = 0f) },
            { TableConfig(splitGapEm = 1.5f) },
            { TableConfig(minSplitRows = 1) },
            { TableConfig(splitShare = 1.2f) },
        )
        for (make in bad) assertThrows(IllegalArgumentException::class.java) { make() }
    }
}
