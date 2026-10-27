// File: app/src/test/kotlin/io/github/skm0078/audora/ui/storage/StorageRowsTest.kt
// Purpose: The Storage screen's rows (UI plan, wave 4): what counts, what is still measuring, and folder sizes.

package io.github.skm0078.audora.ui.storage

import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.speech.voices.VoiceKind
import io.github.skm0078.audora.speech.voices.VoiceRow
import io.github.skm0078.audora.speech.voices.VoiceStatus
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StorageRowsTest {
    private fun voice(name: String, bytes: Long, status: VoiceStatus, kind: VoiceKind = VoiceKind.CATALOG) =
        VoiceRow(VoiceId(EngineId.SHERPA, name), name, Lang.HI, "MIT", bytes, kind, status, readsLanguage = false)

    @Test
    fun `counts downloaded voices and every measured kind`() {
        val voices = listOf(
            voice("built-in", 60L, VoiceStatus.BuiltIn, VoiceKind.BUILT_IN),
            voice("installed", 100L, VoiceStatus.Installed),
            voice("old", 50L, VoiceStatus.UpdateAvailable),
            voice("not-yet", 70L, VoiceStatus.NotDownloaded),
        )
        val sizes = StorageKind.entries.filter { it != StorageKind.VOICES }.associateWith { 1L }
        val rows = storageRows(voices, sizes)
        assertEquals(StorageKind.entries, rows.map { it.kind })
        assertEquals(150L, rows.first().bytes)
        assertEquals(150L + sizes.size, storageTotal(rows))
    }

    @Test
    fun `models are deleted, caches cleared, and a shared PDF's copy only sized`() {
        val actions = storageRows(null, emptyMap()).associate { it.kind to it.action }
        assertEquals(StorageAction.MANAGE_VOICES, actions[StorageKind.VOICES])
        val models = listOf(StorageKind.OCR, StorageKind.PUNCTUATION, StorageKind.VOICE_COMMANDS)
        val packs = listOf(StorageKind.ACTED_VOICE, StorageKind.STORY_BRAIN)
        (models + packs + StorageKind.DICTIONARIES + StorageKind.READING_TUTOR).forEach {
            assertEquals("$it", StorageAction.DELETE, actions[it])
        }
        val caches =
            listOf(StorageKind.PAGE_PICTURES, StorageKind.OCR_TEXT, StorageKind.COVERS, StorageKind.VOICE_BLENDS)
        (caches + StorageKind.STUDIO_AUDIO + StorageKind.SEARCH_INDEXES).forEach {
            assertEquals("$it", StorageAction.CLEAR, actions[it])
        }
        assertNull(actions[StorageKind.SHARED_PDFS])
    }

    @Test
    fun `an owner that failed says so, one still loading does not`() {
        val rows = storageRows(
            null,
            mapOf(StorageKind.PAGE_PICTURES to 5L),
            failed = setOf(StorageKind.VOICES, StorageKind.OCR),
        )
        val failed = rows.filter { it.failed }.map { it.kind }
        assertEquals(listOf(StorageKind.VOICES, StorageKind.OCR), failed)
        assertEquals(5L, storageTotal(rows))
    }

    @Test
    fun `a size that arrives after all is shown, not the failure`() {
        val rows = storageRows(null, mapOf(StorageKind.OCR to 3L), failed = setOf(StorageKind.OCR))
        val ocr = rows.first { it.kind == StorageKind.OCR }
        assertEquals(3L, ocr.bytes)
        assertEquals(false, ocr.failed)
    }

    @Test
    fun `rows still loading are measuring and leave the total`() {
        val rows = storageRows(null, mapOf(StorageKind.PUNCTUATION to 4L))
        assertNull(rows.first { it.kind == StorageKind.VOICES }.bytes)
        assertNull(rows.first { it.kind == StorageKind.COVERS }.bytes)
        assertEquals(4L, storageTotal(rows))
    }

    @Test
    fun `folder size sums its files and a missing folder is empty`() {
        val dir = Files.createTempDirectory("storage-test").toFile()
        try {
            dir.resolve("a.webp").writeBytes(ByteArray(10))
            dir.resolve("book").mkdirs()
            dir.resolve("book/b.webp").writeBytes(ByteArray(5))
            assertEquals(15L, directoryBytes(dir))
            assertEquals(0L, directoryBytes(dir.resolve("missing")))
        } finally {
            dir.deleteRecursively()
        }
    }
}
