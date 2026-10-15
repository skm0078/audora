// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/contract/PlaybackAddress.kt
// Purpose: A sentence's place in a book as a Media3 media id, shared by the reader and the playback service.

package io.github.skm0078.audora.playback.contract

import io.github.skm0078.audora.core.library.BookId

/**
 * Where reading is, or should start: a book, a zero-based page and a character offset into that page's text.
 *
 * Every playlist item's media id is an address (the unit's own `charStart`), and the reader starts playback by
 * sending a single item whose media id is an address; the service expands it into the playlist from the unit
 * that contains [charStart] (0 = start of the page). `charStart`, not the sentence index, is the key, as for
 * resume anchors (`ReadingUnit.charStart`).
 *
 * Format: `audora://book/<bookId>/page/<page>/char/<charStart>`.
 */
data class PlaybackAddress(val bookId: BookId, val page: Int, val charStart: Int) {
    init {
        require(BOOK_ID.matches(bookId.value)) { "bookId has characters a media id cannot carry: ${bookId.logLabel}" }
        require(page >= 0) { "page must be >= 0, was $page" }
        require(charStart >= 0) { "charStart must be >= 0, was $charStart" }
    }

    fun toMediaId(): String = "$PREFIX${bookId.value}/page/$page/char/$charStart"

    /** Same page and book; the reader uses it to follow the spoken sentence. */
    fun isOnPage(bookId: BookId, page: Int): Boolean = this.bookId == bookId && this.page == page

    companion object {
        private const val PREFIX = "audora://book/"
        private val BOOK_ID = Regex("[A-Za-z0-9_-]+")
        private val MEDIA_ID = Regex("""audora://book/([A-Za-z0-9_-]+)/page/(\d{1,9})/char/(\d{1,9})""")

        fun startOfPage(bookId: BookId, page: Int): PlaybackAddress = PlaybackAddress(bookId, page, 0)

        /** Null for anything that is not an address (another app's media id, an empty item, a newer format). */
        fun parse(mediaId: String?): PlaybackAddress? {
            val match = mediaId?.let(MEDIA_ID::matchEntire) ?: return null
            val (book, page, char) = match.destructured
            return PlaybackAddress(BookId(book), page.toInt(), char.toInt())
        }
    }
}
