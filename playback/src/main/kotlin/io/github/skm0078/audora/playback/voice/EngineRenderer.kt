// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/voice/EngineRenderer.kt
// Purpose: Adapts script-routed speech (suspending) to the render queue's blocking SentenceRenderer.

package io.github.skm0078.audora.playback.voice

import io.github.skm0078.audora.core.config.ScriptRoutingConfig
import io.github.skm0078.audora.core.config.SpeakabilityConfig
import io.github.skm0078.audora.core.config.SpeechStyleConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.ClauseMark
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.model.SpeechStyle
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.map
import io.github.skm0078.audora.playback.contract.SentenceTiming
import io.github.skm0078.audora.playback.queue.SentenceRenderer
import io.github.skm0078.audora.playback.queue.UnitKey
import io.github.skm0078.audora.playback.reading.Speech
import io.github.skm0078.audora.playback.reading.UnitReading
import io.github.skm0078.audora.playback.session.SentenceTimings
import io.github.skm0078.audora.speech.SpeechError
import io.github.skm0078.audora.speech.audio.Earcon
import io.github.skm0078.audora.speech.audio.PcmAudio
import io.github.skm0078.audora.speech.audio.VoiceShaper
import io.github.skm0078.audora.speech.normalize.PronunciationFixer
import io.github.skm0078.audora.speech.normalize.TextNormalizer
import io.github.skm0078.audora.speech.punct.RunOnPunctuation
import io.github.skm0078.audora.speech.route.Cast
import io.github.skm0078.audora.speech.route.CastSpeech
import io.github.skm0078.audora.speech.route.ClauseProsody
import io.github.skm0078.audora.speech.route.RoutedAudio
import io.github.skm0078.audora.speech.route.ScriptRouter
import io.github.skm0078.audora.speech.route.SpeechRequest
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
 * The voice's audio is then shaped by the style's [SpeechStyle.shaping] (plan items 9, 10): with pause trim, each
 * voiced piece's own long silences are cut before the clause pauses go in, so only the voice's are trimmed; then the
 * whole sentence is evened to one loudness and boosted, before any cue and the silence after.
 *
 * The open book's pronunciation fixes ([ReadingSetup.fixer], plan item 1) are applied first, before normalization;
 * then, on a scanned page, a run-on sentence gets the punctuation model's pauses when it is downloaded (RI-11).
 *
 * What the voice is given follows the style's reading modes ([UnitReading], ADR-035): a table's size or a row with its
 * column names, "Equation 3", a cue in place of unreadable text, a cue before a caption or a chapter's heading.
 *
 * Each clip's layout (cue, voice, silence after) goes to [timings] for the reader's word highlight (plan item 14).
 *
 * With cast voices in the style's shaping (plan item 7), headings and quoted speech are read by their own voices
 * ([CastSpeech]), the quote's clause pause where the voice changes, each part evened before the join.
 */
@Suppress("LongParameterList") // All optional: the app passes the speed tracker, style, reading setup and timings.
class EngineRenderer(
    voices: VoiceSource,
    config: ScriptRoutingConfig = ScriptRoutingConfig(),
    normalizers: Map<Lang, TextNormalizer> = speechNormalizers(),
    private val speed: RenderSpeedTracker? = null,
    private val style: () -> SpeechStyle = { SpeechStyle() },
    private val styleConfig: SpeechStyleConfig = SpeechStyleConfig(),
    private val reading: ReadingSetup = ReadingSetup(),
    private val timings: SentenceTimings? = null,
) : SentenceRenderer {
    private val castSpeech = CastSpeech(voices, ScriptRouter(config), normalizers)

    override fun render(unit: ReadingUnit): Outcome<PcmAudio, SpeechError> {
        val now = style()
        val pauseAfterMs = styleConfig.silenceAfterMs(unit.pauseAfterMs, now)
        return when (val speech = UnitReading(now.modes, reading.speakability, reading.chapterPages()).speechOf(unit)) {
            Speech.Cue -> {
                AudoraLog.d(TAG, "${UnitKey.of(unit)}: unreadable, cue instead")
                val cue = reading.earcon.cue()
                val clip = cue.withSilenceAfter(pauseAfterMs)
                record(unit, leadMs = cue.durationMs, speechMs = 0, clip)
                Outcome.Success(clip)
            }

            is Speech.Text -> speak(unit, speech, now).map { voice ->
                val cued = if (speech.cueBefore) reading.earcon.before(voice) else voice
                val clip = cued.withSilenceAfter(pauseAfterMs)
                record(unit, leadMs = cued.durationMs - voice.durationMs, speechMs = voice.durationMs, clip)
                clip
            }
        }
    }

    /** Where the voice speaks in [clip] (plan item 14); millisecond rounding is kept inside the clip. */
    private fun record(unit: ReadingUnit, leadMs: Long, speechMs: Long, clip: PcmAudio) {
        val timings = timings ?: return
        val total = clip.durationMs
        val lead = leadMs.coerceIn(0, total)
        val timing = SentenceTiming(unit.page, unit.charStart, lead, speechMs.coerceIn(0, total - lead), total)
        timings.record(UnitKey.of(unit), timing)
    }

    private fun speak(unit: ReadingUnit, speech: Speech.Text, now: SpeechStyle): Outcome<PcmAudio, SpeechError> {
        val started = System.nanoTime()
        val rate = styleConfig.modelRate(now.rate)
        val prosody = if (now.clausePauses) {
            ClauseProsody({ mark -> styleConfig.clauseSilenceMs(mark, now) }, styleConfig.asideGainDb)
        } else {
            null
        }
        val text = reading.punctuated(reading.fixer().apply(speech.text), unit)
        val shaping = now.shaping
        val shaper = reading.shaper
        val trim: (PcmAudio) -> PcmAudio = if (shaping.trimPauses) shaper::trimPauses else { audio -> audio }
        val cast = Cast(shaping.dialogueVoices, shaping.headingVoices)
        val quotePauseMs = if (now.clausePauses) styleConfig.clauseSilenceMs(ClauseMark.QUOTE, now) else 0
        val level: (PcmAudio) -> PcmAudio = { shaper.level(it, shaping.evenLoudness, boostDb = 0) }
        val request = SpeechRequest(speech.role, rate, prosody, trim)
        return runBlocking { castSpeech.render(text, unit.lang, request, cast, quotePauseMs, level) }.map { routed ->
            speed?.onRendered(text.trim().length, routed, System.nanoTime() - started, rate)
            log(unit, routed)
            shaper.level(routed.audio, shaping.evenLoudness, shaping.boostDb)
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
 * @property fixer the open book's pronunciation fixes, read on the render thread for each sentence.
 * @property scanned whether a page was read by OCR; only those pages' run-ons reach [runOn].
 * @property runOn the punctuation model's guard (RI-11); null leaves every sentence as written.
 */
@Suppress("LongParameterList") // All optional: each caller sets the few it has.
class ReadingSetup(
    val speakability: SpeakabilityConfig = SpeakabilityConfig(),
    val earcon: Earcon = Earcon(),
    val chapterPages: () -> Set<Int> = { emptySet() },
    val shaper: VoiceShaper = VoiceShaper(),
    val fixer: () -> PronunciationFixer = { NO_FIXES },
    val scanned: (Int) -> Boolean = { false },
    val runOn: RunOnPunctuation? = null,
) {
    /** [text] with the model's pauses when it is a run-on on a scanned page, else [text]. */
    fun punctuated(text: String, unit: ReadingUnit): String = when {
        runOn == null || !scanned(unit.page) -> text
        else -> runOn.apply(text, unit.lang, UnitKey.of(unit).toString())
    }
}

private val NO_FIXES = PronunciationFixer(emptyList())
