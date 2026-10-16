// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/session/SleepTimerTest.kt
// Purpose: The sleep timer counts minutes on a monotonic clock and pauses at the end of a page or chapter.

package io.github.skm0078.audora.playback.session

import io.github.skm0078.audora.playback.contract.SleepTimerMode
import io.github.skm0078.audora.playback.playlist.PlaybackItem
import io.github.skm0078.audora.playback.unit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepTimerTest {
    private var now = 1_000L
    private val timer = SleepTimer { now }

    @Test
    fun minutesCountDownToZero() {
        timer.set(SleepTimerMode.MINUTES, 15)
        assertEquals(15 * 60_000L, timer.remainingMs())
        now += 14 * 60_000L
        assertEquals(60_000L, timer.remainingMs())
        now += 5 * 60_000L
        assertEquals(0L, timer.remainingMs())
    }

    @Test
    fun clearingStopsEverything() {
        timer.set(SleepTimerMode.MINUTES, 1)
        timer.clear()
        assertEquals(SleepTimerMode.OFF, timer.mode)
        assertNull(timer.remainingMs())
    }

    @Test(expected = IllegalArgumentException::class)
    fun aMinutesTimerNeedsMinutes() {
        timer.set(SleepTimerMode.MINUTES, 0)
    }

    @Test
    fun endOfPagePausesAfterThePageLastSentenceOnly() {
        timer.set(SleepTimerMode.END_OF_PAGE, 0)
        val first = PlaybackItem.Sentence(unit(4, 0))
        val last = PlaybackItem.Sentence(unit(4, 1))
        val nextPage = PlaybackItem.Sentence(unit(5, 0))
        assertFalse(timer.pauseAfter(first, last))
        assertTrue(timer.pauseAfter(last, nextPage))
        assertTrue("next page not extracted yet", timer.pauseAfter(last, PlaybackItem.More(0)))
        assertTrue("end of the book", timer.pauseAfter(last, null))
        assertFalse("waiting on the placeholder is not a page end", timer.pauseAfter(PlaybackItem.More(0), nextPage))
    }

    @Test
    fun endOfChapterPausesBeforeThePageWhereTheNextChapterStarts() {
        timer.chapterStarts = listOf(0, 5, 9)
        timer.set(SleepTimerMode.END_OF_CHAPTER, 0)
        val lastOfPage3 = PlaybackItem.Sentence(unit(3, 2))
        val firstOfPage4 = PlaybackItem.Sentence(unit(4, 0))
        val lastOfPage4 = PlaybackItem.Sentence(unit(4, 1))
        assertFalse("page end inside a chapter", timer.pauseAfter(lastOfPage3, firstOfPage4))
        assertFalse(timer.pauseAfter(firstOfPage4, lastOfPage4))
        assertTrue(timer.pauseAfter(lastOfPage4, PlaybackItem.Sentence(unit(5, 0))))
        assertTrue("next page not extracted yet", timer.pauseAfter(lastOfPage4, PlaybackItem.More(0)))
        assertFalse("still extracting page 4", timer.pauseAfter(lastOfPage3, PlaybackItem.More(0)))
        assertTrue("end of the book", timer.pauseAfter(lastOfPage3, null))
    }

    @Test
    fun endOfChapterCountsAChapterStartOnASkippedBlankPage() {
        timer.chapterStarts = listOf(6)
        timer.set(SleepTimerMode.END_OF_CHAPTER, 0)
        // Page 6 has no sentences (a chapter title image), so page 5 is followed directly by page 7.
        assertTrue(timer.pauseAfter(PlaybackItem.Sentence(unit(5, 3)), PlaybackItem.Sentence(unit(7, 0))))
    }

    @Test
    fun otherModesNeverPauseAtItemEnds() {
        val last = PlaybackItem.Sentence(unit(4, 1))
        assertFalse(timer.pauseAfter(last, null))
        timer.set(SleepTimerMode.MINUTES, 5)
        assertFalse(timer.pauseAfter(last, null))
        timer.chapterStarts = listOf(5)
        assertFalse(timer.pauseAfter(last, PlaybackItem.Sentence(unit(5, 0))))
    }
}
