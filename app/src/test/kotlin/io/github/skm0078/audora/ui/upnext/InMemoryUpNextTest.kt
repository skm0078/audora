// File: app/src/test/kotlin/io/github/skm0078/audora/ui/upnext/InMemoryUpNextTest.kt
// Purpose: The in-memory Up next queue adds, moves, removes and keeps its two switches (plan item 25).

package io.github.skm0078.audora.ui.upnext

import io.github.skm0078.audora.core.library.BookId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class InMemoryUpNextTest {
    private val queue = InMemoryUpNext()

    private fun books() = queue.state.value.entries.map { it.bookId.value }

    @Test
    fun addsInOrderAndTheSameBookTwiceGetsTwoEntries() {
        queue.add(BookId("a"))
        queue.add(BookId("b"), startPage = 40, startLabel = "Chapter 3")
        queue.add(BookId("a"))

        assertEquals(listOf("a", "b", "a"), books())
        assertEquals(3, queue.state.value.entries.map { it.entryId }.toSet().size)
        assertEquals(40, queue.state.value.entries[1].startPage)
    }

    @Test
    fun movesRemovesAndIgnoresBadIndexes() {
        listOf("a", "b", "c").forEach { queue.add(BookId(it)) }

        queue.move(from = 2, to = 0)
        assertEquals(listOf("c", "a", "b"), books())
        queue.move(from = 5, to = 0)
        assertEquals(listOf("c", "a", "b"), books())

        queue.remove(queue.state.value.entries[1].entryId)
        assertEquals(listOf("c", "b"), books())
        queue.clear()
        assertEquals(emptyList<String>(), books())
    }

    @Test
    fun switchesAreKept() {
        queue.setKeepGoingToNextChapter(false)
        queue.setThenPlayQueue(false)

        assertFalse(queue.state.value.keepGoingToNextChapter)
        assertFalse(queue.state.value.thenPlayQueue)
    }
}
