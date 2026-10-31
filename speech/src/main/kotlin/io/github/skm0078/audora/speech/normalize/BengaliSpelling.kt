// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/normalize/BengaliSpelling.kt
// Purpose: Respells Bengali words the way they are said, for voices that read letters, not phonemes (SYSPIN, Rasa).

package io.github.skm0078.audora.speech.normalize

import java.text.Normalizer

/**
 * SYSPIN and Rasa (ADR-053, ADR-057) turn Bengali letters straight into sound, with no pronunciation rules of their
 * own. Bengali spelling leaves two things unwritten, and such a voice guesses them, often wrongly (ADR
 * 2026-10-30-bengali-spoken-spelling):
 * - whether a word's last consonant keeps its vowel: "সব" is "shob", yet the voice said "sobo"; "কাণ্ড" is "kando",
 *   yet it said "kand";
 * - whether "এ" is "e" or the wide "æ": "এমন" is "æmon", "গেল" is "gælo".
 *
 * Each Bengali word is respelled with the marks a careful writer uses: a hasanta for a silent end ("সব্"), "ো" for a
 * spoken one ("কাণ্ডো"), "্যা" for æ ("অ্যামন্"). In order:
 * 1. a word listed with its spoken form ([BengaliSpellingWords.said]), or listed to stay as written;
 * 2. a past, future or habitual form of a known verb is spoken to its end: করল is "korlo", বলছিল "bolchhilo";
 * 3. a cluster at the end keeps its vowel (গল্প is "golpo"), except the clusters English loans end in (লাস্ট, পার্ক)
 *    and য-ফলা (সত্য), which these voices already end well;
 * 4. one consonant at the end is silent (চোর is "chor") where Bengali is regular. A final ল, ব or ত ends both ways
 *    (জল is "jol", করল "korlo", কত "koto"), so those words are left to the lists; a final হ keeps its vowel (দেহ is
 *    "deho"), except after "আ" (আল্লাহ, শাহ).
 *
 * Anything else stays as written, so no word is read worse than before. Only marks are added (or a listed spelling
 * swapped in): words keep their order and number, and nothing but Bengali letters changes. Pure and thread-safe;
 * about a microsecond a word.
 */
object BengaliSpelling {
    fun respell(text: String): String =
        if (text.none(BengaliLetters::isBengali)) text else WORD.replace(text) { respellWord(it.value) }

    /** One word of Bengali letters and marks, respelled; the word itself when no rule applies. */
    internal fun respellWord(word: String): String {
        val key = Normalizer.normalize(word, Normalizer.Form.NFC)
        return BengaliSpellingWords.said[key] ?: (word + endingOf(key).mark)
    }

    /** How a word ends when said, and the mark that writes it so. */
    private enum class Ending(val mark: String) {
        SILENT(BengaliLetters.HASANTA.toString()),
        SPOKEN(BengaliLetters.O_SIGN.toString()),
        AS_WRITTEN(""),
    }

    /**
     * Rules 2 to 4. After a visarga the consonant doubles and keeps its vowel (দুঃখ is "dukkho"); after an anusvara
     * it keeps it too (অংশ, মাংস), except in English words ending in ংক (ব্যাংক). A final ছ is silent after "আ"
     * (গাছ, মাছ) and spoken in "tumi" verbs (করছ, করেছ, আছ).
     */
    private fun endingOf(word: String): Ending {
        val last = finalConsonant(word)
        val oneLetter = word.count(BengaliLetters::startsAkshara) < MIN_LETTERS
        if (last == null || oneLetter || word in BengaliSpellingWords.asWritten) return Ending.AS_WRITTEN
        val consonant = word[last]
        val before = word.getOrNull(last - 1)
        return when {
            isVerbForm(word) -> Ending.SPOKEN
            before == BengaliLetters.HASANTA -> clusterEnding(word, last)
            before == BengaliLetters.VISARGA -> Ending.SPOKEN
            before == BengaliLetters.ANUSVARA -> if (consonant == 'ক') Ending.AS_WRITTEN else Ending.SPOKEN
            consonant == 'ছ' -> if (before == BengaliLetters.AA_SIGN) Ending.SILENT else Ending.SPOKEN
            consonant == 'হ' -> afterHa(before)
            consonant in SILENT_ENDINGS -> Ending.SILENT
            else -> Ending.AS_WRITTEN
        }
    }

    /**
     * দেহ, স্নেহ, গ্রহ keep their vowel ("deho"). After "আ" the word may be Arabic or Persian (আল্লাহ, শাহ) as well
     * as Sanskrit (প্রবাহ), and after a vowel letter it is a cry ("ওহ"), so those stay as written.
     */
    private fun afterHa(before: Char?): Ending =
        if (before == null || before == BengaliLetters.AA_SIGN || BengaliLetters.isVowel(before)) {
            Ending.AS_WRITTEN
        } else {
            Ending.SPOKEN
        }

    /**
     * [last] ends a cluster: the consonant before it carries a hasanta. A য-ফলা (সত্য, রাজ্য, অন্য) and the clusters
     * English loans end in stay as written; any other cluster keeps its vowel.
     */
    private fun clusterEnding(word: String, last: Int): Ending {
        val leadAt = (last - 2).let { if (word.getOrNull(it) == BengaliLetters.NUKTA) it - 1 else it }
        val lead = word.getOrNull(leadAt)?.takeIf(BengaliLetters::isConsonant) ?: return Ending.AS_WRITTEN
        val end = word[last]
        val yaPhala = end == 'য' && word.last() != BengaliLetters.NUKTA
        val loan = "$lead${BengaliLetters.HASANTA}$end" in LOAN_CLUSTERS
        return if (yaPhala || loan) Ending.AS_WRITTEN else Ending.SPOKEN
    }

    /**
     * A past, future or habitual form of a known verb (করল, করব, করত, তাকাল, sadhu করিল), a past progressive
     * (করেছিল, বলছিল: "...chhilo") or a sadhu perfect (করিয়াছ: "koriyachho").
     */
    private fun isVerbForm(word: String): Boolean =
        (word.length > PAST_PROGRESSIVE.length && word.endsWith(PAST_PROGRESSIVE)) ||
            word.endsWith(SADHU_PERFECT) ||
            hasStem(word, CONSONANT_ENDINGS, BengaliSpellingWords.consonantStems) ||
            hasStem(word, VOWEL_ENDINGS, BengaliSpellingWords.vowelStems)

    private fun hasStem(word: String, endings: List<String>, stems: Set<String>): Boolean =
        endings.any { word.length > it.length && word.endsWith(it) && word.dropLast(it.length) in stems }

    /** Where the word's final consonant is, when it ends in one with no vowel sign or hasanta after it. */
    private fun finalConsonant(word: String): Int? {
        val end = if (word.lastOrNull() == BengaliLetters.NUKTA) word.length - 2 else word.length - 1
        return end.takeIf { it >= 0 && BengaliLetters.isConsonant(word[it]) }
    }

    /** A one-letter word is an initial or a list label ("ক)"), not a word to respell. */
    private const val MIN_LETTERS = 2

    /**
     * Consonants whose word-final vowel Bengali drops ("চোর", "দিন", "মাঠ", "পাড়"). Not ল, ব, ত (both ways), ছ and হ
     * (their own rules), য় (a glide), ঢ় (দৃঢ় is "drirho") and the rare ঞ.
     */
    private const val SILENT_ENDINGS = "কখগঘঙচজঝটঠডণথদধনপফভমরশষস"

    private const val PAST_PROGRESSIVE = "ছিল"
    private val SADHU_PERFECT = Normalizer.normalize("য়াছ", Normalizer.Form.NFC)

    /** Past, future and habitual endings after a consonant stem, then their sadhu forms. */
    private val CONSONANT_ENDINGS = listOf("ল", "ব", "ত", "িল", "িব", "িত")

    /** The same after a vowel stem; sadhu adds "ই" (তাকাইল). */
    private val VOWEL_ENDINGS = listOf("ল", "ব", "ত", "ইল", "ইব", "ইত")

    /**
     * End clusters of English and Persian loans, said without a vowel after them: লাস্ট, পয়েন্ট, সেকেন্ড, শার্ট,
     * কার্ড, বেল্ট, গোল্ড, লিফ্ট, কনসেপ্ট, প্রজেক্ট, বক্স, লাইসেন্স, কোর্স, পালস, ক্লিপস, টার্ন, ফিল্ম, পার্ক, চার্চ,
     * মর্দ, ক্যাম্প, মিল্ক, ডেস্ক, গ্যাস্প. Bengali's own words end in other clusters (কষ্ট, কাণ্ড, শর্ত, ধর্ম, গল্প).
     */
    private val LOAN_CLUSTERS = setOf(
        "স্ট", "ন্ট", "ন্ড", "র্ট", "র্ড", "ল্ট", "ল্ড", "ফ্ট", "প্ট", "ক্ট", "ক্স", "ন্স",
        "র্স", "ল্স", "প্স", "র্ন", "ল্ম", "র্ক", "র্চ", "র্দ", "ম্প", "ল্ক", "স্ক", "স্প",
    )

    /** A run of Bengali letters and marks, joiners included; digits, dandas and Latin letters are not part of it. */
    private val WORD = Regex("""[\u0981-\u09E3\u200C\u200D]+""")
}

/** The Bengali block's letters and marks, as the rules tell them apart. */
private object BengaliLetters {
    const val HASANTA = '\u09CD'
    const val O_SIGN = '\u09CB' // ো
    const val AA_SIGN = '\u09BE' // া
    const val NUKTA = '\u09BC'
    const val VISARGA = '\u0983' // ঃ
    const val ANUSVARA = '\u0982' // ং

    /** ড়, ঢ় and য় as single letters, in text that was never NFC. */
    private const val EXTRA_CONSONANTS = "\u09DC\u09DD\u09DF"

    fun isConsonant(c: Char): Boolean = c in '\u0995'..'\u09B9' || c in EXTRA_CONSONANTS // ক to হ

    fun isVowel(c: Char): Boolean = c in '\u0985'..'\u0994' || c in '\u09E0'..'\u09E1' // অ to ঔ, ৠ, ৡ

    fun isBengali(c: Char): Boolean = c in '\u0981'..'\u09E3' // candrabindu to the last vowel sign

    /** A consonant or an independent vowel: each starts one letter as a reader counts them. */
    fun startsAkshara(c: Char): Boolean = isConsonant(c) || isVowel(c)
}
