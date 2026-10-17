// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/backup/PendingRestoreTest.kt
// Purpose: A staged restore replaces the allowed files whole at the next start, and survives a crash part-way.

package io.github.skm0078.audora.playback.backup

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

    @After
    fun cleanUp() {
        root.deleteRecursively()
    }

    @Test
    fun `nothing happens without a ready marker, and a leftover staging folder is cleared`() {
        put("databases/audora.db", "live")
        stage("databases/audora.db", "half")

        assertFalse(restore.applyIfReady())
        assertEquals("live", File(root, "databases/audora.db").readText())
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
        assertTrue(restore.markReady())

        assertTrue(restore.applyIfReady())

        assertEquals("restored", File(root, "databases/audora.db").readText())
        assertFalse(File(root, "databases/audora.db-wal").exists())
        assertEquals("new", File(root, "files/voice-notes/new.m4a").readText())
        assertFalse(File(root, "files/voice-notes/old.m4a").exists())
        assertFalse("a file the backup lacks goes back to its default", File(root, "files/up-next.txt").exists())
        assertEquals("log", File(root, "files/logs/audora.log").readText())
        assertEquals("first run is not skipped", "done", File(root, "shared_prefs/onboarding.xml").readText())
        assertFalse(restore.staging.exists())
        assertFalse(restore.applyIfReady())
    }

    @Test
    fun `a ready marker without a staged database never empties the library`() {
        put("databases/audora.db", "live")
        put("files/up-next.txt", "queue")
        stage("files/up-next.txt", "other")
        assertTrue(restore.markReady())

        assertFalse(restore.applyIfReady())

        assertEquals("live", File(root, "databases/audora.db").readText())
        assertEquals("queue", File(root, "files/up-next.txt").readText())
        assertFalse(restore.applyIfReady())
    }

    @Test
    fun `clearing drops a ready restore`() {
        stage("databases/audora.db", "restored")
        assertTrue(restore.markReady())

        restore.clear()

        assertFalse(restore.applyIfReady())
        assertFalse(restore.staging.exists())
    }

    @Test
    fun `a restore cut short is applied whole at the next start`() {
        stage("databases/audora.db", "restored")
        stage("files/up-next.txt", "queue")
        assertTrue(restore.markReady())
        put("databases/audora.db", "half-applied")

        assertTrue(restore.applyIfReady())

        assertEquals("restored", File(root, "databases/audora.db").readText())
        assertEquals("queue", File(root, "files/up-next.txt").readText())
    }

    private fun put(path: String, text: String) = File(root, path).apply {
        parentFile.mkdirs()
        writeText(text)
    }

    private fun stage(path: String, text: String) = File(restore.staging, path).apply {
        parentFile.mkdirs()
        writeText(text)
    }
}
