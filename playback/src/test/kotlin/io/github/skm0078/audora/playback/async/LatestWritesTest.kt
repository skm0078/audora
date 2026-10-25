// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/async/LatestWritesTest.kt
// Purpose: A burst of settings changes is saved once with its last value, in order, and nothing is ever dropped.

package io.github.skm0078.audora.playback.async

import java.util.concurrent.Executor
import org.junit.Assert.assertEquals
import org.junit.Test

class LatestWritesTest {
    /** Runs queued writes only when told: the background thread, held still. */
    private class HeldExecutor : Executor {
        val queued = ArrayDeque<Runnable>()

        override fun execute(command: Runnable) {
            queued.addLast(command)
        }

        fun runAll() {
            while (queued.isNotEmpty()) queued.removeFirst().run()
        }
    }

    private val written = mutableListOf<Int>()

    @Test
    fun `a burst before the write runs is written once, with its last value`() {
        val executor = HeldExecutor()
        val writes = LatestWrites<Int>(executor) { written += it }

        (1..10).forEach(writes::submit)
        executor.runAll()

        assertEquals(listOf(10), written)
    }

    @Test
    fun `a value submitted after a write started is written too`() {
        val executor = HeldExecutor()
        val writes = LatestWrites<Int>(executor) { written += it }

        writes.submit(1)
        executor.runAll()
        writes.submit(2)
        writes.submit(3)
        executor.runAll()

        assertEquals(listOf(1, 3), written)
    }

    @Test
    fun `writing now saves every value at once`() {
        val writes = LatestWrites<Int>(LatestWrites.NOW) { written += it }

        writes.submit(1)
        writes.submit(2)

        assertEquals(listOf(1, 2), written)
    }
}
