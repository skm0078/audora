// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/pdfium/PdfiumPageRenderer.kt
// Purpose: Draws one PDF page into a bitmap with PDFium, plus the map from glyph boxes onto it (P4-03, ADR-024).

package io.github.skm0078.audora.pdf.pdfium

import android.graphics.Bitmap
import io.github.skm0078.audora.core.config.ExtractionConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.pdf.document.PdfOpenError
import io.github.skm0078.audora.pdf.document.PdfPageError
import io.github.skm0078.audora.pdf.render.PageTransform
import io.github.skm0078.audora.pdf.render.RenderSize
import io.legere.pdfiumandroid.core.unlocked.PdfDocumentU
import io.legere.pdfiumandroid.core.unlocked.PdfPageU
import java.io.Closeable

/**
 * A page drawn for display.
 *
 * @property transform maps the page's glyph boxes ([io.github.skm0078.audora.core.model.ReadingUnit.bounds])
 *   onto [bitmap], in fractions of its size.
 * @property aspectRatio width over height of the page as displayed (`/Rotate` applied).
 */
class RenderedPage(val bitmap: Bitmap, val transform: PageTransform, val aspectRatio: Float)

/**
 * Renders pages of its own open document, so drawing a page never waits on, or reopens, the extraction
 * document. Like [io.github.skm0078.audora.pdf.document.PdfBook] it reopens the document every
 * [ExtractionConfig.reopenEveryPages] pages to release PDFium's caches. Thread-safe: calls are serialized and
 * every PDFium call holds PdfiumAndroidKt's global lock.
 *
 * @param logContext identifies the book in logs (e.g. "book=3f2a"); never the title or the PDF's text.
 */
class PdfiumPageRenderer internal constructor(
    private var document: PdfDocumentU?,
    private val reopen: () -> Outcome<PdfDocumentU, PdfOpenError>,
    private val config: ExtractionConfig,
    private val logContext: String,
) : Closeable {
    private var rendersSinceOpen = 0

    /** [pageIndex] drawn [targetWidth] pixels wide, smaller if it would exceed [maxPixels]. */
    @Synchronized
    @Suppress("TooGenericExceptionCaught") // PdfiumAndroidKt and JNI report page failures as RuntimeExceptions.
    fun render(pageIndex: Int, targetWidth: Int, maxPixels: Int): Outcome<RenderedPage, PdfPageError> {
        val open = when (val current = currentDocument()) {
            is Outcome.Success -> current.value
            is Outcome.Failure -> return current
        }
        rendersSinceOpen++
        return try {
            pdfiumLocked { renderLocked(open, pageIndex, targetWidth, maxPixels) }
        } catch (e: RuntimeException) {
            AudoraLog.w(TAG, "Render failed, $logContext page=$pageIndex", e)
            Outcome.Failure(PdfPageError.ExtractionFailed(pageIndex, e.toString(), e))
        }
    }

    @Synchronized
    override fun close() {
        document?.let { pdfiumLocked { it.close() } }
        document = null
    }

    private fun renderLocked(
        open: PdfDocumentU,
        pageIndex: Int,
        targetWidth: Int,
        maxPixels: Int,
    ): Outcome<RenderedPage, PdfPageError> {
        val pageCount = open.getPageCount()
        val inRange = pageIndex in 0 until pageCount
        val page = if (inRange) open.openPage(pageIndex) else null
        if (page == null) {
            return Outcome.Failure(
                if (inRange) {
                    PdfPageError.ExtractionFailed(pageIndex, "PDFium could not load the page")
                } else {
                    PdfPageError.PageOutOfRange(pageIndex, pageCount)
                },
            )
        }
        return try {
            draw(page, pageIndex, targetWidth, maxPixels)
        } finally {
            page.close()
        }
    }

    private fun draw(
        page: PdfPageU,
        pageIndex: Int,
        targetWidth: Int,
        maxPixels: Int,
    ): Outcome<RenderedPage, PdfPageError> {
        // Width and height as displayed: PDFium swaps them for a page with /Rotate 90 or 270.
        val width = page.getPageWidthPoint().toFloat()
        val height = page.getPageHeightPoint().toFloat()
        // Null exactly when the page has no size.
        val transform = pageTransformOf(page)
            ?: return Outcome.Failure(PdfPageError.ExtractionFailed(pageIndex, "page size $width × $height"))
        val size = RenderSize.fit(width, height, targetWidth, maxPixels)
        val bitmap = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
        page.renderPageBitmap(bitmap, 0, 0, size.width, size.height, renderAnnot = true)
        return Outcome.Success(RenderedPage(bitmap, transform, width / height))
    }

    private fun currentDocument(): Outcome<PdfDocumentU, PdfPageError> {
        val open = document ?: return Outcome.Failure(PdfPageError.BookClosed)
        return if (rendersSinceOpen < config.reopenEveryPages) Outcome.Success(open) else reopenDocument(open)
    }

    private fun reopenDocument(open: PdfDocumentU): Outcome<PdfDocumentU, PdfPageError> {
        pdfiumLocked { open.close() }
        document = null
        return when (val reopened = reopen()) {
            is Outcome.Success -> {
                document = reopened.value
                rendersSinceOpen = 0
                AudoraLog.d(TAG, "Reopened the display document, $logContext")
                Outcome.Success(reopened.value)
            }

            is Outcome.Failure -> {
                AudoraLog.e(TAG, "Display reopen failed, $logContext error=${reopened.error}")
                Outcome.Failure(PdfPageError.ReopenFailed(reopened.error))
            }
        }
    }

    private companion object {
        const val TAG = "Audora.PageRender"
    }
}
