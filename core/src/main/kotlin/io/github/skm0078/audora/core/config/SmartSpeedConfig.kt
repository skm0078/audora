// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/SmartSpeedConfig.kt
// Purpose: Tunables for smart speed (plan item 10, ADR-049): the voice sped up unevenly past the model's top speed.

package io.github.skm0078.audora.core.config

/**
 * Smart speed is a WSOLA time-stretch whose speed changes frame by frame: silences and held sounds are shortened
 * most, onsets (consonants, the start and end of words) least, and the whole sentence still comes out exactly the
 * asked factor shorter.
 *
 * @property frameMs the frame each part of the voice is judged over (silence, steady or changing).
 * @property windowMs WSOLA's window; with 50% overlap each output hop is half of it. 20 ms holds at least two pitch
 *   periods of every voice, which the alignment search needs.
 * @property toleranceMs how far a window may move from its nominal place to line up with the last one's waveform;
 *   5 ms covers one pitch period of a low voice (about 100 Hz).
 * @property searchStep samples skipped between alignment candidates; 2 halves the search with no audible cost.
 * @property silenceDbfs frames quieter than this are silence (the same level as pause trim's).
 * @property transientDb a frame whose level differs from a neighbour's by more than this is an onset or release.
 * @property silenceWeight how much of the extra speed a silent frame takes, relative to the other kinds: the most.
 * @property steadyWeight the same for a held sound (a vowel, a hum): half a silence's.
 * @property transientWeight the same for an onset or release: none, so consonants stay crisp.
 * @property smoothFrames the weights are averaged over this many frames so the speed never jumps frame to frame.
 * @property consonantShare the share of the asked extra speed every frame gets, so even an onset is sped up a little
 *   (with 0.5 at 2x an onset plays at 1.5x) and the rest goes to silences and held sounds.
 * @property maxFrameSpeed no frame plays faster than this; past it the stretch smears.
 * @property minFactor below this factor the voice is returned as it is (no audible difference, no work).
 */
data class SmartSpeedConfig(
    val frameMs: Int = 10,
    val windowMs: Int = 20,
    val toleranceMs: Int = 5,
    val searchStep: Int = 2,
    val silenceDbfs: Float = DEFAULT_SILENCE_DBFS,
    val transientDb: Float = 6f,
    val silenceWeight: Float = 1f,
    val steadyWeight: Float = 0.5f,
    val transientWeight: Float = 0f,
    val smoothFrames: Int = 3,
    val consonantShare: Float = 0.5f,
    val maxFrameSpeed: Float = 4f,
    val minFactor: Float = 1.001f,
) {
    init {
        require(frameMs > 0 && windowMs >= 2 && toleranceMs >= 0) { "need frameMs > 0, windowMs >= 2, tolerance >= 0" }
        require(searchStep >= 1) { "searchStep must be >= 1, was $searchStep" }
        require(listOf(silenceWeight, steadyWeight, transientWeight).all { it >= 0f }) { "weights must be >= 0" }
        require(smoothFrames >= 1) { "smoothFrames must be >= 1, was $smoothFrames" }
        require(consonantShare in 0f..1f) { "consonantShare must be in 0..1, was $consonantShare" }
        require(maxFrameSpeed > 1f && minFactor >= 1f) { "need maxFrameSpeed > 1 and minFactor >= 1" }
    }

    private companion object {
        const val DEFAULT_SILENCE_DBFS = -40f
    }
}
