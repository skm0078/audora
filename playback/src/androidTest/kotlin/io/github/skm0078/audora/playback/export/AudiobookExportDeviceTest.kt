// File: playback/src/androidTest/kotlin/io/github/skm0078/audora/playback/export/AudiobookExportDeviceTest.kt
// Purpose: ADR-034 on a device: the platform AAC encoder writes playable .m4a chapters from a real PDF.

package io.github.skm0078.audora.playback.export

import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.getOrNull
import io.github.skm0078.audora.pdf.document.PdfBook
import io.github.skm0078.audora.pdf.pdfium.PdfiumEngine
import io.github.skm0078.audora.playback.book.PdfBookText
import io.github.skm0078.audora.playback.queue.SentenceRenderer
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
class AudiobookExportDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var folder: File

    @Before
    fun makeFolder() {
        folder = File(context.cacheDir, "audiobook-test").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    @After
    fun removeFolder() {
        folder.deleteRecursively()
    }

    @Test
    fun aWrittenChapterIsAacAtTheVoicesRateAndLength() {
        val chapter = AudioChapter(1, 0, 0)
        val writer = requireNotNull(AacChapterWriter.create(chapter, newFile("one.m4a"), RATE).getOrNull())
        repeat(3) { assertTrue(writer.write(tone(RATE, HALF_SECOND_MS)) is Outcome.Success) }

        val exported = (writer.finish() as Outcome.Success).value

        assertEquals(3 * HALF_SECOND_MS.toLong(), exported.durationMs)
        val format = trackFormat(File(folder, "one.m4a"))
        assertEquals(MediaFormat.MIMETYPE_AUDIO_AAC, format.getString(MediaFormat.KEY_MIME))
        assertEquals(RATE, format.getInteger(MediaFormat.KEY_SAMPLE_RATE))
        assertNear(exported.durationMs, format.getLong(MediaFormat.KEY_DURATION) / US_PER_MS)
    }

    @Test
    fun anAbortedChapterLeavesNoFile() {
        val file = File(folder, "partial.m4a")
        val writer =
            requireNotNull(AacChapterWriter.create(AudioChapter(1, 0, 0), newFile(file.name), RATE).getOrNull())
        writer.write(tone(RATE, HALF_SECOND_MS))

        writer.abort()

        assertFalse(file.exists())
    }

    @Test
    fun aRealPdfBecomesOnePlayableFilePerChapter() {
        val book = requireNotNull(PdfBook.open(PdfiumEngine({ descriptor() }), logContext = "test=export").getOrNull())
        PdfBookText(book).use { text ->
            val chapters = AudioChapters.plan(text.chapterStarts(), text.pageCount)
            // Alternate rates as Piper and Kokoro would, so the per-file resampling runs through the real encoder.
            var sentence = 0
            val renderer = SentenceRenderer {
                Outcome.Success(tone(if (sentence++ % 2 == 0) RATE else OTHER_RATE, SENTENCE_MS))
            }

            val result = AudiobookExporter(
                renderer,
            ).export(text, chapters, AacAudioSink(LocalChapterFiles(folder), chapters.size))

            val summary = (result as Outcome.Success).value
            assertTrue("nothing exported", summary.chapters.isNotEmpty())
            assertEquals(chapters.size, summary.chapters.size + summary.silentChapters)
            for (exported in summary.chapters) {
                val file = File(folder, exported.name)
                assertEquals("${exported.chapter.fileStem(chapters.size)}.m4a", file.name)
                assertNear(exported.durationMs, trackFormat(file).getLong(MediaFormat.KEY_DURATION) / US_PER_MS)
            }
        }
    }

    private fun newFile(name: String): ChapterFile = requireNotNull(LocalChapterFiles(folder).create(name).getOrNull())

    private fun trackFormat(file: File): MediaFormat {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.path)
            assertEquals(1, extractor.trackCount)
            return extractor.getTrackFormat(0)
        } finally {
            extractor.release()
        }
    }

    /** AAC adds encoder priming and pads the last frame, so lengths agree to within a few frames. */
    private fun assertNear(expectedMs: Long, actualMs: Long) {
        assertTrue("expected about $expectedMs ms, file has $actualMs ms", abs(expectedMs - actualMs) <= TOLERANCE_MS)
    }

    private fun tone(rate: Int, ms: Int): PcmAudio {
        val samples = ShortArray(rate * ms / MS_PER_S) { i ->
            (sin(2 * PI * TONE_HZ * i / rate) * AMPLITUDE).toInt().toShort()
        }
        return PcmAudio(samples, rate)
    }

    private fun descriptor(): ParcelFileDescriptor {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(instrumentation.targetContext.cacheDir, "test-pdfs/$PDF")
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            instrumentation.context.assets.open("pdfs/$PDF").use { input -> file.outputStream().use(input::copyTo) }
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    private companion object {
        const val PDF = "enhi-udhr-bilingual.pdf"
        const val RATE = 22_050
        const val OTHER_RATE = 24_000
        const val HALF_SECOND_MS = 500
        const val SENTENCE_MS = 50
        const val TONE_HZ = 440.0
        const val AMPLITUDE = 8_000.0
        const val MS_PER_S = 1_000
        const val US_PER_MS = 1_000L
        const val TOLERANCE_MS = 150L
    }
}
