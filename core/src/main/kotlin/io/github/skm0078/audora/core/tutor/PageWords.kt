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
    // Letters, marks and digits, with apostrophes inside ("don't"); Hindi and Bengali words are letters plus marks.
    private val WORD = Regex("""[\p{L}\p{M}\p{N}]+(?:['’][\p{L}\p{M}\p{N}]+)*""")

    // Joiners and the nukta change how a word is drawn, rarely how it is heard; recognisers write them unevenly.
    private val DROPPED = setOf('‌', '‍', '़', '়', '\'', '’')

    // Candrabindu and anusvara are heard alike and written either way.
    private val SAME_SOUND = mapOf('ँ' to 'ं', 'ঁ' to 'ং')

    /** The words of [sentences] in reading order; [lang] is the language the tutor listens for. */
    fun of(sentences: List<String>, lang: Lang): List<PageWord> = sentences.flatMapIndexed { unit, text ->
        WORD.findAll(text).map { match ->
            val word = match.value
            val checked = word.none(Char::isDigit) && DictionaryLanguage.of(word) == lang
            PageWord(unit, match.range.first, match.range.last + 1, key(word), checked)
        }.filter { it.key.isNotEmpty() }.toList()
    }

    /** What a recogniser heard, as keys in order. */
    fun heard(text: String): List<String> = WORD.findAll(text).map { key(it.value) }.filter { it.isNotEmpty() }.toList()

    /** A word's comparable form: NFKC, lower case, without joiners or nukta, candrabindu as anusvara. */
    fun key(word: String): String {
        val normal = Normalizer.normalize(word, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
        return buildString(normal.length) {
            normal.forEach { char -> if (char !in DROPPED) append(SAME_SOUND[char] ?: char) }
        }
    }
}
