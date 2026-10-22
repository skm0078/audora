// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/export/PasswordHandoff.kt
// Purpose: Hands a PDF's password from the reader to a job that reopens the book, in memory only (items 41, ADR-034).

package io.github.skm0078.audora.playback.export

import io.github.skm0078.audora.core.library.BookId
import java.util.concurrent.ConcurrentHashMap

/**
 * In this process's memory, never in an Intent (whose extras pass through the system server) and never on disk or
 * in the log. The UI [put]s the password the reader opened the book with just before starting the job's service;
 * the service [take]s it once. A start that throws removes it at once ([handOver]); one nobody takes for another
 * reason (the service was busy and refused the job) cannot be taken after [ttlMs] and is dropped by the next call.
 */
internal class PasswordHandoff(private val ttlMs: Long = DEFAULT_TTL_MS, private val clock: () -> Long) {
    private class Held(val password: String, val atMs: Long)

    private val passwords = ConcurrentHashMap<BookId, Held>()

    fun put(id: BookId, password: String?) {
        dropExpired()
        if (password == null) passwords.remove(id) else passwords[id] = Held(password, clock())
    }

    /** [put]s [password] for [id], then runs [start]; if [start] throws, the password is removed before rethrowing. */
    fun handOver(id: BookId, password: String?, start: () -> Unit) {
        put(id, password)
        var started = false
        try {
            start()
            started = true
        } finally {
            if (!started) passwords.remove(id)
        }
    }

    fun take(id: BookId): String? {
        dropExpired()
        return passwords.remove(id)?.password
    }

    /** How many passwords are held, for tests. */
    internal val heldCount: Int get() = passwords.size

    private fun dropExpired() {
        val now = clock()
        passwords.entries.removeIf { now - it.value.atMs > ttlMs }
    }

    companion object {
        /** Long enough for a service to start on a slow phone, short enough that nothing lingers. */
        private const val DEFAULT_TTL_MS = 60_000L

        /** The one the app uses; tests make their own with a fake clock. */
        val shared = PasswordHandoff(clock = System::currentTimeMillis)
    }
}
