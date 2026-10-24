// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/book/BookOpener.kt
// Purpose: Turns a library book id (+ optional password) into an open BookText (PDF, EPUB or text), typed failure.

package io.github.skm0078.audora.playback.book

import android.content.Context
import io.github.skm0078.audora.core.config.CleanupConfig
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.DocumentFormat
import io.github.skm0078.audora.core.library.Library
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.pdf.document.PdfBook
import io.github.skm0078.audora.pdf.document.PdfOpenError
import io.github.skm0078.audora.pdf.pdfium.PdfiumEngine
import io.github.skm0078.audora.pdf.units.ReadingUnitBuilder
import io.github.skm0078.audora.playback.contract.PlaybackErrorCode
import io.github.skm0078.audora.playback.documents.SharedPdfCopies
import io.github.skm0078.audora.playback.ocr.BookOcr
import io.github.skm0078.audora.playback.text.TextBookError
import io.github.skm0078.audora.playback.text.TextBooks
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** An opened book: its library entry and its text, cleaned with [cleanup]. The caller closes [text]. */
class OpenedBook(val book: Book, val text: BookText, val cleanup: CleanupConfig = CleanupConfig())

/** Why a book could not be opened, as the contract's code plus a message that is safe to show and log. */
data class OpenFailure(val code: PlaybackErrorCode, val message: String)

/** Seam so the session can be tested with fake books. Main-safe. */
fun interface BookSource {
    suspend fun open(id: BookId, password: String?): Outcome<OpenedBook, OpenFailure>

    /**
     * False when the reading text settings changed since [opened] was opened: its sentence offsets may no longer
     * match the reader's, so the session opens the book again before playing it.
     */
    suspend fun isCurrent(opened: OpenedBook): Boolean = true
}

/**
 * Opens library books through the Storage Access Framework grant P1-01 keeps for each one, cleaned with the
 * user's reading text settings ([ReadingTextPreferences], the same ones the reader opens the book with).
 */
class LibraryBookSource(
    private val context: Context,
    private val library: Library,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val textPreferences: ReadingTextPreferences = ReadingTextPreferences.forContext(context),
    private val textBooks: TextBooks = TextBooks(context),
) : BookSource {
    override suspend fun open(id: BookId, password: String?): Outcome<OpenedBook, OpenFailure> {
        val book = library.book(id) ?: return Outcome.Failure(
            OpenFailure(PlaybackErrorCode.BOOK_NOT_FOUND, "This book is no longer in the library"),
        )
        val cleanup = withContext(io) { textPreferences.cleanupConfig() }
        // Text books (EPUB, Word, RTF, HTML, text: ADR-042, ADR-066) open beside PDFs; an unreadable head falls
        // through to PDFium, which says why.
        val format = withContext(io) { textBooks.formatOf(book.documentUri) }
        return if (format != null && format.isText) {
            openText(book, format, cleanup)
        } else {
            openPdf(book, password, cleanup)
        }
    }

    private suspend fun openPdf(
        book: Book,
        password: String?,
        cleanup: CleanupConfig,
    ): Outcome<OpenedBook, OpenFailure> {
        val id = book.id
        val engine = PdfiumEngine.forUri(context, SharedPdfCopies.readableUri(context, book.documentUri))
        val logContext = "book=${id.logLabel}"
        val ocr = BookOcr.factory(context, book.documentUri, logContext)
        val opened = withContext(io) { PdfBook.open(engine, password, logContext = logContext, ocr = ocr) }
        return when (opened) {
            is Outcome.Success -> {
                val marked = library.markOpened(id, opened.value.pageCount)
                if (marked is Outcome.Failure) {
                    AudoraLog.w(
                        TAG,
                        "Could not record the open, book=${id.logLabel}: ${marked.error}",
                    )
                }
                val builder = ReadingUnitBuilder(cleanupConfig = cleanup)
                Outcome.Success(OpenedBook(book, PdfBookText(opened.value, builder), cleanup))
            }

            is Outcome.Failure -> Outcome.Failure(failureOf(opened.error)).also {
                AudoraLog.w(TAG, "Could not open book=${id.logLabel}: ${opened.error}")
            }
        }
    }

    private suspend fun openText(
        book: Book,
        format: DocumentFormat,
        cleanup: CleanupConfig,
    ): Outcome<OpenedBook, OpenFailure> = when (
        val opened = withContext(io) {
            textBooks.open(book.documentUri, format)
        }
    ) {
        is Outcome.Success -> {
            val marked = library.markOpened(book.id, opened.value.pageCount)
            if (marked is Outcome.Failure) {
                AudoraLog.w(
                    TAG,
                    "Could not record the open, book=${book.id.logLabel}: ${marked.error}",
                )
            }
            Outcome.Success(OpenedBook(book, opened.value, cleanup))
        }

        is Outcome.Failure -> Outcome.Failure(OpenFailure(PlaybackErrorCode.BOOK_UNREADABLE, textFailure(opened.error)))
    }

    private fun textFailure(error: TextBookError): String = when (error) {
        is TextBookError.Damaged -> "This book looks damaged"
        is TextBookError.TooLarge -> "This book is too large to read"
        is TextBookError.Unreadable -> "The book was moved, deleted or is protected"
        TextBookError.NoText -> "This book has no text to read"
    }

    override suspend fun isCurrent(opened: OpenedBook): Boolean =
        withContext(io) { textPreferences.cleanupConfig() } == opened.cleanup

    private fun failureOf(error: PdfOpenError): OpenFailure = when (error) {
        PdfOpenError.PasswordRequired -> OpenFailure(PlaybackErrorCode.PASSWORD_REQUIRED, "This PDF needs its password")

        PdfOpenError.IncorrectPassword -> OpenFailure(PlaybackErrorCode.INCORRECT_PASSWORD, "Wrong password")

        is PdfOpenError.Damaged -> OpenFailure(PlaybackErrorCode.BOOK_UNREADABLE, "This PDF looks damaged")

        is PdfOpenError.Unreadable -> OpenFailure(PlaybackErrorCode.BOOK_UNREADABLE, "The PDF was moved or deleted")

        is PdfOpenError.EngineUnavailable -> OpenFailure(
            PlaybackErrorCode.BOOK_UNREADABLE,
            "The PDF reader failed to start",
        )
    }

    private companion object {
        const val TAG = "Playback.Books"
    }
}
