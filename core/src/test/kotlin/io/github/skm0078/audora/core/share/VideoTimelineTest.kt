// File: core/src/test/kotlin/io/github/skm0078/audora/core/share/VideoTimelineTest.kt
// Purpose: A quote video's frames cover the reading up to the one-minute cap and light the word being said.

package io.github.skm0078.audora.core.share

import io.github.skm0078.audora.core.config.QuoteVideoConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoTimelineTest {
    private val config = QuoteVideoConfig()

    @Test
    fun `the frames cover the whole reading, the last one never short of it`() {
        val timeline = VideoTimeline(audioMs = 2_010, wordStartsMs = emptyList(), config = config)
        assertEquals(61, timeline.frameCount)
        assertEquals(2_000_000L, timeline.timeUs(60))
        assertFalse(timeline.isCut)
        assertEquals(30, VideoTimeline(1_000, emptyList(), config).frameCount)
    }

    @Test
    fun `a reading past a minute is cut there`() {
        val timeline = VideoTimeline(audioMs = 75_000, wordStartsMs = listOf(0), config = config)
        assertTrue(timeline.isCut)
        assertEquals(60_000L, timeline.durationMs)
        assertEquals(1_800, timeline.frameCount)
    }

    @Test
    fun `each frame lights the last word started by its time, none before the first`() {
        val timeline = VideoTimeline(audioMs = 2_000, wordStartsMs = listOf(200, 500, 500, 1_200), config = config)
        assertEquals(VideoTimeline.NO_WORD, timeline.wordAt(0))
        assertEquals(VideoTimeline.NO_WORD, timeline.wordAt(5)) // 166 ms
        assertEquals(0, timeline.wordAt(6)) // 200 ms: the word starts on this frame
        assertEquals(2, timeline.wordAt(15)) // 500 ms: of two words starting together, the later
        assertEquals(3, timeline.wordAt(timeline.frameCount - 1))
    }

    @Test
    fun `no word times light nothing`() {
        val timeline = VideoTimeline(audioMs = 1_000, wordStartsMs = emptyList(), config = config)
        assertEquals(VideoTimeline.NO_WORD, timeline.wordAt(29))
    }

    @Test
    fun `bad input is refused`() {
        assertThrows(IllegalArgumentException::class.java) { VideoTimeline(0, emptyList(), config) }
        assertThrows(IllegalArgumentException::class.java) { VideoTimeline(1_000, listOf(500, 100), config) }
        assertThrows(IllegalArgumentException::class.java) { VideoTimeline(1_000, emptyList(), config).timeUs(30) }
        assertThrows(IllegalArgumentException::class.java) { QuoteVideoConfig(widthPx = 721) }
    }
}
