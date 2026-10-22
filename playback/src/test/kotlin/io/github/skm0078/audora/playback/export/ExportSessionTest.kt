// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/export/ExportSessionTest.kt
// Purpose: A stopped audiobook export goes on from its saved chapters; saving again with other settings replaces them.

package io.github.skm0078.audora.playback.export

import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportSessionTest {
    private val root = Files.createTempDirectory("exports").toFile()
    private val records = ExportRecords(root)
    private val files = FakeFiles()
    private val chapters = listOf(AudioChapter(1, 0, 4), AudioChapter(2, 5, 9), AudioChapter(3, 10, 11))

    @After
    fun cleanUp() {
        root.deleteRecursively()
    }

    @Test
    fun `a first export renders every chapter`() {
        val session = session("key-a")

        assertEquals(chapters, session.remaining)
        assertTrue(session.kept.isEmpty())
    }

    @Test
    fun `a stopped export goes on after the chapters it saved`() {
        val first = session("key-a")
        first.onChapterDone(saved(chapters[0], "content://media/1"))

        val second = session("key-a")

        assertEquals(chapters.drop(1), second.remaining)
        assertEquals(listOf(ExportedChapter(chapters[0], "01.m4a", 1_000)), second.kept)
        assertEquals(5, second.keptPages)
        assertTrue(files.deleted.isEmpty())
    }

    @Test
    fun `a saved chapter whose file is gone is rendered again`() {
        session("key-a").onChapterDone(saved(chapters[0], "content://media/1"))
        files.existing.remove("content://media/1")

        assertEquals(chapters, session("key-a").remaining)
    }

    @Test
    fun `other settings replace the earlier files`() {
        val first = session("key-a")
        first.onChapterDone(saved(chapters[0], "content://media/1"))
        first.onChapterDone(saved(chapters[1], "content://media/2"))

        val second = session("key-b")

        assertEquals(chapters, second.remaining)
        assertEquals(listOf("content://media/1", "content://media/2"), files.deleted)
    }

    @Test
    fun `a complete export saved again is replaced, not doubled`() {
        val first = session("key-a")
        chapters.forEachIndexed { i, chapter -> first.onChapterDone(saved(chapter, "content://media/$i")) }
        first.finish(ExportSummary(emptyList(), 0, 0, 0))

        val again = session("key-a")

        assertEquals(chapters, again.remaining)
        assertEquals(3, files.deleted.size)
    }

    @Test
    fun `the summary covers kept and new chapters in order`() {
        session("key-a").onChapterDone(saved(chapters[0], "content://media/1"))
        val second = session("key-a")
        val newer = saved(chapters[1], "content://media/2")

        val summary = second.finish(ExportSummary(listOf(newer), 1, 0, 0))

        assertEquals(listOf(1, 2), summary.chapters.map { it.chapter.number })
        assertEquals(1, summary.skippedSentences)
    }

    @Test
    fun `a chapter without a location is not recorded`() {
        session("key-a").onChapterDone(ExportedChapter(chapters[0], "01.m4a", 1_000))

        assertEquals(chapters, session("key-a").remaining)
    }

    @Test
    fun `a record reads back with its saved name, a damaged one is ignored`() {
        records.save("book", ExportRecord("k", listOf(SavedChapter(1, "01 Pages 1-5 (1).m4a", 9, "content://m/1"))))
        assertEquals("01 Pages 1-5 (1).m4a", records.load("book")?.chapters?.single()?.name)

        root.listFiles()!!.single().writeText("audora-audiobook-export 1\nkey k\nchapter x\n")
        assertNull(records.load("book"))
    }

    @Test
    fun `a file created but never finished is deleted by the next run, a finished one is not`() {
        val first = session("key-a")
        first.onFileCreated("content://media/01.m4a")
        first.onFileCreated("content://media/02.m4a")
        first.onChapterDone(saved(chapters[0], "content://media/01.m4a"))
        this.files.existing.add("content://media/02.m4a")

        val second = session("key-a")

        assertEquals(listOf("content://media/02.m4a"), this.files.deleted)
        assertEquals(chapters.drop(1), second.remaining)
        assertEquals(emptyList<String>(), records.load("book")?.pending)
    }

    @Test
    fun `a partial file that could not be deleted stays pending for the next run`() {
        session("key-a").onFileCreated("content://media/02.m4a")
        files.existing.add("content://media/02.m4a")
        files.undeletable.add("content://media/02.m4a")

        session("key-a")
        assertEquals(listOf("content://media/02.m4a"), records.load("book")?.pending)

        files.undeletable.clear()
        session("key-a")
        assertEquals(emptyList<String>(), records.load("book")?.pending)
        assertTrue("content://media/02.m4a" !in files.existing)
    }

    @Test
    fun `pending files read back from the record`() {
        records.save("book", ExportRecord("k", emptyList(), pending = listOf("content://m/9")))

        assertEquals(listOf("content://m/9"), records.load("book")?.pending)
    }

    @Test
    fun `the key changes with the chapter plan and the folder`() {
        val key = exportKey("sig", "Audiobooks/A/", chapters)
        assertNotEquals(key, exportKey("sig", "Audiobooks/B/", chapters))
        assertNotEquals(key, exportKey("sig", "Audiobooks/A/", chapters.dropLast(1)))
        assertEquals(key, exportKey("sig", "Audiobooks/A/", chapters))
    }

    private fun session(key: String) = ExportSession(records, files, "book", key, chapters)

    private fun saved(chapter: AudioChapter, location: String): ExportedChapter {
        files.existing.add(location)
        return ExportedChapter(chapter, "%02d.m4a".format(chapter.number), 1_000, location)
    }

    private class FakeFiles : SavedFiles {
        val existing = HashSet<String>()
        val deleted = ArrayList<String>()
        val undeletable = HashSet<String>()

        override fun exists(location: String) = location in existing

        override fun delete(location: String): Boolean {
            if (location in undeletable) return false
            deleted.add(location)
            existing.remove(location)
            return true
        }
    }
}
