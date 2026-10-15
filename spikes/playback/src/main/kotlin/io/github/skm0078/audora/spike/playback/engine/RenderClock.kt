// File: spikes/playback/src/main/kotlin/io/github/skm0078/audora/spike/playback/engine/RenderClock.kt
// Purpose: How the fake engine passes time, behind an interface so tests run instantly.

package io.github.skm0078.audora.spike.playback.engine

import io.github.skm0078.audora.spike.playback.config.EngineWork

interface RenderClock {
    fun nowNanos(): Long

    /** Lets [nanos] pass. Throws [InterruptedException] if the thread is interrupted, so a cancelled render stops. */
    fun spend(nanos: Long)

    companion object {
        fun forWork(work: EngineWork): RenderClock = when (work) {
            EngineWork.SLEEP -> SleepingClock
            EngineWork.BUSY -> BusyClock
        }
    }
}

/** Uses System.nanoTime, which stops while the CPU is suspended: exactly the stall a missing wake lock causes. */
object SleepingClock : RenderClock {
    override fun nowNanos(): Long = System.nanoTime()

    override fun spend(nanos: Long) {
        if (nanos <= 0) return
        Thread.sleep(nanos / NANOS_PER_MILLI, (nanos % NANOS_PER_MILLI).toInt())
    }
}

/** Keeps one core busy, like neural synthesis. */
object BusyClock : RenderClock {
    @Volatile
    private var sink = 0.0

    override fun nowNanos(): Long = System.nanoTime()

    override fun spend(nanos: Long) {
        val deadline = System.nanoTime() + nanos
        var acc = 0.0
        while (System.nanoTime() < deadline) {
            if (Thread.interrupted()) throw InterruptedException("Busy render interrupted")
            repeat(BUSY_BATCH) { acc += kotlin.math.sin(acc + it) }
        }
        sink = acc // Keeps the loop from being optimized away.
    }

    private const val BUSY_BATCH = 2_000
}

private const val NANOS_PER_MILLI = 1_000_000L
