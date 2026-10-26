// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/focus/FocusNoise.kt
// Purpose: Makes brown noise for the focus sound (plan item 72) sample by sample; nature sounds are recordings (Q-1).

package io.github.skm0078.audora.playback.focus

import io.github.skm0078.audora.core.model.FocusSound

/** One mono sample at a time, about -1..1, at the sample rate it was made for. */
fun interface NoiseSource {
    fun next(): Float
}

/**
 * Brown noise is noise by definition, so made here it sounds the same as a recording of it. Rain and café used to be
 * made here too, from filtered noise; they sounded fake, and nature sounds are recorded loops now ([FocusLoops]).
 */
object FocusNoise {
    /** Brown noise for [FocusSound.BROWN_NOISE], else null. The same [seed] gives the same sound, for tests. */
    fun of(sound: FocusSound, sampleRate: Int, seed: Long): NoiseSource? {
        require(sampleRate > 0) { "sampleRate must be > 0, was $sampleRate" }
        if (sound != FocusSound.BROWN_NOISE) return null
        val brown = Brown(Xorshift(seed))
        return NoiseSource { brown.next() * BROWN_GAIN }
    }

    private const val BROWN_GAIN = 0.9f
}

/** A fast, seedable random source: no locking, no allocation (java.util.Random synchronizes every call). */
internal class Xorshift(seed: Long) {
    private var state = (seed xor SEED_MIX).let { if (it == 0L) SEED_MIX else it }

    fun nextLong(): Long {
        state = state xor (state shl SHIFT_A)
        state = state xor (state ushr SHIFT_B)
        state = state xor (state shl SHIFT_C)
        return state
    }

    /** Uniform in 0..1. */
    fun unit(): Float = (nextLong() ushr FLOAT_SHIFT).toFloat() / FLOAT_SCALE

    /** Uniform in -1..1. */
    fun white(): Float = unit() * 2f - 1f

    private companion object {
        const val SEED_MIX = -0x61c8864680b583ebL
        const val SHIFT_A = 13
        const val SHIFT_B = 7
        const val SHIFT_C = 17
        const val FLOAT_SHIFT = 40
        const val FLOAT_SCALE = (1 shl 24).toFloat()
    }
}

/** Integrated white noise with a leak, so it never drifts off: deep, like a distant waterfall. */
internal class Brown(private val random: Xorshift) {
    private var level = 0f

    fun next(): Float {
        level = (level + STEP * random.white()) / LEAK
        return level * GAIN
    }

    private companion object {
        const val STEP = 0.02f
        const val LEAK = 1.02f
        const val GAIN = 3.5f
    }
}
