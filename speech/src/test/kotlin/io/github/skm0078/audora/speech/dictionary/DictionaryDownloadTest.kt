// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/dictionary/DictionaryDownloadTest.kt
// Purpose: The dictionary download (item 33): pins match models.json, the zip installs safely and opens for look-ups.

package io.github.skm0078.audora.speech.dictionary

import io.github.skm0078.audora.core.config.VoiceDownloadConfig
import io.github.skm0078.audora.core.dictionary.Dictionary
import io.github.skm0078.audora.core.dictionary.FileByteSource
import io.github.skm0078.audora.core.dictionary.SortedLines
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

    private fun pinFor(zip: ByteArray) =
        DictionaryPin.ENGLISH.copy(sha256 = sha256Hex(zip), sizeBytes = zip.size.toLong())

    private fun filesFor(pin: DictionaryPin, cap: Long = config.maxUnpackedBytes) =
        DictionaryFiles(temp.root.resolve("dictionary"), pin, cap, config.bufferBytes)

    private fun zipFile(bytes: ByteArray): File = temp.newFile().apply { writeBytes(bytes) }

    @Test
    fun `the pins repeat speech models json`() {
        val catalog = File("models.json").takeIf { it.isFile } ?: File("speech/models.json")
        val text = catalog.readText()
        val english = DictionaryPin.ENGLISH
        val entry = text.substringAfter("\"id\": \"${english.id}\"").substringBefore("\"id\":")
        listOf(
            "\"zip\": \"${english.fileName}\"",
            "\"sha256\": \"${english.sha256}\"",
            "\"sizeBytes\": ${english.sizeBytes}",
            "\"unpackedBytes\": ${english.unpackedBytes}",
            "\"file\": \"${english.entryName}\"",
        ).forEach { assertTrue("models.json lacks $it", it in entry) }
        assertTrue("\"baseUrl\": \"${english.baseUrl}\"" in text)
        assertEquals("${english.id}-${english.sha256.take(12)}.zip", english.fileName)
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
}
