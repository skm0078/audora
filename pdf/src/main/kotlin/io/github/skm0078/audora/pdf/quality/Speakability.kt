// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/quality/Speakability.kt
// Purpose: Scores how likely a sentence is real language a voice can read (RI-2a, ADR-035), from five signals.

package io.github.skm0078.audora.pdf.quality

import io.github.skm0078.audora.core.config.Signal
import io.github.skm0078.audora.core.config.SpeakabilityConfig
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ScriptLang
import java.util.Locale

/**
 * The raw signals behind a score, each 0–1; null when the signal abstains.
 *
 * @property ocrConfidence the OCR engine's word confidence (OCR pages only).
 * @property dictionary the share of words found in their language's word list, weighted by length.
 * @property letterModel the letter-trigram score of the words whose language has a model, weighted by length.
 * @property symbolSoup the share of characters in tokens that look like words or numbers ([CleanTokens]).
 * @property scriptMix the share of words written in one script; null without a word of two or more letters.
 */
data class SpeakabilitySignals(
    val ocrConfidence: Float?,
    val dictionary: Float?,
    val letterModel: Float?,
    val symbolSoup: Float,
    val scriptMix: Float?,
)

/**
 * `ReadingUnit.speakScore` (ADR-035): a weighted blend of the signals in [SpeakabilitySignals], each mapped by its
 * `SignalRange`. It reads only the sentence and its OCR confidence, never a setting, so the same page always scores
 * the same. Words are read by [Words]; one-letter words are left out of the word signals ("a", "x", "व" say little).
 */
class SpeakabilityScorer(
    private val config: SpeakabilityConfig = SpeakabilityConfig(),
    private val models: WordModels = BundledWordModels,
) {
    /** 0–1, higher is more speakable. [ocrConfidence]: the OCR engine's confidence for these words, 0–1. */
    fun score(text: String, ocrConfidence: Float? = null): Float = blend(signals(text, ocrConfidence))

    fun blend(signals: SpeakabilitySignals): Float {
        var sum = 0f
        var weights = 0f
        fun add(signal: Signal, raw: Float?) {
            if (raw == null || signal.weight == 0f) return
            sum += signal.weight * signal.range.map(raw)
            weights += signal.weight
        }
        add(config.ocrConfidence, signals.ocrConfidence)
        add(config.dictionary, signals.dictionary)
        add(config.letterModel, signals.letterModel)
        add(config.symbolSoup, signals.symbolSoup)
        add(config.scriptMix, signals.scriptMix)
        // Symbol soup never abstains, so weights is 0 only when every present signal is switched off.
        return if (weights == 0f) 1f else sum / weights
    }

    fun signals(text: String, ocrConfidence: Float? = null): SpeakabilitySignals {
        val words = Words.of(text).filter { it.codePointCount(0, it.length) >= MIN_WORD_LETTERS }
        // Word signals are weighted by length: "a", "at" and "or" turn up in any noise; a long known word does not.
        var known = 0
        var counted = 0
        var letters = 0f
        var modelled = 0
        var oneScript = 0
        for (word in words) {
            val length = word.codePointCount(0, word.length)
            val script = Words.scriptOf(word)
            if (script != null) oneScript++
            val lang = if (script == null) null else Words.langOf(word)
            val model = lang?.let(models::of)
            if (model != null) {
                modelled += length
                letters += length * model.letterScore(word)
                if (word in model) known += length
            }
            // A word in a script without a list (Greek, Gurmukhi) or mixing scripts counts as unknown.
            if (model != null || lang == null) counted += length
        }
        return SpeakabilitySignals(
            // List numbers ("2.", "14") often read with a low confidence; a short unit without words skips it.
            ocrConfidence = ocrConfidence.takeIf { words.isNotEmpty() || visibleChars(text) > config.shortUnitChars },
            dictionary = if (modelled > 0 &&
                words.size >= config.minDictionaryWords
            ) {
                known.toFloat() / counted
            } else {
                null
            },
            letterModel = if (modelled > 0) letters / modelled else null,
            symbolSoup = CleanTokens.share(text),
            scriptMix = if (words.isEmpty()) null else oneScript.toFloat() / words.size,
        )
    }

    private fun visibleChars(text: String): Int =
        text.codePoints().filter { !Character.isWhitespace(it) && !Character.isSpaceChar(it) }.count().toInt()

    private companion object {
        const val MIN_WORD_LETTERS = 2
    }
}

/**
 * Words as the word lists hold them (scripts/speakability/build_models.py): runs of letters and combining marks, NFC
 * as the pipeline gives text, zero-width joiners dropped, lower-cased; a Latin word keeps an inner apostrophe
 * ("don't", "o’clock" → "o'clock"). Digits, punctuation and symbols end a word.
 */
object Words {
    private const val ZWNJ = 0x200C
    private const val ZWJ = 0x200D
    private const val APOSTROPHE = '\''.code
    private const val RIGHT_QUOTE = 0x2019

    fun of(text: String): List<String> {
        val words = ArrayList<String>()
        val word = StringBuilder()
        fun flush() {
            if (word.isNotEmpty()) words.add(word.toString().lowercase(Locale.ROOT))
            word.setLength(0)
        }
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val next = i + Character.charCount(cp)
            when {
                isWordChar(cp) -> word.appendCodePoint(cp)
                cp == ZWNJ || cp == ZWJ -> Unit
                isInnerApostrophe(text, cp, word, next) -> word.append('\'')
                else -> flush()
            }
            i = next
        }
        flush()
        return words
    }

    /** The one script [word]'s letters are written in (marks follow their letter), or null when it mixes scripts. */
    fun scriptOf(word: String): Character.UnicodeScript? {
        var found: Character.UnicodeScript? = null
        var i = 0
        while (i < word.length) {
            val cp = word.codePointAt(i)
            i += Character.charCount(cp)
            val script = Character.UnicodeScript.of(cp)
            if (script == Character.UnicodeScript.COMMON || script == Character.UnicodeScript.INHERITED) continue
            if (found != null && found != script) return null
            found = script
        }
        return found
    }

    /** The language of [word]'s first letter's script (ScriptLang, ADR-005); null for scripts Audora does not read. */
    fun langOf(word: String): Lang? {
        val first = word.codePoints().filter(Character::isLetter).findFirst()
        return if (first.isPresent) ScriptLang.of(first.asInt) else null
    }

    internal fun isWordChar(cp: Int): Boolean = Character.isLetter(cp) || when (Character.getType(cp)) {
        Character.NON_SPACING_MARK.toInt(),
        Character.COMBINING_SPACING_MARK.toInt(),
        Character.ENCLOSING_MARK.toInt(),
        -> true

        else -> false
    }

    private fun isInnerApostrophe(text: String, cp: Int, word: StringBuilder, next: Int): Boolean {
        val candidate = (cp == APOSTROPHE || cp == RIGHT_QUOTE) && word.isNotEmpty() && next < text.length
        if (!candidate) return false
        val before = word.codePointBefore(word.length)
        val after = text.codePointAt(next)
        return ScriptLang.of(before) == Lang.EN && Character.isLetter(after) && ScriptLang.of(after) == Lang.EN
    }
}
