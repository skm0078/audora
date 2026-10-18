// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/voice/ShapedRenderTest.kt
// Purpose: The renderer applies the style's voice shaping: pause trim, smart speed, even loudness and boost (9, 10).

package io.github.skm0078.audora.playback.voice

import io.github.skm0078.audora.core.config.PaceTiming
import io.github.skm0078.audora.core.config.SpeechStyleConfig
import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.Pace
import io.github.skm0078.audora.core.model.SpeechStyle
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.core.model.VoiceShaping
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.playback.TEST_SAMPLE_RATE
import io.github.skm0078.audora.playback.queue.UnitKey
import io.github.skm0078.audora.playback.unit
import io.github.skm0078.audora.speech.RenderResult
import io.github.skm0078.audora.speech.SpeechEngine
import io.github.skm0078.audora.speech.SpeechError
import io.github.skm0078.audora.speech.VoiceInfo
import io.github.skm0078.audora.speech.audio.PcmAudio
import io.github.skm0078.audora.speech.route.VoiceSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShapedRenderTest {
    /** Every sentence: 300 ms of voice, a 600 ms silence of the voice's own, 300 ms of voice (1 kHz fixtures). */
    private object PausingVoice : VoiceSource, SpeechEngine {
        override val id = EngineId.SHERPA

        override suspend fun voiceFor(lang: Lang): Outcome<VoiceId, SpeechError> = Outcome.Success(VOICE)

        override fun engine(id: EngineId): Outcome<SpeechEngine, SpeechError> = Outcome.Success(this)

        override fun supports(lang: Lang): Boolean = true

        override suspend fun voices(): Outcome<List<VoiceInfo>, SpeechError> = Outcome.Success(emptyList())

        override suspend fun render(text: String, voice: VoiceId, rate: Float): RenderResult {
            val samples = ShortArray(VOICED + PAUSE + VOICED) { i -> if (i in VOICED until VOICED + PAUSE) 0 else LOUD }
            return Outcome.Success(PcmAudio(samples, TEST_SAMPLE_RATE))
        }

        override fun release() = Unit
    }

    private fun render(
        shaping: VoiceShaping,
        rate: Float = SpeechStyle.NORMAL_RATE,
        text: String = "Hello there.",
        clausePauses: Boolean = false,
    ): PcmAudio {
        val style = SpeechStyle(rate = rate, pace = Pace.NORMAL, clausePauses = clausePauses, shaping = shaping)
        val renderer = EngineRenderer(PausingVoice, style = { style }, styleConfig = flatPaces)
        return (renderer.render(unit(0, 0).copy(text = text, pauseAfterMs = 0)) as Outcome.Success).value
    }

    @Test
    fun `pause trim shortens the voice's own long silence`() {
        val untouched = render(VoiceShaping(evenLoudness = false, trimPauses = false))
        val trimmed = render(VoiceShaping(evenLoudness = false, trimPauses = true))
        assertEquals(VOICED + PAUSE + VOICED, untouched.samples.size)
        assertEquals(VOICED + LONG_PAUSE_MS + VOICED, trimmed.samples.size)
    }

    @Test
    fun `smart speed shortens the voice by what the player would have stretched`() {
        val plain = render(VoiceShaping(evenLoudness = false), rate = 3f)
        val smart = render(VoiceShaping(evenLoudness = false, smartSpeed = true), rate = 3f)
        assertEquals(VOICED + PAUSE + VOICED, plain.samples.size)
        // The model is at its 1.5 cap; the other 2x is the stretch's.
        assertEquals((VOICED + PAUSE + VOICED) / 2f, smart.samples.size.toFloat(), (VOICED + PAUSE + VOICED) * 0.01f)
    }

    @Test
    fun `with smart speed Audora's own clause pauses are heard as long as with the player's stretch`() {
        val text = "Hello there, said the voice; and then, at last, it stopped."
        val plain = render(VoiceShaping(evenLoudness = false), rate = 3f, text = text, clausePauses = true)
        val smart = render(VoiceShaping(evenLoudness = false, smartSpeed = true), 3f, text, clausePauses = true)
        val unpaused = render(VoiceShaping(evenLoudness = false), rate = 3f, text = text, clausePauses = false)
        assertTrue("clause pauses were added", plain.samples.size > unpaused.samples.size)
        // The player plays the plain one at 2x; the smart one is already that short, its pauses included.
        assertEquals(plain.samples.size / 2f, smart.samples.size.toFloat(), plain.samples.size * 0.01f)
    }

    @Test
    fun `the replayed sentence renders with the replay's style, the rest with the listener's`() {
        val listener =
            SpeechStyle(rate = 3f, pace = Pace.NORMAL, shaping = VoiceShaping(evenLoudness = false, smartSpeed = true))
        val replayed = unit(0, 0).copy(text = "Hello there.", pauseAfterMs = 0)
        val replay = ReplayRender().apply {
            set(UnitKey.of(replayed), requireNotNull(flatPaces.replayStyle(listener, 0.5f)))
        }
        val renderer = EngineRenderer(PausingVoice, style = { listener }, styleConfig = flatPaces, replay = replay)

        val slower = (renderer.render(replayed) as Outcome.Success).value
        val next = (renderer.render(replayed.copy(index = 1)) as Outcome.Success).value
        // At half of 3x the model reads 1.5x itself, unstretched; the next sentence is stretched to 3x.
        assertEquals(VOICED + PAUSE + VOICED, slower.samples.size)
        assertEquals((VOICED + PAUSE + VOICED) / 2f, next.samples.size.toFloat(), (VOICED + PAUSE + VOICED) * 0.01f)
    }

    @Test
    fun `even loudness lowers a loud voice and boost raises it`() {
        val evened = render(VoiceShaping(evenLoudness = true)).samples.first()
        val boosted = render(VoiceShaping(evenLoudness = false, boostDb = 6)).samples.first()
        assertTrue("evened $evened", evened in 1 until LOUD)
        assertTrue("boosted $boosted", boosted > LOUD)
    }

    private val flatPaces = SpeechStyleConfig(
        paces = Pace.entries.associateWith { PaceTiming(sentenceGapMs = 0, pauseScale = 1f) },
    )

    private companion object {
        val VOICE = VoiceId(EngineId.SHERPA, "en-voice")
        const val LOUD: Short = 5_000
        const val VOICED = 300
        const val PAUSE = 600
        const val LONG_PAUSE_MS = 250 // VoiceShapingConfig's default, at 1 sample a millisecond
    }
}
