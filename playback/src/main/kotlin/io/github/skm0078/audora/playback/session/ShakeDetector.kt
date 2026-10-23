// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/session/ShakeDetector.kt
// Purpose: Tells a deliberate shake from bumps in accelerometer samples, for "shake to extend" (plan item 37).

package io.github.skm0078.audora.playback.session

import io.github.skm0078.audora.core.config.SleepConfig
import kotlin.math.sqrt

/** Pure; fed by the service's accelerometer listener. Not thread-safe: one sensor thread. */
class ShakeDetector(private val config: SleepConfig = SleepConfig()) {
    private val jolts = ArrayDeque<Long>()
    private var quietUntil = Long.MIN_VALUE

    /** One sample in m/s² at [atMillis]; true when it completes a shake ([SleepConfig.shakesNeeded] jolts). */
    fun onSample(x: Float, y: Float, z: Float, atMillis: Long): Boolean {
        val g = sqrt(x * x + y * y + z * z) / STANDARD_GRAVITY
        if (atMillis < quietUntil || g < config.shakeThresholdG) return false
        jolts.addLast(atMillis)
        while (jolts.first() < atMillis - config.shakeWindowMs) jolts.removeFirst()
        val shaken = jolts.size >= config.shakesNeeded
        if (shaken) {
            jolts.clear()
            quietUntil = atMillis + config.shakeCooldownMs
        }
        return shaken
    }

    private companion object {
        /** m/s² in one g (`SensorManager.GRAVITY_EARTH`, kept here so this stays plain Kotlin). */
        const val STANDARD_GRAVITY = 9.80665f
    }
}
