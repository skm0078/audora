// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/SpeechStyleConfig.kt
// Purpose: Tunables for the voice's rate and the pace of pauses (ADR-033): the range, step and pause lengths.

package io.github.skm0078.audora.core.config

import io.github.skm0078.audora.core.model.ClauseMark
import io.github.skm0078.audora.core.model.Pace
import io.github.skm0078.audora.core.model.SpeechStyle
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The silences one [Pace] adds.
 *
 * @property sentenceGapMs silence after every sentence, on top of the short one each voice ends with.
 * @property pauseScale multiplies a sentence's own pause (after paragraphs, headings and list items, ADR-029).
 */
data class PaceTiming(val sentenceGapMs: Int, val pauseScale: Float) {
    init {
        require(sentenceGapMs >= 0) { "sentenceGapMs must be >= 0, was $sentenceGapMs" }
        require(pauseScale >= 0f && pauseScale.isFinite()) { "pauseScale must be >= 0, was $pauseScale" }
    }
}

/**
 * Speed is hybrid (ADR-033): up to [maxModelRate] the voice model itself speaks faster (VITS/Piper length scale,
 * Kokoro and Supertonic duration), which keeps every phoneme; past it the model stays at [maxModelRate] and the
 * player time-stretches the rest (Sonic, in Media3), because a model pushed further starts to swallow syllables.
 * Slower than normal is always the model's own: a stretched voice sounds smeared.
 *
 * @property minRate slowest rate offered. Neural voices stay clear down to about half speed.
 * @property maxRate fastest rate offered.
 * @property maxModelRate fastest rate asked of the voice model; a starting point from published listening tests,
 *   to be tuned per voice by ear on the phone.
 * @property rateStep the slider's step; a saved rate is snapped to it.
 * @property paces the silences of each pace at normal speed; tune by ear on the phone.
 * @property clausePausesMs the pause at each strong mark inside a sentence at normal speed, before the pace's
 *   scale (RI-6); tune by ear on the phone.
 * @property asideGainDb the loudness of bracketed words against the rest of the sentence, in decibels.
 */
data class SpeechStyleConfig(
    val minRate: Float = 0.5f,
    val maxRate: Float = 3f,
    val maxModelRate: Float = 1.5f,
    val rateStep: Float = 0.05f,
    val paces: Map<Pace, PaceTiming> = DEFAULT_PACES,
    val clausePausesMs: Map<ClauseMark, Int> = DEFAULT_CLAUSE_PAUSES,
    val asideGainDb: Float = DEFAULT_ASIDE_GAIN_DB,
) {
    init {
        require(minRate > 0f && maxRate > minRate) { "rates must satisfy 0 < minRate < maxRate" }
        require(SpeechStyle.NORMAL_RATE in minRate..maxRate) { "the normal rate must be in range" }
        require(maxModelRate in SpeechStyle.NORMAL_RATE..maxRate) { "maxModelRate must be in 1..maxRate" }
        require(rateStep > 0f) { "rateStep must be > 0, was $rateStep" }
        require(paces.keys == Pace.entries.toSet()) { "every pace needs its timing" }
        require(clausePausesMs.keys == ClauseMark.entries.toSet()) { "every clause mark needs its pause" }
        require(clausePausesMs.values.all { it >= 0 }) { "clause pauses must be >= 0" }
        require(asideGainDb <= 0f && asideGainDb.isFinite()) { "asideGainDb must be <= 0, was $asideGainDb" }
    }

    /** [rate] within the range and on a step, 2 decimals; a non-finite value (a corrupt file) is normal speed. */
    fun clampRate(rate: Float): Float {
        if (!rate.isFinite()) return SpeechStyle.NORMAL_RATE
        val steps = ((rate.coerceIn(minRate, maxRate) - minRate) / rateStep).roundToInt()
        val snapped = (minRate + steps * rateStep).coerceIn(minRate, maxRate)
        return (snapped * HUNDREDTHS).roundToInt() / HUNDREDTHS
    }

    /** The rate the voice model renders at for listening [rate]. */
    fun modelRate(rate: Float): Float = clampRate(rate).coerceAtMost(maxModelRate)

    /** The player's time-stretch for listening [rate]: 1.0 until the model's cap, the rest above it. */
    fun playerSpeed(rate: Float): Float = clampRate(rate) / modelRate(rate)

    /**
     * Milliseconds of silence to put in the clip after a sentence whose own pause is [pauseAfterMs]. Pauses shrink
     * with the square root of the speed, not in proportion to it: kept pauses are what keeps fast speech
     * intelligible. The clip is then stretched by [playerSpeed], so the silence is lengthened to survive that.
     */
    fun silenceAfterMs(pauseAfterMs: Int, style: SpeechStyle): Int {
        val timing = paces.getValue(style.pace)
        val atNormal = timing.sentenceGapMs + pauseAfterMs.coerceAtLeast(0) * timing.pauseScale
        val rate = clampRate(style.rate)
        return (atNormal / sqrt(rate) * playerSpeed(rate)).roundToInt()
    }

    /** Milliseconds of silence at [mark] inside a sentence: scaled by the pace and the speed like [silenceAfterMs]. */
    fun clauseSilenceMs(mark: ClauseMark, style: SpeechStyle): Int {
        val atNormal = clausePausesMs.getValue(mark) * paces.getValue(style.pace).pauseScale
        val rate = clampRate(style.rate)
        return (atNormal / sqrt(rate) * playerSpeed(rate)).roundToInt()
    }

    private companion object {
        const val HUNDREDTHS = 100f

        /** About 70 % of the loudness: heard as an aside, still clear. */
        const val DEFAULT_ASIDE_GAIN_DB = -3f

        val DEFAULT_PACES = mapOf(
            // Normal: about 650 ms after a paragraph, 900 after a heading (on ReadingUnitConfig's 400 and 600).
            Pace.TIGHT to PaceTiming(sentenceGapMs = 0, pauseScale = 0.6f),
            Pace.NORMAL to PaceTiming(sentenceGapMs = 150, pauseScale = 1.25f),
            Pace.RELAXED to PaceTiming(sentenceGapMs = 400, pauseScale = 1.8f),
        )

        // At Normal pace (x1.25): a dash about 250 ms, a colon 300, an ellipsis 500; a bracket or quote is a breath.
        val DEFAULT_CLAUSE_PAUSES = mapOf(
            ClauseMark.DASH to 200,
            ClauseMark.COLON to 240,
            ClauseMark.SEMICOLON to 240,
            ClauseMark.BRACKET to 120,
            ClauseMark.QUOTE to 80,
            ClauseMark.ELLIPSIS to 400,
            ClauseMark.DANDA to 280,
        )
    }
}
