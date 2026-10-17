// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/system/SystemSpeechEngineTest.kt
// Purpose: The system engine's logic over a fake TextToSpeech: voice choice, missing packs, chunking, failures.

package io.github.skm0078.audora.speech.system

import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.errorOrNull
import io.github.skm0078.audora.core.result.getOrNull
import io.github.skm0078.audora.speech.SpeechError
import io.github.skm0078.audora.speech.SpeechError.VoiceUnavailable.Reason
import io.github.skm0078.audora.speech.audio.PcmAudio
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemSpeechEngineTest {
    private class FakeClient : SystemTtsClient {
        var connectResult: Outcome<Unit, SpeechError> = Outcome.Success(Unit)
        var voiceList = listOf<SystemVoice>()
        override var maxInputChars = 4_000
        val synthesized = mutableListOf<Pair<String, String>>()
        val speechRates = mutableListOf<Float>()
        var rates = ArrayDeque<Int>()
        var failWith: String? = null
        var hold: CompletableDeferred<Unit>? = null
        var connects = 0
        var shutDown = false

        override val engineName = "com.example.tts"

        override suspend fun connect(): Outcome<Unit, SpeechError> {
            connects++
            return connectResult
        }

        override fun voices() = voiceList

        override suspend fun synthesize(text: String, voiceName: String, rate: Float): Outcome<PcmAudio, String> {
            synthesized += text to voiceName
            speechRates += rate
            hold?.await()
            failWith?.let { return Outcome.Failure(it) }
            return Outcome.Success(PcmAudio(ShortArray(text.length) { 1 }, rates.removeFirstOrNull() ?: 24_000))
        }

        override fun shutdown() {
            shutDown = true
        }
    }

    private val client = FakeClient()
    private val engine = SystemSpeechEngine(client)

    private fun voice(name: String, lang: String, network: Boolean = false, installed: Boolean = true) =
        SystemVoice(name, lang, network, installed, quality = 300, latency = 300)

    private fun systemVoice(name: String) = VoiceId(EngineId.SYSTEM, name)

    @Test
    fun `lists only offline installed voices in Audora's languages`() = runTest {
        client.voiceList = listOf(
            voice("en-local", "en"),
            voice("en-network", "en", network = true),
            voice("hi-not-downloaded", "hi", installed = false),
            voice("bn-local", "bn"),
            voice("fr-local", "fr"),
        )

        val voices = requireNotNull(engine.voices().getOrNull())

        assertEquals(listOf("en-local" to Lang.EN, "bn-local" to Lang.BN), voices.map { it.id.name to it.lang })
    }

    @Test
    fun `best voice prefers quality, then lower latency`() = runTest {
        client.voiceList = listOf(
            voice("hi-a", "hi"),
            voice("hi-b", "hi").copy(quality = 400, latency = 400),
            voice("hi-c", "hi").copy(quality = 400, latency = 200),
        )

        assertEquals(systemVoice("hi-c"), engine.bestVoice(Lang.HI).getOrNull())
    }

    @Test
    fun `a missing voice pack is reported per language with the reason`() = runTest {
        client.voiceList = listOf(
            voice("hi-not-downloaded", "hi", installed = false),
            voice("bn-network", "bn", network = true),
        )

        val hindi = engine.bestVoice(Lang.HI).errorOrNull() as SpeechError.VoiceUnavailable
        val bengali = engine.bestVoice(Lang.BN).errorOrNull() as SpeechError.VoiceUnavailable
        val english = engine.bestVoice(Lang.EN).errorOrNull() as SpeechError.VoiceUnavailable

        assertEquals(Reason.NOT_INSTALLED to Lang.HI, hindi.reason to hindi.lang)
        assertEquals(Reason.NETWORK_ONLY to Lang.BN, bengali.reason to bengali.lang)
        assertEquals(Reason.NOT_INSTALLED to Lang.EN, english.reason to english.lang)
        assertTrue(hindi.message, hindi.message.contains("Hindi") && hindi.message.contains("Text-to-speech"))
        assertTrue(bengali.message, bengali.message.contains("needs the internet"))
    }

    @Test
    fun `rendering with a voice whose data is missing says so instead of calling the engine`() = runTest {
        client.voiceList =
            listOf(voice("hi-not-downloaded", "hi", installed = false), voice("en-net", "en", network = true))

        val notInstalled = engine.render("नमस्ते", systemVoice("hi-not-downloaded")).errorOrNull()
        val networkOnly = engine.render("Hello.", systemVoice("en-net")).errorOrNull()
        val unknown = engine.render("Hello.", systemVoice("gone")).errorOrNull()
        val wrongEngine = engine.render("Hello.", VoiceId(EngineId.SHERPA, "piper")).errorOrNull()

        assertEquals(Reason.NOT_INSTALLED, (notInstalled as SpeechError.VoiceUnavailable).reason)
        assertEquals(Lang.HI, notInstalled.lang)
        assertEquals(Reason.NETWORK_ONLY, (networkOnly as SpeechError.VoiceUnavailable).reason)
        assertEquals(Reason.UNKNOWN_VOICE, (unknown as SpeechError.VoiceUnavailable).reason)
        assertEquals(Reason.UNKNOWN_VOICE, (wrongEngine as SpeechError.VoiceUnavailable).reason)
        assertTrue(client.synthesized.isEmpty())
    }

    @Test
    fun `renders a sentence in one call when it fits`() = runTest {
        client.voiceList = listOf(voice("en-local", "en"))

        val audio = requireNotNull(engine.render("Hello world.", systemVoice("en-local")).getOrNull())

        assertEquals(listOf("Hello world." to "en-local"), client.synthesized)
        assertEquals(listOf(1f), client.speechRates)
        assertEquals(12, audio.samples.size)
        assertEquals(24_000, audio.sampleRate)
    }

    @Test
    fun `the speaking rate goes to the phone's engine`() = runTest {
        client.voiceList = listOf(voice("en-local", "en"))

        engine.render("Hello world.", systemVoice("en-local"), rate = 0.75f)

        assertEquals(listOf(0.75f), client.speechRates)
    }

    @Test
    fun `text over the engine's limit is chunked and the audio joined in order`() = runTest {
        client.voiceList = listOf(voice("en-local", "en"))
        client.maxInputChars = 20

        val audio = requireNotNull(
            engine.render("First part here. Second part there.", systemVoice("en-local")).getOrNull(),
        )

        assertEquals(listOf("First part here.", "Second part there."), client.synthesized.map { it.first })
        assertEquals("First part here.Second part there.".length, audio.samples.size)
    }

    @Test
    fun `a failing chunk fails the render with the chunk named`() = runTest {
        client.voiceList = listOf(voice("en-local", "en"))
        client.failWith = "synthesis error -3"

        val error = engine.render("Hello.", systemVoice("en-local")).errorOrNull() as SpeechError.SynthesisFailed

        assertEquals("chunk 1/1: synthesis error -3", error.detail)
    }

    @Test
    fun `chunks at different sample rates are a failure, not garbled audio`() = runTest {
        client.voiceList = listOf(voice("en-local", "en"))
        client.maxInputChars = 20
        client.rates = ArrayDeque(listOf(24_000, 22_050))

        val error = engine.render("First part here. Second part there.", systemVoice("en-local")).errorOrNull()

        assertTrue(error is SpeechError.SynthesisFailed)
    }

    @Test
    fun `blank text renders silence without calling the engine`() = runTest {
        client.voiceList = listOf(voice("en-local", "en"))

        val audio = requireNotNull(engine.render(" ", systemVoice("en-local")).getOrNull())

        assertArrayEquals(ShortArray(0), audio.samples)
        assertTrue(client.synthesized.isEmpty())
    }

    @Test
    fun `no TTS engine on the phone is reported as engine unavailable`() = runTest {
        val noEngine = SpeechError.EngineUnavailable(EngineId.SYSTEM, "no text-to-speech engine is installed")
        client.connectResult = Outcome.Failure(noEngine)

        assertEquals(noEngine, engine.voices().errorOrNull())
        assertEquals(noEngine, engine.render("Hi.", systemVoice("en-local")).errorOrNull())
        assertEquals(noEngine, engine.bestVoice(Lang.EN).errorOrNull())
    }

    @Test
    fun `cancelling a render cancels the engine call and the engine stays usable`() = runTest {
        client.voiceList = listOf(voice("en-local", "en"))
        client.hold = CompletableDeferred()

        val render = async { engine.render("Hello.", systemVoice("en-local")) }
        while (client.synthesized.isEmpty()) yield()
        render.cancelAndJoin()

        assertTrue(render.isCancelled)
        client.hold = null
        assertTrue(engine.render("Again.", systemVoice("en-local")) is Outcome.Success)
    }

    @Test
    fun `release shuts the engine down and later calls fail typed`() = runTest {
        engine.release()

        assertTrue(client.shutDown)
        assertTrue(engine.render("Hi.", systemVoice("en-local")).errorOrNull() is SpeechError.EngineUnavailable)
        assertFalse(engine.voices() is Outcome.Success)
    }
}
