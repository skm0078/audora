// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/sherpa/SherpaVoiceManifestTest.kt
// Purpose: voice.properties parsing: Piper, Kitten, Kokoro and Supertonic manifests, defaults and every rejection.

package io.github.skm0078.audora.speech.sherpa

import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.errorOrNull
import io.github.skm0078.audora.core.result.getOrNull
import java.io.StringReader
import java.util.Properties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SherpaVoiceManifestTest {
    private val piper = """
        contentVersion=3dfb4b75+dicts=bn,en,hi
        dataDir=espeak-ng-data
        family=vits
        id=piper-en-ljspeech
        label=English (US), Piper ljspeech
        lang=en
        licence=Piper voice (MIT), trained on LJ Speech (public domain)
        model=en_US-ljspeech-medium.onnx
        speakerId=0
        tokens=tokens.txt
    """.trimIndent()

    @Test
    fun `parses the Piper manifest the build writes`() {
        val spec = requireNotNull(parse(piper).getOrNull())

        assertEquals(VoiceId(EngineId.SHERPA, "piper-en-ljspeech"), spec.voiceId)
        assertEquals(Lang.EN, spec.lang)
        assertEquals(SherpaFamily.VITS, spec.family)
        assertEquals(listOf("en_US-ljspeech-medium.onnx", "tokens.txt"), spec.requiredFiles)
        assertEquals("espeak-ng-data", spec.dataDir)
        assertNull(spec.voices)
        assertEquals("3dfb4b75+dicts=bn,en,hi", spec.contentVersion)
        assertEquals("English (US), Piper ljspeech", spec.toVoiceInfo().label)
    }

    @Test
    fun `parses a Kitten manifest with its voices file and speaker`() {
        val kitten = piper.replace("family=vits", "family=kitten").replace("speakerId=0", "speakerId=7") +
            "\nvoices=voices.bin"
        val spec = requireNotNull(parse(kitten).getOrNull())

        assertEquals(SherpaFamily.KITTEN, spec.family)
        assertEquals(7, spec.speakerId)
        assertEquals(listOf("en_US-ljspeech-medium.onnx", "tokens.txt", "voices.bin"), spec.requiredFiles)
    }

    @Test
    fun `parses a Kokoro manifest with its lexicon and language`() {
        val kokoro = piper.replace("family=vits", "family=kokoro").replace("speakerId=0", "speakerId=3")
            .replace("model=en_US-ljspeech-medium.onnx", "model=model.int8.onnx") +
            "\nvoices=voices.bin\nlexicon=lexicon-us-en.txt\nengineLang=en-us"
        val spec = requireNotNull(parse(kokoro).getOrNull())

        assertEquals(SherpaFamily.KOKORO, spec.family)
        assertEquals("en-us", spec.engineLang)
        assertEquals(
            listOf("model.int8.onnx", "tokens.txt", "voices.bin", "lexicon-us-en.txt"),
            spec.requiredFiles,
        )
        assertNull(spec.numSteps)
    }

    @Test
    fun `parses a Supertonic manifest, which needs its fixed parts and no tokens`() {
        val spec = requireNotNull(parse(supertonic).getOrNull())

        assertEquals(SherpaFamily.SUPERTONIC, spec.family)
        assertEquals(Lang.HI, spec.lang)
        assertEquals("hi", spec.engineLang)
        assertEquals(5, spec.numSteps)
        assertEquals(listOf("tts.json", "voice.bin") + SupertonicParts.ALL, spec.requiredFiles)
        assertTrue("tokens.txt" !in spec.requiredFiles)
    }

    @Test
    fun `reads a voice's own thread count, absent by default`() {
        assertEquals(4, parse("$piper\nthreads=4").getOrNull()?.threads)
        assertNull(parse(piper).getOrNull()?.threads)
    }

    @Test
    fun `tokens and speaker default, data dir is optional`() {
        val minimal = piper.lines().filterNot { it.startsWith("tokens=") || it.startsWith("speakerId=") }
            .filterNot { it.startsWith("dataDir=") }.joinToString("\n")
        val spec = requireNotNull(parse(minimal).getOrNull())

        assertEquals("tokens.txt", spec.tokens)
        assertEquals(0, spec.speakerId)
        assertNull(spec.dataDir)
    }

    @Test
    fun `rejects broken manifests with a reason`() {
        val cases = mapOf(
            "missing model" to piper.lines().filterNot { it.startsWith("model=") }.joinToString("\n"),
            "blank id" to piper.replace("id=piper-en-ljspeech", "id= "),
            "unknown lang" to piper.replace("lang=en", "lang=fr"),
            "unknown family" to piper.replace("family=vits", "family=opera"),
            "kokoro without voices" to piper.replace("family=vits", "family=kokoro") + "\nengineLang=en-us",
            "kokoro without engineLang" to piper.replace("family=vits", "family=kokoro") + "\nvoices=voices.bin",
            "supertonic without engineLang" to supertonic.lines().filterNot { it.startsWith("engineLang=") }
                .joinToString("\n"),
            "zero steps" to supertonic.replace("numSteps=5", "numSteps=0"),
            "steps not a number" to supertonic.replace("numSteps=5", "numSteps=five"),
            "bad speaker" to piper.replace("speakerId=0", "speakerId=-1"),
            "zero threads" to "$piper\nthreads=0",
            "kitten without voices" to piper.replace("family=vits", "family=kitten"),
        )
        for ((name, text) in cases) {
            val error = parse(text).errorOrNull()
            assertNotNull(name, error)
            assertTrue("$name: $error", error.orEmpty().startsWith("voice.properties"))
        }
    }

    private val supertonic = """
        contentVersion=24d94e841d36
        engineLang=hi
        family=supertonic
        id=supertonic-hi
        label=Hindi, Supertonic 3 (premium)
        lang=hi
        licence=OpenRAIL-M
        model=tts.json
        numSteps=5
        speakerId=2
        voices=voice.bin
    """.trimIndent()

    private fun parse(text: String): Outcome<SherpaVoiceSpec, String> =
        SherpaVoiceManifest.parse(Properties().apply { load(StringReader(text)) })
}
