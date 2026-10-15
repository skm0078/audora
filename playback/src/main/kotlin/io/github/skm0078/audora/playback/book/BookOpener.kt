// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/book/BookOpener.kt
// Purpose: Turns a library book id (+ optional password) into an open BookText, with a typed failure.

package io.github.skm0078.audora.playback.book

import android.content.Context
import android.net.Uri
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.Library
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.pdf.document.PdfBook
import io.github.skm0078.audora.pdf.document.PdfOpenError
import io.github.skm0078.audora.pdf.pdfium.PdfiumEngine
import io.github.skm0078.audora.playback.contract.PlaybackErrorCode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** An opened book: its library entry and its text. The caller closes [text]. */
class OpenedBook(val book: Book, val text: BookText)

/** Why a book could not be opened, as the contract's code plus a message that is safe to show and log. */
data class OpenFailure(val code: PlaybackErrorCode, val message: String)

/** Seam so the session can be tested with fake books. Main-safe. */
fun interface BookSource {
    suspend fun open(id: BookId, password: String?): Outcome<OpenedBook, OpenFailure>
}

/** Opens library books through the Storage Access Framework grant P1-01 keeps for each one. */
class LibraryBookSource(
    private val context: Context,
    private val library: Library,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : BookSource {
    override suspend fun open(id: BookId, password: String?): Outcome<OpenedBook, OpenFailure> {
        val book = library.book(id) ?: return Outcome.Failure(
            OpenFailure(PlaybackErrorCode.BOOK_NOT_FOUND, "This book is no longer in the library"),
        )
        val engine = PdfiumEngine.forUri(context, Uri.parse(book.documentUri))
        val opened = withContext(io) { PdfBook.open(engine, password, logContext = "book=${id.logLabel}") }
        return when (opened) {
            is Outcome.Success -> {
                val marked = library.markOpened(id, opened.value.pageCount)
                if (marked is Outcome.Failure) {
                    AudoraLog.w(
                        TAG,
                        "Could not record the open, book=${id.logLabel}: ${marked.error}",
                    )
                }
                Outcome.Success(OpenedBook(book, PdfBookText(opened.value)))
            }

            is Outcome.Failure -> Outcome.Failure(failureOf(opened.error)).also {
                AudoraLog.w(TAG, "Could not open book=${id.logLabel}: ${opened.error}")
            }
        }
    }

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
