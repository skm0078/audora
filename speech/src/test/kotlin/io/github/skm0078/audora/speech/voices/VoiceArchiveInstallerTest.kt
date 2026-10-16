// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/voices/VoiceArchiveInstallerTest.kt
// Purpose: Unpacking a verified voice zip: files plus manifest, hostile or broken zips refused, updates swap.

package io.github.skm0078.audora.speech.voices

import io.github.skm0078.audora.core.config.VoiceDownloadConfig
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.getOrNull
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VoiceArchiveInstallerTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val files by lazy { VoiceFiles(temp.newFolder("voices")) }
    private val installer by lazy { VoiceArchiveInstaller(files, VoiceDownloadConfig(maxUnpackedBytes = 100_000)) }

    private fun zipFile(entries: Map<String, ByteArray>): Pair<File, CatalogVoice> {
        val bytes = zipOf(entries)
        return temp.newFile().apply { writeBytes(bytes) } to catalogVoice(bytes)
    }

    @Test
    fun `installs the files with a manifest and deletes the zip`() {
        val model = ByteArray(5_000) { 7 }
        val (zip, voice) = zipFile(voiceZipEntries(model))

        val installed = requireNotNull(installer.install(zip, voice).getOrNull())

        assertArrayEquals(model, File(installed.dir, "model.onnx").readBytes())
        assertTrue(File(installed.dir, "espeak-ng-data/en_dict").isFile)
        assertEquals(VoiceOrigin.CATALOG, installed.origin)
        assertEquals(voice.sha256, installed.spec.contentVersion)
        assertEquals(listOf(voice.spec), files.installed().map { it.spec })
        assertFalse(zip.exists())
    }

    @Test
    fun `an entry that climbs out of the folder is refused and nothing is installed`() {
        val (zip, voice) = zipFile(voiceZipEntries() + ("../../evil.txt" to ByteArray(1)))

        assertTrue(installer.install(zip, voice) is Outcome.Failure)
        assertTrue(files.installed().isEmpty())
        assertFalse(File(temp.root, "evil.txt").exists())
        assertFalse(files.stagingDir(voice.id).exists())
    }

    @Test
    fun `a zip without the model is refused`() {
        val (zip, voice) = zipFile(voiceZipEntries() - "model.onnx")

        val error = (installer.install(zip, voice) as Outcome.Failure).error

        assertTrue(error, error.contains("model.onnx"))
        assertTrue(files.installed().isEmpty())
    }

    @Test
    fun `a file that is not a zip is refused`() {
        val zip = temp.newFile().apply { writeText("not a zip") }

        assertTrue(installer.install(zip, catalogVoice(zip.readBytes())) is Outcome.Failure)
    }

    @Test
    fun `an archive that unpacks past the cap is refused`() {
        val (zip, voice) = zipFile(voiceZipEntries(model = ByteArray(200_000)))

        assertTrue(installer.install(zip, voice) is Outcome.Failure)
        assertTrue(files.installed().isEmpty())
    }

    @Test
    fun `an update replaces the older copy completely`() {
        val (oldZip, oldVoice) = zipFile(voiceZipEntries() + ("old-only.txt" to ByteArray(1)))
        installer.install(oldZip, oldVoice)
        val (newZip, newVoice) = zipFile(voiceZipEntries(model = ByteArray(10) { 1 }))

        val installed = requireNotNull(installer.install(newZip, newVoice).getOrNull())

        assertFalse(File(installed.dir, "old-only.txt").exists())
        assertEquals(newVoice.sha256, files.installed().single().spec.contentVersion)
    }
}
