// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/voices/VoiceFilesTest.kt
// Purpose: The on-disk voice layout: listing, manifests, delete, and tidying what a killed install leaves behind.

package io.github.skm0078.audora.speech.voices

import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.getOrNull
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VoiceFilesTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val files by lazy { VoiceFiles(temp.newFolder("voices")) }

    private fun install(id: String, origin: VoiceOrigin = VoiceOrigin.CATALOG, label: String = "Voice $id"): File {
        val staging = files.stagingDir(id).apply { mkdirs() }
        File(staging, "model.onnx").writeBytes(ByteArray(10))
        val spec = voiceSpec(id).copy(label = label)
        return requireNotNull(files.commit(staging, spec, origin).getOrNull()).dir
    }

    @Test
    fun `a committed voice lists with its manifest, origin and size`() {
        install("custom-hindi-1a2b", VoiceOrigin.CUSTOM, label = "मेरी आवाज़")

        val voice = files.installed().single()

        assertEquals("मेरी आवाज़", voice.spec.label)
        assertEquals(VoiceOrigin.CUSTOM, voice.origin)
        assertTrue(voice.sizeBytes >= 10)
        assertFalse(files.stagingDir("custom-hindi-1a2b").exists())
    }

    @Test
    fun `folders with a broken manifest, another voice's manifest, or none are skipped`() {
        install("piper-en-cori")
        File(files.installedRoot, "empty").mkdirs()
        File(files.installedRoot, "broken").apply { mkdirs() }.write("voice.properties", "id=broken".toByteArray())
        install("kitten-en-nano").renameTo(File(files.installedRoot, "renamed"))

        assertEquals(listOf("piper-en-cori"), files.installed().map { it.spec.id })
    }

    @Test
    fun `withdrawn catalog voices are deleted, an imported voice and other voices stay`() {
        install("piper-hi-pratham")
        install("mms-bn", VoiceOrigin.CUSTOM)
        install("supertonic-hi")

        val gone = files.withdraw(WithdrawnVoices.IDS)

        assertEquals(listOf("piper-hi-pratham"), gone)
        assertEquals(listOf("mms-bn", "supertonic-hi"), files.installed().map { it.spec.id })
        assertTrue(files.withdraw(WithdrawnVoices.IDS).isEmpty())
    }

    @Test
    fun `recover deletes half-made folders and puts back a copy an update had moved aside`() {
        install("piper-en-cori")
        install("kitten-en-nano").renameTo(File(files.installedRoot, "kitten-en-nano.old"))
        File(files.installedRoot, "piper-en-cori.old").mkdirs()
        files.stagingDir("piper-en-cori").mkdirs()

        files.recover()

        assertEquals(listOf("kitten-en-nano", "piper-en-cori"), files.installed().map { it.spec.id })
        assertEquals(listOf("kitten-en-nano", "piper-en-cori"), files.installedRoot.list().orEmpty().sorted())
    }

    @Test
    fun `delete removes the voice and its partial downloads only`() {
        install("piper-en-cori")
        install("kitten-en-nano")
        val partial = files.downloadsRoot.write("piper-en-cori-0123456789ab.zip.partial")
        val other = files.downloadsRoot.write("kitten-en-nano-0123456789ab.zip.partial")

        assertTrue(files.delete("piper-en-cori") is Outcome.Success)

        assertEquals(listOf("kitten-en-nano"), files.installed().map { it.spec.id })
        assertFalse(partial.exists())
        assertTrue(other.exists())
        assertTrue(files.delete("not-installed") is Outcome.Success)
    }

    @Test
    fun `partial downloads are named by their pin`() {
        val voice = catalogVoice(ByteArray(3))

        assertEquals("piper-en-cori-${voice.sha256.take(12)}.zip.partial", files.partialDownload(voice).name)
    }

    @Test
    fun `reading choices survive a round trip and a bad file means defaults`() {
        val prefs = VoicePreferences(File(temp.root, "prefs/choices.properties"))
        val hindi = voiceSpec("custom-hi-1", Lang.HI).voiceId

        assertTrue(prefs.read().isEmpty())
        assertTrue(prefs.write(mapOf(Lang.HI to hindi)))
        assertEquals(mapOf(Lang.HI to hindi), prefs.read())

        File(temp.root, "prefs/choices.properties").writeText("en=NOPE:x\nbn=sherpa\n")
        assertTrue(prefs.read().isEmpty())
    }
}
