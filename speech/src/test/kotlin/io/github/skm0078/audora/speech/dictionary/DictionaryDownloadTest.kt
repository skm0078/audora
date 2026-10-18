// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/dictionary/DictionaryDownloadTest.kt
// Purpose: The dictionary download (item 33): pins match models.json, the zip installs safely and opens for look-ups.

package io.github.skm0078.audora.speech.dictionary

import io.github.skm0078.audora.core.config.VoiceDownloadConfig
import io.github.skm0078.audora.core.dictionary.Dictionary
import io.github.skm0078.audora.core.dictionary.FileByteSource
import io.github.skm0078.audora.core.dictionary.SortedLines
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

class DictionaryDownloadTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val config = VoiceDownloadConfig()

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { bytes ->
        ZipOutputStream(bytes).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content)
                zip.closeEntry()
            }
        }
    }.toByteArray()

    private val text = "box\tn|a container|\nmice\t=mouse\nmouse\tn|a small rodent|\n".toByteArray()
    private val goodZip = zipOf("LICENSE" to "CC BY 4.0".toByteArray(), "dictionary.tsv" to text)

    private fun pinFor(zip: ByteArray) = DictionaryPin.ENGLISH.copy(
        sha256 = sha256Hex(zip),
        sizeBytes = zip.size.toLong(),
        unpackedBytes = text.size.toLong(),
    )

    private fun filesFor(pin: DictionaryPin, cap: Long = config.maxUnpackedBytes) =
        DictionaryFiles(temp.root.resolve("dictionary"), pin, cap, config.bufferBytes)

    private fun zipFile(bytes: ByteArray): File = temp.newFile().apply { writeBytes(bytes) }

    @Test
    fun `the pins repeat speech models json`() {
        val catalog = File("models.json").takeIf { it.isFile } ?: File("speech/models.json")
        val text = catalog.readText()
        Lang.entries.map(DictionaryPin::of).forEach { pin ->
            val entry = text.substringAfter("\"id\": \"${pin.id}\"").substringBefore("\"id\":")
            listOf(
                "\"zip\": \"${pin.fileName}\"",
                "\"sha256\": \"${pin.sha256}\"",
                "\"sizeBytes\": ${pin.sizeBytes}",
                "\"unpackedBytes\": ${pin.unpackedBytes}",
                "\"file\": \"${pin.entryName}\"",
            ).forEach { assertTrue("models.json lacks $it for ${pin.id}", it in entry) }
            assertTrue("\"baseUrl\": \"${pin.baseUrl}\"" in text)
            assertEquals("${pin.id}-${pin.sha256.take(12)}.zip", pin.fileName)
        }
    }

    @Test
    fun `an installed dictionary opens for look-ups, and the zip is deleted`() {
        val files = filesFor(pinFor(goodZip))
        val zip = zipFile(goodZip)

        assertTrue(files.install(zip) is Outcome.Success)

        assertTrue(files.isInstalled())
        assertFalse(zip.exists())
        FileByteSource(files.text).use { source ->
            assertEquals(listOf("mouse"), Dictionary(SortedLines(source)).lookUp("Mice").map { it.headword })
        }
    }

    @Test
    fun `a zip without the dictionary or over the cap installs nothing`() {
        val noText = zipOf("LICENSE" to "x".toByteArray())
        assertTrue(filesFor(pinFor(noText)).install(zipFile(noText)) is Outcome.Failure)

        val files = filesFor(pinFor(goodZip), cap = 10)
        assertTrue(files.install(zipFile(goodZip)) is Outcome.Failure)
        assertFalse(files.isInstalled())
    }

    @Test
    fun `a text of another size than pinned is never installed, and one damaged later reads as not installed`() {
        val wrongSize = filesFor(pinFor(goodZip).copy(unpackedBytes = text.size + 1L))
        assertTrue(wrongSize.install(zipFile(goodZip)) is Outcome.Failure)
        assertFalse(wrongSize.isInstalled())

        val files = filesFor(pinFor(goodZip))
        assertTrue(files.install(zipFile(goodZip)) is Outcome.Success)
        files.text.writeText("box")
        assertFalse(files.isInstalled())
    }

    @Test
    fun `partial downloads of an older pin are dropped, the current one kept`() {
        val files = filesFor(pinFor(goodZip))
        val current = files.partialDownload().apply {
            parentFile.mkdirs()
            writeText("half")
        }
        val old = File(current.parentFile, "dict-en-oewn-2025-000000000000.partial").apply { writeText("old") }

        files.dropOtherPartials()

        assertTrue(current.exists())
        assertFalse(old.exists())
    }
}
