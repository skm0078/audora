// File: core/src/main/kotlin/io/github/skm0078/audora/core/diagnostics/StallWatchdog.kt
// Purpose: Logs when the main thread stops answering, with where it is stuck, before Android calls it an ANR.

package io.github.skm0078.audora.core.diagnostics

import io.github.skm0078.audora.core.config.DiagnosticsConfig
import io.github.skm0078.audora.core.logging.AudoraLog
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * A regression guard for "the app must never hang": every `stallCheckEveryMs` it posts a no-op to [watched]'s queue
 * through [post] and, when that has not run within `stallAfterMs`, logs a warning with [watched]'s stack (the code
 * blocking it: a lock held by OCR, a disk read), then one line with the stall's length once it answers. The lines go
 * to the shared log bundle, so a hang on a phone is diagnosable from "Share logs". Logs code locations, never text.
 *
 * Its own daemon thread; [start] and [stop] from any thread. Debug and dev builds start it (`AudoraApplication`).
 */
class StallWatchdog(
    private val watched: Thread,
    private val post: (Runnable) -> Unit,
    private val config: DiagnosticsConfig = DiagnosticsConfig(),
) {
    @Volatile
    private var worker: Thread? = null

    @Synchronized
    fun start() {
        if (worker != null) return
        worker = Thread(::watch, THREAD_NAME).apply {
            isDaemon = true
            priority = Thread.MIN_PRIORITY
            start()
        }
        AudoraLog.i(TAG, "Stall watchdog on, threshold=${config.stallAfterMs} ms thread=${watched.name}")
    }

    @Synchronized
    fun stop() {
        worker?.interrupt()
        worker = null
    }

    private fun watch() {
        try {
            while (!Thread.currentThread().isInterrupted) {
                val answered = CountDownLatch(1)
                val postedAt = System.nanoTime()
                post(Runnable { answered.countDown() })
                if (!answered.await(config.stallAfterMs, TimeUnit.MILLISECONDS)) {
                    AudoraLog.w(TAG, "Main thread stuck for ${config.stallAfterMs}+ ms at ${stackOf(watched)}")
                    answered.await()
                    AudoraLog.w(TAG, "Main thread answered after ${(System.nanoTime() - postedAt) / NANOS_PER_MS} ms")
                }
                Thread.sleep(config.stallCheckEveryMs)
            }
        } catch (_: InterruptedException) {
            // Stopped.
        }
    }

    private fun stackOf(thread: Thread): String = thread.stackTrace
        .take(config.stallStackFrames)
        .joinToString(" < ") { "${it.className}.${it.methodName}:${it.lineNumber}" }

    private companion object {
        const val TAG = "Audora.Stall"
        const val THREAD_NAME = "audora-stall-watchdog"
        const val NANOS_PER_MS = 1_000_000L
    }
}
