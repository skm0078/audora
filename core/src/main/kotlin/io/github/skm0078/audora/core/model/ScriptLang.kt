// File: core/src/main/kotlin/io/github/skm0078/audora/core/model/ScriptLang.kt
// Purpose: The one mapping from Unicode script to reading language (ADR-005), shared by :pdf and :speech.

package io.github.skm0078.audora.core.model

/** Latin → English, Devanagari → Hindi, Bengali → Bengali (Build Spec §3, ADR-005). Deterministic and instant. */
object ScriptLang {
    /**
     * The language whose script [codePoint] belongs to, or null for anything script-neutral: spaces, punctuation,
     * ASCII digits, symbols, and scripts Audora does not read. Devanagari and Bengali digits count as their script.
     */
    fun of(codePoint: Int): Lang? = when (Character.UnicodeScript.of(codePoint)) {
        Character.UnicodeScript.LATIN -> Lang.EN
        Character.UnicodeScript.DEVANAGARI -> Lang.HI
        Character.UnicodeScript.BENGALI -> Lang.BN
        else -> null
    }
}
