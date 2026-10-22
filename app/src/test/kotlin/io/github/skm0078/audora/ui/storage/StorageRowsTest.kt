// File: app/src/test/kotlin/io/github/skm0078/audora/ui/storage/StorageRowsTest.kt
// Purpose: The Storage screen's rows (UI plan, wave 4): what counts, what is still measuring, and folder sizes.

package io.github.skm0078.audora.ui.storage

import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.speech.ocr.OcrModelsState
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
    fun `counts downloaded voices and ready packs only`() {
        val voices = listOf(
            voice("built-in", 60L, VoiceStatus.BuiltIn, VoiceKind.BUILT_IN),
            voice("installed", 100L, VoiceStatus.Installed),
            voice("old", 50L, VoiceStatus.UpdateAvailable),
            voice("not-yet", 70L, VoiceStatus.NotDownloaded),
        )
        val rows = storageRows(voices, OcrModelsState.Ready(30L), OcrModelsState.NotDownloaded(9L), 5L)
        assertEquals(listOf(150L, 30L, 0L, 5L), rows.map { it.bytes })
        assertEquals(185L, storageTotal(rows))
    }

    @Test
    fun `an owner that failed says so, one still loading does not`() {
        val rows = storageRows(null, null, null, 5L, failed = setOf(StorageKind.VOICES, StorageKind.OCR))
        assertEquals(
            listOf(true, true, false, false),
            rows.map { it.failed },
        )
        assertEquals(5L, storageTotal(rows))
    }

    @Test
    fun `a size that arrives after all is shown, not the failure`() {
        val rows = storageRows(null, OcrModelsState.Ready(3L), null, null, failed = setOf(StorageKind.OCR))
        val ocr = rows.first { it.kind == StorageKind.OCR }
        assertEquals(3L, ocr.bytes)
        assertEquals(false, ocr.failed)
    }

    @Test
    fun `rows still loading are measuring and leave the total`() {
        val rows = storageRows(null, null, OcrModelsState.Ready(4L), null)
        assertNull(rows.first { it.kind == StorageKind.VOICES }.bytes)
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
