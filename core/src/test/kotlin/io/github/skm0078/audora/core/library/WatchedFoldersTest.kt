// File: core/src/test/kotlin/io/github/skm0078/audora/core/library/WatchedFoldersTest.kt
// Purpose: The folder walk finds books within its limits; watched folders survive a save and load.

package io.github.skm0078.audora.core.library

import io.github.skm0078.audora.core.config.WatchedFolderConfig
import java.io.File
import java.io.IOException
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
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
    fun `folders, names and seen books survive a save and load`() {
        val store = WatchedFolderStore(File(dir, "watched.txt"))
        val folders = listOf(
            WatchedFolder("content://x/tree/1", "My books", setOf("content://x/tree/1/document/a")),
            WatchedFolder("content://y/tree/2", "किताबें"),
        )

        assertTrue(store.save(folders))
        assertEquals(folders, store.load())
    }

    @Test
    fun `a missing or foreign file loads as no folders`() {
        assertEquals(emptyList<WatchedFolder>(), WatchedFolderStore(File(dir, "none.txt")).load())
        File(dir, "other.txt").writeText("something else\nfolder x\n")
        assertEquals(emptyList<WatchedFolder>(), WatchedFolderStore(File(dir, "other.txt")).load())
    }
}
