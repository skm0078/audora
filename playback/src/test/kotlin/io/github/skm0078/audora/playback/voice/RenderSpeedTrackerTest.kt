// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/voice/RenderSpeedTrackerTest.kt
// Purpose: Only warm, single-voice renders are measured, so model loads and mixed sentences never skew a voice.

package io.github.skm0078.audora.playback.voice

import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.playback.pcm
import io.github.skm0078.audora.speech.route.RoutedAudio
import io.github.skm0078.audora.speech.voices.MeasuredSpeeds
import io.github.skm0078.audora.speech.voices.SpeedSample
import io.github.skm0078.audora.speech.voices.VoiceSpeed
import org.junit.Assert.assertEquals
import org.junit.Test

class RenderSpeedTrackerTest {
    private val recorded = ArrayList<Pair<VoiceId, SpeedSample>>()
    private val speeds = object : MeasuredSpeeds {
        override fun get(voice: VoiceId): VoiceSpeed? = null

        override fun record(voice: VoiceId, sample: SpeedSample) {
            recorded.add(voice to sample)
        }
    }
    private val tracker = RenderSpeedTracker { speeds }
    private val kokoro = VoiceId(EngineId.SHERPA, "kokoro-en-heart")
    private val hindi = VoiceId(EngineId.SHERPA, "supertonic-hi")

    /** 2 seconds of audio at the fixtures' 1 kHz. */
    private fun routed(vararg voices: VoiceId) =
        RoutedAudio(pcm(2_000), voices.map { if (it == hindi) Lang.HI else Lang.EN }, voices.toList(), null)

    @Test
    fun `a render at another rate is measured as if at the voice's own speed`() {
        tracker.onRendered(30, routed(kokoro), renderNanos = 1)
        // 2 s of audio spoken at 2x: 4 s at the voice's own speed.
        tracker.onRendered(30, routed(kokoro), renderNanos = 2_000_000_000, rate = 2f)

        val sample = recorded.single().second
        assertEquals(4.0, sample.audioSeconds, EPS)
        assertEquals(0.5, sample.rtf, EPS)
        assertEquals(7.5, sample.charsPerSecond, EPS)
    }

    @Test
    fun `the first render of a voice includes its load and is not measured`() {
        tracker.onRendered(30, routed(kokoro), renderNanos = 9_000_000_000)
        tracker.onRendered(30, routed(kokoro), renderNanos = 3_000_000_000)

        assertEquals(1, recorded.size)
        val (voice, sample) = recorded.single()
        assertEquals(kokoro, voice)
        assertEquals(1.5, sample.rtf, EPS)
        assertEquals(15.0, sample.charsPerSecond, EPS)
    }

    @Test
    fun `a switch of voice reloads the model, so the next render is not measured`() {
        tracker.onRendered(30, routed(kokoro), 1)
        tracker.onRendered(30, routed(hindi), 1)
        tracker.onRendered(30, routed(kokoro), 1)
        tracker.onRendered(30, routed(kokoro), 1)

        assertEquals(listOf(kokoro), recorded.map { it.first })
    }

    @Test
    fun `a mixed sentence is never charged to one voice, but sets which model is loaded`() {
        tracker.onRendered(30, routed(kokoro), 1)
        tracker.onRendered(30, routed(kokoro, hindi), 1)
        tracker.onRendered(30, routed(hindi), 1)

        assertEquals(listOf(hindi), recorded.map { it.first })
    }

    @Test
    fun `empty text or audio is not a measurement`() {
        tracker.onRendered(30, routed(kokoro), 1)
        tracker.onRendered(0, routed(kokoro), 1)
        tracker.onRendered(30, RoutedAudio(pcm(0), listOf(Lang.EN), listOf(kokoro), null), 1)

        assertEquals(0, recorded.size)
    }

    private companion object {
        const val EPS = 1e-9
    }
}
