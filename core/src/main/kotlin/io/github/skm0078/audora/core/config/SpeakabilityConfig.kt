// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/SpeakabilityConfig.kt
// Purpose: Tunables for the speakability score: how sure we are a sentence is real language (RI-2, ADR-035).

package io.github.skm0078.audora.core.config

/**
 * ADR-035. `:pdf` scores every sentence (`ReadingUnit.speakScore`, 0–1) from up to five signals and blends them with
 * these weights; a signal that has nothing to say (no OCR on a text-layer page, no dictionary downloaded, too few
 * words) abstains and the others are re-weighted. `:playback` skips sentences scoring below [skipBelow] when
 * "Skip unreadable text" is on. The weights are first guesses; RI-2a tunes them on the hand-labelled lines in
 * pdf/golden/speakability/ and records the result in ADR-035.
 *
 * @property skipBelow a sentence scoring below this is unreadable junk (symbol soup, OCR noise, a broken font map).
 * @property ocrConfidenceWeight weight of the OCR engine's own word confidence (OCR pages only).
 * @property dictionaryWeight weight of the share of words found in the language's word list.
 * @property letterModelWeight weight of the language's letter-trigram likelihood: real words have likely letter
 *   sequences even when they are not in the word list (names, inflections).
 * @property symbolSoupWeight weight of the share of characters that are letters rather than symbols and stray
 *   digits.
 * @property scriptMixWeight weight of words that keep to one script (OCR noise mixes Latin into Devanagari words).
 * @property minDictionaryWords the dictionary and letter-model signals abstain on sentences with fewer words than
 *   this, where one unknown name would decide the score.
 */
data class SpeakabilityConfig(
    val skipBelow: Float = 0.5f,
    val ocrConfidenceWeight: Float = 0.25f,
    val dictionaryWeight: Float = 0.3f,
    val letterModelWeight: Float = 0.2f,
    val symbolSoupWeight: Float = 0.15f,
    val scriptMixWeight: Float = 0.1f,
    val minDictionaryWords: Int = 3,
) {
    init {
        require(skipBelow in 0f..1f) { "skipBelow must be in 0..1, was $skipBelow" }
        val weights =
            listOf(ocrConfidenceWeight, dictionaryWeight, letterModelWeight, symbolSoupWeight, scriptMixWeight)
        require(weights.all { it >= 0f }) { "weights must be >= 0, were $weights" }
        require(weights.sum() > 0f) { "at least one weight must be > 0" }
        require(minDictionaryWords >= 1) { "minDictionaryWords must be >= 1, was $minDictionaryWords" }
    }
}
