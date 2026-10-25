// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/route/NameGendersTest.kt
// Purpose: Pins which names tell a gender: the hand lists, the English birth-record list, and what stays unknown.

package io.github.skm0078.audora.speech.route

import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.speech.sherpa.SpeakerGender
import org.junit.Assert.assertEquals
import org.junit.Test

class NameGendersTest {
    @Test
    fun `the English birth-record list tells names the hand list lacks`() {
        assertEquals(SpeakerGender.MALE, NameGenders.of("Felix", Lang.EN))
        assertEquals(SpeakerGender.FEMALE, NameGenders.of("Gwendolyn", Lang.EN))
        assertEquals(SpeakerGender.FEMALE, NameGenders.of("Miss Havisham", Lang.EN))
    }

    @Test
    fun `names books give both genders, and names that are common words, stay unknown`() {
        listOf("Sam", "Alex", "Jordan", "Taylor", "Kit", "Hope", "Will", "June").forEach { name ->
            assertEquals(name, SpeakerGender.UNKNOWN, NameGenders.of(name, Lang.EN))
        }
    }

    @Test
    fun `the first word decides, the last only by the hand lists, never by a surname`() {
        assertEquals(SpeakerGender.FEMALE, NameGenders.of("Grandma Rose", Lang.EN))
        assertEquals(SpeakerGender.FEMALE, NameGenders.of("बूढ़ी नानी", Lang.HI))
        assertEquals(SpeakerGender.UNKNOWN, NameGenders.of("Inspector Ward", Lang.EN))
        assertEquals(SpeakerGender.UNKNOWN, NameGenders.of("Dr Clark", Lang.EN))
    }
}
