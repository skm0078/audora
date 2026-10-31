// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/PdfReaderDocumentOpener.kt
// Purpose: Opens a book for the reader off the main thread: PDFs with :pdf (PDFium), EPUB and text with TextBooks.

package io.github.skm0078.audora.ui.reader

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import io.github.skm0078.audora.core.library.DocumentFormat
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.map
import io.github.skm0078.audora.pdf.document.OutlineEntry
import io.github.skm0078.audora.pdf.document.PageReading
import io.github.skm0078.audora.pdf.document.PageSource
import io.github.skm0078.audora.pdf.document.PdfBook
import io.github.skm0078.audora.pdf.document.PdfOpenError
import io.github.skm0078.audora.pdf.document.PdfPageError
import io.github.skm0078.audora.pdf.pdfium.PdfiumEngine
import io.github.skm0078.audora.pdf.pdfium.PdfiumPageRenderer
import io.github.skm0078.audora.pdf.pdfium.RenderedPage
import io.github.skm0078.audora.pdf.quality.OcrError
import io.github.skm0078.audora.pdf.render.PageTransform
import io.github.skm0078.audora.pdf.units.ReadingUnitBuilder
import io.github.skm0078.audora.playback.book.ReadingTextPreferences
import io.github.skm0078.audora.playback.documents.SharedPdfCopies
import io.github.skm0078.audora.playback.ocr.BookOcr
import io.github.skm0078.audora.playback.text.TextBookError
import io.github.skm0078.audora.playback.text.TextBookText
import io.github.skm0078.audora.playback.text.TextBooks
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext

/**
 * PDFium-backed; all native work runs on [io]. Sentences are built with the user's reading text settings, read at
 * each open from the same [ReadingTextPreferences] as the playback service, so both agree on `charStart` offsets.
 */
class PdfReaderDocumentOpener(
    private val context: Context,
    private val io: CoroutineDispatcher,
    private val textPreferences: ReadingTextPreferences = ReadingTextPreferences.forContext(context),
    private val textBooks: TextBooks = TextBooks(context),
) : ReaderDocumentOpener {
    override suspend fun open(
        documentUri: String,
        password: String?,
        logContext: String,
    ): Outcome<ReaderDocument, PdfOpenError> = withContext(io) {
        // Text books (ADR-042, ADR-066): the same TextBooks the playback service opens them with.
        val format = textBooks.formatOf(documentUri)
        if (format != null && format.isText) {
            openText(documentUri, format)
        } else {
            openPdf(documentUri, password, logContext)
        }
    }

    private fun openText(documentUri: String, format: DocumentFormat): Outcome<ReaderDocument, PdfOpenError> =
        when (val opened = textBooks.open(documentUri, format)) {
            is Outcome.Success -> Outcome.Success(TextReaderDocument(opened.value))

            is Outcome.Failure -> Outcome.Failure(
                when (opened.error) {
                    is TextBookError.Unreadable -> PdfOpenError.Unreadable(opened.error.message)

                    is TextBookError.Locked -> PdfOpenError.Unreadable(
                        opened.error.message,
                        DrmLockedException(opened.error.message),
                    )

                    else -> PdfOpenError.Damaged(opened.error.message)
                },
            )
        }

    private fun openPdf(
        documentUri: String,
        password: String?,
        logContext: String,
    ): Outcome<ReaderDocument, PdfOpenError> {
        val engine = PdfiumEngine.forUri(context, SharedPdfCopies.readableUri(context, documentUri))
        // Same OCR, models and text cache as the playback service, so a page is recognised once for both; tracked so
        // the slow-page notice says OCR only while OCR really runs.
        val ocrInUse = OcrInUse()
        val ocr = ocrInUse.tracking(BookOcr.factory(context, documentUri, logContext))
        val builder = ReadingUnitBuilder(cleanupConfig = textPreferences.cleanupConfig())
        return when (val opened = PdfBook.open(engine, password, logContext = logContext, ocr = ocr)) {
            is Outcome.Success -> Outcome.Success(
                PdfReaderDocument(opened.value, engine, password, logContext, io, builder, ocrInUse),
            )

            is Outcome.Failure -> opened
        }
    }
}

/** An EPUB or text book: sentences straight from its pages, chapters from its contents, no page images. */
private class TextReaderDocument(private val text: TextBookText) : ReaderDocument {
    override val pageCount: Int get() = text.pageCount

    override val hasPageImages: Boolean get() = false

    override suspend fun units(page: Int): Outcome<List<ReadingUnit>, PdfPageError> = text.units(page)

    override suspend fun outline(): List<OutlineEntry> = text.contents.map { (title, page) ->
        OutlineEntry(title, page)
    }

    override suspend fun render(page: Int, widthPx: Int, maxPixels: Int): Outcome<PageImage, PdfPageError> =
        Outcome.Failure(PdfPageError.ExtractionFailed(page, "a text book has no page images"))

    override fun close() = text.close()
}

/**
 * The page renderer is a second PDFium document over the same file, opened on the first [render] so the text
 * view never pays for it. It cannot fail on a password: the book's own open already checked it.
 *
 * Page reads are interruptible: a scanned page can take a minute of OCR, and a listener who turns the page (the
 * reader cancels the old load) must not wait behind it. [close] never blocks the main thread: the book closes once
 * the page being read lets go, and the renderer closes on [io].
 */
// The book, what its renderer reopens (engine, password), its log name, thread, sentence builder and OCR tracker.
@Suppress("LongParameterList")
private class PdfReaderDocument(
    private val book: PdfBook,
    private val engine: PdfiumEngine,
    private val password: String?,
    private val logContext: String,
    private val io: CoroutineDispatcher,
    private val builder: ReadingUnitBuilder,
    private val ocrInUse: OcrInUse,
) : ReaderDocument {
    private var renderer: PdfiumPageRenderer? = null

    override val recognizing: StateFlow<Boolean> get() = ocrInUse.recognizing

    override val ocrSpentMs: Long get() = ocrInUse.spentMs

    @Volatile
    private var closed = false

    override val pageCount: Int get() = book.pageCount

    override suspend fun units(page: Int): Outcome<List<ReadingUnit>, PdfPageError> =
        runInterruptible(io) { book.readingUnits(page, builder) }

    override suspend fun page(page: Int): Outcome<ReaderPage, PdfPageError> =
        runInterruptible(io) { book.readPage(page, builder) }.map(::readerPage)

    override suspend fun pageIfReady(page: Int): Outcome<ReaderPage?, PdfPageError> =
        runInterruptible(io) { book.readPageIfReady(page, builder) }.map { it?.let(::readerPage) }

    private fun readerPage(reading: PageReading) =
        ReaderPage(reading.units, originOf(reading.source), reading.ocrConfidence)

    override suspend fun outline(): List<OutlineEntry> = withContext(io) {
        when (val outline = book.outline()) {
            is Outcome.Success -> outline.value

            is Outcome.Failure -> {
                AudoraLog.w(TAG, "Outline unavailable, $logContext: ${outline.error}")
                emptyList()
            }
        }
    }

    override suspend fun render(page: Int, widthPx: Int, maxPixels: Int): Outcome<PageImage, PdfPageError> =
        withContext(io) {
            when (val open = rendererOrOpen()) {
                is Outcome.Success -> open.value.render(page, widthPx, maxPixels).map(::BitmapPageImage)
                is Outcome.Failure -> open
            }
        }

    @Synchronized
    private fun rendererOrOpen(): Outcome<PdfiumPageRenderer, PdfPageError> {
        val open = renderer
        return when {
            closed -> Outcome.Failure(PdfPageError.BookClosed)

            open != null -> Outcome.Success(open)

            else -> when (val opened = engine.openRenderer(password, logContext)) {
                is Outcome.Success -> Outcome.Success(opened.value.also { renderer = it })
                is Outcome.Failure -> Outcome.Failure(PdfPageError.ReopenFailed(opened.error))
            }
        }
    }

    override fun close() {
        closed = true
        book.close()
        // A page may be drawing (a heavy scan takes seconds under PDFium's lock): the renderer waits for it on io.
        CoroutineScope(io).launch { closeRenderer() }
    }

    @Synchronized
    private fun closeRenderer() {
        renderer?.close()
        renderer = null
    }
}

private class BitmapPageImage(page: RenderedPage) : PageImage {
    private val image = page.bitmap.asImageBitmap()

    init {
        // Starts copying the page to the graphics chip now, on the render thread, so the first frame that shows it (a
        // page drawn ahead, at the start of a turn) does not stop to copy 8 to 10 MB (ADR 2026-10-29-speed-quick-wins).
        page.bitmap.prepareToDraw()
    }

    override val transform: PageTransform = page.transform
    override val aspectRatio: Float = page.aspectRatio

    override fun imageBitmap(): ImageBitmap = image
}

private fun originOf(source: PageSource): PageOrigin = when (source) {
    PageSource.TextLayer -> PageOrigin.TEXT

    is PageSource.Ocr -> PageOrigin.OCR

    is PageSource.OcrUnavailable -> when (source.error) {
        is OcrError.ModelMissing, OcrError.NoModels -> PageOrigin.NEEDS_OCR_MODELS
        is OcrError.Failed -> PageOrigin.OCR_FAILED
    }
}

private const val TAG = "Audora.ReaderDocument"

/**
 * Why a text book's [PdfOpenError.Unreadable] is a shop's DRM lock rather than a missing file: the reader says so
 * ([ReaderFailure.LOCKED]) without `:pdf` knowing about e-books (ADR 2026-10-26-more-ebook-formats).
 */
class DrmLockedException(message: String) : Exception(message)
