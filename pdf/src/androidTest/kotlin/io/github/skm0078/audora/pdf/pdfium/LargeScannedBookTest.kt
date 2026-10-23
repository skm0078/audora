// File: pdf/src/androidTest/kotlin/io/github/skm0078/audora/pdf/pdfium/LargeScannedBookTest.kt
// Purpose: A 700-page scanned book opens without reading pages, never holds a closing caller, and stays memory-bounded.

package io.github.skm0078.audora.pdf.pdfium

import android.os.Debug
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.skm0078.audora.core.config.OcrConfig
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.errorOrNull
import io.github.skm0078.audora.core.result.getOrNull
import io.github.skm0078.audora.pdf.document.PageImage
import io.github.skm0078.audora.pdf.document.PageReading
import io.github.skm0078.audora.pdf.document.PageSource
import io.github.skm0078.audora.pdf.document.PdfBook
import io.github.skm0078.audora.pdf.document.PdfPageError
import io.github.skm0078.audora.pdf.ocr.OcrCache
import io.github.skm0078.audora.pdf.ocr.OcrEngine
import io.github.skm0078.audora.pdf.ocr.OcrText
import io.github.skm0078.audora.pdf.ocr.RecognizedLine
import io.github.skm0078.audora.pdf.ocr.RecognizedPage
import io.github.skm0078.audora.pdf.ocr.RecognizedWord
import io.github.skm0078.audora.pdf.ocr.RenderedPageOcr
import io.github.skm0078.audora.pdf.quality.OcrError
import io.github.skm0078.audora.pdf.quality.PageOcr
import io.github.skm0078.audora.pdf.quality.PageOcrFactory
import io.github.skm0078.audora.pdf.quality.PageRenderer
import io.github.skm0078.audora.pdf.units.ReadingUnitBuilder
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The hang a 700-page scanned book caused (main thread stuck closing a book whose page was in OCR, a minute at a
 * time) and its memory, on a real PDFium. OCR is a fake that draws the page for real at 300 dpi, so the test needs no
 * models and runs in minutes; TesseractOcrTest covers recognition itself.
 */
@RunWith(AndroidJUnit4::class)
class LargeScannedBookTest {
    private val builder = ReadingUnitBuilder()

    /** Draws the page at OCR resolution like the real OCR, then holds it until [release]. */
    private class HeldOcr(private val pages: PageRenderer) : PageOcr {
        val drawing = CountDownLatch(1)
        val release = CountDownLatch(1)

        override fun recognize(pageIndex: Int, langs: Set<Lang>): Outcome<OcrText, OcrError> {
            val drawn = pages.render(pageIndex, OcrConfig().renderDpi, OcrConfig().maxRenderPixels)
            drawing.countDown()
            release.await()
            return if (drawn is Outcome.Success) {
                Outcome.Success(OcrText.plain("Page ${pageIndex + 1} was read."))
            } else {
                Outcome.Failure(OcrError.Failed("not drawn"))
            }
        }

        override fun detectLangs(pageIndex: Int): Outcome<Set<Lang>, OcrError> = Outcome.Success(setOf(Lang.EN))
    }

    /** Reads one confident line per page, after the real render and grey conversion. */
    private class CountingEngine : OcrEngine {
        var pages = 0
        override val cacheTag = "large-book-test"

        override fun installedLangs() = setOf(Lang.EN)

        override fun recognize(image: PageImage, langs: Set<Lang>): Outcome<RecognizedPage, OcrError> {
            pages++
            val word = RecognizedWord("Page ${image.pageIndex + 1} was read.", 0, 0, image.width, 1, CONFIDENT)
            return Outcome.Success(RecognizedPage(listOf(RecognizedLine(listOf(word), 0)), image.width, image.height))
        }
    }

    @Test
    fun openingSevenHundredScannedPagesReadsNoPage() {
        val started = System.nanoTime()
        val opened = requireNotNull(open())
        val openMs = (System.nanoTime() - started) / NANOS_PER_MS
        opened.use { pdf ->
            Log.i(TAG, "open pages=${pdf.pageCount} ms=$openMs")
            assertEquals(PAGES, pdf.pageCount)
            assertEquals("no page is read at open", 0, pdf.pagesRead)
            assertTrue("open took $openMs ms", openMs < MAX_OPEN_MS)
        }
    }

    @Test
    fun closingNeverWaitsForAPageInOcr() {
        var held: HeldOcr? = null
        val factory = PageOcrFactory { pages -> HeldOcr(pages).also { held = it } }
        val pdf = requireNotNull(open(factory))
        val ocr = requireNotNull(held)
        val reader = Executors.newSingleThreadExecutor()
        try {
            val reading = reader.submit<Outcome<PageReading, PdfPageError>> { pdf.readPage(MIDDLE_PAGE, builder) }
            assertTrue("OCR started", ocr.drawing.await(WAIT_SECONDS, TimeUnit.SECONDS))

            val started = System.nanoTime()
            pdf.close() // What the reader screen does on the main thread when the listener leaves.
            val closeMs = (System.nanoTime() - started) / NANOS_PER_MS
            Log.i(TAG, "close while in OCR ms=$closeMs")
            assertTrue("close took $closeMs ms", closeMs < MAX_CLOSE_MS)

            ocr.release.countDown()
            reading.get(WAIT_SECONDS, TimeUnit.SECONDS)
            assertEquals(PdfPageError.BookClosed, pdf.readPage(MIDDLE_PAGE + 1, builder).errorOrNull())
        } finally {
            ocr.release.countDown()
            reader.shutdownNow()
        }
    }

    @Test
    fun jumpingThroughTheBookWithOcrStaysMemoryBounded() {
        val engine = CountingEngine()
        val cache = OcrCache(File(context.cacheDir, "large-book-ocr").apply { deleteRecursively() }, CACHE_BYTES)
        val factory = RenderedPageOcr.factory(engine, cache, "large-book", "test=scanned")
        val pdf = requireNotNull(open(factory))
        pdf.use {
            var nativeBaseline = 0L
            var javaBaseline = 0L
            val visited = (0 until PAGES step JUMP).toList()
            visited.forEachIndexed { i, page ->
                if (i == WARM_UP_PAGES) {
                    nativeBaseline = settledNativeHeap()
                    javaBaseline = settledJavaHeap()
                }
                val reading = requireNotNull(pdf.readPage(page, builder).getOrNull()) { "page $page failed" }
                assertTrue("page $page read by OCR", reading.source is PageSource.Ocr)
            }
            val nativeGrowthMb = (settledNativeHeap() - nativeBaseline) / BYTES_PER_MB
            val javaGrowthMb = (settledJavaHeap() - javaBaseline) / BYTES_PER_MB
            Log.i(
                TAG,
                "jumped pages=${visited.size} recognized=${engine.pages} opens=${pdf.opens} " +
                    "nativeGrowthMb=$nativeGrowthMb javaGrowthMb=$javaGrowthMb",
            )
            assertTrue("native heap grew $nativeGrowthMb MB", nativeGrowthMb <= MAX_NATIVE_GROWTH_MB)
            assertTrue("Java heap grew $javaGrowthMb MB", javaGrowthMb <= MAX_JAVA_GROWTH_MB)
        }
    }

    private fun open(ocr: PageOcrFactory? = null): PdfBook? =
        PdfBook.open(TestPdfs.engine(book), ocr = ocr, logContext = "test=scanned").getOrNull()

    private fun settledNativeHeap(): Long {
        settle()
        return Debug.getNativeHeapAllocatedSize()
    }

    private fun settledJavaHeap(): Long {
        settle()
        val runtime = Runtime.getRuntime()
        return runtime.totalMemory() - runtime.freeMemory()
    }

    private fun settle() {
        repeat(2) {
            Runtime.getRuntime().gc()
            System.runFinalization()
        }
    }

    companion object {
        private const val TAG = "AudoraMemoryTest"
        private const val PAGES = 700
        private const val MIDDLE_PAGE = 350
        private const val JUMP = 25
        private const val WARM_UP_PAGES = 3
        private const val CONFIDENT = 95
        private const val WAIT_SECONDS = 60L
        private const val MAX_OPEN_MS = 3_000L

        /** Far under Android's 5 s "isn't responding" limit: close must not wait at all. */
        private const val MAX_CLOSE_MS = 100L
        private const val MAX_NATIVE_GROWTH_MB = 48.0
        private const val MAX_JAVA_GROWTH_MB = 32.0
        private const val CACHE_BYTES = 4L shl 20
        private const val NANOS_PER_MS = 1_000_000L
        private const val BYTES_PER_MB = 1024.0 * 1024.0

        private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
        private lateinit var book: File

        @BeforeClass
        @JvmStatic
        fun writeBook() {
            book = File(context.cacheDir, "scanned-$PAGES-pages.pdf")
            if (!book.exists()) ScannedPdf.write(book, PAGES)
            Log.i(TAG, "scanned book pages=$PAGES bytes=${book.length()}")
        }

        @AfterClass
        @JvmStatic
        fun deleteBook() {
            book.delete()
        }
    }
}
