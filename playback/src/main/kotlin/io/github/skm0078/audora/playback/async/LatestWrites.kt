// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/async/LatestWrites.kt
// Purpose: Saves a small settings file off the main thread, and only the latest value of a burst: a slider dragged
//          through ten speed steps writes the file once or twice, not ten times on the main thread.

package io.github.skm0078.audora.playback.async

import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

/**
 * [submit] returns at once; [write] runs on [executor] with the newest value submitted since the last write. Values
 * are written in the order submitted and the last one is always written. Callers keep the value in memory too, so
 * reads never wait for the file.
 */
class LatestWrites<T : Any>(private val executor: Executor = SETTINGS_WRITER, private val write: (T) -> Unit) {
    private val pending = AtomicReference<T?>(null)

    fun submit(value: T) {
        // Only the submit that finds nothing pending schedules a write; later ones replace the value it will take.
        if (pending.getAndSet(value) == null) executor.execute { pending.getAndSet(null)?.let(write) }
    }

    companion object {
        /** One thread for every settings file, so two saves of one file never overlap. */
        val SETTINGS_WRITER: Executor by lazy {
            Executors.newSingleThreadExecutor { task -> Thread(task, WRITER_THREAD).apply { isDaemon = true } }
        }

        private const val WRITER_THREAD = "audora-settings-writer"

        /** Writes on the caller's thread: for tests. */
        val NOW: Executor = Executor(Runnable::run)
    }
}
