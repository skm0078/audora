// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/ReaderDocument.kt
// Purpose: The reader's own read-only view of a book's PDF: page count, one page's sentences and its image.

package io.github.skm0078.audora.ui.reader

import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.map
import io.github.skm0078.audora.pdf.document.OutlineEntry
import io.github.skm0078.audora.pdf.document.PdfOpenError
import io.github.skm0078.audora.pdf.document.PdfPageError
import java.io.Closeable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the reader shows. The playback service opens the same PDF on its own and builds the same units (same
 * :pdf pipeline, same `charStart`s), so a tapped sentence's position matches the service's playlist item.
 */
interface ReaderDocument : Closeable {
    val pageCount: Int

    /** False for EPUB and text books (ADR-042): they have no page images, so the reader shows the text view. */
    val hasPageImages: Boolean get() = true

    suspend fun units(page: Int): Outcome<List<ReadingUnit>, PdfPageError>

    /** [page] drawn [widthPx] pixels wide (smaller if [maxPixels] would be exceeded), for the page view (P4-03). */
    suspend fun render(page: Int, widthPx: Int, maxPixels: Int): Outcome<PageImage, PdfPageError>

    /** The PDF's outline (bookmarks), for the chapter list; empty when it has none or it cannot be read (logged). */
    suspend fun outline(): List<OutlineEntry> = emptyList()

    /** [units] with where the page's words came from (P3-05); documents that cannot tell say the text layer. */
    suspend fun page(page: Int): Outcome<ReaderPage, PdfPageError> = units(page).map { ReaderPage(it, PageOrigin.TEXT) }

    /**
     * [page] without starting OCR: null for a scanned page not recognised yet. For whole-book work such as search,
     * which must not queue hundreds of pages of OCR in front of the page the listener is on.
     */
    suspend fun pageIfReady(page: Int): Outcome<ReaderPage?, PdfPageError> = page(page)

    /**
     * True while the book is reading a page with text recognition (OCR), its wait for another screen's OCR included:
     * the only time the slow-page notice may say OCR. Always false for a book that has no OCR (EPUB and text).
     */
    val recognizing: StateFlow<Boolean> get() = NEVER_RECOGNIZING

    /** Milliseconds this book has spent in OCR so far, for the slow-page log; 0 for a book that has no OCR. */
    val ocrSpentMs: Long get() = 0
}

private val NEVER_RECOGNIZING: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()

/** [ocrConfidence]: how sure OCR was of the page, 0–100, for a page read by OCR; else null (RI-9). */
data class ReaderPage(val units: List<ReadingUnit>, val origin: PageOrigin, val ocrConfidence: Int? = null)

fun interface ReaderDocumentOpener {
    /** A [PdfOpenError.PasswordRequired] failure means: ask for the password, call again. */
    suspend fun open(documentUri: String, password: String?, logContext: String): Outcome<ReaderDocument, PdfOpenError>
}
