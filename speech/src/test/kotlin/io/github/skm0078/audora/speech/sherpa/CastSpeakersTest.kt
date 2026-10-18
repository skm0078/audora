// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/sherpa/CastSpeakersTest.kt
// Purpose: Character speakers are used only on the model version their table was written for.

package io.github.skm0078.audora.speech.sherpa

import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.getOrNull
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
    fun `Kokoro lists 27 English speakers besides the narrator`() {
        assertEquals(27, CastSpeakers.of(kokoro).count { it.speakerId != HEART })
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
        const val HEART = 3
        const val ADAM = 11
        const val KOKORO_V1_0 = 53
        const val KOKORO_V1_1 = 103
        const val KITTEN_V0_8 = 8
    }
}
