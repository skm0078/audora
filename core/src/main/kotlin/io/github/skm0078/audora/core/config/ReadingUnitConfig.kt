// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/ReadingUnitConfig.kt
// Purpose: Tunables for cutting page text into ReadingUnits (Build Spec §5 steps 7 and 9).

package io.github.skm0078.audora.core.config

/**
 * @property maxUnitChars a "sentence" longer than this (tables, text without punctuation) is cut at the last
 *   space before the limit, so one unit never stalls the render queue. P1-03 chunks further per engine.
 * @property sentencePauseMs silence after each sentence. 0 until prosody (P2-04): voices already end in silence.
 * @property lineOverlapRatio two glyph boxes are on the same line when their vertical overlap is at least
 *   this share of the smaller height.
 */
data class ReadingUnitConfig(
    val maxUnitChars: Int = 400,
    val sentencePauseMs: Int = 0,
    val lineOverlapRatio: Float = 0.5f,
) {
    init {
        require(maxUnitChars > 0) { "maxUnitChars must be > 0, was $maxUnitChars" }
        require(sentencePauseMs >= 0) { "sentencePauseMs must be >= 0, was $sentencePauseMs" }
        require(lineOverlapRatio in 0f..1f) { "lineOverlapRatio must be in 0..1, was $lineOverlapRatio" }
    }
}
