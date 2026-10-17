// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/upnext/QueueAdvanceTest.kt
// Purpose: Up next (plan item 25): the saved queue survives a restart, and a finished book starts the next one.

package io.github.skm0078.audora.playback.upnext

import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.upnext.QueuedBook
import io.github.skm0078.audora.playback.book.OpenFailure
import io.github.skm0078.audora.playback.contract.PlaybackErrorCode
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class QueueAdvanceTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val dispatcher = UnconfinedTestDispatcher()
    private val scope = TestScope(dispatcher)
    private val file by lazy { File(temp.root, "up-next.txt") }
    private val store by lazy { UpNextStore(file, scope, dispatcher) }
    private val started = mutableListOf<QueuedBook>()
    private var unreadable = emptySet<String>()

    private val advance by lazy {
        QueueAdvance(store, scope) { entry ->
            started += entry
            if (entry.bookId.value in unreadable) {
                Outcome.Failure(OpenFailure(PlaybackErrorCode.BOOK_UNREADABLE, "moved"))
            } else {
                Outcome.Success(Unit)
            }
        }
    }

    @Test
    fun aSavedQueueIsReadBackByANewStore() = runTest(dispatcher) {
        store.update { it.add(BookId("a")).add(BookId("b"), 7, "Ch. 2") }
        scope.advanceUntilIdle()

        assertEquals(store.queue.value, UpNextStore(file, scope, dispatcher).queue.value)
    }

    @Test
    fun theFirstEntryStartsAndLeavesTheQueue() = runTest(dispatcher) {
        store.update { it.add(BookId("a")).add(BookId("b")) }

        advance.onBookFinished(BookId("done"))

        assertEquals(listOf("a"), started.map { it.bookId.value })
        assertEquals(listOf("b"), store.queue.value.entries.map { it.bookId.value })
    }

    @Test
    fun aBookThatCannotOpenIsSkipped() = runTest(dispatcher) {
        unreadable = setOf("a")
        store.update { it.add(BookId("a")).add(BookId("b")).add(BookId("c")) }

        advance.onBookFinished(BookId("done"))

        assertEquals(listOf("a", "b"), started.map { it.bookId.value })
        assertEquals(listOf("c"), store.queue.value.entries.map { it.bookId.value })
    }

    @Test
    fun nothingStartsWithThenPlayTheQueueOff() = runTest(dispatcher) {
        store.update { it.add(BookId("a")).copy(thenPlayQueue = false) }

        advance.onBookFinished(BookId("done"))

        assertEquals(emptyList<QueuedBook>(), started)
        assertEquals(1, store.queue.value.entries.size)
    }
}
