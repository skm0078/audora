// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/voice/VoiceWarmUpTest.kt
// Purpose: The voice loaded ahead of Play is the one reading will use, only neural models are loaded, and a failure
//          never escapes (ADR 2026-10-29-speed-quick-wins).

package io.github.skm0078.audora.playback.voice

import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.speech.SpeechError
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceWarmUpTest {
    private val book = BookId("book-1")
    private val bundled = VoiceId(EngineId.SHERPA, "kitten-en-micro")
    private val phone = VoiceId(EngineId.SYSTEM, "hi-in-x-hia-local")
    private val loaded = mutableListOf<VoiceId>()
    private val asked = mutableListOf<Pair<BookId, Lang>>()

    private fun warmUp(
        voice: Outcome<VoiceId, SpeechError> = Outcome.Success(bundled),
        load: Outcome<Unit, SpeechError> = Outcome.Success(Unit),
    ) = VoiceWarmUp(
        voiceFor = { id, lang ->
            asked += id to lang
            voice
        },
        load = { asked ->
            loaded += asked
            load
        },
    )

    @Test
    fun `the voice reading the book's language is picked for that book and loaded`() = runTest {
        assertEquals(bundled, warmUp().warm(book, Lang.EN))
        assertEquals(listOf(book to Lang.EN), asked)
        assertEquals(listOf(bundled), loaded)
    }

    @Test
    fun `a phone voice is not loaded, finding it already bound it`() = runTest {
        assertNull(warmUp(voice = Outcome.Success(phone)).warm(book, Lang.HI))
        assertEquals(emptyList<VoiceId>(), loaded)
    }

    @Test
    fun `no voice for the language loads nothing and does not throw`() = runTest {
        val missing = SpeechError.EngineUnavailable(EngineId.SYSTEM, "no TTS")
        assertNull(warmUp(voice = Outcome.Failure(missing)).warm(book, Lang.BN))
        assertEquals(emptyList<VoiceId>(), loaded)
    }

    @Test
    fun `a model that fails to load is reported as not warmed`() = runTest {
        val broken = SpeechError.EngineUnavailable(EngineId.SHERPA, "native library not loaded")
        assertNull(warmUp(load = Outcome.Failure(broken)).warm(book, Lang.EN))
    }
}
