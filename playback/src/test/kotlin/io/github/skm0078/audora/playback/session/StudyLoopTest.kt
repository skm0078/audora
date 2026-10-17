// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/session/StudyLoopTest.kt
// Purpose: Study modes: repeats, pause after each sentence, A–B loops, a selection read once, and late pages.

package io.github.skm0078.audora.playback.session

import io.github.skm0078.audora.core.model.AfterSentence
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingModes
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.playback.playlist.PlaybackItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyLoopTest {
    /** Page 2's sentences, ten characters each. */
    private val units = (0 until 5).map { unit(2, it) }
    private val plain = ReadingModes()

    private fun unit(page: Int, index: Int) = ReadingUnit(
        page = page,
        index = index,
        charStart = index * SENTENCE_CHARS,
        charEnd = (index + 1) * SENTENCE_CHARS,
        text = "Sentence $index.",
        lang = Lang.EN,
        pauseAfterMs = 0,
        bounds = null,
    )

    private fun item(index: Int): PlaybackItem = PlaybackItem.Sentence(units[index])

    @Test
    fun `with no study mode playback never stops at a sentence end`() {
        val study = StudyLoop()
        assertFalse(study.pausesAfter(item(0), item(1), plain))
        assertEquals(StudyStep.Continue, study.onSentenceEnd(units[0], item(1), plain))
    }

    @Test
    fun `each sentence is read as many times as asked, then reading goes on`() {
        val study = StudyLoop()
        val modes = ReadingModes(sentenceRepeats = 3)

        assertTrue(study.pausesAfter(item(0), item(1), modes))
        assertEquals(StudyStep.Repeat, study.onSentenceEnd(units[0], item(1), modes))
        assertEquals(StudyStep.Repeat, study.onSentenceEnd(units[0], item(1), modes))
        assertEquals(StudyStep.Continue, study.onSentenceEnd(units[0], item(1), modes))

        study.onNewSentence()
        assertEquals(
            "the next sentence starts its own count",
            StudyStep.Repeat,
            study.onSentenceEnd(units[1], item(2), modes),
        )
    }

    @Test
    fun `pause after each sentence stops after the last repeat`() {
        val study = StudyLoop()
        val modes = ReadingModes(sentenceRepeats = 2, afterSentence = AfterSentence.STOP)

        assertEquals(StudyStep.Repeat, study.onSentenceEnd(units[0], item(1), modes))
        assertEquals(StudyStep.Stop, study.onSentenceEnd(units[0], item(1), modes))
    }

    @Test
    fun `a gap alone needs no stop at the sentence end`() {
        assertFalse(StudyLoop().pausesAfter(item(0), item(1), ReadingModes(afterSentence = AfterSentence.GAP)))
    }

    @Test
    fun `an A-B loop goes back to A at the end of B`() {
        val study = StudyLoop()
        assertTrue(study.mark(StudyMark.START, units[1], loop = true))
        assertTrue(study.mark(StudyMark.END, units[3], loop = true))

        assertFalse("inside the range", study.pausesAfter(item(2), item(3), plain))
        assertTrue("B is the range's last sentence", study.pausesAfter(item(3), item(4), plain))
        assertEquals(StudyStep.LoopTo(Spot(2, 10)), study.onSentenceEnd(units[3], item(4), plain))
    }

    @Test
    fun `a selection read once stops at its end and is then done`() {
        val study = StudyLoop()
        study.set(StudyRange(Spot(2, 15), Spot(2, 29), loop = false))

        assertFalse("ends after the start, so not inside", units[0] in requireNotNull(study.range))
        assertTrue("the selection starts mid-sentence: that sentence is read", units[1] in requireNotNull(study.range))
        assertEquals(StudyStep.Stop, study.onSentenceEnd(units[2], item(3), plain))
        assertNull(study.range)
    }

    @Test
    fun `B before A swaps them, and B alone loops the sentence playing`() {
        val study = StudyLoop()
        study.mark(StudyMark.START, units[3], loop = true)
        study.mark(StudyMark.END, units[1], loop = true)
        assertEquals(StudyRange(Spot(2, 10), Spot(2, 30), loop = true), study.range)

        study.clear()
        study.mark(StudyMark.END, units[2], loop = true)
        assertEquals(StudyRange(Spot(2, 20), Spot(2, 20), loop = true), study.range)
    }

    @Test
    fun `a new A after B drops B`() {
        val study = StudyLoop()
        study.mark(StudyMark.START, units[0], loop = true)
        study.mark(StudyMark.END, units[1], loop = true)

        study.mark(StudyMark.START, units[3], loop = true)

        assertNull(study.range)
        assertEquals(Spot(2, 30), study.start)
    }

    @Test
    fun `a range end before the next page is known is caught when the next sentence starts`() {
        val study = StudyLoop()
        study.mark(StudyMark.START, units[3], loop = true)
        study.mark(StudyMark.END, units[4], loop = true)

        assertFalse("the next page is not loaded: unknown", study.pausesAfter(item(4), PlaybackItem.More(0), plain))
        assertEquals(StudyStep.LoopTo(Spot(2, 30)), study.onOvershoot(units[4], unit(3, 0)))
        assertNull("inside the range is no overshoot", study.onOvershoot(units[3], units[4]))
    }

    @Test
    fun `the book's last sentence ends a range`() {
        val study = StudyLoop()
        study.mark(StudyMark.START, units[4], loop = false)
        study.mark(StudyMark.END, units[4], loop = false)
        assertTrue(study.pausesAfter(item(4), null, plain))
    }

    @Test
    fun `nothing to mark without a sentence playing`() {
        assertFalse(StudyLoop().mark(StudyMark.START, null, loop = true))
    }

    private companion object {
        const val SENTENCE_CHARS = 10
    }
}
