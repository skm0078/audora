// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/document/PdfBookCarryTest.kt
// Purpose: What a page hands on to the next is kept only from a complete chain: long tables, scanned pages, jumps.

package io.github.skm0078.audora.pdf.document

import io.github.skm0078.audora.core.config.CleanupConfig
import io.github.skm0078.audora.core.config.PageCarryConfig
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.UnitKind
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.getOrNull
import io.github.skm0078.audora.pdf.extract.RawPage
import io.github.skm0078.audora.pdf.extract.RawPageFixture
import io.github.skm0078.audora.pdf.layout.PageSketch
import io.github.skm0078.audora.pdf.ocr.OcrText
import io.github.skm0078.audora.pdf.quality.OcrError
import io.github.skm0078.audora.pdf.quality.PageOcr
import io.github.skm0078.audora.pdf.quality.PageOcrFactory
import io.github.skm0078.audora.pdf.units.ReadingUnitBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfBookCarryTest {
    /** Each page drawn by [pages]; [failing] pages cannot be read. */
    private class Engine(val count: Int, val pages: (Int) -> RawPage) : PdfEngine {
        val failing = HashSet<Int>()

        override fun open(password: String?): Outcome<PdfDocumentHandle, PdfOpenError> = Outcome.Success(
            object : PdfDocumentHandle {
                override val pageCount = count

                override fun readPage(pageIndex: Int): Outcome<RawPage, PdfPageError> = if (pageIndex in failing) {
                    Outcome.Failure(PdfPageError.ExtractionFailed(pageIndex, "broken"))
                } else {
                    Outcome.Success(pages(pageIndex))
                }

                override fun render(pageIndex: Int, dpi: Int, maxPixels: Long): Outcome<PageImage, PdfPageError> =
                    Outcome.Success(PageImage(pageIndex, 1, 1, dpi, ByteArray(1)))

                override fun close() = Unit
            },
        )
    }

    /** A table of [ROWS] numbered rows a page, running over every page; the first page has its header row. */
    private fun tablePage(page: Int): RawPage {
        val sketch = PageSketch()
        val header = page == 0
        if (header) sketch.line("No.", NO, TOP).line("Name", NAME, TOP).line("Pay", PAY, TOP)
        repeat(ROWS) { r ->
            val serial = page * ROWS + r + 1
            val top = TOP - (r + 1) * PITCH
            sketch.line("$serial", NO, top).line("Asha$serial", NAME, top).line("${PAY_BASE + serial * 7}", PAY, top)
        }
        return sketch.raw(page)
    }

    /** OCR shared by every book object: what one recognises, another finds cached. */
    private class SharedOcr(val texts: Map<Int, String>) : PageOcr {
        val cached = HashSet<Int>()

        override fun recognize(pageIndex: Int, langs: Set<Lang>): Outcome<OcrText, OcrError> {
            cached.add(pageIndex)
            return Outcome.Success(OcrText.plain(texts.getValue(pageIndex)))
        }

        override fun detectLangs(pageIndex: Int): Outcome<Set<Lang>, OcrError> = Outcome.Success(setOf(Lang.EN))

        override fun cachedOnly(): PageOcr = object : PageOcr {
            override fun recognize(pageIndex: Int, langs: Set<Lang>): Outcome<OcrText, OcrError> =
                if (pageIndex in cached) {
                    Outcome.Success(OcrText.plain(texts.getValue(pageIndex)))
                } else {
                    Outcome.Failure(OcrError.Failed("not cached"))
                }

            override fun detectLangs(pageIndex: Int): Outcome<Set<Lang>, OcrError> = Outcome.Success(setOf(Lang.EN))
        }
    }

    private val builder = ReadingUnitBuilder(cleanupConfig = CleanupConfig(carry = PageCarryConfig(maxCarryPages = 2)))

    private fun open(engine: PdfEngine, ocr: PageOcr? = null): PdfBook =
        requireNotNull(PdfBook.open(engine, ocr = ocr?.let { fake -> PageOcrFactory { fake } }).getOrNull())

    private fun PdfBook.rowsBefore(page: Int): Int? =
        readPage(page, builder).getOrNull()?.units?.firstOrNull { it.kind == UnitKind.TABLE_ROW }?.table?.rowsBefore

    @Test
    fun `a jump past the carry limit is best effort, and the page reads right once the chain is read`() {
        val engine = Engine(5, ::tablePage)
        val inOrder = open(engine)
        val expected = (0 until 5).map { requireNotNull(inOrder.rowsBefore(it)) }
        assertEquals(expected.sorted().distinct(), expected)
        val jumped = open(engine)
        // Two pages back is as far as a jump looks: page 4 cannot see the rows of pages 0 and 1.
        assertTrue(jumped.rowsBefore(4) != expected[4])
        // What pages 2 and 3 handed on in that jump was not complete, so it is not reused.
        (0..2).forEach { jumped.readPage(it, builder) }
        assertEquals(expected[4], jumped.rowsBefore(4))
    }

    @Test
    fun `a page before that cannot be read hands on nothing and is not remembered`() {
        val engine = Engine(4, ::tablePage)
        val inOrder = open(engine).let { other -> (0..3).map { other.rowsBefore(it) } }
        val book = open(engine)
        engine.failing.add(1)
        assertEquals(0, book.rowsBefore(2))
        engine.failing.clear()
        book.readPage(0, builder)
        // Page 2 was read without its page before, so what it handed on then is not used for page 3.
        assertEquals(inOrder[3], book.rowsBefore(3))
    }

    @Test
    fun `a scanned page hands on what OCR has cached, and nothing until it has been read`() {
        val ocr = SharedOcr(mapOf(0 to "He turned and said that the", 1 to "rains came late that year."))
        val texts = listOf("", "rains came late that year.")
        val engine = Engine(2) { RawPageFixture.page(RawPageFixture.chars(texts[it]), pageIndex = it) }
        val first = open(engine, ocr)
        assertFalse(requireNotNull(first.readPage(1, builder).getOrNull()).continuesPreviousPage)
        assertFalse(0 in ocr.cached)
        first.readPage(0, builder)
        assertTrue(requireNotNull(first.readPage(1, builder).getOrNull()).continuesPreviousPage)
        // Another book object (the player beside the reader) finds the page in the OCR cache.
        assertTrue(requireNotNull(open(engine, ocr).readPage(1, builder).getOrNull()).continuesPreviousPage)
    }

    private companion object {
        const val ROWS = 4
        const val TOP = 700f
        const val PITCH = 14f
        const val NO = 50f
        const val NAME = 150f
        const val PAY = 300f
        const val PAY_BASE = 30000
    }
}
