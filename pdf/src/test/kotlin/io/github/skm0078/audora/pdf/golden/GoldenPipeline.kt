// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/golden/GoldenPipeline.kt
// Purpose: Runs the app's own :pdf pipeline (PdfBook → ReadingUnitBuilder) over dumped pages, as the player would.

package io.github.skm0078.audora.pdf.golden

import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.pdf.document.PdfBook
import io.github.skm0078.audora.pdf.document.PdfDocumentHandle
import io.github.skm0078.audora.pdf.document.PdfEngine
import io.github.skm0078.audora.pdf.document.PdfOpenError
import io.github.skm0078.audora.pdf.document.PdfPageError
import io.github.skm0078.audora.pdf.extract.RawPage
import io.github.skm0078.audora.pdf.units.ReadingUnitBuilder

/**
 * The one place the golden harness calls into :pdf. When a ticket adds a pipeline stage (layout in P2-02,
 * clutter removal in P2-03, recovery in P3), it wires the stage here exactly as the playback service does, so the
 * score always measures what the listener hears. Keep the signature: other threads rely on it.
 */
object GoldenPipeline {
    /** The text the player would speak for the whole document: every ReadingUnit, in order, space-separated. */
    fun spokenText(pages: List<RawPage>): String {
        val book = when (val opened = PdfBook.open(DumpEngine(pages), logContext = "golden")) {
            is Outcome.Success -> opened.value
            is Outcome.Failure -> error("golden document did not open: ${opened.error}")
        }
        val builder = ReadingUnitBuilder()
        return book.use {
            (0 until book.pageCount).joinToString(" ") { page ->
                when (val units = book.readingUnits(page, builder)) {
                    is Outcome.Success -> units.value.joinToString(" ") { it.text }
                    is Outcome.Failure -> error("golden page $page failed: ${units.error}")
                }
            }
        }
    }

    /** A [PdfEngine] over pages PDFium already read on the desktop (scripts/pdf/golden_corpus.py). */
    internal class DumpEngine(private val pages: List<RawPage>) : PdfEngine {
        override fun open(password: String?): Outcome<PdfDocumentHandle, PdfOpenError> = Outcome.Success(
            object : PdfDocumentHandle {
                override val pageCount = pages.size

                override fun readPage(pageIndex: Int): Outcome<RawPage, PdfPageError> =
                    pages.getOrNull(pageIndex)?.let { Outcome.Success(it) }
                        ?: Outcome.Failure(PdfPageError.PageOutOfRange(pageIndex, pages.size))

                override fun close() = Unit
            },
        )
    }
}
