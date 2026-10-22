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

    fun onMessageShown()

    fun onScreenResumed()

    /** The search text, sort or filter changed (plan item 22). */
    fun onQuery(query: LibraryQuery) = Unit

    /** "Add to Up next" in a book's menu (plan item 25). */
    fun onAddToUpNext(book: Book) = Unit
}
