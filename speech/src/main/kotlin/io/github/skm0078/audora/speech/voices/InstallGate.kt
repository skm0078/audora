// File: speech/src/main/kotlin/io/github/skm0078/audora/speech/voices/InstallGate.kt
// Purpose: Keeps a model's install and its delete apart, so a delete is never undone by a download finishing late.

package io.github.skm0078.audora.speech.voices

/**
 * WorkManager reports a cancelled download as finished before its worker has stopped, so a delete that waited for
 * that report can still meet a worker about to install. Installs and deletes run under one [lock] (the models' own),
 * and each delete is counted: a download takes a [ticket] when it starts, and its install runs only if no delete came
 * since. The worker's partial file may survive a race; the next delete or download deals with it.
 */
class InstallGate(private val lock: Any = Any()) {
    private var deletes = 0L

    /** Taken when a download starts. */
    fun ticket(): Long = synchronized(lock) { deletes }

    /** Runs [install] under the lock unless a delete came after [ticket] was taken; null when one did. */
    fun <T> install(ticket: Long, install: () -> T): T? = synchronized(lock) {
        if (deletes == ticket) install() else null
    }

    /** Runs [delete] under the lock and marks every download started before it as stale. */
    fun <T> delete(delete: () -> T): T = synchronized(lock) {
        deletes++
        delete()
    }
}
