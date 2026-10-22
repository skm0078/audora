// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/ocr/OcrRepair.kt
// Purpose: Corrects OCR near-misses against the bundled word lists and rejoins words OCR split in two (RI-3).

package io.github.skm0078.audora.pdf.ocr

import io.github.skm0078.audora.core.config.OcrRepairConfig
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.pdf.quality.LanguageModel
import io.github.skm0078.audora.pdf.quality.WordModels
import io.github.skm0078.audora.pdf.render.ViewRect

/**
 * Runs on the words Tesseract read, before they become page text (so offsets and highlight boxes follow the repaired
 * words). Each word is checked against the word list of its script (Latin English, Devanagari Hindi, Bengali):
 *
 * - **Rejoin.** Two neighbouring words that are not known but are one known word together ("govern ment") become
 *   that word.
 * - **Correct.** An unknown word Tesseract was unsure of (`OcrRepairConfig.maxConfidence`) becomes the one known,
 *   common word one known OCR confusion away ([OcrConfusions]: letters of near-identical shape) or, in English, also
 *   a single edit away (delete, insert, replace or swap a character); confusions cost less, so they win ties.
 *   Ambiguous words (two candidates about as common), short words, compounds and words with digits are kept. Capitals
 *   are kept as printed.
 * - **Signs read as punctuation.** A colon read for a visarga, and in Bengali "!", "|" or "৷" read for the vowel sign
 *   aa, when the word with the sign is known and the word without it is not.
 * - **A bar running on.** In Bengali, a bar glued to a word that runs on to the next word is the vowel sign aa
 *   ([OcrAaBars]), short and known words included.
 *
 * Punctuation around a word is kept. A language without a word list is left as read.
 */
internal class OcrRepair(private val config: OcrRepairConfig, private val models: WordModels) {
    private val alphabets = HashMap<Lang, CharArray>()
    private val aaBars = OcrAaBars(config, models)

    fun repair(ocr: OcrText): OcrText {
        if (!config.enabled) return ocr
        val lines = ocr.lines.map { line -> OcrLine(repairLine(line.words), line.paragraph, line.size) }
        return OcrText(lines, ocr.quarterTurns, ocr.rules)
    }

    private fun repairLine(words: List<OcrWord>): List<OcrWord> {
        val out = ArrayList<OcrWord>(words.size)
        var i = 0
        while (i < words.size) {
            val joined = words.getOrNull(i + 1)?.let { rejoined(words[i], it) }
            if (joined != null) {
                out.add(joined)
                i += 2
            } else {
                out.add(corrected(words[i], next = words.getOrNull(i + 1)))
                i++
            }
        }
        return out
    }

    private fun rejoined(first: OcrWord, second: OcrWord): OcrWord? {
        val a = Token.of(first.text)
        val b = Token.of(second.text)
        val touching = a.suffix.isEmpty() && b.prefix.isEmpty()
        val filled = a.core.isNotEmpty() && b.core.isNotEmpty()
        val model = scriptLang(a.core)?.let(models::of)?.takeIf {
            touching && filled &&
                it === scriptLang(b.core)?.let(models::of)
        }
        val word = a.core + b.core
        val joins = model?.let {
            !isKnown(a.core, it) && !isKnown(b.core, it) && isCommon(frequency(word, it))
        } == true
        return if (joins) {
            OcrWord(
                a.prefix + word + b.suffix,
                union(first.box, second.box),
                minConfidence(first, second),
            )
        } else {
            null
        }
    }

    private fun corrected(word: OcrWord, next: OcrWord?): OcrWord {
        val token = Token.of(word.text)
        val fixed = aaBars.runningOn(word.text, word.confidence, next?.text)
            ?: if (mayBeMisread(word, token, isLineEnd = next == null)) fixedText(token) else null
        return if (fixed == null) word else OcrWord(fixed, word.box, word.confidence)
    }

    /** Whether [word] is worth checking: Tesseract was unsure of it, and it is one whole plain word. */
    private fun mayBeMisread(word: OcrWord, token: Token, isLineEnd: Boolean): Boolean {
        val core = token.core
        // No confidence (plain text, an engine without one): nothing says the word was misread.
        val unsure = word.confidence?.let { it < config.maxConfidence } == true
        // A hyphen at the line's end is a word broken across lines: page text joins it with the next line.
        val broken = isLineEnd && token.suffix.endsWith('-')
        val compound = core.any { !isWordChar(it) } // "भेद-भाव", "co-operation": parts of a compound
        val plain = !compound && core.length >= config.minLetters && core.none(Char::isDigit)
        return unsure && !broken && plain
    }

    /** The word as printed, or null when it is known, its language has no word list, or nothing fits. */
    private fun fixedText(token: Token): String? {
        val lang = scriptLang(token.core)
        val model = lang?.let(models::of)
        if (lang == null || model == null || isKnown(token.core, model)) return null
        return misreadSign(token, lang, model)
            ?: bestCandidate(token.core, lang, model)?.let { token.prefix + withCaseOf(token.core, it) + token.suffix }
    }

    /**
     * A sign Tesseract read as punctuation after an unknown Indic word, when the word with the sign is known: a colon
     * for the visarga ("विशेषत:" → "विशेषतः"), and in Bengali "!" or "৷" for the vowel sign aa ("নিরাপত্ত!" →
     * "নিরাপত্তা"), which is drawn as the same bar as a danda.
     */
    private fun misreadSign(token: Token, lang: Lang, model: LanguageModel): String? {
        val word = token.suffix.firstOrNull()?.let { OcrConfusions.signFor(lang, it) }?.let { token.core + it }
        return word?.takeIf { isCommon(model.words[it]) }?.let { token.prefix + it + token.suffix.substring(1) }
    }

    private fun isCommon(zipf: Int?): Boolean = zipf != null && zipf >= config.minZipf

    /** The one common known word nearest [core], or null when none is close or two are about as likely. */
    private fun bestCandidate(core: String, lang: Lang, model: LanguageModel): String? {
        val lookup = lookupForm(core)
        val costs = HashMap<String, Float>()
        fun offer(candidate: String, cost: Float) {
            if (candidate != lookup && candidate in model) costs.merge(candidate, cost, ::minOf)
        }
        for ((from, to) in OcrConfusions.of(lang)) {
            var at = lookup.indexOf(from)
            while (at >= 0) {
                offer(lookup.substring(0, at) + to + lookup.substring(at + from.length), config.confusionCost)
                at = lookup.indexOf(from, at + 1)
            }
        }
        // Hindi and Bengali change only through the confusions: the word lists lack many real inflected words, and
        // on the P3-05 scans a free edit turned more of those into other words ("अकृत" → "अकूत") than it fixed.
        if (lang == Lang.EN) singleEdits(lookup, alphabetOf(lang, model)) { offer(it, 1f) }
        val ranked = costs.entries
            .filter { (model.words[it.key] ?: 0) >= config.minZipf }
            .sortedWith(compareBy<Map.Entry<String, Float>> { it.value }.thenByDescending { model.words[it.key] ?: 0 })
        val first = ranked.firstOrNull() ?: return null
        val second = ranked.getOrNull(1)
        val tied = second != null && second.value == first.value &&
            (model.words.getValue(first.key) - model.words.getValue(second.key)) < config.zipfMargin
        return if (tied) null else first.key
    }

    private fun alphabetOf(lang: Lang, model: LanguageModel): CharArray = alphabets.getOrPut(lang) {
        model.words.keys.flatMap { it.asIterable() }.toSortedSet().toCharArray()
    }

    /** A word split into the punctuation before it, its letters and the punctuation after it. */
    private class Token(val prefix: String, val core: String, val suffix: String) {
        companion object {
            fun of(text: String): Token {
                val start = text.indexOfFirst(::isWordChar).takeIf { it >= 0 } ?: return Token(text, "", "")
                val end = text.indexOfLast(::isWordChar) + 1
                return Token(text.substring(0, start), text.substring(start, end), text.substring(end))
            }
        }
    }

    private companion object {
        /** Every word one edit from [word]: a character deleted, swapped with the next, replaced or inserted. */
        fun singleEdits(word: String, alphabet: CharArray, emit: (String) -> Unit) {
            for (i in word.indices) {
                emit(word.removeRange(i, i + 1))
                if (i + 1 < word.length) {
                    emit(word.substring(0, i) + word[i + 1] + word[i] + word.substring(i + 2))
                }
                for (c in alphabet) if (c != word[i]) emit(word.substring(0, i) + c + word.substring(i + 1))
            }
            for (i in 0..word.length) for (c in alphabet) emit(word.substring(0, i) + c + word.substring(i))
        }

        fun isKnown(core: String, model: LanguageModel) = core in model || lookupForm(core) in model

        fun frequency(word: String, model: LanguageModel): Int? = model.words[word] ?: model.words[lookupForm(word)]

        fun union(a: ViewRect?, b: ViewRect?): ViewRect? = if (a == null || b == null) {
            a ?: b
        } else {
            ViewRect(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom))
        }

        fun minConfidence(a: OcrWord, b: OcrWord): Int? = listOfNotNull(a.confidence, b.confidence).minOrNull()

        /** Letters, digits, combining marks, and the joiners Bengali and Hindi spell some conjuncts with. */
        fun isWordChar(c: Char): Boolean = Character.isLetterOrDigit(c) || isSign(c) || c == ZWJ || c == ZWNJ

        /** A vowel sign, virama, nukta, anusvara, candrabindu or visarga: the marks written on a letter. */
        fun isSign(c: Char): Boolean = Character.getType(c).let {
            it == Character.NON_SPACING_MARK.toInt() || it == Character.COMBINING_SPACING_MARK.toInt()
        }

        const val ZWJ = '\u200D'
        const val ZWNJ = '\u200C'

        /** The language whose word list a word is checked against, from the script of its first letter. */
        fun scriptLang(core: String): Lang? =
            when (core.firstOrNull { Character.isLetter(it) }?.let { Character.UnicodeScript.of(it.code) }) {
                Character.UnicodeScript.LATIN -> Lang.EN
                Character.UnicodeScript.DEVANAGARI -> Lang.HI
                Character.UnicodeScript.BENGALI -> Lang.BN
                else -> null
            }

        /** The word lists are lower case: "The" is looked up as "the". */
        fun lookupForm(core: String): String = core.lowercase()

        /** [corrected] with [original]'s capitals: a capital first letter, or all capitals. */
        fun withCaseOf(original: String, corrected: String): String = when {
            original.length > 1 && original.all { !it.isLetter() || it.isUpperCase() } -> corrected.uppercase()
            original.first().isUpperCase() -> corrected.replaceFirstChar { it.uppercaseChar() }
            else -> corrected
        }
    }
}
