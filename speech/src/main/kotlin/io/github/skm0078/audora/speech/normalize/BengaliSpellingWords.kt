// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/normalize/BengaliSpellingWords.kt
// Purpose: The Bengali words BengaliSpelling respells by name, and the verb stems its rules know (ADR 2026-10-30).

package io.github.skm0078.audora.speech.normalize

import java.text.Normalizer

/**
 * The word lists behind [BengaliSpelling], every entry NFC so a word matches however its source composed it. Each
 * entry is a pronunciation a native listener can check by ear; the ADR's word list shows them side by side.
 */
internal object BengaliSpellingWords {
    /** Whole words whose spoken form the rules cannot tell from the letters, written the way they are said. */
    val said: Map<String, String> =
        (SPOKEN_ENDS + WIDE_E + SILENT_ENDS).entries.associate { (word, spoken) -> nfc(word) to nfc(spoken) }

    /**
     * Words left exactly as written. A bare verb stem is a "tui" order with a silent end and an old-spelling "tumi"
     * form with a spoken one ("কর" is "kor" and "koro"); a few words have two readings ("আসল" is "ashol", real, and
     * "ashlo", came); a few English loans end in a cluster Bengali words keep open ("ফর্ম", form). Respelled forms
     * that end in a letter are listed too, so respelling twice changes nothing.
     */
    val asWritten: Set<String> = (BARE_VERBS + TWO_READINGS + LOANS + setOf("দ্যাখ")).map(::nfc).toSet()

    /** Verb stems ending in a consonant: "কর" gives করল, করব, করত ("korlo", "korbo", "korto") and sadhu করিল. */
    val consonantStems: Set<String> = CONSONANT_STEMS.map(::nfc).toSet()

    /** Verb stems ending in a vowel sign: "তাকা" gives তাকাল ("takalo"), তাকাব, তাকাত and sadhu তাকাইল. */
    val vowelStems: Set<String> = VOWEL_STEMS.map(::nfc).toSet()

    private fun nfc(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFC)
}

/** Words that end on a spoken vowel though their letters suggest a silent end. */
private val SPOKEN_ENDS = mapOf(
    "কত" to "কতো", "যত" to "যতো", "তত" to "ততো", "গত" to "গতো", "শত" to "শতো", "নত" to "নতো", "হত" to "হতো",
    "অত" to "অতো", "বড়" to "বড়ো", "ছোট" to "ছোটো", "ভাল" to "ভালো", "শুভ" to "শুভো", "নব" to "নবো", "তব" to "তবো",
    "অথচ" to "অথচো",
    // Older spellings of 11 to 18 (এগারো, তেরো, পনেরো, ষোলো, সতেরো, আঠারো).
    "এগার" to "এগারো", "তের" to "তেরো", "পনের" to "পনেরো", "ষোল" to "ষোলো", "সতের" to "সতেরো", "আঠার" to "আঠারো",
    // Verbs whose forms are not stem + ending.
    "এল" to "এলো", "দিল" to "দিলো", "নিল" to "নিলো", "ছিল" to "ছিলো", "পেল" to "পেলো", "হল" to "হলো",
    "শুল" to "শুলো", "রইল" to "রইলো", "চাইল" to "চাইলো", "গাইল" to "গাইলো", "পাইল" to "পাইলো",
    "খাইল" to "খাইলো", "হইল" to "হইলো", "যাব" to "যাবো", "খাব" to "খাবো", "পাব" to "পাবো", "নেব" to "নেবো",
    "হব" to "হবো", "শোব" to "শোবো", "দিব" to "দিবো", "নিব" to "নিবো", "চাইব" to "চাইবো", "গাইব" to "গাইবো",
    "যাইব" to "যাইবো", "খাইব" to "খাইবো", "পাইব" to "পাইবো", "হইব" to "হইবো", "যেত" to "যেতো",
    "দিত" to "দিতো", "নিত" to "নিতো", "পেত" to "পেতো", "শুত" to "শুতো", "চাইত" to "চাইতো", "গাইত" to "গাইতো",
    "যাইত" to "যাইতো", "খাইত" to "খাইতো", "পাইত" to "পাইতো", "হইত" to "হইতো",
)

/** Words whose "এ" or "ে" is the wide "æ" ("অ্যা", "্যা"), with their end as said. */
private val WIDE_E = mapOf(
    "এক" to "অ্যাক্", "একটা" to "অ্যাকটা", "একবার" to "অ্যাকবার্", "একদিন" to "অ্যাকদিন্", "একজন" to "অ্যাকজন্",
    "একা" to "অ্যাকা", "একসাথে" to "অ্যাকসাথে", "একসঙ্গে" to "অ্যাকসঙ্গে", "এমন" to "অ্যামন্",
    "কেমন" to "ক্যামন্", "যেমন" to "য্যামন্", "তেমন" to "ত্যামন্", "এখন" to "অ্যাখন্", "এখনও" to "অ্যাখনও",
    "এখনো" to "অ্যাখনো", "এত" to "অ্যাতো", "এতো" to "অ্যাতো", "কেন" to "ক্যানো", "যেন" to "য্যানো",
    "হেন" to "হ্যানো", "দেখা" to "দ্যাখা", "দেখো" to "দ্যাখো", "দেখ" to "দ্যাখ", "দেখাল" to "দ্যাখালো",
    "দেখানো" to "দ্যাখানো", "খেলা" to "খ্যালা", "বেলা" to "ব্যালা", "ফেলা" to "ফ্যালা",
    "ঠেলা" to "ঠ্যালা", "গেল" to "গ্যালো", "গেছে" to "গ্যাছে", "দেওয়া" to "দ্যাওয়া", "নেওয়া" to "ন্যাওয়া",
)

/** Common words ending in ল, ব or ত, which the rules leave alone, whose end is silent. */
private val SILENT_ENDS = listOf(
    "সব", "খুব", "শব", "যব", "আজব", "গজব", "আরব", "আদব", "সাহেব", "নবাব", "জবাব", "কাবাব", "কিতাব", "হিসাব",
    "অভাব", "স্বভাব", "প্রভাব", "গরিব", "জীব", "শিব", "নীরব", "গৌরব", "উৎসব", "সম্ভব", "অসম্ভব", "অনুভব", "দানব",
    "রাত", "হাত", "ভাত", "সাত", "দাঁত", "ছাত", "জাত", "ডাকাত", "শীত", "গীত", "ভূত", "দূত", "প্রভাত",
    "আঘাত", "অতীত", "উচিত", "ইঙ্গিত", "সংগীত", "সঙ্গীত", "বিপরীত", "ভারত", "ভরত", "জগত", "মানত", "আদালত",
    "শরবত", "দৌলত", "ইজ্জত", "বরকত", "রহমত", "জল", "ফল", "দল", "কল", "তল", "ছল", "খল", "নল", "ফুল", "ভুল",
    "চুল", "মূল", "কূল", "নীল", "বিল", "ঝিল", "তিল", "কিল", "খাল", "ডাল", "চাল", "জাল", "তাল", "গাল", "মাল",
    "লাল", "পাল", "হাল", "ছাল", "শাল", "সকাল", "বিকাল", "বিকেল", "কপাল", "শিয়াল", "বিড়াল", "খেয়াল", "দেয়াল",
    "আড়াল", "পাতাল", "মশাল", "রাখাল", "কাঙাল", "পাগল", "ছাগল", "জঙ্গল", "মঙ্গল", "সকল", "কেবল", "মহল", "তেল",
    "বেল", "জেল", "গোল", "কোল", "দোল", "বোল", "ঘোল", "ঢোল", "পুল", "ফসল", "কাজল", "আঁচল", "শিকল", "পটল", "বাদল",
    "নকল", "দখল", "সরল", "তরল", "প্রবল", "সফল", "বিফল", "কুশল", "চঞ্চল", "উজ্জ্বল", "দুর্বল", "পিতল", "শীতল",
    "কোমল", "কমল",
).associateWith { "$it্" }

/** Bare stems: a "tui" order with a silent end, or an old-spelling "tumi" form with a spoken one. */
private val BARE_VERBS = setOf(
    "কর", "ধর", "পড়", "শোন", "শুন", "রাখ", "থাক", "বস", "ওঠ", "হাস", "লেখ", "শেখ", "ছাড়", "মার", "আন", "জান",
    "চেন", "বাঁচ", "বোঝ", "কাঁদ",
)

/**
 * Words with two common readings that end differently: আসল ("ashol", real; "ashlo", came), মানব ("manob", human;
 * "manbo", will accept), পরব ("porob", a festival; "porbo", will wear), বার ("bar", times; "baro", twelve), কোন
 * ("kon", which; "kono", any) and মত ("mot", an opinion; "moto", like), the last three in older spelling.
 */
private val TWO_READINGS = setOf("আসল", "মানব", "পরব", "বার", "কোন", "মত")

/** English loans whose end cluster Bengali words keep open ("ধর্ম" is "dhormo", "ফর্ম" is "form"). */
private val LOANS = setOf("ফর্ম", "ফার্ম", "প্ল্যাটফর্ম", "ইউনিফর্ম", "ব্যাঙ্ক")

private val CONSONANT_STEMS = setOf(
    "কর", "বল", "চল", "ধর", "মর", "পড়", "লড়", "নড়", "জ্বল", "গল", "টল", "দেখ", "শুন", "উঠ", "বস", "হাস", "কাঁদ",
    "লাগ", "থাক", "রাখ", "ভাব", "ডাক", "পার", "মার", "নাম", "খুঁজ", "খুল", "ফেল", "ছুট", "হাঁট", "ফির", "ঘুর",
    "চিন", "জান", "আন", "কাট", "বাঁধ", "মান", "টান", "ছাড়", "ভাঙ", "নাচ", "বাঁচ", "ঢুক", "জাগ", "বুঝ", "গড়",
    "চড়", "লিখ", "শিখ", "খেল", "ভুল", "দুল", "তুল", "মিল", "গুন", "পুড়", "উড়", "জিত", "হার", "ভর", "গিল",
    "চাপ", "কাঁপ", "থাম", "সাজ", "ছিঁড়", "মুছ", "নিভ", "বাড়", "ফুট", "পাক", "রাঁধ", "বাজ", "ডুব", "ভাস", "আস",
    "পৌঁছ", "ঝুল", "সার", "খস", "ঘষ", "মাখ", "ঢাল", "গাঁথ",
)

private val VOWEL_STEMS = setOf(
    "তাকা", "দাঁড়া", "পালা", "জানা", "শোনা", "ঘুমা", "বানা", "পাঠা", "চালা", "হারা", "ফুরা", "লুকা", "ছড়া",
    "সাজা", "নাচা", "হাসা", "কাঁদা", "চেঁচা", "জাগা", "ফেরা", "খাওয়া", "তাড়া", "কামড়া", "থামা", "নামা", "বসা",
    "চমকা", "হাঁপা", "মেলা", "পৌঁছা", "ঘোরা", "ওঠা", "শোয়া", "বোঝা", "শেখা", "দৌড়া", "লাফা", "ঝাঁপা", "কাটা",
    "পোড়া", "ভাসা", "ডোবা", "লাগা", "বাজা", "গড়া", "ছাড়া", "বাড়া", "ঝরা", "ঘুমো", "এগো", "পেরো", "ফুরো", "বেরো",
)
