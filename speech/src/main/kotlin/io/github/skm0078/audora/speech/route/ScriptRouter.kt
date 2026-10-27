// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/route/ScriptRouter.kt
// Purpose: Cuts one sentence into script runs so each part is read by its own language's voice (P3-01, ADR-026), the
//          phone's own voice for languages beyond English, Hindi and Bengali (ADR 2026-10-26).

package io.github.skm0078.audora.speech.route

import io.github.skm0078.audora.core.config.ScriptRoutingConfig
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.PhoneLanguages
import io.github.skm0078.audora.core.model.ScriptLang
import java.lang.Character.UnicodeScript

/**
 * One part of a sentence and who reads it. [text] is trimmed and never blank. With [phone] set (a BCP 47 language
 * tag, "fr"), the phone's own voice for that language reads it as written; [lang] is then the voice that reads the
 * whole sentence instead when the phone has no such voice.
 */
data class ScriptSegment(val text: String, val lang: Lang, val phone: String? = null)

/**
 * Rules (Build Spec §3, ADR-005, ADR-026, ADR 2026-10-26):
 * - Each letter's script picks its language ([ScriptLang]); spaces, punctuation and ASCII digits go with the run
 *   before them, so "world, " keeps its comma. A cut falls after the last space before the new script, so an
 *   opening quote or bracket travels with the words it opens.
 * - In a mostly Hindi or Bengali sentence, an English run shorter than
 *   [ScriptRoutingConfig.minEnglishWordsInIndic] words stays with the Indic voice next to it.
 * - Indic text inside an English sentence always gets its own voice, however short.
 * - The book's language, when it is none of the three ([PhoneLanguages.phoneLanguage]), reads the letters of its
 *   script with the phone's voice: a French book's Latin letters in French, a Marathi book's Devanagari in Marathi.
 * - Letters of a script none of the three is written in (Tamil, Arabic, Japanese) are read by the phone's voice for
 *   that script's language ([PhoneLanguages.defaultFor]); a run of fewer than [ScriptRoutingConfig.minOtherScriptLetters]
 *   of them ("Ж" in a list of symbols) stays with the voice before it, as before. Greek letters do so in any book
 *   not in Greek: they are maths there.
 * - Text with no letter at all is one segment in [route]'s fallback language, read in the book's language when that
 *   is a phone language written in the fallback's script (or the fallback is English, the pipeline's default).
 *
 * Pure and deterministic; segments come back in reading order, adjacent ones read by different voices.
 */
class ScriptRouter(private val config: ScriptRoutingConfig = ScriptRoutingConfig()) {
    /** [bookLanguage]: the book's language tag as the library holds it; null or one of the three changes nothing. */
    fun route(text: String, fallback: Lang, bookLanguage: String? = null): List<ScriptSegment> {
        val voicing = Voicing(text, fallback, bookLanguage)
        val runs = scriptRuns(text, voicing)
        if (runs.isEmpty()) {
            return if (text.isBlank()) emptyList() else listOf(ScriptSegment(text.trim(), fallback, voicing.letterless))
        }
        val voiced = absorbShortOtherScripts(keepShortEnglishWithIndic(text, runs))
        return merge(voiced).map { ScriptSegment(text.substring(it.start, it.end).trim(), it.key.lang, it.key.phone) }
    }

    /**
     * Who reads a letter: [lang]'s voice, or the phone's voice for [phone]. [tuned]: the letter is Latin, Devanagari
     * or Bengali. [byDefault]: [phone] comes from the letter's script, not from the book.
     */
    private data class Key(val lang: Lang, val phone: String?, val tuned: Boolean, val byDefault: Boolean)

    private class Voicing(text: String, private val fallback: Lang, bookLanguage: String?) {
        private val book = PhoneLanguages.phoneLanguage(bookLanguage)
        private val bookScript = book?.let(PhoneLanguages::scriptOf)
        private val kana = text.codePoints().anyMatch { PhoneLanguages.isKana(UnicodeScript.of(it)) }

        /** The phone language a sentence without letters is read in, if any. */
        val letterless: String? = book?.takeIf { fallback == Lang.EN || bookScript == scriptOf(fallback) }

        /** Null for script-neutral characters: spaces, punctuation, ASCII digits, symbols, unread scripts. */
        fun of(codePoint: Int): Key? {
            val script = PhoneLanguages.unified(UnicodeScript.of(codePoint))
            val lang = ScriptLang.of(codePoint)
            val phone = PhoneLanguages.defaultFor(script)
            return when {
                book != null && script == bookScript -> Key(lang ?: fallback, book, lang != null, byDefault = false)
                lang != null -> Key(lang, null, tuned = true, byDefault = false)
                phone == null -> null
                script == UnicodeScript.HAN && kana -> Key(fallback, PhoneLanguages.CJK_WITH_KANA, false, true)
                else -> Key(fallback, phone, tuned = false, byDefault = true)
            }
        }

        private fun scriptOf(lang: Lang): UnicodeScript = when (lang) {
            Lang.EN -> UnicodeScript.LATIN
            Lang.HI -> UnicodeScript.DEVANAGARI
            Lang.BN -> UnicodeScript.BENGALI
        }
    }

    private class Run(val start: Int, var end: Int, var key: Key, var letters: Int) {
        val isEnglish: Boolean get() = key.tuned && key.lang == Lang.EN && key.phone == null
        val isIndic: Boolean get() = key.tuned && key.lang != Lang.EN
    }

    /** Maximal runs of one voice; the first starts at 0 and the last ends at the text's end. */
    private fun scriptRuns(text: String, voicing: Voicing): List<Run> {
        val runs = ArrayList<Run>()
        var lastLetterEnd = 0
        var lastSpaceEnd = 0
        var i = 0
        while (i < text.length) {
            val cp = Character.codePointAt(text, i)
            val next = i + Character.charCount(cp)
            val key = voicing.of(cp)
            val current = runs.lastOrNull()
            when {
                key == null -> if (Character.isWhitespace(cp)) lastSpaceEnd = next

                current == null -> runs.add(Run(0, text.length, key, 0))

                current.key != key -> {
                    val cut = if (lastSpaceEnd > lastLetterEnd) lastSpaceEnd else i
                    current.end = cut
                    runs.add(Run(cut, text.length, key, 0))
                }
            }
            if (key != null) {
                runs.last().letters++
                lastLetterEnd = next
            }
            i = next
        }
        return runs
    }

    private fun keepShortEnglishWithIndic(text: String, runs: List<Run>): List<Run> {
        val indicLetters = runs.filter { it.isIndic }.sumOf { it.letters }
        val englishLetters = runs.filter { it.isEnglish }.sumOf { it.letters }
        // Same tie rule as the unit's own language (ScriptDetector): a tie counts as Indic.
        if (indicLetters == 0 || englishLetters > indicLetters) return runs
        runs.forEachIndexed { i, run ->
            if (run.isEnglish && words(text, run) < config.minEnglishWordsInIndic) {
                val neighbour = runs.getOrNull(i - 1)?.takeIf { it.isIndic } ?: runs.getOrNull(i + 1)
                if (neighbour != null && neighbour.isIndic) run.key = neighbour.key
            }
        }
        return runs
    }

    /** A few letters of another script inside a sentence ("α", "Ж") are read by the voice before them, or after. */
    private fun absorbShortOtherScripts(runs: List<Run>): List<Run> {
        if (runs.size < 2) return runs
        runs.forEachIndexed { i, run ->
            if (run.key.byDefault && run.letters < config.minOtherScriptLetters) {
                val neighbour = runs.getOrNull(i - 1) ?: runs.getOrNull(i + 1)
                if (neighbour != null) run.key = neighbour.key
            }
        }
        return runs
    }

    /** Whitespace-separated tokens holding a letter of [run]'s script: "U.N." is one word, "12" none. */
    private fun words(text: String, run: Run): Int = text.substring(run.start, run.end)
        .split(WHITESPACE)
        .count { token -> token.codePoints().anyMatch { ScriptLang.of(it) == run.key.lang } }

    private fun merge(runs: List<Run>): List<Run> {
        val merged = ArrayList<Run>()
        for (run in runs) {
            val last = merged.lastOrNull()
            if (last != null && last.key.lang == run.key.lang && last.key.phone == run.key.phone) {
                last.end = run.end
                last.letters += run.letters
            } else {
                merged.add(Run(run.start, run.end, run.key, run.letters))
            }
        }
        return merged
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}
