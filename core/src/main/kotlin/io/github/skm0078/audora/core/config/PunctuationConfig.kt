// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/PunctuationConfig.kt
// Purpose: Tunables for the opt-in punctuation model (RI-11): which scanned sentences it may add pauses to.

package io.github.skm0078.audora.core.config

import io.github.skm0078.audora.core.model.Lang

/**
 * The model only ever sees a run-on: a sentence from a scanned page in one of [langs], at least [minRunWords] words
 * long, with none of [sentenceEnds] inside it. Text that already has its full stops is never touched. Measured on the
 * scanned corpus (RI-11): below about 40 words the model adds more wrong pauses than right ones.
 *
 * @property minRunWords the shortest run the model is asked about.
 * @property sentenceEnds a run containing any of these already has its sentence breaks.
 * @property addedMarks the only marks taken from the model's answer; anything else it adds is dropped.
 * @property softenedMarks marks taken from the model as [softMark]: on the scanned corpus two of every three full stops
 *   it added fell mid-sentence, while its commas read naturally, so a wrong one costs a short pause, not a sentence
 *   break.
 * @property softMark what a softened mark becomes.
 * @property langs the languages the downloaded model reads (the only one today is English).
 * @property numThreads the model's threads; it is small (7.5 MB) and runs once per run-on.
 */
data class PunctuationConfig(
    val minRunWords: Int = DEFAULT_MIN_RUN_WORDS,
    val sentenceEnds: String = ".?!।॥",
    val addedMarks: String = ",.?!;:",
    val softenedMarks: String = ".!;:",
    val softMark: Char = ',',
    val langs: Set<Lang> = setOf(Lang.EN),
    val numThreads: Int = 1,
) {
    init {
        require(minRunWords > 1) { "minRunWords must be > 1, was $minRunWords" }
        require(sentenceEnds.isNotEmpty()) { "sentenceEnds must not be empty" }
        require(addedMarks.isNotEmpty()) { "addedMarks must not be empty" }
        require(numThreads > 0) { "numThreads must be > 0, was $numThreads" }
    }

    private companion object {
        const val DEFAULT_MIN_RUN_WORDS = 40
    }
}
