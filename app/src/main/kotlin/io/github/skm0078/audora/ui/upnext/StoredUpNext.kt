// File: app/src/main/kotlin/io/github/skm0078/audora/ui/upnext/StoredUpNext.kt
// Purpose: Backs the Up next sheet (plan item 25) with the saved queue the playback service plays from.

package io.github.skm0078.audora.ui.upnext

import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.Library
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.SpeechStyle
import io.github.skm0078.audora.core.progress.TimeLeft
import io.github.skm0078.audora.core.resume.ResumeAnchor
import io.github.skm0078.audora.core.upnext.QueuedBook
import io.github.skm0078.audora.core.upnext.UpNextQueue
import io.github.skm0078.audora.playback.upnext.UpNextStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

/**
 * Rows get their titles from the library and a rough time left: the book's pages from where it starts (its chapter,
 * else its resume position) at [speed]. A book removed from the library leaves the queue.
 *
 * @param speed the listener's saved speed, read when the rows are built.
 */
class StoredUpNext(
    private val store: UpNextStore,
    library: Library,
    anchors: Flow<List<ResumeAnchor>>,
    scope: CoroutineScope,
    private val timeLeft: TimeLeft = TimeLeft(),
    private val speed: suspend () -> Float = { SpeechStyle.NORMAL_RATE },
) : UpNextPort {
    private val books = library.books().catch { e ->
        AudoraLog.w(TAG, "Library unreadable; Up next shows no titles", e)
        emit(emptyList())
    }
    private val positions = anchors.catch { e ->
        AudoraLog.w(TAG, "Reading positions unreadable; Up next starts books from their saved page", e)
        emit(emptyList())
    }

    override val state: StateFlow<UpNextState> =
        combine(store.queue, books, positions) { queue, latest, saved -> stateOf(queue, latest, saved) }
            .stateIn(scope, SharingStarted.Eagerly, UpNextState())

    init {
        // Only a list read from the database prunes: the empty fallback above never empties the queue.
        library.books().onEach { latest -> store.update { it.keepOnly(latest.mapTo(HashSet()) { book -> book.id }) } }
            .catch { e -> AudoraLog.w(TAG, "Up next not pruned: the library could not be read", e) }
            .launchIn(scope)
    }

    override fun add(bookId: BookId, startPage: Int?, startLabel: String?) {
        store.update { it.add(bookId, startPage, startLabel) }
        AudoraLog.i(TAG, "Queued book=${bookId.logLabel} start=${startPage ?: "resume"}")
    }

    override fun remove(entryId: Long) = store.update { it.remove(entryId) }

    override fun move(from: Int, to: Int) = store.update { it.move(from, to) }

    override fun clear() = store.update { it.clear() }

    override fun setKeepGoingToNextChapter(on: Boolean) = store.update { it.copy(keepGoingToNextChapter = on) }

    override fun setThenPlayQueue(on: Boolean) = store.update { it.copy(thenPlayQueue = on) }

    private suspend fun stateOf(queue: UpNextQueue, latest: List<Book>, saved: List<ResumeAnchor>): UpNextState {
        val byId = latest.associateBy { it.id }
        val pages = saved.associate { it.bookId to it.page }
        val rate = speed()
        return UpNextState(
            entries = queue.entries.map { entry -> entryOf(entry, byId[entry.bookId], pages[entry.bookId], rate) },
            keepGoingToNextChapter = queue.keepGoingToNextChapter,
            thenPlayQueue = queue.thenPlayQueue,
        )
    }

    private fun entryOf(entry: QueuedBook, book: Book?, savedPage: Int?, rate: Float): UpNextEntry {
        val pageCount = book?.pageCount
        val from = entry.startPage ?: savedPage ?: 0
        return UpNextEntry(
            entryId = entry.entryId,
            bookId = entry.bookId,
            title = book?.title.orEmpty(),
            startPage = entry.startPage,
            startLabel = entry.startLabel,
            // The book's language is not known before it is read; English rates are close enough for "about".
            timeLeftMillis = pageCount?.let { timeLeft.roughMs(it - from, Lang.EN, rate) },
        )
    }

    private companion object {
        const val TAG = "Audora.UpNext"
    }
}
