// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/LibraryShelves.kt
// Purpose: The listener's collections as the big-tablet sidebar lists them, and opening the library on one
//          (ADR 2026-10-28-four-places).

package io.github.skm0078.audora.ui.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.skm0078.audora.core.library.BookCollection
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.ui.shell.Shelf
import io.github.skm0078.audora.ui.shell.SidebarShelves

/**
 * The collections with how many of the library's books each holds, in the library's own order (by name). A book
 * whose removal can still be undone is not counted: it is not on the shelf the listener sees.
 */
internal fun shelvesOf(
    collections: List<BookCollection>,
    members: Map<BookId, Set<String>>,
    books: Collection<BookId>,
): List<Shelf> {
    val counts = HashMap<String, Int>()
    books.forEach { book -> members[book]?.forEach { counts[it] = (counts[it] ?: 0) + 1 } }
    return collections.map { Shelf(it.id, it.name, counts[it.id] ?: 0) }
}

/** What the sidebar lists, worked out once per change of the library; opening one narrows the library to it. */
@Composable
fun sidebarShelves(library: LibraryController, onOpened: () -> Unit): SidebarShelves {
    val loaded = library.listState as? LibraryListState.Loaded ?: return SidebarShelves()
    val shelves = remember(loaded.collections, loaded.members, loaded.all) {
        shelvesOf(loaded.collections, loaded.members, loaded.all.map { it.book.id })
    }
    val query = loaded.query
    return SidebarShelves(shelves, query.narrow.collection) { id ->
        library.setQuery(query.copy(narrow = query.narrow.copy(collection = id)))
        onOpened()
    }
}
