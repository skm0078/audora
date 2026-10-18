// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/export/JobSlotTest.kt
// Purpose: A job slot refuses a second run until the first thread has ended, and frees itself after a failure.

package io.github.skm0078.audora.playback.export

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JobSlotTest {
    private val slot = JobSlot()

    @Test
    fun `a second run is refused while the first thread runs, even after an interrupt`() {
        val release = CountDownLatch(1)
        val ended = CountDownLatch(1)
        assertTrue(
            slot.launch("first") {
                // Like a native call that ignores the interrupt for a while.
                var released = false
                while (!released) released = awaitIgnoringInterrupts(release)
                ended.countDown()
            },
        )

        slot.interrupt()
        assertTrue(slot.busy)
        assertFalse(slot.launch("second") {})

        release.countDown()
        assertTrue(ended.await(1, TimeUnit.SECONDS))
        waitUntilFree()
        assertTrue(slot.launch("third") {})
    }

    @Test
    fun `a job that throws frees the slot`() {
        val thrown = CountDownLatch(1)
        assertTrue(
            slot.launch("failing") {
                thrown.countDown()
                error("boom")
            },
        )
        assertTrue(thrown.await(1, TimeUnit.SECONDS))
        waitUntilFree()
        assertFalse(slot.busy)
    }

    @Suppress("SwallowedException") // The point: this job does not stop when interrupted.
    private fun awaitIgnoringInterrupts(latch: CountDownLatch): Boolean = try {
        latch.await(WAIT_MS, TimeUnit.MILLISECONDS)
    } catch (e: InterruptedException) {
        false
    }

    private fun waitUntilFree() {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1)
        while (slot.busy && System.nanoTime() < deadline) Thread.sleep(WAIT_MS)
    }

    private companion object {
        const val WAIT_MS = 5L
    }
}
