// File: core/src/main/kotlin/io/github/skm0078/audora/core/sync/SyncModel.kt
// Purpose: What one phone writes to the sync folder (CF-16): its books by fingerprint, each with its place,
//          bookmarks and highlights, and the marks it removed.

package io.github.skm0078.audora.core.sync

import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.notes.Bookmark
import io.github.skm0078.audora.core.notes.Highlight
import io.github.skm0078.audora.core.notes.HighlightColor
import io.github.skm0078.audora.core.resume.ResumeAnchor

/**
 * A place in a book without the book's local id: phones share books by their content fingerprint, since each phone
 * gives the same book its own id.
 */
data class SyncAnchor(
    val page: Int,
    val charStart: Int,
    val fingerprint: Long,
    val tailFingerprint: Long,
    val pipelineVersion: Int,
    val savedAtMillis: Long,
) {
    fun toAnchor(bookId: BookId) =
        ResumeAnchor(bookId, page, charStart, fingerprint, tailFingerprint, pipelineVersion, savedAtMillis)

    companion object {
        fun of(anchor: ResumeAnchor) = with(anchor) {
            SyncAnchor(page, charStart, fingerprint, tailFingerprint, pipelineVersion, savedAtMillis)
        }
    }
}

/** A bookmark; [key] names it on every phone (its place and the moment it was made). */
data class SyncBookmark(val anchor: SyncAnchor, val note: String) {
    val key: String get() = "b:${anchor.page}:${anchor.charStart}:${anchor.savedAtMillis}"

    fun toBookmark(bookId: BookId) = Bookmark(id = 0, anchor = anchor.toAnchor(bookId), note = note)

    companion object {
        fun of(bookmark: Bookmark) = SyncBookmark(SyncAnchor.of(bookmark.anchor), bookmark.note)
    }
}

/** A highlight; [color] is a [HighlightColor] name (a colour this build does not know is not added). */
data class SyncHighlight(
    val anchor: SyncAnchor,
    val endPage: Int,
    val endChar: Int,
    val quote: String,
    val note: String,
    val color: String,
) {
    val key: String get() = "h:${anchor.page}:${anchor.charStart}:$endPage:$endChar:${anchor.savedAtMillis}"

    fun toHighlight(bookId: BookId): Highlight? = HighlightColor.entries.firstOrNull { it.name == color }?.let {
        Highlight(0, anchor.toAnchor(bookId), endPage, endChar, quote, note, it)
    }

    companion object {
        fun of(highlight: Highlight) = with(highlight) {
            SyncHighlight(SyncAnchor.of(anchor), endPage, endChar, quote, note, color.name)
        }
    }
}

/**
 * One book on one phone. [fileName] is its copy in the sync folder's Books folder, when this phone put one there, so
 * another phone can add the book itself.
 */
data class SyncBook(
    val fingerprint: String,
    val title: String,
    val fileName: String? = null,
    val progress: SyncAnchor? = null,
    val bookmarks: List<SyncBookmark> = emptyList(),
    val highlights: List<SyncHighlight> = emptyList(),
) {
    /** Every mark's key, with the book's fingerprint in front: unique across books. */
    val markKeys: List<String> get() = (bookmarks.map { it.key } + highlights.map { it.key }).map { markKey(it) }

    fun markKey(key: String) = "$fingerprint|$key"
}

/** A bookmark or highlight removed on some phone at [atMillis], so other phones remove it too. */
data class SyncRemoval(val markKey: String, val atMillis: Long)

/** What one phone wrote: [deviceId] is random, made once per install; never the phone's name. */
data class SyncSnapshot(
    val deviceId: String,
    val writtenAtMillis: Long,
    val books: List<SyncBook>,
    val removals: List<SyncRemoval>,
)

/**
 * What this phone remembers between syncs, to tell "removed here" from "new there".
 *
 * @property knownMarks every mark key this phone had after its last sync.
 * @property removals marks removed (here or elsewhere) not yet forgotten.
 * @property knownBooks fingerprints of this phone's books after its last sync.
 * @property dismissedBooks books removed from this phone's library: never added back from the folder.
 */
data class SyncMemory(
    val knownMarks: Set<String> = emptySet(),
    val removals: List<SyncRemoval> = emptyList(),
    val knownBooks: Set<String> = emptySet(),
    val dismissedBooks: Set<String> = emptySet(),
)
