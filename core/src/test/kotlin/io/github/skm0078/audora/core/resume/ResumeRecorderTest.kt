// File: core/src/test/kotlin/io/github/skm0078/audora/core/resume/ResumeRecorderTest.kt
// Purpose: Position saves are throttled, flushed on pause, survive failures, and resume across a pipeline change.

package io.github.skm0078.audora.core.resume

import io.github.skm0078.audora.core.config.ResumeConfig
import io.github.skm0078.audora.core.library.StorageError
import io.github.skm0078.audora.core.result.Outcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResumeRecorderTest {
    private val store = FakeResumeAnchorStore()
    private val page = unitsOf(2, "One sentence here.", "Two sentence here.", "Three sentence here.")

    private fun TestScope.recorder(pipelineVersion: Int = 1) = ResumeRecorder(
        store = store,
        scope = backgroundScope,
        pipelineVersion = pipelineVersion,
        clock = { testScheduler.currentTime },
        config = ResumeConfig(minWriteIntervalMs = INTERVAL),
    )

    @Test
    fun `the first sentence is saved at once`() = runTest {
        val recorder = recorder()

        recorder.onUnitStarted(BOOK, page[0])
        runCurrent()

        assertEquals(listOf(page[0].charStart), store.writes.map { it.charStart })
        assertEquals(2, store.anchors.getValue(BOOK).page)
    }

    @Test
    fun `sentences inside the interval collapse into one write of the newest`() = runTest {
        val recorder = recorder()
        recorder.onUnitStarted(BOOK, page[0])
        runCurrent()

        recorder.onUnitStarted(BOOK, page[1])
        advanceTimeBy(INTERVAL / 2)
        recorder.onUnitStarted(BOOK, page[2])
        runCurrent()
        assertEquals(1, store.writes.size)

        advanceTimeBy(INTERVAL)
        assertEquals(listOf(page[0].charStart, page[2].charStart), store.writes.map { it.charStart })
    }

    @Test
    fun `pause writes the pending sentence immediately`() = runTest {
        val recorder = recorder()
        recorder.onUnitStarted(BOOK, page[0])
        runCurrent()
        recorder.onUnitStarted(BOOK, page[1])

        recorder.flush()

        assertEquals(page[1].charStart, store.anchors.getValue(BOOK).charStart)
        advanceTimeBy(INTERVAL * 2)
        assertEquals("the cancelled timer must not write again", 2, store.writes.size)
    }

    @Test
    fun `restarting the same sentence is not a new write`() = runTest {
        val recorder = recorder()
        recorder.onUnitStarted(BOOK, page[0])
        runCurrent()

        recorder.onUnitStarted(BOOK, page[0])
        advanceTimeBy(INTERVAL * 2)

        assertEquals(1, store.writes.size)
    }

    @Test
    fun `a failed write is logged and the next sentence tries again`() = runTest {
        val recorder = recorder()
        store.failWrites = true
        recorder.onUnitStarted(BOOK, page[0])
        runCurrent()
        assertNull(store.anchors[BOOK])

        store.failWrites = false
        recorder.onUnitStarted(BOOK, page[1])
        runCurrent()

        assertEquals(page[1].charStart, store.anchors.getValue(BOOK).charStart)
    }

    @Test
    fun `a sentence that starts while a write is still running is written next`() = runTest {
        // Regression: the scheduled job is still active while its write runs; the next sentence must not be lost.
        val gate = CompletableDeferred<Unit>()
        val slowStore = object : ResumeAnchorStore by store {
            override suspend fun put(anchor: ResumeAnchor): Outcome<Unit, StorageError> {
                if (store.writes.isEmpty()) gate.await()
                return store.put(anchor)
            }
        }
        val recorder = ResumeRecorder(slowStore, backgroundScope, 1, { testScheduler.currentTime })
        recorder.onUnitStarted(BOOK, page[0])
        runCurrent()

        recorder.onUnitStarted(BOOK, page[1])
        gate.complete(Unit)
        advanceTimeBy(INTERVAL * 2)

        assertEquals(listOf(page[0].charStart, page[1].charStart), store.writes.map { it.charStart })
    }

    @Test
    fun `flush with nothing pending writes nothing`() = runTest {
        recorder().flush()

        assertEquals(0, store.writes.size)
    }

    @Test
    fun `a kill mid-sentence then a pipeline change still resumes at that sentence`() = runTest {
        // Done-when of P1-06: changing PIPELINE_VERSION still resumes at the same sentence.
        val recorder = recorder(pipelineVersion = 1)
        recorder.onUnitStarted(BOOK, page[0])
        runCurrent()
        recorder.onUnitStarted(BOOK, page[1])
        advanceTimeBy(INTERVAL + 1)
        // Process killed here: no flush. Version 2 extracts the page with a header removed, so offsets move.
        val newPage = unitsOf(2, "One sentence here.", "Two sentence here.", "Three sentence here.", startOffset = 41)
        val locator = ResumeLocator(store, pipelineVersion = 2)

        val point = locator.locate(BOOK, pageCount = 10) { newPage }

        assertEquals(ResumePoint(2, newPage[1].charStart, AnchorMatch.HEAD_FINGERPRINT), point)
    }

    private companion object {
        const val INTERVAL = 2_000L
    }
}
