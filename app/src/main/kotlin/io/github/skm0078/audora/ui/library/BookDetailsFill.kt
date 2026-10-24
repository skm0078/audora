// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/BookDetailsFill.kt
// Purpose: Fills each book in from its file once (title, author, language, format, its own cover; ADR-068): books
//          added before, and every book added later, one at a time in the background.

package io.github.skm0078.audora.ui.library

import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookDetails
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.Library
import io.github.skm0078.audora.core.library.fill
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.playback.details.FileDetailsRead
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch

/**
 * A book needs filling in while its [Book.detailsVersion] is below [BookDetails.VERSION]. A file that cannot be read
 * now (missing, access lost) is tried again at the next start, not in a loop. Runs on [scope], whose dispatcher should
 * allow one book at a time: on a low-end phone the library stays smooth while a hundred old books are filled in.
 *
 * @param read the file's details ([io.github.skm0078.audora.playback.details.FileDetailsReader.read]); blocking.
 * @param keepCover keeps the file's own cover picture as the book's ([BookCovers.keep]).
 * @param once runs before the first fill with the library as it is then (finding audiobooks saved earlier).
 */
class BookDetailsFill(
    private val library: Library,
    private val read: (documentUri: String) -> FileDetailsRead,
    private val keepCover: suspend (BookId, ByteArray) -> Boolean,
    private val once: suspend (List<Book>) -> Unit = {},
    private val startDelayMs: Long = DEFAULT_START_DELAY_MS,
) {
    private val started = AtomicBoolean(false)
    private val first = AtomicBoolean(true)
    private val tried: MutableSet<BookId> = ConcurrentHashMap.newKeySet()

    /** Once per process; later calls do nothing. */
    fun start(scope: CoroutineScope) {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            // The first screen is drawn first: filling in competes with it for the disk.
            delay(startDelayMs)
            library.books()
                .catch { e -> AudoraLog.w(TAG, "Books unreadable, nothing filled in this time", e) }
                .conflate()
                .collect { books ->
                    if (first.compareAndSet(true, false)) once(books)
                    books.filter(::needsFill).forEach { fill(it) }
                }
        }
    }

    private fun needsFill(book: Book) = book.detailsVersion < BookDetails.VERSION && book.id !in tried

    @Suppress("TooGenericExceptionCaught") // One bad file must not stop the others; logged with its kind.
    private suspend fun fill(book: Book) {
        tried += book.id
        try {
            val read = read(book.documentUri)
            if (read.details.format == null) {
                AudoraLog.i(TAG, "Not filled in now, file unreadable, book=${book.id.logLabel}")
                return
            }
            val kept = read.coverImage?.let { keepCover(book.id, it) } == true
            val details = if (read.coverImage != null &&
                !kept
            ) {
                read.details.copy(hasCoverImage = false)
            } else {
                read.details
            }
            when (val filled = library.fill(book.id, details)) {
                is Outcome.Success -> AudoraLog.i(
                    TAG,
                    "Filled in book=${book.id.logLabel} format=${details.format} author=${details.author != null} " +
                        "language=${filled.value.language} cover=${filled.value.cover::class.simpleName}",
                )

                is Outcome.Failure -> AudoraLog.w(TAG, "Not filled in, book=${book.id.logLabel}: ${filled.error}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: RuntimeException) {
            AudoraLog.w(TAG, "Filling in failed, book=${book.id.logLabel}: ${e.javaClass.simpleName}", e)
        }
    }

    private companion object {
        const val TAG = "Library.Fill"
        const val DEFAULT_START_DELAY_MS = 3_000L
    }
}
