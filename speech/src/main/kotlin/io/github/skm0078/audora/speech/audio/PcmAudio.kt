// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/audio/PcmAudio.kt
// Purpose: One rendered sentence as 16-bit mono PCM, plus the conversions engines need to produce it.

package io.github.skm0078.audora.speech.audio

import kotlin.math.roundToInt

/** 16-bit signed mono PCM. A class, not a data class: array equality by reference would mislead. */
class PcmAudio(val samples: ShortArray, val sampleRate: Int) {
    init {
        require(sampleRate > 0) { "sampleRate must be > 0, was $sampleRate" }
    }

    val durationMs: Long get() = samples.size * MS_PER_SECOND / sampleRate

    /**
     * The same sound at [rate] Hz, by linear interpolation. Meant for upsampling (joining voices of different
     * rates at the highest one); downsampling this way would alias.
     */
    fun resampled(rate: Int): PcmAudio {
        require(rate > 0) { "rate must be > 0, was $rate" }
        if (rate == sampleRate) return this
        val size = (samples.size.toLong() * rate / sampleRate).toInt()
        val last = samples.lastIndex
        val out = ShortArray(size) { i ->
            val position = i.toDouble() * sampleRate / rate
            val index = position.toInt()
            val fraction = position - index
            val a = samples[index]
            val b = samples[minOf(index + 1, last)]
            (a + (b - a) * fraction).roundToInt().toShort()
        }
        return PcmAudio(out, rate)
    }

    override fun toString(): String = "PcmAudio(${samples.size} samples @ $sampleRate Hz, $durationMs ms)"

    companion object {
        private const val MS_PER_SECOND = 1_000L
        private const val PCM16_MAX = Short.MAX_VALUE.toFloat()

        fun silence(sampleRate: Int): PcmAudio = PcmAudio(ShortArray(0), sampleRate)

        /** Converts float samples in [-1, 1] (sherpa-onnx's output) to 16-bit, clipping anything outside. */
        fun fromFloats(samples: FloatArray, sampleRate: Int): PcmAudio = PcmAudio(
            ShortArray(samples.size) { i -> (samples[i].coerceIn(-1f, 1f) * PCM16_MAX).roundToInt().toShort() },
            sampleRate,
        )

        /** Joins chunks rendered separately. They must share one sample rate; resampling is not this layer's job. */
        fun concat(parts: List<PcmAudio>): PcmAudio {
            require(parts.isNotEmpty()) { "Nothing to concatenate" }
            val rate = parts.first().sampleRate
            require(parts.all { it.sampleRate == rate }) {
                "Chunks have different sample rates: ${parts.map { it.sampleRate }.distinct()}"
            }
            if (parts.size == 1) return parts.first()
            val joined = ShortArray(parts.sumOf { it.samples.size })
            var offset = 0
            for (part in parts) {
                part.samples.copyInto(joined, offset)
                offset += part.samples.size
            }
            return PcmAudio(joined, rate)
        }
    }
}
