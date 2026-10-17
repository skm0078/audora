// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/sherpa/SherpaModelConfigTest.kt
// Purpose: The sherpa-onnx configs built for Kokoro and Supertonic voices match what S1 measured (P4-02).

package io.github.skm0078.audora.speech.sherpa

import io.github.skm0078.audora.core.model.Lang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SherpaModelConfigTest {
    private val base = SherpaVoiceSpec(
        id = "v",
        label = "V",
        lang = Lang.HI,
        family = SherpaFamily.VITS,
        model = "m.onnx",
        tokens = "tokens.txt",
        voices = null,
        dataDir = null,
        speakerId = 2,
        licence = "L",
        contentVersion = "1",
    )
    private val kokoro = base.copy(
        family = SherpaFamily.KOKORO,
        model = "model.int8.onnx",
        voices = "voices.bin",
        lexicon = "lexicon-us-en.txt",
        engineLang = "en-us",
    )
    private val supertonic = base.copy(
        family = SherpaFamily.SUPERTONIC,
        model = "tts.json",
        voices = "voice.bin",
        engineLang = "hi",
        numSteps = 5,
    )

    @Test
    fun `kokoro gets its lexicon and language in the model config`() {
        val config = sherpaModelConfig(kokoro, threads = 2, dataDir = "/v/espeak-ng-data") { "/v/$it" }.kokoro

        assertEquals("/v/model.int8.onnx", config.model)
        assertEquals("/v/voices.bin", config.voices)
        assertEquals("/v/tokens.txt", config.tokens)
        assertEquals("/v/lexicon-us-en.txt", config.lexicon)
        assertEquals("/v/espeak-ng-data", config.dataDir)
        assertEquals("en-us", config.lang)
    }

    @Test
    fun `supertonic gets its five parts, tts json and voice style`() {
        val config = sherpaModelConfig(supertonic, threads = 2, dataDir = "") { "/v/$it" }.supertonic

        assertEquals("/v/tts.json", config.ttsJson)
        assertEquals("/v/voice.bin", config.voiceStyle)
        assertEquals("/v/${SupertonicParts.DURATION_PREDICTOR}", config.durationPredictor)
        assertEquals("/v/${SupertonicParts.TEXT_ENCODER}", config.textEncoder)
        assertEquals("/v/${SupertonicParts.VECTOR_ESTIMATOR}", config.vectorEstimator)
        assertEquals("/v/${SupertonicParts.VOCODER}", config.vocoder)
        assertEquals("/v/${SupertonicParts.UNICODE_INDEXER}", config.unicodeIndexer)
    }

    @Test
    fun `only supertonic is told its language and step count per render`() {
        val forSupertonic = sherpaGenerationConfig(supertonic, speakerId = 2, rate = 1f)
        val forKokoro = sherpaGenerationConfig(kokoro, speakerId = 3, rate = 1f)

        assertEquals(mapOf("lang" to "hi"), forSupertonic.extra)
        assertEquals(5, forSupertonic.numSteps)
        assertEquals(2, forSupertonic.sid)
        assertEquals(3, forKokoro.sid)
        assertTrue(forKokoro.extra.isNullOrEmpty())
    }

    @Test
    fun `every family gets the speaking rate and keeps the voice's own pauses`() {
        val supertonicConfig = sherpaGenerationConfig(supertonic, speakerId = 0, rate = 1.5f)
        val kokoroConfig = sherpaGenerationConfig(kokoro, speakerId = 0, rate = 0.75f)

        assertEquals(1.5f, supertonicConfig.speed)
        assertEquals(0.75f, kokoroConfig.speed)
        assertEquals(1f, supertonicConfig.silenceScale)
        assertEquals(1f, kokoroConfig.silenceScale)
    }
}
