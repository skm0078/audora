// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/ocr/TessdataFilesTest.kt
// Purpose: OCR models count as installed only when their size and pin marker match; install and delete.

package io.github.skm0078.audora.speech.ocr

import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.result.Outcome
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TessdataFilesTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val hindi = ocrModel(Lang.HI)
    private val english = ocrModel(Lang.EN)
    private val catalog = TessdataCatalog("test", listOf(english, hindi))
    private val files by lazy { TessdataFiles(temp.newFolder("ocr")) }

    private fun verifiedDownload(model: OcrModel): File =
        temp.newFile().apply { writeBytes(fakeModelBytes(model.lang)) }

    @Test
    fun `an installed model sits where Tesseract looks and counts as installed`() {
        assertEquals(Outcome.Success(Unit), files.install(verifiedDownload(hindi), hindi))
        assertTrue(File(files.root, "tessdata/hin.traineddata").isFile)
        assertEquals(setOf(Lang.HI), files.installed(catalog))
        assertEquals(hindi.sizeBytes, files.installedBytes(catalog))
        assertFalse(files.isInstalled(english))
    }

    @Test
    fun `a new pin, a wrong size or a missing marker is not installed`() {
        files.install(verifiedDownload(hindi), hindi)
        assertFalse(files.isInstalled(hindi.copy(sha256 = "0".repeat(64))))
        assertFalse(files.isInstalled(hindi.copy(sizeBytes = hindi.sizeBytes + 1)))
        File(files.tessdataDir, "hin.traineddata.sha256").delete()
        assertFalse(files.isInstalled(hindi))
    }

    @Test
    fun `installing again replaces the old copy`() {
        files.install(verifiedDownload(hindi), hindi)
        val newer = ocrModel(Lang.HI, fakeModelBytes(Lang.HI, size = 4_000))
        val download = temp.newFile().apply { writeBytes(fakeModelBytes(Lang.HI, size = 4_000)) }
        assertEquals(Outcome.Success(Unit), files.install(download, newer))
        assertTrue(files.isInstalled(newer))
        assertFalse(files.isInstalled(hindi))
    }

    @Test
    fun `partial downloads are named by their pin`() {
        val partial = files.partialDownload(hindi)
        assertTrue(partial.name, partial.name.startsWith("ocr-hi-${hindi.sha256.take(12)}"))
        assertNotEquals(partial, files.partialDownload(hindi.copy(sha256 = "1".repeat(64))))
    }

    @Test
    fun `delete removes every model and download`() {
        files.install(verifiedDownload(hindi), hindi)
        files.partialDownload(english).apply {
            parentFile.mkdirs()
            writeText("part")
        }
        assertEquals(Outcome.Success(Unit), files.deleteAll())
        assertTrue(files.installed(catalog).isEmpty())
        assertFalse(files.partialDownload(english).exists())
    }
}
