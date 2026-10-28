// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/route/RoutedSpeechTest.kt
// Purpose: A mixed sentence is voiced part by part and joined into one clip; a missing voice falls back (P3-01).

package io.github.skm0078.audora.speech.route

import io.github.skm0078.audora.core.model.ClauseMark
import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.speech.RenderResult
import io.github.skm0078.audora.speech.SpeechEngine
import io.github.skm0078.audora.speech.SpeechError
import io.github.skm0078.audora.speech.VoiceInfo
import io.github.skm0078.audora.speech.audio.PcmAudio
import io.github.skm0078.audora.speech.normalize.TextRole
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutedSpeechTest {
    /** Renders one sample per character at the voice's rate, so lengths show which voice read what. */
    private class RecordingEngine(override val id: EngineId) : SpeechEngine {
        val calls = mutableListOf<Pair<String, String>>()
        val rates = mutableListOf<Float>()

        override fun supports(lang: Lang): Boolean = true

        override suspend fun voices(): Outcome<List<VoiceInfo>, SpeechError> = Outcome.Success(emptyList())

        override suspend fun render(text: String, voice: VoiceId, rate: Float): RenderResult {
            calls.add(voice.name to text)
            rates.add(rate)
            return Outcome.Success(PcmAudio(ShortArray(text.length) { SAMPLE }, RATES.getValue(voice.name)))
        }

        override fun release() = Unit
    }

    private class FakeVoices(
        private val missing: Set<Lang> = emptySet(),
        private val phones: Set<String> = emptySet(),
    ) : VoiceSource {
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

        override suspend fun phoneVoiceFor(language: String): Outcome<VoiceId, SpeechError> = if (language in phones) {
            Outcome.Success(VoiceId(EngineId.SYSTEM, phoneVoice(language)))
        } else {
            Outcome.Failure(
                SpeechError.VoiceUnavailable(EngineId.SYSTEM, SpeechError.VoiceUnavailable.Reason.NOT_INSTALLED),
            )
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
        assertEquals(listOf(VoiceId(EngineId.SHERPA, EN_VOICE), VoiceId(EngineId.SYSTEM, HI_VOICE)), routed.voices)
        assertNull(routed.fallback)
        assertEquals(EN_RATE, routed.audio.sampleRate)
        // 15 samples at 22 050 Hz plus 14 at 16 000 Hz upsampled to 22 050 Hz.
        assertEquals(15 + 14 * EN_RATE / HI_RATE, routed.audio.samples.size)
    }

    @Test
    fun `the speaking rate reaches every part's voice`() = runTest {
        val voices = FakeVoices()

        RoutedSpeech(voices, ScriptRouter()).render("Read the world, इसी घोषणा को ।", Lang.EN, rate = 1.5f)

        assertEquals(listOf(1.5f), voices.sherpa.rates)
        assertEquals(listOf(1.5f), voices.system.rates)
    }

    @Test
    fun `with clause prosody each piece is voiced alone, with the mark's silence between`() = runTest {
        val voices = FakeVoices()
        val prosody = ClauseProsody({ mark -> if (mark == ClauseMark.DASH) 100 else 40 }, asideGainDb = -6f)

        val routed = (
            RoutedSpeech(voices, ScriptRouter()).render("Read — the (whole) world.", Lang.EN, prosody = prosody)
                as Outcome.Success
            ).value

        assertEquals(listOf("Read", "the", "whole", "world.").map { EN_VOICE to it }, voices.sherpa.calls)
        assertEquals(listOf(Lang.EN), routed.langs)
        assertEquals(180L, routed.pausesMs)
        val samples = routed.audio.samples
        assertEquals((100 + 40 + 40) * EN_RATE / 1_000, samples.count { it == 0.toShort() })
        assertEquals("whole".length, samples.count { it == (SAMPLE / 2).toShort() })
        assertEquals("Readtheworld.".length, samples.count { it == SAMPLE })
    }

    @Test
    fun `the fallback reads the whole sentence uncut`() = runTest {
        val voices = FakeVoices(missing = setOf(Lang.HI))
        val prosody = ClauseProsody({ 100 }, asideGainDb = 0f)
        val text = "The word (नमस्ते) means hello."

        val routed = (RoutedSpeech(voices, ScriptRouter()).render(text, Lang.EN, prosody = prosody) as Outcome.Success)
            .value

        assertEquals(EN_VOICE to text, voices.sherpa.calls.last())
        assertEquals(0L, routed.pausesMs)
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
        assertEquals(listOf(VoiceId(EngineId.SHERPA, EN_VOICE)), routed.voices)
        assertEquals(Lang.HI, (routed.fallback as SpeechError.VoiceUnavailable).lang)
    }

    @Test
    fun `missing voice for the sentence's own language fails the sentence`() = runTest {
        val voices = FakeVoices(missing = setOf(Lang.HI))

        val result = RoutedSpeech(voices, ScriptRouter()).render("मानव अधिकारों की घोषणा the rule of law ।", Lang.HI)

        assertEquals(Lang.HI, ((result as Outcome.Failure).error as SpeechError.VoiceUnavailable).lang)
    }

    @Test
    fun `each part is normalized for its own language`() = runTest {
        val voices = FakeVoices()

        RoutedSpeech(voices, ScriptRouter()).render("Read the world, इसी के २ भाग हैं ।", Lang.EN)

        assertEquals(listOf(EN_VOICE to "Read the world,"), voices.sherpa.calls)
        assertEquals(listOf(HI_VOICE to "इसी के दो भाग हैं ।"), voices.system.calls)
    }

    @Test
    fun `English parts are normalized too, and a heading reads its Roman numeral`() = runTest {
        val voices = FakeVoices()
        val speech = RoutedSpeech(voices, ScriptRouter())

        speech.render("Dr. Rao paid ₹1,50,000 on 15/08/1947.", Lang.EN)
        speech.render("IV. Results", Lang.EN, TextRole.HEADING)
        speech.render("IV. Results", Lang.EN)

        assertEquals(
            listOf(
                EN_VOICE to "Doctor Rao paid one lakh fifty thousand rupees on fifteenth August nineteen forty-seven.",
                EN_VOICE to "four. Results",
                EN_VOICE to "IV. Results",
            ),
            voices.sherpa.calls,
        )
    }

    @Test
    fun `blank text renders once with the sentence's voice`() = runTest {
        val voices = FakeVoices()

        RoutedSpeech(voices, ScriptRouter()).render(" ", Lang.EN) as Outcome.Success

        assertEquals(listOf(EN_VOICE to ""), voices.sherpa.calls)
    }

    @Test
    fun `a sentence of symbols no voice can say is sent as nothing, never as the symbols`() = runTest {
        val voices = FakeVoices()
        val speech = RoutedSpeech(voices, ScriptRouter())

        speech.render("* * *", Lang.EN)
        speech.render("★★★ 👍", Lang.HI)

        assertEquals(listOf(EN_VOICE to ""), voices.sherpa.calls)
        assertEquals(listOf(HI_VOICE to ""), voices.system.calls)
    }

    @Test
    fun `a French book reads with the phone's French voice, as written (ADR 2026-10-26)`() = runTest {
        val voices = FakeVoices(phones = setOf("fr"))

        val routed = RoutedSpeech(voices, ScriptRouter())
            .render("Le 15/08/1947, il a payé 20 €.", Lang.EN, bookLanguage = "fr-FR")

        // No English normalizer: the phone's French engine says the date and the sum in French.
        assertEquals(listOf(phoneVoice("fr") to "Le 15/08/1947, il a payé 20 €."), voices.system.calls)
        assertEquals(emptyList<Pair<String, String>>(), voices.sherpa.calls)
        assertEquals(listOf(VoiceId(EngineId.SYSTEM, phoneVoice("fr"))), (routed as Outcome.Success).value.voices)
    }

    @Test
    fun `a book in English, Hindi or Bengali reads as before`() = runTest {
        val voices = FakeVoices(phones = setOf("en", "fr"))

        RoutedSpeech(voices, ScriptRouter()).render("Dr. Rao.", Lang.EN, bookLanguage = "en")

        assertEquals(listOf(EN_VOICE to "Doctor Rao."), voices.sherpa.calls)
        assertEquals(emptyList<Pair<String, String>>(), voices.system.calls)
    }

    @Test
    fun `Tamil inside an English sentence gets the phone's Tamil voice`() = runTest {
        val voices = FakeVoices(phones = setOf("ta"))

        RoutedSpeech(voices, ScriptRouter()).render("The word is வணக்கம் here.", Lang.EN)

        assertEquals(listOf(EN_VOICE to "The word is", EN_VOICE to "here."), voices.sherpa.calls)
        assertEquals(listOf(phoneVoice("ta") to "வணக்கம்"), voices.system.calls)
    }

    @Test
    fun `no phone voice for the book's language reads the sentence whole with its own voice, as before`() = runTest {
        val voices = FakeVoices()

        val routed = RoutedSpeech(voices, ScriptRouter()).render("Le chat dort.", Lang.EN, bookLanguage = "fr")

        assertEquals(listOf(EN_VOICE to "Le chat dort."), voices.sherpa.calls)
        assertEquals(listOf(Lang.EN), (routed as Outcome.Success).value.langs)
        assertTrue(routed.value.fallback is SpeechError.VoiceUnavailable)
    }

    @Test
    fun `clause pauses keep the phone voice on every piece`() = runTest {
        val voices = FakeVoices(phones = setOf("fr"))
        val prosody = ClauseProsody(silenceMs = { 100 }, asideGainDb = 0f)

        RoutedSpeech(voices, ScriptRouter())
            .render("Il pleut; je reste ici.", Lang.EN, prosody = prosody, bookLanguage = "fr")

        assertEquals(listOf(phoneVoice("fr") to "Il pleut", phoneVoice("fr") to "je reste ici."), voices.system.calls)
    }

    private companion object {
        const val EN_VOICE = "en-voice"
        const val HI_VOICE = "hi-voice"
        const val EN_RATE = 22_050
        const val HI_RATE = 16_000
        const val PHONE_RATE = 24_000
        const val SAMPLE: Short = 100
        val RATES = mapOf(EN_VOICE to EN_RATE, HI_VOICE to HI_RATE).withDefault { PHONE_RATE }

        fun phoneVoice(language: String) = "phone-$language"
    }
}
