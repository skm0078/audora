// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/document/PdfBookOcrTest.kt
// Purpose: Pages routed to OCR are read by the book's OCR, in the right languages, with a text-layer fallback.

package io.github.skm0078.audora.pdf.document

import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.getOrNull
import io.github.skm0078.audora.pdf.extract.RawPage
import io.github.skm0078.audora.pdf.extract.RawPageFixture
import io.github.skm0078.audora.pdf.quality.OcrError
import io.github.skm0078.audora.pdf.quality.PageOcr
import io.github.skm0078.audora.pdf.quality.PageOcrFactory
import io.github.skm0078.audora.pdf.quality.PageRenderer
import io.github.skm0078.audora.pdf.quality.PageRoute
import io.github.skm0078.audora.pdf.quality.RouteReason
import io.github.skm0078.audora.pdf.units.ReadingUnitBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfBookOcrTest {
    /** Pages hold [texts]; an empty string is a scanned page (no text layer). */
    private class Engine(val texts: List<String>) : PdfEngine {
        val renders = ArrayList<Int>()

        override fun open(password: String?): Outcome<PdfDocumentHandle, PdfOpenError> = Outcome.Success(
            object : PdfDocumentHandle {
                override val pageCount = texts.size

                override fun readPage(pageIndex: Int): Outcome<RawPage, PdfPageError> = Outcome.Success(
                    RawPageFixture.page(RawPageFixture.chars(texts[pageIndex]), pageIndex = pageIndex),
                )

                override fun render(pageIndex: Int, dpi: Int, maxPixels: Long): Outcome<PageImage, PdfPageError> {
                    renders.add(pageIndex)
                    return Outcome.Success(PageImage(pageIndex, 1, 1, dpi, ByteArray(1)))
                }

                override fun close() = Unit
            },
        )
    }

    private class FakeOcr(val text: String, val detected: Set<Lang> = setOf(Lang.HI)) : PageOcr {
        val recognized = ArrayList<Pair<Int, Set<Lang>>>()
        val probes = ArrayList<Int>()
        var fail = false

        override fun recognize(pageIndex: Int, langs: Set<Lang>): Outcome<String, OcrError> {
            recognized.add(pageIndex to langs)
            return if (fail) Outcome.Failure(OcrError.Failed("broke")) else Outcome.Success(text)
        }

        override fun detectLangs(pageIndex: Int): Outcome<Set<Lang>, OcrError> {
            probes.add(pageIndex)
            return Outcome.Success(detected)
        }
    }

    private val builder = ReadingUnitBuilder()

    private fun open(engine: PdfEngine, ocr: PageOcr?): PdfBook {
        val factory = ocr?.let { fake -> PageOcrFactory { fake } }
        return requireNotNull(PdfBook.open(engine, ocr = factory).getOrNull())
    }

    @Test
    fun `a scanned page is read from OCR and says so`() {
        val ocr = FakeOcr("सभी मनुष्य स्वतंत्र पैदा होते हैं।")
        val reading = requireNotNull(open(Engine(listOf("")), ocr).readPage(0, builder).getOrNull())
        assertEquals(PageRoute.OCR, reading.routing.route)
        assertEquals(RouteReason.TOO_LITTLE_TEXT, reading.routing.reason)
        assertEquals(PageSource.Ocr(setOf(Lang.HI)), reading.source)
        assertEquals(listOf("सभी मनुष्य स्वतंत्र पैदा होते हैं।"), reading.units.map { it.text })
        assertEquals(listOf(0), ocr.probes)
    }

    @Test
    fun `without OCR the page reads its text layer and says why`() {
        val reading = requireNotNull(open(Engine(listOf("")), ocr = null).readPage(0, builder).getOrNull())
        assertEquals(PageSource.OcrUnavailable(OcrError.NoModels), reading.source)
        assertTrue(reading.units.isEmpty())
    }

    @Test
    fun `a failed OCR falls back to the text layer`() {
        val ocr = FakeOcr("unused").apply { fail = true }
        val reading = requireNotNull(open(Engine(listOf("Short.")), ocr).readPage(0, builder).getOrNull())
        assertEquals(PageSource.OcrUnavailable(OcrError.Failed("broke")), reading.source)
        assertEquals(listOf("Short."), reading.units.map { it.text })
    }

    @Test
    fun `a readable page teaches the book its language, so a later scan needs no probe`() {
        val ocr = FakeOcr("The scanned page reads well.")
        val book = open(Engine(listOf(ENGLISH_PAGE, "")), ocr)
        assertEquals(PageSource.TextLayer, requireNotNull(book.readPage(0, builder).getOrNull()).source)
        assertEquals(PageSource.Ocr(setOf(Lang.EN)), requireNotNull(book.readPage(1, builder).getOrNull()).source)
        assertEquals(listOf(1 to setOf(Lang.EN)), ocr.recognized)
        assertTrue(ocr.probes.isEmpty())
    }

    @Test
    fun `a scanned book probes once and then reuses the languages its OCR showed`() {
        val ocr = FakeOcr("সব মানুষ স্বাধীনভাবে জন্মগ্রহণ করে।", detected = setOf(Lang.BN))
        val book = open(Engine(listOf("", "", "")), ocr)
        repeat(3) { assertEquals(PageSource.Ocr(setOf(Lang.BN)), book.readPage(it, builder).getOrNull()?.source) }
        assertEquals(listOf(0), ocr.probes)
    }

    @Test
    fun `the book's renderer draws pages for its OCR`() {
        val engine = Engine(listOf("", ""))
        var renderer: PageRenderer? = null
        val book = requireNotNull(
            PdfBook.open(
                engine,
                ocr = PageOcrFactory { pages ->
                    renderer = pages
                    FakeOcr("x")
                },
            ).getOrNull(),
        )
        assertTrue(requireNotNull(renderer).render(1, 300, 1_000).getOrNull() != null)
        assertEquals(listOf(1), engine.renders)
        assertTrue(book.render(5, 300, 1_000) is Outcome.Failure)
    }

    private companion object {
        const val ENGLISH_PAGE =
            "This page has a proper text layer with more than enough letters to be trusted as it stands."
    }
}
