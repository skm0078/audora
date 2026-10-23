// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/report/SoundedWrongReportTest.kt
// Purpose: The "This sounded wrong" log line (RI-8b): every field, unknowns as "none", and never the sentence's text.

package io.github.skm0078.audora.playback.report

import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingModes
import io.github.skm0078.audora.core.model.TableMode
import io.github.skm0078.audora.core.model.UnitKind
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.playback.unit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SoundedWrongReportTest {
    private val book = BookId("0123456789abcdef0123")

    @Test
    fun `the line names where the sentence is, how it was judged and who read it`() {
        val unit = unit(4, 7, Lang.HI).copy(kind = UnitKind.FOOTNOTE, speakScore = 0.4567f)
        val report = SoundedWrongReport.of(book, unit, SoundedWrongReason.NOTE)
        val modes = ReadingModes(tables = TableMode.ROWS)
        val line = report.logLine(VoiceId(EngineId.SHERPA, "hi_IN-pratham-medium"), modes)
        assertEquals(
            "Sounded wrong, book=${book.logLabel} page=4 unit=7 kind=FOOTNOTE score=0.46 lang=HI engine=sherpa " +
                "voice=hi_IN-pratham-medium reason=NOTE modes=${modes.encode()}",
            line,
        )
        assertFalse("the text never reaches the log", line.contains(unit.text))
    }

    @Test
    fun `an unscored sentence and an unknown voice read as none`() {
        val report = SoundedWrongReport.of(book, unit(0, 0), SoundedWrongReason.PAUSE)
        val line = report.logLine(voice = null, ReadingModes())
        assertEquals(true, line.contains(" score=none "))
        assertEquals(true, line.contains(" engine=none voice=none reason=PAUSE "))
    }
}
