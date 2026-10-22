// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/SleepConfig.kt
// Purpose: Tunables for the sleep timer extras (plan item 37): fade-out length and what counts as a shake.

package io.github.skm0078.audora.core.config

/**
 * @property fadeOutMs how long the voice fades out at the end of a minutes timer.
 * @property fadeStepMs how often the volume steps down while fading; 20 steps a second sounds smooth.
 * @property shakeThresholdG a jolt is an acceleration of at least this many g, gravity included; walking peaks near
 *   1.3 g, so 2.5 needs a deliberate shake (the common Android "shake to" value).
 * @property shakesNeeded jolts within [shakeWindowMs] that make one shake, so a bump in bed does not count.
 * @property shakeCooldownMs after a shake, jolts are ignored this long, so one vigorous shake counts once.
 * @property buzzMs the vibration that confirms a shake was felt.
 */
data class SleepConfig(
    val fadeOutMs: Long = DEFAULT_FADE_OUT_MS,
    val fadeStepMs: Long = DEFAULT_FADE_STEP_MS,
    val shakeThresholdG: Float = DEFAULT_SHAKE_THRESHOLD_G,
    val shakesNeeded: Int = DEFAULT_SHAKES_NEEDED,
    val shakeWindowMs: Long = DEFAULT_SHAKE_WINDOW_MS,
    val shakeCooldownMs: Long = DEFAULT_SHAKE_COOLDOWN_MS,
    val buzzMs: Long = DEFAULT_BUZZ_MS,
) {
    init {
        require(fadeOutMs >= 0) { "fadeOutMs must be >= 0, was $fadeOutMs" }
        require(fadeStepMs > 0) { "fadeStepMs must be > 0, was $fadeStepMs" }
        require(shakeThresholdG > 0f) { "shakeThresholdG must be > 0, was $shakeThresholdG" }
        require(shakesNeeded > 0) { "shakesNeeded must be > 0, was $shakesNeeded" }
        require(shakeWindowMs > 0 && shakeCooldownMs >= 0 && buzzMs >= 0) { "shake timings must not be negative" }
    }

    private companion object {
        const val DEFAULT_FADE_OUT_MS = 10_000L
        const val DEFAULT_FADE_STEP_MS = 50L
        const val DEFAULT_SHAKE_THRESHOLD_G = 2.5f
        const val DEFAULT_SHAKES_NEEDED = 3
        const val DEFAULT_SHAKE_WINDOW_MS = 1_500L
        const val DEFAULT_SHAKE_COOLDOWN_MS = 3_000L
        const val DEFAULT_BUZZ_MS = 150L
    }
}
