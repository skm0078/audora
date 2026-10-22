// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/browse/BrowseLookup.kt
// Purpose: Answers "what is in this folder", "what is this row" and "play this row" from the library (plan item 34).

package io.github.skm0078.audora.playback.browse

import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.upnext.UpNextQueue
import io.github.skm0078.audora.playback.book.OpenFailure

/** What the browser reads and does; the service binds these to the library, the queue and the book session. */
class BrowseSources(
    val books: suspend () -> List<Book>,
    val upNext: () -> UpNextQueue,
    val chapters: suspend (Book) -> List<BookChapter>,
    val resume: suspend (BookId) -> Outcome<Unit, OpenFailure>,
    val playFrom: suspend (BookId, page: Int) -> Outcome<Unit, OpenFailure>,
)

class BrowseLookup(private val tree: BrowseTree, private val sources: BrowseSources) {
    /** Null for a row that is not a folder, or a book no longer in the library. */
    suspend fun children(node: BrowseNode): List<BrowseEntry>? = when (node) {
        BrowseNode.Root -> tree.root()
        BrowseNode.Recent -> tree.recent(sources.books())
        BrowseNode.UpNext -> tree.upNext(sources.upNext(), sources.books())
        BrowseNode.Shelf -> tree.shelf(sources.books())
        is BrowseNode.Book -> book(node.id)?.let { tree.book(it, sources.chapters(it)) }
        is BrowseNode.Resume, is BrowseNode.Chapter -> null
    }

    /** One row by its id: a tab, or a book's row as its folder shows it. */
    suspend fun entry(node: BrowseNode): BrowseEntry? = when (node) {
        BrowseNode.Root -> BrowseEntry(node, "", browsable = true)

        BrowseNode.Recent, BrowseNode.UpNext, BrowseNode.Shelf -> tree.root().firstOrNull { it.node == node }

        is BrowseNode.Book -> book(node.id)?.let { tree.shelf(listOf(it)).single() }

        is BrowseNode.Resume -> book(node.id)?.let { tree.recent(listOf(it)).single() }

        is BrowseNode.Chapter -> book(node.id)?.let { book ->
            tree.book(book, sources.chapters(book)).firstOrNull { it.node == node }
        }
    }

    suspend fun search(query: String): List<BrowseEntry> = tree.search(query, sources.books())

    /** Opens and plays the row's book; null for a folder, which has nothing to play. */
    suspend fun play(node: BrowseNode): Outcome<Unit, OpenFailure>? = when (node) {
        is BrowseNode.Resume -> sources.resume(node.id)
        is BrowseNode.Book -> sources.resume(node.id)
        is BrowseNode.Chapter -> sources.playFrom(node.id, node.page)
        BrowseNode.Root, BrowseNode.Recent, BrowseNode.UpNext, BrowseNode.Shelf -> null
    }

    private suspend fun book(id: BookId): Book? = sources.books().firstOrNull { it.id == id }
}
