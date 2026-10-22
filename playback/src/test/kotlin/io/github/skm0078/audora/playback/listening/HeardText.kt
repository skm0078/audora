// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/listening/HeardText.kt
// Purpose: What the voice is given for a document's units: playback's default reading modes, then the real renderer.

package io.github.skm0078.audora.playback.listening

import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.playback.voice.EngineRenderer
import io.github.skm0078.audora.playback.voice.TablePlaceholder
import io.github.skm0078.audora.speech.RenderResult
import io.github.skm0078.audora.speech.SpeechEngine
import io.github.skm0078.audora.speech.SpeechError
import io.github.skm0078.audora.speech.VoiceInfo
import io.github.skm0078.audora.speech.audio.PcmAudio
import io.github.skm0078.audora.speech.route.VoiceSource

/**
 * Drives the app's own [EngineRenderer] (script routing, per-language normalizers, heading readings) with a voice that
 * records the text it is asked to speak instead of synthesizing it, so the listening score measures exactly what a
 * voice would say.
 *
 * Playback's reading modes ("Skip unreadable text", Tables, Footnotes, Captions, Equations; RI-2b, RI-4b, RI-5b,
 * RI-7b) decide which units reach the renderer and how. Each of those tickets wires its real mode code into
 * [spokenUnits] with the default setting, so the score follows the app. Until then every unit is spoken as text,
 * but tables follow [TablePlaceholder] as the app does (rows skipped, the summary read as the table's size).
 */
class HeardText {
    private val voice = RecordingVoice()
    private val renderer = EngineRenderer(voice)

    /** The text the voice gets for [units], one entry per voice call, in playback order. */
    fun of(units: List<ReadingUnit>): List<String> {
        voice.spoken.clear()
        spokenUnits(units).forEach { unit ->
            when (val rendered = renderer.render(unit)) {
                is Outcome.Success -> Unit
                is Outcome.Failure -> error("page ${unit.page} unit ${unit.index} did not render: ${rendered.error}")
            }
        }
        return voice.spoken.toList()
    }

    /** The units playback reads with its default settings, in order. */
    private fun spokenUnits(units: List<ReadingUnit>): List<ReadingUnit> = TablePlaceholder.spokenUnits(units)

    /** One voice per language that writes down what it is asked to say and returns a silent clip. */
    private class RecordingVoice :
        VoiceSource,
        SpeechEngine {
        val spoken = ArrayList<String>()
        override val id = EngineId.SHERPA

        override suspend fun voiceFor(lang: Lang): Outcome<VoiceId, SpeechError> =
            Outcome.Success(VoiceId(EngineId.SHERPA, lang.bcp47Tag))

        override fun engine(id: EngineId): Outcome<SpeechEngine, SpeechError> = Outcome.Success(this)

        override fun supports(lang: Lang): Boolean = true

        override suspend fun voices(): Outcome<List<VoiceInfo>, SpeechError> = Outcome.Success(emptyList())

        override suspend fun render(text: String, voice: VoiceId, rate: Float): RenderResult {
            spoken.add(text)
            return Outcome.Success(PcmAudio.silence(SAMPLE_RATE))
        }

        override fun release() = Unit
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
    }
}
