// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/upnext/UpNextStore.kt
// Purpose: Keeps the Up next queue (plan item 25) in one small file, shared by the screens and the playback service.

package io.github.skm0078.audora.playback.upnext

import android.content.Context
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.upnext.UpNextQueue
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * One instance per process (an app singleton), so the reader's edits and the service's advance see one queue.
 * [queue] changes at once; the file is written after, one write at a time and always the latest queue, so quick edits
 * end with the file matching the screen. A missing or unreadable file means an empty queue, never an error.
 *
 * @param writes runs the file writes; it must run one at a time (see [forContext]).
 */
class UpNextStore(private val file: File, private val scope: CoroutineScope, private val writes: CoroutineDispatcher) {
    private val lock = Any()
    private val mutableQueue = MutableStateFlow(load())
    val queue: StateFlow<UpNextQueue> = mutableQueue.asStateFlow()

    /** Applies [edit] to the queue and saves the result; safe from any thread. */
    fun update(edit: (UpNextQueue) -> UpNextQueue) {
        synchronized(lock) {
            val next = edit(mutableQueue.value)
            if (next == mutableQueue.value) return
            mutableQueue.value = next
        }
        scope.launch(writes) { save(mutableQueue.value) }
    }

    private fun save(queue: UpNextQueue) {
        val temp = File(file.path + TEMP_SUFFIX)
        try {
            file.parentFile?.mkdirs()
            temp.writeText(queue.encode(), Charsets.UTF_8)
            if (!temp.renameTo(file)) throw IOException("cannot replace ${file.name}")
            AudoraLog.d(TAG, "Up next saved: ${queue.entries.size} books")
        } catch (e: IOException) {
            AudoraLog.e(TAG, "Could not save Up next; it applies until the app stops", e)
        }
    }

    private fun load(): UpNextQueue = try {
        UpNextQueue.decode(file.takeIf { it.isFile }?.readText(Charsets.UTF_8))
    } catch (e: IOException) {
        AudoraLog.w(TAG, "Up next unreadable, starting empty: ${e.message}")
        UpNextQueue()
    }

    companion object {
        private const val TAG = "Playback.UpNext"
        private const val FILE_NAME = "up-next.txt"
        private const val TEMP_SUFFIX = ".tmp"

        fun forContext(context: Context, scope: CoroutineScope, io: CoroutineDispatcher): UpNextStore =
            UpNextStore(File(context.applicationContext.filesDir, FILE_NAME), scope, io.limitedParallelism(1))
    }
}
