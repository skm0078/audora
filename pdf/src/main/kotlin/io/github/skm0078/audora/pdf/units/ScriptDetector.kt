// File: pdf/src/main/kotlin/io/github/skm0078/audora/pdf/units/ScriptDetector.kt
// Purpose: A text's language by its dominant script: picks sentence rules and the unit's main language.

package io.github.skm0078.audora.pdf.units

import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ScriptLang

/**
 * One language per unit, for sentence splitting and as the unit's fallback voice. Which voice reads each part of a
 * mixed-script sentence is decided at render time by `:speech`'s ScriptRouter (P3-01), so it never changes this
 * module's output.
 */
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
            when (ScriptLang.of(cp)) {
                Lang.EN -> latin++
                Lang.HI -> devanagari++
                Lang.BN -> bengali++
                null -> Unit
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
