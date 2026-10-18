// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/listen/ListenPinTest.kt
// Purpose: The reading tutor's model pins repeat speech/models.json, and a model zip installs only whole.

package io.github.skm0078.audora.speech.listen

import io.github.skm0078.audora.core.config.VoiceDownloadConfig
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.speech.voices.sha256Hex
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ListenPinTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `the pins repeat speech models json`() {
        val catalog = File("models.json").takeIf { it.isFile } ?: File("speech/models.json")
        val text = catalog.readText()
        Lang.entries.map(ListenPin::of).forEach { pin ->
            val entry = text.substringAfter("\"id\": \"${pin.id}\"").substringBefore("\"id\":")
            listOf(
                "\"file\": \"${pin.fileName}\"",
                "\"sha256\": \"${pin.sha256}\"",
                "\"sizeBytes\": ${pin.sizeBytes}",
                "\"unpackedBytes\": ${pin.unpackedBytes}",
                "\"kind\": \"${pin.kind.catalogName}\"",
            ).forEach { assertTrue("models.json lacks $it for ${pin.id}", it in entry) }
            assertEquals("${pin.id}-${pin.sha256.take(12)}.zip", pin.fileName)
        }
    }

    @Test
    fun `a model zip installs only with every file at the pinned total size`() {
        val entries = ListenKind.PHRASE_CTC.entries.associateWith { "bytes of $it".toByteArray() }
        val zip = ByteArrayOutputStream().also { bytes ->
            ZipOutputStream(bytes).use { out ->
                entries.forEach { (name, content) ->
                    out.putNextEntry(ZipEntry(name))
                    out.write(content)
                    out.closeEntry()
                }
            }
        }.toByteArray()
        val total = entries.values.sumOf { it.size.toLong() }
        val pin = ListenPin.HINDI.copy(sha256 = sha256Hex(zip), sizeBytes = zip.size.toLong(), unpackedBytes = total)
        val config = VoiceDownloadConfig()
        val files = listenFiles(temp.root, pin, config.maxUnpackedBytes, config.bufferBytes)

        assertTrue(files.install(temp.newFile().apply { writeBytes(zip) }) is Outcome.Success)
        assertTrue(files.isInstalled())
        files.file("vad.onnx").writeText("x")
        assertFalse(files.isInstalled())

        val short =
            listenFiles(temp.root, pin.copy(unpackedBytes = total + 1), config.maxUnpackedBytes, config.bufferBytes)
        assertTrue(short.install(temp.newFile().apply { writeBytes(zip) }) is Outcome.Failure)
    }
}
