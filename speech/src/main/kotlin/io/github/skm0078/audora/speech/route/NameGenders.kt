// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/route/NameGenders.kt
// Purpose: The gender a character's name or role word tells ("Mira", "दादी", "বাবা"), so they can get a voice at once.

package io.github.skm0078.audora.speech.route

import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.speech.sherpa.SpeakerGender

/**
 * Without this, a character whose gender no pronoun or verb had told yet kept the plain dialogue voice, and Hindi
 * and Bengali names never got one (Bengali verbs carry no gender, Hindi "ने कहा" neither). Only names and role words
 * that are clearly one gender are listed: a name used for both (Sam, Alex, Kiran, Suman) stays unknown, since a
 * wrong-gender voice is worse than none. The first word decides ("Mira Sen", "सेठ जी", "Grandma Rose"), after an
 * English article ("the old woman" is decided by its last word elsewhere). Lists are lowercase in English and NFC in
 * Hindi and Bengali; matching is on the name as the book prints it.
 */
object NameGenders {
    fun of(name: String, lang: Lang): SpeakerGender {
        val words = name.trim().split(SPACES).filter { it.isNotEmpty() }
        val first = words.firstOrNull()?.trimEnd('.', ',')?.lowercase() ?: return SpeakerGender.UNKNOWN
        val table = when (lang) {
            Lang.EN -> ENGLISH
            Lang.HI -> HINDI
            Lang.BN -> BENGALI
        }
        return table[first] ?: words.lastOrNull()?.lowercase()?.let { table[it] } ?: SpeakerGender.UNKNOWN
    }

    private val SPACES = Regex("""\s+""")

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
        female = "माँ मां माता माताजी अम्मा अम्माँ दादी नानी बुआ मौसी चाची ताई भाभी दीदी बहन बेटी बिटिया लड़की " +
            "रानी बहू सास पत्नी मालकिन बुढ़िया औरत स्त्री देवी " +
            "सीता गीता मीना सुधा कमला राधा लक्ष्मी सरस्वती पार्वती उमा आशा उषा निशा पूजा प्रिया नेहा अंजलि " +
            "सुनीता अनीता रीता शीला सरला विमला निर्मला कविता ममता सविता रेखा शांति माया गंगा यमुना धनिया " +
            "मुन्नी गुड्डी चंपा बेला रुक्मिणी सावित्री दुर्गा लीला इंदु रजनी दीपा स्नेहा श्वेता नंदिनी " +
            "मालती कौशल्या सुखिया झुनिया",
        male = "पिता पिताजी बाबूजी बापू दादा नाना चाचा ताऊ मामा भैया भाई बेटा लड़का राजा सेठ मालिक पति " +
            "गुरुजी गुरु शिष्य बाबा पंडित पुजारी मुंशी ठाकुर साहब बूढ़ा आदमी " +
            "राम श्याम मोहन सोहन रामू हामिद अमर रवि राजू राजेश सुरेश रमेश महेश दिनेश गणेश विजय अजय संजय " +
            "अर्जुन कृष्ण किशन गोपाल होरी गोबर हरि शंकर विष्णु लक्ष्मण भरत प्रकाश अशोक अनिल सुनील मनोज " +
            "विनोद प्रेम बलराम गोविंद माधव केशव रघु मुन्ना भोला सूरज चंदन दीपक आलोक राहुल रोहित अमित " +
            "सुमित नरेश जगदीश सलीम रहीम करीम अली अहमद यूसुफ",
    )

    private val BENGALI = table(
        female = "মা মাসি পিসি কাকিমা জেঠিমা মামি ঠাকুমা দিদিমা দিদি বৌদি বোন মেয়ে বালিকা রানি গিন্নি স্ত্রী " +
            "বউ বৌ খুকি বৃদ্ধা বুড়ি মহিলা " +
            "মিনু রমা উমা সীতা গীতা মীনা কমলা লক্ষ্মী সরস্বতী দুর্গা অপর্ণা অন্নপূর্ণা পারুল বকুল শিউলি " +
            "মালতী মাধবী রেখা রীতা সুমিতা অনিতা দীপা টুম্পা রুমা শ্যামলী চারুলতা বিমলা কুসুম হৈমন্তী " +
            "লাবণ্য নন্দিনী সুচিত্রা অঞ্জলি দুর্গা",
        male = "বাবা দাদা কাকা জেঠু জ্যাঠা মামা মেসো ঠাকুরদা দাদু ভাই ছেলে বালক রাজা মন্ত্রী বৃদ্ধ বুড়ো " +
            "দোকানদার মাস্টারমশাই স্বামী খোকা " +
            "রহিম করিম রাহুল টুবাই বাবলু অপু গোপাল হরি রাম শ্যাম অমল বিমল নিখিল অনিল সুনীল সুব্রত প্রদীপ " +
            "দীপক তপন স্বপন রতন মদন নিতাই গৌর বিশু ফেলুদা তোপসে শঙ্কর রবি অর্জুন গণেশ মানিক রঞ্জন " +
            "সুকুমার ব্যোমকেশ অজিত",
    )
}
