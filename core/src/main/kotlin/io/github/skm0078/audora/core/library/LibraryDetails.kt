// File: core/src/main/kotlin/io/github/skm0078/audora/core/library/LibraryDetails.kt
// Purpose: A book's details changed through the library (ADR-068): filled in from its file, edited by the listener,
//          or handed back to the file.

package io.github.skm0078.audora.core.library

import io.github.skm0078.audora.core.result.Outcome

/** Fills [id] in from what its file says ([BookDetails.filled]); the listener's edits stay. */
suspend fun Library.fill(id: BookId, file: FileDetails): Outcome<Book, LibraryError> =
    change(id) { book -> BookDetails.filled(book, file) }

/** The listener's edit of [id]'s title, author, language or cover ([BookDetails.edited]). */
suspend fun Library.edit(id: BookId, edit: BookEdit): Outcome<Book, LibraryError> =
    change(id) { book -> BookDetails.edited(book, edit) }

/** Hands every field back to the file: the next fill sets them again ([BookDetails.reset]). */
suspend fun Library.resetDetails(id: BookId): Outcome<Book, LibraryError> = change(id, BookDetails::reset)
