// File: app/src/androidTest/kotlin/io/github/skm0078/audora/release/CharacterJourney.kt
// Purpose: The release journey's character-voices step on the installed app: a story's women and men are each read
//          in a voice of their own gender by the English narrator's model, kept apart from ReleaseJourneyTest.

package io.github.skm0078.audora.release

import android.util.Log
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.model.VoiceShaping
import io.github.skm0078.audora.core.result.getOrNull
import io.github.skm0078.audora.playback.reading.QuoteCarry
import io.github.skm0078.audora.playback.voice.CharacterCast
import io.github.skm0078.audora.playback.voice.SpeechVoices
import io.github.skm0078.audora.playback.voice.VoiceEngines
import io.github.skm0078.audora.playback.voice.forReading
import io.github.skm0078.audora.release.JourneyDevice.app
import io.github.skm0078.audora.speech.sherpa.CastSpeakers
import io.github.skm0078.audora.speech.sherpa.SpeakerGender
import io.github.skm0078.audora.speech.sherpa.castSpeakers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * Reads a short story the way playback does (who speaks each line, then the book's character cast) with the app's own
 * engines and English narrator, and renders one line per character: Ravi and Mr Brennan in men's voices, Mira and
 * Lucy in women's, each a voice of its own. A narrator with no cast (a one-speaker English voice) is logged and the
 * step passes, as the voice-change steps do on a build with one English voice.
 */
internal object CharacterJourney {
    private const val LOG_TAG = "ReleaseJourney"
    private const val PARAGRAPH_PAUSE_MS = 400
    private val BOOK = BookId("release-journey-characters")

    private val STORY = listOf(
        "Ravi stopped at the gate." to 0,
        "“Where were you?” Mira asked." to PARAGRAPH_PAUSE_MS,
        "“At the shop,” he said." to PARAGRAPH_PAUSE_MS,
        "Mr Brennan looked up from the till." to 0,
        "“We're closed,” he said." to PARAGRAPH_PAUSE_MS,
        "“It's me, Dad,” said Lucy." to PARAGRAPH_PAUSE_MS,
    )

    fun womenAndMenGetVoicesOfTheirGender() {
        val engines = VoiceEngines(app)
        try {
            check(engines)
        } finally {
            engines.release()
        }
    }

    private fun check(engines: VoiceEngines) {
        val voices = SpeechVoices(engines)
        val narrator = runBlocking { voices.voiceFor(Lang.EN) }.getOrNull()
        val engine = requireNotNull(engines.bundled().getOrNull()?.engine) { "no neural engine" }
        val base = narrator?.let { CastSpeakers.parse(it).first }
        val genders = base?.let { model ->
            engine.castSpeakers(model).associate { CastSpeakers.variant(model, it.speakerId) to it.gender }
        }.orEmpty()
        if (genders.isEmpty()) {
            Log.w(LOG_TAG, "English narrator $narrator has no cast; character voices are not checked")
            return
        }
        val units = STORY.mapIndexed { i, (text, pause) -> unit(i, text, pause) }
        val carry = QuoteCarry().apply { page(units) }
        val cast = CharacterCast.forReading(app, engines, { voices }) { VoiceShaping() }.apply { open(BOOK) }
        val expected = mapOf(
            1 to SpeakerGender.FEMALE,
            2 to SpeakerGender.MALE,
            4 to SpeakerGender.MALE,
            5 to SpeakerGender.FEMALE,
        )
        val read = expected.map { (index, gender) ->
            val speaker = requireNotNull(carry.speakerOf(units[index])) { "no speaker for line $index" }
            assertEquals("line $index's speaker", gender, speaker.gender)
            val voice = requireNotNull(cast.voiceFor(Lang.EN, speaker)) { "line $index has no voice of its own" }
            assertEquals("line $index's voice $voice", gender, genders[voice] ?: SpeakerGender.UNKNOWN)
            val audio = runBlocking { engine.render(units[index].text, voice) }.getOrNull()
            assertTrue("line $index rendered with $voice", (audio?.samples?.size ?: 0) > 0)
            speaker.key to voice
        }.toMap()
        // Four characters, four voices: none shares one with another, and the narrator reads none of them.
        assertEquals(read.toString(), read.size, read.values.toSet().size)
        assertTrue(narrator !in read.values)
    }

    private fun unit(index: Int, text: String, pause: Int) = ReadingUnit(
        page = 0,
        index = index,
        charStart = 0,
        charEnd = text.length,
        text = text,
        lang = Lang.EN,
        pauseAfterMs = pause,
        bounds = null,
    )
}
