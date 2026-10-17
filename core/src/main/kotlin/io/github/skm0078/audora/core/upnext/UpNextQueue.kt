// File: core/src/main/kotlin/io/github/skm0078/audora/core/upnext/UpNextQueue.kt
// Purpose: The Up next queue of books (plan item 25): entries, its two switches, the edits and a one-file encoding.

package io.github.skm0078.audora.core.upnext

import io.github.skm0078.audora.core.library.BookId
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * @property startPage the page a queued chapter starts on; null starts the book from where it was left.
 * @property startLabel that chapter's title, for the screen only: it comes from the PDF, so it is never logged.
 */
data class QueuedBook(
    val entryId: Long,
    val bookId: BookId,
    val startPage: Int? = null,
    val startLabel: String? = null,
) {
    val logLabel: String get() = "entry=$entryId book=${bookId.logLabel} start=${startPage ?: "resume"}"
}

/**
 * Immutable; every edit returns a new queue. [nextId] keeps entry ids unique for the queue's life, so the same book
 * can be queued twice.
 *
 * @property keepGoingToNextChapter off: reading stops at the end of each chapter.
 * @property thenPlayQueue when a book ends, the first entry starts and leaves the queue.
 */
data class UpNextQueue(
    val entries: List<QueuedBook> = emptyList(),
    val keepGoingToNextChapter: Boolean = true,
    val thenPlayQueue: Boolean = true,
    val nextId: Long = 1,
) {
    fun add(bookId: BookId, startPage: Int? = null, startLabel: String? = null): UpNextQueue {
        require(startPage == null || startPage >= 0) { "startPage must be >= 0, was $startPage" }
        val entry = QueuedBook(nextId, bookId, startPage, startLabel?.takeIf { startPage != null })
        return copy(entries = entries + entry, nextId = nextId + 1)
    }

    fun remove(entryId: Long): UpNextQueue = copy(entries = entries.filterNot { it.entryId == entryId })

    /** Out-of-range indexes leave the queue as it is. */
    fun move(from: Int, to: Int): UpNextQueue = if (from !in entries.indices || to !in entries.indices || from == to) {
        this
    } else {
        copy(entries = entries.toMutableList().apply { add(to, removeAt(from)) })
    }

    fun clear(): UpNextQueue = copy(entries = emptyList())

    /** Drops entries whose book left the library. */
    fun keepOnly(books: Set<BookId>): UpNextQueue = copy(entries = entries.filter { it.bookId in books })

    /** One header line, then one line per entry; text fields are URL-encoded, so tabs and newlines are safe. */
    fun encode(): String = buildString {
        append("$VERSION\t${flag(keepGoingToNextChapter)}\t${flag(thenPlayQueue)}\t$nextId")
        entries.forEach { entry ->
            append('\n')
            append(entry.entryId).append('\t').append(encoded(entry.bookId.value)).append('\t')
            append(entry.startPage?.toString() ?: NONE).append('\t')
            append(entry.startLabel?.let(::encoded) ?: NONE)
        }
    }

    companion object {
        private const val VERSION = "upnext1"
        private const val NONE = "-"
        private const val ON = "1"
        private const val HEADER_FIELDS = 4
        private const val KEEP_GOING = 1
        private const val THEN_PLAY = 2
        private const val NEXT_ID = 3
        private const val ENTRY_FIELDS = 4
        private const val ENTRY_ID = 0
        private const val BOOK = 1
        private const val PAGE = 2
        private const val LABEL = 3

        /** The defaults for a missing, empty or unknown file; a malformed entry line is dropped, the rest kept. */
        fun decode(text: String?): UpNextQueue {
            val lines = text?.lines()?.filter { it.isNotBlank() }.orEmpty()
            val header = lines.firstOrNull()?.split('\t')
            if (header == null || header.size != HEADER_FIELDS || header[0] != VERSION) return UpNextQueue()
            val entries = lines.drop(1).mapNotNull(::entryOf)
            val savedNextId = header[NEXT_ID].toLongOrNull() ?: 1
            val nextId = maxOf(savedNextId, (entries.maxOfOrNull { it.entryId } ?: 0) + 1)
            return UpNextQueue(entries, header[KEEP_GOING] == ON, header[THEN_PLAY] == ON, nextId)
        }

        private fun entryOf(line: String): QueuedBook? {
            val fields = line.split('\t').takeIf { it.size == ENTRY_FIELDS }
            val id = fields?.get(ENTRY_ID)?.toLongOrNull()
            val book = fields?.get(BOOK)?.let(::decoded)?.takeIf { it.isNotBlank() }
            if (fields == null || id == null || book == null) return null
            val page = fields[PAGE].takeUnless { it == NONE }?.toIntOrNull()?.takeIf { it >= 0 }
            val label = fields[LABEL].takeUnless { it == NONE || page == null }?.let(::decoded)
            return QueuedBook(id, BookId(book), page, label)
        }

        private fun flag(on: Boolean) = if (on) ON else "0"

        private fun encoded(text: String): String = URLEncoder.encode(text, Charsets.UTF_8)

        private fun decoded(text: String): String? = runCatching { URLDecoder.decode(text, Charsets.UTF_8) }.getOrNull()
    }
}
