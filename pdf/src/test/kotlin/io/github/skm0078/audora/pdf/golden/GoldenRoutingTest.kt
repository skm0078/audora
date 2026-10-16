// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/golden/GoldenRoutingTest.kt
// Purpose: P3-03's and P3-04's done-when on the golden corpus: Unicode Indic PDFs keep their text layer, mis-mapped
//          ones go to OCR, legacy-font ones go through the converter.

package io.github.skm0078.audora.pdf.golden

import io.github.skm0078.audora.core.config.LegacyEncoding
import io.github.skm0078.audora.core.config.QualityConfig
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.pdf.document.PdfBook
import io.github.skm0078.audora.pdf.extract.PageTextAssembler
import io.github.skm0078.audora.pdf.extract.RawPage
import io.github.skm0078.audora.pdf.quality.CharacterErrorRate
import io.github.skm0078.audora.pdf.quality.OcrError
import io.github.skm0078.audora.pdf.quality.PageOcr
import io.github.skm0078.audora.pdf.quality.PageRoute
import io.github.skm0078.audora.pdf.quality.PageRouting
import io.github.skm0078.audora.pdf.quality.RouteReason
import io.github.skm0078.audora.pdf.quality.TextLayerVerdict
import io.github.skm0078.audora.pdf.units.ReadingUnitBuilder
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every Indic document of the corpus is read through [PdfBook] as the player reads it, with a stand-in for OCR
 * (P3-05): the stretch of the hand-checked expected text that best matches the page. Unicode documents must keep
 * their text layer and pass the sample; broken-map documents (Office exports, fpdf2 without shaping data) must go to
 * OCR, both from the page scorer alone and, with the scorer's thresholds switched off, from the OCR sample alone.
 */
class GoldenRoutingTest {
    private val corpus = GoldenCorpus(File(System.getProperty("audora.golden.dir") ?: "golden"))

    private fun documents(vararg categories: String) = corpus.documents.filter { it.category in categories }

    private fun routes(document: GoldenDocument, config: QualityConfig): Pair<List<PageRouting>, PdfBook> {
        val pages = corpus.pages(document)
        val ocr = ExpectedTextOcr(pages, corpus.expectedText(document))
        val opened = PdfBook.open(GoldenPipeline.DumpEngine(pages), logContext = "golden=${document.id}", ocr = ocr)
        val book = (opened as Outcome.Success).value
        val builder = ReadingUnitBuilder(qualityConfig = config)
        val routes = book.use {
            (0 until book.pageCount).map { (book.readPage(it, builder) as Outcome.Success).value.routing }
        }
        println("${document.id}: verdict=${book.textLayerVerdict} sampleCer=${book.textLayerSampleCer}")
        return routes to book
    }

    @Test
    fun `Unicode Hindi, Bengali and bilingual documents keep their text layer and pass the OCR sample`() {
        val documents = documents("hi-unicode", "bn-unicode", "bilingual")
        assertTrue(documents.isNotEmpty())
        documents.forEach { document ->
            val (routes, book) = routes(document, QualityConfig())
            assertTrue("${document.id}: $routes", routes.all { it.route == PageRoute.TEXT_LAYER })
            assertEquals(document.id, TextLayerVerdict.TRUSTED, book.textLayerVerdict)
        }
    }

    @Test
    fun `mis-mapped documents go to OCR on the scorer alone`() {
        val documents = documents("hi-broken-map", "bn-broken-map")
        assertTrue(documents.isNotEmpty())
        documents.forEach { document ->
            val (routes, _) = routes(document, QualityConfig())
            assertTrue("${document.id}: $routes", routes.all { it.route == PageRoute.OCR })
        }
    }

    @Test
    fun `mis-mapped documents go to OCR on the OCR sample alone`() {
        val scorerOff = QualityConfig(
            maxUnicodeMapErrorRate = 1f,
            maxGarbageRate = 1f,
            maxInvalidIndicRate = 1f,
        )
        val documents = documents("hi-broken-map", "bn-broken-map").filter { it.id !in LETTERS_RIGHT_JUNK_ADDED }
        assertTrue(documents.isNotEmpty())
        documents.forEach { document ->
            val (routes, book) = routes(document, scorerOff)
            assertEquals(document.id, TextLayerVerdict.REJECTED, book.textLayerVerdict)
            assertTrue("${document.id}: $routes", routes.all { it.route == PageRoute.OCR })
        }
    }

    @Test
    fun `legacy-font documents are read through the converter, or go to OCR when it cannot read them`() {
        val documents = documents("hi-legacy", "bn-legacy")
        assertTrue(documents.isNotEmpty())
        documents.forEach { document ->
            val (routes, _) = routes(document, QualityConfig())
            assertTrue("${document.id}: $routes", routes.all { it.route == PageRoute.LEGACY_FONT })
            // The same fonts with no converter (P3-04 ADR-023: unknown glyphs route to OCR).
            val unconverted = QualityConfig().legacyFonts.mapValues { LegacyEncoding.UNSUPPORTED }
            val (unknown, _) = routes(document, QualityConfig(legacyFonts = unconverted))
            assertTrue("${document.id}: $unknown", unknown.all { it.reason == RouteReason.LEGACY_UNKNOWN_GLYPHS })
        }
    }

    private companion object {
        /**
         * fpdf2 (no shaping data) gets the letters right but glues junk into words ("घोषि0त", "সদস্যে$%র") and splits
         * words with stray spaces. The sample ignores spacing and symbols, so these stay under its 5 %; their
         * Unicode-map errors and junk route them to OCR in the scorer, which the test above checks.
         */
        val LETTERS_RIGHT_JUNK_ADDED = setOf("hi-udhr-fpdf2", "bn-udhr-fpdf2")
    }

    /**
     * A perfect OCR approximated from the expected text: the stretch of it closest to the page's raw PDFium text,
     * found by semi-global alignment (free start and end in the expected text). One line: the folded expected text
     * has no line breaks left. It skips headers and page numbers, which the cross-check does not count against the
     * layer anyway.
     */
    private class ExpectedTextOcr(private val pages: List<RawPage>, expected: String) : PageOcr {
        private val reference = CharacterErrorRate.fold(expected)

        override fun recognize(pageIndex: Int, langs: Set<Lang>): Outcome<String, OcrError> {
            val raw = CharacterErrorRate.fold(PageTextAssembler().assemble(pages[pageIndex]).text)
            return Outcome.Success(bestWindow(raw))
        }

        private fun bestWindow(page: String): String {
            val m = reference.length
            var previous = IntArray(m + 1)
            var previousStart = IntArray(m + 1) { it }
            var current = IntArray(m + 1)
            var currentStart = IntArray(m + 1)
            for (i in 1..page.length) {
                current[0] = i
                currentStart[0] = 0
                for (j in 1..m) {
                    val diagonal = previous[j - 1] + if (page[i - 1] == reference[j - 1]) 0 else 1
                    val up = previous[j] + 1
                    val left = current[j - 1] + 1
                    when {
                        diagonal <= up && diagonal <= left -> {
                            current[j] = diagonal
                            currentStart[j] = previousStart[j - 1]
                        }

                        up <= left -> {
                            current[j] = up
                            currentStart[j] = previousStart[j]
                        }

                        else -> {
                            current[j] = left
                            currentStart[j] = currentStart[j - 1]
                        }
                    }
                }
                previous = current.also { current = previous }
                previousStart = currentStart.also { currentStart = previousStart }
            }
            val end = (0..m).minBy { previous[it] }
            return reference.substring(previousStart[end], end)
        }
    }
}
