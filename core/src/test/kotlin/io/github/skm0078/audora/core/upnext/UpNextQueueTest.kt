// File: core/src/test/kotlin/io/github/skm0078/audora/core/upnext/UpNextQueueTest.kt
// Purpose: The Up next queue's edits and its file encoding survive odd titles and damaged lines (plan item 25).

package io.github.skm0078.audora.core.upnext

import io.github.skm0078.audora.core.library.BookId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class UpNextQueueTest {
    private fun UpNextQueue.books() = entries.map { it.bookId.value }

    @Test
    fun addMoveRemoveAndClear() {
        var queue = UpNextQueue().add(BookId("a")).add(BookId("b"), 40, "Ch. 3").add(BookId("a"))
        assertEquals(listOf("a", "b", "a"), queue.books())
        assertEquals(listOf(1L, 2L, 3L), queue.entries.map { it.entryId })

        queue = queue.move(2, 0)
        assertEquals(listOf("a", "a", "b"), queue.books())
        assertEquals(queue, queue.move(0, 9))

        queue = queue.remove(1)
        assertEquals(listOf(3L, 2L), queue.entries.map { it.entryId })
        assertEquals(4L, queue.clear().add(BookId("c")).entries.single().entryId)
    }

    @Test
    fun aLabelWithoutAStartPageIsDropped() {
        assertNull(UpNextQueue().add(BookId("a"), startLabel = "Ch. 3").entries.single().startLabel)
    }

    @Test
    fun keepOnlyDropsBooksThatLeftTheLibrary() {
        val queue = UpNextQueue().add(BookId("a")).add(BookId("gone")).add(BookId("b"))
        assertEquals(listOf("a", "b"), queue.keepOnly(setOf(BookId("a"), BookId("b"))).books())
    }

    @Test
    fun encodingRoundTripsTabsNewlinesAndIndicTitles() {
        val queue = UpNextQueue(keepGoingToNextChapter = false)
            .add(BookId("3f2a-1"), 12, "অধ্যায় ৩\tপর্ব\nদুই")
            .add(BookId("9c-2"))
            .remove(1)
            .add(BookId("x"), 0, "Ch. 1 · 100% done")

        assertEquals(queue, UpNextQueue.decode(queue.encode()))
    }

    @Test
    fun missingOrUnknownFilesGiveTheDefaults() {
        assertEquals(UpNextQueue(), UpNextQueue.decode(null))
        assertEquals(UpNextQueue(), UpNextQueue.decode(""))
        assertEquals(UpNextQueue(), UpNextQueue.decode("upnext9\t1\t1\t3"))
    }

    @Test
    fun damagedEntryLinesAreDroppedAndIdsStayUnique() {
        val text = "upnext1\t1\t0\t2\n5\tbook-a\t-\t-\nnot a line\n7\t\t3\tx\n6\tbook-b\t-4\tlabel"
        val queue = UpNextQueue.decode(text)

        assertEquals(listOf("book-a", "book-b"), queue.books())
        assertNull(queue.entries[1].startPage)
        assertFalse(queue.thenPlayQueue)
        assertEquals(7L, queue.nextId)
    }
}
