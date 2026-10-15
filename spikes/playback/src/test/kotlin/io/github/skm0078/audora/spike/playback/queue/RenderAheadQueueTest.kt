// File: spikes/playback/src/test/kotlin/io/github/skm0078/audora/spike/playback/queue/RenderAheadQueueTest.kt
// Purpose: Readers block until their unit is rendered, rendering stays inside the window, and close() frees readers.

package io.github.skm0078.audora.spike.playback.queue

import io.github.skm0078.audora.spike.playback.config.SpikeConfig
import io.github.skm0078.audora.spike.playback.engine.FakeBook
import io.github.skm0078.audora.spike.playback.engine.FakeUnit
import io.github.skm0078.audora.spike.playback.engine.UnitRenderer
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderAheadQueueTest {
    private val book = FakeBook(SpikeConfig(unitCount = 50))
    private val executor = Executors.newCachedThreadPool()

    /** Renders one unit per permit, so the test controls exactly how far rendering gets. */
    private class GatedRenderer : UnitRenderer {
        val permits = Semaphore(0)
        val rendered: MutableList<Int> = Collections.synchronizedList(mutableListOf())
        var failOn: Int? = null

        override fun render(unit: FakeUnit): ShortArray {
            permits.acquire()
            rendered += unit.index
            if (unit.index == failOn) error("engine exploded")
            return shortArrayOf(unit.index.toShort())
        }
    }

    private class RecordingListener : RenderListener {
        val failures = CountDownLatch(1)

        override fun onRendered(unitIndex: Int, renderNanos: Long, audioMs: Long) = Unit

        override fun onRenderFailed(unitIndex: Int, error: Throwable) = failures.countDown()
    }

    private val renderer = GatedRenderer()
    private val listener = RecordingListener()
    private val queue = RenderAheadQueue(book, renderer, prefetchUnits = 2, listener = listener)

    @After
    fun tearDown() {
        queue.close()
        executor.shutdownNow()
    }

    @Test
    fun `reader blocks until its unit is rendered`() {
        queue.start()
        val read = executor.submit<ShortArray> { queue.awaitPcm(0) }

        assertBlocked(read)
        renderer.permits.release()

        assertEquals(0, read.get(TIMEOUT_S, TimeUnit.SECONDS)[0].toInt())
    }

    @Test
    fun `rendering stops at the prefetch window and resumes when playback moves`() {
        queue.start()
        renderer.permits.release(10)
        waitUntil { renderer.rendered.size == 3 } // units 0, 1, 2

        assertEquals(listOf(0, 1, 2), renderer.rendered.toList())
        queue.onPlaying(2)
        waitUntil { renderer.rendered.size == 5 }

        assertEquals(listOf(0, 1, 2, 3, 4), renderer.rendered.toList())
        assertEquals(3, queue.readyAheadCount())
    }

    @Test
    fun `reader beyond the window waits for playback to reach it`() {
        queue.start()
        renderer.permits.release(10)
        val read = executor.submit<ShortArray> { queue.awaitPcm(4) }

        assertBlocked(read)
        queue.onPlaying(3)

        assertEquals(4, read.get(TIMEOUT_S, TimeUnit.SECONDS)[0].toInt())
    }

    @Test
    fun `close frees blocked readers with QueueClosedException`() {
        queue.start()
        val read = executor.submit<ShortArray> { queue.awaitPcm(0) }
        assertBlocked(read)

        queue.close()

        val cause = runCatching { read.get(TIMEOUT_S, TimeUnit.SECONDS) }.exceptionOrNull()?.cause
        assertTrue("got $cause", cause is QueueClosedException)
    }

    @Test
    fun `interrupting a reader throws InterruptedException`() {
        queue.start()
        val interrupted = CountDownLatch(1)
        val reader = Thread {
            try {
                queue.awaitPcm(0)
            } catch (_: InterruptedException) {
                interrupted.countDown()
            }
        }
        reader.start()
        Thread.sleep(BLOCK_CHECK_MS)

        reader.interrupt()

        assertTrue(interrupted.await(TIMEOUT_S, TimeUnit.SECONDS))
    }

    @Test
    fun `engine failure reaches the reader and the worker keeps going`() {
        renderer.failOn = 0
        queue.start()
        renderer.permits.release(2)
        val failedRead = executor.submit<ShortArray> { queue.awaitPcm(0) }

        val cause = runCatching { failedRead.get(TIMEOUT_S, TimeUnit.SECONDS) }.exceptionOrNull()?.cause
        assertTrue("got $cause", cause is RenderFailedException)
        assertTrue(listener.failures.await(TIMEOUT_S, TimeUnit.SECONDS))
        assertEquals(1, queue.awaitPcm(1)[0].toInt())
    }

    private fun assertBlocked(future: Future<*>) {
        val result = runCatching { future.get(BLOCK_CHECK_MS, TimeUnit.MILLISECONDS) }
        assertTrue("expected the reader to block", result.exceptionOrNull() is TimeoutException)
        assertFalse(future.isDone)
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_S)
        while (!condition()) {
            check(System.nanoTime() < deadline) { "condition not met in time" }
            Thread.sleep(POLL_MS)
        }
        Thread.sleep(SETTLE_MS) // Lets the worker overshoot if it were going to.
    }

    private companion object {
        const val TIMEOUT_S = 5L
        const val BLOCK_CHECK_MS = 200L
        const val POLL_MS = 10L
        const val SETTLE_MS = 150L
    }
}
