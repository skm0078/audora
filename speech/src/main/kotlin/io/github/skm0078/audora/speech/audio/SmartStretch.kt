// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/audio/SmartStretch.kt
// Purpose: Smart speed (plan item 10, ADR-049): speeds a voice up unevenly, sparing consonants, keeping its pitch.

package io.github.skm0078.audora.speech.audio

import io.github.skm0078.audora.core.config.SmartSpeedConfig
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Pure and stateless, one voiced piece at a time. Each frame of the voice is judged silent (relative to the piece's
 * loud level), steady (a held vowel, a hum, a hiss) or changing (an onset or release, where consonants are, with a
 * guard frame either side); [frameSpeeds] then gives every frame the same floor of extra speed and spreads the rest
 * by those kinds, so the piece comes out exactly [stretch]'s factor shorter. A WSOLA time-stretch follows that speed
 * map: each 20 ms window is taken from where the map says, nudged within the tolerance to line up with the waveform
 * already written, and overlap-added, so pitch is kept and no click appears; the piece's end fades out.
 */
class SmartStretch(private val config: SmartSpeedConfig = SmartSpeedConfig()) {

    /** [audio] played [factor] times faster; itself when [factor] is too close to 1 or the audio is too short. */
    fun stretch(audio: PcmAudio, factor: Float): PcmAudio {
        val window = msToSamples(config.windowMs, audio.sampleRate) / 2 * 2
        val tooShort = window < 2 || audio.samples.size < 2 * window
        if (factor <= config.minFactor || !factor.isFinite() || tooShort) return audio
        val frame = msToSamples(config.frameMs, audio.sampleRate).coerceAtLeast(1)
        val lengths = frameLengths(audio.samples.size, frame)
        val speeds = frameSpeeds(frameWeights(audio.samples, frame), factor, lengths)
        val map = SpeedMap(speeds, frame, audio.samples.size)
        val wsola = Wsola(
            input = audio.samples,
            window = window,
            tolerance = msToSamples(config.toleranceMs, audio.sampleRate),
            step = config.searchStep,
            floorDbfs = config.alignFloorDbfs,
        )
        return PcmAudio(fadeOut(wsola.run(map), msToSamples(config.endFadeMs, audio.sampleRate)), audio.sampleRate)
    }

    /** How much of the extra speed each frame of [samples] takes: silence most, onsets (and their guard) least. */
    internal fun frameWeights(samples: ShortArray, frame: Int): FloatArray {
        val count = (samples.size + frame - 1) / frame
        val levels = FloatArray(count) { f -> levelDbfs(samples, f * frame, minOf((f + 1) * frame, samples.size)) }
        val quiet = loudLevel(levels) - config.silenceBelowLoudDb
        val kinds = Array(count) { f ->
            when {
                levels[f] < quiet -> Kind.SILENT
                isTransient(levels, f) -> Kind.CHANGING
                else -> Kind.STEADY
            }
        }
        val guard = config.onsetGuardFrames
        return FloatArray(count) { f ->
            val near = maxOf(0, f - guard)..minOf(count - 1, f + guard)
            val guarded = kinds[f] == Kind.STEADY && near.any { kinds[it] == Kind.CHANGING }
            weightOf(if (guarded) Kind.CHANGING else kinds[f])
        }
    }

    /**
     * Each frame's speed, `floor + k × weight` clamped to [SmartSpeedConfig.maxFrameSpeed], with k found so the frames
     * together last exactly 1/[factor] of their time ([lengths]: each frame's samples; the last one may be short).
     * The floor is [SmartSpeedConfig.consonantShare] of the extra speed. When no k gets there (the weights are all
     * zero, or the clamp is reached), every frame takes [factor].
     */
    internal fun frameSpeeds(
        weights: FloatArray,
        factor: Float,
        lengths: IntArray = IntArray(weights.size) { 1 },
    ): FloatArray {
        val floor = 1f + (factor - 1f) * config.consonantShare
        val target = lengths.sum() / factor.toDouble()
        val minWeight = smallestPositive(weights)
        val kMax = if (minWeight > 0f) (config.maxFrameSpeed - floor) / minWeight else 0f
        if (kMax <= 0f || duration(weights, lengths, floor, kMax) > target) {
            return FloatArray(weights.size) { factor }
        }
        var low = 0f
        var high: Float = kMax
        repeat(BISECTION_STEPS) {
            val mid = (low + high) / 2
            if (duration(weights, lengths, floor, mid) > target) low = mid else high = mid
        }
        return FloatArray(weights.size) { speedOf(weights[it], floor, high) }
    }

    private enum class Kind { SILENT, STEADY, CHANGING }

    private fun weightOf(kind: Kind): Float = when (kind) {
        Kind.SILENT -> config.silenceWeight
        Kind.STEADY -> config.steadyWeight
        Kind.CHANGING -> config.transientWeight
    }

    /** The frame levels' value at [SmartSpeedConfig.loudPercentile]. */
    private fun loudLevel(levels: FloatArray): Float {
        val ranked = levels.sortedArray()
        return ranked[(config.loudPercentile * ranked.size).toInt().coerceAtMost(ranked.lastIndex)]
    }

    private fun duration(weights: FloatArray, lengths: IntArray, floor: Float, k: Float): Double {
        var total = 0.0
        for (f in weights.indices) total += lengths[f] / speedOf(weights[f], floor, k).toDouble()
        return total
    }

    private fun speedOf(weight: Float, floor: Float, k: Float): Float =
        (floor + k * weight).coerceAtMost(config.maxFrameSpeed)

    private fun isTransient(levels: FloatArray, f: Int): Boolean {
        val before = if (f > 0) abs(levels[f] - levels[f - 1]) else 0f
        val after = if (f < levels.lastIndex) abs(levels[f + 1] - levels[f]) else 0f
        return maxOf(before, after) > config.transientDb
    }

    private companion object {
        const val BISECTION_STEPS = 40
        const val MS_PER_SECOND = 1_000
        const val PCM16_FULL_SCALE = 32_768.0
        const val FLOOR_DBFS = -120f
        const val POWER_TO_DB = 10f

        fun msToSamples(ms: Int, sampleRate: Int): Int = (ms.toLong() * sampleRate / MS_PER_SECOND).toInt()

        fun frameLengths(size: Int, frame: Int): IntArray =
            IntArray((size + frame - 1) / frame) { f -> minOf(frame, size - f * frame) }

        /** The smallest weight above zero, or 0 when there is none (no boxing, unlike filter + min). */
        fun smallestPositive(weights: FloatArray): Float {
            var smallest = Float.MAX_VALUE
            for (w in weights) if (w > 0f && w < smallest) smallest = w
            return if (smallest == Float.MAX_VALUE) 0f else smallest
        }

        /** Fades the last [samples] linearly to exactly zero, in place. */
        fun fadeOut(audio: ShortArray, samples: Int): ShortArray {
            val fade = minOf(samples, audio.size)
            for (j in 0 until fade) {
                val i = audio.size - fade + j
                audio[i] = (audio[i] * (fade - 1 - j).toFloat() / fade).roundToInt().toShort()
            }
            return audio
        }

        fun levelDbfs(samples: ShortArray, start: Int, end: Int): Float {
            if (end <= start) return FLOOR_DBFS
            var energy = 0.0
            for (i in start until end) energy += samples[i].toDouble() * samples[i]
            val meanSquare = energy / (end - start) / (PCM16_FULL_SCALE * PCM16_FULL_SCALE)
            return if (meanSquare <= 0.0) FLOOR_DBFS else (POWER_TO_DB * log10(meanSquare)).toFloat()
        }
    }
}

/** Where in the input each output sample comes from, given each input frame's speed. */
internal class SpeedMap(private val speeds: FloatArray, private val frame: Int, private val inputSize: Int) {
    /** Output samples before each frame starts; the last entry is the output's length. */
    private val starts = DoubleArray(speeds.size + 1).also { out ->
        for (f in speeds.indices) out[f + 1] = out[f] + frameLength(f) / speeds[f]
    }

    val outputSize: Int = starts.last().roundToInt()

    /** The input position output sample [at] is read from. */
    fun inputAt(at: Double): Double {
        var low = 0
        var high = speeds.size - 1
        while (low < high) {
            val mid = (low + high + 1) / 2
            if (starts[mid] <= at) low = mid else high = mid - 1
        }
        return (low.toDouble() * frame + (at - starts[low]) * speeds[low]).coerceIn(0.0, inputSize.toDouble())
    }

    private fun frameLength(f: Int): Int = minOf(frame, inputSize - f * frame)
}

/**
 * Waveform-similarity overlap-add with a periodic Hann window at 50% overlap (the windows sum to one). Windows are
 * lined up by normalised correlation (so a rising level does not pull them toward louder candidates), searched outward
 * from the nominal place so a tie keeps it, and a window too quiet to line up stays at its nominal place.
 */
private class Wsola(
    private val input: ShortArray,
    private val window: Int,
    private val tolerance: Int,
    private val step: Int,
    floorDbfs: Float,
) {
    private val hop = window / 2
    private val hann = FloatArray(window) { j -> (HALF - HALF * cos(2 * PI * j / window)).toFloat() }

    /** The energy of one hop at the floor's level, in squared PCM units. */
    private val floorEnergy = DB_BASE.pow(floorDbfs / POWER_TO_DB) * PCM16_FULL_SCALE * PCM16_FULL_SCALE * hop

    fun run(map: SpeedMap): ShortArray {
        val size = map.outputSize
        val out = FloatArray(size + window)
        var previous = -1
        var at = 0
        while (at < size) {
            val nominal = map.inputAt(at.toDouble()).roundToInt()
            val start = if (previous < 0) nominal else aligned(nominal, previous + hop)
            add(out, at, start, firstWindow = previous < 0)
            previous = start
            at += hop
        }
        return ShortArray(size) { i -> out[i].roundToInt().coerceIn(SHORT_MIN, SHORT_MAX).toShort() }
    }

    /** The start near [nominal] whose first half best matches the input that naturally follows the last window. */
    private fun aligned(nominal: Int, natural: Int): Int {
        val reference = energy(natural)
        if (reference < floorEnergy) return nominal
        var best = nominal
        var bestScore = similarity(nominal, natural, reference)
        var offset = step
        while (offset <= tolerance) {
            for (sign in SIGNS) {
                val candidate = nominal + sign * offset
                val score = similarity(candidate, natural, reference)
                if (score > bestScore) {
                    bestScore = score
                    best = candidate
                }
            }
            offset += step
        }
        return best
    }

    /** Normalised correlation of one hop at [a] with the one at [b]; minus infinity when [a] is below the floor. */
    private fun similarity(a: Int, b: Int, energyB: Double): Double {
        val energyA = energy(a)
        if (energyA < floorEnergy) return Double.NEGATIVE_INFINITY
        var sum = 0.0
        for (j in 0 until hop) sum += sample(a + j).toDouble() * sample(b + j)
        return sum / sqrt(energyA * energyB)
    }

    private fun energy(start: Int): Double {
        var sum = 0.0
        for (j in 0 until hop) sum += sample(start + j).toDouble() * sample(start + j)
        return sum
    }

    /** The first window keeps its rising half at full level, so the piece does not fade in. */
    private fun add(out: FloatArray, at: Int, start: Int, firstWindow: Boolean) {
        for (j in 0 until window) {
            val weight = if (firstWindow && j < hop) 1f else hann[j]
            out[at + j] += sample(start + j) * weight
        }
    }

    private fun sample(i: Int): Float = if (i in input.indices) input[i].toFloat() else 0f

    private companion object {
        const val HALF = 0.5
        const val SHORT_MIN = Short.MIN_VALUE.toInt()
        const val SHORT_MAX = Short.MAX_VALUE.toInt()
        const val DB_BASE = 10.0
        const val POWER_TO_DB = 10.0
        const val PCM16_FULL_SCALE = 32_768.0

        /** Earlier first, then later: the same order at every offset. */
        val SIGNS = intArrayOf(-1, 1)
    }
}
