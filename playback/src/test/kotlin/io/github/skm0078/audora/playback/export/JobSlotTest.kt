// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/export/JobSlotTest.kt
// Purpose: A job slot refuses a second run until the first thread has ended, and frees itself after a failure;
//          short work on a job's files holds it on the calling thread.

package io.github.skm0078.audora.playback.export

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun `short work holds the slot on the calling thread, so a job cannot start meanwhile`() {
        var refused = false
        val result = slot.runIfFree {
            refused = !slot.launch("job") {}
            42
        }
        assertEquals(42, result)
        assertTrue(refused)
        assertFalse(slot.busy)
    }

    @Test
    fun `short work is skipped while a job runs, and an interrupt meant for it is not left on the thread`() {
        val release = CountDownLatch(1)
        assertTrue(
            slot.launch("job") {
                var released = false
                while (!released) released = awaitIgnoringInterrupts(release)
            },
        )
        assertNull(slot.runIfFree { error("must not run") })
        release.countDown()
        waitUntilFree()

        slot.runIfFree { slot.interrupt() }
        assertFalse(Thread.currentThread().isInterrupted)
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
