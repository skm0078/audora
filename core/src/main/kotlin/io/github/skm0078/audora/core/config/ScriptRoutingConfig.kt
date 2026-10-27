// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/ScriptRoutingConfig.kt
// Purpose: Tunables for reading each part of a mixed-script sentence with its own language's voice (P3-01, ADR-026).

package io.github.skm0078.audora.core.config

/**
 * @property minEnglishWordsInIndic an English run inside a mostly Hindi or Bengali sentence gets the English voice
 *   only from this many words on (Build Spec §3: "splits English runs of ≥3 words inside Indic sentences"). Shorter
 *   runs ("PDF", "Article 12") stay with the Indic voice, which reads a loanword in its own accent instead of
 *   switching voice mid-phrase. Indic text inside an English sentence always gets its own voice: an English voice
 *   cannot read Devanagari or Bengali at all.
 * @property minOtherScriptLetters letters of a script none of the three is written in (Tamil, Arabic, Japanese) get
 *   the phone's voice for that script's language from this many on (ADR 2026-10-26). Fewer (a lone "Ж" or "ש" used
 *   as a symbol) stay with the voice next to them, which reads them as it did before.
 */
data class ScriptRoutingConfig(val minEnglishWordsInIndic: Int = 3, val minOtherScriptLetters: Int = 3) {
    init {
        require(minEnglishWordsInIndic >= 1) { "minEnglishWordsInIndic must be >= 1, was $minEnglishWordsInIndic" }
        require(minOtherScriptLetters >= 1) { "minOtherScriptLetters must be >= 1, was $minOtherScriptLetters" }
    }
}
