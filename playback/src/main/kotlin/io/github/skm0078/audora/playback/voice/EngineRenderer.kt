// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/voice/EngineRenderer.kt
// Purpose: Adapts script-routed speech (suspending) to the render queue's blocking SentenceRenderer.

package io.github.skm0078.audora.playback.voice

import io.github.skm0078.audora.core.config.ScriptRoutingConfig
import io.github.skm0078.audora.core.config.SpeechStyleConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.model.SpeechStyle
import io.github.skm0078.audora.core.model.UnitKind
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.map
import io.github.skm0078.audora.playback.queue.SentenceRenderer
import io.github.skm0078.audora.playback.queue.UnitKey
import io.github.skm0078.audora.speech.SpeechError
import io.github.skm0078.audora.speech.audio.PcmAudio
import io.github.skm0078.audora.speech.normalize.TextNormalizer
import io.github.skm0078.audora.speech.normalize.TextRole
import io.github.skm0078.audora.speech.route.RoutedAudio
import io.github.skm0078.audora.speech.route.RoutedSpeech
import io.github.skm0078.audora.speech.route.ScriptRouter
import io.github.skm0078.audora.speech.route.VoiceSource
import io.github.skm0078.audora.speech.route.speechNormalizers
import kotlinx.coroutines.runBlocking

/**
 * Runs on the render queue's worker thread, so blocking is fine. Interrupting the worker (the sentence left
 * the window) cancels the engine's coroutine and `runBlocking` throws [InterruptedException], which the queue
 * expects; the engine stays usable (SpeechEngine contract).
 *
 * Each part of a mixed-script sentence is read by its language's voice (P3-01, ADR-026); the voices come from
 * [voices], so a change in Settings → Voices applies from the next sentence rendered. Each part is normalized for
 * its language first ([normalizers]: English P2-04, Hindi and Bengali P3-02; ADR-019), with headings read as headings.
 * Each render is timed for [speed], which sizes the render-ahead window (P4-02).
 *
 * Every sentence is spoken by the voice itself at the [style]'s rate, up to the model's cap (the player stretches any
 * speed above it, ADR-033), and the style's pace sets the silences.
 *
 * The sentence's pause ([ReadingUnit.pauseAfterMs]: after headings, paragraphs and list items), scaled by the pace,
 * plus the pace's gap after every sentence, is rendered as silence at the end of its clip (ADR-029), so the player,
 * highlight and sleep timer need no notion of pauses. The speed is measured on the voice's audio alone: silence
 * costs no render time and would make a voice look faster.
 */
class EngineRenderer(
    voices: VoiceSource,
    config: ScriptRoutingConfig = ScriptRoutingConfig(),
    normalizers: Map<Lang, TextNormalizer> = speechNormalizers(),
    private val speed: RenderSpeedTracker? = null,
    private val style: () -> SpeechStyle = { SpeechStyle() },
    private val styleConfig: SpeechStyleConfig = SpeechStyleConfig(),
) : SentenceRenderer {
    private val speech = RoutedSpeech(voices, ScriptRouter(config), normalizers)

    override fun render(unit: ReadingUnit): Outcome<PcmAudio, SpeechError> {
        val started = System.nanoTime()
        val now = style()
        val rate = styleConfig.modelRate(now.rate)
        val text = TablePlaceholder.spokenText(unit)
        return runBlocking { speech.render(text, unit.lang, roleOf(unit.kind), rate) }.map { routed ->
            speed?.onRendered(text.trim().length, routed, System.nanoTime() - started, rate)
            log(unit, routed)
            routed.audio.withSilenceAfter(styleConfig.silenceAfterMs(unit.pauseAfterMs, now))
        }
    }

    private fun roleOf(kind: UnitKind): TextRole = when (kind) {
        UnitKind.HEADING -> TextRole.HEADING

        UnitKind.BODY, UnitKind.LIST_ITEM, UnitKind.TABLE_SUMMARY, UnitKind.TABLE_ROW, UnitKind.FOOTNOTE,
        UnitKind.CAPTION, UnitKind.EQUATION, UnitKind.ASIDE,
        -> TextRole.BODY
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
