// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/ReaderPorts.kt
// Purpose: The reader's view of the library and of the saved speed, as small seams the tests can fake.

package io.github.skm0078.audora.ui.reader

import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.Library
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome

interface ReaderBooks {
    suspend fun find(id: BookId): Book?

    /** Records the page count and moves the book to the top of the library. Failures are logged, not shown. */
    suspend fun markOpened(id: BookId, pageCount: Int)
}

/** The last speed the listener picked, kept across app restarts. */
interface SpeedPreference {
    suspend fun load(): Float?

    suspend fun save(speed: Float)
}

class LibraryReaderBooks(private val library: Library) : ReaderBooks {
    override suspend fun find(id: BookId): Book? = library.book(id)

    override suspend fun markOpened(id: BookId, pageCount: Int) {
        val marked = library.markOpened(id, pageCount)
        if (marked is Outcome.Failure) {
            AudoraLog.w(
                TAG,
                "Could not record the open, book=${id.logLabel}: ${marked.error}",
            )
        }
    }

    private companion object {
        const val TAG = "Audora.Reader"
    }
}
