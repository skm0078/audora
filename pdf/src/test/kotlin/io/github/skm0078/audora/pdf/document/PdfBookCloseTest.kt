// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/document/PdfBookCloseTest.kt
// Purpose: A book never holds a caller up behind a long OCR read: close returns at once, waits can be interrupted.

package io.github.skm0078.audora.pdf.document

import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.errorOrNull
import io.github.skm0078.audora.core.result.getOrNull
import io.github.skm0078.audora.pdf.extract.RawPage
import io.github.skm0078.audora.pdf.extract.RawPageFixture
import io.github.skm0078.audora.pdf.ocr.OcrText
import io.github.skm0078.audora.pdf.quality.OcrError
import io.github.skm0078.audora.pdf.quality.PageOcr
import io.github.skm0078.audora.pdf.quality.PageOcrFactory
import io.github.skm0078.audora.pdf.render.PageTransform
import io.github.skm0078.audora.pdf.units.ReadingUnitBuilder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfBookCloseTest {
    /** Pages hold [texts] (an empty string is a scanned page); counts the documents left open. */
    private class Engine(val texts: List<String>) : PdfEngine {
        val openHandles = AtomicInteger()

        override fun open(password: String?): Outcome<PdfDocumentHandle, PdfOpenError> {
            openHandles.incrementAndGet()
            return Outcome.Success(
                object : PdfDocumentHandle {
                    override val pageCount = texts.size

                    override fun readPage(pageIndex: Int): Outcome<RawPage, PdfPageError> = Outcome.Success(
                        RawPageFixture.page(RawPageFixture.chars(texts[pageIndex]), pageIndex = pageIndex),
                    )

                    override fun render(pageIndex: Int, dpi: Int, maxPixels: Long) =
                        Outcome.Success(PageImage(pageIndex, 1, 1, dpi, ByteArray(1)))

                    override fun pageTransform(pageIndex: Int): PageTransform? = null

                    override fun close() {
                        openHandles.decrementAndGet()
                    }
                },
            )
        }
    }

    /** OCR that holds the page until [release] opens: a slow scanned page. */
    private class SlowOcr : PageOcr {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val reads = AtomicInteger()

        override fun recognize(pageIndex: Int, langs: Set<Lang>): Outcome<OcrText, OcrError> {
            reads.incrementAndGet()
            started.countDown()
            release.await()
            return Outcome.Success(OcrText.plain("The scanned page reads well."))
        }

        override fun detectLangs(pageIndex: Int): Outcome<Set<Lang>, OcrError> = Outcome.Success(setOf(Lang.EN))

        override fun cachedOnly(): PageOcr = PageOcr { _, _ -> Outcome.Failure(OcrError.Failed("not cached")) }
    }

    private val builder = ReadingUnitBuilder()
    private val threads = Executors.newCachedThreadPool()

    @After
    fun stopThreads() {
        threads.shutdownNow()
    }

    private fun open(engine: PdfEngine, ocr: PageOcr): PdfBook =
        requireNotNull(PdfBook.open(engine, ocr = PageOcrFactory { ocr }).getOrNull())

    private fun <T> Future<T>.result(): T = get(WAIT_SECONDS, TimeUnit.SECONDS)

    @Test
    fun `close returns at once while a scanned page is being read, and the document closes after it`() {
        val engine = Engine(listOf("", ""))
        val ocr = SlowOcr()
        val book = open(engine, ocr)
        val reading = threads.submit<Outcome<PageReading, PdfPageError>> { book.readPage(0, builder) }
        assertTrue(ocr.started.await(WAIT_SECONDS, TimeUnit.SECONDS))

        val started = System.nanoTime()
        book.close()
        val closeMs = (System.nanoTime() - started) / NANOS_PER_MS
        assertTrue("close took $closeMs ms", closeMs < MAX_CLOSE_MS)
        assertEquals("the page being read still holds the document", 1, engine.openHandles.get())

        ocr.release.countDown()
        reading.result()
        assertEquals("closed once the read let go", 0, engine.openHandles.get())
        assertEquals(PdfPageError.BookClosed, book.readPage(1, builder).errorOrNull())
        assertEquals(PdfPageError.BookClosed, book.outline().errorOrNull())
    }

    @Test
    fun `a caller waiting behind a slow page can be interrupted`() {
        val ocr = SlowOcr()
        val book = open(Engine(listOf("", "")), ocr)
        threads.submit { book.readPage(0, builder) }
        assertTrue(ocr.started.await(WAIT_SECONDS, TimeUnit.SECONDS))

        val waiter = threads.submit<Throwable?> {
            try {
                book.readPage(1, builder)
                null
            } catch (e: InterruptedException) {
                e
            }
        }
        Thread.sleep(SETTLE_MS) // Let it reach the book's lock.
        waiter.cancel(true)
        val stopped = threads.submit<Boolean> {
            runCatching { waiter.get() }
            true
        }
        assertTrue(stopped.result())
        ocr.release.countDown()
        // The book is still readable after an interrupted wait.
        assertTrue(book.readPage(1, builder) is Outcome.Success)
        assertEquals(2, ocr.reads.get())
    }

    @Test
    fun `a page read without OCR leaves scanned pages out and reads text pages as usual`() {
        val ocr = SlowOcr()
        val book = open(Engine(listOf(TEXT_PAGE, "")), ocr)
        assertNull(book.readPageIfReady(1, builder).getOrNull())
        val text = requireNotNull(book.readPageIfReady(0, builder).getOrNull())
        assertEquals(PageSource.TextLayer, text.source)
        assertEquals(book.readPage(0, builder).getOrNull()?.units, text.units)
        assertEquals("no OCR ran", 0, ocr.reads.get())
    }

    @Test
    fun `close from inside a read waits for that read to finish`() {
        val engine = Engine(listOf(""))
        lateinit var book: PdfBook
        val closingOcr = object : PageOcr {
            override fun recognize(pageIndex: Int, langs: Set<Lang>): Outcome<OcrText, OcrError> {
                book.close()
                assertEquals("still open for the read in progress", 1, engine.openHandles.get())
                return Outcome.Success(OcrText.plain("Read while closing."))
            }

            override fun detectLangs(pageIndex: Int): Outcome<Set<Lang>, OcrError> = Outcome.Success(setOf(Lang.EN))
        }
        book = open(engine, closingOcr)
        assertTrue(book.readPage(0, builder) is Outcome.Success)
        assertEquals(0, engine.openHandles.get())
    }

    private companion object {
        const val WAIT_SECONDS = 5L
        const val MAX_CLOSE_MS = 100L
        const val SETTLE_MS = 100L
        const val NANOS_PER_MS = 1_000_000L
        const val TEXT_PAGE =
            "This page has a proper text layer with more than enough letters to be trusted as it stands."
    }
}
