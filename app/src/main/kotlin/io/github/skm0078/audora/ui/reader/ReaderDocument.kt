// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/ReaderDocument.kt
// Purpose: The reader's own read-only view of a book's PDF: page count, one page's sentences and its image.

package io.github.skm0078.audora.ui.reader

import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.pdf.document.PdfOpenError
import io.github.skm0078.audora.pdf.document.PdfPageError
import java.io.Closeable

/**
 * What the reader shows. The playback service opens the same PDF on its own and builds the same units (same
 * :pdf pipeline, same `charStart`s), so a tapped sentence's position matches the service's playlist item.
 */
interface ReaderDocument : Closeable {
    val pageCount: Int

    suspend fun units(page: Int): Outcome<List<ReadingUnit>, PdfPageError>

    /** [page] drawn [widthPx] pixels wide (smaller if [maxPixels] would be exceeded), for the page view (P4-03). */
    suspend fun render(page: Int, widthPx: Int, maxPixels: Int): Outcome<PageImage, PdfPageError>
}

fun interface ReaderDocumentOpener {
    /** A [PdfOpenError.PasswordRequired] failure means: ask for the password, call again. */
    suspend fun open(documentUri: String, password: String?, logContext: String): Outcome<ReaderDocument, PdfOpenError>
}
