// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/browse/BrowseTree.kt
// Purpose: What each folder of the browsable library holds, and the book a spoken search means (plan item 34).

package io.github.skm0078.audora.playback.browse

import io.github.skm0078.audora.core.config.BrowseConfig
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.upnext.UpNextQueue

/** One row a media browser shows. A tab or book folder is [browsable]; resume and chapter rows are [playable]. */
data class BrowseEntry(
    val node: BrowseNode,
    val title: String,
    val subtitle: String? = null,
    val playable: Boolean = false,
    val browsable: Boolean = false,
)

/** The words the tree shows, from the app's strings. */
interface BrowseTexts {
    val recent: String
    val upNext: String
    val library: String
    val resume: String
    val whereLeftOff: String

    fun page(number: Int): String

    fun chapter(number: Int): String
}

/**
 * Pure: the service hands it the books, the queue and a book's chapters. Laid out for driving: tapping a recent book
 * or an Up next entry plays it at once; the library tab opens a book to pick a chapter.
 */
class BrowseTree(private val texts: BrowseTexts, private val config: BrowseConfig = BrowseConfig()) {
    fun root(): List<BrowseEntry> = listOf(
        BrowseEntry(BrowseNode.Recent, texts.recent, browsable = true),
        BrowseEntry(BrowseNode.UpNext, texts.upNext, browsable = true),
        BrowseEntry(BrowseNode.Shelf, texts.library, browsable = true),
    )

    fun recent(books: List<Book>): List<BrowseEntry> =
        books.sortedByDescending { it.recentAtMillis }.take(config.recentCount).map(::resumeRow)

    /** Entries whose book left the library are not shown (the queue prunes them too). */
    fun upNext(queue: UpNextQueue, books: List<Book>): List<BrowseEntry> {
        val byId = books.associateBy { it.id }
        return queue.entries.mapNotNull { entry ->
            val book = byId[entry.bookId] ?: return@mapNotNull null
            val page = entry.startPage
            if (page == null) {
                resumeRow(book)
            } else {
                BrowseEntry(
                    BrowseNode.Chapter(book.id, page),
                    book.title,
                    entry.startLabel ?: texts.page(page + 1),
                    playable = true,
                )
            }
        }
    }

    fun shelf(books: List<Book>): List<BrowseEntry> = books.sortedBy { it.title.lowercase() }.map { book ->
        BrowseEntry(BrowseNode.Book(book.id), book.title, browsable = true)
    }

    fun book(book: Book, chapters: List<BookChapter>): List<BrowseEntry> =
        listOf(BrowseEntry(BrowseNode.Resume(book.id), texts.resume, book.title, playable = true)) +
            chapters.mapIndexed { i, chapter ->
                BrowseEntry(
                    BrowseNode.Chapter(book.id, chapter.page),
                    chapter.title.ifBlank { texts.chapter(i + 1) },
                    texts.page(chapter.page + 1),
                    playable = true,
                )
            }

    /**
     * Books whose title holds every word of [query] (any case), titles starting with it first, then the most recent.
     * A blank query (a bare "play Audora") is the most recent book.
     */
    fun search(query: String, books: List<Book>): List<BrowseEntry> {
        val words = query.lowercase().split(WHITESPACE).filter { it.isNotBlank() }
        val recentFirst = books.sortedByDescending { it.recentAtMillis }
        if (words.isEmpty()) return recentFirst.take(1).map(::resumeRow)
        val start = words.joinToString(" ")
        return recentFirst
            .filter { book -> words.all { it in book.title.lowercase() } }
            .sortedByDescending { it.title.lowercase().startsWith(start) }
            .map(::resumeRow)
    }

    private fun resumeRow(book: Book) =
        BrowseEntry(BrowseNode.Resume(book.id), book.title, texts.whereLeftOff, playable = true)

    private companion object {
        val WHITESPACE = Regex("""\s+""")
    }
}
