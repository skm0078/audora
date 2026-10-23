// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/session/ShakeSource.kt
// Purpose: Where shakes come from for "shake to extend" (plan item 37); the service's is the accelerometer.

package io.github.skm0078.audora.playback.session

/** Listened to only while a sleep timer runs with shake on, so the sensor costs nothing otherwise. Main thread. */
interface ShakeSource {
    /** Starts calling [onShake] (on the main thread) for each shake; a second start replaces the first. */
    fun start(onShake: () -> Unit)

    fun stop()

    companion object {
        val NONE = object : ShakeSource {
            override fun start(onShake: () -> Unit) = Unit

            override fun stop() = Unit
        }
    }
}
