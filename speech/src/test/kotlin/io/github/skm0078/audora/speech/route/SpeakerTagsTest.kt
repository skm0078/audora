// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/route/SpeakerTagsTest.kt
// Purpose: Dialogue tags in English, Hindi and Bengali name the right speaker, and plain narration names none.

package io.github.skm0078.audora.speech.route

import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.speech.sherpa.SpeakerGender
import io.github.skm0078.audora.speech.sherpa.SpeakerGender.FEMALE
import io.github.skm0078.audora.speech.sherpa.SpeakerGender.MALE
import io.github.skm0078.audora.speech.sherpa.SpeakerGender.UNKNOWN
import org.junit.Assert.assertEquals
import org.junit.Test

class SpeakerTagsTest {
    private val tags = SpeakerTags()

    private data class Case(val text: String, val lang: Lang, val name: String?, val gender: SpeakerGender?)

    @Test
    fun englishTags() = check(
        Case("“Come here at once,” said Tom.", Lang.EN, "Tom", MALE),
        Case("“Come here at once,” Tom said.", Lang.EN, "Tom", MALE),
        Case("“Come here at once,” Tom said quietly.", Lang.EN, "Tom", MALE),
        Case("“Come here at once,” Tom quietly said.", Lang.EN, "Tom", MALE),
        Case("“Come here at once,” Zorvan said.", Lang.EN, "Zorvan", UNKNOWN),
        Case("“All hands!” roared the captain.", Lang.EN, "the captain", MALE),
        Case("“Two hundred,” said the old woman behind the counter.", Lang.EN, "the old woman", FEMALE),
        Case("“Where were you last night?” asked Mrs Gupta.", Lang.EN, "Mrs Gupta", FEMALE),
        Case("“I have no idea at all,” replied Mr. Das.", Lang.EN, "Mr. Das", MALE),
        Case("“Leave the boat alone,” said the old man to his son.", Lang.EN, "the old man", MALE),
        Case("“It is late now,” she said.", Lang.EN, null, FEMALE),
        Case("“It is late now,” he whispered.", Lang.EN, null, MALE),
        Case("Mira Sen asked, “Is anyone there tonight?”", Lang.EN, "Mira Sen", FEMALE),
        Case("Then she said, “We go at dawn.”", Lang.EN, null, FEMALE),
        Case("“We go at dawn,” said Anna, and left.", Lang.EN, "Anna", FEMALE),
    )

    @Test
    fun englishWithoutATagNamesNoOne() = check(
        Case("The river was quiet that night.", Lang.EN, null, null),
        Case("“We go at dawn, and no later.”", Lang.EN, null, null),
        Case("He called it “a very strange business” and smiled.", Lang.EN, null, null),
        Case("“We go at dawn,” The end.", Lang.EN, null, null),
    )

    @Test
    fun hindiTags() = check(
        Case("“तुम कहाँ थे कल रात?” राम ने पूछा।", Lang.HI, "राम", MALE),
        Case("“मैं घर पर ही थी,” सीता बोली।", Lang.HI, "सीता", FEMALE),
        Case("“मैं घर पर ही था,” मोहन बोला।", Lang.HI, "मोहन", MALE),
        Case("“अब चलो यहाँ से,” उसने कहा।", Lang.HI, null, UNKNOWN),
        Case("वह बोली, “अब चलो यहाँ से।”", Lang.HI, null, FEMALE),
        Case("“अब चलो यहाँ से,” तब राम ने कहा।", Lang.HI, "राम", MALE),
        Case("“डर लग रहा है,” उसने धीरे से कहा।", Lang.HI, null, UNKNOWN),
        Case("“मैं कहाँ रो रही हूँ,” वह आँसू पोंछते हुए बोली।", Lang.HI, null, FEMALE),
        Case("‘सच?’ पिताजी ने चश्मा उतारते हुए पूछा।", Lang.HI, "पिताजी", MALE),
        Case("“कौन है वहाँ?” सेठ जी चिल्लाए।", Lang.HI, "सेठ जी", MALE),
        Case("“हर हफ़्ते,” बिरजू मुस्कुराया।", Lang.HI, "बिरजू", MALE),
        Case("नदी शांत थी।", Lang.HI, null, null),
    )

    @Test
    fun bengaliTags() = check(
        Case("“তুমি কোথায় ছিলে কাল?” রহিম জিজ্ঞেস করল।", Lang.BN, "রহিম", MALE),
        Case("“আমি বাড়িতেই ছিলাম,” মা বললেন।", Lang.BN, "মা", FEMALE),
        Case("“চলো এখান থেকে যাই,” সে বলল।", Lang.BN, null, UNKNOWN),
        Case("“তোর জন্য কী এনেছি দেখ,” দাদা হেসে বলল।", Lang.BN, "দাদা", MALE),
        Case("“বড় সুন্দর নাম,” বৃদ্ধ হাসিয়া কহিল।", Lang.BN, "বৃদ্ধ", MALE),
        Case("মন্ত্রী কাঁপিতে কাঁপিতে কহিলেন, “যুদ্ধে, মহারাজ।”", Lang.BN, "মন্ত্রী", MALE),
        Case("তারপর দীর্ঘশ্বাস ফেলে বললেন, “ঠিক আছে, কাল থেকে পড়াব।”", Lang.BN, null, null),
        Case("‘চুপ কর, ঘুমো,’ পল্টু ফিসফিস করে বলল।", Lang.BN, "পল্টু", UNKNOWN),
        Case("নদী শান্ত ছিল।", Lang.BN, null, null),
    )

    @Test
    fun subjectsOfNarration() {
        assertEquals(Speaker("Ravi", MALE), tags.subjectOf("Ravi stopped at a stall heaped with mangoes.", Lang.EN))
        assertEquals(Speaker("their mother", FEMALE), tags.subjectOf("Their mother put down her spoon.", Lang.EN))
        assertEquals(Speaker(null, FEMALE), tags.subjectOf("She folded her arms.", Lang.EN))
        assertEquals(Speaker("मीना", FEMALE), tags.subjectOf("मीना हाथ में परिणाम लिए घर में घुसी।", Lang.HI))
        assertEquals(Speaker("शिष्य", MALE), tags.subjectOf("शिष्य ने गुरुजी के चरण छुए।", Lang.HI))
        assertEquals(null, tags.subjectOf("“Where were you?” Tom asked.", Lang.EN))
    }

    @Test
    fun keysFoldCaseAndSpacing() {
        assertEquals("mira sen", Speaker("Mira  Sen", UNKNOWN).key)
        assertEquals(null, Speaker(null, FEMALE).key)
    }

    private fun check(vararg cases: Case) {
        for (case in cases) {
            val found = tags.find(case.text, case.lang)
            val expected = case.gender?.let { Speaker(case.name, it) }
            assertEquals(case.text, expected, found)
        }
    }
}
