// File: speech/src/test/kotlin/io/github/skm0078/audora/speech/voices/ResumableDownloaderTest.kt
// Purpose: P4-01 done-when: an interrupted download resumes where it stopped, and a corrupt file is rejected.

package io.github.skm0078.audora.speech.voices

import io.github.skm0078.audora.core.config.VoiceDownloadConfig
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.core.result.errorOrNull
import java.io.File
import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ResumableDownloaderTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val content = ByteArray(10_000) { (it * 31 % 251).toByte() }
    private val voice = catalogVoice(content)
    private val http = FakeHttpSource(content)
    private val config = VoiceDownloadConfig(bufferBytes = 512, progressStepBytes = 1_000, installHeadroomBytes = 0)
    private var free = Long.MAX_VALUE
    private val downloader = ResumableDownloader(http, config) { free }
    private val partial: File get() = File(temp.root, "downloads/cori.zip.partial")
    private val progress = mutableListOf<Long>()

    private fun download(keepGoing: () -> Boolean = { true }) =
        downloader.download(voice, partial, { bytes, _ -> progress += bytes }, keepGoing)

    @Test
    fun `a fresh download is verified and returned`() {
        val result = download()

        assertArrayEquals(content, (result as Outcome.Success).value.readBytes())
        assertEquals(listOf(0L), http.offsets)
        assertEquals(content.size.toLong(), progress.last())
        assertEquals(progress.sorted(), progress)
    }

    @Test
    fun `an interrupted download keeps its bytes and the next attempt resumes from them`() {
        http.dropAfter = 3_000

        val first = download().errorOrNull()

        assertTrue(first is DownloadError.Network && first.retryable)
        assertEquals(3_000L, partial.length())

        val second = download()

        assertEquals(listOf(0L, 3_000L), http.offsets)
        assertArrayEquals(content, (second as Outcome.Success).value.readBytes())
    }

    @Test
    fun `a stop keeps the bytes for the next attempt`() {
        var reads = 0
        val stopped = download(keepGoing = { ++reads <= 4 }).errorOrNull()

        assertEquals(DownloadError.Stopped, stopped)
        assertEquals(4L * 512, partial.length())
        assertTrue(download() is Outcome.Success)
        assertEquals(4L * 512, http.offsets.last())
    }

    @Test
    fun `a server that ignores the resume restarts the file from the top`() {
        http.dropAfter = 2_500
        download()
        http.ignoreRange = true

        val result = download()

        assertArrayEquals(content, (result as Outcome.Success).value.readBytes())
    }

    @Test
    fun `a corrupt file is rejected and deleted`() {
        http.content = content.copyOf().also { it[5_000] = (it[5_000] + 1).toByte() }

        val error = download().errorOrNull()

        assertTrue(error is DownloadError.Corrupt)
        assertFalse(requireNotNull(error).retryable)
        assertFalse(partial.exists())
    }

    @Test
    fun `a file that changed on the server between attempts is rejected`() {
        http.dropAfter = 4_000
        download()
        http.content = content.copyOf().also { it[9_000] = 0 }

        assertTrue(download().errorOrNull() is DownloadError.Corrupt)
        assertFalse(partial.exists())
    }

    @Test
    fun `more bytes than pinned are rejected without waiting for the hash`() {
        http.content = content + ByteArray(100)

        assertTrue(download().errorOrNull() is DownloadError.Corrupt)
        assertFalse(partial.exists())
    }

    @Test
    fun `a partial file that does not fit the server's file starts over`() {
        partial.parentFile.mkdirs()
        partial.writeBytes(content.copyOf(2_000))
        http.content = content.copyOf(1_500)

        val error = download().errorOrNull()

        assertTrue(error is DownloadError.Network)
        assertFalse(partial.exists())
    }

    @Test
    fun `a partial file longer than the pin is dropped`() {
        partial.parentFile.mkdirs()
        partial.writeBytes(ByteArray(content.size + 10))

        assertTrue(download() is Outcome.Success)
        assertEquals(listOf(0L), http.offsets)
    }

    @Test
    fun `a complete partial file is only verified`() {
        partial.parentFile.mkdirs()
        partial.writeBytes(content)

        assertTrue(download() is Outcome.Success)
        assertTrue(http.offsets.isEmpty())
    }

    @Test
    fun `server errors say whether to retry`() {
        http.status = 503
        assertTrue(requireNotNull(download().errorOrNull()).retryable)

        http.status = 404
        val notFound = download().errorOrNull()
        assertEquals(DownloadError.Http(404), notFound)
        assertFalse(requireNotNull(notFound).retryable)
    }

    @Test
    fun `a connection that cannot open is a retryable network error`() {
        http.connectFailure = IOException("no route")

        val error = download().errorOrNull()

        assertTrue(error is DownloadError.Network && error.retryable)
    }

    @Test
    fun `no request is made when the phone lacks room for the voice and its unpacked copy`() {
        free = content.size * 2L - 1

        val error = download().errorOrNull()

        assertTrue(error is DownloadError.Storage)
        assertTrue(http.offsets.isEmpty())
    }

    @Test
    fun `Content-Range start is parsed`() {
        assertEquals(1000L, HttpResponse.rangeStart("bytes 1000-1999/2000"))
        assertEquals(5L, HttpResponse.rangeStart("bytes 5-9/*"))
        assertEquals(null, HttpResponse.rangeStart("items 1-2/3"))
        assertEquals(null, HttpResponse.rangeStart(null))
    }
}
