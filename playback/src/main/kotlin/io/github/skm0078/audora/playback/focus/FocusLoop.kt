// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/focus/FocusLoop.kt
// Purpose: Plays a recorded focus-sound loop round and round (Q-1), and resamples it to the voice's rate once.

package io.github.skm0078.audora.playback.focus

import java.nio.ShortBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Plays [pcm] (mono, already at the voice's rate) from its start, round and round: the loops are cut so their end
 * flows into their start. [pcm] is null while the loop is still being decoded, and the source is silent until then;
 * when it arrives it fades in over [fadeFrames], so a sound chosen mid-sentence never starts with a jump. One audio
 * thread; [pcm] may be filled from another.
 */
class LoopSource(private val pcm: () -> ShortArray?, private val fadeFrames: Int) : NoiseSource {
    private var position = 0
    private var faded = 0

    init {
        require(fadeFrames >= 0) { "fadeFrames must be >= 0, was $fadeFrames" }
    }

    override fun next(): Float {
        val loop = pcm()?.takeIf { it.isNotEmpty() } ?: return 0f
        val sample = loop[position].toFloat() / Short.MAX_VALUE
        position = (position + 1) % loop.size
        if (faded < fadeFrames) faded++
        return if (fadeFrames == 0) sample else sample * faded / fadeFrames
    }
}

/**
 * Changes a loop's sample rate with a windowed-sinc filter, treating the loop as circular so the result still loops
 * without a click. Going down (48 kHz to 22.05 kHz), the filter first removes what the lower rate can't hold, which
 * would otherwise fold back as a hiss. Pure. The two rates' ratio repeats every few samples (48 kHz to 22.05 kHz:
 * every 147), so the filter's weights are worked out once per position in that cycle and each output sample is then
 * only multiplications: a minute of loop takes a fraction of a second. Runs once per loop and voice rate, on a
 * background thread.
 */
object FocusResampler {
    /** Filter taps each side of a sample at the lower rate; more is sharper and slower. */
    const val HALF_TAPS = 12

    /** The filter's cut-off as a share of the lower rate's Nyquist frequency: a little below it, for the slope. */
    const val CUTOFF = 0.92

    /** More positions in the rates' cycle than this (two odd rates) and the weights are not worth keeping. */
    private const val MAX_PHASES = 4_096

    fun resample(input: ShortArray, fromRate: Int, toRate: Int): ShortArray {
        require(fromRate > 0 && toRate > 0) { "rates must be > 0, were $fromRate and $toRate" }
        if (fromRate == toRate || input.isEmpty()) return input.copyOf()
        val common = gcd(fromRate, toRate)
        val phases = toRate / common
        require(phases <= MAX_PHASES) { "rates $fromRate and $toRate have no short common cycle" }
        val stride = (fromRate / common).toLong()
        val band = CUTOFF * minOf(1.0, toRate.toDouble() / fromRate)
        val reach = ceil(HALF_TAPS / band).toInt()
        val weights = Array(phases) { phase -> weightsAt(phase.toDouble() / phases, band, reach) }
        val outSize = (input.size.toLong() * toRate / fromRate).toInt()
        return ShortArray(outSize) { j ->
            val position = j * stride
            val centre = (position / phases).toInt()
            val taps = weights[(position % phases).toInt()]
            var sum = 0.0
            var k = Math.floorMod(centre - reach + 1, input.size)
            for (weight in taps) {
                sum += input[k] * weight
                k = if (k + 1 == input.size) 0 else k + 1
            }
            sum.roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    /**
     * The filter's weights for an output sample [fraction] of the way past an input sample, for the inputs from
     * [reach] − 1 before it to [reach] after. Normalised to sum to 1, so the level stays exact at every position.
     */
    private fun weightsAt(fraction: Double, band: Double, reach: Int): DoubleArray {
        val weights = DoubleArray(2 * reach) { t -> kernel(fraction + reach - 1 - t, band, reach) }
        val total = weights.sum()
        for (t in weights.indices) weights[t] /= total
        return weights
    }

    /** A Hann-windowed sinc low-pass at [band] (share of the input's Nyquist), [reach] input samples each side. */
    private fun kernel(distance: Double, band: Double, reach: Int): Double {
        val x = PI * distance
        return when {
            abs(distance) >= reach -> 0.0
            distance == 0.0 -> band
            else -> sin(band * x) / x * (HANN_HALF + HANN_HALF * cos(PI * distance / reach))
        }
    }

    private tailrec fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

    private const val HANN_HALF = 0.5
}

/** A growing mono PCM buffer: frames of [channels] interleaved samples are averaged into one. */
internal class PcmBuilder {
    private var pcm = ShortArray(INITIAL)
    private var size = 0

    fun addFrames(samples: ShortBuffer, channels: Int) {
        require(channels > 0) { "channels must be > 0, was $channels" }
        val frames = samples.remaining() / channels
        if (size + frames > pcm.size) pcm = pcm.copyOf(maxOf(pcm.size * 2, size + frames))
        repeat(frames) {
            var sum = 0
            repeat(channels) { sum += samples.get() }
            pcm[size++] = (sum / channels).toShort()
        }
    }

    fun build(): ShortArray = pcm.copyOf(size)

    private companion object {
        const val INITIAL = 1 shl 16
    }
}
