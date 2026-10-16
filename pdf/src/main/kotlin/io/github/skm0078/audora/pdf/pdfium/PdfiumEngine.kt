// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/pdfium/PdfiumEngine.kt
// Purpose: PdfEngine over PDFium: opens a PDF from a file descriptor and reads pages through our JNI library.

package io.github.skm0078.audora.pdf.pdfium

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.ParcelFileDescriptor
import io.github.skm0078.audora.core.config.ExtractionConfig
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.flatMap
import io.github.skm0078.audora.pdf.document.PageImage
import io.github.skm0078.audora.pdf.document.PdfDocumentHandle
import io.github.skm0078.audora.pdf.document.PdfEngine
import io.github.skm0078.audora.pdf.document.PdfOpenError
import io.github.skm0078.audora.pdf.document.PdfPageError
import io.github.skm0078.audora.pdf.document.RenderSize
import io.github.skm0078.audora.pdf.extract.RawPage
import io.legere.pdfiumandroid.api.PdfPasswordException
import io.legere.pdfiumandroid.core.unlocked.PdfDocumentU
import io.legere.pdfiumandroid.core.unlocked.PdfiumCoreU
import java.io.FileNotFoundException
import java.io.IOException

/**
 * @param openFile opens a fresh descriptor on every call; the document owns and closes it. Called again on
 *   every periodic reopen, so it must keep working for the book's lifetime.
 */
class PdfiumEngine(
    private val openFile: () -> ParcelFileDescriptor,
    private val config: ExtractionConfig = ExtractionConfig(),
) : PdfEngine {
    override fun open(password: String?): Outcome<PdfDocumentHandle, PdfOpenError> =
        PdfiumRuntime.core().flatMap { pdfium ->
            openDescriptor().flatMap { descriptor -> openDocument(pdfium, descriptor, password) }
        }

    private fun openDescriptor(): Outcome<ParcelFileDescriptor, PdfOpenError> = try {
        Outcome.Success(openFile())
    } catch (e: IOException) {
        Outcome.Failure(PdfOpenError.Unreadable(e.toString(), e))
    } catch (e: SecurityException) {
        Outcome.Failure(PdfOpenError.Unreadable(e.toString(), e))
    }

    // A missing or wrong password is an expected outcome, reported as a typed error rather than an exception.
    @Suppress("SwallowedException")
    private fun openDocument(
        pdfium: PdfiumCoreU,
        descriptor: ParcelFileDescriptor,
        password: String?,
    ): Outcome<PdfDocumentHandle, PdfOpenError> = try {
        val document = pdfiumLocked { pdfium.newDocument(descriptor, password) }
        Outcome.Success(PdfiumDocumentHandle(document, config))
    } catch (e: PdfPasswordException) {
        descriptor.close()
        Outcome.Failure(if (password == null) PdfOpenError.PasswordRequired else PdfOpenError.IncorrectPassword)
    } catch (e: IOException) {
        descriptor.close()
        Outcome.Failure(classify(e))
    }

    /** PdfiumAndroidKt reports FPDF_GetLastError as an IOException message. */
    private fun classify(e: IOException): PdfOpenError {
        val message = e.message.orEmpty()
        return if (message.contains(FILE_ERROR_MESSAGE, ignoreCase = true)) {
            PdfOpenError.Unreadable(message, e)
        } else {
            PdfOpenError.Damaged(message.ifEmpty { e.toString() })
        }
    }

    companion object {
        private const val FILE_ERROR_MESSAGE = "not found or could not be opened"

        /** An engine reading a document the app may open through [context]'s ContentResolver. */
        fun forUri(context: Context, uri: Uri, config: ExtractionConfig = ExtractionConfig()): PdfiumEngine {
            val resolver = context.applicationContext.contentResolver
            return PdfiumEngine(
                openFile = { resolver.openFileDescriptor(uri, "r") ?: throw FileNotFoundException("no descriptor") },
                config = config,
            )
        }
    }
}

private class PdfiumDocumentHandle(private val document: PdfDocumentU, private val config: ExtractionConfig) :
    PdfDocumentHandle {
    override val pageCount: Int = pdfiumLocked { document.getPageCount() }

    @Suppress("TooGenericExceptionCaught") // PdfiumAndroidKt and JNI report page failures as RuntimeExceptions.
    override fun readPage(pageIndex: Int): Outcome<RawPage, PdfPageError> = try {
        pdfiumLocked { readLocked(pageIndex) }
    } catch (e: RuntimeException) {
        Outcome.Failure(PdfPageError.ExtractionFailed(pageIndex, e.toString(), e))
    }

    private fun readLocked(pageIndex: Int): Outcome<RawPage, PdfPageError> {
        val page = document.openPage(pageIndex)
            ?: return Outcome.Failure(PdfPageError.ExtractionFailed(pageIndex, "PDFium could not load the page"))
        try {
            val textPage = page.openTextPage()
            try {
                return Outcome.Success(
                    PdfiumExtNative.readPage(pageIndex, page.pagePtr, textPage.pagePtr, config.maxFormDepth),
                )
            } finally {
                textPage.close()
            }
        } finally {
            page.close()
        }
    }

    /**
     * Draws under PDFium's lock, converts to grey outside it, so other books only wait for the drawing. The ARGB
     * bitmap (4 bytes per pixel, bounded by `maxPixels`) lives only until the grey copy is made.
     */
    @Suppress("TooGenericExceptionCaught") // PdfiumAndroidKt reports page failures as RuntimeExceptions.
    override fun render(pageIndex: Int, dpi: Int, maxPixels: Long): Outcome<PageImage, PdfPageError> = try {
        pdfiumLocked { drawLocked(pageIndex, dpi, maxPixels) }.flatMap { (bitmap, size) ->
            try {
                Outcome.Success(PageImage(pageIndex, size.width, size.height, size.dpi, grayOf(bitmap)))
            } finally {
                bitmap.recycle()
            }
        }
    } catch (e: RuntimeException) {
        Outcome.Failure(PdfPageError.ExtractionFailed(pageIndex, "render failed: $e", e))
    } catch (e: OutOfMemoryError) {
        Outcome.Failure(PdfPageError.ExtractionFailed(pageIndex, "no memory to render the page: $e", e))
    }

    /** Draws the page on white; the bitmap is recycled here if drawing throws, otherwise the caller owns it. */
    private fun drawLocked(
        pageIndex: Int,
        dpi: Int,
        maxPixels: Long,
    ): Outcome<Pair<Bitmap, RenderSize>, PdfPageError> {
        val page = document.openPage(pageIndex)
            ?: return Outcome.Failure(PdfPageError.ExtractionFailed(pageIndex, "PDFium could not load the page"))
        return try {
            val size = RenderSize.of(page.getPageWidthPoint(), page.getPageHeightPoint(), dpi, maxPixels)
            if (size == null) {
                Outcome.Failure(PdfPageError.ExtractionFailed(pageIndex, "the page has no size"))
            } else {
                val bitmap = drawnOrRecycled(Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)) {
                    page.renderPageBitmap(it, 0, 0, size.width, size.height, false, false, Color.WHITE, Color.WHITE)
                }
                Outcome.Success(bitmap to size)
            }
        } finally {
            page.close()
        }
    }

    /** [bitmap] after [draw], or recycled (and the error passed on) if [draw] throws. */
    private inline fun drawnOrRecycled(bitmap: Bitmap, draw: (Bitmap) -> Unit): Bitmap {
        var drawn = false
        try {
            draw(bitmap)
            drawn = true
        } finally {
            if (!drawn) bitmap.recycle()
        }
        return bitmap
    }

    /** ITU-R BT.601 luma, one row at a time so only one extra row of pixels is held. */
    private fun grayOf(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val gray = ByteArray(width * bitmap.height)
        val row = IntArray(width)
        for (y in 0 until bitmap.height) {
            bitmap.getPixels(row, 0, width, 0, y, width, 1)
            val base = y * width
            for (x in 0 until width) {
                val pixel = row[x]
                val luma = (LUMA_R * Color.red(pixel) + LUMA_G * Color.green(pixel) + LUMA_B * Color.blue(pixel)) /
                    LUMA_SCALE
                gray[base + x] = luma.toByte()
            }
        }
        return gray
    }

    override fun close() {
        pdfiumLocked { document.close() }
    }

    private companion object {
        const val LUMA_R = 299
        const val LUMA_G = 587
        const val LUMA_B = 114
        const val LUMA_SCALE = 1000
    }
}
