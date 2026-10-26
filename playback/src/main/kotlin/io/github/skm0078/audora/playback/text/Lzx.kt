// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/text/Lzx.kt
// Purpose: The LZX decompressor a CHM file's compressed section needs (ADR 2026-10-26-more-ebook-formats).

package io.github.skm0078.audora.playback.text

/**
 * Microsoft's LZX as CHM files use it: the output comes in frames of [FRAME_SIZE] bytes, and each frame's compressed
 * bytes start on their own (the CHM reset table says where), so [decompressFrame] reads one frame with a fresh bit
 * reader while the window, the repeated offsets and the Huffman lengths carry over until [reset]. Written from
 * Microsoft's LZX DS format notes and checked against libmspack on the test CHM; pure Kotlin, so tested on the JVM.
 * Damaged input throws [LzxException], never reads outside its arrays.
 */
@Suppress("TooManyFunctions") // One decoder's state machine, split by the part of a block each function reads.
internal class Lzx(windowBits: Int) {
    private val windowSize: Int
    private val window: ByteArray
    private val mainElements: Int
    private val mainLengths: IntArray
    private val lengthLengths = IntArray(LENGTH_SYMBOLS)
    private val alignedLengths = IntArray(ALIGNED_SYMBOLS)
    private var windowPosition = 0
    private var r0 = 1
    private var r1 = 1
    private var r2 = 1
    private var headerRead = false
    private var blockType = BLOCK_INVALID
    private var blockLength = 0
    private var blockRemaining = 0
    private var intelFileSize = 0
    private var intelCurrent = 0
    private var intelStarted = false
    private var framesRead = 0
    private var main = Huffman.EMPTY
    private var lengths = Huffman.EMPTY
    private var aligned = Huffman.EMPTY

    init {
        if (windowBits !in MIN_WINDOW_BITS..MAX_WINDOW_BITS) throw LzxException("window of 2^$windowBits bytes")
        windowSize = 1 shl windowBits
        window = ByteArray(windowSize)
        val slots = when (windowBits) {
            MAX_WINDOW_BITS -> SLOTS_21
            MAX_WINDOW_BITS - 1 -> SLOTS_20
            else -> windowBits * 2
        }
        mainElements = CHARS + slots * LENGTH_STATES
        mainLengths = IntArray(mainElements)
    }

    /** Back to the start of a reset interval: the window, offsets and code lengths start over. */
    fun reset() {
        r0 = 1
        r1 = 1
        r2 = 1
        headerRead = false
        framesRead = 0
        blockRemaining = 0
        blockType = BLOCK_INVALID
        intelCurrent = 0
        intelStarted = false
        windowPosition = 0
        mainLengths.fill(0)
        lengthLengths.fill(0)
    }

    /** [outLength] bytes of output from [input]'s bytes [from] until [to] (one frame's compressed data). */
    fun decompressFrame(input: ByteArray, from: Int, to: Int, outLength: Int): ByteArray {
        lzxCheck(outLength in 1..FRAME_SIZE && from in 0..to && to <= input.size) { "bad frame bounds" }
        val bits = LzxBits(input, from, to)
        if (!headerRead) {
            intelFileSize = if (bits.read(1) == 1) (bits.read(WORD) shl WORD) or bits.read(WORD) else 0
            headerRead = true
        }
        var togo = outLength
        while (togo > 0) {
            if (blockRemaining == 0) startBlock(bits)
            val run = minOf(blockRemaining, togo)
            togo -= run
            blockRemaining -= run
            decodeRun(bits, run)
        }
        val end = if (windowPosition == 0) windowSize else windowPosition
        val out = window.copyOfRange(end - outLength, end)
        translateIntel(out)
        return out
    }

    private fun startBlock(bits: LzxBits) {
        if (blockType == BLOCK_UNCOMPRESSED) {
            if (blockLength and 1 != 0) bits.skipRawByte()
            bits.restart()
        }
        blockType = bits.read(BLOCK_TYPE_BITS)
        blockLength = (bits.read(WORD) shl BLOCK_LENGTH_LOW_BITS) or bits.read(BLOCK_LENGTH_LOW_BITS)
        blockRemaining = blockLength
        when (blockType) {
            BLOCK_ALIGNED -> {
                for (i in 0 until ALIGNED_SYMBOLS) alignedLengths[i] = bits.read(ALIGNED_LENGTH_BITS)
                aligned = Huffman(alignedLengths)
                readTrees(bits)
            }

            BLOCK_VERBATIM -> readTrees(bits)

            BLOCK_UNCOMPRESSED -> {
                intelStarted = true
                bits.alignToBytes()
                r0 = bits.rawInt()
                r1 = bits.rawInt()
                r2 = bits.rawInt()
            }

            else -> throw LzxException("block type $blockType")
        }
    }

    private fun readTrees(bits: LzxBits) {
        readLengths(bits, mainLengths, 0, CHARS)
        readLengths(bits, mainLengths, CHARS, mainElements)
        main = Huffman(mainLengths)
        if (mainLengths[INTEL_E8] != 0) intelStarted = true
        readLengths(bits, lengthLengths, 0, LENGTH_SYMBOLS)
        lengths = Huffman(lengthLengths)
    }

    /** Code lengths [first] until [last], each coded against its last value by a 20-symbol pretree. */
    private fun readLengths(bits: LzxBits, lens: IntArray, first: Int, last: Int) {
        val pretree = Huffman(IntArray(PRETREE_SYMBOLS) { bits.read(PRETREE_LENGTH_BITS) })
        var x = first
        while (x < last) {
            when (val z = pretree.decode(bits)) {
                ZEROS_SHORT -> repeat(bits.read(ZEROS_SHORT_BITS) + ZEROS_SHORT_BASE) { lens.setIn(x++, last, 0) }

                ZEROS_LONG -> repeat(bits.read(ZEROS_LONG_BITS) + ZEROS_LONG_BASE) { lens.setIn(x++, last, 0) }

                SAME_RUN -> {
                    val count = bits.read(1) + SAME_RUN_BASE
                    if (x >= last) throw LzxException("a length run past the table")
                    val value = Math.floorMod(lens[x] - pretree.decode(bits), DELTA_MODULUS)
                    repeat(count) { lens.setIn(x++, last, value) }
                }

                else -> {
                    lens[x] = Math.floorMod(lens[x] - z, DELTA_MODULUS)
                    x++
                }
            }
        }
    }

    private fun IntArray.setIn(index: Int, last: Int, value: Int) {
        if (index >= last) throw LzxException("a length run past the table")
        this[index] = value
    }

    /** One literal or match into the window; returns how many bytes it wrote. */
    private fun decodeOne(bits: LzxBits): Int {
        val symbol = main.decode(bits)
        if (symbol < CHARS) {
            window[windowPosition++] = symbol.toByte()
            return 1
        }
        val element = symbol - CHARS
        var matchLength = element and PRIMARY_LENGTHS
        if (matchLength == PRIMARY_LENGTHS) matchLength += lengths.decode(bits)
        matchLength += MIN_MATCH
        val offset = matchOffset(bits, element shr LENGTH_STATE_BITS)
        copyMatch(offset, matchLength)
        return matchLength
    }

    private fun matchOffset(bits: LzxBits, slot: Int): Int = when (slot) {
        0 -> r0

        1 -> r1.also {
            r1 = r0
            r0 = it
        }

        2 -> r2.also {
            r2 = r0
            r0 = it
        }

        else -> {
            val offset = if (blockType == BLOCK_ALIGNED) alignedOffset(bits, slot) else verbatimOffset(bits, slot)
            r2 = r1
            r1 = r0
            r0 = offset
            offset
        }
    }

    private fun verbatimOffset(bits: LzxBits, slot: Int): Int = POSITION_BASE[slot] - 2 + bits.read(EXTRA_BITS[slot])

    private fun alignedOffset(bits: LzxBits, slot: Int): Int {
        val extra = EXTRA_BITS[slot]
        val base = POSITION_BASE[slot] - 2
        return when {
            extra > ALIGNED_BITS ->
                base + (bits.read(extra - ALIGNED_BITS) shl ALIGNED_BITS) + aligned.decode(bits)

            extra == ALIGNED_BITS -> base + aligned.decode(bits)

            extra > 0 -> base + bits.read(extra)

            else -> 1
        }
    }

    private fun copyMatch(offset: Int, length: Int) {
        if (offset <= 0 || offset > windowSize) throw LzxException("match offset $offset")
        if (windowPosition + length > windowSize) throw LzxException("a match crosses the window's end")
        var source = windowPosition - offset
        if (source < 0) source += windowSize
        repeat(length) {
            window[windowPosition++] = window[source]
            source = (source + 1) and (windowSize - 1)
        }
    }

    /** [run] bytes of the current block into the window. */
    private fun decodeRun(bits: LzxBits, run: Int) {
        windowPosition = windowPosition and (windowSize - 1)
        lzxCheck(windowPosition + run <= windowSize) { "a run crosses the window's end" }
        when (blockType) {
            BLOCK_VERBATIM, BLOCK_ALIGNED -> {
                var left = run
                while (left > 0) left -= decodeOne(bits)
                lzxCheck(left == 0) { "a match runs past its block" }
            }

            BLOCK_UNCOMPRESSED -> {
                bits.copyRaw(window, windowPosition, run)
                windowPosition += run
            }

            else -> throw LzxException("block type $blockType")
        }
    }

    /** Undoes the x86 CALL translation an encoder may have applied (never in CHM text in practice). */
    @Suppress("MagicNumber") // The E8 call's 32-bit little-endian operand.
    private fun translateIntel(out: ByteArray) {
        val frame = framesRead++
        if (frame >= INTEL_MAX_FRAMES || intelFileSize == 0) return
        if (out.size <= INTEL_MIN_FRAME || !intelStarted) {
            intelCurrent += out.size
            return
        }
        var current = intelCurrent
        intelCurrent += out.size
        var i = 0
        val end = out.size - INTEL_TAIL
        while (i < end) {
            if (out[i++] != INTEL_E8.toByte()) {
                current++
                continue
            }
            val absolute = (out[i].toInt() and BYTE) or ((out[i + 1].toInt() and BYTE) shl 8) or
                ((out[i + 2].toInt() and BYTE) shl 16) or ((out[i + 3].toInt() and BYTE) shl 24)
            if (absolute >= -current && absolute < intelFileSize) {
                val relative = if (absolute >= 0) absolute - current else absolute + intelFileSize
                for (k in 0 until 4) out[i + k] = (relative shr (8 * k)).toByte()
            }
            i += 4
            current += INTEL_CALL_SIZE
        }
    }

    companion object {
        const val FRAME_SIZE = 0x8000
        const val MIN_WINDOW_BITS = 15
        const val MAX_WINDOW_BITS = 21
        private const val SLOTS_20 = 42
        private const val SLOTS_21 = 50
        private const val CHARS = 256
        private const val LENGTH_STATES = 8
        private const val LENGTH_STATE_BITS = 3
        private const val PRIMARY_LENGTHS = 7
        private const val MIN_MATCH = 2
        private const val LENGTH_SYMBOLS = 249
        private const val ALIGNED_SYMBOLS = 8
        private const val ALIGNED_BITS = 3
        private const val ALIGNED_LENGTH_BITS = 3
        private const val PRETREE_SYMBOLS = 20
        private const val PRETREE_LENGTH_BITS = 4
        private const val ZEROS_SHORT = 17
        private const val ZEROS_SHORT_BASE = 4
        private const val ZEROS_SHORT_BITS = 4
        private const val ZEROS_LONG = 18
        private const val ZEROS_LONG_BASE = 20
        private const val ZEROS_LONG_BITS = 5
        private const val SAME_RUN = 19
        private const val SAME_RUN_BASE = 4
        private const val DELTA_MODULUS = 17
        private const val BLOCK_INVALID = 0
        private const val BLOCK_VERBATIM = 1
        private const val BLOCK_ALIGNED = 2
        private const val BLOCK_UNCOMPRESSED = 3
        private const val BLOCK_TYPE_BITS = 3
        private const val BLOCK_LENGTH_LOW_BITS = 8
        private const val WORD = 16
        private const val INTEL_E8 = 0xE8
        private const val INTEL_MAX_FRAMES = 32768
        private const val INTEL_MIN_FRAME = 6
        private const val INTEL_TAIL = 10
        private const val INTEL_CALL_SIZE = 5
        private const val BYTE = 0xFF
        private const val SLOT_TABLE_SIZE = 52
        private const val MAX_EXTRA_BITS = 17

        /** Extra bits per position slot: 0, 0, 0, 0, 1, 1, 2, 2, ... up to 17. */
        private val EXTRA_BITS = IntArray(SLOT_TABLE_SIZE).also { bits ->
            var j = 0
            for (i in 0 until SLOT_TABLE_SIZE step 2) {
                bits[i] = j
                bits[i + 1] = j
                if (i != 0 && j < MAX_EXTRA_BITS) j++
            }
        }
        private val POSITION_BASE = IntArray(SLOT_TABLE_SIZE).also { base ->
            for (i in 1 until SLOT_TABLE_SIZE) base[i] = base[i - 1] + (1 shl EXTRA_BITS[i - 1])
        }
    }
}

internal class LzxException(message: String) : RuntimeException(message)

/** Throws [LzxException] with [message] unless [valid]. */
internal inline fun lzxCheck(valid: Boolean, message: () -> String) {
    if (!valid) throw LzxException(message())
}

/**
 * LZX's bit order: 16-bit little-endian words, read from their top bit. Past the end it reads zeros, as the reference
 * decoder tolerates a final code that ends inside the padding; more than one word past it is damage.
 */
internal class LzxBits(private val input: ByteArray, private var position: Int, private val end: Int) {
    private var buffer = 0L
    private var count = 0

    fun read(n: Int): Int {
        if (n == 0) return 0
        ensure(n)
        val value = ((buffer ushr (count - n)) and ((1L shl n) - 1)).toInt()
        count -= n
        return value
    }

    /** The next [n] bits without taking them, for Huffman decoding. */
    fun peek(n: Int): Int {
        ensure(n)
        return ((buffer ushr (count - n)) and ((1L shl n) - 1)).toInt()
    }

    fun skip(n: Int) {
        count -= n
    }

    private fun ensure(n: Int) {
        while (count < n) {
            if (position >= end + 2) throw LzxException("input ends inside a frame")
            val low = if (position < end) input[position].toInt() and BYTE else 0
            val high = if (position + 1 < end) input[position + 1].toInt() and BYTE else 0
            position += 2
            buffer = (buffer shl WORD) or (((high shl BITS_PER_BYTE) or low).toLong())
            buffer = buffer and BUFFER_MASK
            count += WORD
        }
    }

    /** Before an uncompressed block's raw bytes: drop the padding to the next word, 1 to 16 bits. */
    fun alignToBytes() {
        ensure(WORD)
        if (count > WORD) position -= 2
        restart()
    }

    fun restart() {
        buffer = 0
        count = 0
    }

    fun skipRawByte() {
        position++
    }

    fun rawInt(): Int {
        if (position + INT_BYTES > end) throw LzxException("input ends inside a block header")
        var value = 0
        for (k in 0 until INT_BYTES) value = value or ((input[position + k].toInt() and BYTE) shl (BITS_PER_BYTE * k))
        position += INT_BYTES
        return value
    }

    fun copyRaw(target: ByteArray, at: Int, length: Int) {
        if (position + length > end) throw LzxException("input ends inside an uncompressed block")
        input.copyInto(target, at, position, position + length)
        position += length
    }

    private companion object {
        const val WORD = 16
        const val BYTE = 0xFF
        const val BITS_PER_BYTE = 8
        const val INT_BYTES = 4
        const val BUFFER_MASK = 0xFFFFFFFFFFFFL
    }
}

/**
 * A canonical Huffman code from code lengths (0 = unused): codes are given out shortest first and, within a length,
 * in symbol order, as LZX writes them. An incomplete or over-full code is damage unless every length is 0.
 */
internal class Huffman(lengths: IntArray) {
    private val counts = IntArray(MAX_LENGTH + 1)
    private val symbols: IntArray
    private val empty: Boolean

    init {
        lengths.forEach { if (it > MAX_LENGTH) throw LzxException("code length $it") else counts[it]++ }
        counts[0] = 0
        val used = lengths.count { it > 0 }
        empty = used == 0
        var room = 1L
        for (len in 1..MAX_LENGTH) {
            room = room * 2 - counts[len]
            if (room < 0) throw LzxException("over-full Huffman code")
        }
        if (!empty && room != 0L) throw LzxException("incomplete Huffman code")
        val offsets = IntArray(MAX_LENGTH + 2)
        for (len in 1..MAX_LENGTH) offsets[len + 1] = offsets[len] + counts[len]
        symbols = IntArray(used)
        lengths.forEachIndexed { symbol, len -> if (len > 0) symbols[offsets[len]++] = symbol }
    }

    fun decode(bits: LzxBits): Int {
        if (empty) throw LzxException("a symbol from an empty Huffman code")
        val peeked = bits.peek(MAX_LENGTH)
        var code = 0
        var first = 0
        var index = 0
        for (len in 1..MAX_LENGTH) {
            code = code or ((peeked ushr (MAX_LENGTH - len)) and 1)
            val count = counts[len]
            if (code - first < count) {
                bits.skip(len)
                return symbols[index + code - first]
            }
            index += count
            first = (first + count) shl 1
            code = code shl 1
        }
        throw LzxException("no Huffman code matches")
    }

    companion object {
        const val MAX_LENGTH = 16
        val EMPTY = Huffman(IntArray(1))
    }
}
