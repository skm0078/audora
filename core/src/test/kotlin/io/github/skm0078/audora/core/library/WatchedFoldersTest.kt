// File: core/src/test/kotlin/io/github/skm0078/audora/core/library/WatchedFoldersTest.kt
// Purpose: The folder walk finds books within its limits, a scan adds and retries them, and records survive a reload.

package io.github.skm0078.audora.core.library

import io.github.skm0078.audora.core.config.WatchedFolderConfig
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchedFoldersTest {
    private val dir = Files.createTempDirectory("watched").toFile()

    @After
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private val tree = mapOf(
        "root" to listOf(
            FolderEntry("a", "Novel.PDF", null, isFolder = false),
            FolderEntry("b", "notes.txt", "text/plain", isFolder = false),
            FolderEntry("sub", "Hindi", null, isFolder = true),
            FolderEntry("c", "download", "application/epub+zip", isFolder = false),
        ),
        "sub" to listOf(
            FolderEntry("d", "Kahani.epub", null, isFolder = false),
            FolderEntry("deep", "More", null, isFolder = true),
        ),
        "deep" to listOf(FolderEntry("e", "Deep.pdf", "application/pdf", isFolder = false)),
    )
    private val lister = FolderLister { tree[it].orEmpty() }

    @Test
    fun `books are found by type or ending, in sub-folders, never text files`() {
        assertEquals(listOf("a", "c", "d", "e"), FolderWalker(lister).books("root"))
    }

    @Test
    fun `the walk stops at the depth and entry limits`() {
        assertEquals(listOf("a", "c"), FolderWalker(lister, WatchedFolderConfig(maxDepth = 0)).books("root"))
        assertEquals(listOf("a"), FolderWalker(lister, WatchedFolderConfig(maxEntriesPerScan = 2)).books("root"))
    }

    @Test
    fun `a folder that cannot be listed is skipped`() {
        val failing = FolderLister { if (it == "sub") throw IOException("gone") else tree[it].orEmpty() }

        assertEquals(listOf("a", "c"), FolderWalker(failing).books("root"))
    }

    @Test
    fun `hidden entries such as trashed and pending copies are skipped`() {
        val hidden = FolderLister {
            when (it) {
                "root" -> listOf(
                    FolderEntry("t", ".trashed-1700000000-Old.pdf", "application/pdf", isFolder = false),
                    FolderEntry("p", ".pending-1700000000-New.pdf", "application/pdf", isFolder = false),
                    FolderEntry("h", ".thumbnails", null, isFolder = true),
                    FolderEntry("a", "Book.pdf", "application/pdf", isFolder = false),
                )

                else -> listOf(FolderEntry("x", "Inside.pdf", "application/pdf", isFolder = false))
            }
        }

        assertEquals(listOf("a"), FolderWalker(hidden).books("root"))
    }

    @Test
    fun `a walk is complete only when every folder was listed within the entry limit`() {
        assertTrue(FolderWalker(lister).walk("root").complete)
        assertFalse(FolderWalker(lister, WatchedFolderConfig(maxEntriesPerScan = 2)).walk("root").complete)
        val failingSub = FolderLister { if (it == "sub") throw IOException("gone") else tree[it].orEmpty() }
        val partial = FolderWalker(failingSub).walk("root")
        assertFalse(partial.complete)
        assertTrue(partial.rootListed)
        assertFalse(FolderWalker(FolderLister { throw IOException("revoked") }).walk("root").rootListed)
    }

    @Test
    fun `books in the library become seen and new ones are added`() = runBlocking {
        val result = scanAt(0, inLibrary = setOf("a")).scan(folder(), walkOf("a", "b", "c"), budget = 10) {
            if (it == "b") FolderAdd.ADDED else FolderAdd.KNOWN
        }

        assertEquals(setOf("a", "b", "c"), result.folder.seen)
        assertEquals(1, result.added)
        assertEquals(2, result.tried)
    }

    @Test
    fun `failures count toward the budget and wait, doubling, before another try`() = runBlocking {
        val failing: suspend (String) -> FolderAdd = { FolderAdd.FAILED }
        val first = scanAt(0).scan(folder(), walkOf("a", "b", "c"), budget = 2, add = failing)
        assertEquals(2, first.tried)
        assertEquals(RetryAt(1, HOUR), first.folder.retries["a"])
        assertTrue(first.folder.seen.isEmpty())

        val tooSoon = scanAt(HOUR - 1).scan(first.folder, walkOf("a", "b", "c"), 10, add = failing)
        assertEquals("only c is due", 1, tooSoon.tried)

        val again = scanAt(HOUR).scan(tooSoon.folder, walkOf("a", "b", "c"), 10, add = failing)
        assertEquals(RetryAt(2, 3 * HOUR), again.folder.retries["a"])

        val joined = scanAt(10 * HOUR).scan(again.folder, walkOf("a"), 10) { FolderAdd.ADDED }
        assertEquals(setOf("a"), joined.folder.seen)
        assertTrue(joined.folder.retries.isEmpty())
    }

    @Test
    fun `a complete walk forgets files that are gone, an incomplete one keeps them`() = runBlocking {
        val before = folder(seen = setOf("gone", "kept"), retries = mapOf("lost" to RetryAt(1, 0)))
        val complete = scanAt(0).scan(before, walkOf("kept"), 10) { FolderAdd.FAILED }
        val partial = scanAt(0).scan(before, walkOf("kept", complete = false), 10) { FolderAdd.FAILED }

        assertEquals(setOf("kept"), complete.folder.seen)
        assertTrue(complete.folder.retries.isEmpty())
        assertEquals(setOf("gone", "kept"), partial.folder.seen)
        assertEquals(setOf("lost"), partial.folder.retries.keys)
    }

    @Test
    fun `a folder that cannot be listed is marked unreachable`() = runBlocking {
        val walk = FolderWalk(emptyList(), complete = false, rootListed = false)

        assertFalse(scanAt(0).scan(folder(), walk, 10) { FolderAdd.ADDED }.folder.reachable)
    }

    @Test
    fun `folders and every field survive a save and load`() {
        val store = WatchedFolderStore(File(dir, "watched.txt"))
        val folders = listOf(
            WatchedFolder(
                "content://x/tree/1",
                "My books",
                setOf("content://x/tree/1/document/a"),
                retries = mapOf("content://x/tree/1/document/b c" to RetryAt(2, 99)),
            ),
            WatchedFolder("content://y/tree/2", "किताबें", watched = false, granted = false, reachable = false),
        )

        assertTrue(store.save(folders))
        assertEquals(folders, store.load())
    }

    @Test
    fun `a missing file is no folders, a foreign one is unreadable and never saved over`() {
        assertEquals(emptyList<WatchedFolder>(), WatchedFolderStore(File(dir, "none.txt")).load())
        File(dir, "other.txt").writeText("something else\nfolder x\n")
        assertNull(WatchedFolderStore(File(dir, "other.txt")).load())
    }

    private fun scanAt(nowMs: Long, inLibrary: Set<String> = emptySet()) =
        FolderScan(inLibrary, nowMs, WatchedFolderConfig(retryBaseMs = HOUR, retryMaxMs = 100 * HOUR))

    private fun folder(seen: Set<String> = emptySet(), retries: Map<String, RetryAt> = emptyMap()) =
        WatchedFolder("tree", "Books", seen, retries = retries)

    private fun walkOf(vararg uris: String, complete: Boolean = true) = FolderWalk(uris.toList(), complete, true)

    private companion object {
        const val HOUR = 3_600_000L
    }
}
