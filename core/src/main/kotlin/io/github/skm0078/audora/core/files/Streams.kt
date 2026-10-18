// File: core/src/main/kotlin/io/github/skm0078/audora/core/files/Streams.kt
// Purpose: Stream reads that work on every supported Android version (InputStream.readNBytes is API 33).

package io.github.skm0078.audora.core.files

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

private const val CHUNK_BYTES = 64 * 1024

/**
 * Reads until [limit] bytes or the end of the stream, whichever comes first (InputStream.readNBytes(int), which
 * Android has only from API 33). Grows with what is read, so a large [limit] on a small stream costs little.
 */
@Throws(IOException::class)
fun InputStream.readUpTo(limit: Int): ByteArray {
    require(limit >= 0) { "limit must be >= 0, was $limit" }
    val out = ByteArrayOutputStream(minOf(limit, CHUNK_BYTES))
    val buffer = ByteArray(minOf(limit, CHUNK_BYTES).coerceAtLeast(1))
    var left = limit
    while (left > 0) {
        val read = read(buffer, 0, minOf(left, buffer.size))
        if (read < 0) break
        out.write(buffer, 0, read)
        left -= read
    }
    return out.toByteArray()
}
