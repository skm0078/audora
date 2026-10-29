// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/backup/BackupArchiveTest.kt
// Purpose: A backup round-trips its allowed files, and a picked file that is foreign, newer or unsafe is refused.

package io.github.skm0078.audora.playback.backup

import io.github.skm0078.audora.core.config.BackupConfig
import io.github.skm0078.audora.core.library.ShelfLookStore
import io.github.skm0078.audora.core.result.Outcome
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupArchiveTest {
    private val temp = Files.createTempDirectory("backup").toFile()
    private val root = File(temp, "data")
    private val staging = File(temp, "staging")
    private val snapshot = File(temp, "snapshot.db").apply { writeText("db") }
    private val archive = BackupArchive()
    private val manifest = BackupManifest(BackupManifest.CURRENT_FORMAT, DB_VERSION, 1L)

    @After
    fun cleanUp() {
        temp.deleteRecursively()
    }

    @Test
    fun `a backup holds the snapshot and allowed files only, and unpacks them`() {
        put("files/up-next.txt", "queue")
        put("files/voice-notes/1.m4a", "voice")
        put("files/logs/audora.log", "log")
        put("databases/audora.db", "live")
        put("no_backup/voices/managed/reading-voices.properties", "voice choice")

        val bytes = ByteArrayOutputStream().also { archive.write(it, manifest, snapshot, root) }.toByteArray()
        val result = archive.stage(bytes.inputStream(), staging, DB_VERSION)

        assertEquals("the total size travels", Outcome.Success(manifest.copy(bytes = 12L)), result)
        assertEquals("db", File(staging, "databases/audora.db").readText())
        assertEquals("queue", File(staging, "files/up-next.txt").readText())
        assertEquals("voice", File(staging, "files/voice-notes/1.m4a").readText())
        assertFalse(File(staging, "files/logs/audora.log").exists())
        assertFalse(File(staging, "no_backup/voices/managed/reading-voices.properties").exists())
    }

    @Test
    fun `a backup from a newer database or format is refused before unpacking`() {
        val newerDb = zipOf(BackupManifest(1, DB_VERSION + 1, 1L), "databases/audora.db" to "db")
        val newerFormat = zipOf(BackupManifest(BackupManifest.CURRENT_FORMAT + 1, 1, 1L), "databases/audora.db" to "db")

        assertEquals(
            Outcome.Failure(BackupError.FROM_NEWER_APP),
            archive.stage(newerDb.inputStream(), staging, DB_VERSION),
        )
        assertEquals(
            Outcome.Failure(BackupError.FROM_NEWER_APP),
            archive.stage(newerFormat.inputStream(), staging, DB_VERSION),
        )
        assertFalse(staging.exists())
    }

    @Test
    fun `an older database version is accepted, Room migrates it`() {
        val older = zipOf(BackupManifest(1, DB_VERSION - 1, 1L), "databases/audora.db" to "db")

        assertTrue(archive.stage(older.inputStream(), staging, DB_VERSION) is Outcome.Success)
    }

    @Test
    fun `a zip without a manifest first or without a database is not a backup`() {
        val plain = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip -> entry(zip, "files/up-next.txt", "queue") }
        }.toByteArray()
        val noDatabase = zipOf(manifest, "files/up-next.txt" to "queue")

        assertEquals(Outcome.Failure(BackupError.NOT_A_BACKUP), archive.stage(plain.inputStream(), staging, DB_VERSION))
        assertEquals(
            Outcome.Failure(BackupError.NOT_A_BACKUP),
            archive.stage(noDatabase.inputStream(), staging, DB_VERSION),
        )
        assertEquals(
            Outcome.Failure(BackupError.NOT_A_BACKUP),
            archive.stage("not a zip".byteInputStream(), staging, DB_VERSION),
        )
        assertFalse(staging.exists())
    }

    @Test
    fun `entries that climb out or are not allowed are skipped`() {
        val crafted = zipOf(
            manifest,
            "databases/audora.db" to "db",
            "files/voice-notes/../../evil.txt" to "x",
            "../evil.txt" to "x",
            "files/up-next.txt/../../shared_prefs/onboarding.xml" to "x",
            "shared_prefs/onboarding.xml" to "x",
        )

        assertTrue(archive.stage(crafted.inputStream(), staging, DB_VERSION) is Outcome.Success)
        assertEquals(
            listOf("databases/audora.db"),
            staging.walkTopDown().filter { it.isFile }.map {
                it.relativeTo(staging).invariantSeparatorsPath
            }.toList(),
        )
        assertFalse(File(temp, "evil.txt").exists())
    }

    @Test
    fun `an entry over the limit is refused by the bytes it unpacks`() {
        val small = BackupArchive(config = BackupConfig(maxEntryBytes = 4, maxTotalBytes = 8))
        val big = zipOf(manifest, "databases/audora.db" to "db", "files/up-next.txt" to "too long")
        val many = zipOf(
            manifest,
            "databases/audora.db" to "db",
            "files/voice-notes/a" to "1234",
            "files/voice-notes/b" to "1234",
        )

        assertEquals(Outcome.Failure(BackupError.TOO_LARGE), small.stage(big.inputStream(), staging, DB_VERSION))
        assertEquals(Outcome.Failure(BackupError.TOO_LARGE), small.stage(many.inputStream(), staging, DB_VERSION))
        assertFalse(staging.exists())
    }

    @Test
    fun `a malformed entry name or manifest escape is not a backup, and too little space is said so`() {
        val badName = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out, Charsets.ISO_8859_1).use { zip ->
                zip.putNextEntry(ZipEntry(BackupManifest.ENTRY))
                zip.write(manifest.encode())
                zip.closeEntry()
                entry(zip, "files/voice-notes/\u00e9.m4a", "x")
            }
        }.toByteArray()
        val badManifest = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip -> entry(zip, BackupManifest.ENTRY, "format=\\uZZZZ\n") }
        }.toByteArray()
        // Streamed entries claim no size: free space is checked as bytes arrive.
        val full = BackupArchive(config = BackupConfig(minFreeBytes = Long.MAX_VALUE / 2, spaceCheckBytes = 1))
        val valid = zipOf(manifest, "databases/audora.db" to "db")

        assertEquals(
            Outcome.Failure(BackupError.NOT_A_BACKUP),
            archive.stage(badName.inputStream(), staging, DB_VERSION),
        )
        assertEquals(
            Outcome.Failure(BackupError.NOT_A_BACKUP),
            archive.stage(badManifest.inputStream(), staging, DB_VERSION),
        )
        assertEquals(Outcome.Failure(BackupError.NO_SPACE), full.stage(valid.inputStream(), staging, DB_VERSION))
        assertFalse(staging.exists())
    }

    @Test
    fun `the writer's list of units travels in the manifest`() {
        val listed = manifest.copy(units = BackupContents.DEFAULT.units())
        val bytes = ByteArrayOutputStream().also { archive.write(it, listed, snapshot, root) }.toByteArray()

        assertEquals(Outcome.Success(listed.copy(bytes = 2L)), archive.stage(bytes.inputStream(), staging, DB_VERSION))
    }

    @Test
    fun `a backup bigger than the free space is refused before anything is unpacked`() {
        val huge = zipOf(manifest.copy(bytes = Long.MAX_VALUE / 4), "databases/audora.db" to "db")

        assertEquals(Outcome.Failure(BackupError.NO_SPACE), archive.stage(huge.inputStream(), staging, DB_VERSION))
        assertFalse(staging.exists())
    }

    @Test
    fun `files and folders saved under an earlier name come back under the current one`() {
        val renamed = BackupContents.DEFAULT.copy(
            renamed = mapOf("files/queue.txt" to "files/up-next.txt", "files/notes" to "files/voice-notes"),
        )
        val old = zipOf(manifest, "databases/audora.db" to "db", "files/queue.txt" to "q", "files/notes/1.m4a" to "v")

        assertTrue(BackupArchive(renamed).stage(old.inputStream(), staging, DB_VERSION) is Outcome.Success)

        assertEquals("q", File(staging, "files/up-next.txt").readText())
        assertEquals("v", File(staging, "files/voice-notes/1.m4a").readText())
        assertEquals("files/voice-notes", renamed.current("files/notes"))
    }

    private fun put(path: String, text: String) = File(root, path).apply {
        parentFile.mkdirs()
        writeText(text)
    }

    private fun zipOf(manifest: BackupManifest, vararg entries: Pair<String, String>): ByteArray =
        ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry(BackupManifest.ENTRY))
                zip.write(manifest.encode())
                zip.closeEntry()
                entries.forEach { (name, text) -> entry(zip, name, text) }
            }
        }.toByteArray()

    private fun entry(zip: ZipOutputStream, name: String, text: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(text.toByteArray())
        zip.closeEntry()
    }

    @Test
    fun `the colours and emoji given to collections travel with the backup`() {
        assertTrue(BackupContents.DEFAULT.allows("files/${ShelfLookStore.FILE_NAME}"))
    }

    private companion object {
        const val DB_VERSION = 4
    }
}
