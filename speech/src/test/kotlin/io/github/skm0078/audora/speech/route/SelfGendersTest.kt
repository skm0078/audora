// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/route/SelfGendersTest.kt
// Purpose: Pins which Hindi first-person forms tell the speaker's gender, and which tell nothing.

package io.github.skm0078.audora.speech.route

import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.speech.sherpa.SpeakerGender
import org.junit.Assert.assertEquals
import org.junit.Test

class SelfGendersTest {
    @Test
    fun `the first person future and a participle before हूँ tell the speaker's gender`() {
        assertEquals(SpeakerGender.FEMALE, SelfGenders.of("मैं कल सुबह आऊँगी।", Lang.HI))
        assertEquals(SpeakerGender.MALE, SelfGenders.of("ठीक है, मैं देख लूँगा।", Lang.HI))
        assertEquals(SpeakerGender.FEMALE, SelfGenders.of("मैं थक गई हूँ।", Lang.HI))
        assertEquals(SpeakerGender.MALE, SelfGenders.of("बस घर जा रहा हूं।", Lang.HI))
        assertEquals(SpeakerGender.FEMALE, SelfGenders.of("मैं रोज़ यहीं बैठती हूँ।", Lang.HI))
        assertEquals(SpeakerGender.MALE, SelfGenders.of("मैं सो रहा था!", Lang.HI))
    }

    @Test
    fun `forms anyone could say, a name before हूँ and "मैंने" tell nothing`() {
        listOf(
            "मज़ाक कर रहा था, पगली।", // no "I": could be about anyone
            "मैं सीता हूँ।", // a name, not a participle
            "मैंने रोटी खाई थी।", // agrees with the roti
            "मैं पहुँचा तो दरवाज़ा खुला था।", // the door was open; पहुँचा alone is not a first-person form here
            "हम कल आएँगे।",
            "मैं आऊँगा, तुम कहोगी कि मैं आऊँगी?", // both: a speech within a speech
        ).forEach { speech -> assertEquals(speech, SpeakerGender.UNKNOWN, SelfGenders.of(speech, Lang.HI)) }
    }

    @Test
    fun `English and Bengali verbs tell nothing`() {
        assertEquals(SpeakerGender.UNKNOWN, SelfGenders.of("I will come tomorrow.", Lang.EN))
        assertEquals(SpeakerGender.UNKNOWN, SelfGenders.of("আমি কাল আসব।", Lang.BN))
    }
}
