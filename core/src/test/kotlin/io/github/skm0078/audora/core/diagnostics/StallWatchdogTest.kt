// File: core/src/test/kotlin/io/github/skm0078/audora/core/diagnostics/StallWatchdogTest.kt
// Purpose: The stall watchdog stays quiet while the watched thread answers and logs where it is stuck when not.

package io.github.skm0078.audora.core.diagnostics

import io.github.skm0078.audora.core.config.DiagnosticsConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.logging.LogEntry
import io.github.skm0078.audora.core.logging.LogLevel
import io.github.skm0078.audora.core.logging.LogSink
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StallWatchdogTest {
    private val logs = CopyOnWriteArrayList<LogEntry>()
    private lateinit var watchedThread: Thread
    private val main = Executors.newSingleThreadExecutor { task ->
        Thread(task, "fake-main").also { watchedThread = it }
    }
    private val config = DiagnosticsConfig(stallAfterMs = 100, stallCheckEveryMs = 20, stallStackFrames = 12)

    init {
        AudoraLog.install(LogSink { logs += it })
        main.submit {}.get() // Starts the thread.
    }

    @After
    fun tearDown() {
        main.shutdownNow()
        AudoraLog.install()
    }

    private fun warnings() = logs.filter { it.level == LogLevel.WARN }

    @Test
    fun `a responsive thread logs nothing`() {
        val watchdog = StallWatchdog(watchedThread, main::execute, config).apply { start() }
        Thread.sleep(QUIET_MS)
        watchdog.stop()
        assertEquals(emptyList<LogEntry>(), warnings())
    }

    @Test
    fun `a blocked thread is logged with where it is stuck, then how long it was stuck`() {
        val watchdog = StallWatchdog(watchedThread, main::execute, config).apply { start() }
        val release = CountDownLatch(1)
        main.execute { blockedOnALock(release) }
        Thread.sleep(BLOCK_MS)
        release.countDown()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
        while (warnings().size < 2 && System.nanoTime() < deadline) Thread.sleep(10)
        watchdog.stop()
        val (stuck, answered) = warnings().take(2).map { it.message }
        assertTrue(stuck, stuck.startsWith("Main thread stuck for 100+ ms at "))
        assertTrue(stuck, "blockedOnALock" in stuck)
        assertTrue(answered, answered.startsWith("Main thread answered after "))
    }

    private fun blockedOnALock(release: CountDownLatch) {
        release.await()
    }

    private companion object {
        const val QUIET_MS = 300L
        const val BLOCK_MS = 400L
    }
}
