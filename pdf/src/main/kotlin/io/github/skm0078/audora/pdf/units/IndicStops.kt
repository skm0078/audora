// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/units/IndicStops.kt
// Purpose: Full stops in Hindi and Bengali text that never end a sentence: inside dotted words, after "a.m.".

package io.github.skm0078.audora.pdf.units

import io.github.skm0078.audora.pdf.units.SentenceScan.skipSpaces

/**
 * Indic text ends its sentences with the danda, so a full stop glued between two Devanagari or Bengali letters is
 * inside a dotted abbreviation ("रा.शै.अ.प्र.प.", "वि.वि.", "बी.ए."), as the JDK reads it, where ICU (Android) breaks
 * there. A sentence end that lost its space ("है.अगला") merges instead, which costs a pause, where a split
 * abbreviation cuts a name in half.
 */
internal object IndicStops {
    /** Zero-width non-joiner and joiner, which some fonts' text puts inside Indic words ("र‍्य", "कि.‍मी."). */
    const val JOINERS = "‌‍"

    /**
     * The break [at] ends the word from [start] to its full stop [stop], and that stop does not end a sentence: it is
     * glued between Indic letters ([gluedStop]), the word has such a stop inside it ("वि.वि. बंद"; a date's stops
     * between digits, "१२.०५.२०१९. अगली", do not count), or the word is "a.m." or "p.m." (any case) before a Hindi or
     * Bengali word ("समय 10:30 a.m. है").
     */
    fun holds(text: String, start: Int, stop: Int, at: Int): Boolean {
        val inside = (start until stop).any { text[it] == '.' && !text[it + 1].isDigit() && gluedStop(text, it + 1) }
        val meridiem = text.substring(start, stop).lowercase() in MERIDIEMS &&
            isIndic(text.getOrElse(skipSpaces(text, at)) { ' ' })
        return gluedStop(text, at) || inside || meridiem
    }

    /**
     * The break [at] falls right after a full stop between two Devanagari or Bengali letters, with no space
     * ("रा.|शै.अ.प्र.प.", "अ.|जा."), or after a joiner that follows such a stop, where ICU puts it ("कि.‍|मी.").
     */
    private fun gluedStop(text: String, at: Int): Boolean {
        var stop = at
        while (stop > 0 && text[stop - 1] in JOINERS) stop--
        return stop >= 2 && text[stop - 1] == '.' && isIndic(text[stop - 2]) && isIndic(text.getOrElse(at) { ' ' })
    }

    /** A Devanagari or Bengali letter or sign, or a zero-width joiner inside such a word. */
    fun isIndic(c: Char): Boolean = c in 'ऀ'..'ॿ' || c in 'ঀ'..'৿' || c in JOINERS

    private val MERIDIEMS = setOf("a.m", "p.m")
}
