// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/brain/StoryBrainDownloadJobTest.kt
// Purpose: The story brain's parts download, resume, join and install only when every byte matches its pins
//          (ADR-078); a delete during the download wins.

package io.github.skm0078.audora.speech.brain

import io.github.skm0078.audora.core.config.VoiceDownloadConfig
import io.github.skm0078.audora.speech.ocr.FakeModelServer
import io.github.skm0078.audora.speech.voices.InstallGate
import io.github.skm0078.audora.speech.voices.JobResult
import io.github.skm0078.audora.speech.voices.ResumableDownloader
import io.github.skm0078.audora.speech.voices.sha256Hex
import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StoryBrainDownloadJobTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val whole = ByteArray(5_000) { (it * 31 + 7).toByte() }
    private val pieces = listOf(whole.copyOfRange(0, 3_000), whole.copyOfRange(3_000, whole.size))
    private val model = StoryBrainModel(
        id = "brain-test",
        sha256 = sha256Hex(whole),
        sizeBytes = whole.size.toLong(),
        parts = pieces.mapIndexed { i, bytes ->
            StoryBrainModel.Part(
                "brain-test.part${i + 1}",
                "https://example.test/p${i + 1}",
                sha256Hex(bytes),
                bytes.size.toLong(),
            )
        },
        licence = "Apache-2.0",
    )
    private val server = FakeModelServer(model.parts.zip(pieces).associate { (part, bytes) -> part.url to bytes })
    private val config = VoiceDownloadConfig(maxAttempts = 3, installHeadroomBytes = 0, progressStepBytes = 1)
    private val gate = InstallGate()
    private val files by lazy { StoryBrainFiles(temp.newFolder("brain"), model, bufferBytes = 512) }
    private val progress = ArrayList<Pair<Long, Long>>()
    private var installedCalls = 0
    private val job by lazy {
        StoryBrainDownloadJob(
            model = model,
            files = files,
            downloader = ResumableDownloader(server, config) { Long.MAX_VALUE },
            config = config,
            gate = gate,
            onInstalled = { installedCalls++ },
        )
    }

    private fun run(attempt: Int = 0, keepGoing: () -> Boolean = { true }) =
        job.run(attempt, { bytes, total -> progress.add(bytes to total) }, keepGoing)

    private fun source(part: Int) = server.sources.getValue(model.parts[part].url)

    @Test
    fun `the parts are joined into the model, byte for byte`() {
        assertEquals(JobResult.Installed, run())
        assertTrue(files.isInstalled())
        assertArrayEquals(whole, files.modelFile.readBytes())
        assertEquals(1, installedCalls)
        assertFalse(files.joined.exists())
        // Progress counts the whole model and never goes back.
        assertTrue(progress.all { it.second == model.sizeBytes })
        assertEquals(progress.map { it.first }.sorted(), progress.map { it.first })
        assertEquals(model.sizeBytes, progress.last().first)
        assertEquals(JobResult.Installed, run())
        assertEquals(listOf(0L), source(0).offsets)
    }

    @Test
    fun `a stop between parts resumes with the next part, and a dropped part from its bytes`() {
        source(1).dropAfter = 700
        assertTrue(run() is JobResult.Retry)
        assertEquals(model.parts[1], files.nextPart())
        assertEquals(JobResult.Installed, run(attempt = 1))
        assertEquals(listOf(0L), source(0).offsets)
        assertEquals(listOf(0L, 700L), source(1).offsets)
        assertArrayEquals(whole, files.modelFile.readBytes())
    }

    @Test
    fun `a corrupt part is never joined and the download gives up`() {
        source(1).content = ByteArray(pieces[1].size)
        val result = run()
        assertTrue("$result", result is JobResult.Failed)
        assertFalse(files.isInstalled())
        assertEquals(pieces[0].size.toLong(), files.joined.length())
    }

    @Test
    fun `a joined file that ends between parts is not trusted`() {
        files.joined.parentFile.mkdirs()
        files.joined.writeBytes(ByteArray(10))
        assertEquals(model.parts[0], files.nextPart())
        assertFalse(files.joined.exists())
    }

    @Test
    fun `network trouble retries until the last attempt`() {
        source(0).connectFailure = IOException("offline")
        assertTrue(run(attempt = 0) is JobResult.Retry)
        val last = run(attempt = 2)
        assertTrue("$last", last is JobResult.Failed && last.message.contains("3 tries"))
    }

    @Test
    fun `a delete while downloading means nothing is installed`() {
        var deleted = false
        val result = run {
            if (!deleted && files.nextPart() == null) {
                gate.delete { files.deleteAll() }
                deleted = true
            }
            true
        }
        assertTrue("$result", result is JobResult.Failed)
        assertFalse(files.isInstalled())
        assertEquals(0, installedCalls)
    }

    @Test
    fun `deleting removes every byte`() {
        assertEquals(JobResult.Installed, run())
        assertTrue(files.sizeOnDisk() >= model.sizeBytes)
        files.deleteAll()
        assertEquals(0L, files.sizeOnDisk())
        assertFalse(files.isInstalled())
        assertEquals(model.parts.first(), files.nextPart())
    }
}
