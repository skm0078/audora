// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/voice/EngineRenderer.kt
// Purpose: Adapts script-routed speech (suspending) to the render queue's blocking SentenceRenderer.

package io.github.skm0078.audora.playback.voice

import io.github.skm0078.audora.core.config.ScriptRoutingConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.map
import io.github.skm0078.audora.playback.queue.SentenceRenderer
import io.github.skm0078.audora.playback.queue.UnitKey
import io.github.skm0078.audora.speech.SpeechError
import io.github.skm0078.audora.speech.audio.PcmAudio
import io.github.skm0078.audora.speech.route.RoutedAudio
import io.github.skm0078.audora.speech.route.RoutedSpeech
import io.github.skm0078.audora.speech.route.ScriptRouter
import io.github.skm0078.audora.speech.route.VoiceSource
import kotlinx.coroutines.runBlocking

/**
 * Runs on the render queue's worker thread, so blocking is fine. Interrupting the worker (the sentence left
 * the window) cancels the engine's coroutine and `runBlocking` throws [InterruptedException], which the queue
 * expects; the engine stays usable (SpeechEngine contract).
 *
 * Each part of a mixed-script sentence is read by its language's voice (P3-01, ADR-026); the voices come from
 * [voices], so a change in Settings → Voices applies from the next sentence rendered. Each render is timed for
 * [speed], which sizes the render-ahead window (P4-02).
 */
class EngineRenderer(
    voices: VoiceSource,
    config: ScriptRoutingConfig = ScriptRoutingConfig(),
    private val speed: RenderSpeedTracker? = null,
) : SentenceRenderer {
    private val speech = RoutedSpeech(voices, ScriptRouter(config))

    override fun render(unit: ReadingUnit): Outcome<PcmAudio, SpeechError> {
        val started = System.nanoTime()
        return runBlocking { speech.render(unit.text, unit.lang) }.map { routed ->
            speed?.onRendered(unit.text.trim().length, routed, System.nanoTime() - started)
            log(unit, routed)
            routed.audio
        }
    }

    /** Context only (page, sentence, languages), never the sentence's text. */
    private fun log(unit: ReadingUnit, routed: RoutedAudio) {
        val key = UnitKey.of(unit)
        routed.fallback?.let { error ->
            AudoraLog.w(TAG, "$key: read whole with the ${unit.lang.name} voice; ${error.message}")
        }
        if (routed.langs.size > 1) {
            AudoraLog.d(TAG, "$key: voices ${routed.langs.joinToString(" > ") { it.name }}")
        }
    }

    private companion object {
        const val TAG = "Playback.Render"
    }
}
