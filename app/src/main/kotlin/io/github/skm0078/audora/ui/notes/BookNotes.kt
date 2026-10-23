// File: app/src/main/kotlin/io/github/skm0078/audora/ui/notes/BookNotes.kt
// Purpose: The Notes tab's content: every book's bookmarks, highlights and voice notes in reading order, books
//          without any left out.

package io.github.skm0078.audora.ui.notes

import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.notes.BookmarkStore
import io.github.skm0078.audora.core.notes.HighlightStore
import io.github.skm0078.audora.core.notes.VoiceNoteStore
import io.github.skm0078.audora.core.resume.ResumeAnchor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** One saved place in a book, whatever kind it is; [anchor] is where a tap opens the book. */
sealed interface NoteEntry {
    val anchor: ResumeAnchor

    /** Unique across kinds, for list keys. */
    val key: String

    data class Bookmark(val id: Long, override val anchor: ResumeAnchor, val note: String) : NoteEntry {
        override val key get() = "bookmark-$id"
    }

    data class Highlight(val id: Long, override val anchor: ResumeAnchor, val quote: String, val note: String) :
        NoteEntry {
        override val key get() = "highlight-$id"
    }

    data class VoiceNote(val id: Long, override val anchor: ResumeAnchor, val durationMs: Long) : NoteEntry {
        override val key get() = "voice-$id"
    }
}

/** One book's notes of every kind, in reading order (page, then offset). */
data class BookNotes(val book: Book, val entries: List<NoteEntry>)

/** The stores the Notes tab reads; null for a kind this build has no store for. */
class NoteSources(
    val bookmarks: BookmarkStore,
    val highlights: HighlightStore? = null,
    val voiceNotes: VoiceNoteStore? = null,
)

private const val TAG = "Audora.Notes"

/**
 * Every book's notes, books without any left out, in library order. Bookmarks are read one store query per book (the
 * store has no cross-book query; libraries are a few dozen books), highlights and voice notes with one query each. A
 * kind that cannot be read is logged and shows none, so one bad row never empties the tab.
 */
fun bookNotes(sources: NoteSources, books: List<Book>): Flow<List<BookNotes>> {
    if (books.isEmpty()) return flowOf(emptyList())
    val bookmarks = books.map { book ->
        sources.bookmarks.observe(book.id)
            .catch { e ->
                AudoraLog.w(TAG, "Could not read bookmarks, book=${book.id.value}", e)
                emit(emptyList())
            }
            .map { list -> book.id to list.map { NoteEntry.Bookmark(it.id, it.anchor, it.note) } }
    }
    val highlights = sources.highlights?.observeAll()
        ?.map { list -> list.map { NoteEntry.Highlight(it.id, it.anchor, it.quote, it.note) } }
        ?.catch { e ->
            AudoraLog.w(TAG, "Could not read highlights", e)
            emit(emptyList())
        } ?: flowOf(emptyList())
    val voiceNotes = sources.voiceNotes?.observeAll()
        ?.map { list -> list.map { NoteEntry.VoiceNote(it.id, it.anchor, it.durationMs) } }
        ?.catch { e ->
            AudoraLog.w(TAG, "Could not read voice notes", e)
            emit(emptyList())
        } ?: flowOf(emptyList())
    return combine(combine(bookmarks) { it.toMap() }, highlights, voiceNotes) { marks, quotes, voices ->
        groupNotes(books, marks, quotes + voices)
    }
}

/** [books] in order, each with its bookmarks and [others] merged in reading order; books with nothing left out. */
internal fun groupNotes(
    books: List<Book>,
    bookmarks: Map<BookId, List<NoteEntry>>,
    others: List<NoteEntry>,
): List<BookNotes> {
    val byBook = others.groupBy { it.anchor.bookId }
    return books.mapNotNull { book ->
        val entries = bookmarks[book.id].orEmpty() + byBook[book.id].orEmpty()
        entries.takeIf { it.isNotEmpty() }
            ?.let { BookNotes(book, it.sortedWith(compareBy({ e -> e.anchor.page }, { e -> e.anchor.charStart }))) }
    }
}
