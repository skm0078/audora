// File: core/src/main/kotlin/io/github/skm0078/audora/core/dictionary/SortedLines.kt
// Purpose: Finds a line by its first field in a file of sorted lines, by binary search, without loading the file.

package io.github.skm0078.audora.core.dictionary

import io.github.skm0078.audora.core.config.DictionaryConfig
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/** Random access to bytes: a file on the phone, a byte array in tests. */
interface ByteSource : AutoCloseable {
    val length: Long

    /** Reads up to [buffer]'s size from [position]; returns the count read, or -1 at the end. */
    @Throws(IOException::class)
    fun read(position: Long, buffer: ByteArray): Int

    override fun close() = Unit
}

class FileByteSource(file: File) : ByteSource {
    private val access = RandomAccessFile(file, "r")
    override val length: Long = access.length()

    override fun read(position: Long, buffer: ByteArray): Int = synchronized(access) {
        access.seek(position)
        access.read(buffer)
    }

    override fun close() = access.close()
}

class ByteArraySource(private val bytes: ByteArray) : ByteSource {
    override val length: Long get() = bytes.size.toLong()

    override fun read(position: Long, buffer: ByteArray): Int {
        if (position >= bytes.size) return -1
        val count = minOf(buffer.size, bytes.size - position.toInt())
        bytes.copyInto(buffer, 0, position.toInt(), position.toInt() + count)
        return count
    }
}

/**
 * Lines of UTF-8 text, `\n`-terminated and sorted by their first field (up to the first tab) in byte order, which is
 * code point order. [find] costs about 2 log2(lines) block reads: some 34 for the 131,000-line English dictionary.
 */
class SortedLines(private val source: ByteSource, private val config: DictionaryConfig = DictionaryConfig()) {
    /** The line whose first field is exactly [key], without its newline; null when there is none. */
    @Throws(IOException::class)
    fun find(key: String): String? {
        val target = key.toByteArray(Charsets.UTF_8)
        var low = 0L // Always a line start.
        var high = source.length
        var found: String? = null
        while (low < high && found == null) {
            val middle = (low + high) / 2
            val start = lineStartFrom(middle)
            val line = if (start < high) lineAt(start) else null
            when {
                start >= high -> high = middle

                line == null -> high = low

                // A damaged line: stop, as if not found.
                else -> {
                    val order = compareKey(line, target)
                    when {
                        order == 0 -> found = String(line, Charsets.UTF_8)
                        order < 0 -> low = start + line.size + 1
                        else -> high = start
                    }
                }
            }
        }
        return found
    }

    /** The first line start at or after [position]. */
    private fun lineStartFrom(position: Long): Long =
        if (position == 0L) 0L else indexOfNewline(position - 1)?.plus(1) ?: source.length

    /** The line starting at [start], or null when it runs past [DictionaryConfig.maxLineBytes] (damage). */
    private fun lineAt(start: Long): ByteArray? {
        val end = indexOfNewline(start) ?: source.length
        val size = end - start
        if (size > config.maxLineBytes) return null
        val line = ByteArray(size.toInt())
        var read = 0
        while (read < line.size) {
            val chunk = ByteArray(minOf(config.chunkBytes, line.size - read))
            val count = source.read(start + read, chunk)
            if (count <= 0) break
            chunk.copyInto(line, read, 0, count)
            read += count
        }
        return line
    }

    private fun indexOfNewline(from: Long): Long? {
        val buffer = ByteArray(config.chunkBytes)
        var position = from
        var found: Long? = null
        var more = true
        while (found == null && more && position - from <= config.maxLineBytes) {
            val count = source.read(position, buffer)
            more = count > 0
            val index = (0 until maxOf(count, 0)).firstOrNull { buffer[it] == NEWLINE }
            if (index != null) found = position + index
            position += maxOf(count, 0)
        }
        return found
    }

    /** The line's first field against [target], bytes compared unsigned. */
    private fun compareKey(line: ByteArray, target: ByteArray): Int {
        val keyEnd = line.indexOf(TAB).takeIf { it >= 0 } ?: line.size
        for (i in 0 until minOf(keyEnd, target.size)) {
            val difference = (line[i].toInt() and BYTE_MASK) - (target[i].toInt() and BYTE_MASK)
            if (difference != 0) return difference
        }
        return keyEnd - target.size
    }

    private companion object {
        const val NEWLINE = '\n'.code.toByte()
        const val TAB = '\t'.code.toByte()
        const val BYTE_MASK = 0xFF
    }
}
