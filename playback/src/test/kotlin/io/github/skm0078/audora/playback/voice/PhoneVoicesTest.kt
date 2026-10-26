// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/voice/PhoneVoicesTest.kt
// Purpose: A phone language's voice is found once, and a missing one is asked again only after the retry window
//          (ADR 2026-10-26).

package io.github.skm0078.audora.playback.voice

import io.github.skm0078.audora.core.config.PhoneLanguageConfig
import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.speech.SpeechError
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneVoicesTest {
    private var now = 0L
    private val asked = mutableListOf<String>()
    private val installed = mutableSetOf<String>()

    private val voices = PhoneVoices(
        find = { language ->
            asked += language
            if (language in installed) {
                Outcome.Success(VoiceId(EngineId.SYSTEM, "phone-$language"))
            } else {
                Outcome.Failure(MISSING)
            }
        },
        config = PhoneLanguageConfig(missingVoiceRetryMs = RETRY_MS),
        clock = { now },
    )

    @Test
    fun `a found voice is kept, the phone is asked once`() = runTest {
        installed += "fr"

        val first = voices.voiceFor("fr")
        val second = voices.voiceFor("fr")

        assertEquals(Outcome.Success(VoiceId(EngineId.SYSTEM, "phone-fr")), first)
        assertEquals(first, second)
        assertEquals(listOf("fr"), asked)
    }

    @Test
    fun `a missing voice answers from memory inside the window, then the phone is asked again`() = runTest {
        assertTrue(voices.voiceFor("ta") is Outcome.Failure)
        now = RETRY_MS - 1
        assertTrue(voices.voiceFor("ta") is Outcome.Failure)
        assertEquals(listOf("ta"), asked)

        // The listener downloads the Tamil voice pack meanwhile: it is picked up without a restart.
        installed += "ta"
        now = RETRY_MS

        assertEquals(Outcome.Success(VoiceId(EngineId.SYSTEM, "phone-ta")), voices.voiceFor("ta"))
        assertEquals(listOf("ta", "ta"), asked)
    }

    @Test
    fun `each language is remembered on its own`() = runTest {
        installed += "fr"

        voices.voiceFor("ta")
        voices.voiceFor("fr")
        voices.voiceFor("ta")

        assertEquals(listOf("ta", "fr"), asked)
    }

    private companion object {
        const val RETRY_MS = 60_000L
        val MISSING = SpeechError.VoiceUnavailable(EngineId.SYSTEM, SpeechError.VoiceUnavailable.Reason.NOT_INSTALLED)
    }
}
