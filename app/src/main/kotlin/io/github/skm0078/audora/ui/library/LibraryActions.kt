// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/LibraryActions.kt
// Purpose: Everything the library screen can ask for; the activity wires it to the controller and navigation.

package io.github.skm0078.audora.ui.library

import android.net.Uri
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookId

/** One interface instead of a dozen lambdas keeps [LibraryScreen]'s signature readable. */
@Suppress("TooManyFunctions") // One per control on the screen.
interface LibraryActions {
    fun onAddPdf(documentUri: Uri)

    fun onLocate(id: BookId, documentUri: Uri)

    fun onOpenBook(book: Book)

    /** "Continue" on the Continue reading card: opens the book and reads on from where it was left. */
    fun onContinue(book: Book)

    /** The mini player's book: opens it in the reader. */
    fun onOpenPlaying(id: BookId)

    fun onPlayPause()

    fun onRequestRemoval(book: Book)

    /** "Save as audiobook" in a book's menu: reads the whole book into audio files in the background (ADR-034). */
    fun onExportAudiobook(book: Book)

    fun onConfirmRemoval()

    fun onCancelRemoval()

    fun onMessageShown()

    fun onOpenSettings()

    fun onScreenResumed()
}
