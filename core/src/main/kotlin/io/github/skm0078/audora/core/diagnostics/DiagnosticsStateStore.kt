// File: core/src/main/kotlin/io/github/skm0078/audora/core/diagnostics/DiagnosticsStateStore.kt
// Purpose: Crash-safe on-disk state for diagnostics: the reading-session marker and the last exit already logged.

package io.github.skm0078.audora.core.diagnostics

import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties

/**
 * A small properties file, replaced atomically (write a temp file, fsync, rename) so a process killed
 * mid-write leaves either the old state or the new one, never a torn file.
 *
 * A plain file rather than DataStore: startup must read it synchronously before any UI exists, and a
 * write must be on disk before the process can be killed. Calls do blocking I/O; keep them off the main thread.
 */
class DiagnosticsStateStore(private val file: File) {
    private val lock = Any()

    fun readSession(): ReadingSession? = synchronized(lock) {
        val props = load()
        val bookId = props.getProperty(KEY_BOOK_ID) ?: return null
        val page = props.getProperty(KEY_PAGE)?.toIntOrNull()?.takeIf { it >= 0 }
        val startedAt = props.getProperty(KEY_STARTED_AT)?.toLongOrNull()
        if (bookId.isBlank() || page == null || startedAt == null) {
            AudoraLog.w(TAG, "Ignoring malformed session marker in ${file.name}: book=$bookId page=$page")
            return null
        }
        ReadingSession(bookId, page, startedAt)
    }

    fun writeSession(session: ReadingSession?): Outcome<Unit, StateWriteError> = update { props ->
        if (session == null) {
            props.remove(KEY_BOOK_ID)
            props.remove(KEY_PAGE)
            props.remove(KEY_STARTED_AT)
        } else {
            props.setProperty(KEY_BOOK_ID, session.bookId)
            props.setProperty(KEY_PAGE, session.page.toString())
            props.setProperty(KEY_STARTED_AT, session.startedAtMillis.toString())
        }
    }

    /** Timestamp of the newest process exit already written to the log; 0 before the first run. */
    fun lastLoggedExitMillis(): Long = synchronized(lock) {
        load().getProperty(KEY_LAST_EXIT)?.toLongOrNull() ?: 0L
    }

    fun setLastLoggedExitMillis(millis: Long): Outcome<Unit, StateWriteError> =
        update { props -> props.setProperty(KEY_LAST_EXIT, millis.toString()) }

    private fun update(change: (Properties) -> Unit): Outcome<Unit, StateWriteError> = synchronized(lock) {
        val props = load()
        change(props)
        try {
            save(props)
            Outcome.Success(Unit)
        } catch (e: IOException) {
            AudoraLog.e(TAG, "Could not save diagnostics state to ${file.path}", e)
            Outcome.Failure(StateWriteError(e.toString()))
        }
    }

    /** A missing file is an empty state; an unreadable one is logged and treated as empty. */
    private fun load(): Properties {
        val props = Properties()
        if (!file.exists()) return props
        try {
            FileInputStream(file).use { props.load(it) }
        } catch (e: IOException) {
            AudoraLog.w(TAG, "Could not read diagnostics state from ${file.path}; starting empty", e)
            props.clear()
        } catch (e: IllegalArgumentException) {
            // Properties.load throws this on a malformed \uXXXX escape, i.e. a corrupt file.
            AudoraLog.w(TAG, "Corrupt diagnostics state in ${file.path}; starting empty", e)
            props.clear()
        }
        return props
    }

    private fun save(props: Properties) {
        val dir = file.parentFile
        if (dir != null && !dir.isDirectory && !dir.mkdirs()) throw IOException("Cannot create $dir")
        val temp = File(dir, "${file.name}$TEMP_SUFFIX")
        FileOutputStream(temp).use { out ->
            props.store(out, null)
            out.flush()
            out.fd.sync()
        }
        try {
            Files.move(
                temp.toPath(),
                file.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (e: AtomicMoveNotSupportedException) {
            // Same directory on one filesystem, so this should not happen; fall back rather than lose state.
            AudoraLog.w(TAG, "Atomic rename unsupported for ${file.path}; replacing non-atomically", e)
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private companion object {
        const val TAG = "DiagnosticsState"
        const val TEMP_SUFFIX = ".tmp"
        const val KEY_BOOK_ID = "session.bookId"
        const val KEY_PAGE = "session.page"
        const val KEY_STARTED_AT = "session.startedAtMillis"
        const val KEY_LAST_EXIT = "exits.lastLoggedMillis"
    }
}

/** The state file could not be written; [message] says why (already logged with the path). */
data class StateWriteError(val message: String)
