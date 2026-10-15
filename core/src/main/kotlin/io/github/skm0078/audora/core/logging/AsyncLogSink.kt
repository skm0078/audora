// File: core/src/main/kotlin/io/github/skm0078/audora/core/logging/AsyncLogSink.kt
// Purpose: Moves a slow sink (file I/O) onto one background thread, preserving entry order.

package io.github.skm0078.audora.core.logging

import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class AsyncLogSink(
    private val delegate: LogSink,
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, THREAD_NAME).apply { isDaemon = true }
    },
) : LogSink {
    override fun write(entry: LogEntry) {
        try {
            executor.execute { delegate.write(entry) }
        } catch (e: RejectedExecutionException) {
            System.err.println("AsyncLogSink: dropped entry after shutdown: $e")
        }
    }

    /**
     * Blocks until every entry queued so far is written, or [timeoutMillis] passes.
     * Used by the crash handler so the fatal stack trace reaches disk before the process dies.
     * Returns false if the queue did not drain in time.
     */
    fun flush(timeoutMillis: Long): Boolean = try {
        executor.submit {}.get(timeoutMillis, TimeUnit.MILLISECONDS)
        true
    } catch (e: TimeoutException) {
        System.err.println("AsyncLogSink: flush timed out after $timeoutMillis ms: $e")
        false
    } catch (e: ExecutionException) {
        System.err.println("AsyncLogSink: flush failed: $e")
        false
    } catch (e: RejectedExecutionException) {
        System.err.println("AsyncLogSink: flush after shutdown: $e")
        false
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        System.err.println("AsyncLogSink: flush interrupted: $e")
        false
    }

    private companion object {
        const val THREAD_NAME = "audora-log-writer"
    }
}
