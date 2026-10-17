// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/book/PasswordHandoff.kt
// Purpose: Hands a PDF's password from the reader to a job that reopens the book, in memory only (plan item 41).

package io.github.skm0078.audora.playback.book

import io.github.skm0078.audora.core.library.BookId
import java.util.concurrent.ConcurrentHashMap

/**
 * In this process's memory, never in an Intent (whose extras pass through the system server) and never on disk or
 * in the log. The UI [put]s the password the reader opened the book with just before starting the job's service;
 * the service [take]s it once, so nothing stays behind.
 */
object PasswordHandoff {
    private val passwords = ConcurrentHashMap<BookId, String>()

    fun put(id: BookId, password: String?) {
        if (password == null) passwords.remove(id) else passwords[id] = password
    }

    fun take(id: BookId): String? = passwords.remove(id)
}
