// File: spikes/playback/src/main/kotlin/io/github/skm0078/audora/spike/playback/queue/RenderAheadQueue.kt
// Purpose: Renders units ahead of the one playing and hands PCM to readers, blocking them until it is ready.

package io.github.skm0078.audora.spike.playback.queue

import io.github.skm0078.audora.spike.playback.engine.FakeBook
import io.github.skm0078.audora.spike.playback.engine.UnitRenderer
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Observes the render worker; called on the worker thread. */
interface RenderListener {
    fun onRendered(unitIndex: Int, renderNanos: Long, audioMs: Long)

    fun onRenderFailed(unitIndex: Int, error: Throwable)
}

class QueueClosedException : IOException("Render queue is closed")

class RenderFailedException(unitIndex: Int, cause: Throwable) : IOException("Rendering unit $unitIndex failed", cause)

/**
 * One worker thread renders, in order, the units in the window [playing, playing + prefetch].
 * Readers (TtsDataSource on ExoPlayer's loader threads) call [awaitPcm] and block until their unit is ready.
 * Because a reader blocks instead of failing, the player waits in BUFFERING and never reaches ENDED (C5).
 *
 * A reader asking beyond the window simply waits until playback moves the window forward. That cannot
 * deadlock: the next unit to play is always inside the window (prefetch >= 1).
 */
class RenderAheadQueue(
    private val book: FakeBook,
    private val renderer: UnitRenderer,
    private val prefetchUnits: Int,
    private val listener: RenderListener,
    private val nanoClock: () -> Long = System::nanoTime,
) : Closeable {
    private val lock = ReentrantLock()
    private val changed = lock.newCondition()
    private val ready = HashMap<Int, ShortArray>()
    private val failed = HashMap<Int, Throwable>()
    private var playingIndex = 0
    private var closed = false
    private var worker: Thread? = null

    init {
        require(prefetchUnits >= 1) { "prefetchUnits must be at least 1, was $prefetchUnits" }
    }

    fun start(threadName: String = DEFAULT_THREAD_NAME) {
        lock.withLock {
            check(worker == null) { "Render queue already started" }
            check(!closed) { "Render queue is closed" }
            worker = Thread(::workerLoop, threadName).apply {
                isDaemon = true
                start()
            }
        }
    }

    /** Moves the window to [index]; drops PCM behind it. Call on every media item transition and seek. */
    fun onPlaying(index: Int) {
        lock.withLock {
            playingIndex = index.coerceIn(0, book.unitCount - 1)
            ready.keys.removeAll { it < playingIndex }
            failed.keys.removeAll { it < playingIndex }
            changed.signalAll()
        }
    }

    /**
     * Blocks until [unitIndex] is rendered and returns its PCM.
     * Throws [InterruptedException] when the reader is cancelled, [QueueClosedException] after [close],
     * and [RenderFailedException] when the engine failed on this unit.
     */
    fun awaitPcm(unitIndex: Int): ShortArray {
        lock.lockInterruptibly()
        try {
            while (true) {
                if (closed) throw QueueClosedException()
                ready[unitIndex]?.let { return it }
                failed[unitIndex]?.let { throw RenderFailedException(unitIndex, it) }
                changed.await(WAIT_SLICE_MS, TimeUnit.MILLISECONDS)
            }
        } finally {
            lock.unlock()
        }
    }

    /** Units rendered and not yet played, for the live stats panel. */
    fun readyAheadCount(): Int = lock.withLock { ready.keys.count { it >= playingIndex } }

    override fun close() {
        val thread = lock.withLock {
            closed = true
            ready.clear()
            failed.clear()
            changed.signalAll()
            worker.also { worker = null }
        }
        thread?.interrupt()
    }

    private fun workerLoop() {
        try {
            while (true) {
                val unitIndex = lock.withLock {
                    var next = nextToRender()
                    while (!closed && next == null) {
                        changed.await()
                        next = nextToRender()
                    }
                    if (closed) return
                    checkNotNull(next)
                }
                renderOne(unitIndex)
            }
        } catch (_: InterruptedException) {
            // close() interrupts the worker; nothing left to do.
        }
    }

    @Suppress("TooGenericExceptionCaught") // An engine failure must reach the reader as an error, not kill the worker.
    private fun renderOne(unitIndex: Int) {
        val unit = book.unit(unitIndex)
        val started = nanoClock()
        try {
            val pcm = renderer.render(unit)
            val renderNanos = nanoClock() - started
            lock.withLock {
                if (!closed && unitIndex >= playingIndex) ready[unitIndex] = pcm
                changed.signalAll()
            }
            listener.onRendered(unitIndex, renderNanos, unit.durationMs)
        } catch (e: InterruptedException) {
            throw e
        } catch (e: Exception) {
            lock.withLock {
                failed[unitIndex] = e
                changed.signalAll()
            }
            listener.onRenderFailed(unitIndex, e)
        }
    }

    /** Lowest unit in the window that is neither rendered nor failed. Caller holds [lock]. */
    private fun nextToRender(): Int? {
        val last = minOf(playingIndex + prefetchUnits, book.unitCount - 1)
        return (playingIndex..last).firstOrNull { it !in ready && it !in failed }
    }

    private companion object {
        const val DEFAULT_THREAD_NAME = "S3-render"

        /** Readers re-check state at least this often, as a guard against a missed signal. */
        const val WAIT_SLICE_MS = 500L
    }
}
