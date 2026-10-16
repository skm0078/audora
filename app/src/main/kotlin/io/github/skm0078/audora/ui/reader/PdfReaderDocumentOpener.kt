// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/PdfReaderDocumentOpener.kt
// Purpose: Opens a book's PDF with :pdf (PDFium) for the reader; all native work runs off the main thread.

package io.github.skm0078.audora.ui.reader

import android.content.Context
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.map
import io.github.skm0078.audora.pdf.document.PdfBook
import io.github.skm0078.audora.pdf.document.PdfOpenError
import io.github.skm0078.audora.pdf.document.PdfPageError
import io.github.skm0078.audora.pdf.pdfium.PdfiumEngine
import io.github.skm0078.audora.pdf.pdfium.PdfiumPageRenderer
import io.github.skm0078.audora.pdf.pdfium.RenderedPage
import io.github.skm0078.audora.pdf.render.PageTransform
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
        when (val opened = PdfBook.open(engine, password, logContext = logContext)) {
            is Outcome.Success -> Outcome.Success(PdfReaderDocument(opened.value, engine, password, logContext, io))
            is Outcome.Failure -> opened
        }
    }
}

/**
 * The page renderer is a second PDFium document over the same file, opened on the first [render] so the text
 * view never pays for it. It cannot fail on a password: the book's own open already checked it.
 */
private class PdfReaderDocument(
    private val book: PdfBook,
    private val engine: PdfiumEngine,
    private val password: String?,
    private val logContext: String,
    private val io: CoroutineDispatcher,
) : ReaderDocument {
    private val builder = ReadingUnitBuilder()
    private var renderer: PdfiumPageRenderer? = null
    private var closed = false

    override val pageCount: Int get() = book.pageCount

    override suspend fun units(page: Int): Outcome<List<ReadingUnit>, PdfPageError> =
        withContext(io) { book.readingUnits(page, builder) }

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

    @Synchronized
    override fun close() {
        closed = true
        renderer?.close()
        renderer = null
        book.close()
    }
}

private class BitmapPageImage(page: RenderedPage) : PageImage {
    private val image = page.bitmap.asImageBitmap()
    override val transform: PageTransform = page.transform
    override val aspectRatio: Float = page.aspectRatio

    override fun imageBitmap(): ImageBitmap = image
}
