// File: core/src/main/kotlin/io/github/skm0078/audora/core/library/KeepOnPhone.kt
// Purpose: "Keep on this phone" for a book read through another app (Google Drive, OneDrive, Dropbox): ListenLock
//          makes its own copy and the book moves to it, keeping its place, notes and studio audio.

package io.github.skm0078.audora.core.library

import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome

/** How keeping a copy went; each has its own message. */
enum class KeepResult {
    KEPT,

    /** The book was already read from the phone's own storage: nothing to copy. */
    ALREADY_ON_PHONE,

    /** The other app gave no file (offline, signed out, not enough space, or larger than a copy may be). */
    NOT_COPIED,

    /** The copy was made but the book could not move to it (removed meanwhile, or the file changed); copy deleted. */
    NOT_MOVED,
}

/**
 * The copy is the same file, so the library moves the book to it ([Library.relocate]: same fingerprint) and gives the
 * other app's grant back; reading position, notes and studio audio are keyed by the book, not its URI, so they stay.
 * From then on the copy is the book's only file: removing the book deletes it, like any shared copy.
 * [copy] makes ListenLock's own copy and returns its URI (null when it could not); [discard] deletes a copy the book
 * did not take. Main-safe when [copy] is.
 */
class KeepOnPhone(
    private val library: Library,
    private val copy: suspend (String) -> String?,
    private val discard: (String) -> Unit,
) {
    suspend fun keep(book: Book): KeepResult {
        val source = BookSources.of(book.documentUri)
        if (source.isOnPhone) return KeepResult.ALREADY_ON_PHONE
        val copied = copy(book.documentUri)
        return if (copied == null) {
            AudoraLog.w(TAG, "No copy made, book=${book.id.logLabel} source=$source")
            KeepResult.NOT_COPIED
        } else {
            moveTo(book, copied, source)
        }
    }

    private suspend fun moveTo(book: Book, copied: String, source: BookSource): KeepResult =
        when (val moved = library.relocate(book.id, copied)) {
            is Outcome.Success -> {
                AudoraLog.i(TAG, "Book kept on the phone, book=${book.id.logLabel} source=$source")
                KeepResult.KEPT
            }

            is Outcome.Failure -> {
                // The book still points at the other app's file, so the copy is nobody's: never leave it behind.
                discard(copied)
                AudoraLog.w(TAG, "Copy made but not used, book=${book.id.logLabel}: ${moved.error}")
                KeepResult.NOT_MOVED
            }
        }

    private companion object {
        const val TAG = "Audora.KeepOnPhone"
    }
}
