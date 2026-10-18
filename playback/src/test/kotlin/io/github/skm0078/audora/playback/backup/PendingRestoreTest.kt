// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/backup/PendingRestoreTest.kt
// Purpose: A staged restore replaces whole units by renames, rolls back on failure and never loses a unit.

package io.github.skm0078.audora.playback.backup

import io.github.skm0078.audora.playback.backup.PendingRestore.Result
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

        assertEquals(Result.NONE, restore.applyIfReady().result)
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

        assertEquals(Result.APPLIED, restore.applyIfReady().result)
        restore.cleanUp()

        assertEquals("restored", read("databases/audora.db"))
        assertFalse(File(root, "databases/audora.db-wal").exists())
        assertEquals("new", read("files/voice-notes/new.m4a"))
        assertFalse(File(root, "files/voice-notes/old.m4a").exists())
        assertFalse("a file the writer knew but lacked goes to its default", File(root, "files/up-next.txt").exists())
        assertEquals("log", read("files/logs/audora.log"))
        assertEquals("first run is not skipped", "done", read("shared_prefs/onboarding.xml"))
        assertEquals("only the live tree is left", listOf("databases", "files", "shared_prefs"), leftovers())
        assertEquals(Result.NONE, restore.applyIfReady().result)
    }

    @Test
    fun `a file the writing app did not know about is kept`() {
        put("databases/audora.db", "live")
        put("files/characters/1.txt", "names")
        stage("databases/audora.db", "restored")
        assertTrue(restore.markReady(everything - "files/characters"))

        assertEquals(Result.APPLIED, restore.applyIfReady().result)

        assertEquals("names", read("files/characters/1.txt"))
    }

    @Test
    fun `without the writer's list only what the backup holds is replaced`() {
        put("databases/audora.db", "live")
        put("files/up-next.txt", "queue")
        stage("databases/audora.db", "restored")
        assertTrue(restore.markReady(null))

        assertEquals(Result.APPLIED, restore.applyIfReady().result)

        assertEquals("restored", read("databases/audora.db"))
        assertEquals("queue", read("files/up-next.txt"))
    }

    @Test
    fun `a ready marker without a staged database never empties the library`() {
        put("databases/audora.db", "live")
        put("files/up-next.txt", "queue")
        stage("files/up-next.txt", "other")
        assertTrue(restore.markReady(everything))

        assertEquals(Result.NONE, restore.applyIfReady().result)

        assertEquals("live", read("databases/audora.db"))
        assertEquals("queue", read("files/up-next.txt"))
        assertEquals(Result.NONE, restore.applyIfReady().result)
    }

    @Test
    fun `clearing drops a ready restore`() {
        stage("databases/audora.db", "restored")
        assertTrue(restore.markReady(everything))

        assertTrue(restore.clear())

        assertEquals(Result.NONE, restore.applyIfReady().result)
        assertFalse(restore.staging.exists())
    }

    @Test
    fun `a failure while moving in rolls every unit back, folders, journals and absent ones included`() {
        put("databases/audora.db", "live")
        put("databases/audora.db-wal", "live wal")
        put("files/voice-notes/1.m4a", "voice")
        stage("databases/audora.db", "restored")
        stage("files/up-next.txt", "a queue the new phone never had")
        stage("files/voice-notes/2.m4a", "other voice")
        stage("no_backup/voices/managed/voice-speeds.properties", "speeds")
        assertTrue(restore.markReady(everything))
        // A file where a folder must be: the speeds cannot move in, after everything else moved aside.
        put("no_backup/voices", "in the way")

        val run = restore.applyIfReady()

        assertEquals(Result.FAILED, run.result)
        assertEquals("live", read("databases/audora.db"))
        assertEquals("live wal", read("databases/audora.db-wal"))
        assertEquals("voice", read("files/voice-notes/1.m4a"))
        assertFalse(File(root, "files/voice-notes/2.m4a").exists())
        assertFalse("absent before, absent after", File(root, "files/up-next.txt").exists())
        assertEquals(Result.NONE, restore.applyIfReady().result)
    }

    @Test
    fun `a unit that cannot go back is kept with the marker and put back at the next start`() {
        put("databases/audora.db", "live")
        put("files/up-next.txt", "queue")
        stage("databases/audora.db", "restored")
        stage("files/up-next.txt", "other queue")
        assertTrue(restore.markReady(everything))
        startApplying(movedAside = listOf("databases/audora.db", "files/up-next.txt"))
        assertTrue(File(root, "files").delete())
        put("files", "a file where the files folder must be")

        val first = restore.applyIfReady()

        assertEquals(Result.INCOMPLETE, first.result)
        assertEquals("live", read("databases/audora.db"))
        assertTrue(restore.unfinished)
        assertFalse("nothing new is staged meanwhile", restore.clear())
        assertFalse(restore.markReady(everything))

        File(root, "files").delete()
        assertEquals(Result.FAILED, restore.applyIfReady().result)
        assertEquals("queue", read("files/up-next.txt"))
        assertFalse(restore.unfinished)
    }

    @Test
    fun `an apply cut short by a crash is rolled back at the next start, even mid roll-back`() {
        put("databases/audora.db", "live")
        put("files/up-next.txt", "queue")
        stage("databases/audora.db", "restored")
        stage("files/up-next.txt", "other queue")
        assertTrue(restore.markReady(everything))
        startApplying(movedAside = listOf("databases/audora.db", "files/up-next.txt"))
        // A first roll-back put the database back, then the process died again.
        move(aside("databases/audora.db"), "databases/audora.db")

        assertEquals(Result.FAILED, restore.applyIfReady().result)

        assertEquals("live", read("databases/audora.db"))
        assertEquals("queue", read("files/up-next.txt"))
        assertEquals(Result.NONE, restore.applyIfReady().result)
    }

    @Test
    fun `a torn marker is read as an apply cut short`() {
        put("databases/audora.db", "live")
        stage("databases/audora.db", "restored")
        assertTrue(restore.markReady(everything))
        startApplying(movedAside = listOf("databases/audora.db"))
        File(root, MARKER).writeText("\u0000\u0000")

        assertEquals(Result.FAILED, restore.applyIfReady().result)

        assertEquals("live", read("databases/audora.db"))
    }

    @Test
    fun `a restore that crashed after its commit point stands and only tidies up`() {
        put("databases/audora.db", "live")
        stage("databases/audora.db", "restored")
        assertTrue(restore.markReady(everything))
        startApplying(movedAside = listOf("databases/audora.db"))
        move("no_backup/restore-pending/databases/audora.db", "databases/audora.db")
        val lines = File(root, MARKER).readLines()
        File(root, MARKER).writeText((listOf("committed") + lines.drop(1)).joinToString("\n"))

        assertEquals(Result.APPLIED, restore.applyIfReady().result)
        restore.cleanUp()

        assertEquals("restored", read("databases/audora.db"))
        assertEquals(listOf("databases"), leftovers())
    }

    @Test
    fun `an aside folder left by another attempt is never put back over current data`() {
        put("databases/audora.db", "live")
        put("files/up-next.txt", "current queue")
        put("no_backup/restore-aside/old-attempt/files/up-next.txt", "stale queue")
        stage("databases/audora.db", "restored")
        stage("no_backup/voices/managed/voice-speeds.properties", "speeds")
        assertTrue(restore.markReady(everything))
        put("no_backup/voices", "in the way")

        assertEquals(Result.FAILED, restore.applyIfReady().result)
        restore.cleanUp()

        assertEquals("current queue", read("files/up-next.txt"))
        assertFalse(File(root, "no_backup/restore-aside").exists())
    }

    /** As a killed process leaves it: marked applying, with [movedAside] moved to this attempt's aside folder. */
    private fun startApplying(movedAside: List<String>) {
        val lines = File(root, MARKER).readLines()
        File(root, MARKER).writeText((listOf("applying") + lines.drop(1)).joinToString("\n"))
        movedAside.forEach { move(it, aside(it)) }
    }

    private fun aside(unit: String): String = "no_backup/restore-aside/${File(root, MARKER).readLines()[1]}/$unit"

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

    private companion object {
        const val MARKER = "no_backup/restore-pending.ready"
    }
}
