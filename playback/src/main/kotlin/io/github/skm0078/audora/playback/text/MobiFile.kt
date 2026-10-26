// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/text/MobiFile.kt
// Purpose: Reads a Mobipocket or Kindle book (.mobi, .azw3, .azw, .prc) or a PalmDOC (.pdb) without DRM: its
//          details and its text, decompressed (ADR 2026-10-26-more-ebook-formats).

package io.github.skm0078.audora.playback.text

import io.github.skm0078.audora.core.files.readUpTo
import io.github.skm0078.audora.core.result.Outcome
import java.io.EOFException
import java.io.InputStream
import java.nio.charset.Charset

/**
 * A Palm database: a header, a table of record offsets, then the records. Record 0 holds the PalmDOC header
 * (compression, text length, text record count, encryption), then for a Mobipocket or Kindle book the MOBI header
 * (text encoding, title, the HUFF/CDIC tables' place, the trailing-entry flags) and its EXTH metadata. Records
 * 1..count hold the text, each compressed alone. A Kindle file made by KindleGen holds an old (MOBI 6) book and a KF8
 * one; the first is read, so one reader serves both. A calibre AZW3 is KF8 only and reads the same way.
 *
 * The file is read once from the start, skipping pictures: the text records come first. Errors are typed: a book
 * locked with DRM is Locked, a broken one Damaged, a huge one TooLarge.
 */
object MobiFile {
    /** What [read] found: [markup] is the book's HTML (MOBI 6 or KF8), or plain text when [isHtml] is false. */
    data class Content(
        val title: String?,
        val author: String?,
        val language: String?,
        val isHtml: Boolean,
        val markup: String,
    )

    /** The details and, unless [withText] is false, the text of the book in [stream]; [stream] is not closed. */
    // A damaged header points outside its record: the JVM's bounds check finds it, and here it becomes Damaged.
    @Suppress("TooGenericExceptionCaught")
    fun read(stream: InputStream, maxTextBytes: Long, withText: Boolean = true): Outcome<Content, TextBookError> = try {
        readOrThrow(PdbReader(stream), maxTextBytes, withText)
    } catch (e: MobiException) {
        Outcome.Failure(e.error)
    } catch (e: EOFException) {
        Outcome.Failure(TextBookError.Damaged("cut short: ${e.message}"))
    } catch (e: IndexOutOfBoundsException) {
        Outcome.Failure(TextBookError.Damaged("a record points outside the file: ${e.javaClass.simpleName}"))
    } catch (e: IllegalArgumentException) {
        Outcome.Failure(TextBookError.Damaged("bad HUFF/CDIC tables: ${e.message}"))
    }

    private fun readOrThrow(pdb: PdbReader, maxTextBytes: Long, withText: Boolean): Outcome<Content, TextBookError> {
        val header = MobiHeader.parse(pdb.record(0), pdb.name)
        when {
            header.encryption != 0 -> fail(TextBookError.Locked("protected by DRM"))
            header.textRecords < 0 || header.textRecords >= pdb.count -> fail(damaged("bad text record count"))
            withText && header.textLength > maxTextBytes -> fail(TextBookError.TooLarge("text over the limit"))
        }
        val text = if (withText) decompressText(pdb, header, maxTextBytes) else ""
        return Outcome.Success(Content(header.title, header.author, header.language, header.isMobi, text))
    }

    private fun decompressText(pdb: PdbReader, header: MobiHeader, maxTextBytes: Long): String {
        val wanted = (1..header.textRecords).toMutableList()
        if (header.compression == COMPRESSION_HUFF_CDIC) {
            if (header.huffRecord <= 0 || header.huffCount < 1) fail(damaged("no HUFF/CDIC tables"))
            wanted += header.huffRecord until header.huffRecord + header.huffCount
        }
        val records = pdb.records(wanted.distinct().sorted(), maxTextBytes)
        val decompress: (ByteArray) -> ByteArray = when (header.compression) {
            COMPRESSION_NONE -> { bytes -> bytes }

            COMPRESSION_PALMDOC -> PalmDoc::decompress

            COMPRESSION_HUFF_CDIC -> HuffCdic(
                (header.huffRecord until header.huffRecord + header.huffCount).map { records.getValue(it) },
            )::unpack

            else -> fail(damaged("unknown compression ${header.compression}"))
        }
        val text = java.io.ByteArrayOutputStream()
        for (index in 1..header.textRecords) {
            val record = records.getValue(index)
            val body = record.copyOf(record.size - TrailingEntries.size(record, header.extraFlags))
            text.write(decompress(body))
            if (text.size() > maxTextBytes) fail(TextBookError.TooLarge("text over the limit"))
        }
        val bytes = text.toByteArray()
        val length = if (header.textLength in 1..bytes.size) header.textLength.toInt() else bytes.size
        return String(bytes, 0, length, header.charset)
    }

    private fun damaged(why: String) = TextBookError.Damaged(why)

    private fun fail(error: TextBookError): Nothing = throw MobiException(error)

    private class MobiException(val error: TextBookError) : RuntimeException(error.message)

    private const val COMPRESSION_NONE = 1
    private const val COMPRESSION_PALMDOC = 2
    private const val COMPRESSION_HUFF_CDIC = 17480
}

/** Record 0's PalmDOC header, MOBI header and EXTH metadata; offsets are from the start of record 0. */
@Suppress("LongParameterList") // One value per header field the reader uses.
internal class MobiHeader private constructor(
    val compression: Int,
    val textLength: Long,
    val textRecords: Int,
    val encryption: Int,
    val isMobi: Boolean,
    val charset: Charset,
    val huffRecord: Int,
    val huffCount: Int,
    val extraFlags: Int,
    val title: String?,
    val author: String?,
    val language: String?,
) {
    companion object {
        private val MOBI = "MOBI".toByteArray(Charsets.US_ASCII)
        private val EXTH = "EXTH".toByteArray(Charsets.US_ASCII)
        private const val PALMDOC_HEADER = 16
        private const val TEXT_LENGTH = 4
        private const val RECORD_COUNT = 8
        private const val ENCRYPTION = 12
        private const val HEADER_LENGTH = 0x14
        private const val TEXT_ENCODING = 0x1C
        private const val FULL_NAME_OFFSET = 0x54
        private const val FULL_NAME_LENGTH = 0x58
        private const val HUFF_RECORD = 0x70
        private const val HUFF_COUNT = 0x74
        private const val EXTH_FLAGS = 0x80
        private const val EXTH_PRESENT = 0x40
        private const val EXTRA_FLAGS = 0xF2

        /** The MOBI header must be this long for [EXTRA_FLAGS] to be there (older files have none). */
        private const val EXTRA_FLAGS_MIN_HEADER = 0xE4
        private const val UTF8_CODE_PAGE = 65001
        private const val EXTH_AUTHOR = 100
        private const val EXTH_UPDATED_TITLE = 503
        private const val EXTH_LANGUAGE = 524
        private const val EXTH_RECORD_HEADER = 8
        private const val EXTH_HEADER = 12

        fun parse(record: ByteArray, pdbName: String?): MobiHeader {
            val isMobi = record.size >= PALMDOC_HEADER + MOBI.size && record.matches(MOBI, PALMDOC_HEADER)
            val headerLength = if (isMobi) record.u32(HEADER_LENGTH).toInt() else 0
            val exth = if (isMobi && record.u32(EXTH_FLAGS).toInt() and EXTH_PRESENT != 0) {
                exthRecords(record, PALMDOC_HEADER + headerLength)
            } else {
                emptyMap()
            }
            val fullName = if (isMobi) fullName(record) else null
            return MobiHeader(
                compression = record.u16(0),
                textLength = record.u32(TEXT_LENGTH),
                textRecords = record.u16(RECORD_COUNT),
                encryption = record.u16(ENCRYPTION),
                isMobi = isMobi,
                charset = if (isMobi && record.u32(TEXT_ENCODING).toInt() == UTF8_CODE_PAGE) {
                    Charsets.UTF_8
                } else {
                    WINDOWS_1252
                },
                huffRecord = if (isMobi) record.u32(HUFF_RECORD).toInt() else 0,
                huffCount = if (isMobi) record.u32(HUFF_COUNT).toInt() else 0,
                extraFlags = if (isMobi && headerLength >= EXTRA_FLAGS_MIN_HEADER) record.u16(EXTRA_FLAGS) else 0,
                title = (exth[EXTH_UPDATED_TITLE] ?: fullName ?: pdbName)?.clean(),
                author = exth[EXTH_AUTHOR]?.clean(),
                language = exth[EXTH_LANGUAGE]?.clean(),
            )
        }

        private fun fullName(record: ByteArray): String? {
            val offset = record.u32(FULL_NAME_OFFSET).toInt()
            val length = record.u32(FULL_NAME_LENGTH).toInt()
            return if (offset > 0 && length > 0 && offset.toLong() + length <= record.size) {
                String(record, offset, length, Charsets.UTF_8)
            } else {
                null
            }
        }

        /** EXTH records by type, text ones only (the first of each type); a damaged block stops where it breaks. */
        private fun exthRecords(record: ByteArray, at: Int): Map<Int, String> {
            if (at + EXTH_HEADER > record.size || !record.matches(EXTH, at)) return emptyMap()
            val found = HashMap<Int, String>()
            var position = at + EXTH_HEADER
            var left = record.u32(at + EXTH_RECORD_HEADER)
            while (left-- > 0 && position + EXTH_RECORD_HEADER <= record.size) {
                val type = record.u32(position).toInt()
                val length = record.u32(position + U32_BYTES).toInt()
                if (length < EXTH_RECORD_HEADER || position + length > record.size) break
                val data = String(record, position + EXTH_RECORD_HEADER, length - EXTH_RECORD_HEADER, Charsets.UTF_8)
                found.putIfAbsent(type, data)
                position += length
            }
            return found
        }

        private fun String.clean(): String? = replace('\u0000', ' ').trim().takeIf { it.isNotEmpty() }

        private val WINDOWS_1252: Charset = Charset.forName("windows-1252")
    }
}

private const val U16_BYTES = 2
private const val U32_BYTES = 4

/** HUFF/CDIC text is usually about three times its compressed size: the output buffer's first size. */
private const val UNPACKED_GUESS = 3

/** A record table that points outside the file or backwards: read as damaged ([MobiFile.read]). */
private fun outOfPlace(why: String): Nothing = throw IndexOutOfBoundsException(why)

/** Reads a Palm database's records in order from a stream that cannot seek, skipping the ones not wanted. */
private class PdbReader(private val stream: InputStream) {
    val name: String?
    val count: Int
    private val offsets: LongArray
    private var position: Long

    init {
        val header = stream.readExactly(HEADER_SIZE)
        name = String(header, 0, NAME_SIZE, Charsets.ISO_8859_1).substringBefore('\u0000').trim().ifEmpty { null }
        count = header.u16(RECORD_COUNT)
        if (count == 0) throw EOFException("no records")
        val table = stream.readExactly(count * ENTRY_SIZE)
        offsets = LongArray(count) { table.u32(it * ENTRY_SIZE) }
        position = (HEADER_SIZE + count * ENTRY_SIZE).toLong()
    }

    fun record(index: Int): ByteArray = records(listOf(index), Long.MAX_VALUE).getValue(index)

    /** [indexes] in ascending order, read at most [maxBytes] in all; a record before the stream's place is refused. */
    fun records(indexes: List<Int>, maxBytes: Long): Map<Int, ByteArray> {
        val found = HashMap<Int, ByteArray>()
        var total = 0L
        for (index in indexes) {
            if (index !in 0 until count) outOfPlace("record $index of $count")
            val start = offsets[index]
            if (start < position) outOfPlace("record $index is behind the reading place")
            skipTo(start)
            val last = index + 1 == count
            val length = if (last) {
                (maxBytes - total).coerceIn(0L, Int.MAX_VALUE.toLong())
            } else {
                offsets[index + 1] - start
            }
            if (length < 0) outOfPlace("record $index ends before it starts")
            total += length
            if (total > maxBytes) outOfPlace("records over the limit")
            // The last record runs to the end of the file, so it is read up to what is left of the limit.
            val bytes = if (last) stream.readUpTo(length.toInt()) else stream.readExactly(length.toInt())
            position += bytes.size
            found[index] = bytes
        }
        return found
    }

    private fun skipTo(target: Long) {
        while (position < target) {
            val skipped = stream.skip(target - position)
            if (skipped <= 0) {
                if (stream.read() < 0) throw EOFException("file ends before a record")
                position++
            } else {
                position += skipped
            }
        }
    }

    private companion object {
        const val HEADER_SIZE = 78
        const val NAME_SIZE = 32
        const val RECORD_COUNT = 76
        const val ENTRY_SIZE = 8
    }
}

private fun InputStream.readExactly(length: Int): ByteArray {
    val bytes = readUpTo(length)
    if (bytes.size < length) throw EOFException("wanted $length bytes, got ${bytes.size}")
    return bytes
}

/** The bytes a Mobipocket writer adds after a text record's data (indexing hints, a multibyte tail). */
internal object TrailingEntries {
    private const val MULTIBYTE_FLAG = 1
    private const val MULTIBYTE_MASK = 0x3
    private const val SEVEN_BITS = 0x7F
    private const val LAST_BYTE = 0x80
    private const val MAX_SHIFT = 28

    fun size(record: ByteArray, flags: Int): Int {
        var total = 0
        var test = flags shr 1
        while (test != 0) {
            if (test and 1 != 0) total += entrySize(record, record.size - total)
            test = test shr 1
        }
        if (flags and MULTIBYTE_FLAG != 0 && record.size - total > 0) {
            total += (record[record.size - total - 1].toInt() and MULTIBYTE_MASK) + 1
        }
        return total.coerceIn(0, record.size)
    }

    /** A size written backwards from [end] in 7-bit groups, the first byte flagged with the high bit. */
    private fun entrySize(record: ByteArray, end: Int): Int {
        var result = 0
        var shift = 0
        var at = end
        while (at > 0) {
            val value = record[at - 1].toInt() and BYTE_MASK
            result = result or ((value and SEVEN_BITS) shl shift)
            shift += SEVEN_BITS_SHIFT
            at--
            if (value and LAST_BYTE != 0 || shift >= MAX_SHIFT) break
        }
        return result
    }

    private const val SEVEN_BITS_SHIFT = 7
}

/** PalmDOC's LZ77: literals, runs of literals, a space and a letter in one byte, and back-references. */
internal object PalmDoc {
    private const val LITERAL_RUN_MAX = 8
    private const val PLAIN_MAX = 0x7F
    private const val SPACE_PAIR = 0xC0
    private const val SPACE_PAIR_MASK = 0x80
    private const val DISTANCE_SHIFT = 3
    private const val DISTANCE_MASK = 0x7FF
    private const val LENGTH_MASK = 0x7
    private const val MIN_MATCH = 3

    fun decompress(input: ByteArray): ByteArray {
        val out = GrowingBytes(input.size * 2)
        var i = 0
        while (i < input.size) {
            val c = input[i++].toInt() and BYTE_MASK
            when {
                c in 1..LITERAL_RUN_MAX -> {
                    val end = minOf(input.size, i + c)
                    while (i < end) out.add(input[i++].toInt())
                }

                c <= PLAIN_MAX -> out.add(c)

                c >= SPACE_PAIR -> {
                    out.add(' '.code)
                    out.add(c xor SPACE_PAIR_MASK)
                }

                i < input.size -> {
                    val pair = (c shl BITS_PER_BYTE) or (input[i++].toInt() and BYTE_MASK)
                    val distance = (pair shr DISTANCE_SHIFT) and DISTANCE_MASK
                    val length = (pair and LENGTH_MASK) + MIN_MATCH
                    if (distance in 1..out.size) out.copyBack(distance, length)
                }

                else -> Unit
            }
        }
        return out.toByteArray()
    }
}

/**
 * The Huffman and dictionary compression of some Kindle books (compression 17480): a HUFF record gives the code
 * tables, CDIC records the phrases, and a phrase may itself be compressed (decoded once, then kept).
 * Ported from the published format (MobileRead wiki and KindleUnpack's reader).
 */
internal class HuffCdic(tables: List<ByteArray>) {
    private val dict1 = LongArray(DICT1_SIZE)
    private val codeLengths = IntArray(DICT1_SIZE)
    private val terminal = BooleanArray(DICT1_SIZE)
    private val minCode = LongArray(MAX_CODE_LENGTH + 1)
    private val maxCode = LongArray(MAX_CODE_LENGTH + 1)
    private val phrases = ArrayList<ByteArray?>()
    private val phraseUnpacked = ArrayList<Boolean>()
    private var depth = 0

    init {
        val huff = tables.first()
        require(huff.size >= HUFF_HEADER && huff.matches(HUFF_MAGIC, 0)) { "not a HUFF record" }
        val dict1Offset = huff.u32(HUFF_DICT1).toInt()
        val dict2Offset = huff.u32(HUFF_DICT2).toInt()
        for (i in 0 until DICT1_SIZE) {
            val v = huff.u32(dict1Offset + i * U32_BYTES)
            val length = (v and CODE_LENGTH_MASK).toInt()
            require(length != 0) { "a HUFF code of no length" }
            codeLengths[i] = length
            terminal[i] = v and TERMINAL_FLAG != 0L
            dict1[i] = (((v shr BITS_PER_BYTE) + 1) shl (WORD_BITS - length)) - 1
        }
        for (length in 1..MAX_CODE_LENGTH) {
            val range = dict2Offset + (length - 1) * 2 * U32_BYTES
            minCode[length] = huff.u32(range) shl (WORD_BITS - length)
            maxCode[length] = ((huff.u32(range + U32_BYTES) + 1) shl (WORD_BITS - length)) - 1
        }
        tables.drop(1).forEach(::loadCdic)
    }

    private fun loadCdic(cdic: ByteArray) {
        require(cdic.size >= CDIC_HEADER && cdic.matches(CDIC_MAGIC, 0)) { "not a CDIC record" }
        val count = cdic.u32(CDIC_PHRASES).toInt()
        val bits = cdic.u32(CDIC_BITS).toInt()
        val n = minOf(1 shl bits, count - phrases.size)
        for (i in 0 until n) {
            val offset = cdic.u16(CDIC_HEADER + i * U16_BYTES)
            val lengthWord = cdic.u16(CDIC_HEADER + offset)
            val start = CDIC_HEADER + offset + U16_BYTES
            phrases += cdic.copyOfRange(start, start + (lengthWord and PHRASE_LENGTH_MASK))
            phraseUnpacked += lengthWord and PHRASE_DONE_FLAG != 0
        }
    }

    fun unpack(data: ByteArray): ByteArray {
        depth++
        require(depth <= MAX_DEPTH) { "HUFF/CDIC phrases nest too deep" }
        try {
            return unpackBits(data)
        } finally {
            depth--
        }
    }

    private fun unpackBits(data: ByteArray): ByteArray {
        val out = GrowingBytes(data.size * UNPACKED_GUESS)
        val padded = data.copyOf(data.size + PADDING)
        var bitsLeft = data.size.toLong() * BITS_PER_BYTE
        var position = 0
        var x = padded.u64(position)
        var n = WORD_BITS
        while (true) {
            if (n <= 0) {
                position += U32_BYTES
                x = padded.u64(position)
                n += WORD_BITS
            }
            val code = (x ushr n) and WORD_MASK
            val top = (code ushr (WORD_BITS - BITS_PER_BYTE)).toInt()
            var length = codeLengths[top]
            var max = dict1[top]
            if (!terminal[top]) {
                while (length < MAX_CODE_LENGTH && code < minCode[length]) length++
                max = maxCode[length]
            }
            n -= length
            bitsLeft -= length
            if (bitsLeft < 0) break
            val index = ((max - code) ushr (WORD_BITS - length)).toInt()
            out.addAll(phrase(index))
        }
        return out.toByteArray()
    }

    private fun phrase(index: Int): ByteArray {
        val stored = phrases.getOrNull(index) ?: throw IndexOutOfBoundsException("HUFF/CDIC phrase $index")
        if (phraseUnpacked[index]) return stored
        phrases[index] = null // a phrase that refers to itself is damage, found by the null above
        val unpacked = unpack(stored)
        phrases[index] = unpacked
        phraseUnpacked[index] = true
        return unpacked
    }

    private companion object {
        val HUFF_MAGIC = byteArrayOf(0x48, 0x55, 0x46, 0x46, 0, 0, 0, 0x18)
        val CDIC_MAGIC = byteArrayOf(0x43, 0x44, 0x49, 0x43, 0, 0, 0, 0x10)
        const val HUFF_HEADER = 0x18
        const val HUFF_DICT1 = 8
        const val HUFF_DICT2 = 12
        const val CDIC_HEADER = 16
        const val CDIC_PHRASES = 8
        const val CDIC_BITS = 12
        const val DICT1_SIZE = 256
        const val MAX_CODE_LENGTH = 32
        const val WORD_BITS = 32
        const val WORD_MASK = 0xFFFFFFFFL
        const val CODE_LENGTH_MASK = 0x1FL
        const val TERMINAL_FLAG = 0x80L
        const val PHRASE_LENGTH_MASK = 0x7FFF
        const val PHRASE_DONE_FLAG = 0x8000
        const val PADDING = 8
        const val MAX_DEPTH = 32
    }
}

/** A byte buffer that grows, with LZ77's overlapping copy from [distance] bytes back. */
internal class GrowingBytes(initial: Int) {
    private var bytes = ByteArray(maxOf(initial, MIN_CAPACITY))
    var size = 0
        private set

    fun add(value: Int) {
        ensure(1)
        bytes[size++] = value.toByte()
    }

    fun addAll(values: ByteArray) {
        ensure(values.size)
        values.copyInto(bytes, size)
        size += values.size
    }

    fun copyBack(distance: Int, length: Int) {
        ensure(length)
        repeat(length) {
            bytes[size] = bytes[size - distance]
            size++
        }
    }

    fun toByteArray(): ByteArray = bytes.copyOf(size)

    private fun ensure(more: Int) {
        if (size + more > bytes.size) bytes = bytes.copyOf(maxOf(bytes.size * 2, size + more))
    }

    private companion object {
        const val MIN_CAPACITY = 64
    }
}

private const val BYTE_MASK = 0xFF
private const val BITS_PER_BYTE = 8

internal fun ByteArray.u16(at: Int): Int = ((this[at].toInt() and BYTE_MASK) shl BITS_PER_BYTE) or
    (this[at + 1].toInt() and BYTE_MASK)

internal fun ByteArray.u32(at: Int): Long = (u16(at).toLong() shl (2 * BITS_PER_BYTE)) or u16(at + 2).toLong()

private fun ByteArray.u64(at: Int): Long = (u32(at) shl (U32_BYTES * BITS_PER_BYTE)) or u32(at + U32_BYTES)

private fun ByteArray.matches(prefix: ByteArray, at: Int): Boolean =
    at >= 0 && at + prefix.size <= size && prefix.indices.all { this[at + it] == prefix[it] }
