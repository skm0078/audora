// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/studio/LosslessPcm.kt
// Purpose: Lossless compression of 16-bit speech for studio mode: fixed linear prediction plus Rice codes (FLAC-style).

package io.github.skm0078.audora.playback.studio

/**
 * Studio audio must play exactly as the live voice would, so it is stored losslessly; speech from a TTS voice, with
 * its exact-zero silences, shrinks to about 40–50 % of raw PCM (about 60–80 MB per listening hour at 22–24 kHz).
 * Pure Kotlin, no codec or library (ADR-039), so it behaves the same on every phone and is tested on the JVM.
 *
 * The audio is cut into blocks of [blockSamples]; each block is the cheapest of:
 * - CONSTANT: one sample value (the silences after sentences);
 * - FIXED: FLAC's fixed predictor of order 0–3 (the order whose residuals are smallest), then each residual
 *   zigzag-mapped and Rice-coded with the parameter that codes the block in the fewest bits;
 * - VERBATIM: the raw samples, when prediction does not pay (noise).
 *
 * The sample count is not in the stream: the caller stores it ([StudioClip.sampleCount]).
 */
class LosslessPcm(private val blockSamples: Int) {
    init {
        require(blockSamples >= MAX_ORDER + 1) { "blockSamples must be > $MAX_ORDER, was $blockSamples" }
    }

    fun encode(samples: ShortArray): ByteArray {
        val out = BitWriter(samples.size)
        var start = 0
        while (start < samples.size) {
            val end = minOf(start + blockSamples, samples.size)
            encodeBlock(samples, start, end, out)
            start = end
        }
        return out.toByteArray()
    }

    /** Throws [CorruptStudioData] when [data] is not a stream of [count] samples this codec wrote. */
    fun decode(data: ByteArray, count: Int): ShortArray {
        require(count >= 0) { "count must be >= 0, was $count" }
        val input = BitReader(data)
        val samples = ShortArray(count)
        var start = 0
        while (start < count) {
            val end = minOf(start + blockSamples, count)
            decodeBlock(input, samples, start, end)
            start = end
        }
        return samples
    }

    private fun encodeBlock(samples: ShortArray, start: Int, end: Int, out: BitWriter) {
        val first = samples[start]
        if ((start until end).all { samples[it] == first }) {
            out.write(TYPE_CONSTANT, TYPE_BITS)
            out.write(first.toInt() and SAMPLE_MASK, SAMPLE_BITS)
            return
        }
        val length = end - start
        val order = bestOrder(samples, start, end)
        val residuals = IntArray(length - order) { i -> zigzag(residual(samples, start + order + i, order)) }
        val k = bestRiceParameter(residuals)
        val fixedBits = ORDER_BITS + order * SAMPLE_BITS + RICE_BITS + riceCost(residuals, k)
        if (fixedBits >= length.toLong() * SAMPLE_BITS) {
            out.write(TYPE_VERBATIM, TYPE_BITS)
            for (i in start until end) out.write(samples[i].toInt() and SAMPLE_MASK, SAMPLE_BITS)
            return
        }
        out.write(TYPE_FIXED, TYPE_BITS)
        out.write(order, ORDER_BITS)
        for (i in start until start + order) out.write(samples[i].toInt() and SAMPLE_MASK, SAMPLE_BITS)
        out.write(k, RICE_BITS)
        for (value in residuals) {
            out.writeUnary(value ushr k)
            out.write(value and ((1 shl k) - 1), k)
        }
    }

    private fun decodeBlock(input: BitReader, samples: ShortArray, start: Int, end: Int) {
        when (input.read(TYPE_BITS)) {
            TYPE_CONSTANT -> samples.fill(input.readSample(), start, end)

            TYPE_VERBATIM -> for (i in start until end) samples[i] = input.readSample()

            TYPE_FIXED -> {
                val order = input.read(ORDER_BITS)
                corruptUnless(order <= end - start) { "order $order in a block of ${end - start}" }
                for (i in start until start + order) samples[i] = input.readSample()
                val k = input.read(RICE_BITS)
                for (i in start + order until end) {
                    val value = (input.readUnary() shl k) or input.read(k)
                    val sample = prediction(samples, i, order) + unzigzag(value)
                    corruptUnless(sample in Short.MIN_VALUE..Short.MAX_VALUE) { "sample out of range" }
                    samples[i] = sample.toShort()
                }
            }

            else -> throw CorruptStudioData("unknown block type")
        }
    }

    /** The order 0–3 whose residuals have the smallest absolute sum (FLAC's estimate of the cheapest). */
    private fun bestOrder(samples: ShortArray, start: Int, end: Int): Int {
        val maxOrder = minOf(MAX_ORDER, end - start - 1)
        return (0..maxOrder).minBy { order ->
            var sum = 0L
            for (i in start + maxOrder until end) sum += kotlin.math.abs(residual(samples, i, order))
            sum
        }
    }

    private companion object {
        const val TYPE_BITS = 2
        const val TYPE_CONSTANT = 0
        const val TYPE_FIXED = 1
        const val TYPE_VERBATIM = 2
        const val ORDER_BITS = 2
        const val MAX_ORDER = 3
        const val RICE_BITS = 5
        const val MAX_RICE = 30
        const val SAMPLE_BITS = 16
        const val SAMPLE_MASK = 0xFFFF

        fun residual(samples: ShortArray, i: Int, order: Int): Int = samples[i] - prediction(samples, i, order)

        /** FLAC's fixed predictors: 0, s1, 2·s1 − s2, 3·s1 − 3·s2 + s3 (sN = the sample N before [i]). */
        @Suppress("MagicNumber") // The predictor coefficients themselves.
        fun prediction(samples: ShortArray, i: Int, order: Int): Int = when (order) {
            0 -> 0
            1 -> samples[i - 1].toInt()
            2 -> 2 * samples[i - 1] - samples[i - 2]
            else -> 3 * samples[i - 1] - 3 * samples[i - 2] + samples[i - 3]
        }

        fun zigzag(value: Int): Int = (value shl 1) xor (value shr (Int.SIZE_BITS - 1))

        fun unzigzag(value: Int): Int = (value ushr 1) xor -(value and 1)

        /** Bits to Rice-code [values] with parameter [k]: a unary quotient, its stop bit, then k low bits each. */
        fun riceCost(values: IntArray, k: Int): Long = values.sumOf { ((it ushr k) + 1 + k).toLong() }

        /** The parameter near log2 of the mean that codes [values] in the fewest bits, checked exactly. */
        fun bestRiceParameter(values: IntArray): Int {
            if (values.isEmpty()) return 0
            val mean = values.sumOf { it.toLong() } / values.size
            val estimate = if (mean <= 0) 0 else (Long.SIZE_BITS - 1 - mean.countLeadingZeroBits())
            val candidates = (estimate - 1).coerceAtLeast(0)..(estimate + 1).coerceAtMost(MAX_RICE)
            return candidates.minBy { riceCost(values, it) }
        }

        fun BitReader.readSample(): Short = read(SAMPLE_BITS).toShort()
    }
}

/** A studio file or stream that is not what Audora wrote: it is ignored and the sentence is rendered live. */
class CorruptStudioData(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Throws [CorruptStudioData] with [what] unless [condition] holds. */
internal inline fun corruptUnless(condition: Boolean, what: () -> String) {
    if (!condition) throw CorruptStudioData(what())
}

/** Most significant bit first. */
private class BitWriter(expectedSamples: Int) {
    private var bytes = ByteArray(maxOf(INITIAL_BYTES, expectedSamples))
    private var size = 0
    private var buffer = 0L
    private var buffered = 0

    fun write(value: Int, bits: Int) {
        if (bits == 0) return
        buffer = (buffer shl bits) or (value.toLong() and ((1L shl bits) - 1))
        buffered += bits
        while (buffered >= Byte.SIZE_BITS) {
            buffered -= Byte.SIZE_BITS
            put((buffer ushr buffered).toInt())
        }
    }

    /** [count] zeros, then a one. */
    fun writeUnary(count: Int) {
        var left = count
        while (left >= CHUNK) {
            write(0, CHUNK)
            left -= CHUNK
        }
        write(1, left + 1)
    }

    fun toByteArray(): ByteArray {
        if (buffered > 0) put((buffer shl (Byte.SIZE_BITS - buffered)).toInt())
        buffered = 0
        return bytes.copyOf(size)
    }

    private fun put(byte: Int) {
        if (size == bytes.size) bytes = bytes.copyOf(bytes.size * 2)
        bytes[size++] = byte.toByte()
    }

    private companion object {
        const val INITIAL_BYTES = 64
        const val CHUNK = 24
    }
}

private class BitReader(private val bytes: ByteArray) {
    private var position = 0L
    private val limit = bytes.size.toLong() * Byte.SIZE_BITS

    fun read(bits: Int): Int {
        corruptUnless(position + bits <= limit) { "stream ends early" }
        var value = 0
        repeat(bits) { value = (value shl 1) or bit() }
        return value
    }

    fun readUnary(): Int {
        var count = 0
        while (true) {
            corruptUnless(position < limit) { "stream ends early" }
            if (bit() == 1) return count
            corruptUnless(++count <= MAX_UNARY) { "quotient too long" }
        }
    }

    private fun bit(): Int {
        val byte = bytes[(position ushr BYTE_SHIFT).toInt()].toInt()
        val shift = Byte.SIZE_BITS - 1 - (position and BYTE_MASK).toInt()
        position++
        return (byte ushr shift) and 1
    }

    private companion object {
        const val BYTE_SHIFT = 3
        const val BYTE_MASK = 7L

        /** Larger than any zigzagged residual of 16-bit audio (order 3 stays under 2^20). */
        const val MAX_UNARY = 1 shl 21
    }
}
