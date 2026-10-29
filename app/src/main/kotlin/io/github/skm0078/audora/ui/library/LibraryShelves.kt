// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/LibraryShelves.kt
// Purpose: The listener's shelves and collections as the big-tablet sidebar and the phone drawer list them, and
//          opening the library on one (ADR 2026-10-28-four-places, ADR 2026-10-28-phone-drawer).

package io.github.skm0078.audora.ui.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.library.BookCollection
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.LibraryFilter
import io.github.skm0078.audora.core.library.ShelfLook
import io.github.skm0078.audora.ui.shell.DrawerShelf
import io.github.skm0078.audora.ui.shell.Shelf
import io.github.skm0078.audora.ui.shell.SidebarShelves

/**
 * The collections with how many of the library's books each holds, in the library's own order (by name). A book
 * whose removal can still be undone is not counted: it is not on the shelf the listener sees. Each wears its [looks].
 */
internal fun shelvesOf(
    collections: List<BookCollection>,
    members: Map<BookId, Set<String>>,
    books: Collection<BookId>,
    looks: Map<String, ShelfLook> = emptyMap(),
): List<Shelf> {
    val counts = HashMap<String, Int>()
    books.forEach { book -> members[book]?.forEach { counts[it] = (counts[it] ?: 0) + 1 } }
    return collections.map { Shelf(it.id, it.name, counts[it.id] ?: 0, looks[it.id] ?: ShelfLook.DEFAULT) }
}

/** What the sidebar lists, worked out once per change of the library; opening one narrows the library to it. */
@Composable
fun sidebarShelves(library: LibraryController, onOpened: () -> Unit): SidebarShelves {
    val looks = rememberShelfLooks()
    val loaded = library.listState as? LibraryListState.Loaded ?: return SidebarShelves()
    val shelves = remember(loaded.collections, loaded.members, loaded.all, looks) {
        shelvesOf(loaded.collections, loaded.members, loaded.all.map { it.book.id }, looks)
    }
    val query = loaded.query
    return SidebarShelves(shelves, query.narrow.collection) { id ->
        library.setQuery(query.copy(narrow = query.narrow.copy(collection = id)))
        onOpened()
    }
}

/**
 * The drawer's shelves, the same four as the library's chips with the same counts. One is marked only while the
 * library shows it on no collection, since a collection is then the place the listener is in.
 */
@Composable
fun drawerShelves(library: LibraryController, libraryShowing: Boolean, onOpened: () -> Unit): List<DrawerShelf> {
    val loaded = library.listState as? LibraryListState.Loaded ?: return emptyList()
    val counts = remember(loaded.all) { shelfCounts(loaded.all) }
    val query = loaded.query
    val current = query.filter.takeIf { libraryShowing && query.narrow.collection == null }
    return LibraryFilter.entries.map { shelf ->
        DrawerShelf(shelf.label, shelf.icon, counts[shelf] ?: 0, shelf == current) {
            library.setQuery(query.copy(filter = shelf, narrow = query.narrow.copy(collection = null)))
            onOpened()
        }
    }
}

private val LibraryFilter.icon: Int
    get() = when (this) {
        LibraryFilter.ALL -> R.drawable.ic_library
        LibraryFilter.IN_PROGRESS -> R.drawable.ic_headphones
        LibraryFilter.NOT_STARTED -> R.drawable.ic_bookmark_border
        LibraryFilter.FINISHED -> R.drawable.ic_check
    }
