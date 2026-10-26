// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/reading/SentenceLanguage.kt
// Purpose: The phone language one sentence of a book is read in (ADR 2026-10-26), and whether its speakability score
//          can be trusted, from the book's language and the sentence's own words.

package io.github.skm0078.audora.playback.reading

import io.github.skm0078.audora.core.config.PhoneLanguageConfig
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.PhoneLanguages
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.pdf.quality.BundledWordModels
import io.github.skm0078.audora.pdf.quality.LanguageModel
import io.github.skm0078.audora.pdf.quality.Words
import java.lang.Character.UnicodeScript

/**
 * A book's language (what its file says, or the listener's choice in Edit details) that is none of English, Hindi
 * and Bengali is a phone language: the phone's voice reads the letters of its script (`:speech`'s ScriptRouter). A
 * Latin sentence that reads as English ([PhoneLanguageConfig]) keeps the English voice, so an English abstract in a
 * French paper, or a file that names the wrong language, is still read in English.
 *
 * [english]: the English word list `:pdf` scores speakability with; null leaves every sentence to the book's language.
 */
class SentenceLanguage(
    private val config: PhoneLanguageConfig = PhoneLanguageConfig(),
    private val english: () -> LanguageModel? = { BundledWordModels.of(Lang.EN) },
) {
    /** The phone language [unit] is read in, or null for the voices of the three languages. */
    fun of(unit: ReadingUnit, bookLanguage: String?): String? {
        val phone = PhoneLanguages.phoneLanguage(bookLanguage) ?: return null
        val latin = PhoneLanguages.scriptOf(phone) == UnicodeScript.LATIN
        return phone.takeUnless { latin && unit.lang == Lang.EN && readsAsEnglish(unit.text) }
    }

    /**
     * True when [unit]'s speakability score was measured with another language's word list than the one it is read
     * in: a French or Marathi sentence scored as English or Hindi looks like junk, so "Skip unreadable text" must not
     * judge it. Scripts with no word list (Tamil, Arabic) were never scored by one, so their scores stand.
     */
    fun misjudged(unit: ReadingUnit, bookLanguage: String?): Boolean {
        val phone = of(unit, bookLanguage) ?: return false
        return PhoneLanguages.scriptOf(phone) == scriptOf(unit.lang)
    }

    /** At least [PhoneLanguageConfig.minWords] words, and their letters mostly in English words. */
    fun readsAsEnglish(text: String): Boolean {
        val model = english()
        val words = Words.of(text).filter { it.codePointCount(0, it.length) >= config.minWordLetters }
        if (model == null || words.size < config.minWords) return false
        val letters = words.sumOf { it.length }
        val known = words.filter { it in model }.sumOf { it.length }
        return known >= config.englishShare * letters
    }

    private fun scriptOf(lang: Lang): UnicodeScript = when (lang) {
        Lang.EN -> UnicodeScript.LATIN
        Lang.HI -> UnicodeScript.DEVANAGARI
        Lang.BN -> UnicodeScript.BENGALI
    }

    companion object {
        /** One per process: the word list loads once and the rule holds no state. */
        val DEFAULT: SentenceLanguage = SentenceLanguage()
    }
}
