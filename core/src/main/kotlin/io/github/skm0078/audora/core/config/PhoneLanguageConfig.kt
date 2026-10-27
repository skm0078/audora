// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/PhoneLanguageConfig.kt
// Purpose: Tunables for books in languages read by the phone's own voices (ADR 2026-10-26).

package io.github.skm0078.audora.core.config

/**
 * A book whose language is written in Latin letters but is not English (French, Swahili) still holds English
 * sentences now and then (an abstract, a quote), and some files name the wrong language (an office suite writes its
 * own). A Latin sentence of such a book stays with the English voice when it reads as English: at least
 * [minWords] words of [minWordLetters] letters or more, and [englishShare] of their letters in English words.
 * Measured on everyday sentences: English scores 1.0, French, Spanish, German, Portuguese, Italian, Dutch and
 * Indonesian 0.1 to 0.6 (ADR 2026-10-26).
 *
 * @property missingVoiceRetryMs a language the phone had no offline voice for is asked about again after this long,
 *   not on every sentence, so a voice pack installed while the book plays is found within a minute and a book with no
 *   voice does not ask the phone's engine (and log) for each sentence.
 */
data class PhoneLanguageConfig(
    val englishShare: Float = 0.85f,
    val minWords: Int = 4,
    val minWordLetters: Int = 3,
    val missingVoiceRetryMs: Long = 60_000L,
) {
    init {
        require(missingVoiceRetryMs >= 0) { "missingVoiceRetryMs must be >= 0, was $missingVoiceRetryMs" }
        require(englishShare in 0f..1f) { "englishShare must be in 0..1, was $englishShare" }
        require(minWords >= 1) { "minWords must be >= 1, was $minWords" }
        require(minWordLetters >= 1) { "minWordLetters must be >= 1, was $minWordLetters" }
    }
}
