// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/LibraryActions.kt
// Purpose: Everything the library screen can ask for; the activity wires it to the controller and navigation.
//          The mini player and Settings live in the app shell now (ADR-036).

package io.github.skm0078.audora.ui.library

import android.net.Uri
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.LibraryQuery

/** One interface instead of a dozen lambdas keeps [LibraryScreen]'s signature readable. */
@Suppress("TooManyFunctions") // One per control on the screen.
interface LibraryActions {
    fun onAddPdf(documentUri: Uri)

    fun onLocate(id: BookId, documentUri: Uri)

    fun onOpenBook(book: Book)

    /** "Continue" on the Continue reading card: opens the book and reads on from where it was left. */
    fun onContinue(book: Book)

    fun onRequestRemoval(book: Book)

    /** "Save as audiobook" in a book's menu: reads the whole book into audio files in the background (ADR-034). */
    fun onExportAudiobook(book: Book)

    fun onConfirmRemoval()

    fun onCancelRemoval()

    /** Undo on the "Removed" snackbar, within its few seconds: the book comes back as it was (CF-3). */
    fun onUndoRemoval(id: BookId) = Unit

    fun onMessageShown()

    fun onScreenResumed()

    /** The search text, sort or filter changed (plan item 22). */
    fun onQuery(query: LibraryQuery) = Unit

    /** "Add to Up next" in a book's menu (plan item 25). */
    fun onAddToUpNext(book: Book) = Unit

    /** One of the [BookAction]s in a book's menu (studio mode and the exports, plan items 6, 69, 76). */
    fun onBookAction(book: Book, action: BookAction) = Unit

    /** One of the [LibraryAction]s in the library's ⋮ menu (point and read, plan item 62). */
    fun onLibraryAction(action: LibraryAction) = Unit

    /** The Jobs button in the top bar (ADR-067): every long job and download, with progress and time left. */
    fun onOpenJobs() = Unit

    /** Play on a saved audiobook or studio file in a book's details (ADR-068): the in-app player opens. */
    fun onPlayMade(setKey: String) = Unit
}
