// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/NarrationConfig.kt
// Purpose: Tunables for the narration's natural rise and fall across a paragraph (Q-3, ADR natural-narration).

package io.github.skm0078.audora.core.config

import kotlin.math.abs

/**
 * How a narrator moves through a paragraph: it starts a little higher and settles lower by its end, slows for the
 * first sentence of a section and a paragraph's last, and takes more breath in a long sentence. Every shift is small
 * on purpose: heard together they stop sentence after sentence sounding the same, heard alone none should stand out.
 *
 * @property startSemitones the pitch of a paragraph's first sentence, in semitones from the voice's own.
 * @property endSemitones the pitch of its last; the sentences between step evenly from one to the other.
 * @property sectionStartRate the speed of a section's first sentence (straight after a heading), as a multiplier.
 * @property paragraphStartRate the speed of any other paragraph's first sentence.
 * @property paragraphEndRate the speed of a paragraph's last sentence (of two or more).
 * @property longSentenceWords a sentence of at least this many words is long.
 * @property longClauseScale a long sentence's pauses at its commas, as a multiplier: a breath before going on.
 * @property jitterSemitones each sentence's own pitch varies by up to this much, the same every time it is read.
 * @property jitterRate and its speed by up to this share.
 */
data class NarrationConfig(
    val startSemitones: Float = DEFAULT_START_SEMITONES,
    val endSemitones: Float = DEFAULT_END_SEMITONES,
    val sectionStartRate: Float = DEFAULT_SECTION_START_RATE,
    val paragraphStartRate: Float = DEFAULT_PARAGRAPH_START_RATE,
    val paragraphEndRate: Float = DEFAULT_PARAGRAPH_END_RATE,
    val longSentenceWords: Int = DEFAULT_LONG_SENTENCE_WORDS,
    val longClauseScale: Float = DEFAULT_LONG_CLAUSE_SCALE,
    val jitterSemitones: Float = DEFAULT_JITTER_SEMITONES,
    val jitterRate: Float = DEFAULT_JITTER_RATE,
) {
    init {
        val rates = listOf(sectionStartRate, paragraphStartRate, paragraphEndRate, longClauseScale)
        require(rates.all { it > 0f && it.isFinite() }) { "rates and scales must be > 0" }
        require(abs(startSemitones) <= MAX_SEMITONES && abs(endSemitones) <= MAX_SEMITONES) {
            "the arc stays within $MAX_SEMITONES semitones"
        }
        require(jitterSemitones in 0f..MAX_SEMITONES && jitterRate in 0f..MAX_JITTER_RATE) { "jitter out of range" }
        require(longSentenceWords >= 1) { "longSentenceWords must be >= 1, was $longSentenceWords" }
    }

    companion object {
        const val DEFAULT_START_SEMITONES = 0.5f
        const val DEFAULT_END_SEMITONES = -0.6f
        const val DEFAULT_SECTION_START_RATE = 0.94f
        const val DEFAULT_PARAGRAPH_START_RATE = 0.98f
        const val DEFAULT_PARAGRAPH_END_RATE = 0.96f
        const val DEFAULT_LONG_SENTENCE_WORDS = 24
        const val DEFAULT_LONG_CLAUSE_SCALE = 1.25f
        const val DEFAULT_JITTER_SEMITONES = 0.2f
        const val DEFAULT_JITTER_RATE = 0.015f

        /** A narrator's whole arc stays inside a tone: more would sound sung. */
        const val MAX_SEMITONES = 2f
        const val MAX_JITTER_RATE = 0.1f
    }
}
