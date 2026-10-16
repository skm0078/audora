// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/voice/EngineRendererTest.kt
// Purpose: The renderer hands the voice normalized text, reads headings as headings and ends each clip with its pause.

package io.github.skm0078.audora.playback.voice

import io.github.skm0078.audora.core.config.PaceTiming
import io.github.skm0078.audora.core.config.SpeechStyleConfig
import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.Pace
import io.github.skm0078.audora.core.model.SpeechStyle
import io.github.skm0078.audora.core.model.UnitKind
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.playback.TEST_SAMPLE_RATE
import io.github.skm0078.audora.playback.unit
import io.github.skm0078.audora.speech.RenderResult
import io.github.skm0078.audora.speech.SpeechEngine
import io.github.skm0078.audora.speech.SpeechError
import io.github.skm0078.audora.speech.VoiceInfo
import io.github.skm0078.audora.speech.audio.PcmAudio
import io.github.skm0078.audora.speech.route.VoiceSource
import io.github.skm0078.audora.speech.voices.MeasuredSpeeds
import io.github.skm0078.audora.speech.voices.SpeedSample
import io.github.skm0078.audora.speech.voices.VoiceSpeed
import org.junit.Assert.assertEquals
import org.junit.Test

class EngineRendererTest {
    /** One sample per character at the fixtures' 1 kHz, so a clip's length shows the spoken text's length. */
    private class RecordingVoices :
        VoiceSource,
        SpeechEngine {
        val spoken = ArrayList<String>()
        val rates = ArrayList<Float>()
        override val id = EngineId.SHERPA

        override suspend fun voiceFor(lang: Lang): Outcome<VoiceId, SpeechError> = Outcome.Success(VOICE)

        override fun engine(id: EngineId): Outcome<SpeechEngine, SpeechError> = Outcome.Success(this)

        override fun supports(lang: Lang): Boolean = true

        override suspend fun voices(): Outcome<List<VoiceInfo>, SpeechError> = Outcome.Success(emptyList())

        override suspend fun render(text: String, voice: VoiceId, rate: Float): RenderResult {
            spoken.add(text)
            rates.add(rate)
            return Outcome.Success(PcmAudio(ShortArray(text.length) { SAMPLE }, TEST_SAMPLE_RATE))
        }

        override fun release() = Unit
    }

    private val recorded = ArrayList<SpeedSample>()
    private val speeds = object : MeasuredSpeeds {
        override fun get(voice: VoiceId): VoiceSpeed? = null

        override fun record(voice: VoiceId, sample: SpeedSample) {
            recorded.add(sample)
        }
    }

    @Test
    fun `the voice gets normalized text and the clip ends with the sentence's pause`() {
        val voices = RecordingVoices()
        val renderer = EngineRenderer(voices, style = { SpeechStyle(pace = Pace.NORMAL) }, styleConfig = flatPaces)

        val audio = (
            renderer.render(
                unit(0, 0).copy(text = "Pay ₹5 now.", pauseAfterMs = 400),
            ) as Outcome.Success
            ).value

        assertEquals(listOf("Pay five rupees now."), voices.spoken)
        // 20 characters spoken at 1 kHz, then 400 ms of silence.
        assertEquals(20 + 400, audio.samples.size)
        assertEquals(20, audio.samples.count { it == SAMPLE })
        assertEquals(0, audio.samples.drop(20).count { it != 0.toShort() })
    }

    @Test
    fun `the voice speaks the style's rate up to the model's cap`() {
        val voices = RecordingVoices()
        var style = SpeechStyle(rate = 1.33f)
        val renderer = EngineRenderer(voices, style = { style }, styleConfig = SpeechStyleConfig(maxModelRate = 1.5f))

        renderer.render(unit(0, 0).copy(text = "Hello."))
        style = SpeechStyle(rate = 9f)
        renderer.render(unit(0, 0).copy(text = "Hello."))

        // Snapped to the slider's step; above the cap the player stretches the rest.
        assertEquals(listOf(1.35f, 1.5f), voices.rates)
    }

    @Test
    fun `the style's pace sizes the silence after the sentence`() {
        val voices = RecordingVoices()
        val config = SpeechStyleConfig(
            paces = mapOf(
                Pace.TIGHT to PaceTiming(sentenceGapMs = 0, pauseScale = 0.5f),
                Pace.NORMAL to PaceTiming(sentenceGapMs = 100, pauseScale = 1f),
                Pace.RELAXED to PaceTiming(sentenceGapMs = 300, pauseScale = 2f),
            ),
        )
        var style = SpeechStyle(pace = Pace.RELAXED)
        val renderer = EngineRenderer(voices, style = { style }, styleConfig = config)
        val sentence = unit(0, 0).copy(text = "Hello.", pauseAfterMs = 400)

        val relaxed = (renderer.render(sentence) as Outcome.Success).value
        style = SpeechStyle(pace = Pace.TIGHT)
        val tight = (renderer.render(sentence) as Outcome.Success).value

        // 6 characters at 1 kHz, then the pace's gap plus the scaled pause.
        assertEquals(6 + 300 + 800, relaxed.samples.size)
        assertEquals(6 + 0 + 200, tight.samples.size)
    }

    @Test
    fun `a heading is normalized as a heading, body text as body text`() {
        val voices = RecordingVoices()
        val renderer = EngineRenderer(voices)

        renderer.render(unit(0, 0).copy(text = "IV. Results", kind = UnitKind.HEADING))
        renderer.render(unit(0, 1).copy(text = "IV. Results", kind = UnitKind.LIST_ITEM))

        assertEquals(listOf("four. Results", "IV. Results"), voices.spoken)
    }

    @Test
    fun `render speed is measured on the voice's audio, not its pause`() {
        val renderer = EngineRenderer(RecordingVoices(), speed = RenderSpeedTracker { speeds })
        val paused = unit(0, 0).copy(text = "Sentence one.", pauseAfterMs = 1_000)

        renderer.render(paused)
        renderer.render(paused.copy(index = 1))

        // The first render loads the voice and is not measured; the second's 13 characters are 13 ms of audio.
        assertEquals(0.013, recorded.single().audioSeconds, EPS)
    }

    /** Normal pace adds nothing, so a clip's silence is exactly the sentence's own pause. */
    private val flatPaces = SpeechStyleConfig(
        paces = Pace.entries.associateWith { PaceTiming(sentenceGapMs = 0, pauseScale = 1f) },
    )

    private companion object {
        val VOICE = VoiceId(EngineId.SHERPA, "en-voice")
        const val SAMPLE: Short = 100
        const val EPS = 1e-9
    }
}
