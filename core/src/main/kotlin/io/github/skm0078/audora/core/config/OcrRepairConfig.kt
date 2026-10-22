// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/OcrRepairConfig.kt
// Purpose: Tunables for correcting OCR near-misses against the bundled word lists (RI-3, ADR-035).

package io.github.skm0078.audora.core.config

/**
 * `OcrRepair` rewrites an OCR'd word only when it is not a known word, Tesseract was unsure of it, and exactly one
 * known, common word is a single edit (or a known OCR confusion) away. Every threshold leans towards leaving a word
 * alone: a wrong "correction" of a real word the list lacks is worse than an OCR error left in.
 *
 * @property enabled off leaves OCR text as Tesseract read it.
 * @property maxConfidence words Tesseract read with at least this confidence (0–100) are kept as read.
 * @property minLetters words shorter than this (in characters) are kept: too many short words are one edit apart.
 * @property minZipf a correction must be at least this common (Zipf frequency × 10, as in the word lists; 30 is about
 *   one in a million words).
 * @property zipfMargin when two corrections cost the same, the commoner wins only by at least this much (Zipf × 10);
 *   otherwise the word is ambiguous and kept.
 * @property confusionCost what a known OCR confusion ("rn" read for "m", a vowel sign for its neighbour) costs,
 *   against 1 for any other single edit; lower makes confusions win ties.
 */
data class OcrRepairConfig(
    val enabled: Boolean = true,
    val maxConfidence: Int = 90,
    val minLetters: Int = 3,
    val minZipf: Int = 25,
    val zipfMargin: Int = 5,
    val confusionCost: Float = 0.5f,
) {
    init {
        require(maxConfidence in 0..OcrConfig.MAX_CONFIDENCE) { "maxConfidence must be in 0..100, was $maxConfidence" }
        require(minLetters >= 1) { "minLetters must be >= 1, was $minLetters" }
        require(minZipf >= 0 && zipfMargin >= 0) { "minZipf and zipfMargin must be >= 0" }
        require(confusionCost > 0f && confusionCost <= 1f) { "confusionCost must be in (0, 1], was $confusionCost" }
    }
}
