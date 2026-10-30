// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/download/DownloadLeftoversTest.kt
// Purpose: Half-finished downloads are found where every owner keeps them, kept for a while, then deleted, and
//          never while a download is queued or running; installed models are never touched.

package io.github.skm0078.audora.speech.download

import io.github.skm0078.audora.core.config.LeftoverConfig
import io.github.skm0078.audora.core.files.LeftoverKind
import io.github.skm0078.audora.core.files.LeftoverPolicy
import io.github.skm0078.audora.core.files.LeftoverTally
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.speech.ocr.OcrModel
import io.github.skm0078.audora.speech.ocr.TessdataFiles
import io.github.skm0078.audora.speech.voices.PinnedFile
import io.github.skm0078.audora.speech.voices.VoiceFiles
import io.github.skm0078.audora.speech.voices.catalogVoice
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DownloadLeftoversTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val root by lazy { temp.newFolder("no_backup") }
    private var downloading = false
    private val leftovers by lazy { DownloadLeftovers(root) { downloading } }
    private val now = 100L * DAY
    private val policy = LeftoverPolicy(LeftoverConfig(keepMs = 7 * DAY), now)

    private val pin = object : PinnedFile {
        override val id = "dict-bn"
        override val url = "https://example.test/dict.zip"
        override val sha256 = "ab".repeat(32)
        override val sizeBytes = 10L
    }

    private fun partial(file: File, bytes: Int, ageDays: Long): File = file.apply {
        parentFile.mkdirs()
        writeBytes(ByteArray(bytes))
        setLastModified(now - ageDays * DAY)
    }

    @Test
    fun `every owner's half download is where the clean-up looks`() {
        val voice = VoiceFiles(File(root, "voices/managed")).partialDownload(catalogVoice(byteArrayOf(1)))
        val ocr = TessdataFiles(File(root, "ocr"))
            .partialDownload(OcrModel(Lang.BN, "ben.traineddata", "https://example.test/ben", "cd".repeat(32), 5L))
        val dictionary = PinnedZipFiles(File(root, "dictionary-bn"), pin, setOf("a"), setOf("a"), 1L, 10L, 8, "test")
            .partialDownload()
        val acted = File(root, "acted-voices/part-1/${DownloadLeftovers.DOWNLOADS}/acted-1-abc.partial")
        listOf(voice, ocr, dictionary, acted).forEach { partial(it, bytes = 1, ageDays = 0) }

        assertEquals(setOf(voice, ocr, dictionary, acted), leftovers.partials().toSet())
    }

    @Test
    fun `a fresh half download is kept so it can resume, an old one is deleted and counted`() {
        val fresh = partial(File(root, "ocr/downloads/ocr-bn-1.partial"), bytes = 30, ageDays = 1)
        val old = partial(File(root, "voices/managed/downloads/bruno-2.zip.partial"), bytes = 70, ageDays = 8)

        assertEquals(LeftoverTally.of(LeftoverKind.DOWNLOAD, 2, 100L), leftovers.measure())
        val cleaned = leftovers.clean(policy, asked = false)

        assertEquals(LeftoverTally.of(LeftoverKind.DOWNLOAD, 1, 70L), cleaned)
        assertTrue(fresh.exists())
        assertFalse(old.exists())
    }

    @Test
    fun `clean up now deletes fresh ones too`() {
        val fresh = partial(File(root, "ocr/downloads/ocr-bn-1.partial"), bytes = 30, ageDays = 0)

        assertEquals(1, leftovers.clean(policy, asked = true).count(LeftoverKind.DOWNLOAD))
        assertFalse(fresh.exists())
    }

    @Test
    fun `nothing is deleted while a download is queued or running`() {
        val old = partial(File(root, "ocr/downloads/ocr-bn-1.partial"), bytes = 30, ageDays = 30)

        downloading = true

        assertEquals(LeftoverTally.NONE, leftovers.clean(policy, asked = true))
        assertEquals(LeftoverTally.NONE, leftovers.measure())
        assertTrue(old.exists())
    }

    @Test
    fun `installed models, install folders and files outside downloads are never touched`() {
        val installed = partial(File(root, "ocr/tessdata/ben.traineddata"), bytes = 5, ageDays = 30)
        val installing = partial(File(root, "voices/managed/installed/bruno.partial/model.onnx"), 5, ageDays = 30)
        val elsewhere = partial(File(root, "studio/book/page-1.partial"), bytes = 5, ageDays = 30)
        val notPartial = partial(File(root, "ocr/downloads/notes.txt"), bytes = 5, ageDays = 30)

        assertEquals(LeftoverTally.NONE, leftovers.clean(policy, asked = true))
        listOf(installed, installing, elsewhere, notPartial).forEach { assertTrue(it.path, it.exists()) }
    }

    @Test
    fun `no folder at all finds nothing`() {
        assertEquals(LeftoverTally.NONE, DownloadLeftovers(File(root, "missing")) { false }.clean(policy, true))
    }

    private companion object {
        const val DAY = 24L * 60 * 60 * 1000
    }
}
