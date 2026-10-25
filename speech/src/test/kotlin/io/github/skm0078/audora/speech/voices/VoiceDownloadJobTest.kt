// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/voices/VoiceDownloadJobTest.kt
// Purpose: The worker's decisions: install on success, retry network trouble and stops, give up on corrupt or last try.

package io.github.skm0078.audora.speech.voices

import io.github.skm0078.audora.core.config.VoiceDownloadConfig
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VoiceDownloadJobTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val zip = zipOf(voiceZipEntries())
    private val voice = catalogVoice(zip)
    private val http = FakeHttpSource(zip)
    private val config = VoiceDownloadConfig(maxAttempts = 3, installHeadroomBytes = 0)
    private val files by lazy { VoiceFiles(temp.newFolder("voices")) }
    private var installedCalls = 0
    private val job by lazy {
        VoiceDownloadJob(
            catalog = VoiceCatalog(listOf(voice)),
            files = files,
            downloader = ResumableDownloader(http, config) { Long.MAX_VALUE },
            installer = VoiceArchiveInstaller(files, config),
            config = config,
            onInstalled = { installedCalls++ },
        )
    }

    private fun run(attempt: Int = 0, id: String = voice.id, keepGoing: () -> Boolean = { true }) =
        job.run(id, attempt, { _, _ -> }, keepGoing)

    @Test
    fun `a good download is installed and announced`() {
        assertEquals(JobResult.Installed, run())
        assertEquals(1, installedCalls)
        assertEquals(listOf(voice.spec), files.installed().map { it.spec })
        assertTrue(files.downloadsRoot.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `network trouble retries until the last attempt`() {
        http.connectFailure = IOException("offline")

        assertTrue(run(attempt = 0) is JobResult.Retry)
        val last = run(attempt = 2)

        assertTrue(last is JobResult.Failed && last.message.contains("3 tries"))
        assertEquals(0, installedCalls)
    }

    @Test
    fun `a stop always retries, even on the last attempt`() {
        assertTrue(run(attempt = 2, keepGoing = { false }) is JobResult.Retry)
    }

    @Test
    fun `a voice deleted while it downloads is not installed and leaves no zip`() {
        var deleted = false
        val result = run(
            keepGoing = {
                if (!deleted) files.installGate.delete { files.delete(voice.id) }
                deleted = true
                true
            },
        )

        assertTrue("was $result", result is JobResult.Failed)
        assertEquals(0, installedCalls)
        assertTrue(files.installed().isEmpty())
        assertTrue(files.downloadsRoot.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `a corrupt file fails at once`() {
        http.content = zip.copyOf().also { it[10] = (it[10] + 1).toByte() }

        assertTrue(run() is JobResult.Failed)
        assertTrue(files.installed().isEmpty())
    }

    @Test
    fun `an unknown voice fails`() {
        assertTrue(run(id = "nope") is JobResult.Failed)
    }

    @Test
    fun `a verified zip that will not install fails`() {
        val broken = zipOf(voiceZipEntries() - "tokens.txt")
        val brokenVoice = catalogVoice(broken)
        http.content = broken
        val brokenJob = VoiceDownloadJob(
            VoiceCatalog(listOf(brokenVoice)),
            files,
            ResumableDownloader(http, config) { Long.MAX_VALUE },
            VoiceArchiveInstaller(files, config),
            config,
        ) { installedCalls++ }

        assertTrue(brokenJob.run(brokenVoice.id, 0, { _, _ -> }, { true }) is JobResult.Failed)
        assertEquals(0, installedCalls)
    }
}
