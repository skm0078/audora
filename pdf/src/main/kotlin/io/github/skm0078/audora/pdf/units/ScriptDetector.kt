// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/units/ScriptDetector.kt
// Purpose: Guesses a text's language from its dominant script; P3-01 replaces this with per-sentence routing.

package io.github.skm0078.audora.pdf.units

import io.github.skm0078.audora.core.model.Lang

object ScriptDetector {
    /** The language of the most frequent script among the letters of [text], or null if it has no letters. */
    fun dominantLang(text: CharSequence): Lang? {
        var latin = 0
        var devanagari = 0
        var bengali = 0
        var i = 0
        while (i < text.length) {
            val cp = Character.codePointAt(text, i)
            i += Character.charCount(cp)
            when (Character.UnicodeScript.of(cp)) {
                Character.UnicodeScript.LATIN -> latin++
                Character.UnicodeScript.DEVANAGARI -> devanagari++
                Character.UnicodeScript.BENGALI -> bengali++
                else -> Unit
            }
        }
        val max = maxOf(latin, devanagari, bengali)
        return when {
            max == 0 -> null
            devanagari == max -> Lang.HI
            bengali == max -> Lang.BN
            else -> Lang.EN
        }
    }
}
