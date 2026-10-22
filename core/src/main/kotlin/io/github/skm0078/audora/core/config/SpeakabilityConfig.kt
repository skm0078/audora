// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/SpeakabilityConfig.kt
// Purpose: Tunables for the speakability score: how sure we are a sentence is real language (RI-2, ADR-035).

package io.github.skm0078.audora.core.config

/**
 * ADR-035. `:pdf` scores every sentence (`ReadingUnit.speakScore`, 0–1) from up to five signals. Each signal's raw
 * value (a share or a probability, 0–1) is mapped onto 0–1 by its [SignalRange], then the signals are blended with
 * these weights; a signal that has nothing to say (no OCR on a text-layer page, no word list, too few words) abstains
 * and the others are re-weighted. `:playback` skips sentences scoring below [skipBelow] when "Skip unreadable text" is
 * on. Values are tuned on the labelled lines in pdf/golden/speakability/labels.tsv (SpeakabilityLabelsTest): 91% of
 * junk lines fall below [skipBelow] and 1.4% of real ones do (ADR-035 has the tuning).
 *
 * @property skipBelow a sentence scoring below this is unreadable junk (symbol soup, OCR noise, a broken font map).
 * @property ocrConfidence the OCR engine's own word confidence, as a share of its maximum (OCR pages only).
 * @property dictionary the share of words found in the language's word list.
 * @property letterModel how word-like the words' spelling is under the language's letter-trigram model: real words
 *   have likely letter sequences even when they are not in the word list (names, inflections).
 * @property symbolSoup the share of characters in tokens that look like words or numbers, not symbol soup.
 * @property scriptMix the share of words that keep to one script (OCR noise mixes Latin into Devanagari words).
 * @property minDictionaryWords the dictionary signal abstains on sentences with fewer words than this, where one
 *   unknown name would decide the score.
 * @property shortUnitChars OCR confidence abstains on a unit without words of at most this many visible characters:
 *   list numbers ("2.", "14") often read with a low confidence but are spoken correctly.
 */
data class SpeakabilityConfig(
    val skipBelow: Float = 0.55f,
    val ocrConfidence: Signal = Signal(weight = 0.3f, range = SignalRange(junk = 0.4f, clean = 0.7f)),
    val dictionary: Signal = Signal(weight = 0.1f, range = SignalRange(junk = 0f, clean = 0.8f)),
    val letterModel: Signal = Signal(weight = 0.4f, range = SignalRange(junk = 0.2f, clean = 1f)),
    val symbolSoup: Signal = Signal(weight = 0.15f, range = SignalRange(junk = 0.3f, clean = 1f)),
    val scriptMix: Signal = Signal(weight = 0.05f, range = SignalRange(junk = 0.5f, clean = 1f)),
    val minDictionaryWords: Int = 3,
    val shortUnitChars: Int = 5,
) {
    init {
        require(skipBelow in 0f..1f) { "skipBelow must be in 0..1, was $skipBelow" }
        val weights = signals.map { it.weight }
        require(weights.sum() > 0f) { "at least one weight must be > 0, were $weights" }
        require(minDictionaryWords >= 1) { "minDictionaryWords must be >= 1, was $minDictionaryWords" }
        require(shortUnitChars >= 0) { "shortUnitChars must be >= 0, was $shortUnitChars" }
    }

    val signals: List<Signal> get() = listOf(ocrConfidence, dictionary, letterModel, symbolSoup, scriptMix)
}

/** One signal's share of the blend and how its raw value maps onto 0–1. */
data class Signal(val weight: Float, val range: SignalRange) {
    init {
        require(weight >= 0f) { "weight must be >= 0, was $weight" }
    }
}

/** A raw value at or below [junk] maps to 0, at or above [clean] to 1, linearly between. */
data class SignalRange(val junk: Float, val clean: Float) {
    init {
        require(junk in 0f..1f && clean in 0f..1f && junk < clean) { "need 0 <= junk < clean <= 1, were $junk, $clean" }
    }

    fun map(raw: Float): Float = ((raw - junk) / (clean - junk)).coerceIn(0f, 1f)
}
