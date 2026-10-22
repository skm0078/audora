// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/cleanup/ColumnChannelsTest.kt
// Purpose: A column is split only where a gap wider than a word space runs down most of its rows (RI-4a).

package io.github.skm0078.audora.pdf.cleanup

import io.github.skm0078.audora.core.config.TableConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ColumnChannelsTest {
    private val channels = ColumnChannels(TableConfig())

    /** One row's words from "left-right" pairs, in points; the type is [EM] points. */
    private fun row(vararg words: Pair<Int, Int>) = words.map { (l, r) -> l.toFloat()..r.toFloat() }

    @Test
    fun `a gap down every row splits the column in the middle of the shared gap`() {
        val rows = listOf(row(0 to 5, 12 to 40), row(0 to 5, 13 to 30), row(0 to 6, 13 to 35), row(13 to 25))
        assertEquals(9f, requireNotNull(channels.find(rows, EM)), DELTA)
    }

    @Test
    fun `word spaces, too few rows or too many rows across the place keep the column whole`() {
        val cases = mapOf(
            "word spaces" to listOf(row(0 to 5, 8 to 30), row(0 to 5, 8 to 20), row(0 to 9, 12 to 30)),
            "two rows" to listOf(row(0 to 5, 12 to 40), row(0 to 5, 12 to 30), row(0 to 30)),
            "gaps that do not line up" to listOf(row(0 to 5, 12 to 40), row(0 to 20, 27 to 40), row(0 to 30, 37 to 40)),
            "rows across the gap" to
                listOf(row(0 to 5, 12 to 40), row(0 to 5, 12 to 30), row(0 to 5, 12 to 30), row(0 to 40), row(0 to 40)),
        )
        for ((name, rows) in cases) assertNull(name, channels.find(rows, EM))
    }

    private companion object {
        const val EM = 10f
        const val DELTA = 0.01f
    }
}
