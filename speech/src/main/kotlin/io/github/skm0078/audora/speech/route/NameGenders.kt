// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/route/NameGenders.kt
// Purpose: The gender a character's name or role word tells ("Mira", "दादी", "বাবা"), so they can get a voice at once.

package io.github.skm0078.audora.speech.route

import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.speech.sherpa.SpeakerGender
import java.io.IOException
import java.text.Normalizer

/**
 * Without this, a character whose gender no pronoun or verb had told yet kept the plain dialogue voice, and Hindi
 * and Bengali names never got one (Bengali verbs carry no gender, Hindi "ने कहा" neither). Only names and role words
 * that are clearly one gender are listed: a name used for both (Sam, Alex, Kiran, Suman) stays unknown, since a
 * wrong-gender voice is worse than none. The first word decides ("Mira Sen", "सेठ जी", "Grandma Rose"), after an
 * English article ("the old woman" is decided by its last word elsewhere). Lists are lowercase in English and NFC in
 * Hindi and Bengali; matching is on the name as the book prints it. English also knows about 3,000 given names from
 * public-domain birth records ([ENGLISH_DATA], `scripts/build_name_lists.py`), read once on first use; the hand
 * lists win where both know a name.
 */
object NameGenders {
    fun of(name: String, lang: Lang): SpeakerGender {
        val words = name.trim().split(SPACES).filter { it.isNotEmpty() }
        val first = words.firstOrNull()?.trimEnd('.', ',')?.lowercase() ?: return SpeakerGender.UNKNOWN
        val (table, hand) = when (lang) {
            Lang.EN -> englishAll to ENGLISH
            Lang.HI -> HINDI to HINDI
            Lang.BN -> BENGALI to BENGALI
        }
        // The last word only by the hand lists ("the old woman"): a surname is often a given name too ("Dr Ward").
        return table[first] ?: words.lastOrNull()?.lowercase()?.let { hand[it] } ?: SpeakerGender.UNKNOWN
    }

    /**
     * [of], or for a Hindi or Bengali name it does not know, the gender its last sound most often tells: a given name
     * ending in -ā or -ī is a woman's (সীমা, नीलिमा, মালতী, सुखिया), one ending on a consonant (or -ū in Hindi) a
     * man's (হরিপদ, रमेसर, टिल्लू); others (-i, -u in Bengali, -o) stay unknown. About nine in ten names of each
     * kind follow it, against the coin flip a voice-less character gets from the narrator's own voice, so it is used
     * only for a character already found (a tag's or the conversation's speaker), never to make a word a character
     * ([of] decides that). English keeps [of]: its names tell nothing by their ending.
     */
    fun likely(name: String, lang: Lang): SpeakerGender {
        val known = of(name, lang)
        if (known != SpeakerGender.UNKNOWN || lang == Lang.EN) return known
        val word = Normalizer.normalize(name.trim().split(SPACES).first(), Normalizer.Form.NFC)
        val last = word.lastOrNull()
        return when {
            last == null || word.length < MIN_GUESSED_LENGTH -> SpeakerGender.UNKNOWN
            MALE_ENDINGS.any { word.endsWith(it) } -> SpeakerGender.MALE
            last in FEMALE_SIGNS -> SpeakerGender.FEMALE
            lang == Lang.HI && last in HINDI_MALE_SIGNS -> SpeakerGender.MALE
            CONSONANTS.any { last in it } || last in CONSONANT_MARKS -> SpeakerGender.MALE
            else -> SpeakerGender.UNKNOWN
        }
    }

    /** A one-letter "name" is an initial or a stray word: no guess. */
    private const val MIN_GUESSED_LENGTH = 2

    /** -ā and -ī (Devanagari, Bengali). */
    private val FEMALE_SIGNS = setOf('ा', 'ी', 'া', 'ী')

    /** Hindi -ū and -u (टिल्लू, गुड्डू, रामू). */
    private val HINDI_MALE_SIGNS = setOf('ू', 'ु')

    /** Devanagari's and Bengali's consonant letters (a vowel letter at the end, as in "माई", tells nothing). */
    private val CONSONANTS = listOf('क'..'ह', 'ক'..'হ', 'ৎ'..'ৎ')

    /** After NFC a nukta ends a consonant (বিনয়, ज़फ़र), as do the virama and the anusvara. */
    private val CONSONANT_MARKS = setOf('়', '़', '্', '्', 'ং', 'ं')

    /** "-वाला" is the man who does a thing (दूधवाला), however it ends. */
    private val MALE_ENDINGS = listOf("वाला", "वाले")

    private const val TAG = "NameGenders"
    private const val ENGLISH_DATA = "/io/github/skm0078/audora/speech/route/names-en.tsv"
    private val SPACES = Regex("""\s+""")

    private val englishAll: Map<String, SpeakerGender> by lazy { englishData() + ENGLISH }

    /** The data list; empty (hand lists only) when it is missing or unreadable, which is logged. */
    private fun englishData(): Map<String, SpeakerGender> {
        val lines = try {
            NameGenders::class.java.getResourceAsStream(ENGLISH_DATA)?.bufferedReader(Charsets.UTF_8)?.use {
                it.readLines()
            }
        } catch (e: IOException) {
            AudoraLog.w(TAG, "English name list unreadable, using the hand list only", e)
            null
        }
        if (lines == null) AudoraLog.w(TAG, "English name list missing, using the hand list only")
        return lines.orEmpty().filterNot { it.startsWith('#') }.mapNotNull { line ->
            val (name, gender) = line.split('\t').takeIf { it.size == 2 } ?: return@mapNotNull null
            when (gender) {
                "F" -> name to SpeakerGender.FEMALE
                "M" -> name to SpeakerGender.MALE
                else -> null
            }
        }.toMap()
    }

    private fun table(female: String, male: String): Map<String, SpeakerGender> =
        female.split(' ').filter { it.isNotBlank() }.associateWith { SpeakerGender.FEMALE } +
            male.split(' ').filter { it.isNotBlank() }.associateWith { SpeakerGender.MALE }

    private val ENGLISH = table(
        female = "mrs ms miss lady madam madame dame aunt auntie grandma granny grandmother mother mum mom mummy " +
            "sister queen princess duchess " +
            "mary anna anne ann emma mira grace lily nora asha elizabeth jane sarah lucy alice emily charlotte " +
            "margaret helen kate katherine catherine ruth rose rosie sophie sophia olivia amelia isabella ella " +
            "hannah laura rachel rebecca jessica jennifer linda susan karen nancy betty dorothy sandra ashley " +
            "michelle carol amanda melissa deborah stephanie sharon cynthia kathleen amy shirley angela brenda " +
            "pamela nicole samantha christine diana janet julia joyce victoria kelly christina lauren joan evelyn " +
            "judith megan andrea martha frances gloria teresa sara janice jean doris abigail julie marie beth " +
            "bella luna kiki molly polly meg elinor marianne lydia kitty harriet fanny ada agnes edith ethel mabel " +
            "florence clara eleanor priya anjali neha pooja sita meena radha lakshmi kamala leela uma usha sunita " +
            "kavita rekha divya aditi ananya diya riya isha tara maya nina mina sudha minu wendy heidi",
        male = "mr sir lord king prince duke uncle grandpa grandfather father dad daddy brother monk " +
            "tom thomas john james robert michael william david richard joseph charles christopher daniel " +
            "matthew anthony mark donald steven paul andrew joshua kenneth kevin brian george timothy ronald " +
            "edward jason jeffrey ryan jacob gary nicholas eric jonathan stephen larry justin scott brandon " +
            "benjamin samuel gregory frank alexander raymond patrick jack dennis jerry tyler aaron adam henry " +
            "nathan douglas zachary peter kyle walter ethan jeremy harold keith roger noah gerald carl terry " +
            "sean arthur lawrence jesse bruce albert gabriel alan wayne roy ralph eugene vincent russell elijah " +
            "louis philip johnny harry ron fred bob bill jim ben max oliver jonah ahab ishmael sherlock watson " +
            "darcy bingley ravi arjun rahul raj amit vikram rohan hamid ram krishna mohan gopal hari ramesh " +
            "suresh anil sunil vijay ajay sanjay amar dev kabir aman ishaan jasper bruno hugo leo",
    )

    private val HINDI = table(
        female = "माँ मां माता माताजी अम्मा अम्माँ दादी नानी बुआ मौसी चाची ताई भाभी दीदी बहन बेटी बिटिया लड़की रानी " +
            "बहू सास पत्नी मालकिन बुढ़िया औरत स्त्री देवी नौकरानी धोबिन मालिन पड़ोसन दाई मास्टरनी सेठानी ठकुराइन " +
            "पंडिताइन मेमसाहब सीता गीता मीना सुधा कमला राधा लक्ष्मी सरस्वती पार्वती उमा आशा उषा निशा पूजा प्रिया " +
            "नेहा अंजलि सुनीता अनीता रीता शीला सरला विमला निर्मला कविता ममता सविता रेखा शांति माया गंगा यमुना " +
            "धनिया मुन्नी गुड्डी चंपा बेला रुक्मिणी सावित्री दुर्गा लीला इंदु रजनी दीपा स्नेहा श्वेता नंदिनी " +
            "मालती कौशल्या सुखिया झुनिया आरती अर्चना बबीता बीना भारती चंदा चंद्रिका दिव्या कुसुम लता मंजू मनीषा " +
            "मीरा मोहिनी नीलम नीता पद्मा पुष्पा राखी रश्मि रूपा सपना संगीता शकुंतला शारदा शोभा सोनी तारा तुलसी " +
            "उर्मिला वंदना वीणा यशोदा जानकी कावेरी गौरी हेमा इला जया कोयल लाली मैना फूलमती रामप्यारी बुधिया पारो " +
            "धनो गोमती नर्मदा अहिल्या द्रौपदी सुभद्रा उर्वशी मेनका शबनम नसरीन फ़ातिमा ज़ैनब सलमा रज़िया आयशा " +
            "अमीना",
        male = "पिता पिताजी बाबूजी बापू दादा नाना चाचा ताऊ मामा भैया भाई बेटा लड़का राजा सेठ मालिक पति गुरुजी गुरु " +
            "शिष्य बाबा बाबू दरोगा दारोगा थानेदार सिपाही डाकिया मुखिया पटवारी चौकीदार मास्टर मास्टरजी वकील किसान " +
            "नौकर धोबी नाई माली हलवाई लाला बनिया महाजन ज़मींदार साधु संन्यासी फ़कीर भिखारी मौलवी काज़ी दुकानदार " +
            "कुली मुनीम पंडित पुजारी मुंशी ठाकुर साहब बूढ़ा आदमी राम श्याम मोहन सोहन रामू हामिद अमर रवि राजू " +
            "राजेश सुरेश रमेश महेश दिनेश गणेश विजय अजय संजय अर्जुन कृष्ण किशन गोपाल होरी गोबर हरि शंकर विष्णु " +
            "लक्ष्मण भरत प्रकाश अशोक अनिल सुनील मनोज विनोद प्रेम बलराम गोविंद माधव केशव रघु मुन्ना भोला सूरज चंदन " +
            "दीपक आलोक राहुल रोहित अमित सुमित नरेश जगदीश सलीम रहीम करीम अली अहमद यूसुफ कल्लू लल्लू पप्पू बबलू " +
            "छोटू मंगल बुधई घीसू माधो हल्कू जोखू झूरी धनपत बंसी बिहारी मुरारी कन्हैया भीखू शिवा अभय आदित्य आकाश " +
            "अंकित अनुज भुवन चेतन देवेंद्र धर्मेंद्र गौरव हर्ष जितेंद्र कमल कुणाल महावीर नवीन पंकज पवन राजीव " +
            "संदीप शेखर तरुण उमेश वरुण यश रामदीन रामलाल लालू बुद्धू नत्थू मंगरू सुक्खू इरफ़ान रशीद इक़बाल अकबर " +
            "ज़फ़र",
    )

    private val BENGALI = table(
        female = "মা মাসি পিসি কাকিমা জেঠিমা মামি ঠাকুমা দিদিমা দিদি বৌদি বোন মেয়ে বালিকা রানি গিন্নি স্ত্রী বউ বৌ " +
            "খুকি বৃদ্ধা বুড়ি মহিলা ঝি দাসী রানিমা মেমসাহেব ঠাকুরানি গিন্নিমা মাসিমা পিসিমা জ্যেঠিমা কাকি মামিমা " +
            "বৌমা নাতনি শাশুড়ি ননদ মিনু রমা উমা সীতা গীতা মীনা কমলা লক্ষ্মী সরস্বতী দুর্গা অপর্ণা অন্নপূর্ণা " +
            "পারুল বকুল শিউলি মালতী মাধবী রেখা রীতা সুমিতা অনিতা দীপা টুম্পা রুমা শ্যামলী চারুলতা বিমলা কুসুম " +
            "হৈমন্তী লাবণ্য নন্দিনী সুচিত্রা অঞ্জলি মৌ মৌমিতা সোমা রিনা শম্পা পিয়ালী মিতা মিলি বুলবুল মণিমালা " +
            "নির্মলা প্রতিমা পূর্ণিমা রাধা শকুন্তলা কল্পনা সুপ্রিয়া মধুমিতা দেবযানী ইন্দ্রাণী তনুশ্রী শর্মিলা " +
            "সুস্মিতা পায়েল তিস্তা মহুয়া চন্দনা জয়া ভারতী অরুন্ধতী গৌরী পার্বতী শান্তি মায়া আলো পুতুল খুকু " +
            "ঝুনু বেলা কাজল সুমি তপতী মমতা সবিতা সুলতা কবিতা বনলতা নয়না দামিনী কাদম্বিনী মৃণালিনী হেমলতা রত্না " +
            "স্বাতী রূপা শবনম রহিমা ফাতেমা নাসরিন আমিনা রোকেয়া",
        male = "বাবা দাদা কাকা জেঠু জ্যাঠা মামা মেসো ঠাকুরদা দাদু ভাই ছেলে বালক রাজা মন্ত্রী বৃদ্ধ বুড়ো দোকানদার " +
            "মাস্টারমশাই স্বামী খোকা দারোগা চৌকিদার জমিদার মাস্টার পণ্ডিত পুরোহিত ঠাকুর মোড়ল সর্দার মাঝি ধোপা " +
            "নাপিত গোয়ালা মুদি ময়রা কবিরাজ উকিল চাকর পিয়ন সাহেব মহারাজ মৌলবি ফকির সাধু সন্ন্যাসী কেরানি " +
            "দারোয়ান মালি মালী রহিম করিম রাহুল টুবাই বাবলু অপু গোপাল হরি রাম শ্যাম অমল বিমল নিখিল অনিল সুনীল " +
            "সুব্রত প্রদীপ দীপক তপন স্বপন রতন মদন নিতাই গৌর বিশু ফেলুদা তোপসে শঙ্কর রবি অর্জুন গণেশ মানিক রঞ্জন " +
            "সুকুমার ব্যোমকেশ অজিত ভোলা নিমাই কানাই বলাই রমেন নরেন সুরেন দেবেন বীরেন জীবন কালীপদ শিবু বিনয় অশোক " +
            "অভিজিৎ অরূপ অনির্বাণ দেবাশিস সৌমিত্র সমীর সঞ্জয় শুভ্র তন্ময় উত্তম পার্থ প্রবীর বিকাশ কমল কেষ্ট " +
            "গোবিন্দ জগন্নাথ নারায়ণ ভূপতি শচীন হাবুল পটলা টেনিদা ঘনাদা নন্দলাল দুলাল বিভূতি রাখাল মোহন রজত কৌশিক " +
            "রাজু বাপি খোকন বাবু তোতন রফিক কামাল জামাল হাসান নজরুল আলম",
    )
}
