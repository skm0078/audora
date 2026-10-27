// File: playback/src/androidTest/kotlin/io/github/skm0078/audora/playback/export/AudiobookExportDeviceTest.kt
// Purpose: ADR-034 on a device: the platform AAC encoder writes playable .m4a chapters from a real PDF, tagged with
//          the book's title, author and cover that players show.

package io.github.skm0078.audora.playback.export

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.MediaExtractor
import android.media.MediaMetadataRetriever
import android.media.MediaFormat
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.skm0078.audora.core.config.AudiobookConfig
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.getOrNull
import io.github.skm0078.audora.pdf.document.PdfBook
import io.github.skm0078.audora.pdf.pdfium.PdfiumEngine
import io.github.skm0078.audora.playback.book.PdfBookText
import io.github.skm0078.audora.playback.queue.SentenceRenderer
import io.github.skm0078.audora.speech.audio.PcmAudio
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun aTaggedChapterShowsTheBookTitleAuthorTrackAndCoverAndStillPlays() {
        val cover = jpeg(COVER_SIDE * 2, COVER_SIDE * 3)
        val book = BookTags(title = "বইয়ের নাম", author = "लेखक", coverJpeg = cover)
        val sink = AacAudioSink(LocalChapterFiles(folder), chapterCount = 12, book = book)
        val chapter = AudioChapter(3, 20, 29)
        val writer = requireNotNull(sink.open(chapter, RATE).getOrNull())
        repeat(3) { assertTrue(writer.write(tone(RATE, HALF_SECOND_MS)) is Outcome.Success) }

        val exported = (writer.finish() as Outcome.Success).value

        val file = File(folder, exported.name)
        MediaMetadataRetriever().use { tags ->
            tags.setDataSource(file.path)
            assertEquals("বইয়ের নাম", tags.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM))
            assertEquals("03 Pages 21-30", tags.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE))
            assertEquals("लेखक", tags.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST))
            assertEquals("3/12", tags.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER))
            assertArrayEquals(cover, tags.embeddedPicture)
        }
        assertNear(exported.durationMs, trackFormat(file).getLong(MediaFormat.KEY_DURATION) / US_PER_MS)
    }

    @Test
    fun aBigCoverIsShrunkToAJpegOfTheConfiguredSide() {
        val webp = File(folder, "cover.webp")
        val big = Bitmap.createBitmap(COVER_SIDE * 3, COVER_SIDE * 4, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(200, 120, 40))
        }
        webp.outputStream().use { big.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, it) }
        big.recycle()

        val jpeg = requireNotNull(CoverJpeg.of(webp, AudiobookConfig(coverMaxSidePx = COVER_SIDE)))

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
        assertEquals("image/jpeg", bounds.outMimeType)
        assertEquals(COVER_SIDE, maxOf(bounds.outWidth, bounds.outHeight))
        assertNull(CoverJpeg.of(File(folder, "missing.webp")))
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

    private fun jpeg(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            bitmap.recycle()
            out.toByteArray()
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
        const val COVER_SIDE = 200
        const val JPEG_QUALITY = 80
    }
}
