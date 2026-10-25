// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/voices/VoiceImporterTest.kt
// Purpose: Importing a sherpa-onnx voice folder: what is detected, copied and trimmed, and what is refused and why.

package io.github.skm0078.audora.speech.voices

import io.github.skm0078.audora.core.config.VoiceDownloadConfig
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.errorOrNull
import io.github.skm0078.audora.core.result.getOrNull
import io.github.skm0078.audora.speech.sherpa.SherpaFamily
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VoiceImporterTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val files by lazy { VoiceFiles(temp.newFolder("voices")) }
    private var free = Long.MAX_VALUE
    private val config = VoiceDownloadConfig(maxImportFiles = 50, maxUnpackedBytes = 100_000, installHeadroomBytes = 0)
    private val importer by lazy { VoiceImporter(files, config, freeBytes = { free }, newSuffix = { "1a2b3c4d" }) }

    private fun piperFolder(name: String = "vits-piper-hi_IN-test"): File = temp.newFolder(name).apply {
        write("hi_IN-test.onnx", ByteArray(1_000))
        write("hi_IN-test.onnx.json", ByteArray(10))
        write("tokens.txt")
        write("MODEL_CARD")
        write("espeak-ng-data/phontab")
        write("espeak-ng-data/hi_dict")
        write("espeak-ng-data/fr_dict")
        write("espeak-ng-data/voices/!v/zac")
    }

    @Test
    fun `a Piper folder becomes a VITS voice with only the files it needs`() {
        val voice = requireNotNull(importer.import(FolderTree(piperFolder()), Lang.HI, "  My Hindi  ").getOrNull())

        assertEquals("custom-my-hindi-1a2b3c4d", voice.spec.id)
        assertEquals("My Hindi", voice.spec.label)
        assertEquals(Lang.HI, voice.spec.lang)
        assertEquals(SherpaFamily.VITS, voice.spec.family)
        assertEquals("hi_IN-test.onnx", voice.spec.model)
        assertEquals("espeak-ng-data", voice.spec.dataDir)
        assertEquals(VoiceOrigin.CUSTOM, voice.origin)
        val copied = voice.dir.walkTopDown().filter {
            it.isFile
        }.map { it.relativeTo(voice.dir).invariantSeparatorsPath }
        assertEquals(
            listOf(
                "MODEL_CARD",
                "espeak-ng-data/hi_dict",
                "espeak-ng-data/phontab",
                "espeak-ng-data/voices/!v/zac",
                "hi_IN-test.onnx",
                "tokens.txt",
                "voice.properties",
            ),
            copied.sorted().toList(),
        )
        assertEquals(listOf(voice.spec), files.installed().map { it.spec })
    }

    @Test
    fun `a blank name uses the folder's name`() {
        val voice = requireNotNull(importer.import(FolderTree(piperFolder("Rohini voice")), Lang.HI, " ").getOrNull())

        assertEquals("Rohini voice", voice.spec.label)
        assertEquals("custom-rohini-voice-1a2b3c4d", voice.spec.id)
    }

    @Test
    fun `a KittenTTS folder needs voices bin and espeak data`() {
        val folder = temp.newFolder("kitten").apply {
            write("model.int8.onnx")
            write("voices.bin")
            write("tokens.txt")
        }
        assertEquals(ImportError.KittenWithoutEspeak, importer.import(FolderTree(folder), Lang.EN, "").errorOrNull())

        folder.write("espeak-ng-data/phontab")
        val voice = requireNotNull(importer.import(FolderTree(folder), Lang.EN, "").getOrNull())

        assertEquals(SherpaFamily.KITTEN, voice.spec.family)
        assertEquals("voices.bin", voice.spec.voices)
    }

    @Test
    fun `an MMS folder without espeak data is fine`() {
        val folder = temp.newFolder("mms").apply {
            write("model.onnx")
            write("tokens.txt")
        }

        val voice = requireNotNull(importer.import(FolderTree(folder), Lang.BN, "").getOrNull())

        assertEquals(null, voice.spec.dataDir)
    }

    @Test
    fun `a Piper folder without espeak data is refused before anything is copied`() {
        val folder = temp.newFolder("piper-no-espeak").apply {
            write("hi_IN-test.onnx")
            write("tokens.txt", "_ 0\na 14\n\u02C8 120\n\u02CC 121\n".toByteArray())
        }

        val error = importer.import(FolderTree(folder), Lang.HI, "").errorOrNull()

        assertEquals(ImportError.PiperWithoutEspeak, error)
        assertTrue(files.installed().isEmpty())
    }

    @Test
    fun `an MMS folder with letter tokens and no espeak data imports`() {
        val folder = temp.newFolder("mms-letters").apply {
            write("model.onnx")
            write("tokens.txt", "\u0995 1\n\u0996 2\n\u09BE 3\n".toByteArray())
        }

        assertTrue(importer.import(FolderTree(folder), Lang.BN, "") is Outcome.Success)
    }

    @Test
    fun `picking the folder above the voice still finds it`() {
        val parent = temp.newFolder("Download")
        piperFolder().renameTo(File(parent, "vits-piper-hi_IN-test"))
        parent.write("notes.txt")

        val voice = requireNotNull(importer.import(FolderTree(parent), Lang.HI, "").getOrNull())

        assertEquals("hi_IN-test.onnx", voice.spec.model)
        assertTrue(File(voice.dir, "tokens.txt").isFile)
    }

    @Test
    fun `folders that are not one supported voice are refused with a reason`() {
        val empty = temp.newFolder("empty").apply { write("readme.txt") }
        assertEquals(ImportError.NoModel, importer.import(FolderTree(empty), Lang.EN, "").errorOrNull())

        val noTokens = temp.newFolder("no-tokens").apply { write("a.onnx") }
        assertEquals(ImportError.NoTokens, importer.import(FolderTree(noTokens), Lang.EN, "").errorOrNull())

        val two = temp.newFolder("matcha").apply {
            write("acoustic.onnx")
            write("vocos.onnx")
            write("tokens.txt")
        }
        assertTrue(importer.import(FolderTree(two), Lang.EN, "").errorOrNull() is ImportError.SeveralModels)

        val kokoro = temp.newFolder("kokoro").apply {
            write("model.onnx")
            write("voices.bin")
            write("tokens.txt")
            write("lexicon-us-en.txt")
            write("espeak-ng-data/phontab")
        }
        assertEquals(
            ImportError.Unsupported("lexicon-us-en.txt"),
            importer.import(FolderTree(kokoro), Lang.EN, "").errorOrNull(),
        )
        assertTrue(files.installed().isEmpty())
    }

    @Test
    fun `size, file count and free space limits are enforced before copying`() {
        val big = temp.newFolder("big").apply {
            write("model.onnx", ByteArray(200_000))
            write("tokens.txt")
        }
        assertTrue(importer.import(FolderTree(big), Lang.EN, "").errorOrNull() is ImportError.TooBig)

        val many = piperFolder("many").apply { repeat(60) { write("espeak-ng-data/voices/v$it") } }
        assertEquals(ImportError.TooManyFiles, importer.import(FolderTree(many), Lang.EN, "").errorOrNull())

        free = 10
        assertTrue(importer.import(FolderTree(piperFolder("tight")), Lang.EN, "").errorOrNull() is ImportError.NoSpace)
        assertTrue(files.installed().isEmpty())
    }

    @Test
    fun `a folder that cannot be read leaves nothing behind`() {
        val folder = piperFolder()
        val broken = object : ImportTree by FolderTree(folder) {
            override fun open(path: String) =
                if (path == "tokens.txt") throw java.io.IOException("gone") else File(folder, path).inputStream()
        }

        val error = importer.import(broken, Lang.HI, "").errorOrNull()

        assertTrue(error is ImportError.Failed)
        assertTrue(files.installed().isEmpty())
        assertFalse(files.installedRoot.listFiles().orEmpty().any { it.name.endsWith(VoiceFiles.STAGING_SUFFIX) })
        assertTrue(importer.import(FolderTree(folder), Lang.HI, "") is Outcome.Success)
    }
}
