// File: core/src/test/kotlin/io/github/skm0078/audora/core/sync/SyncMergeTest.kt
// Purpose: Two phones syncing through a folder: the newest place wins, marks are joined, a removal anywhere removes
//          everywhere, a removed book stays removed here and on the others, and books come from the folder.

package io.github.skm0078.audora.core.sync

import io.github.skm0078.audora.core.config.FolderSyncConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMergeTest {
    private val merge = SyncMerge(FolderSyncConfig(removalKeepMs = 1_000_000L))

    private fun anchor(page: Int, saved: Long) = SyncAnchor(page, 0, 1L, 2L, 13, saved)

    private fun book(
        progress: SyncAnchor? = null,
        bookmarks: List<SyncBookmark> = emptyList(),
        fingerprint: String = FP,
        fileName: String? = null,
    ) = SyncBook(fingerprint, "Book", fileName, progress, bookmarks)

    private fun other(vararg books: SyncBook, removals: List<SyncRemoval> = emptyList()) =
        SyncSnapshot("other", 0L, books.toList(), removals)

    @Test
    fun `the newest place wins, whichever phone saved it`() {
        val later = merge.merge(listOf(book(anchor(5, 100L))), SyncMemory(), listOf(other(book(anchor(9, 200L)))), NOW)
        val earlier = merge.merge(
            listOf(book(anchor(5, 300L))),
            SyncMemory(),
            listOf(other(book(anchor(9, 200L)))),
            NOW,
        )
        val none = merge.merge(listOf(book()), SyncMemory(), listOf(other(book(anchor(2, 1L)))), NOW)

        assertEquals(mapOf(FP to anchor(9, 200L)), later.progress)
        assertEquals(emptyMap<String, SyncAnchor>(), earlier.progress)
        assertEquals(mapOf(FP to anchor(2, 1L)), none.progress)
    }

    @Test
    fun `a mark made on another phone is added once, and becomes known`() {
        val mark = SyncBookmark(anchor(1, 50L), "note")
        val twice = listOf(other(book(bookmarks = listOf(mark))), other(book(bookmarks = listOf(mark))))

        val plan = merge.merge(listOf(book()), SyncMemory(), twice, NOW)

        assertEquals(mapOf(FP to listOf(mark)), plan.bookmarks)
        assertEquals(setOf("$FP|${mark.key}"), plan.memory.knownMarks)
    }

    @Test
    fun `a mark removed here is not added back and its removal is written for the others`() {
        val mark = SyncBookmark(anchor(1, 50L), "")
        val memory = SyncMemory(knownMarks = setOf("$FP|${mark.key}"), knownBooks = setOf(FP))

        val plan = merge.merge(listOf(book()), memory, listOf(other(book(bookmarks = listOf(mark)))), NOW)

        assertTrue(plan.bookmarks.isEmpty())
        assertEquals(listOf(SyncRemoval("$FP|${mark.key}", NOW)), plan.memory.removals)
    }

    @Test
    fun `a mark removed on another phone is removed here`() {
        val mark = SyncBookmark(anchor(1, 50L), "")
        val removal = SyncRemoval("$FP|${mark.key}", NOW - 10)

        val plan = merge.merge(
            listOf(book(bookmarks = listOf(mark))),
            SyncMemory(),
            listOf(other(removals = listOf(removal))),
            NOW,
        )

        assertEquals(setOf("$FP|${mark.key}"), plan.removeMarks)
        assertTrue(plan.memory.knownMarks.isEmpty())
    }

    @Test
    fun `a removed book's marks are forgotten, never removed on the other phones, and it is not added back`() {
        val mark = SyncBookmark(anchor(1, 50L), "")
        val memory = SyncMemory(knownMarks = setOf("$FP|${mark.key}"), knownBooks = setOf(FP))
        val theirs = other(book(bookmarks = listOf(mark), fileName = "Book [abc].pdf"))

        val plan = merge.merge(emptyList(), memory, listOf(theirs), NOW)

        assertTrue(plan.memory.removals.isEmpty())
        assertTrue(plan.booksToAdd.isEmpty())
        assertEquals(setOf(FP), plan.memory.dismissedBooks)
    }

    @Test
    fun `a book in the folder this phone never had is added, one added again by hand is not dismissed`() {
        val theirs = other(book(fingerprint = "v1:new", fileName = "New [new].epub"), book(fingerprint = "v1:nofile"))

        val plan = merge.merge(listOf(book()), SyncMemory(dismissedBooks = setOf(FP)), listOf(theirs), NOW)

        assertEquals(listOf("v1:new"), plan.booksToAdd.map { it.fingerprint })
        assertTrue(plan.memory.dismissedBooks.isEmpty())
    }

    @Test
    fun `old removals are forgotten`() {
        val old = SyncRemoval("$FP|b:1:0:1", NOW - 2_000_000L)

        val plan = merge.merge(listOf(book()), SyncMemory(removals = listOf(old)), emptyList(), NOW)

        assertTrue(plan.memory.removals.isEmpty())
    }

    @Test
    fun `nothing new changes nothing`() {
        val mark = SyncBookmark(anchor(1, 50L), "")
        val mine = book(anchor(3, 10L), listOf(mark))

        val plan = merge.merge(listOf(mine), SyncMemory(), listOf(other(mine)), NOW)

        assertEquals(0, plan.changes)
    }

    private companion object {
        const val FP = "v1:abc"
        const val NOW = 10_000_000L
    }
}
