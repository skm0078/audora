// File: app/src/androidTest/kotlin/io/github/skm0078/audora/release/SymbolJourney.kt
// Purpose: The release journey's symbols step on the installed app: arrows, maths, emoji and broken-font glyphs reach
//          the bundled voice as words or not at all, and a line of only symbols plays as silence, not a failure.

package io.github.skm0078.audora.release

import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.model.Feeling
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.map
import io.github.skm0078.audora.playback.voice.SpeechVoices
import io.github.skm0078.audora.playback.voice.VoiceEngines
import io.github.skm0078.audora.release.JourneyDevice.app
import io.github.skm0078.audora.speech.SpeechEngine
import io.github.skm0078.audora.speech.SpeechError
import io.github.skm0078.audora.speech.route.RoutedSpeech
import io.github.skm0078.audora.speech.route.ScriptRouter
import io.github.skm0078.audora.speech.route.VoiceSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * Renders symbol-heavy sentences the way playback does (script routing, the app's normalizers, the voice picked for
 * English) with the app's own engines, writing down the text each engine is handed. Every sentence must render; none
 * may hand a voice a symbol it cannot say; a sentence with words must make sound and "* * *" none.
 */
internal object SymbolJourney {
    private val SPOKEN = listOf(
        "Input → Process → Output." to "Input to Process to Output.",
        "Keep the dose ≤ 5 mg and p < 0.05." to
            "Keep the dose at most five milligrams and p less than zero point zero five.",
        "E = mc², water is H₂O and θ ≈ 30°." to
            "E equals mc squared, water is H two O and theta approximately thirty degrees.",
        "Great job 👍 ★★★ and a glyph \uF0B7 here." to "Great job and a glyph here.",
    )
    private const val SCENE_BREAK = "* * *"

    fun symbolsAreSaidOrLeftOut() {
        val engines = VoiceEngines(app)
        try {
            val voices = RecordingVoices(SpeechVoices(engines))
            val speech = RoutedSpeech(voices, ScriptRouter())
            for ((text, expected) in SPOKEN) {
                val samples = render(speech, text)
                assertEquals("what the voice was handed for \"$text\"", listOf(expected), voices.takeHanded())
                assertTrue("\"$text\" made sound", samples > SILENCE_SAMPLES)
            }
            val samples = render(speech, SCENE_BREAK)
            assertEquals("a scene break hands the voice nothing", listOf(""), voices.takeHanded())
            assertTrue("a scene break is silence ($samples samples)", samples <= SILENCE_SAMPLES)
        } finally {
            engines.release()
        }
    }

    private fun render(speech: RoutedSpeech, text: String): Int =
        when (val result = runBlocking { speech.render(text, Lang.EN) }) {
            is Outcome.Success -> result.value.audio.samples.size
            is Outcome.Failure -> throw AssertionError("\"$text\" failed to render: ${result.error}")
        }

    /** The app's voices, with every engine wrapped to write down the text it is asked to say. */
    private class RecordingVoices(private val voices: VoiceSource) : VoiceSource by voices {
        private val handed = mutableListOf<String>()

        fun takeHanded(): List<String> = handed.toList().also { handed.clear() }

        override fun engine(id: EngineId): Outcome<SpeechEngine, SpeechError> =
            voices.engine(id).map { engine -> Recording(engine) }

        private inner class Recording(private val engine: SpeechEngine) : SpeechEngine by engine {
            override suspend fun render(text: String, voice: VoiceId, rate: Float) =
                engine.render(text, voice, rate).also { handed.add(text) }

            override suspend fun render(text: String, voice: VoiceId, rate: Float, feeling: Feeling) =
                engine.render(text, voice, rate, feeling).also { handed.add(text) }
        }
    }

    /** A blank render is the engine's silence: no samples. */
    private const val SILENCE_SAMPLES = 0
}
