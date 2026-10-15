// File: app/src/main/kotlin/io/github/skm0078/audora/ui/book/BookController.kt
// Purpose: Opens one library book: finds it, runs the PDF check on its file, and records that it was opened.

package io.github.skm0078.audora.ui.book

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.Library
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.ui.pdfcheck.PdfCheckController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Plain state holder for the book screen. P1-05 replaces the check with the reader. */
class BookController(
    context: Context,
    private val library: Library,
    private val scope: CoroutineScope,
    private val id: BookId,
) {
    sealed interface Lookup {
        data object Loading : Lookup

        data class Found(val book: Book) : Lookup

        /** Removed from the library meanwhile (or never there, after a restore from saved state). */
        data object NotInLibrary : Lookup
    }

    var lookup: Lookup by mutableStateOf(Lookup.Loading)
        private set

    val check = PdfCheckController(
        context = context,
        scope = scope,
        logContext = "book=${id.logLabel}",
        onOpened = ::recordOpened,
    )

    fun start() {
        scope.launch {
            val book = library.book(id)
            lookup = if (book == null) Lookup.NotInLibrary else Lookup.Found(book)
            if (book != null) check.open(Uri.parse(book.documentUri))
        }
    }

    private fun recordOpened(pageCount: Int) {
        scope.launch {
            val marked = library.markOpened(id, pageCount)
            if (marked is Outcome.Failure) {
                AudoraLog.w(
                    TAG,
                    "Could not record the open, book=${id.logLabel}: ${marked.error}",
                )
            }
        }
    }

    private companion object {
        const val TAG = "Audora.Book"
    }
}
