// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/ReaderConfig.kt
// Purpose: Tunables for the reader screen: speed steps and sleep-timer choices (Build Spec §2, P1-05).

package io.github.skm0078.audora.core.config

/**
 * @property speeds playback speeds offered, ascending, within [MIN_SPEED]..[MAX_SPEED]. Applied by the player
 *   (Sonic time-stretch, C6), so a change is heard at once and nothing is synthesized again.
 * @property defaultSpeed used until the listener picks one; must be one of [speeds].
 * @property sleepMinutes sleep-timer durations offered, besides "end of page".
 */
data class ReaderConfig(
    val speeds: List<Float> = DEFAULT_SPEEDS,
    val defaultSpeed: Float = NORMAL_SPEED,
    val sleepMinutes: List<Int> = DEFAULT_SLEEP_MINUTES,
) {
    init {
        require(speeds.isNotEmpty() && speeds == speeds.sorted()) { "speeds must be non-empty and ascending" }
        require(speeds.all { it in MIN_SPEED..MAX_SPEED }) { "speeds must be within $MIN_SPEED..$MAX_SPEED" }
        require(defaultSpeed in speeds) { "defaultSpeed $defaultSpeed must be one of the offered speeds" }
        require(sleepMinutes.all { it > 0 }) { "sleepMinutes must be positive" }
    }

    /** The offered speed nearest to [speed], e.g. for a speed saved by an older version with other steps. */
    fun nearestSpeed(speed: Float): Float = speeds.minBy { kotlin.math.abs(it - speed) }

    companion object {
        const val MIN_SPEED = 0.5f
        const val MAX_SPEED = 3f
        private const val NORMAL_SPEED = 1f
        private val DEFAULT_SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f)
        private val DEFAULT_SLEEP_MINUTES = listOf(15, 30, 60)
    }
}
