// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/export/AudiobookLeftoversTest.kt
// Purpose: An unfinished audiobook's files are kept for a while, then deleted with its record; a finished one's never
//          are, nor anything Made lists; a gone book's go at once.

package io.github.skm0078.audora.playback.export

import io.github.skm0078.audora.core.config.LeftoverConfig
import io.github.skm0078.audora.core.files.LeftoverKind
import io.github.skm0078.audora.core.files.LeftoverPolicy
import io.github.skm0078.audora.core.files.LeftoverTally
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudiobookLeftoversTest {
    private val root = Files.createTempDirectory("exports").toFile()
    private val records = ExportRecords(root)
    private val files = FakeFiles()
    private val leftovers = AudiobookLeftovers(records, files)
    private val library = setOf(ExportRecords.hashOf(BOOK), ExportRecords.hashOf(OTHER))
    private val now = 100L * DAY
    private val policy = LeftoverPolicy(LeftoverConfig(keepMs = 7 * DAY), now)

    @After
    fun cleanUp() {
        root.deleteRecursively()
    }

    private fun stored(book: String, record: ExportRecord, ageDays: Long) {
        records.save(book, record)
        (record.chapters.map { it.location } + record.pending).forEach { files.sizes[it] = SIZE }
        File(root, ExportRecords.hashOf(book) + ".txt").setLastModified(now - ageDays * DAY)
    }

    private fun chapter(n: Int) = SavedChapter(n, "%02d.m4a".format(n), 1_000L, "content://media/$BOOK/$n")

    private val unfinished = ExportRecord("key", listOf(chapter(1), chapter(2)), pending = listOf("content://media/p"))

    @Test
    fun `an unfinished export kept less than a week stays, for saving it again to go on`() {
        stored(BOOK, unfinished, ageDays = 3)

        assertEquals(LeftoverTally.NONE, leftovers.clean(policy, library, emptySet(), asked = false))
        assertTrue(files.deleted.isEmpty())
        assertNotNull(records.load(BOOK))
        assertEquals(LeftoverTally.of(LeftoverKind.AUDIOBOOK, 1, 3 * SIZE), leftovers.measure(emptySet()))
    }

    @Test
    fun `an unfinished export older than a week loses its chapters, half files and record`() {
        stored(BOOK, unfinished, ageDays = 8)

        val cleaned = leftovers.clean(policy, library, emptySet(), asked = false)

        assertEquals(LeftoverTally.of(LeftoverKind.AUDIOBOOK, 1, 3 * SIZE), cleaned)
        val expected = setOf("content://media/$BOOK/1", "content://media/$BOOK/2", "content://media/p")
        assertEquals(expected, files.deleted.toSet())
        assertNull(records.load(BOOK))
    }

    @Test
    fun `a book gone from the library loses its unfinished export at once`() {
        stored("gone", unfinished.copy(chapters = listOf(chapter(1))), ageDays = 0)

        assertEquals(1, leftovers.clean(policy, library, emptySet(), asked = false).count(LeftoverKind.AUDIOBOOK))
        assertNull(records.load("gone"))
    }

    @Test
    fun `a finished audiobook's chapters are never deleted, only its stray half files`() {
        val done = ExportRecord("key", listOf(chapter(1)), complete = true, pending = listOf("content://media/p"))
        stored(BOOK, done, ageDays = 30)

        leftovers.clean(policy, emptySet(), emptySet(), asked = true)

        assertEquals(listOf("content://media/p"), files.deleted)
        assertEquals(listOf(chapter(1)), records.load(BOOK)?.chapters)
        assertEquals(emptyList<String>(), records.load(BOOK)?.pending)
    }

    @Test
    fun `nothing Made lists is ever deleted, whatever a record says`() {
        stored(BOOK, unfinished, ageDays = 30)
        val made = setOf("content://media/$BOOK/1")

        leftovers.clean(policy, library, made, asked = true)

        assertTrue("content://media/$BOOK/1" !in files.deleted)
        assertEquals(0L, leftovers.bytesFor(BOOK, made))
    }

    @Test
    fun `a file that will not delete stays pending for the next export to retry`() {
        stored(BOOK, unfinished, ageDays = 30)
        files.undeletable += "content://media/$BOOK/2"

        val cleaned = leftovers.clean(policy, library, emptySet(), asked = false)

        assertEquals(LeftoverTally.of(LeftoverKind.AUDIOBOOK, 1, 2 * SIZE), cleaned)
        val left = records.load(BOOK)
        assertEquals(emptyList<SavedChapter>(), left?.chapters)
        assertEquals(listOf("content://media/$BOOK/2"), left?.pending)
    }

    @Test
    fun `the card's delete takes only that book's unfinished export`() {
        stored(BOOK, unfinished, ageDays = 0)
        stored(OTHER, ExportRecord("key", listOf(SavedChapter(1, "01.m4a", 1L, "content://media/o/1"))), ageDays = 0)

        assertEquals(3 * SIZE, leftovers.bytesFor(BOOK, emptySet()))
        assertEquals(1, leftovers.discard(BOOK, emptySet()).count(LeftoverKind.AUDIOBOOK))
        assertNull(records.load(BOOK))
        assertNotNull(records.load(OTHER))
        assertEquals(0L, leftovers.bytesFor(BOOK, emptySet()))
    }

    @Test
    fun `an unreadable record and a temp file a kill left are dropped`() {
        File(root, ExportRecords.hashOf("gone") + ".txt").writeText("not a record")
        val temp = File(root, "abc.txt.partial").apply { writeText("half") }

        assertEquals(LeftoverTally.NONE, leftovers.clean(policy, library, emptySet(), asked = false))
        assertTrue(records.stored().isEmpty())
        assertTrue(!temp.exists())
    }

    private class FakeFiles : SavedFiles {
        val sizes = HashMap<String, Long>()
        val deleted = ArrayList<String>()
        val undeletable = HashSet<String>()

        override fun exists(location: String) = location in sizes

        override fun delete(location: String): Boolean {
            if (location in undeletable) return false
            deleted.add(location)
            sizes.remove(location)
            return true
        }

        override fun sizeOf(location: String) = sizes[location] ?: 0L
    }

    private companion object {
        const val BOOK = "book"
        const val OTHER = "other"
        const val SIZE = 100L
        const val DAY = 24L * 60 * 60 * 1000
    }
}
