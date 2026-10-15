// File: spikes/voices/src/test/kotlin/io/github/skm0078/audora/spike/voices/download/ModelStoreTest.kt
// Purpose: An archive is installed only after its checksum matched; failures are per archive and retried.

package io.github.skm0078.audora.spike.voices.download

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelStoreTest {
    private val root = Files.createTempDirectory("models").toFile()
    private val goodZip = zipBytes("voice-a/model.onnx" to "weights")

    private inner class FakeHttp(
        private val files: Map<String, ByteArray>,
        private val sums: String?,
        private var failuresBeforeSuccess: Int = 0,
    ) : HttpFetcher {
        var downloads = 0

        override fun fetchText(url: String): String = sums ?: throw IOException("offline")

        override fun download(url: String, target: File, onBytes: (Long, Long) -> Unit) {
            downloads++
            if (failuresBeforeSuccess-- > 0) throw IOException("connection reset")
            val body = files[url.substringAfterLast('/')] ?: throw IOException("404")
            target.writeBytes(body)
            onBytes(body.size.toLong(), body.size.toLong())
        }
    }

    private fun store(http: HttpFetcher) =
        ModelStore(root, "https://example.invalid/", http, DownloadConfig(attempts = 2))

    private fun sumsFor(name: String, bytes: ByteArray) = "${ModelArchives.sha256(bytes.inputStream())}  $name\n"

    @Test
    fun `installs a verified archive and skips it next time`() {
        val http = FakeHttp(mapOf("voice-a.zip" to goodZip), sumsFor("voice-a.zip", goodZip))
        val store = store(http)

        val first = store.install(listOf("voice-a"), isCancelled = { false }) {}
        val second = store.install(listOf("voice-a"), isCancelled = { false }) {}

        assertTrue(first.single() is InstallOutcome.Installed)
        assertTrue(second.single() is InstallOutcome.AlreadyInstalled)
        assertEquals("weights", File(store.modelDir("voice-a"), "model.onnx").readText())
        assertEquals(1, http.downloads)
    }

    @Test
    fun `rejects an archive whose checksum does not match`() {
        val http = FakeHttp(mapOf("voice-a.zip" to goodZip), "${"0".repeat(64)}  voice-a.zip\n")
        val store = store(http)

        val outcome = store.install(listOf("voice-a"), isCancelled = { false }) {}.single()

        assertTrue(outcome is InstallOutcome.Failed && "SHA-256 mismatch" in outcome.reason)
        assertFalse(store.isInstalled("voice-a"))
        assertFalse(File(root, "voice-a.zip.part").exists())
    }

    @Test
    fun `retries a dropped download`() {
        val http = FakeHttp(mapOf("voice-a.zip" to goodZip), sumsFor("voice-a.zip", goodZip), failuresBeforeSuccess = 1)

        val outcome = store(http).install(listOf("voice-a"), isCancelled = { false }) {}.single()

        assertTrue(outcome is InstallOutcome.Installed)
        assertEquals(2, http.downloads)
    }

    @Test
    fun `reports every archive as failed when the checksum list is unreachable`() {
        val outcomes = store(FakeHttp(emptyMap(), sums = null)).install(listOf("a", "b"), isCancelled = { false }) {}

        assertEquals(listOf("a", "b"), outcomes.map { it.archive })
        assertTrue(outcomes.all { it is InstallOutcome.Failed })
    }

    @Test
    fun `fails an archive whose zip lacks its top-level directory`() {
        val wrong = zipBytes("model.onnx" to "weights")
        val http = FakeHttp(mapOf("voice-a.zip" to wrong), sumsFor("voice-a.zip", wrong))

        val outcome = store(http).install(listOf("voice-a"), isCancelled = { false }) {}.single()

        assertTrue(outcome is InstallOutcome.Failed)
    }

    private fun zipBytes(vararg entries: Pair<String, String>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { out ->
            entries.forEach { (name, body) ->
                out.putNextEntry(ZipEntry(name))
                out.write(body.toByteArray())
                out.closeEntry()
            }
        }
        return bytes.toByteArray()
    }
}
