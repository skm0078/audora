// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/VoiceShapingConfig.kt
// Purpose: Tunables for even loudness, voice boost, pause trim (plan items 9, 10) and expressiveness (item 8).

package io.github.skm0078.audora.core.config

import io.github.skm0078.audora.core.model.VoiceShaping

/**
 * Levels are in dBFS (0 = the loudest 16-bit sample). Loudness is the RMS of the frames that hold speech, so a
 * sentence with long pauses is not pushed up for its silence.
 *
 * @property frameMs the window loudness and silence are measured over; 10 ms is a common speech frame.
 * @property targetDbfs the speech loudness every sentence is brought to; −20 dBFS RMS leaves room for peaks and
 *   matches typical spoken-word masters (about −18 to −20 LUFS for mono speech).
 * @property speechGateDbfs frames quieter than this are not speech and do not count towards loudness.
 * @property maxGainDb the most a sentence is raised or lowered by evening, so a near-silent clip is not blown up.
 * @property ceilingDbfs peaks above this are softly limited, so a boost never clips; −1 dBFS is the usual ceiling.
 * @property kneeDb limiting starts this far below the ceiling and bends smoothly up to it.
 * @property boostChoicesDb the voice boost steps offered; 0 = none. Each step of 3 dB is clearly louder.
 * @property silenceDbfs below this a frame counts as silence for pause trim; voices' breaths sit around −45 dBFS.
 * @property longPauseMs a silence of the voice's own longer than this is cut down to it.
 * @property edgeMs the voice's lead-in and tail silence is cut down to this, so Audora's own pauses set the rhythm.
 * @property expressionNoiseScales a Piper or MMS voice's `noise_scale` at each expressiveness step, from newsreader
 *   (flat, even) to storyteller (livelier pitch and energy). The middle step is the voices' own 0.667, so the default
 *   sounds as before; much past 0.9 the voice starts to slur.
 * @property expressionNoiseWidths the voice's `noise_w` (how much word and phoneme lengths vary) at each step, the
 *   same number of steps; the middle is the voices' own 0.8. calibre's Piper settings tune the same two values.
 * @property styledNoiseScales and [styledNoiseWidths] the same steps for a model with speaking styles (Rasa,
 *   ADR-057), which carries a sentence's feeling in its style, so its noise only adds random swings: at the plain
 *   steps its voice jumped in pitch and pace ("shrill, changes again and again"), and a native listener found 0.35
 *   and 0.4 natural at the middle step (ADR 2026-10-30-bengali-spoken-spelling). The storyteller end stays below the
 *   plain middle.
 */
data class VoiceShapingConfig(
    val frameMs: Int = 10,
    val targetDbfs: Float = DEFAULT_TARGET_DBFS,
    val speechGateDbfs: Float = DEFAULT_SPEECH_GATE_DBFS,
    val maxGainDb: Float = 12f,
    val ceilingDbfs: Float = DEFAULT_CEILING_DBFS,
    val kneeDb: Float = 6f,
    val boostChoicesDb: List<Int> = DEFAULT_BOOST_CHOICES_DB,
    val silenceDbfs: Float = DEFAULT_SILENCE_DBFS,
    val longPauseMs: Int = 250,
    val edgeMs: Int = 40,
    val expressionNoiseScales: List<Float> = DEFAULT_NOISE_SCALES,
    val expressionNoiseWidths: List<Float> = DEFAULT_NOISE_WIDTHS,
    val styledNoiseScales: List<Float> = DEFAULT_STYLED_NOISE_SCALES,
    val styledNoiseWidths: List<Float> = DEFAULT_STYLED_NOISE_WIDTHS,
) {
    /** The highest expressiveness step; steps run from 0 (newsreader) to this (storyteller). */
    val maxExpression: Int get() = expressionNoiseScales.lastIndex

    init {
        require(frameMs > 0) { "frameMs must be > 0, was $frameMs" }
        require(targetDbfs < ceilingDbfs && ceilingDbfs <= 0f) { "need targetDbfs < ceilingDbfs <= 0" }
        require(speechGateDbfs < targetDbfs) { "speechGateDbfs must be below targetDbfs" }
        require(maxGainDb >= 0f) { "maxGainDb must be >= 0, was $maxGainDb" }
        require(kneeDb > 0f) { "kneeDb must be > 0, was $kneeDb" }
        require(boostChoicesDb.isNotEmpty() && boostChoicesDb.all { it >= 0 }) { "boost choices must be >= 0" }
        require(longPauseMs >= edgeMs && edgeMs >= 0) { "need 0 <= edgeMs <= longPauseMs" }
        require(expressionNoiseScales.size == expressionNoiseWidths.size) { "one noise width per noise scale" }
        require(VoiceShaping.DEFAULT_EXPRESSION in expressionNoiseScales.indices) { "default step out of range" }
        require(listOf(styledNoiseScales, styledNoiseWidths).all { it.size == expressionNoiseScales.size }) {
            "one styled noise scale and width per step"
        }
        val noise = expressionNoiseScales + expressionNoiseWidths + styledNoiseScales + styledNoiseWidths
        require(noise.all { it > 0f }) { "noise values must be > 0" }
    }

    /**
     * The `noise_scale` and `noise_w` for [step], clamped to the steps there are: a model with speaking styles
     * ([styled]) takes the calmer [styledNoiseScales] and [styledNoiseWidths].
     */
    fun expressionNoise(step: Int, styled: Boolean = false): Pair<Float, Float> {
        val index = step.coerceIn(0, maxExpression)
        return if (styled) {
            styledNoiseScales[index] to styledNoiseWidths[index]
        } else {
            expressionNoiseScales[index] to expressionNoiseWidths[index]
        }
    }

    private companion object {
        const val DEFAULT_TARGET_DBFS = -20f
        const val DEFAULT_SPEECH_GATE_DBFS = -45f
        const val DEFAULT_CEILING_DBFS = -1f
        const val DEFAULT_SILENCE_DBFS = -40f
        val DEFAULT_BOOST_CHOICES_DB = listOf(0, 3, 6, 9)
        val DEFAULT_NOISE_SCALES = listOf(0.33f, 0.5f, 0.667f, 0.78f, 0.9f)
        val DEFAULT_NOISE_WIDTHS = listOf(0.4f, 0.6f, 0.8f, 0.92f, 1.05f)
        val DEFAULT_STYLED_NOISE_SCALES = listOf(0.2f, 0.28f, 0.35f, 0.45f, 0.55f)
        val DEFAULT_STYLED_NOISE_WIDTHS = listOf(0.25f, 0.32f, 0.4f, 0.5f, 0.6f)
    }
}
