// File: pdf/src/test/kotlin/io/github/skm0078/audora/pdf/golden/GoldenPipeline.kt
// Purpose: Runs the app's own :pdf pipeline (PdfBook → ReadingUnitBuilder) over dumped pages, as the player would.

package io.github.skm0078.audora.pdf.golden

import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.model.UnitKind
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
 *
 * `:pdf` tags page elements instead of dropping them (ADR-035), so the score keeps the rules the expected texts were
 * written under (pdf/golden/README.md) by leaving out the kinds those rules skip: table interiors.
 */
object GoldenPipeline {
    /** Kinds the expected texts leave out. Captions, footnote text and equations are in them, so they are scored. */
    val UNSCORED_KINDS: Set<UnitKind> = setOf(UnitKind.TABLE_ROW)

    /** The text the player would speak for the whole document: every scored ReadingUnit, in order, space-separated. */
    fun spokenText(pages: List<RawPage>): String {
        val book = when (val opened = PdfBook.open(DumpEngine(pages), logContext = "golden")) {
            is Outcome.Success -> opened.value
            is Outcome.Failure -> error("golden document did not open: ${opened.error}")
        }
        val builder = ReadingUnitBuilder()
        return book.use {
            (0 until book.pageCount).joinToString(" ") { page ->
                when (val units = book.readingUnits(page, builder)) {
                    is Outcome.Success -> units.value.filter(::isScored).joinToString(" ") { it.text }
                    is Outcome.Failure -> error("golden page $page failed: ${units.error}")
                }
            }
        }
    }

    fun isScored(unit: ReadingUnit): Boolean = unit.kind !in UNSCORED_KINDS

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
