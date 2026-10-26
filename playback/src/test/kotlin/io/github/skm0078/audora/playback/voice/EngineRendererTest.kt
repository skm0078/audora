// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/voice/EngineRendererTest.kt
// Purpose: The renderer hands the voice normalized text, reads headings as headings, ends each clip with its pause
//          and records each clip's layout.

package io.github.skm0078.audora.playback.voice

import io.github.skm0078.audora.core.config.NarrationConfig
import io.github.skm0078.audora.core.config.PaceTiming
import io.github.skm0078.audora.core.config.SpeechStyleConfig
import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.model.EquationMode
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.Pace
import io.github.skm0078.audora.core.model.ParagraphPlace
import io.github.skm0078.audora.core.model.ReadingModes
import io.github.skm0078.audora.core.model.SpeechStyle
import io.github.skm0078.audora.core.model.UnitKind
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.core.model.VoiceShaping
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.playback.TEST_SAMPLE_RATE
import io.github.skm0078.audora.playback.contract.SentenceTiming
import io.github.skm0078.audora.playback.queue.UnitKey
import io.github.skm0078.audora.playback.session.SentenceTimings
import io.github.skm0078.audora.playback.unit
import io.github.skm0078.audora.speech.RenderResult
import io.github.skm0078.audora.speech.SpeechEngine
import io.github.skm0078.audora.speech.SpeechError
import io.github.skm0078.audora.speech.VoiceInfo
import io.github.skm0078.audora.speech.audio.PcmAudio
import io.github.skm0078.audora.speech.delivery.NarrationArc
import io.github.skm0078.audora.speech.route.VoiceSource
import io.github.skm0078.audora.speech.voices.MeasuredSpeeds
import io.github.skm0078.audora.speech.voices.SpeedSample
import io.github.skm0078.audora.speech.voices.VoiceSpeed
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Test

class EngineRendererTest {
    /** One sample per character at the fixtures' 1 kHz, so a clip's length shows the spoken text's length. */
    private class RecordingVoices :
        VoiceSource,
        SpeechEngine {
        val spoken = ArrayList<String>()
        val rates = ArrayList<Float>()
        val readBy = ArrayList<VoiceId>()
        override val id = EngineId.SHERPA

        override suspend fun voiceFor(lang: Lang): Outcome<VoiceId, SpeechError> = Outcome.Success(VOICE)

        override fun engine(id: EngineId): Outcome<SpeechEngine, SpeechError> = Outcome.Success(this)

        override fun supports(lang: Lang): Boolean = true

        override suspend fun voices(): Outcome<List<VoiceInfo>, SpeechError> = Outcome.Success(emptyList())

        override suspend fun render(text: String, voice: VoiceId, rate: Float): RenderResult {
            spoken.add(text)
            rates.add(rate)
            readBy.add(voice)
            return Outcome.Success(PcmAudio(ShortArray(text.length) { SAMPLE }, TEST_SAMPLE_RATE))
        }

        override fun release() = Unit
    }

    private val recorded = ArrayList<SpeedSample>()
    private val speeds = object : MeasuredSpeeds {
        override fun get(voice: VoiceId): VoiceSpeed? = null

        override fun record(voice: VoiceId, sample: SpeedSample) {
            recorded.add(sample)
        }
    }

    @Test
    fun `the voice gets normalized text and the clip ends with the sentence's pause`() {
        val voices = RecordingVoices()
        val renderer = EngineRenderer(voices, style = { SpeechStyle(pace = Pace.NORMAL) }, styleConfig = flatPaces)

        val audio = (
            renderer.render(
                unit(0, 0).copy(text = "Pay ₹5 now.", pauseAfterMs = 400),
            ) as Outcome.Success
            ).value

        assertEquals(listOf("Pay five rupees now."), voices.spoken)
        // 20 characters spoken at 1 kHz, then 400 ms of silence.
        assertEquals(20 + 400, audio.samples.size)
        assertEquals(20, audio.samples.count { it == SAMPLE })
        assertEquals(0, audio.samples.drop(20).count { it != 0.toShort() })
    }

    @Test
    fun `an equation read as written is never cut at its operators`() {
        val voices = RecordingVoices()
        val modes = ReadingModes(equations = EquationMode.AS_WRITTEN)
        val renderer = EngineRenderer(voices, style = { SpeechStyle(modes = modes) }, styleConfig = flatPaces)

        renderer.render(unit(0, 0).copy(text = "v = u - at", kind = UnitKind.EQUATION))
        renderer.render(unit(0, 1).copy(text = "¬(P ∧ Q) ↔ (¬P ∨ ¬Q)", kind = UnitKind.EQUATION))

        assertEquals(
            listOf("v equals u minus a t", "not (P and Q) if and only if ( not P or not Q)"),
            voices.spoken.map { it.replace(Regex("""\s+"""), " ").trim() },
        )
    }

    @Test
    fun `quotes and headings go to the style's cast voices`() {
        val voices = RecordingVoices()
        val dialogue = VoiceId(EngineId.SHERPA, "dialogue")
        val heading = VoiceId(EngineId.SHERPA, "heading")
        val shaping =
            VoiceShaping(dialogueVoices = mapOf(Lang.EN to dialogue), headingVoices = mapOf(Lang.EN to heading))
        val renderer = EngineRenderer(voices, style = { SpeechStyle(shaping = shaping) }, styleConfig = flatPaces)

        renderer.render(unit(0, 0).copy(text = "“Come here at once,” she said."))
        renderer.render(unit(0, 1).copy(text = "The Long Road", kind = UnitKind.HEADING))

        assertEquals(listOf(dialogue, VOICE, heading), voices.readBy)
    }

    @Test
    fun `each clip's layout is recorded for the word highlight`() {
        val timings = SentenceTimings()
        val renderer = EngineRenderer(RecordingVoices(), styleConfig = flatPaces, timings = timings)
        val sentence = unit(2, 5).copy(text = "Pay ₹5 now.", charStart = 30, pauseAfterMs = 400)

        renderer.render(sentence)

        // No cue, 20 ms of voice ("Pay five rupees now." at 1 kHz), then the 400 ms pause.
        assertEquals(SentenceTiming(2, 30, 5, 0, 20, 420), timings.of(UnitKey(2, 5)))
    }

    @Test
    fun `an unreadable line's cue is recorded as a lead with no speech`() {
        val timings = SentenceTimings()
        val renderer = EngineRenderer(RecordingVoices(), styleConfig = flatPaces, timings = timings)
        val junk = unit(0, 0).copy(text = "#&%@", speakScore = 0f, pauseAfterMs = 250)

        val clip = (renderer.render(junk) as Outcome.Success).value

        val timing = requireNotNull(timings.of(UnitKey(0, 0)))
        assertEquals(0, timing.speechMs)
        assertEquals(clip.durationMs, timing.totalMs)
        assertEquals(timing.totalMs - 250, timing.leadMs)
    }

    @Test
    fun `the voice speaks the style's rate up to the model's cap`() {
        val voices = RecordingVoices()
        var style = SpeechStyle(rate = 1.33f)
        val renderer = EngineRenderer(voices, style = { style }, styleConfig = SpeechStyleConfig(maxModelRate = 1.5f))

        renderer.render(unit(0, 0).copy(text = "Hello."))
        style = SpeechStyle(rate = 9f)
        renderer.render(unit(0, 0).copy(text = "Hello."))

        // Snapped to the slider's step; above the cap the player stretches the rest.
        assertEquals(listOf(1.35f, 1.5f), voices.rates)
    }

    @Test
    fun `narration rises and falls by the sentence's place only with the arc and Read to suit the book on`() {
        val steady = NarrationArc(NarrationConfig(jitterSemitones = 0f, jitterRate = 0f))
        val sectionStart = ReadingSetup(placeOf = { ParagraphPlace(0, 3, afterHeading = true) }, narration = steady)
        val plain = RecordingVoices()
        val narrated = RecordingVoices()
        val switchedOff = RecordingVoices()
        val off = SpeechStyle(shaping = VoiceShaping(suitBook = false))

        EngineRenderer(plain, styleConfig = flatPaces).render(unit(0, 0).copy(text = "Hello."))
        EngineRenderer(
            narrated,
            styleConfig = flatPaces,
            reading = sectionStart,
        ).render(unit(0, 0).copy(text = "Hello."))
        EngineRenderer(switchedOff, style = { off }, styleConfig = flatPaces, reading = sectionStart)
            .render(unit(0, 0).copy(text = "Hello."))

        // A section's first sentence: slower, and a little higher, which the voice is asked for as a slower rate still.
        val pitch = 2.0.pow(NarrationConfig.DEFAULT_START_SEMITONES / 12.0).toFloat()
        val expected = plain.rates.single() * NarrationConfig.DEFAULT_SECTION_START_RATE / pitch
        assertEquals(expected, narrated.rates.single(), 1e-4f)
        assertEquals(plain.rates, switchedOff.rates)
    }

    @Test
    fun `the style's pace sizes the silence after the sentence`() {
        val voices = RecordingVoices()
        val config = SpeechStyleConfig(
            paces = mapOf(
                Pace.TIGHT to PaceTiming(sentenceGapMs = 0, pauseScale = 0.5f),
                Pace.NORMAL to PaceTiming(sentenceGapMs = 100, pauseScale = 1f),
                Pace.RELAXED to PaceTiming(sentenceGapMs = 300, pauseScale = 2f),
            ),
        )
        var style = SpeechStyle(pace = Pace.RELAXED)
        val renderer = EngineRenderer(voices, style = { style }, styleConfig = config)
        val sentence = unit(0, 0).copy(text = "Hello.", pauseAfterMs = 400)

        val relaxed = (renderer.render(sentence) as Outcome.Success).value
        style = SpeechStyle(pace = Pace.TIGHT)
        val tight = (renderer.render(sentence) as Outcome.Success).value

        // 6 characters at 1 kHz, then the pace's gap plus the scaled pause.
        assertEquals(6 + 300 + 800, relaxed.samples.size)
        assertEquals(6 + 0 + 200, tight.samples.size)
    }

    @Test
    fun `a heading is normalized as a heading, body text as body text`() {
        val voices = RecordingVoices()
        val renderer = EngineRenderer(voices)

        renderer.render(unit(0, 0).copy(text = "IV. Results", kind = UnitKind.HEADING))
        renderer.render(unit(0, 1).copy(text = "IV. Results", kind = UnitKind.LIST_ITEM))

        assertEquals(listOf("four. Results", "IV. Results"), voices.spoken)
    }

    @Test
    fun `render speed is measured on the voice's audio, not its pause`() {
        val renderer = EngineRenderer(RecordingVoices(), speed = RenderSpeedTracker { speeds })
        val paused = unit(0, 0).copy(text = "Sentence one.", pauseAfterMs = 1_000)

        renderer.render(paused)
        renderer.render(paused.copy(index = 1))

        // The first render loads the voice and is not measured; the second's 13 characters are 13 ms of audio.
        assertEquals(0.013, recorded.single().audioSeconds, EPS)
    }

    @Test
    fun `strong marks get the pace's silence in the clip, and the switch turns them off`() {
        val voices = RecordingVoices()
        var style = SpeechStyle(pace = Pace.NORMAL)
        val renderer = EngineRenderer(voices, style = { style }, styleConfig = flatPaces)
        val sentence = unit(0, 0).copy(text = "Wait — this (and that): done.")

        val cut = (renderer.render(sentence) as Outcome.Success).value
        style = SpeechStyle(pace = Pace.NORMAL, clausePauses = false)
        val whole = (renderer.render(sentence) as Outcome.Success).value

        assertEquals(listOf("Wait", "this", "and that", "done.", "Wait — this (and that): done."), voices.spoken)
        // Voice samples are 100, quieter in brackets; silences are zero: dash 200, bracket 120, colon 240 (flat pace).
        val runs = silentRuns(cut.samples)
        assertEquals(listOf(200, 120, 240), runs)
        assertEquals("Wait".length + "this".length + "done.".length, cut.samples.count { it == SAMPLE })
        assertEquals("and that".length, cut.samples.count { it in 1 until SAMPLE })
        assertEquals("Wait — this (and that): done.".length, whole.samples.size)
    }

    @Test
    fun `render speed leaves out the clause pauses`() {
        val renderer = EngineRenderer(RecordingVoices(), speed = RenderSpeedTracker { speeds }, styleConfig = flatPaces)
        val sentence = unit(0, 0).copy(text = "One; two.")

        renderer.render(sentence)
        renderer.render(sentence.copy(index = 1))

        // "One" and "two." are 7 ms of voice; the 240 ms semicolon pause is not the voice's.
        assertEquals(0.007, recorded.single().audioSeconds, EPS)
    }

    /** Lengths of the runs of silence between voiced samples. */
    private fun silentRuns(samples: ShortArray): List<Int> {
        val runs = ArrayList<Int>()
        var run = 0
        for (sample in samples) {
            if (sample == 0.toShort()) {
                run++
            } else if (run > 0) {
                runs.add(run)
                run = 0
            }
        }
        return runs
    }

    /** Normal pace adds nothing, so a clip's silence is exactly the sentence's own pause. */
    private val flatPaces = SpeechStyleConfig(
        paces = Pace.entries.associateWith { PaceTiming(sentenceGapMs = 0, pauseScale = 1f) },
    )

    private companion object {
        val VOICE = VoiceId(EngineId.SHERPA, "en-voice")
        const val SAMPLE: Short = 100
        const val EPS = 1e-9
    }
}
