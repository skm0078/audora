// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/voice/ShapedRenderTest.kt
// Purpose: The renderer applies the style's voice shaping: pause trim, even loudness and boost (plan items 9, 10).

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

    private fun render(shaping: VoiceShaping): PcmAudio {
        val style = SpeechStyle(pace = Pace.NORMAL, clausePauses = false, shaping = shaping)
        val renderer = EngineRenderer(PausingVoice, style = { style }, styleConfig = flatPaces)
        return (renderer.render(unit(0, 0).copy(text = "Hello there.", pauseAfterMs = 0)) as Outcome.Success).value
    }

    @Test
    fun `pause trim shortens the voice's own long silence`() {
        val untouched = render(VoiceShaping(evenLoudness = false, trimPauses = false))
        val trimmed = render(VoiceShaping(evenLoudness = false, trimPauses = true))
        assertEquals(VOICED + PAUSE + VOICED, untouched.samples.size)
        assertEquals(VOICED + LONG_PAUSE_MS + VOICED, trimmed.samples.size)
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
