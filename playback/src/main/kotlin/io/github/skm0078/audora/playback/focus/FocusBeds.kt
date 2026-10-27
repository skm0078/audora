// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/focus/FocusBeds.kt
// Purpose: Where each focus sound (plan item 72, Q-1) comes from: a recorded loop, or brown noise made on the phone.

package io.github.skm0078.audora.playback.focus

import io.github.skm0078.audora.core.model.FocusSound

/**
 * Gives [FocusMixer] the sound to mix, at the voice's sample rate. Null for [FocusSound.OFF], and for a recorded sound
 * this source can't play (the mixer then stays silent rather than play something else).
 */
fun interface FocusBeds {
    fun of(sound: FocusSound, sampleRate: Int, seed: Long): NoiseSource?

    companion object {
        /** Only what is made on the phone: brown noise. Recorded sounds need [FocusLoops]. */
        val GENERATED = FocusBeds { sound, sampleRate, seed -> FocusNoise.of(sound, sampleRate, seed) }
    }
}

/** The recorded focus sounds and their loops in the app's assets (Q-1): CC0 field recordings, see CREDITS.md there. */
object FocusLoopAssets {
    const val FOLDER = "focus"

    private val RECORDED = mapOf(
        FocusSound.RAIN to "rain",
        FocusSound.FOREST_RAIN to "forest-rain",
        FocusSound.STREAM to "stream",
        FocusSound.BROOK to "brook",
        FocusSound.RIVER to "river",
        FocusSound.FOREST_BIRDS to "forest-birds",
        FocusSound.SPRING_BIRDS to "spring-birds",
        FocusSound.FIREPLACE to "fireplace",
        FocusSound.WIND to "wind",
    )

    /** The asset path of [sound]'s loop, or null for a sound made on the phone (or none). */
    fun pathOf(sound: FocusSound): String? = RECORDED[sound]?.let { "$FOLDER/$it.ogg" }
}
