// File: core/src/test/kotlin/io/github/skm0078/audora/core/files/StreamsTest.kt
// Purpose: readUpTo stops at its limit or the stream's end, across short reads.

package io.github.skm0078.audora.core.files

import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamsTest {
    private val bytes = ByteArray(200_000) { it.toByte() }

    /** Hands out at most 7 bytes per read, as some content providers do. */
    private class Trickle(bytes: ByteArray) : FilterInputStream(ByteArrayInputStream(bytes)) {
        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, minOf(len, 7))
    }

    @Test
    fun `reads up to the limit or the end`() {
        assertArrayEquals(bytes.copyOf(10), ByteArrayInputStream(bytes).readUpTo(10))
        assertArrayEquals(bytes, ByteArrayInputStream(bytes).readUpTo(Int.MAX_VALUE))
        assertArrayEquals(bytes.copyOf(100_001), Trickle(bytes).readUpTo(100_001))
        assertEquals(0, ByteArrayInputStream(bytes).readUpTo(0).size)
        assertEquals(0, ByteArrayInputStream(ByteArray(0)).readUpTo(5).size)
    }
}
