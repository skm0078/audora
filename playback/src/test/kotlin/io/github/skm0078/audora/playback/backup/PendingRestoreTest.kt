// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/backup/PendingRestoreTest.kt
// Purpose: A staged restore replaces whole units by renames, rolls back on failure and never repeats a failed one.

package io.github.skm0078.audora.playback.backup

import java.io.File
import java.io.IOException
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingRestoreTest {
    private val root = Files.createTempDirectory("restore").toFile()
    private val restore = PendingRestore(root)
    private val everything = BackupContents.DEFAULT.units()

    @After
    fun cleanUp() {
        root.deleteRecursively()
    }

    @Test
    fun `nothing happens without a ready marker, and a leftover staging folder is cleared`() {
        put("databases/audora.db", "live")
        stage("databases/audora.db", "half")

        assertEquals(PendingRestore.Result.NONE, restore.applyIfReady())
        assertEquals("live", read("databases/audora.db"))
        assertFalse(restore.staging.exists())
    }

    @Test
    fun `a ready restore replaces the database, drops its journals and replaces whole folders`() {
        put("databases/audora.db", "live")
        put("databases/audora.db-wal", "stale wal")
        put("files/voice-notes/old.m4a", "old")
        put("files/up-next.txt", "old queue")
        put("files/logs/audora.log", "log")
        put("shared_prefs/onboarding.xml", "done")
        stage("databases/audora.db", "restored")
        stage("files/voice-notes/new.m4a", "new")
        assertTrue(restore.markReady(everything))

        assertEquals(PendingRestore.Result.APPLIED, restore.applyIfReady())
        restore.cleanUp()

        assertEquals("restored", read("databases/audora.db"))
        assertFalse(File(root, "databases/audora.db-wal").exists())
        assertEquals("new", read("files/voice-notes/new.m4a"))
        assertFalse(File(root, "files/voice-notes/old.m4a").exists())
        assertFalse("a file the writer knew but lacked goes to its default", File(root, "files/up-next.txt").exists())
        assertEquals("log", read("files/logs/audora.log"))
        assertEquals("first run is not skipped", "done", read("shared_prefs/onboarding.xml"))
        assertEquals("only the live tree is left", listOf("databases", "files", "shared_prefs"), leftovers())
        assertEquals(PendingRestore.Result.NONE, restore.applyIfReady())
    }

    @Test
    fun `a file the writing app did not know about is kept`() {
        put("databases/audora.db", "live")
        put("files/characters/1.txt", "names")
        stage("databases/audora.db", "restored")
        assertTrue(restore.markReady(everything - "files/characters"))

        assertEquals(PendingRestore.Result.APPLIED, restore.applyIfReady())

        assertEquals("names", read("files/characters/1.txt"))
    }

    @Test
    fun `without the writer's list only what the backup holds is replaced`() {
        put("databases/audora.db", "live")
        put("files/up-next.txt", "queue")
        stage("databases/audora.db", "restored")
        assertTrue(restore.markReady(null))

        assertEquals(PendingRestore.Result.APPLIED, restore.applyIfReady())

        assertEquals("restored", read("databases/audora.db"))
        assertEquals("queue", read("files/up-next.txt"))
    }

    @Test
    fun `a ready marker without a staged database never empties the library`() {
        put("databases/audora.db", "live")
        put("files/up-next.txt", "queue")
        stage("files/up-next.txt", "other")
        assertTrue(restore.markReady(everything))

        assertEquals(PendingRestore.Result.NONE, restore.applyIfReady())

        assertEquals("live", read("databases/audora.db"))
        assertEquals("queue", read("files/up-next.txt"))
        assertEquals(PendingRestore.Result.NONE, restore.applyIfReady())
    }

    @Test
    fun `clearing drops a ready restore`() {
        stage("databases/audora.db", "restored")
        assertTrue(restore.markReady(everything))

        restore.clear()

        assertEquals(PendingRestore.Result.NONE, restore.applyIfReady())
        assertFalse(restore.staging.exists())
    }

    @Test
    fun `a failure part-way rolls every unit back and the restore is not tried again`() {
        put("databases/audora.db", "live")
        put("files/up-next.txt", "queue")
        put("files/voice-notes/1.m4a", "voice")
        stage("databases/audora.db", "restored")
        stage("files/up-next.txt", "other queue")
        assertTrue(restore.markReady(everything))
        // A file where the restore needs a folder: the database moves aside, then the next unit fails.
        File(root, "no_backup/restore-aside").mkdirs()
        File(root, "no_backup/restore-aside/files").writeText("in the way")

        assertThrows(IOException::class.java) { restore.applyIfReady() }

        assertEquals("live", read("databases/audora.db"))
        assertEquals("queue", read("files/up-next.txt"))
        assertEquals("voice", read("files/voice-notes/1.m4a"))
        assertEquals(PendingRestore.Result.NONE, restore.applyIfReady())
    }

    @Test
    fun `an apply cut short by a crash is rolled back at the next start`() {
        put("databases/audora.db", "live")
        put("files/up-next.txt", "queue")
        stage("databases/audora.db", "restored")
        assertTrue(restore.markReady(everything))
        // As a killed process leaves it: marked applying, the database swapped, Up next moved aside.
        File(root, "no_backup/restore-pending.ready").writeText(
            "applying\n" + (everything + BackupContents.DATABASE_SIDE_FILES.map { "databases/audora.db$it" })
                .joinToString("\n"),
        )
        move("databases/audora.db", "no_backup/restore-aside/databases/audora.db")
        move("no_backup/restore-pending/databases/audora.db", "databases/audora.db")
        move("files/up-next.txt", "no_backup/restore-aside/files/up-next.txt")

        assertThrows(IOException::class.java) { restore.applyIfReady() }

        assertEquals("live", read("databases/audora.db"))
        assertEquals("queue", read("files/up-next.txt"))
        assertEquals(PendingRestore.Result.NONE, restore.applyIfReady())
    }

    private fun put(path: String, text: String) = File(root, path).apply {
        parentFile.mkdirs()
        writeText(text)
    }

    private fun stage(path: String, text: String) = File(restore.staging, path).apply {
        parentFile.mkdirs()
        writeText(text)
    }

    private fun read(path: String) = File(root, path).readText()

    private fun move(from: String, to: String) {
        File(root, to).parentFile.mkdirs()
        assertTrue(File(root, from).renameTo(File(root, to)))
    }

    private fun leftovers(): List<String> = root.listFiles().orEmpty()
        .filter { it.name != "no_backup" || it.list()?.isNotEmpty() == true }
        .map { it.name }
        .sorted()
}
