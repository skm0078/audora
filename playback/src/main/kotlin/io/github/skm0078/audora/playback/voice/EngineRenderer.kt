// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/voice/EngineRenderer.kt
// Purpose: Adapts script-routed speech (suspending) to the render queue's blocking SentenceRenderer.

package io.github.skm0078.audora.playback.voice

import io.github.skm0078.audora.core.config.ScriptRoutingConfig
import io.github.skm0078.audora.core.config.SpeakabilityConfig
import io.github.skm0078.audora.core.config.SpeechStyleConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.model.SpeechStyle
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.map
import io.github.skm0078.audora.playback.queue.SentenceRenderer
import io.github.skm0078.audora.playback.queue.UnitKey
import io.github.skm0078.audora.playback.reading.Speech
import io.github.skm0078.audora.playback.reading.UnitReading
import io.github.skm0078.audora.speech.SpeechError
import io.github.skm0078.audora.speech.audio.Earcon
import io.github.skm0078.audora.speech.audio.PcmAudio
import io.github.skm0078.audora.speech.normalize.TextNormalizer
import io.github.skm0078.audora.speech.route.ClauseProsody
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
 * speed above it, ADR-033), and the style's pace sets the silences. With the style's clause pauses on, strong marks
 * inside the sentence get their own pause, scaled the same way (RI-6).
 *
 * The sentence's pause ([ReadingUnit.pauseAfterMs]: after headings, paragraphs and list items), scaled by the pace,
 * plus the pace's gap after every sentence, is rendered as silence at the end of its clip (ADR-029), so the player,
 * highlight and sleep timer need no notion of pauses. The speed is measured on the voice's audio alone: silence
 * costs no render time and would make a voice look faster.
 *
 * What the voice is given follows the style's reading modes ([UnitReading], ADR-035): a table's size or a row with its
 * column names, "Equation 3", a cue in place of unreadable text, a cue before a caption or a chapter's heading.
 */
@Suppress("LongParameterList") // All optional: the app passes the speed tracker, the style and the reading setup.
class EngineRenderer(
    voices: VoiceSource,
    config: ScriptRoutingConfig = ScriptRoutingConfig(),
    normalizers: Map<Lang, TextNormalizer> = speechNormalizers(),
    private val speed: RenderSpeedTracker? = null,
    private val style: () -> SpeechStyle = { SpeechStyle() },
    private val styleConfig: SpeechStyleConfig = SpeechStyleConfig(),
    private val reading: ReadingSetup = ReadingSetup(),
) : SentenceRenderer {
    private val routedSpeech = RoutedSpeech(voices, ScriptRouter(config), normalizers)

    override fun render(unit: ReadingUnit): Outcome<PcmAudio, SpeechError> {
        val now = style()
        val pauseAfterMs = styleConfig.silenceAfterMs(unit.pauseAfterMs, now)
        return when (val speech = UnitReading(now.modes, reading.speakability, reading.chapterPages()).speechOf(unit)) {
            Speech.Cue -> {
                AudoraLog.d(TAG, "${UnitKey.of(unit)}: unreadable, cue instead")
                Outcome.Success(reading.earcon.cue().withSilenceAfter(pauseAfterMs))
            }

            is Speech.Text -> speak(unit, speech, now).map { audio ->
                (if (speech.cueBefore) reading.earcon.before(audio) else audio).withSilenceAfter(pauseAfterMs)
            }
        }
    }

    private fun speak(unit: ReadingUnit, speech: Speech.Text, now: SpeechStyle): Outcome<PcmAudio, SpeechError> {
        val started = System.nanoTime()
        val rate = styleConfig.modelRate(now.rate)
        val prosody = if (now.clausePauses) {
            ClauseProsody({ mark -> styleConfig.clauseSilenceMs(mark, now) }, styleConfig.asideGainDb)
        } else {
            null
        }
        val text = speech.text
        return runBlocking { routedSpeech.render(text, unit.lang, speech.role, rate, prosody) }.map { routed ->
            speed?.onRendered(text.trim().length, routed, System.nanoTime() - started, rate)
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

/**
 * What the renderer needs beyond the voice and the style to apply the reading modes.
 *
 * @property chapterPages the open book's chapter start pages (PDF outline), for the cue before a chapter's heading;
 *   read on the render thread, so it must be thread-safe.
 */
class ReadingSetup(
    val speakability: SpeakabilityConfig = SpeakabilityConfig(),
    val earcon: Earcon = Earcon(),
    val chapterPages: () -> Set<Int> = { emptySet() },
)
