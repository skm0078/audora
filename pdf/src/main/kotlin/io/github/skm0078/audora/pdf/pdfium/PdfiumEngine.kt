// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/pdfium/PdfiumEngine.kt
// Purpose: PdfEngine over PDFium: opens a PDF from a file descriptor and reads pages through our JNI library.

package io.github.skm0078.audora.pdf.pdfium

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import io.github.skm0078.audora.core.config.ExtractionConfig
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.flatMap
import io.github.skm0078.audora.pdf.document.PdfDocumentHandle
import io.github.skm0078.audora.pdf.document.PdfEngine
import io.github.skm0078.audora.pdf.document.PdfOpenError
import io.github.skm0078.audora.pdf.document.PdfPageError
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

    override fun close() {
        pdfiumLocked { document.close() }
    }
}
