// File: core/src/main/kotlin/io/github/skm0078/audora/core/tutor/PageWords.kt
// Purpose: Splits a page's sentences into the words the reading tutor follows, with keys that heard words match.

package io.github.skm0078.audora.core.tutor

import io.github.skm0078.audora.core.dictionary.DictionaryLanguage
import io.github.skm0078.audora.core.model.Lang
import java.text.Normalizer
import java.util.Locale

/**
 * One word on the page: [unit] is its sentence's index and [start] until [end] its characters in that sentence.
 * [checked] is false for a word the tutor cannot judge (a number, a symbol, or a word in another script than the
 * tutor's language): it may be read any way, or left out, without counting against the reader.
 */
data class PageWord(val unit: Int, val start: Int, val end: Int, val key: String, val checked: Boolean)

object PageWords {
    // Letters, marks and digits, held together by apostrophes ("don't"), joiners (র‍্যাব, क्‍ष) and soft hyphens
    // (an EPUB's "read­ing"); Hindi and Bengali words are letters plus marks.
    private const val WORD = """[\p{L}\p{M}\p{N}]+(?:[\u200C\u200D\u00AD'’][\p{L}\p{M}\p{N}]+)*"""

    // Abbreviations read as words: dotted letters ("p.m.", "e.g.") and a fixed list of titles ("Mr. Smith", "Dr."),
    // never judged. A list, not a pattern: a sentence-final "Tom." or "Dad." is a word to judge.
    private const val ABBREVIATION = """(?:\p{L}\.){2,}|\b(?:Mrs|Mr|Ms|Dr|St|Jr|Sr|Mt)\."""

    private val TOKEN = Regex("$ABBREVIATION|$WORD")
    private val HEARD = Regex(WORD)
    private val LETTER_WORD = Regex("""\p{L}[\p{L}\p{M}]*""")

    // Joiners, soft hyphens and the nukta change how a word is drawn, rarely how it is heard; recognisers write them
    // unevenly. Dropping the nukta also merges Bengali য়/য, ড়/ড and ঢ়/ঢ, which a recogniser rarely tells apart.
    private val DROPPED = setOf('\u200C', '\u200D', '\u00AD', '़', '়', '\'', '’', '.')

    // Candrabindu and anusvara are heard alike and written either way; long and short i and u are written
    // interchangeably (कि/की, কী/কি) and recognisers pick either.
    private val SAME_SOUND = mapOf(
        'ँ' to 'ं',
        'ঁ' to 'ং',
        'ी' to 'ि',
        'ू' to 'ु',
        'ী' to 'ি',
        'ূ' to 'ু',
    )

    // A nasal consonant with virama before another consonant is written as anusvara too (हिन्दी/हिंदी, गङ्गा/गंगा).
    private val NASAL_CLUSTER = Regex("""[ङञणनम]्(?=[क-ह])""")
    private val BENGALI_NASAL_CLUSTER = Regex("""[ঙঞণনম]্(?=[ক-হ])""")

    // ये and यी after a letter are spelled ए and ई as often (गये/गए, लिये/लिए, नयी/नई).
    private val Y_VOWEL = Regex("""(?<=[\u0900-\u0963])(?<!्)य([ेी])""")

    // Bengali khanda-ta written as ta, hasanta and joiner (উত্‍সব), or as ta and hasanta at a word's end (হঠাত্).
    private const val TA_JOINED = "ত্\u200D"
    private const val TA_HASANTA = "ত্"
    private const val KHANDA_TA = "ৎ"

    /** The words of [sentences] in reading order; [lang] is the language the tutor listens for. */
    fun of(sentences: List<String>, lang: Lang): List<PageWord> = sentences.flatMapIndexed { unit, text ->
        TOKEN.findAll(text).map { match ->
            val word = match.value
            val checked = word.none { it.isDigit() || it == '.' } && DictionaryLanguage.of(word) == lang
            PageWord(unit, match.range.first, match.range.last + 1, key(word), checked)
        }.filter { it.key.isNotEmpty() }.toList()
    }

    /**
     * Whether at least half of [text]'s words are in [lang]'s script. Dolphin is not told the language, so Hindi can
     * come back in Urdu script; such a stretch is dropped rather than marking every word to check.
     */
    fun mostlyIn(text: String, lang: Lang): Boolean {
        val words = LETTER_WORD.findAll(text).map { it.value }.toList()
        return words.isEmpty() || words.count { DictionaryLanguage.of(it) == lang } * 2 >= words.size
    }

    /** What a recogniser heard, as keys in order. */
    fun heard(text: String): List<String> =
        HEARD.findAll(text).map { key(it.value) }.filter { it.isNotEmpty() }.toList()

    /**
     * A word's comparable form: NFKC, lower case, without joiners, soft hyphens or nukta; candrabindu as anusvara,
     * long i and u as short, a nasal cluster as anusvara, ये/यी after a letter as ए/ई, khanda-ta as ৎ.
     */
    fun key(word: String): String {
        val normal = Normalizer.normalize(word, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
            .replace(TA_JOINED, KHANDA_TA)
            .let { if (it.endsWith(TA_HASANTA)) it.dropLast(TA_HASANTA.length) + KHANDA_TA else it }
            .replace(NASAL_CLUSTER, "ं")
            .replace(BENGALI_NASAL_CLUSTER, "ং")
            .replace(Y_VOWEL) { if (it.groupValues[1] == "े") "ए" else "ई" }
        return buildString(normal.length) {
            normal.forEach { char -> if (char !in DROPPED) append(SAME_SOUND[char] ?: char) }
        }
    }
}
