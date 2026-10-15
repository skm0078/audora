// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/LibraryActions.kt
// Purpose: Everything the library screen can ask for; the activity wires it to the controller and navigation.

package io.github.skm0078.audora.ui.library

import android.net.Uri
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookId

/** One interface instead of nine lambdas keeps [LibraryScreen]'s signature readable. */
interface LibraryActions {
    fun onAddPdf(documentUri: Uri)

    fun onLocate(id: BookId, documentUri: Uri)

    fun onOpenBook(book: Book)

    fun onRequestRemoval(book: Book)

    fun onConfirmRemoval()

    fun onCancelRemoval()

    fun onMessageShown()

    fun onOpenSettings()

    fun onScreenResumed()
}
