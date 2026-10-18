// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/ReadingUnitConfig.kt
// Purpose: Tunables for cutting page text into ReadingUnits (Build Spec §5 steps 7 and 9).

package io.github.skm0078.audora.core.config

/**
 * @property maxUnitChars a "sentence" longer than this (tables, text without punctuation) is cut at the last
 *   space before the limit, so one unit never stalls the render queue. P1-03 chunks further per engine.
 * @property sentencePauseMs silence after each sentence. 0: voices already end a sentence in a short silence.
 * @property paragraphPauseMs silence after a paragraph's last sentence (Build Spec §5 step 9: ~400).
 * @property headingPauseMs silence after each sentence of a heading (spec: ~600).
 * @property listItemPauseMs silence after a list item's last sentence: shorter than a paragraph, since items are
 *   short and come in a run, but enough to hear where one ends.
 *   All pauses are added to the silence the voice ends with; tune them by ear on the phone (ADR-029).
 * @property lineOverlapRatio two glyph boxes are on the same line when their vertical overlap is at least
 *   this share of the smaller height.
 * @property minBoldTermLetters a bold term (plan item 64) holds a word of at least this many letters (vowel signs
 *   count): bold page numbers in a contents list, a bold maths symbol and letters spaced out in a bold title are
 *   not terms.
 */
data class ReadingUnitConfig(
    val maxUnitChars: Int = 400,
    val sentencePauseMs: Int = 0,
    val paragraphPauseMs: Int = 400,
    val headingPauseMs: Int = 600,
    val listItemPauseMs: Int = 300,
    val lineOverlapRatio: Float = 0.5f,
    val minBoldTermLetters: Int = 2,
) {
    init {
        require(maxUnitChars > 0) { "maxUnitChars must be > 0, was $maxUnitChars" }
        require(sentencePauseMs >= 0) { "sentencePauseMs must be >= 0, was $sentencePauseMs" }
        require(paragraphPauseMs >= 0 && headingPauseMs >= 0 && listItemPauseMs >= 0) { "pauses must be >= 0" }
        require(lineOverlapRatio in 0f..1f) { "lineOverlapRatio must be in 0..1, was $lineOverlapRatio" }
        require(minBoldTermLetters >= 1) { "minBoldTermLetters must be >= 1, was $minBoldTermLetters" }
    }
}
