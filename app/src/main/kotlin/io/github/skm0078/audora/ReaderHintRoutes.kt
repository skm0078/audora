// File: app/src/main/kotlin/io/github/skm0078/audora/ReaderHintRoutes.kt
// Purpose: Where the reader's first-use hints lead outside the reader (ADR 2026-10-30-first-use-hints), kept out of
//          MainActivity, which is at detekt's class size.

package io.github.skm0078.audora

import androidx.activity.ComponentActivity
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.BookSources
import io.github.skm0078.audora.ui.reader.ReaderHintLinks

/**
 * The library's tools (Quiz, Your listening) and, for a book read through Drive or another app, "Keep on this phone",
 * as the book's menu offers it.
 */
internal fun ComponentActivity.readerHintLinks(id: BookId, books: List<Book>, nav: AppNavigation): ReaderHintLinks {
    val cloudBook = books.firstOrNull { it.id == id }?.takeUnless { BookSources.of(it.documentUri).isOnPhone }
    return ReaderHintLinks(
        onLibraryAction = nav::openLibraryAction,
        onKeepOnPhone = cloudBook?.let { book -> { keepOnPhone(book) } },
    )
}
