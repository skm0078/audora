// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/document/PdfEngine.kt
// Purpose: The seam between pure extraction logic and the native PDF engine (PDFium on Android, fakes in tests).

package io.github.skm0078.audora.pdf.document

import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.pdf.extract.RawPage
import io.github.skm0078.audora.pdf.render.PageTransform
import java.io.Closeable

/** Opens one PDF. Each call opens it again from its source, which is how [PdfBook] bounds native memory. */
fun interface PdfEngine {
    fun open(password: String?): Outcome<PdfDocumentHandle, PdfOpenError>
}

/** An open document. Not thread-safe: [PdfBook] serializes access. */
interface PdfDocumentHandle : Closeable {
    val pageCount: Int

    fun readPage(pageIndex: Int): Outcome<RawPage, PdfPageError>

    /** The document's bookmarks, top level first; empty when it has none or they cannot be read. */
    fun outline(): List<OutlineEntry> = emptyList()

    /**
     * The page's printed label from the document's page labels ("iv", "A-3"), as the PDF spells it; null when it
     * has none or the engine cannot read labels. Loads no page: labels live in the document catalog.
     */
    fun pageLabel(pageIndex: Int): String? = null

    /**
     * Maps the page's boxes onto the page as displayed, exactly as the reader draws it: OCR'd words, found on the
     * drawn page, are mapped back through it to get page-space highlight boxes (ADR-032). Null when the engine
     * cannot tell (desktop page dumps in tests): OCR'd sentences then have no highlight.
     */
    fun pageTransform(pageIndex: Int): PageTransform? = null

    /**
     * The page as an 8-bit grey image for OCR (P3-05), at [dpi] or lower so it stays within [maxPixels]. Engines
     * that cannot draw (desktop page dumps in tests) keep this default.
     */
    fun render(pageIndex: Int, dpi: Int, maxPixels: Long): Outcome<PageImage, PdfPageError> =
        Outcome.Failure(PdfPageError.ExtractionFailed(pageIndex, "this engine cannot render pages"))
}

/**
 * One page drawn for OCR: [width] × [height] grey pixels, one byte each (0 black, 255 white), row by row with no
 * padding. [dpi] is the resolution it was actually drawn at, which Tesseract uses to judge text size.
 */
class PageImage(val pageIndex: Int, val width: Int, val height: Int, val dpi: Int, val gray: ByteArray) {
    init {
        require(width > 0 && height > 0) { "image must not be empty, was ${width}x$height" }
        require(gray.size == width * height) { "gray must hold width × height bytes" }
    }
}

/** Why a document could not be opened. */
sealed interface PdfOpenError {
    /** Encrypted with a user password and none was given: show the password prompt. */
    data object PasswordRequired : PdfOpenError

    /** A password was given and it is wrong: show the prompt again with an error. */
    data object IncorrectPassword : PdfOpenError

    /** Not a PDF, damaged, or using a security handler PDFium does not support. */
    data class Damaged(val detail: String) : PdfOpenError

    /** The file could not be read (moved, deleted, permission revoked). */
    data class Unreadable(val detail: String, val cause: Throwable? = null) : PdfOpenError

    /** The native libraries failed to load or lack a function; a broken build, not a broken PDF. */
    data class EngineUnavailable(val detail: String, val cause: Throwable? = null) : PdfOpenError
}

/** Why a page could not be read. */
sealed interface PdfPageError {
    data class PageOutOfRange(val pageIndex: Int, val pageCount: Int) : PdfPageError

    data class ExtractionFailed(val pageIndex: Int, val detail: String, val cause: Throwable? = null) : PdfPageError

    /** The periodic reopen failed, e.g. the file was deleted while the book was being read. */
    data class ReopenFailed(val error: PdfOpenError) : PdfPageError

    data object BookClosed : PdfPageError
}
