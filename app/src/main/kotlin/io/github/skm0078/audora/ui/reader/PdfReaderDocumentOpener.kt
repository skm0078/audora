// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/PdfReaderDocumentOpener.kt
// Purpose: Opens a book's PDF with :pdf (PDFium) for the reader; all native work runs off the main thread.

package io.github.skm0078.audora.ui.reader

import android.content.Context
import android.net.Uri
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.map
import io.github.skm0078.audora.pdf.document.PdfBook
import io.github.skm0078.audora.pdf.document.PdfOpenError
import io.github.skm0078.audora.pdf.document.PdfPageError
import io.github.skm0078.audora.pdf.document.PageSource
import io.github.skm0078.audora.pdf.pdfium.PdfiumEngine
import io.github.skm0078.audora.pdf.quality.OcrError
import io.github.skm0078.audora.playback.ocr.BookOcr
import io.github.skm0078.audora.pdf.units.ReadingUnitBuilder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** PDFium-backed; all native work runs on [io]. */
class PdfReaderDocumentOpener(private val context: Context, private val io: CoroutineDispatcher) :
    ReaderDocumentOpener {
    override suspend fun open(
        documentUri: String,
        password: String?,
        logContext: String,
    ): Outcome<ReaderDocument, PdfOpenError> = withContext(io) {
        val engine = PdfiumEngine.forUri(context, Uri.parse(documentUri))
        // Same OCR, models and text cache as the playback service, so a page is recognised once for both.
        val ocr = BookOcr.factory(context, documentUri, logContext)
        when (val opened = PdfBook.open(engine, password, logContext = logContext, ocr = ocr)) {
            is Outcome.Success -> Outcome.Success(PdfReaderDocument(opened.value, io))
            is Outcome.Failure -> opened
        }
    }
}

private class PdfReaderDocument(private val book: PdfBook, private val io: CoroutineDispatcher) : ReaderDocument {
    private val builder = ReadingUnitBuilder()

    override val pageCount: Int get() = book.pageCount

    override suspend fun units(page: Int): Outcome<List<ReadingUnit>, PdfPageError> =
        withContext(io) { book.readingUnits(page, builder) }

    override suspend fun page(page: Int): Outcome<ReaderPage, PdfPageError> =
        withContext(io) { book.readPage(page, builder) }.map { ReaderPage(it.units, originOf(it.source)) }

    override fun close() = book.close()
}

private fun originOf(source: PageSource): PageOrigin = when (source) {
    PageSource.TextLayer -> PageOrigin.TEXT

    is PageSource.Ocr -> PageOrigin.OCR

    is PageSource.OcrUnavailable -> when (source.error) {
        is OcrError.ModelMissing, OcrError.NoModels -> PageOrigin.NEEDS_OCR_MODELS
        is OcrError.Failed -> PageOrigin.OCR_FAILED
    }
}
