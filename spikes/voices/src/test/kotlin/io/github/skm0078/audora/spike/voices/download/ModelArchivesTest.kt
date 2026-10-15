// File: spikes/voices/src/test/kotlin/io/github/skm0078/audora/spike/voices/download/ModelArchivesTest.kt
// Purpose: Checksum parsing, hashing and safe unzip, including the zip-slip guard.

package io.github.skm0078.audora.spike.voices.download

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelArchivesTest {
    private val hexA = "a".repeat(64)
    private val hexB = "B".repeat(64)

    @Test
    fun `parses sha256sum text and binary mode lines`() {
        val sums = ModelArchives.parseSha256Sums("$hexA  one.zip\n$hexB *two.zip\n\n# comment\n")

        assertEquals(mapOf("one.zip" to hexA, "two.zip" to "b".repeat(64)), sums)
    }

    @Test
    fun `rejects a malformed checksum line`() {
        assertThrows(IllegalArgumentException::class.java) { ModelArchives.parseSha256Sums("abc one.zip") }
    }

    @Test
    fun `hashes like sha256sum`() {
        val expected = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad" // SHA-256("abc")
        assertEquals(expected, ModelArchives.sha256("abc".byteInputStream()))
    }

    @Test
    fun `unzips nested files`() {
        val dir = Files.createTempDirectory("s1").toFile()
        val zip = zipOf(dir, "voice/model.onnx" to "m", "voice/espeak-ng-data/hi_dict" to "d")

        val files = ModelArchives.unzip(zip, File(dir, "out"))

        assertEquals(2, files)
        assertEquals("d", File(dir, "out/voice/espeak-ng-data/hi_dict").readText())
    }

    @Test
    fun `refuses entries that escape the target directory`() {
        val dir = Files.createTempDirectory("s1").toFile()
        val zip = zipOf(dir, "../evil.txt" to "x")

        assertThrows(IOException::class.java) { ModelArchives.unzip(zip, File(dir, "out")) }
        assertTrue(!File(dir, "evil.txt").exists())
    }

    private fun zipOf(dir: File, vararg entries: Pair<String, String>): File {
        val zip = File(dir, "test.zip")
        ZipOutputStream(zip.outputStream()).use { out ->
            entries.forEach { (name, body) ->
                out.putNextEntry(ZipEntry(name))
                out.write(body.toByteArray())
                out.closeEntry()
            }
        }
        return zip
    }
}
