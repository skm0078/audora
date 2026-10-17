// File: core/src/test/kotlin/io/github/skm0078/audora/core/model/ReadingModesTest.kt
// Purpose: The reading modes survive a round trip through their one-line form, and bad input means defaults.

package io.github.skm0078.audora.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingModesTest {
    @Test
    fun `every mode survives encode and decode`() {
        val modes = ReadingModes(
            skipUnreadable = false,
            tables = TableMode.ROWS,
            footnotes = FootnoteMode.SKIP,
            equations = EquationMode.AS_WRITTEN,
            captions = CaptionMode.SKIP,
            skipParentheses = true,
            skipBrackets = true,
            skipLinks = true,
            announcePages = true,
            skim = true,
        )
        assertEquals(modes, ReadingModes.decode(modes.encode()))
        assertEquals(ReadingModes(), ReadingModes.decode(ReadingModes().encode()))
    }

    @Test
    fun `missing, unknown and malformed values take their defaults`() {
        val cases = listOf(
            null to ReadingModes(),
            "" to ReadingModes(),
            "tables=ROWS" to ReadingModes(tables = TableMode.ROWS),
            "tables=LATER;footnotes=SKIP" to ReadingModes(footnotes = FootnoteMode.SKIP),
            "skipUnreadable=maybe;;=;captions" to ReadingModes(),
            " equations = SKIP ;newMode=1" to ReadingModes(equations = EquationMode.SKIP),
        )
        for ((line, expected) in cases) assertEquals(line, expected, ReadingModes.decode(line))
    }
}
