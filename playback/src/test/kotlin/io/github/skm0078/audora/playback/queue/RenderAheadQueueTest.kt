// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/queue/RenderAheadQueueTest.kt
// Purpose: The render queue renders its window in order, blocks readers, cancels and caches as playback moves.

package io.github.skm0078.audora.playback.queue

import io.github.skm0078.audora.core.config.QueueConfig
import io.github.skm0078.audora.core.config.RenderAheadConfig
import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.playback.page
import io.github.skm0078.audora.playback.pcm
import io.github.skm0078.audora.speech.SpeechError
import io.github.skm0078.audora.speech.audio.PcmAudio
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RenderAheadQueueTest {
    // The queue renders what it is given, up to renderAhead.maxAheadUnits; picking the window is tested apart.
    private val config =
        QueueConfig(prefetchUnits = 2, recentAudioBytes = 1_000, renderAhead = RenderAheadConfig(maxAheadUnits = 2))
    private val rendered: MutableList<UnitKey> = Collections.synchronizedList(ArrayList())
    private val failures: MutableList<UnitKey> = Collections.synchronizedList(ArrayList())
    private val listener = object : RenderListener {
        override fun onRendered(key: UnitKey, renderNanos: Long, audio: PcmAudio) {
            rendered.add(key)
        }

        override fun onRenderFailed(key: UnitKey, error: SpeechError) {
            failures.add(key)
        }
    }
    private var queue: RenderAheadQueue? = null

    @After
    fun closeQueue() {
        queue?.close()
    }

    private fun start(
        policy: RenderAheadPolicy = RenderAheadPolicy.FIXED,
        queueConfig: QueueConfig = config,
        clock: () -> Long = System::nanoTime,
        renderer: SentenceRenderer,
    ): RenderAheadQueue = RenderAheadQueue(renderer, queueConfig, listener, policy, clock).also {
        it.start("test-render")
        queue = it
    }

    /** Lets playback start only once nothing in the window is left to render. */
    private val waitForAll = object : RenderAheadPolicy {
        override fun window(candidates: List<ReadingUnit>, config: QueueConfig) = candidates

        override fun canStart(bufferedSeconds: Double, pending: List<ReadingUnit>, config: QueueConfig) =
            pending.isEmpty()
    }

    @Test
    fun firstSentenceIsHeldUntilThePolicyPredictsNoStall() {
        val gate = CountDownLatch(1)
        val q = start(waitForAll) { unit ->
            if (unit.index == 2) gate.await()
            Outcome.Success(pcm(10))
        }
        q.setWindow(page(0, 3))
        val got = AtomicReference<PcmAudio>()
        val reader = Thread { got.set(q.awaitPcm(UnitKey(0, 0))) }.apply { start() }
        Thread.sleep(SETTLE_MS)
        assertTrue("first sentence must wait for the third", reader.isAlive)
        gate.countDown()
        reader.join(JOIN_MS)
        assertEquals(10, got.get().samples.size)
        // Once started, nothing is held again.
        assertEquals(10, q.awaitPcm(UnitKey(0, 1)).samples.size)
    }

    @Test
    fun theStartHoldEndsAtItsLimitEvenIfAStallIsPredicted() {
        val now = AtomicLong(0)
        val holdConfig = config.copy(renderAhead = config.renderAhead.copy(maxStartHoldSeconds = 1.0))
        val q = start(waitForAll, holdConfig, now::get) { unit ->
            if (unit.index == 1) Thread.sleep(LONG_RENDER_MS)
            Outcome.Success(pcm(10))
        }
        q.setWindow(page(0, 2))
        val reader = Thread { q.awaitPcm(UnitKey(0, 0)) }.apply { start() }
        Thread.sleep(SETTLE_MS)
        assertTrue("held while the clock stands still", reader.isAlive)
        now.set(2_000_000_000)
        reader.join(JOIN_MS)
        assertTrue("released once the limit passed", !reader.isAlive)
    }

    @Test
    fun onlyTheWindowsFirstSentenceIsHeld() {
        val gate = CountDownLatch(1)
        val q = start(waitForAll) { unit ->
            if (unit.index == 2) gate.await()
            Outcome.Success(pcm(10))
        }
        q.setWindow(page(0, 3))
        assertEquals(10, q.awaitPcm(UnitKey(0, 1)).samples.size)
        gate.countDown()
    }

    @Test
    fun rendersTheWindowInPlaylistOrderAndNoFurther() {
        val renders = Collections.synchronizedList(ArrayList<UnitKey>())
        val q = start { unit ->
            renders.add(UnitKey.of(unit))
            Outcome.Success(pcm(10))
        }
        val units = page(0, 6)
        q.setWindow(units)
        q.awaitPcm(UnitKey(0, 2))
        waitUntil { renders.size >= 3 }
        Thread.sleep(SETTLE_MS)
        assertEquals(listOf(UnitKey(0, 0), UnitKey(0, 1), UnitKey(0, 2)), renders.toList())
    }

    @Test
    fun readerBlocksUntilItsSentenceIsRendered() {
        val gate = CountDownLatch(1)
        val q = start {
            gate.await()
            Outcome.Success(pcm(10))
        }
        q.setWindow(page(0, 1))
        val got = AtomicReference<PcmAudio>()
        val reader = Thread { got.set(q.awaitPcm(UnitKey(0, 0))) }.apply { start() }
        Thread.sleep(SETTLE_MS)
        assertTrue("reader must still be waiting", reader.isAlive)
        gate.countDown()
        reader.join(JOIN_MS)
        assertEquals(10, got.get().samples.size)
    }

    @Test
    fun aSentenceLeavingTheWindowIsCancelledMidRender() {
        val started = CountDownLatch(1)
        val interrupted = CountDownLatch(1)
        val q = start { unit ->
            if (unit.page == 0) {
                started.countDown()
                try {
                    Thread.sleep(LONG_RENDER_MS)
                } catch (e: InterruptedException) {
                    interrupted.countDown()
                    throw e
                }
            }
            Outcome.Success(pcm(10))
        }
        q.setWindow(page(0, 1))
        assertTrue(started.await(JOIN_MS, TimeUnit.MILLISECONDS))
        q.setWindow(page(5, 1))
        assertTrue("render of the old sentence must be interrupted", interrupted.await(JOIN_MS, TimeUnit.MILLISECONDS))
        assertEquals(10, q.awaitPcm(UnitKey(5, 0)).samples.size)
    }

    @Test
    fun skippingBackReplaysFromTheRecentCacheWithoutRendering() {
        val renders = Collections.synchronizedList(ArrayList<UnitKey>())
        val q = start { unit ->
            renders.add(UnitKey.of(unit))
            Outcome.Success(pcm(10))
        }
        val units = page(0, 6)
        q.setWindow(units)
        val first = q.awaitPcm(UnitKey(0, 0))
        q.setWindow(units.drop(3))
        q.awaitPcm(UnitKey(0, 3))
        q.setWindow(units)
        assertSame(first, q.awaitPcm(UnitKey(0, 0)))
        Thread.sleep(SETTLE_MS)
        assertEquals("unit 0 is rendered once", 1, renders.count { it == UnitKey(0, 0) })
    }

    @Test
    fun recentCacheIsBoundedInBytes() {
        val cache = RecentAudio(maxBytes = 100)
        cache.put(UnitKey(0, 0), pcm(30)) // 60 bytes
        cache.put(UnitKey(0, 1), pcm(30))
        assertEquals(null, cache.get(UnitKey(0, 0)))
        assertEquals(30, cache.get(UnitKey(0, 1))?.samples?.size)
        cache.put(UnitKey(0, 2), pcm(200))
        assertEquals("larger than the whole cache: not kept", null, cache.get(UnitKey(0, 2)))
    }

    @Test
    fun aFailedRenderReachesTheReaderAsATypedError() {
        val error = SpeechError.SynthesisFailed(VoiceId(EngineId.SHERPA, "test"), "boom")
        val q = start { Outcome.Failure(error) }
        q.setWindow(page(0, 1))
        try {
            q.awaitPcm(UnitKey(0, 0))
            fail("expected RenderFailedException")
        } catch (e: RenderFailedException) {
            assertEquals(UnitKey(0, 0), e.key)
            assertSame(error, e.error)
        }
        // The listener runs after readers are woken, so the reader can get here first.
        waitUntil { failures.isNotEmpty() }
        assertEquals(listOf(UnitKey(0, 0)), failures.toList())
    }

    @Test
    fun closeReleasesEveryWaitingReader() {
        val q = start { Thread.sleep(LONG_RENDER_MS).let { Outcome.Success(pcm(1)) } }
        q.setWindow(page(0, 1))
        val errors = Collections.synchronizedList(ArrayList<Throwable>())
        val readers = listOf(
            Thread { runCatching { q.awaitPcm(UnitKey(0, 0)) }.exceptionOrNull()?.let(errors::add) },
            Thread { runCatching { q.awaitClose() }.exceptionOrNull()?.let(errors::add) },
        ).onEach { it.start() }
        Thread.sleep(SETTLE_MS)
        q.close()
        readers.forEach { it.join(JOIN_MS) }
        assertEquals(2, errors.size)
        assertTrue(errors.all { it is QueueClosedException })
    }

    @Test
    fun anInterruptedReaderGetsInterruptedException() {
        val q = start { Thread.sleep(LONG_RENDER_MS).let { Outcome.Success(pcm(1)) } }
        q.setWindow(page(0, 1))
        val error = AtomicReference<Throwable>()
        val reader = Thread { runCatching { q.awaitPcm(UnitKey(0, 0)) }.exceptionOrNull()?.let(error::set) }
        reader.start()
        Thread.sleep(SETTLE_MS)
        reader.interrupt()
        reader.join(JOIN_MS)
        assertTrue(error.get() is InterruptedException)
    }

    @Test
    fun aRenderThatFinishesAfterItsSentenceLeftIsKeptForSkipBack() {
        val release = CountDownLatch(1)
        val q = start { unit ->
            if (unit.index == 0) {
                // Ignores the interrupt, like a native render that can only stop between sentences.
                while (release.count > 0) runCatching { release.await() }
            }
            Outcome.Success(pcm(10))
        }
        val units: List<ReadingUnit> = page(0, 4)
        q.setWindow(units)
        Thread.sleep(SETTLE_MS)
        q.setWindow(units.drop(1))
        release.countDown()
        q.awaitPcm(UnitKey(0, 1))
        assertEquals(10, q.awaitPcm(UnitKey(0, 0)).samples.size)
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + JOIN_MS
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) fail("condition not met in time")
            Thread.sleep(POLL_MS)
        }
    }

    private companion object {
        const val SETTLE_MS = 150L
        const val JOIN_MS = 5_000L
        const val LONG_RENDER_MS = 10_000L
        const val POLL_MS = 10L
    }
}
