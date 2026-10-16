// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/ocr/OcrModelDownloadJobTest.kt
// Purpose: The OCR model pack download: every missing model installed, resumed, verified and retried like voices.

package io.github.skm0078.audora.speech.ocr

import io.github.skm0078.audora.core.config.VoiceDownloadConfig
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.speech.voices.JobResult
import io.github.skm0078.audora.speech.voices.ResumableDownloader
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OcrModelDownloadJobTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val models = listOf(Lang.EN, Lang.HI, Lang.BN).map { ocrModel(it) }
    private val catalog = TessdataCatalog("test", models)
    private val server = FakeModelServer(models.associate { it.url to fakeModelBytes(it.lang) })
    private val config = VoiceDownloadConfig(maxAttempts = 3, installHeadroomBytes = 0, progressStepBytes = 1)
    private val files by lazy { TessdataFiles(temp.newFolder("ocr")) }
    private val progress = ArrayList<Pair<Long, Long>>()
    private var installedCalls = 0
    private val job by lazy {
        OcrModelDownloadJob(
            catalog = catalog,
            files = files,
            downloader = ResumableDownloader(server, config) { Long.MAX_VALUE },
            config = config,
            onInstalled = { installedCalls++ },
        )
    }

    private fun run(attempt: Int = 0, keepGoing: () -> Boolean = { true }) =
        job.run(attempt, { bytes, total -> progress.add(bytes to total) }, keepGoing)

    private fun source(lang: Lang) = server.sources.getValue(catalog.find(lang)!!.url)

    @Test
    fun `every model is downloaded, verified and installed`() {
        assertEquals(JobResult.Installed, run())
        assertEquals(setOf(Lang.EN, Lang.HI, Lang.BN), files.installed(catalog))
        assertEquals(3, installedCalls)
        assertTrue(progress.all { it.second == catalog.totalBytes })
        assertEquals(progress.map { it.first }.sorted(), progress.map { it.first })
    }

    @Test
    fun `models already installed are skipped`() {
        source(Lang.HI).connectFailure = IOException("offline")
        assertTrue(run() is JobResult.Retry)
        assertEquals(setOf(Lang.EN), files.installed(catalog))
        source(Lang.HI).connectFailure = null
        assertEquals(JobResult.Installed, run(attempt = 1))
        assertEquals(listOf(0L), source(Lang.EN).offsets)
        assertEquals(3, installedCalls)
    }

    @Test
    fun `an interrupted model resumes from the bytes on disk`() {
        source(Lang.BN).dropAfter = 1_000
        assertTrue(run() is JobResult.Retry)
        assertEquals(JobResult.Installed, run(attempt = 1))
        assertEquals(listOf(0L, 1_000L), source(Lang.BN).offsets)
    }

    @Test
    fun `a corrupt model is never installed and the job gives up`() {
        source(Lang.HI).content = fakeModelBytes(Lang.BN)
        val result = run()
        assertTrue("$result", result is JobResult.Failed)
        assertEquals(setOf(Lang.EN), files.installed(catalog))
    }

    @Test
    fun `network trouble retries until the last attempt and a stop always retries`() {
        source(Lang.EN).connectFailure = IOException("offline")
        assertTrue(run(attempt = 0) is JobResult.Retry)
        val last = run(attempt = 2)
        assertTrue("$last", last is JobResult.Failed && last.message.contains("3 tries"))
        source(Lang.EN).connectFailure = null
        assertTrue(run(attempt = 2, keepGoing = { false }) is JobResult.Retry)
    }
}
