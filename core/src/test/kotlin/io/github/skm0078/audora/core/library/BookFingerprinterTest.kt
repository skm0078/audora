// File: core/src/test/kotlin/io/github/skm0078/audora/core/library/BookFingerprinterTest.kt
// Purpose: Fingerprints tell PDFs apart by size and head bytes, read only the head, and reject non-PDFs.

package io.github.skm0078.audora.core.library

import io.github.skm0078.audora.core.config.LibraryConfig
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.getOrNull
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookFingerprinterTest {
    private val config = LibraryConfig(fingerprintHeadBytes = 64, pdfHeaderWindowBytes = 16)
    private val fingerprinter = BookFingerprinter(config)

    private fun fingerprintOf(bytes: ByteArray, size: Long? = bytes.size.toLong()) =
        fingerprinter.fingerprint(ByteArrayInputStream(bytes), size)

    @Test
    fun `same bytes and size give the same fingerprint`() {
        val first = fingerprintOf(pdfBytes("a")).getOrNull()

        assertNotNull(first)
        assertEquals(first, fingerprintOf(pdfBytes("a")).getOrNull())
        assertTrue(requireNotNull(first).startsWith("v1:"))
    }

    @Test
    fun `different head bytes give different fingerprints`() {
        assertNotEquals(fingerprintOf(pdfBytes("a")).getOrNull(), fingerprintOf(pdfBytes("b")).getOrNull())
    }

    @Test
    fun `same head but different size give different fingerprints`() {
        val head = pdfBytes("a")

        assertNotEquals(fingerprintOf(head, 100L).getOrNull(), fingerprintOf(head, 200L).getOrNull())
    }

    @Test
    fun `bytes after the head do not matter`() {
        val head = pdfBytes("a") + ByteArray(64)
        val longer = head + "tail one".toByteArray()
        val other = head + "tail two".toByteArray()

        assertEquals(fingerprintOf(longer, 1000L).getOrNull(), fingerprintOf(other, 1000L).getOrNull())
    }

    @Test
    fun `reads no more than the configured head`() {
        val stream = CountingStream(pdfBytes("a") + ByteArray(10_000))
        fingerprinter.fingerprint(stream, null)

        assertEquals(config.fingerprintHeadBytes, stream.consumed)
    }

    @Test
    fun `a header after leading junk inside the window is accepted`() {
        assertTrue(fingerprintOf("junk\n".toByteArray() + pdfBytes("a")) is Outcome.Success)
    }

    @Test
    fun `a header beyond the window is rejected`() {
        assertEquals(
            Outcome.Failure(FingerprintError.NotAPdf),
            fingerprintOf(ByteArray(20) + pdfBytes("a")),
        )
    }

    @Test
    fun `non-PDF and empty files are rejected`() {
        assertEquals(Outcome.Failure(FingerprintError.NotAPdf), fingerprintOf("PK\u0003\u0004zip".toByteArray()))
        assertEquals(Outcome.Failure(FingerprintError.NotAPdf), fingerprintOf(ByteArray(0)))
    }

    @Test
    fun `a read error is reported, not thrown`() {
        val failing = object : InputStream() {
            override fun read(): Int = throw IOException("provider died")
        }

        assertTrue(fingerprinter.fingerprint(failing, 10L) is Outcome.Failure)
    }

    @Test
    fun `a stream that hands over one byte per read gives the same fingerprint`() {
        val bytes = pdfBytes("a") + ByteArray(100) { it.toByte() }
        val trickle = fingerprinter.fingerprint(TrickleStream(bytes), bytes.size.toLong())

        assertEquals(fingerprintOf(bytes).getOrNull(), trickle.getOrNull())
        assertNotNull(trickle.getOrNull())
    }

    @Test
    fun `a file shorter than the head is read whole`() {
        val short = pdfBytes("a")
        val trickle = fingerprinter.fingerprint(TrickleStream(short), short.size.toLong())

        assertEquals(fingerprintOf(short).getOrNull(), trickle.getOrNull())
        assertTrue(short.size < config.fingerprintHeadBytes)
    }

    /** Returns at most one byte from each bulk read, as some content providers' streams do. */
    private class TrickleStream(private val bytes: ByteArray) : InputStream() {
        private var at = 0

        override fun read(): Int = if (at < bytes.size) bytes[at++].toInt() and BYTE_MASK else -1

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val next = if (len == 0) 0 else read()
            if (next >= 0 && len > 0) b[off] = next.toByte()
            return when {
                len == 0 -> 0
                next < 0 -> -1
                else -> 1
            }
        }
    }

    private class CountingStream(private val bytes: ByteArray) : InputStream() {
        var consumed = 0
            private set

        override fun read(): Int = if (consumed < bytes.size) bytes[consumed++].toInt() and BYTE_MASK else -1
    }
}

private const val BYTE_MASK = 0xFF
