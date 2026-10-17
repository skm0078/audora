// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/VoiceShapingConfig.kt
// Purpose: Tunables for even loudness, voice boost and pause trim (plan items 9, 10).

package io.github.skm0078.audora.core.config

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
) {
    init {
        require(frameMs > 0) { "frameMs must be > 0, was $frameMs" }
        require(targetDbfs < ceilingDbfs && ceilingDbfs <= 0f) { "need targetDbfs < ceilingDbfs <= 0" }
        require(speechGateDbfs < targetDbfs) { "speechGateDbfs must be below targetDbfs" }
        require(maxGainDb >= 0f) { "maxGainDb must be >= 0, was $maxGainDb" }
        require(kneeDb > 0f) { "kneeDb must be > 0, was $kneeDb" }
        require(boostChoicesDb.isNotEmpty() && boostChoicesDb.all { it >= 0 }) { "boost choices must be >= 0" }
        require(longPauseMs >= edgeMs && edgeMs >= 0) { "need 0 <= edgeMs <= longPauseMs" }
    }

    private companion object {
        const val DEFAULT_TARGET_DBFS = -20f
        const val DEFAULT_SPEECH_GATE_DBFS = -45f
        const val DEFAULT_CEILING_DBFS = -1f
        const val DEFAULT_SILENCE_DBFS = -40f
        val DEFAULT_BOOST_CHOICES_DB = listOf(0, 3, 6, 9)
    }
}
