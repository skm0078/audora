// File: playback/src/androidTest/kotlin/io/github/skm0078/audora/playback/export/video/QuoteVideoWriterDeviceTest.kt
// Purpose: ADR 2026-10-28-quote-video on a device: the phone's encoder writes a playable 720 x 1280 MP4 with the
//          voice, each frame shows the picture for its moment, the "AI voice" label is on it, the cap cuts it, and
//          its progress counts every frame.

package io.github.skm0078.audora.playback.export.video

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.skm0078.audora.core.config.QuoteVideoConfig
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.share.VideoTimeline
import io.github.skm0078.audora.speech.audio.PcmAudio
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuoteVideoWriterDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var folder: File

    @Before
    fun makeFolder() {
        folder = File(context.cacheDir, "quote-video-test").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    @After
    fun removeFolder() {
        folder.deleteRecursively()
    }

    /** Red while the first word is lit, blue from the second (said at one second). */
    private val twoWords = object : QuoteVideoFrames {
        val timeline = VideoTimeline(TWO_SECONDS_MS, listOf(0, ONE_SECOND_MS), CONFIG)
        var draws = 0

        override fun lookOf(frame: Int) = timeline.wordAt(frame)

        override fun draw(canvas: Canvas, frame: Int) {
            draws++
            canvas.drawColor(if (timeline.wordAt(frame) == 0) Color.RED else Color.BLUE)
        }
    }

    @Test
    fun aVideoIsH264AndAacAtItsSizeAndLengthWithEveryFrame() {
        val file = File(folder, "quote.mp4")
        val started = System.nanoTime()
        val result = QuoteVideoWriter(CONFIG).write(file, tone(TWO_SECONDS_MS), twoWords.timeline, twoWords, LABEL)
        Log.i(TAG, "Two seconds of video written in ${(System.nanoTime() - started) / NS_PER_MS} ms")

        val video = (result as Outcome.Success).value
        assertFalse(video.isCut)
        assertEquals("only the two looks are drawn", 2, twoWords.draws)
        val tracks = tracksOf(file)
        val picture = tracks.getValue(MediaFormat.MIMETYPE_VIDEO_AVC)
        assertEquals(CONFIG.widthPx, picture.format.getInteger(MediaFormat.KEY_WIDTH))
        assertEquals(CONFIG.heightPx, picture.format.getInteger(MediaFormat.KEY_HEIGHT))
        assertEquals("every frame is in the file", twoWords.timeline.frameCount, picture.samples)
        val voice = tracks.getValue(MediaFormat.MIMETYPE_AUDIO_AAC)
        assertNear(TWO_SECONDS_MS, voice.format.getLong(MediaFormat.KEY_DURATION) / US_PER_MS)
        assertNear(TWO_SECONDS_MS, picture.format.getLong(MediaFormat.KEY_DURATION) / US_PER_MS)
    }

    @Test
    fun eachMomentShowsItsPictureWithTheAiVoiceLabel() {
        val file = File(folder, "frames.mp4")
        val result = QuoteVideoWriter(CONFIG).write(file, tone(TWO_SECONDS_MS), twoWords.timeline, twoWords, LABEL)
        assertTrue(result is Outcome.Success)

        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(file.path)
            val first = frameAt(retriever, HALF_SECOND_US)
            val second = frameAt(retriever, ONE_AND_A_HALF_SECONDS_US)
            assertTrue("red while the first word is said", isNear(first.middle(), Color.RED))
            assertTrue("blue once the second is", isNear(second.middle(), Color.BLUE))
            // The label's dark pill sits in the top left corner of every frame.
            assertTrue("the label is drawn", Color.red(first.labelPoint()) < LABEL_MAX_RED)
        }
    }

    @Test
    fun progressCountsEveryFrameUpToTheLast() {
        val reported = mutableListOf<Pair<Int, Int>>()
        val writer = QuoteVideoWriter(CONFIG) { done, total -> reported += done to total }

        val result = writer.write(
            File(folder, "progress.mp4"),
            tone(TWO_SECONDS_MS),
            twoWords.timeline,
            twoWords,
            LABEL,
        )

        assertTrue(result is Outcome.Success)
        val total = twoWords.timeline.frameCount
        assertEquals("once per frame", total, reported.size)
        assertTrue("always out of every frame", reported.all { it.second == total })
        assertEquals((1..total).toList(), reported.map { it.first })
    }

    @Test
    fun aReadingPastTheCapIsCutThere() {
        val capped = CONFIG.copy(maxDurationMs = ONE_SECOND_MS)
        val timeline = VideoTimeline(TWO_SECONDS_MS, listOf(0), capped)
        val frames = object : QuoteVideoFrames {
            override fun lookOf(frame: Int) = 0

            override fun draw(canvas: Canvas, frame: Int) = canvas.drawColor(Color.WHITE)
        }
        val file = File(folder, "cut.mp4")

        val result = QuoteVideoWriter(capped).write(file, tone(TWO_SECONDS_MS), timeline, frames, LABEL)

        val video = (result as Outcome.Success).value

        assertTrue(video.isCut)
        val voice = tracksOf(file).getValue(MediaFormat.MIMETYPE_AUDIO_AAC)
        assertNear(ONE_SECOND_MS, voice.format.getLong(MediaFormat.KEY_DURATION) / US_PER_MS)
    }

    private class Track(val format: MediaFormat, val samples: Int)

    private fun tracksOf(file: File): Map<String, Track> {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.path)
            return (0 until extractor.trackCount).associate { index ->
                val format = extractor.getTrackFormat(index)
                extractor.selectTrack(index)
                var samples = 0
                while (extractor.sampleTrackIndex >= 0) {
                    samples++
                    extractor.advance()
                }
                extractor.unselectTrack(index)
                extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                format.getString(MediaFormat.KEY_MIME).orEmpty() to Track(format, samples)
            }
        } finally {
            extractor.release()
        }
    }

    private fun frameAt(retriever: MediaMetadataRetriever, timeUs: Long): Bitmap {
        val frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
        return requireNotNull(frame) { "No frame at $timeUs" }
    }

    private fun Bitmap.middle(): Int = getPixel(width / 2, height / 2)

    /** Inside the label's pill, left of its text. */
    private fun Bitmap.labelPoint(): Int =
        getPixel((width * LABEL_POINT_SHARE_X).toInt(), (height * LABEL_POINT_SHARE_Y).toInt())

    private fun isNear(actual: Int, expected: Int): Boolean =
        abs(Color.red(actual) - Color.red(expected)) <= COLOUR_TOLERANCE &&
            abs(Color.green(actual) - Color.green(expected)) <= COLOUR_TOLERANCE &&
            abs(Color.blue(actual) - Color.blue(expected)) <= COLOUR_TOLERANCE

    private fun assertNear(expectedMs: Long, actualMs: Long) =
        assertTrue("expected about $expectedMs ms, was $actualMs", abs(expectedMs - actualMs) <= DURATION_TOLERANCE_MS)

    private fun tone(durationMs: Long): PcmAudio {
        val count = (RATE * durationMs / MS_PER_S).toInt()
        val samples = ShortArray(count) { (sin(2 * PI * TONE_HZ * it / RATE) * AMPLITUDE).toInt().toShort() }
        return PcmAudio(samples, RATE)
    }

    private companion object {
        const val TAG = "QuoteVideoTest"
        val CONFIG = QuoteVideoConfig()
        val LABEL = AiVoiceLabel("AI voice")
        const val RATE = 22_050
        const val TONE_HZ = 440.0
        const val AMPLITUDE = 8_000.0
        const val ONE_SECOND_MS = 1_000L
        const val TWO_SECONDS_MS = 2_000L
        const val HALF_SECOND_US = 500_000L
        const val ONE_AND_A_HALF_SECONDS_US = 1_500_000L
        const val MS_PER_S = 1_000L
        const val US_PER_MS = 1_000L
        const val NS_PER_MS = 1_000_000L
        const val DURATION_TOLERANCE_MS = 100L
        const val COLOUR_TOLERANCE = 48

        // Red behind the pill's 60 % black is about 100; a plain red frame is about 255.
        const val LABEL_MAX_RED = 160

        // On a 1280 px frame the pill starts 32 px in and its text 12 px further; its middle is about 59 px down.
        // (43, 59): inside the pill's rounded end, just left of the text.
        const val LABEL_POINT_SHARE_X = 0.06f
        const val LABEL_POINT_SHARE_Y = 0.046f
    }
}
