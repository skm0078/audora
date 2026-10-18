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
 *   8 ms covers one pitch period of the lowest voices (about 80 Hz; at 5 ms a 80-85 Hz voice warbled).
 * @property searchStep samples skipped between alignment candidates; 2 halves the search with no audible cost.
 * @property alignFloorDbfs a window quieter than this (its RMS) has no waveform worth lining up: it stays at its
 *   nominal place instead of snapping to the search's edge in a silence.
 * @property silenceBelowLoudDb frames this far below the piece's loud level are silence. Relative, so a quiet voice
 *   keeps its weak consonants: the model's output level varies by voice.
 * @property loudPercentile the piece's loud level is its frame levels' value at this rank.
 * @property transientDb a frame whose level differs from a neighbour's by more than this is an onset or release.
 * @property onsetGuardFrames held-sound frames this close to an onset or release are spared like it: a consonant's
 *   burst and transition span more than one frame.
 * @property silenceWeight how much of the extra speed a silent frame takes, relative to the other kinds: the most.
 * @property steadyWeight the same for a held sound (a vowel, a hum, a fricative's steady hiss): a quarter of a
 *   silence's.
 * @property transientWeight the same for an onset or release: none, so consonants stay crisp.
 * @property consonantShare the share of the asked extra speed every frame gets, so even an onset is sped up a little
 *   (with 0.25 at 2x an onset plays at 1.25x) and the rest goes to silences and held sounds.
 * @property maxFrameSpeed no frame plays faster than this; past it the stretch smears.
 * @property endFadeMs the piece's last few milliseconds fade to zero: the stretch can end mid-wave, which would click
 *   into the pause after it.
 * @property minFactor below this factor the voice is returned as it is (no audible difference, no work).
 *
 * Tuned with a line-by-line simulator of SmartStretch on synthetic signals; judged by ear on the phone (K16).
 */
data class SmartSpeedConfig(
    val frameMs: Int = 10,
    val windowMs: Int = 20,
    val toleranceMs: Int = 8,
    val searchStep: Int = 2,
    val alignFloorDbfs: Float = DEFAULT_ALIGN_FLOOR_DBFS,
    val silenceBelowLoudDb: Float = 35f,
    val loudPercentile: Float = 0.9f,
    val transientDb: Float = 6f,
    val onsetGuardFrames: Int = 1,
    val silenceWeight: Float = 1f,
    val steadyWeight: Float = 0.25f,
    val transientWeight: Float = 0f,
    val consonantShare: Float = 0.25f,
    val maxFrameSpeed: Float = 4f,
    val endFadeMs: Int = 4,
    val minFactor: Float = 1.001f,
) {
    init {
        require(frameMs > 0 && windowMs >= 2 && toleranceMs >= 0) { "need frameMs > 0, windowMs >= 2, tolerance >= 0" }
        require(searchStep >= 1) { "searchStep must be >= 1, was $searchStep" }
        require(listOf(silenceWeight, steadyWeight, transientWeight).all { it >= 0f }) { "weights must be >= 0" }
        require(onsetGuardFrames >= 0 && endFadeMs >= 0) { "onsetGuardFrames and endFadeMs must be >= 0" }
        require(silenceBelowLoudDb > 0f && loudPercentile in 0f..1f) { "need silenceBelowLoudDb > 0, percentile 0..1" }
        require(consonantShare in 0f..1f) { "consonantShare must be in 0..1, was $consonantShare" }
        require(maxFrameSpeed > 1f && minFactor >= 1f) { "need maxFrameSpeed > 1 and minFactor >= 1" }
    }

    private companion object {
        const val DEFAULT_ALIGN_FLOOR_DBFS = -50f
    }
}
