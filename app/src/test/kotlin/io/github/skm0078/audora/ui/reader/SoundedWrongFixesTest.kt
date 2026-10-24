// File: app/src/test/kotlin/io/github/skm0078/audora/ui/reader/SoundedWrongFixesTest.kt
// Purpose: "This sounded wrong" offers only fixes that would change something, and always "Read it again".

package io.github.skm0078.audora.ui.reader

import io.github.skm0078.audora.core.model.FootnoteMode
import io.github.skm0078.audora.core.model.Pace
import io.github.skm0078.audora.core.model.ReadingModes
import io.github.skm0078.audora.playback.report.SoundedWrongReason
import org.junit.Assert.assertEquals
import org.junit.Test

class SoundedWrongFixesTest {
    private val playing = PlaybackSnapshot(PlaybackSnapshot.Connection.CONNECTED)

    private fun offered(reason: SoundedWrongReason, playback: PlaybackSnapshot = playing) =
        SoundedWrongFixes.offered(reason, playback)

    @Test
    fun `a wrong word opens the word fix`() {
        assertEquals(listOf(SoundedWrongFix.FixWord, SoundedWrongFix.HearAgain), offered(SoundedWrongReason.WRONG_WORD))
    }

    @Test
    fun `junk offers to skip unreadable text only while that is off`() {
        val off = playing.copy(readingModes = ReadingModes(skipUnreadable = false))

        assertEquals(listOf(SoundedWrongFix.HearAgain), offered(SoundedWrongReason.JUNK_READ))
        assertEquals(
            listOf(SoundedWrongFix.SkipUnreadable, SoundedWrongFix.HearAgain),
            offered(SoundedWrongReason.JUNK_READ, off),
        )
    }

    @Test
    fun `a note flips footnotes and offers to drop bracketed notes until they are dropped`() {
        val skipping = playing.copy(readingModes = ReadingModes(footnotes = FootnoteMode.SKIP, skipBrackets = true))

        assertEquals(
            listOf(
                SoundedWrongFix.Footnotes(FootnoteMode.SKIP),
                SoundedWrongFix.SkipBrackets,
                SoundedWrongFix.HearAgain,
            ),
            offered(SoundedWrongReason.NOTE),
        )
        assertEquals(
            listOf(SoundedWrongFix.Footnotes(FootnoteMode.READ), SoundedWrongFix.HearAgain),
            offered(SoundedWrongReason.NOTE, skipping),
        )
    }

    @Test
    fun `a pause offers the other clause pause setting and the other sentence pauses`() {
        assertEquals(
            listOf(
                SoundedWrongFix.ClausePauses(false),
                SoundedWrongFix.SentencePause(Pace.TIGHT),
                SoundedWrongFix.SentencePause(Pace.RELAXED),
                SoundedWrongFix.HearAgain,
            ),
            offered(SoundedWrongReason.PAUSE),
        )
    }

    @Test
    fun `something else has nothing to change`() {
        assertEquals(listOf(SoundedWrongFix.HearAgain), offered(SoundedWrongReason.OTHER))
    }
}
