// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/focus/FocusNoise.kt
// Purpose: Generates the focus sounds (plan item 72) sample by sample: brown noise, rain and a café, no sound files.

package io.github.skm0078.audora.playback.focus

import io.github.skm0078.audora.core.model.FocusSound
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** One mono sample at a time, about -1..1, at the sample rate it was made for. */
fun interface NoiseSource {
    fun next(): Float
}

object FocusNoise {
    /** Null for [FocusSound.OFF]. The same [seed] gives the same sound, for tests. */
    fun of(sound: FocusSound, sampleRate: Int, seed: Long): NoiseSource? {
        require(sampleRate > 0) { "sampleRate must be > 0, was $sampleRate" }
        val random = Xorshift(seed)
        return when (sound) {
            FocusSound.OFF -> null
            FocusSound.BROWN_NOISE -> Brown(random).let { brown -> NoiseSource { brown.next() * BROWN_GAIN } }
            FocusSound.RAIN -> Rain(sampleRate, random)
            FocusSound.CAFE -> Cafe(sampleRate, random)
        }
    }

    private const val BROWN_GAIN = 0.9f
}

/** One full turn in radians. */
private const val TAU = 2.0 * PI

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

    fun between(from: Float, to: Float): Float = from + (to - from) * unit()

    private companion object {
        const val SEED_MIX = -0x61c8864680b583ebL
        const val SHIFT_A = 13
        const val SHIFT_B = 7
        const val SHIFT_C = 17
        const val FLOAT_SHIFT = 40
        const val FLOAT_SCALE = (1 shl 24).toFloat()
    }
}

/** First-order low-pass at [cutoffHz]; [high] gives the matching high-pass of the same input. */
internal class OnePole(cutoffHz: Float, sampleRate: Int) {
    private val alpha = 1f - exp(-TAU * cutoffHz / sampleRate).toFloat()
    private var low = 0f

    fun low(x: Float): Float {
        low += alpha * (x - low)
        return low
    }

    fun high(x: Float): Float = x - low(x)
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

/** Paul Kellet's economy pink filter: equal energy per octave, softer than white noise. */
internal class Pink(private val random: Xorshift) {
    private var b0 = 0f
    private var b1 = 0f
    private var b2 = 0f

    fun next(): Float {
        val white = random.white()
        b0 = P0 * b0 + white * W0
        b1 = P1 * b1 + white * W1
        b2 = P2 * b2 + white * W2
        return (b0 + b1 + b2 + white * W3) * GAIN
    }

    private companion object {
        const val P0 = 0.99765f
        const val P1 = 0.963f
        const val P2 = 0.57f
        const val W0 = 0.099046f
        const val W1 = 0.2965164f
        const val W2 = 1.0526913f
        const val W3 = 0.1848f
        const val GAIN = 0.2f
    }
}

/** Steady hiss of falling rain, a low rumble, and drops landing near by at random. */
private class Rain(sampleRate: Int, private val random: Xorshift) : NoiseSource {
    private val pink = Pink(random)
    private val brown = Brown(random)
    private val hiss = OnePole(HISS_FROM_HZ, sampleRate)
    private val rumble = OnePole(RUMBLE_TO_HZ, sampleRate)
    private val drop = OnePole(DROP_FROM_HZ, sampleRate)
    private val dropChance = DROPS_PER_SECOND / sampleRate
    private val dropDecay = exp(-1.0 / (sampleRate * DROP_SECONDS)).toFloat()
    private var dropLevel = 0f

    override fun next(): Float {
        if (random.unit() < dropChance) dropLevel = random.between(DROP_MIN, 1f)
        dropLevel *= dropDecay
        val sample = hiss.high(pink.next()) * HISS + rumble.low(brown.next()) * RUMBLE +
            drop.high(random.white()) * dropLevel * DROP
        return sample.coerceIn(-1f, 1f)
    }

    private companion object {
        const val HISS_FROM_HZ = 400f
        const val RUMBLE_TO_HZ = 200f
        const val DROP_FROM_HZ = 1_500f
        const val DROPS_PER_SECOND = 25f
        const val DROP_SECONDS = 0.004
        const val DROP_MIN = 0.2f
        const val HISS = 0.9f
        const val RUMBLE = 0.35f
        const val DROP = 0.3f
    }
}

/**
 * A room of people talking too far off to make out (speech-band noise that swells and fades with three slow
 * rhythms), the room's low hum, and now and then a cup or spoon.
 */
private class Cafe(sampleRate: Int, private val random: Xorshift) : NoiseSource {
    private val pink = Pink(random)
    private val brown = Brown(random)
    private val voicesFrom = OnePole(MURMUR_FROM_HZ, sampleRate)
    private val voicesTo = OnePole(MURMUR_TO_HZ, sampleRate)
    private val hum = OnePole(HUM_TO_HZ, sampleRate)
    private val swells = SWELL_HZ.map { hz -> Wave(hz / sampleRate, random.unit()) }
    private val clinkChance = CLINKS_PER_SECOND / sampleRate
    private val clinkDecay = exp(-1.0 / (sampleRate * CLINK_SECONDS)).toFloat()
    private val clinkTopHz = minOf(CLINK_TOP_HZ, sampleRate * NYQUIST_MARGIN)
    private val clink = Wave(0f, 0f)
    private val rate = sampleRate.toFloat()
    private var clinkLevel = 0f

    override fun next(): Float {
        var swell = SWELL_BASE
        for (i in swells.indices) swell += swells[i].next() * SWELL_DEPTH[i]
        val murmur = voicesTo.low(voicesFrom.high(pink.next())) * swell * MURMUR
        if (random.unit() < clinkChance) {
            clinkLevel = random.between(CLINK_MIN, 1f) * CLINK
            clink.step = random.between(CLINK_BOTTOM_HZ, clinkTopHz) / rate
        }
        clinkLevel *= clinkDecay
        val sample = murmur + hum.low(brown.next()) * HUM + clink.next() * clinkLevel
        return sample.coerceIn(-1f, 1f)
    }

    /** A sine with [step] cycles per sample, starting at [phase] (in cycles). */
    private class Wave(var step: Float, private var phase: Float) {
        fun next(): Float {
            phase = (phase + step) % 1f
            return sin(TAU * phase).toFloat()
        }
    }

    private companion object {
        const val MURMUR_FROM_HZ = 250f
        const val MURMUR_TO_HZ = 1_200f
        const val HUM_TO_HZ = 150f
        val SWELL_HZ = listOf(0.4f, 0.9f, 1.7f)
        val SWELL_DEPTH = floatArrayOf(0.25f, 0.15f, 0.1f)
        const val SWELL_BASE = 0.6f
        const val CLINKS_PER_SECOND = 0.3f
        const val CLINK_SECONDS = 0.06
        const val CLINK_BOTTOM_HZ = 2_000f
        const val CLINK_TOP_HZ = 4_500f
        const val NYQUIST_MARGIN = 0.45f
        const val CLINK_MIN = 0.3f
        const val CLINK = 0.08f
        const val MURMUR = 1.9f
        const val HUM = 0.3f
    }
}
