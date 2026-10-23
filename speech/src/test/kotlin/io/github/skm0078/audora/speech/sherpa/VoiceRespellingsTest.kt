// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/sherpa/VoiceRespellingsTest.kt
// Purpose: Per-voice spelling fixes reach only the voice they were found on, in either encoding of a nukta letter.

package io.github.skm0078.audora.speech.sherpa

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceRespellingsTest {
    @Test
    fun `Piper Hindi reads gh, kh and q without the nukta, and keeps z and f`() {
        val fixed = VoiceRespellings.apply("piper-hi-pratham", "वह ग़ज़ल गा रहा था, ख़ुश और क़ानूनी।")

        assertEquals("वह गज़ल गा रहा था, खुश और कानूनी।", fixed)
        assertEquals("ज़िंदगी फ़ायदा", VoiceRespellings.apply("piper-hi-priyamvada", "ज़िंदगी फ़ायदा"))
    }

    @Test
    fun `a nukta letter written as one code point is fixed too`() {
        assertEquals("गजल", VoiceRespellings.apply("piper-hi-pratham", "ग़जल").replace("ज़", "ज"))
    }

    @Test
    fun `other voices get the text unchanged`() {
        assertEquals("ग़ज़ल", VoiceRespellings.apply("supertonic-hi", "ग़ज़ल"))
    }
}
