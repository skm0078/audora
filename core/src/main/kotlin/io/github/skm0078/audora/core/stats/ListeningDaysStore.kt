// File: core/src/main/kotlin/io/github/skm0078/audora/core/stats/ListeningDaysStore.kt
// Purpose: Keeps how long the listener listened on each day in one small file on the phone (CF-8).

package io.github.skm0078.audora.core.stats

import io.github.skm0078.audora.core.files.TextFiles
import io.github.skm0078.audora.core.logging.AudoraLog
import java.io.File
import java.io.IOException
import java.time.LocalDate

/**
 * One line per day listened: the local date and the milliseconds listened; never a book, a title or a time of day.
 * Written to a synced temp file and renamed, so a crash never leaves half a file. A foreign or unreadable file (another
 * format, a read error) reads as no stats and is set aside first, so the next save does not destroy it; one that will
 * not move is never overwritten. Nothing here leaves the phone: no analytics, no network.
 */
class ListeningDaysStore(
    private val file: File,
    private val moveAside: (File) -> Boolean = { TextFiles.setAside(it) },
) {
    @Volatile
    private var keepFile = false

    fun load(): Map<LocalDate, Long> {
        val lines = try {
            if (file.isFile) file.readLines() else emptyList()
        } catch (e: IOException) {
            AudoraLog.w(TAG, "Listening stats unreadable, set aside: ${e.javaClass.simpleName}")
            setAside()
            return emptyMap()
        }
        return when {
            lines.isEmpty() -> emptyMap()

            lines.first() != HEADER -> {
                AudoraLog.w(TAG, "Listening stats from another format set aside")
                setAside()
                emptyMap()
            }

            else -> lines.drop(1).mapNotNull(::entryOf).toMap()
        }
    }

    /** Replaces the file with [days]; false (logged) if it could not be written. */
    fun save(days: Map<LocalDate, Long>): Boolean {
        if (keepFile) {
            AudoraLog.w(TAG, "Listening stats not saved: an unreadable file is in the way")
            return false
        }
        return try {
            val lines = days.filterValues { it > 0 }.toSortedMap().map { (day, ms) -> "$day$SEPARATOR$ms" }
            TextFiles.writeDurably(file, (listOf(HEADER) + lines).joinToString("\n", postfix = "\n"))
            true
        } catch (e: IOException) {
            AudoraLog.w(TAG, "Listening stats not saved: ${e.javaClass.simpleName}")
            false
        }
    }

    private fun setAside() {
        if (!moveAside(file)) keepFile = true
    }

    private fun entryOf(line: String): Pair<LocalDate, Long>? {
        val parts = line.split(SEPARATOR).takeIf { it.size == FIELDS } ?: return null
        // A line this build cannot read is dropped, like a quiz card whose highlight is gone.
        val day = runCatching { LocalDate.parse(parts[0]) }.getOrNull()
        val ms = parts[1].toLongOrNull()?.takeIf { it > 0 }
        return if (day != null && ms != null) day to ms else null
    }

    companion object {
        /** Where the app keeps it, under `filesDir`; the backup lists it by this name. */
        const val FILE_NAME = "listening-days.txt"
        private const val TAG = "Audora.ListeningStats"
        private const val HEADER = "audora-listening-days 1"
        private const val SEPARATOR = '\t'
        private const val FIELDS = 2
    }
}
