// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/ReaderDocument.kt
// Purpose: The reader's own read-only view of a book's PDF: page count and one page's sentences at a time.

package io.github.skm0078.audora.ui.reader

import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.map
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

    /** [units] with where the page's words came from (P3-05); documents that cannot tell say the text layer. */
    suspend fun page(page: Int): Outcome<ReaderPage, PdfPageError> = units(page).map { ReaderPage(it, PageOrigin.TEXT) }
}

data class ReaderPage(val units: List<ReadingUnit>, val origin: PageOrigin)

fun interface ReaderDocumentOpener {
    /** A [PdfOpenError.PasswordRequired] failure means: ask for the password, call again. */
    suspend fun open(documentUri: String, password: String?, logContext: String): Outcome<ReaderDocument, PdfOpenError>
}
