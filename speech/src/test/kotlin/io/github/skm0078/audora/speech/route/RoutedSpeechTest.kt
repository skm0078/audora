// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/route/RoutedSpeechTest.kt
// Purpose: A mixed sentence is voiced part by part and joined into one clip; a missing voice falls back (P3-01).

package io.github.skm0078.audora.speech.route

import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.speech.RenderResult
import io.github.skm0078.audora.speech.SpeechEngine
import io.github.skm0078.audora.speech.SpeechError
import io.github.skm0078.audora.speech.VoiceInfo
import io.github.skm0078.audora.speech.audio.PcmAudio
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoutedSpeechTest {
    /** Renders one sample per character at the voice's rate, so lengths show which voice read what. */
    private class RecordingEngine(override val id: EngineId) : SpeechEngine {
        val calls = mutableListOf<Pair<String, String>>()

        override fun supports(lang: Lang): Boolean = true

        override suspend fun voices(): Outcome<List<VoiceInfo>, SpeechError> = Outcome.Success(emptyList())

        override suspend fun render(text: String, voice: VoiceId): RenderResult {
            calls.add(voice.name to text)
            return Outcome.Success(PcmAudio(ShortArray(text.length) { SAMPLE }, RATES.getValue(voice.name)))
        }

        override fun release() = Unit
    }

    private class FakeVoices(private val missing: Set<Lang> = emptySet()) : VoiceSource {
        val sherpa = RecordingEngine(EngineId.SHERPA)
        val system = RecordingEngine(EngineId.SYSTEM)

        override suspend fun voiceFor(lang: Lang): Outcome<VoiceId, SpeechError> = when {
            lang in missing -> Outcome.Failure(
                SpeechError.VoiceUnavailable(
                    EngineId.SYSTEM,
                    SpeechError.VoiceUnavailable.Reason.NOT_INSTALLED,
                    lang = lang,
                ),
            )

            lang == Lang.EN -> Outcome.Success(VoiceId(EngineId.SHERPA, EN_VOICE))

            else -> Outcome.Success(VoiceId(EngineId.SYSTEM, HI_VOICE))
        }

        override fun engine(id: EngineId): Outcome<SpeechEngine, SpeechError> =
            Outcome.Success(if (id == EngineId.SHERPA) sherpa else system)
    }

    @Test
    fun `mixed sentence is voiced part by part and joined at the higher rate`() = runTest {
        val voices = FakeVoices()
        val speech = RoutedSpeech(voices, ScriptRouter())

        val result = speech.render("Read the world, इसी घोषणा को ।", Lang.EN)

        val routed = (result as Outcome.Success).value
        assertEquals(listOf(EN_VOICE to "Read the world,"), voices.sherpa.calls)
        assertEquals(listOf(HI_VOICE to "इसी घोषणा को ।"), voices.system.calls)
        assertEquals(listOf(Lang.EN, Lang.HI), routed.langs)
        assertNull(routed.fallback)
        assertEquals(EN_RATE, routed.audio.sampleRate)
        // 15 samples at 22 050 Hz plus 14 at 16 000 Hz upsampled to 22 050 Hz.
        assertEquals(15 + 14 * EN_RATE / HI_RATE, routed.audio.samples.size)
    }

    @Test
    fun `single-script sentence is one render`() = runTest {
        val voices = FakeVoices()

        val routed = (RoutedSpeech(voices, ScriptRouter()).render("मानव अधिकार ।", Lang.HI) as Outcome.Success).value

        assertEquals(listOf(HI_VOICE to "मानव अधिकार ।"), voices.system.calls)
        assertEquals(listOf(Lang.HI), routed.langs)
        assertEquals(HI_RATE, routed.audio.sampleRate)
    }

    @Test
    fun `missing voice for a minority part reads the whole sentence with the sentence's voice`() = runTest {
        val voices = FakeVoices(missing = setOf(Lang.HI))
        val text = "The word नमस्ते means hello."

        val routed = (RoutedSpeech(voices, ScriptRouter()).render(text, Lang.EN) as Outcome.Success).value

        assertEquals(listOf(EN_VOICE to "The word", EN_VOICE to text), voices.sherpa.calls)
        assertEquals(listOf(Lang.EN), routed.langs)
        assertEquals(Lang.HI, (routed.fallback as SpeechError.VoiceUnavailable).lang)
    }

    @Test
    fun `missing voice for the sentence's own language fails the sentence`() = runTest {
        val voices = FakeVoices(missing = setOf(Lang.HI))

        val result = RoutedSpeech(voices, ScriptRouter()).render("मानव अधिकारों की घोषणा the rule of law ।", Lang.HI)

        assertEquals(Lang.HI, ((result as Outcome.Failure).error as SpeechError.VoiceUnavailable).lang)
    }

    @Test
    fun `blank text renders once with the sentence's voice`() = runTest {
        val voices = FakeVoices()

        val routed = (RoutedSpeech(voices, ScriptRouter()).render(" ", Lang.EN) as Outcome.Success).value

        assertEquals(listOf(EN_VOICE to " "), voices.sherpa.calls)
        assertEquals(1, routed.audio.samples.size)
    }

    private companion object {
        const val EN_VOICE = "en-voice"
        const val HI_VOICE = "hi-voice"
        const val EN_RATE = 22_050
        const val HI_RATE = 16_000
        const val SAMPLE: Short = 100
        val RATES = mapOf(EN_VOICE to EN_RATE, HI_VOICE to HI_RATE)
    }
}
