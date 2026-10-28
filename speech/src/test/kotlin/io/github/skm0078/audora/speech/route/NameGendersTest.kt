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

    @Test
    fun `a Hindi or Bengali name the lists lack gets the gender its ending most likely tells`() {
        listOf("नीलिमा" to Lang.HI, "সুখমতী" to Lang.HI, "ঝুমকি" to Lang.BN).forEach { (name, lang) ->
            assertEquals(name, SpeakerGender.UNKNOWN, NameGenders.of(name, lang))
        }
        assertEquals(SpeakerGender.FEMALE, NameGenders.likely("नीलिमा", Lang.HI))
        assertEquals(SpeakerGender.FEMALE, NameGenders.likely("রঞ্জনা", Lang.BN))
        assertEquals(SpeakerGender.FEMALE, NameGenders.likely("সুরবালী", Lang.BN))
        assertEquals(SpeakerGender.MALE, NameGenders.likely("टिल्लू", Lang.HI))
        assertEquals(SpeakerGender.MALE, NameGenders.likely("रमेसर", Lang.HI))
        assertEquals(SpeakerGender.MALE, NameGenders.likely("হরিপদ", Lang.BN))
        assertEquals(SpeakerGender.MALE, NameGenders.likely("বিনয়", Lang.BN))
        assertEquals(SpeakerGender.MALE, NameGenders.likely("दूधवाला", Lang.HI))
    }

    @Test
    fun `an ending that tells nothing, an English name and a known role word are not guessed`() {
        // -i and -u in Bengali are both genders' (রবি, টুনি; অপু, মিনু); English endings tell nothing.
        assertEquals(SpeakerGender.UNKNOWN, NameGenders.likely("ঝুমকি", Lang.BN))
        assertEquals(SpeakerGender.UNKNOWN, NameGenders.likely("পল্টু", Lang.BN))
        assertEquals(SpeakerGender.UNKNOWN, NameGenders.likely("Okafor", Lang.EN))
        assertEquals(SpeakerGender.UNKNOWN, NameGenders.likely("Kit", Lang.EN))
        // Role words ending in -ā are men's: the hand list decides before the ending.
        assertEquals(SpeakerGender.MALE, NameGenders.likely("দারোগা", Lang.BN))
        assertEquals(SpeakerGender.MALE, NameGenders.likely("मुखिया", Lang.HI))
        assertEquals(SpeakerGender.MALE, NameGenders.likely("बड़े बाबू", Lang.HI))
    }
}
