// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/queue/RenderAheadQueue.kt
// Purpose: Renders the sentences ahead of the one playing and hands their PCM to readers, blocking until ready.

package io.github.skm0078.audora.playback.queue

import io.github.skm0078.audora.core.config.QueueConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.speech.SpeechError
import io.github.skm0078.audora.speech.audio.PcmAudio
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Observes the render worker; called on the worker thread. Must not block. */
interface RenderListener {
    fun onRendered(key: UnitKey, renderNanos: Long, audio: PcmAudio)

    fun onRenderFailed(key: UnitKey, error: SpeechError)
}

class QueueClosedException : IOException("Render queue is closed")

/** The engine failed on this sentence. An IOException so it reaches the player as a source error. */
class RenderFailedException(val key: UnitKey, val error: SpeechError) :
    IOException("Rendering $key failed: ${error.message}")

/**
 * One worker thread renders, in order, the sentences of the current window: the one playing plus those after it
 * that a [RenderAheadPolicy] picked, as set by [setWindow] on every playlist move. Readers (TtsDataSource
 * on ExoPlayer's loader threads) call [awaitPcm] and block until their sentence is ready, so the player waits
 * in BUFFERING and never reaches ENDED while text remains (Build Spec C5).
 *
 * A sentence that leaves the window while it renders is cancelled (the worker is interrupted, which cancels
 * the engine's coroutine). Rendered sentences that leave the window move to a small recent-audio cache, so
 * skipping back replays them without rendering again.
 *
 * A reader asking for a sentence outside the window waits until the window reaches it. That cannot deadlock:
 * the sentence playing is always the first in the window.
 *
 * Start hold (P4-02, ADR-027): each queue is one (re)start. Its first sentence is handed out only once
 * [RenderAheadPolicy.canStart] predicts the rest of the window renders in time, or after
 * `renderAhead.maxStartHoldSeconds`, so a slow voice starts a little later instead of pausing after one sentence.
 */
class RenderAheadQueue(
    private val renderer: SentenceRenderer,
    private val config: QueueConfig,
    private val listener: RenderListener,
    private val policy: RenderAheadPolicy = RenderAheadPolicy.FIXED,
    private val nanoClock: () -> Long = System::nanoTime,
) : Closeable {
    private val lock = ReentrantLock()
    private val changed = lock.newCondition()
    private var window: List<ReadingUnit> = emptyList()
    private var windowKeys: Set<UnitKey> = emptySet()
    private val ready = HashMap<UnitKey, PcmAudio>()
    private val failed = HashMap<UnitKey, SpeechError>()
    private val recent = RecentAudio(config.recentAudioBytes)
    private var rendering: UnitKey? = null
    private val startHold = StartHold(policy, config, nanoClock)
    private var closed = false
    private var worker: Thread? = null

    fun start(threadName: String) {
        lock.withLock {
            check(worker == null) { "Render queue already started" }
            check(!closed) { "Render queue is closed" }
            worker = Thread(::workerLoop, threadName).apply {
                isDaemon = true
                start()
            }
        }
    }

    /**
     * [units] are the sentence playing followed by the next ones to render, in playlist order; at most
     * `1 + renderAhead.maxAheadUnits` are kept. Call on every playlist move (transition, seek, append).
     */
    fun setWindow(units: List<ReadingUnit>) {
        val cancelled = lock.withLock {
            if (closed) return
            window = units.take(1 + config.renderAhead.maxAheadUnits)
            windowKeys = window.mapTo(HashSet(), UnitKey::of)
            // Rendered sentences leaving the window move to the recent cache; their failures are forgotten.
            ready.keys.filter { it !in windowKeys }.forEach { key -> ready.remove(key)?.let { recent.put(key, it) } }
            failed.keys.retainAll(windowKeys)
            changed.signalAll()
            rendering?.takeIf { it !in windowKeys }?.let { worker }
        }
        // Outside the lock: the worker may be about to take it. An interrupt that lands after the render finished
        // is harmless; the worker treats any interrupt while open as "re-check the window".
        cancelled?.interrupt()
    }

    /**
     * Blocks until [key] is rendered and returns its PCM. Throws [InterruptedException] when the reader is
     * cancelled, [QueueClosedException] after [close], [RenderFailedException] when the engine failed on it.
     */
    fun awaitPcm(key: UnitKey): PcmAudio {
        lock.lockInterruptibly()
        try {
            while (true) {
                if (closed) throw QueueClosedException()
                val pcm = ready[key] ?: recent.get(key)
                if (pcm != null && startHold.allows(key, window, ready, failed)) return pcm
                failed[key]?.let { throw RenderFailedException(key, it) }
                changed.await(WAIT_SLICE_MS, TimeUnit.MILLISECONDS)
            }
        } finally {
            lock.unlock()
        }
    }

    /**
     * Drops [key]'s audio, ready or recent, and any failure, so it renders afresh if it is played again: a replay's
     * slower render must not come back when the listener skips back to that sentence.
     */
    fun forget(key: UnitKey) {
        lock.withLock {
            ready.remove(key)
            recent.take(key)
            failed.remove(key)
        }
    }

    /**
     * Blocks until the queue closes, then throws [QueueClosedException]; [InterruptedException] when cancelled.
     * The playlist's "more is coming" placeholder waits here while the next page is being extracted.
     */
    fun awaitClose(): Nothing {
        lock.lockInterruptibly()
        try {
            while (!closed) changed.await(WAIT_SLICE_MS, TimeUnit.MILLISECONDS)
            throw QueueClosedException()
        } finally {
            lock.unlock()
        }
    }

    override fun close() {
        val thread = lock.withLock {
            if (closed) return
            closed = true
            ready.clear()
            failed.clear()
            recent.clear()
            changed.signalAll()
            worker.also { worker = null }
        }
        thread?.interrupt()
    }

    private fun workerLoop() {
        var unit = nextUnit()
        while (unit != null) {
            try {
                renderOne(unit)
            } catch (_: InterruptedException) {
                // The sentence left the window, or the queue closed; nextUnit() tells which.
            } finally {
                lock.withLock { rendering = null }
            }
            unit = nextUnit()
        }
    }

    /** Waits for work and marks it as rendering; null once closed. */
    private fun nextUnit(): ReadingUnit? = lock.withLock {
        var next = nextToRender()
        while (!closed && next == null) {
            try {
                changed.await()
            } catch (_: InterruptedException) {
                // A cancel aimed at a render that had already finished: just re-check.
            }
            next = nextToRender()
        }
        if (closed) null else next?.also { rendering = UnitKey.of(it) }
    }

    private fun renderOne(unit: ReadingUnit) {
        val key = UnitKey.of(unit)
        // An interrupt that landed between renders: drop it, then skip this sentence if it is no longer wanted.
        Thread.interrupted()
        if (lock.withLock { closed || key !in windowKeys }) return
        val started = nanoClock()
        when (val result = renderer.render(unit)) {
            is Outcome.Success -> {
                val renderNanos = nanoClock() - started
                val kept = lock.withLock {
                    val inWindow = !closed && key in windowKeys
                    if (inWindow) {
                        ready[key] = result.value
                    } else if (!closed) {
                        recent.put(key, result.value)
                    }
                    changed.signalAll()
                    inWindow
                }
                if (kept) listener.onRendered(key, renderNanos, result.value)
            }

            is Outcome.Failure -> {
                lock.withLock {
                    if (!closed && key in windowKeys) failed[key] = result.error
                    changed.signalAll()
                }
                listener.onRenderFailed(key, result.error)
            }
        }
    }

    /**
     * First sentence in the window that is neither rendered nor failed; a sentence back in the window that is
     * still in the recent cache moves back to ready on the way. Caller holds [lock].
     */
    private fun nextToRender(): ReadingUnit? = window.firstOrNull { unit ->
        val key = UnitKey.of(unit)
        if (key !in ready && key !in failed) {
            recent.take(key)?.let { cached ->
                ready[key] = cached
                changed.signalAll()
            }
        }
        key !in ready && key !in failed
    }

    private companion object {
        /** Readers re-check state at least this often, as a guard against a missed signal (and the start hold). */
        const val WAIT_SLICE_MS = 500L
    }
}

/**
 * The start hold of one render queue (ADR-027): until playback starts, the window's first sentence is handed out
 * only once [RenderAheadPolicy.canStart] predicts no stall, nothing is left to render, or the hold reached
 * `renderAhead.maxStartHoldSeconds`. Every other sentence, and everything after the start, passes at once.
 * Not thread-safe: the queue calls it under its lock.
 */
internal class StartHold(
    private val policy: RenderAheadPolicy,
    private val config: QueueConfig,
    private val nanoClock: () -> Long,
) {
    private var started = false
    private var heldSinceNanos: Long? = null

    fun allows(
        key: UnitKey,
        window: List<ReadingUnit>,
        ready: Map<UnitKey, PcmAudio>,
        failed: Map<UnitKey, SpeechError>,
    ): Boolean {
        val holding = !started && window.firstOrNull()?.let { UnitKey.of(it) == key } == true
        if (holding && readyToStart(key, window, ready, failed)) started = true
        return !holding || started
    }

    private fun readyToStart(
        key: UnitKey,
        window: List<ReadingUnit>,
        ready: Map<UnitKey, PcmAudio>,
        failed: Map<UnitKey, SpeechError>,
    ): Boolean {
        val now = nanoClock()
        val heldSince = heldSinceNanos ?: now.also { heldSinceNanos = it }
        val heldSeconds = (now - heldSince) / NANOS_PER_SECOND
        val rendered = window.takeWhile { UnitKey.of(it) in ready }
        val bufferedSeconds = rendered.sumOf { requireNotNull(ready[UnitKey.of(it)]).durationMs } / MS_PER_SECOND
        val pending = window.drop(rendered.size).filter { UnitKey.of(it) !in failed }
        val go = pending.isEmpty() || heldSeconds >= config.renderAhead.maxStartHoldSeconds ||
            policy.canStart(bufferedSeconds, pending, config)
        if (go && heldSeconds > 0) {
            val heldMs = (heldSeconds * MS_PER_SECOND).toLong()
            AudoraLog.d(TAG, "Start held $heldMs ms for $key: buffered ${bufferedSeconds}s, ${pending.size} pending")
        }
        return go
    }

    private companion object {
        const val TAG = "Playback.Queue"
        const val NANOS_PER_SECOND = 1_000_000_000.0
        const val MS_PER_SECOND = 1_000.0
    }
}

/** Least-recently-used PCM of sentences already played, bounded in bytes. Not thread-safe: the queue locks. */
internal class RecentAudio(private val maxBytes: Long) {
    private val entries = LinkedHashMap<UnitKey, PcmAudio>(INITIAL_CAPACITY, LOAD_FACTOR, true)
    private var bytes = 0L

    fun get(key: UnitKey): PcmAudio? = entries[key]

    fun take(key: UnitKey): PcmAudio? = entries.remove(key)?.also { bytes -= it.byteSize() }

    fun put(key: UnitKey, audio: PcmAudio) {
        entries.remove(key)?.let { bytes -= it.byteSize() }
        if (audio.byteSize() > maxBytes) return
        entries[key] = audio
        bytes += audio.byteSize()
        val oldest = entries.entries.iterator()
        while (bytes > maxBytes && oldest.hasNext()) {
            bytes -= oldest.next().value.byteSize()
            oldest.remove()
        }
    }

    fun clear() {
        entries.clear()
        bytes = 0
    }

    private fun PcmAudio.byteSize(): Long = samples.size.toLong() * Short.SIZE_BYTES

    private companion object {
        const val INITIAL_CAPACITY = 16
        const val LOAD_FACTOR = 0.75f
    }
}
