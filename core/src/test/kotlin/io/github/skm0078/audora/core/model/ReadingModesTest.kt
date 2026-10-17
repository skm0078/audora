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
            sentenceRepeats = 3,
            afterSentence = AfterSentence.GAP,
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
            "sentenceRepeats=9;afterSentence=STOP" to ReadingModes(afterSentence = AfterSentence.STOP),
            "sentenceRepeats=0" to ReadingModes(),
            "sentenceRepeats=2" to ReadingModes(sentenceRepeats = 2),
        )
        for ((line, expected) in cases) assertEquals(line, expected, ReadingModes.decode(line))
    }

    @Test
    fun `studying means playback must stop at each sentence end`() {
        assertEquals(false, ReadingModes().studying)
        assertEquals(false, ReadingModes(afterSentence = AfterSentence.GAP).studying)
        assertEquals(true, ReadingModes(afterSentence = AfterSentence.STOP).studying)
        assertEquals(true, ReadingModes(sentenceRepeats = 2).studying)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `repeats outside the offered range are refused`() {
        ReadingModes(sentenceRepeats = ReadingModes.MAX_SENTENCE_REPEATS + 1)
    }
}
