// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/voice/EngineRenderer.kt
// Purpose: Adapts script-routed speech (suspending) to the render queue's blocking SentenceRenderer.

package io.github.skm0078.audora.playback.voice

import io.github.skm0078.audora.core.config.ScriptRoutingConfig
import io.github.skm0078.audora.core.config.SpeakabilityConfig
import io.github.skm0078.audora.core.config.SpeechStyleConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.ClauseMark
import io.github.skm0078.audora.core.model.ContentIntent
import io.github.skm0078.audora.core.model.Feeling
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
import io.github.skm0078.audora.speech.audio.SmartStretch
import io.github.skm0078.audora.speech.audio.VoiceShaper
import io.github.skm0078.audora.speech.delivery.Delivery
import io.github.skm0078.audora.speech.delivery.DeliveryPlanner
import io.github.skm0078.audora.speech.normalize.PronunciationFixer
import io.github.skm0078.audora.speech.normalize.TextNormalizer
import io.github.skm0078.audora.speech.normalize.TextRole
import io.github.skm0078.audora.speech.punct.RunOnPunctuation
import io.github.skm0078.audora.speech.route.Cast
import io.github.skm0078.audora.speech.route.CastSpeech
import io.github.skm0078.audora.speech.route.CharacterVoices
import io.github.skm0078.audora.speech.route.ClauseProsody
import io.github.skm0078.audora.speech.route.RoutedAudio
import io.github.skm0078.audora.speech.route.ScriptRouter
import io.github.skm0078.audora.speech.route.Speaker
import io.github.skm0078.audora.speech.route.SpeechRequest
import io.github.skm0078.audora.speech.route.VoiceSource
import io.github.skm0078.audora.speech.route.speechNormalizers
import kotlin.math.roundToInt
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
 * voiced piece's own long silences are cut before the clause pauses go in, so only the voice's are trimmed; with
 * smart speed, each piece is then sped up unevenly past the model's cap ([ReadingSetup.smartStretch], ADR-049), so
 * Audora's own pauses keep their length; then the whole sentence is evened to one loudness and boosted, before any
 * cue and the silence after.
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
 *
 * The sentence "What did it say?" renders again ([replay]) uses the slower style the session gave it.
 *
 * With "Read to suit the book" on ([io.github.skm0078.audora.core.model.VoiceShaping.suitBook], ADR-056), each
 * sentence's [Delivery] scales all of the above: the book's intent ([ReadingSetup.intent]) sets the speed and pauses,
 * and a story's line is read with the feeling its words ask for, louder or softer by [Delivery.gainDb] after leveling.
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
    private val replay: ReplayRender? = null,
) : SentenceRenderer {
    private val castSpeech = CastSpeech(voices, ScriptRouter(config), normalizers)

    override fun render(unit: ReadingUnit): Outcome<PcmAudio, SpeechError> {
        val now = replay?.styleFor(UnitKey.of(unit)) ?: style()
        val delivery = deliveryOf(unit, now)
        val pauseAfterMs = (styleConfig.silenceAfterMs(unit.pauseAfterMs, now) * delivery.pauseScale).roundToInt()
        return when (val speech = UnitReading(now.modes, reading.speakability, reading.chapterPages()).speechOf(unit)) {
            Speech.Cue -> {
                AudoraLog.d(TAG, "${UnitKey.of(unit)}: unreadable, cue instead")
                val cue = reading.earcon.cue()
                val clip = cue.withSilenceAfter(pauseAfterMs)
                record(unit, leadMs = cue.durationMs, speechMs = 0, clip)
                Outcome.Success(clip)
            }

            is Speech.Text -> speak(unit, speech, now, delivery).map { voice ->
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
        val speech = speechMs.coerceIn(0, total - lead)
        val timing = SentenceTiming(unit.page, unit.charStart, unit.index, lead, speech, total)
        timings.record(UnitKey.of(unit), timing)
    }

    /** How [unit] is delivered for [now]: plain when "Read to suit the book" is off (ADR-056). */
    private fun deliveryOf(unit: ReadingUnit, now: SpeechStyle): Delivery {
        if (!now.shaping.suitBook) return Delivery.PLAIN
        val quoted = reading.startsInQuote(unit) || unit.text.any { it in QUOTE_MARKS }
        val delivery = reading.planner.plan(unit, reading.intent(), quoted)
        if (delivery.feeling != Feeling.NEUTRAL) AudoraLog.d(TAG, "${UnitKey.of(unit)}: feeling ${delivery.feeling}")
        return delivery
    }

    private fun speak(
        unit: ReadingUnit,
        speech: Speech.Text,
        now: SpeechStyle,
        delivery: Delivery,
    ): Outcome<PcmAudio, SpeechError> {
        val started = System.nanoTime()
        // The delivery nudges the model's own speed, so words stay whole; never past the model's cap (ADR-033).
        val rate = (
            styleConfig.modelRate(
                now.rate,
            ) * delivery.rate
            ).coerceIn(styleConfig.minRate, styleConfig.maxModelRate)
        val clauseMs: (ClauseMark) -> Int = { mark ->
            (styleConfig.clauseSilenceMs(mark, now) * delivery.clauseScale).roundToInt()
        }
        // An equation is never cut at its marks: its "-", "|" and brackets are operators for MathSymbols, not pauses.
        val prosody = if (now.clausePauses && speech.role != TextRole.EQUATION) {
            ClauseProsody(clauseMs, styleConfig.asideGainDb)
        } else {
            null
        }
        val text = reading.punctuated(reading.fixer().apply(speech.text), unit)
        val shaping = now.shaping
        val shaper = reading.shaper
        val trim: (PcmAudio) -> PcmAudio = if (shaping.trimPauses) shaper::trimPauses else { audio -> audio }
        val stretch = styleConfig.voiceStretch(now)
        val shape: (PcmAudio) -> PcmAudio = { audio -> reading.smartStretch.stretch(trim(audio), stretch) }
        val characters = reading.characters?.takeIf { shaping.characterVoices }?.current()
        val cast = Cast(shaping.dialogueVoices, shaping.headingVoices, characters)
        val quotePauseMs = if (now.clausePauses) clauseMs(ClauseMark.QUOTE) else 0
        val level: (PcmAudio) -> PcmAudio = { shaper.level(it, shaping.evenLoudness, boostDb = 0) }
        val speaker = characters?.let { reading.speakerOf(unit) }
        val request = SpeechRequest(speech.role, rate, prosody, shape, reading.startsInQuote(unit), speaker)
        return runBlocking { castSpeech.render(text, unit.lang, request, cast, quotePauseMs, level) }.map { routed ->
            speed?.onRendered(text.trim().length, routed, System.nanoTime() - started, rate * stretch)
            log(unit, routed)
            shaper.level(routed.audio, shaping.evenLoudness, shaping.boostDb, delivery.gainDb)
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

        /** Marks that open or close quoted speech, as `QuoteSplitter` reads them. */
        const val QUOTE_MARKS = "“”\"«»"
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
 * @property startsInQuote whether a sentence starts inside a quote an earlier sentence of its paragraph opened
 *   ([io.github.skm0078.audora.playback.reading.QuoteCarry], plan item 7).
 * @property speakerOf who says a sentence's quotes, from the same carry (plan item 63); null when unknown.
 * @property characters the open book's character voices, used when the style's shaping turns them on.
 * @property smartStretch smart speed's uneven stretch (plan item 10), used when the style's shaping turns it on.
 * @property intent what the open book is (ADR-056), read on the render thread for each sentence.
 * @property planner each sentence's delivery from that intent, used when the style's shaping turns it on.
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
    val startsInQuote: (ReadingUnit) -> Boolean = { false },
    val speakerOf: (ReadingUnit) -> Speaker? = { null },
    val characters: CharacterVoices? = null,
    val smartStretch: SmartStretch = SmartStretch(),
    val intent: () -> ContentIntent = { ContentIntent.GENERAL },
    val planner: DeliveryPlanner = DeliveryPlanner(),
) {
    /** [text] with the model's pauses when it is a run-on on a scanned page, else [text]. */
    fun punctuated(text: String, unit: ReadingUnit): String = when {
        runOn == null || !scanned(unit.page) -> text
        else -> runOn.apply(text, unit.lang, UnitKey.of(unit).toString())
    }
}

private val NO_FIXES = PronunciationFixer(emptyList())
