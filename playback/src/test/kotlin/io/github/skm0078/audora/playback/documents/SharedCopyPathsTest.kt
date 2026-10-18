// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/documents/SharedCopyPathsTest.kt
// Purpose: A shared copy is found from a path saved under another files folder, and only unused old copies are pruned.

package io.github.skm0078.audora.playback.documents

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SharedCopyPathsTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `a copy saved under another files folder is found by its last three segments`() {
        val paths = listOf(
            "/data/user/0/io.github.skm0078.audora/files/shared-pdfs/1b2c/Novel.pdf",
            "/data/user/10/io.github.skm0078.audora.debug/files/shared-pdfs/1b2c/Novel.pdf",
        )

        paths.forEach { assertEquals("1b2c" to "Novel.pdf", copyPathOf(it)) }
    }

    @Test
    fun `any other path, or a folder or name that is not plain, is not a copy`() {
        listOf(
            "/sdcard/Download/Novel.pdf",
            "/files/shared-pdfs/Novel.pdf",
            "shared-pdfs/1b2c",
            "/files/shared-pdfs/../Novel.pdf",
            "/files/shared-pdfs/1b2c/..",
            "/files/shared-pdfs/./Novel.pdf",
            "/files/shared-pdfs//Novel.pdf",
            "/files/shared-pdfs/1b2c/a\\b.pdf",
            "/files/other/1b2c/Novel.pdf",
        ).forEach { assertNull(it, copyPathOf(it)) }
    }

    @Test
    fun `only folders no book uses and not written since the cutoff are stray`() {
        val dir = temp.newFolder(SHARED_COPIES_DIR)
        val used = copy(dir, "used", writtenAt = OLD)
        val unused = copy(dir, "unused", writtenAt = OLD)
        copy(dir, "fresh", writtenAt = CUTOFF + 1)
        val empty = File(dir, "empty").apply { mkdirs() }.also { it.setLastModified(OLD) }
        val half = copy(dir, "half", writtenAt = OLD).also { File(it, "more.pdf").writeText("x") }

        val stray = strayCopyFolders(dir, kept = setOf(used.name), cutoffMillis = CUTOFF)

        assertEquals(setOf(unused, empty), stray.toSet())
        assertEquals(false, half in stray) // A file written inside it after the cutoff keeps the folder.
    }

    @Test
    fun `a missing copies folder has nothing to prune`() {
        assertEquals(emptyList<File>(), strayCopyFolders(File(temp.root, "none"), emptySet(), CUTOFF))
    }

    private fun copy(dir: File, folder: String, writtenAt: Long): File {
        val copyDir = File(dir, folder).apply { mkdirs() }
        File(copyDir, "Book.pdf").apply { writeText("%PDF") }.setLastModified(writtenAt)
        copyDir.setLastModified(writtenAt)
        return copyDir
    }

    private companion object {
        const val CUTOFF = 1_700_000_000_000L
        const val OLD = CUTOFF - 60_000L
    }
}
