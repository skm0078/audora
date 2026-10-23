// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/sherpa/CastSpeakersTest.kt
// Purpose: Character speakers are used only on the catalog voice and model version their table was written for.

package io.github.skm0078.audora.speech.sherpa

import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.getOrNull
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CastSpeakersTest {
    private val kokoro = spec(
        "kokoro-en-heart",
        speakerId = HEART,
    ).copy(family = SherpaFamily.KOKORO, engineLang = "en-us")
    private val kitten = spec("kitten-en-nano", speakerId = 7).copy(family = SherpaFamily.KITTEN)

    @Test
    fun `the tables fit the model versions they were written for`() {
        assertTrue(CastSpeakers.tableFits(kokoro, KOKORO_V1_0))
        assertTrue(CastSpeakers.tableFits(kitten, KITTEN_V0_8))
        assertTrue(CastSpeakers.tableFits(kokoro, null))
        assertFalse(CastSpeakers.tableFits(kokoro, KOKORO_V1_1))
        assertFalse(CastSpeakers.tableFits(kitten, KITTEN_V0_8 + 2))
    }

    @Test
    fun `a listed speaker is used, an unlisted one or another model version falls back to the voice's own`() {
        assertEquals(ADAM, CastSpeakers.speakerFor(kokoro, ADAM, KOKORO_V1_0))
        assertEquals(HEART, CastSpeakers.speakerFor(kokoro, KOKORO_V1_0 - 1, KOKORO_V1_0))
        assertEquals(HEART, CastSpeakers.speakerFor(kokoro, ADAM, KOKORO_V1_1))
        assertEquals(HEART, CastSpeakers.speakerFor(kokoro, null, KOKORO_V1_0))
    }

    @Test
    fun `Rasa's Bengali female narrator has the male speaker for characters, in the 1024-speaker model`() {
        val rasa = spec("rasa-bn", speakerId = 2)

        assertEquals(listOf(SpeakerGender.MALE), CastSpeakers.of(rasa).filter { it.speakerId != 2 }.map { it.gender })
        assertEquals(3, CastSpeakers.speakerFor(rasa, 3, RASA_SPEAKERS))
        assertEquals(2, CastSpeakers.speakerFor(rasa, 3, RASA_SPEAKERS - 1))
    }

    @Test
    fun `Kokoro lists 27 English speakers besides the narrator`() {
        assertEquals(27, CastSpeakers.of(kokoro).count { it.speakerId != HEART })
    }

    @Test
    fun `the tables are keyed on the catalog voice, so an imported model of the same family has no cast`() {
        val imported = kokoro.copy(id = "custom-my-kokoro-1a2b")
        val importedKitten = kitten.copy(id = "custom-my-kitten-3c4d")

        assertTrue(CastSpeakers.of(imported).isEmpty())
        assertTrue(CastSpeakers.of(importedKitten).isEmpty())
        assertEquals(HEART, CastSpeakers.speakerFor(imported, ADAM, KOKORO_V1_0))
        assertTrue(CastSpeakers.tableFits(imported, KOKORO_V1_1))
        assertEquals(KITTEN_V0_8, CastSpeakers.of(kitten).size)
    }

    @Test
    fun `every keyed voice installs the archive its table was written for, in both catalogs`() {
        val catalogs = listOf("models.json", "bundled-voices.json").map { name ->
            (File(name).takeIf { it.isFile } ?: File("speech/$name")).readText()
        }
        // Matched by pattern, not exact spacing, so reformatting a catalog cannot make a voice vanish from the check.
        val nextId = Regex(""""id"\s*:""")
        CastSpeakers.archives.forEach { (voice, archive) ->
            val id = Regex(""""id"\s*:\s*"${Regex.escape(voice)}"""")
            val installs = Regex(""""archive"\s*:\s*"${Regex.escape(archive)}"""")
            val entries = catalogs.mapNotNull { text ->
                id.find(text)?.let { found ->
                    val rest = text.substring(found.range.last + 1)
                    rest.substring(0, nextId.find(rest)?.range?.first ?: rest.length)
                }
            }
            assertTrue("no catalog has $voice", entries.isNotEmpty())
            entries.forEach { assertTrue("$voice no longer installs $archive", installs.containsMatchIn(it)) }
        }
    }

    @Test
    fun `the engine checks the loaded model's speaker count before reading a character`() = runBlocking {
        val renders = mutableListOf<FakeSynthesizer>()
        fun engine(count: Int) = SherpaSpeechEngine(
            listOf(kokoro),
            SherpaSynthesizerFactory { Outcome.Success(FakeSynthesizer(speakerCount = count).also(renders::add)) },
            Dispatchers.IO.limitedParallelism(1),
        )
        val character = CastSpeakers.variant(kokoro.voiceId, ADAM)

        requireNotNull(engine(KOKORO_V1_0).render("Hi.", character).getOrNull())
        requireNotNull(engine(KOKORO_V1_1).render("Hi.", character).getOrNull())

        assertEquals(listOf(ADAM, HEART), renders.map { it.calls.single().second })
    }

    private companion object {
        const val RASA_SPEAKERS = 1024
        const val HEART = 3
        const val ADAM = 11
        const val KOKORO_V1_0 = 53
        const val KOKORO_V1_1 = 103
        const val KITTEN_V0_8 = 8
    }
}
