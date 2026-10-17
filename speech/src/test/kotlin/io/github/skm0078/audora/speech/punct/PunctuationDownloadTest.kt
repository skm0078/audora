// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/punct/PunctuationDownloadTest.kt
// Purpose: The punctuation model download (RI-11): pins match models.json, the zip installs safely, retries resume.

package io.github.skm0078.audora.speech.punct

import io.github.skm0078.audora.core.config.VoiceDownloadConfig
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.speech.voices.FakeHttpSource
import io.github.skm0078.audora.speech.voices.JobResult
import io.github.skm0078.audora.speech.voices.ResumableDownloader
import io.github.skm0078.audora.speech.voices.sha256Hex
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PunctuationDownloadTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val config = VoiceDownloadConfig(maxAttempts = 3, installHeadroomBytes = 0, progressStepBytes = 1)

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { bytes ->
        ZipOutputStream(bytes).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content)
                zip.closeEntry()
            }
        }
    }.toByteArray()

    private val goodZip = zipOf(
        "README.md" to "readme".toByteArray(),
        "pack/model.int8.onnx" to ByteArray(2_000) { it.toByte() },
        "pack/bpe.vocab" to "a 1\nb 2\n".toByteArray(),
    )

    private fun modelFor(zip: ByteArray) = PunctuationModel.ENGLISH.copy(
        sha256 = sha256Hex(zip),
        sizeBytes = zip.size.toLong(),
        baseUrl = "https://example.test/",
    )

    private fun filesFor(model: PunctuationModel, cap: Long = config.maxUnpackedBytes) =
        PunctuationFiles(temp.root.resolve("punctuation"), model, cap, config.bufferBytes)

    private fun zipFile(bytes: ByteArray): File = temp.newFile().apply { writeBytes(bytes) }

    @Test
    fun `the pins repeat speech models json`() {
        val catalog = File("models.json").takeIf { it.isFile } ?: File("speech/models.json")
        val text = catalog.readText()
        val english = PunctuationModel.ENGLISH
        val entry = text.substringAfter("\"id\": \"${english.id}\"").substringBefore("\"id\":")
        listOf(
            "\"file\": \"${english.fileName}\"",
            "\"sha256\": \"${english.sha256}\"",
            "\"sizeBytes\": ${english.sizeBytes}",
            "\"model\": \"${english.modelFile}\"",
            "\"tokens\": \"${english.vocabFile}\"",
        ).forEach { assertTrue("models.json lacks $it", it in entry) }
        assertTrue("\"baseUrl\": \"${english.baseUrl}\"" in text)
        assertEquals("${english.id}-${english.sha256.take(12)}.zip", english.fileName)
    }

    @Test
    fun `only the two model files are unpacked, then the zip is deleted`() {
        val files = filesFor(modelFor(goodZip))
        val zip = zipFile(goodZip)

        assertTrue(files.install(zip) is Outcome.Success)
        assertTrue(files.isInstalled())
        assertEquals(setOf("model.int8.onnx", "bpe.vocab", "installed"), files.modelDir.list()!!.toSet())
        assertFalse(zip.exists())
    }

    @Test
    fun `a zip missing a file, a broken zip or one too big installs nothing`() {
        val model = modelFor(goodZip)
        listOf(
            filesFor(model) to zipOf("model.int8.onnx" to ByteArray(10)),
            filesFor(model) to "not a zip".toByteArray(),
            filesFor(model, cap = 100) to goodZip,
        ).forEach { (files, bytes) ->
            assertTrue(files.install(zipFile(bytes)) is Outcome.Failure)
            assertFalse(files.isInstalled())
        }
    }

    @Test
    fun `the job downloads, installs and resumes like a voice`() {
        val model = modelFor(goodZip)
        val files = filesFor(model)
        val server = FakeHttpSource(goodZip).apply { dropAfter = 500 }
        var installed = 0
        val job = PunctuationDownloadJob(
            model,
            files,
            ResumableDownloader(server, config) { Long.MAX_VALUE },
            config,
        ) { installed++ }

        assertTrue(job.run(0, { _, _ -> }, { true }) is JobResult.Retry)
        assertEquals(JobResult.Installed, job.run(1, { _, _ -> }, { true }))
        assertEquals(listOf(0L, 500L), server.offsets)
        assertEquals(1, installed)
        assertEquals(JobResult.Installed, job.run(2, { _, _ -> }, { true }))
        assertEquals(listOf(0L, 500L), server.offsets)
    }

    @Test
    fun `a network failure on the last attempt gives up`() {
        val model = modelFor(goodZip)
        val server = FakeHttpSource(goodZip).apply { connectFailure = IOException("offline") }
        val job = PunctuationDownloadJob(
            model,
            filesFor(model),
            ResumableDownloader(server, config) { Long.MAX_VALUE },
            config,
        ) {}

        assertTrue(job.run(0, { _, _ -> }, { true }) is JobResult.Retry)
        assertTrue(job.run(2, { _, _ -> }, { true }) is JobResult.Failed)
    }
}
