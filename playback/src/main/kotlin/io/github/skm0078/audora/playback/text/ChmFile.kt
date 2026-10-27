// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/text/ChmFile.kt
// Purpose: Reads files out of a compiled help file (.chm): its directory, its plain section and its LZX-compressed
//          section (ADR 2026-10-26-more-ebook-formats).

package io.github.skm0078.audora.playback.text

import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/** A CHM's bytes, read at any place: the whole file in memory, or a file channel on the phone. */
interface ChmSource {
    val size: Long

    /** [length] bytes from [offset]; fewer only at the end of the file. */
    fun read(offset: Long, length: Int): ByteArray
}

class ByteArrayChmSource(private val bytes: ByteArray) : ChmSource {
    override val size: Long get() = bytes.size.toLong()

    override fun read(offset: Long, length: Int): ByteArray {
        if (offset < 0 || offset > bytes.size) throw ChmException("read at $offset of ${bytes.size}")
        return bytes.copyOfRange(offset.toInt(), minOf(bytes.size.toLong(), offset + length).toInt())
    }
}

/** A help file read in place through its descriptor's channel; the caller closes the channel. */
class FileChannelChmSource(private val channel: FileChannel) : ChmSource {
    override val size: Long = channel.size()

    override fun read(offset: Long, length: Int): ByteArray {
        if (offset < 0 || offset > size || length < 0) throw ChmException("read at $offset of $size")
        val buffer = ByteBuffer.allocate(minOf(length.toLong(), size - offset).toInt())
        var at = offset
        while (buffer.hasRemaining()) {
            val read = channel.read(buffer, at)
            if (read < 0) break
            at += read
        }
        return buffer.array().copyOf(buffer.position())
    }
}

class ChmException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * The ITSF header points at the directory (ITSP header, then PMGL listing chunks naming every file with its
 * section, offset and length) and at section 0, which holds files as they are, among them the compressed section's
 * bytes (`::DataSpace/Storage/MSCompressed/Content`), its LZX settings (`ControlData`) and where each 32 KB frame's
 * compressed bytes start (`ResetTable`). Formats as in Matthew Russotto's notes and libmspack's chmd.c.
 */
class ChmFile private constructor(private val source: ChmSource, val entries: Map<String, Entry>, dataOffset: Long) {
    /** One file in the help file; [section] 0 is stored as is, 1 is LZX-compressed. */
    data class Entry(val name: String, val section: Int, val offset: Long, val length: Long)

    private val section0 = dataOffset

    /** The named files' bytes (names as listed, compared without case), at most [maxBytes] in all. */
    fun read(names: Collection<String>, maxBytes: Long): Map<String, ByteArray> {
        val wanted = names.mapNotNull { entry(it) }.distinctBy { it.name }
        val total = wanted.sumOf { it.length }
        if (total > maxBytes) throw ChmTooLargeException()
        val plain = wanted.filter { it.section == 0 }.associate { it.name to readPlain(it) }
        val compressed = wanted.filter { it.section == 1 }
        return plain + if (compressed.isEmpty()) emptyMap() else CompressedSection(this).read(compressed)
    }

    fun entry(name: String): Entry? = entries[name.lowercase()]

    internal fun readPlain(entry: Entry): ByteArray {
        if (entry.length > Int.MAX_VALUE) throw ChmException("a file of ${entry.length} bytes")
        val bytes = source.read(section0 + entry.offset, entry.length.toInt())
        if (bytes.size.toLong() != entry.length) throw ChmException("${entry.name} is cut short")
        return bytes
    }

    internal fun readAt(offset: Long, length: Int): ByteArray = source.read(section0 + offset, length)

    companion object {
        private const val ITSF_HEADER = 0x58
        private const val ITSF_VERSION = 4
        private const val DIR_OFFSET = 0x48
        private const val DIR_LENGTH = 0x50
        private const val DATA_OFFSET = 0x58
        private const val ITSP_HEADER = 0x54
        private const val ITSP_HEADER_LENGTH = 8
        private const val CHUNK_SIZE = 0x10
        private const val CHUNK_COUNT = 0x2C
        private const val PMGL_HEADER = 0x14
        private const val PMGL_FREE_SPACE = 4
        private const val MAX_CHUNK_SIZE = 1 shl 16
        private const val MAX_CHUNKS = 1 shl 16
        private const val VERSION_WITH_DATA_OFFSET = 3

        fun open(source: ChmSource): ChmFile {
            val head = source.read(0, ITSF_HEADER + LONG_BYTES)
            chmCheck(head.size >= ITSF_HEADER && head.startsWith("ITSF")) { "no ITSF header" }
            val version = head.le32(ITSF_VERSION)
            val dirOffset = head.le64(DIR_OFFSET)
            val dirLength = head.le64(DIR_LENGTH)
            val itsp = source.read(dirOffset, ITSP_HEADER)
            chmCheck(itsp.size >= ITSP_HEADER && itsp.startsWith("ITSP")) { "no ITSP header" }
            val itspLength = itsp.le32(ITSP_HEADER_LENGTH).toLong()
            val chunkSize = itsp.le32(CHUNK_SIZE)
            val chunks = itsp.le32(CHUNK_COUNT)
            chmCheck(chunkSize in PMGL_HEADER + 2..MAX_CHUNK_SIZE && chunks in 1..MAX_CHUNKS) {
                "directory of $chunks chunks of $chunkSize bytes"
            }
            val dataOffset = if (version >= VERSION_WITH_DATA_OFFSET && head.size >= DATA_OFFSET + LONG_BYTES) {
                head.le64(DATA_OFFSET)
            } else {
                dirOffset + dirLength
            }
            val entries = LinkedHashMap<String, Entry>()
            for (chunk in 0 until chunks) {
                val bytes = source.read(dirOffset + itspLength + chunk.toLong() * chunkSize, chunkSize)
                if (bytes.size == chunkSize && bytes.startsWith("PMGL")) listing(bytes, entries)
            }
            return ChmFile(source, entries, dataOffset)
        }

        /** A PMGL chunk's entries: name, section, offset and length, up to the free space at its end. */
        private fun listing(chunk: ByteArray, into: MutableMap<String, Entry>) {
            val end = chunk.size - chunk.le32(PMGL_FREE_SPACE)
            if (end !in PMGL_HEADER..chunk.size) throw ChmException("a listing chunk's free space is wrong")
            val reader = EncIntReader(chunk, PMGL_HEADER, end)
            while (reader.hasMore()) {
                val nameLength = reader.next().toInt()
                val name = reader.string(nameLength)
                val entry = Entry(name, reader.next().toInt(), reader.next(), reader.next())
                into[name.lowercase()] = entry
            }
        }
    }
}

internal class ChmTooLargeException : RuntimeException("CHM text over the limit")

/** Reads the CHM's variable-length numbers: 7 bits a byte, high bit set on all but the last. */
private class EncIntReader(private val bytes: ByteArray, private var position: Int, private val end: Int) {
    fun hasMore(): Boolean = position < end

    fun next(): Long {
        var value = 0L
        while (true) {
            if (position >= end) throw ChmException("a number runs past its chunk")
            val b = bytes[position++].toInt() and BYTE
            value = (value shl SEVEN) or (b and LOW_SEVEN).toLong()
            if (b and HIGH_BIT == 0) return value
            if (value > MAX_VALUE) throw ChmException("a number too large")
        }
    }

    fun string(length: Int): String {
        if (length < 0 || position + length > end) throw ChmException("a name runs past its chunk")
        return String(bytes, position, length, Charsets.UTF_8).also { position += length }
    }

    private companion object {
        const val BYTE = 0xFF
        const val SEVEN = 7
        const val LOW_SEVEN = 0x7F
        const val HIGH_BIT = 0x80
        const val MAX_VALUE = 1L shl 48
    }
}

/**
 * The LZX section: decompressed frame by frame from the start, resetting the decoder where the reset interval says,
 * and kept only where a wanted file lies. Frames after the last wanted byte are not decompressed.
 */
private class CompressedSection(private val chm: ChmFile) {
    fun read(wanted: List<ChmFile.Entry>): Map<String, ByteArray> {
        val content = chm.entry(CONTENT) ?: throw ChmException("no compressed content")
        val layout = LzxLayout.of(plain(CONTROL), plain(RESET_TABLE))
        val outputs = wanted.associate { it.name to ByteArray(it.length.toInt()) }
        val lastByte = wanted.maxOf { it.offset + it.length }
        if (lastByte > layout.uncompressed) throw ChmException("a file lies past the compressed section")
        val lzx = Lzx(layout.windowBits)
        val lastFrame = ((lastByte - 1) / Lzx.FRAME_SIZE).toInt()
        val firstFrame = layout.resetBefore(wanted.minOf { it.offset } / Lzx.FRAME_SIZE)
        for (frame in firstFrame..lastFrame) {
            if (frame % layout.resetFrames == 0L) lzx.reset()
            val frameStart = frame.toLong() * Lzx.FRAME_SIZE
            val out = decompress(lzx, layout, content, frame)
            for (entry in wanted) copyOverlap(out, frameStart, entry, outputs.getValue(entry.name))
        }
        return outputs
    }

    private fun plain(name: String): ByteArray =
        chm.entry(name)?.let(chm::readPlain) ?: throw ChmException("no LZX ${name.substringAfterLast('/')}")

    private fun decompress(lzx: Lzx, layout: LzxLayout, content: ChmFile.Entry, frame: Int): ByteArray {
        val start = layout.starts[frame]
        val end = if (frame + 1 < layout.starts.size) layout.starts[frame + 1] else layout.compressed
        if (end < start || end > content.length) throw ChmException("frame $frame's bytes are out of place")
        val input = chm.readAt(content.offset + start, (end - start).toInt())
        val outLength = minOf(Lzx.FRAME_SIZE.toLong(), layout.uncompressed - frame.toLong() * Lzx.FRAME_SIZE)
        return try {
            lzx.decompressFrame(input, 0, input.size, outLength.toInt())
        } catch (e: LzxException) {
            throw ChmException("frame $frame: ${e.message}", e)
        }
    }

    private fun copyOverlap(out: ByteArray, frameStart: Long, entry: ChmFile.Entry, target: ByteArray) {
        val from = maxOf(frameStart, entry.offset)
        val to = minOf(frameStart + out.size, entry.offset + entry.length)
        if (from >= to) return
        out.copyInto(target, (from - entry.offset).toInt(), (from - frameStart).toInt(), (to - frameStart).toInt())
    }

    private companion object {
        const val CONTENT = "::DataSpace/Storage/MSCompressed/Content"
        const val CONTROL = "::DataSpace/Storage/MSCompressed/ControlData"
        const val RESET_TABLE = "::DataSpace/Storage/MSCompressed/Transform/" +
            "{7FC28940-9D31-11D0-9B27-00A0C91E9C7C}/InstanceData/ResetTable"
    }
}

/**
 * What the control data (window size, reset interval) and the reset table (each frame's compressed start) say about
 * the LZX stream. Version 2 control data counts in frames of [Lzx.FRAME_SIZE] bytes.
 */
private class LzxLayout(
    val windowBits: Int,
    val resetFrames: Long,
    val starts: LongArray,
    val uncompressed: Long,
    val compressed: Long,
) {
    /** The frame at or before [frame] where the decoder starts afresh, so decoding may begin there. */
    fun resetBefore(frame: Long): Int = (frame / resetFrames * resetFrames).toInt()

    companion object {
        private const val CONTROL_SIZE = 24
        private const val CONTROL_MAGIC = 4
        private const val CONTROL_VERSION = 8
        private const val CONTROL_RESET_INTERVAL = 12
        private const val CONTROL_WINDOW_SIZE = 16
        private const val CONTROL_WINDOWS_PER_RESET = 20
        private const val VERSION_IN_FRAMES = 2
        private const val TABLE_FRAMES = 4
        private const val TABLE_OFFSET = 12
        private const val TABLE_UNCOMPRESSED = 16
        private const val TABLE_COMPRESSED = 24
        private const val TABLE_FRAME_LENGTH = 32

        fun of(control: ByteArray, table: ByteArray): LzxLayout {
            val (windowBits, resetFrames) = window(control)
            val frames = table.le32(TABLE_FRAMES)
            val tableOffset = table.le32(TABLE_OFFSET)
            val frameLength = table.le64(TABLE_FRAME_LENGTH)
            if (frameLength != Lzx.FRAME_SIZE.toLong() || tableOffset + frames.toLong() * LONG_BYTES > table.size) {
                throw ChmException("reset table of $frames frames of $frameLength bytes")
            }
            return LzxLayout(
                windowBits = windowBits,
                resetFrames = resetFrames,
                starts = LongArray(frames) { table.le64(tableOffset + it * LONG_BYTES) },
                uncompressed = table.le64(TABLE_UNCOMPRESSED),
                compressed = table.le64(TABLE_COMPRESSED),
            )
        }

        /** The window's size in bits and the frames between resets: resetInterval / (window / 2) * windowsPerReset. */
        private fun window(control: ByteArray): Pair<Int, Long> {
            if (control.size < CONTROL_SIZE || !control.startsWith("LZXC", at = CONTROL_MAGIC)) {
                throw ChmException("bad control data")
            }
            val scale = if (control.le32(CONTROL_VERSION) == VERSION_IN_FRAMES) Lzx.FRAME_SIZE else 1
            val resetInterval = control.le32(CONTROL_RESET_INTERVAL).toLong() * scale
            val windowSize = control.le32(CONTROL_WINDOW_SIZE).toLong() * scale
            val windowsPerReset = control.le32(CONTROL_WINDOWS_PER_RESET).toLong()
            if (windowSize < 2 || windowSize and (windowSize - 1) != 0L || resetInterval <= 0) {
                throw ChmException("LZX window $windowSize, reset interval $resetInterval")
            }
            val resetFrames = (resetInterval / (windowSize / 2) * windowsPerReset).coerceAtLeast(1)
            return java.lang.Long.numberOfTrailingZeros(windowSize) to resetFrames
        }
    }
}

private const val BYTE_MASK = 0xFF

/** Throws [ChmException] with [message] unless [valid]: the file is not the help file it claims to be. */
private inline fun chmCheck(valid: Boolean, message: () -> String) {
    if (!valid) throw ChmException(message())
}
private const val LONG_BYTES = 8

private fun ByteArray.startsWith(text: String, at: Int = 0): Boolean =
    at + text.length <= size && text.indices.all { this[at + it] == text[it].code.toByte() }

@Suppress("MagicNumber") // Byte positions within a little-endian word.
private fun ByteArray.le32(at: Int): Int = (this[at].toInt() and BYTE_MASK) or
    ((this[at + 1].toInt() and BYTE_MASK) shl 8) or ((this[at + 2].toInt() and BYTE_MASK) shl 16) or
    ((this[at + 3].toInt() and BYTE_MASK) shl 24)

@Suppress("MagicNumber")
private fun ByteArray.le64(at: Int): Long = (le32(at).toLong() and 0xFFFFFFFFL) or (le32(at + 4).toLong() shl 32)
