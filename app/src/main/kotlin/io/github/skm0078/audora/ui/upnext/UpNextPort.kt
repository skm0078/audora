// File: app/src/main/kotlin/io/github/skm0078/audora/ui/upnext/UpNextPort.kt
// Purpose: The Up next queue of books (plan item 25) as the screens see it; the playback service backs it.

package io.github.skm0078.audora.ui.upnext

import io.github.skm0078.audora.core.library.BookId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * One queued book.
 *
 * @property entryId stable for the entry's life, so the same book can be queued twice and rows animate.
 * @property title for the screen only, never the log.
 * @property startPage where the book starts when its turn comes: a chapter's first page, or null to start from where
 *   it was left (its resume position, else the beginning).
 * @property startLabel the chapter's title from the outline when [startPage] is a chapter, for "from Ch. 3".
 * @property timeLeftMillis about how long the book takes from where it starts, at the current speed; null when it
 *   cannot be estimated yet (never opened, so its page count is unknown).
 */
data class UpNextEntry(
    val entryId: Long,
    val bookId: BookId,
    val title: String,
    val startPage: Int? = null,
    val startLabel: String? = null,
    val timeLeftMillis: Long? = null,
)

/**
 * @property entries the queue in play order; the book playing now is not in it.
 * @property keepGoingToNextChapter off: reading stops at the end of each chapter.
 * @property thenPlayQueue when the book playing ends, the first entry starts and leaves the queue.
 */
data class UpNextState(
    val entries: List<UpNextEntry> = emptyList(),
    val keepGoingToNextChapter: Boolean = true,
    val thenPlayQueue: Boolean = true,
)

/**
 * What the Up next sheet calls. The current book's next chapter is not an entry: the sheet takes it from the reader's
 * chapter list. Calls return at once; [state] shows the result, and a failed save is logged and leaves it unchanged.
 */
interface UpNextPort {
    val state: StateFlow<UpNextState>

    /** Queues [bookId] at the end; [startPage] and [startLabel] as on [UpNextEntry]. */
    fun add(bookId: BookId, startPage: Int? = null, startLabel: String? = null)

    fun remove(entryId: Long)

    /** Moves the entry at index [from] to index [to] of [UpNextState.entries]; out-of-range indexes do nothing. */
    fun move(from: Int, to: Int)

    fun clear()

    fun setKeepGoingToNextChapter(on: Boolean)

    fun setThenPlayQueue(on: Boolean)
}

/**
 * Keeps the queue in memory only, so the Up next sheet can be built and previewed before the service backs it.
 * Titles are unknown here, so rows show none; nothing plays from it.
 */
class InMemoryUpNext : UpNextPort {
    private val mutableState = MutableStateFlow(UpNextState())
    override val state: StateFlow<UpNextState> = mutableState.asStateFlow()
    private var nextId = 1L

    override fun add(bookId: BookId, startPage: Int?, startLabel: String?) {
        val entry = UpNextEntry(nextId++, bookId, title = "", startPage = startPage, startLabel = startLabel)
        mutableState.update { it.copy(entries = it.entries + entry) }
    }

    override fun remove(entryId: Long) =
        mutableState.update { state -> state.copy(entries = state.entries.filterNot { it.entryId == entryId }) }

    override fun move(from: Int, to: Int) = mutableState.update { state ->
        val entries = state.entries
        if (from !in entries.indices || to !in entries.indices || from == to) {
            state
        } else {
            state.copy(entries = entries.toMutableList().apply { add(to, removeAt(from)) })
        }
    }

    override fun clear() = mutableState.update { it.copy(entries = emptyList()) }

    override fun setKeepGoingToNextChapter(on: Boolean) = mutableState.update { it.copy(keepGoingToNextChapter = on) }

    override fun setThenPlayQueue(on: Boolean) = mutableState.update { it.copy(thenPlayQueue = on) }
}
