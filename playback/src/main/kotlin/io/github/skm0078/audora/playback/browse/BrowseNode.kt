// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/browse/BrowseNode.kt
// Purpose: The places in the browsable library (Android Auto, plan item 34) and the media ids that name them.

package io.github.skm0078.audora.playback.browse

import io.github.skm0078.audora.core.library.BookId

/**
 * A media browser sees only [mediaId]s and hands them back when it opens a folder or plays an item, so each node
 * round-trips through [parse]. The book id goes last in an id, so whatever characters it holds cannot be misread.
 */
sealed interface BrowseNode {
    val mediaId: String

    /** The tabs: recent books, the Up next queue, the whole library. */
    data object Root : BrowseNode {
        override val mediaId = "root"
    }

    data object Recent : BrowseNode {
        override val mediaId = "recent"
    }

    data object UpNext : BrowseNode {
        override val mediaId = "upnext"
    }

    data object Shelf : BrowseNode {
        override val mediaId = "library"
    }

    /** A book's folder: "Resume" and its chapters. */
    data class Book(val id: BookId) : BrowseNode {
        override val mediaId = "$BOOK$SEPARATOR${id.value}"
    }

    /** Plays the book from where it was left. */
    data class Resume(val id: BookId) : BrowseNode {
        override val mediaId = "$RESUME$SEPARATOR${id.value}"
    }

    /** Plays the book from the start of the chapter on [page] (zero-based). */
    data class Chapter(val id: BookId, val page: Int) : BrowseNode {
        init {
            require(page >= 0) { "page must be >= 0, was $page" }
        }

        override val mediaId = "$CHAPTER$SEPARATOR$page$SEPARATOR${id.value}"
    }

    companion object {
        private const val SEPARATOR = '/'
        private const val BOOK = "book"
        private const val RESUME = "resume"
        private const val CHAPTER = "chapter"
        private val TABS = listOf(Root, Recent, UpNext, Shelf).associateBy { it.mediaId }

        /** Null for an id Audora never gave out. */
        fun parse(mediaId: String): BrowseNode? {
            TABS[mediaId]?.let { return it }
            val kind = mediaId.substringBefore(SEPARATOR)
            val rest = mediaId.substringAfter(SEPARATOR, missingDelimiterValue = "")
            return when (kind) {
                BOOK -> bookId(rest)?.let(::Book)
                RESUME -> bookId(rest)?.let(::Resume)
                CHAPTER -> chapter(rest)
                else -> null
            }
        }

        private fun chapter(rest: String): Chapter? {
            val page = rest.substringBefore(SEPARATOR).toIntOrNull()?.takeIf { it >= 0 }
            val id = bookId(rest.substringAfter(SEPARATOR, missingDelimiterValue = ""))
            return if (page == null || id == null) null else Chapter(id, page)
        }

        private fun bookId(value: String): BookId? = value.takeIf { it.isNotBlank() }?.let(::BookId)
    }
}
