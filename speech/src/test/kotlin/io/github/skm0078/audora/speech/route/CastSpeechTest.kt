// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/route/CastSpeechTest.kt
// Purpose: Quotes, headings and known characters are read by their own voices, and a cast voice that fails gives way
// (plan items 7 and 63).

package io.github.skm0078.audora.speech.route

import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.speech.RenderResult
import io.github.skm0078.audora.speech.SpeechEngine
import io.github.skm0078.audora.speech.SpeechError
import io.github.skm0078.audora.speech.VoiceInfo
import io.github.skm0078.audora.speech.audio.PcmAudio
import io.github.skm0078.audora.speech.normalize.TextRole
import io.github.skm0078.audora.speech.sherpa.SpeakerGender
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CastSpeechTest {
    /** One sample per character; [broken] voices fail, like an uninstalled model. */
    private class RecordingEngine(private val broken: Set<String> = emptySet()) : SpeechEngine {
        override val id = EngineId.SHERPA
        val calls = mutableListOf<Pair<String, String>>()

        override fun supports(lang: Lang): Boolean = true

        override suspend fun voices(): Outcome<List<VoiceInfo>, SpeechError> = Outcome.Success(emptyList())

        override suspend fun render(text: String, voice: VoiceId, rate: Float): RenderResult {
            if (voice.name in broken) {
                return Outcome.Failure(
                    SpeechError.VoiceUnavailable(id, SpeechError.VoiceUnavailable.Reason.NOT_INSTALLED, voice, Lang.EN),
                )
            }
            calls.add(voice.name to text)
            return Outcome.Success(PcmAudio(ShortArray(text.length) { 1 }, SAMPLE_RATE))
        }

        override fun release() = Unit
    }

    private class FakeVoices(val engine: RecordingEngine) : VoiceSource {
        override suspend fun voiceFor(lang: Lang): Outcome<VoiceId, SpeechError> = Outcome.Success(NARRATOR)

        override fun engine(id: EngineId): Outcome<SpeechEngine, SpeechError> = Outcome.Success(engine)
    }

    private fun speech(engine: RecordingEngine) =
        CastSpeech(FakeVoices(engine), ScriptRouter(), normalizers = emptyMap())

    @Test
    fun `quotes go to the dialogue voice with the quote pause between`() = runTest {
        val engine = RecordingEngine()

        val result = speech(engine).render(
            "“Come here at once,” she said.",
            Lang.EN,
            cast = Cast(dialogue = mapOf(Lang.EN to DIALOGUE)),
            quotePauseMs = PAUSE_MS,
        )

        val routed = (result as Outcome.Success).value
        assertEquals(listOf(DIALOGUE.name to "Come here at once,", NARRATOR.name to "she said."), engine.calls)
        assertEquals(listOf(DIALOGUE, NARRATOR), routed.voices)
        assertEquals(PAUSE_MS.toLong(), routed.pausesMs)
        val pauseSamples = SAMPLE_RATE * PAUSE_MS / MS_PER_SECOND
        assertEquals("Come here at once,".length + pauseSamples + "she said.".length, routed.audio.samples.size)
    }

    @Test
    fun `a heading goes whole to the heading voice, quotes and all`() = runTest {
        val engine = RecordingEngine()
        val cast = Cast(dialogue = mapOf(Lang.EN to DIALOGUE), headings = mapOf(Lang.EN to HEADING))

        speech(engine).render("Chapter One: “Run, run away”", Lang.EN, SpeechRequest(TextRole.HEADING), cast)

        assertEquals(listOf(HEADING.name to "Chapter One: “Run, run away”"), engine.calls)
    }

    @Test
    fun `without a cast or a quote the narrator reads the sentence as written`() = runTest {
        val engine = RecordingEngine()

        speech(engine).render("“Come here at once,” she said.", Lang.EN)
        speech(engine).render("No speech here.", Lang.EN, cast = Cast(dialogue = mapOf(Lang.EN to DIALOGUE)))
        speech(engine).render("A heading", Lang.EN, SpeechRequest(TextRole.HEADING), Cast(mapOf(Lang.EN to DIALOGUE)))

        assertEquals(
            listOf("“Come here at once,” she said.", "No speech here.", "A heading").map { NARRATOR.name to it },
            engine.calls,
        )
    }

    @Test
    fun `a language with no dialogue voice reads the sentence whole with the narrator, unsplit`() = runTest {
        val engine = RecordingEngine()
        val noOne = Cast(characters = { _, _ -> null })

        speech(engine).render("“Come here at once,” she said.", Lang.EN, cast = Cast(mapOf(Lang.HI to DIALOGUE)))
        val tom = SpeechRequest(speaker = Speaker("Tom", SpeakerGender.MALE))
        speech(engine).render("“Halt,” he said.", Lang.EN, tom, noOne)

        assertEquals(
            listOf(NARRATOR.name to "“Come here at once,” she said.", NARRATOR.name to "“Halt,” he said."),
            engine.calls,
        )
    }

    @Test
    fun `a dialogue voice that cannot speak gives the quote to the narrator`() = runTest {
        val engine = RecordingEngine(broken = setOf(DIALOGUE.name))

        val result = speech(engine).render(
            "“Come here at once,” she said.",
            Lang.EN,
            cast = Cast(dialogue = mapOf(Lang.EN to DIALOGUE)),
        )

        assertEquals(listOf(NARRATOR, NARRATOR), (result as Outcome.Success).value.voices)
    }

    @Test
    fun `each part is levelled before the join`() = runTest {
        val engine = RecordingEngine()
        var levelled = 0

        speech(engine).render(
            "“Come here at once,” she said.",
            Lang.EN,
            cast = Cast(dialogue = mapOf(Lang.EN to DIALOGUE)),
            level = { audio -> audio.also { levelled++ } },
        )

        assertEquals(2, levelled)
    }

    @Test
    fun `a known speaker's quote goes to that character's voice`() = runTest {
        val engine = RecordingEngine()
        val characters = CharacterVoices { _, speaker -> TOM.takeIf { speaker.key == "tom" } }
        val cast = Cast(dialogue = mapOf(Lang.EN to DIALOGUE), characters = characters)

        speech(engine).render(
            "“Come here at once,” Tom said.",
            Lang.EN,
            SpeechRequest(speaker = Speaker("Tom", SpeakerGender.MALE)),
            cast,
        )

        assertEquals(listOf(TOM.name to "Come here at once,", NARRATOR.name to "Tom said."), engine.calls)
    }

    @Test
    fun `a speaker with no voice of their own keeps the dialogue voice`() = runTest {
        val engine = RecordingEngine()
        val cast = Cast(dialogue = mapOf(Lang.EN to DIALOGUE), characters = { _, _ -> null })

        speech(engine).render(
            "“Come here at once,” Ann said.",
            Lang.EN,
            SpeechRequest(speaker = Speaker("Ann", SpeakerGender.FEMALE)),
            cast,
        )

        assertEquals(listOf(DIALOGUE.name to "Come here at once,", NARRATOR.name to "Ann said."), engine.calls)
    }

    @Test
    fun `a sentence inside a quote opened earlier is read by the dialogue voice`() = runTest {
        val engine = RecordingEngine()

        speech(engine).render(
            "It was late and the road was long.",
            Lang.EN,
            SpeechRequest(startsInQuote = true),
            Cast(dialogue = mapOf(Lang.EN to DIALOGUE)),
        )

        assertEquals(listOf(DIALOGUE.name to "It was late and the road was long."), engine.calls)
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val PAUSE_MS = 80
        const val MS_PER_SECOND = 1000
        val NARRATOR = VoiceId(EngineId.SHERPA, "narrator")
        val DIALOGUE = VoiceId(EngineId.SHERPA, "dialogue")
        val HEADING = VoiceId(EngineId.SHERPA, "heading")
        val TOM = VoiceId(EngineId.SHERPA, "tom")
    }
}
