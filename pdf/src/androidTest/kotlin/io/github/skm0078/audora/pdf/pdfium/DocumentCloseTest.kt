// File: pdf/src/androidTest/kotlin/io/github/skm0078/audora/pdf/pdfium/DocumentCloseTest.kt
// Purpose: Closing a book never waits for another book's page drawing to let go of PDFium's process-wide lock, and
//          the document is still closed once it does.

package io.github.skm0078.audora.pdf.pdfium

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.skm0078.audora.core.result.getOrNull
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DocumentCloseTest {
    @Test
    fun closeReturnsAtOnceWhileAnotherBookHoldsThePdfiumLock() {
        val handle = requireNotNull(TestPdfs.engine(TestPdfs.file(EN)).open(null).getOrNull())
        val holding = CountDownLatch(1)
        val release = CountDownLatch(1)
        // Another book drawing a page for OCR: it holds the lock for as long as the draw takes.
        val drawing = thread {
            pdfiumLocked {
                holding.countDown()
                release.await(WAIT_SECONDS, TimeUnit.SECONDS)
            }
        }
        try {
            assertTrue("lock held", holding.await(WAIT_SECONDS, TimeUnit.SECONDS))

            val started = System.nanoTime()
            handle.close()
            val closeMs = (System.nanoTime() - started) / NANOS_PER_MS

            assertTrue("close took $closeMs ms", closeMs < MAX_CLOSE_MS)
        } finally {
            release.countDown()
            drawing.join(TimeUnit.SECONDS.toMillis(WAIT_SECONDS))
        }
        // The lock is free again: a later open of another document goes through, so the queued close did not wedge it.
        val next = TestPdfs.engine(TestPdfs.file(EN)).open(null).getOrNull()
        assertTrue("a document opens after the close", next != null)
        next?.close()
    }

    private companion object {
        const val EN = "en-udhr-noto.pdf"
        const val WAIT_SECONDS = 10L
        const val MAX_CLOSE_MS = 200L
        const val NANOS_PER_MS = 1_000_000L
    }
}
