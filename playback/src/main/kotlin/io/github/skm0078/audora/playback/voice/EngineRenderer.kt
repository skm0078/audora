// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/voice/EngineRenderer.kt
// Purpose: Adapts SpeechEngine (suspending) to the render queue's blocking SentenceRenderer.

package io.github.skm0078.audora.playback.voice

import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.flatMap
import io.github.skm0078.audora.playback.queue.SentenceRenderer
import io.github.skm0078.audora.speech.SpeechError
import io.github.skm0078.audora.speech.audio.PcmAudio
import kotlinx.coroutines.runBlocking

/**
 * Runs on the render queue's worker thread, so blocking is fine. Interrupting the worker (the sentence left
 * the window) cancels the engine's coroutine and `runBlocking` throws [InterruptedException], which the queue
 * expects; the engine stays usable (SpeechEngine contract).
 */
class EngineRenderer(private val voices: SpeechVoices) : SentenceRenderer {
    override fun render(unit: ReadingUnit): Outcome<PcmAudio, SpeechError> = runBlocking {
        voices.voiceFor(unit.lang).flatMap { voice ->
            when (val engine = voices.engine(voice.engine)) {
                is Outcome.Success -> engine.value.render(unit.text, voice)
                is Outcome.Failure -> engine
            }
        }
    }
}
