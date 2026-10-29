// File: core/src/main/kotlin/io/github/skm0078/audora/core/stats/ListeningDaysStore.kt
// Purpose: Keeps listening time by hour, book and language in one small file on the phone (CF-8, stats screen).

package io.github.skm0078.audora.core.stats

import io.github.skm0078.audora.core.files.TextFiles
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.Lang
import java.io.File
import java.io.IOException
import java.time.LocalDate

/**
 * One line per slot: the local date and hour, the milliseconds and words heard, the language and the book's id. Never
 * a title or anything read. Version 1 files (date and milliseconds only, before 2026-10-28) still load, as slots with
 * no hour, book or language, and are written back as version 2 on the next save.
 *
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

    fun load(): Map<ListeningSlot, Heard> {
        val lines = readLines() ?: return emptyMap()
        val parse: ((String) -> Pair<ListeningSlot, Heard>?)? = when (lines.firstOrNull()) {
            HEADER -> ::slotOf
            HEADER_V1 -> ::dayOf
            else -> null
        }
        return when {
            lines.isEmpty() -> emptyMap()

            parse == null -> {
                AudoraLog.w(TAG, "Listening stats from another format set aside")
                setAside()
                emptyMap()
            }

            // Two lines for one slot cannot come from save(), but adding them loses nothing if a hand edit made them.
            else -> lines.drop(1).mapNotNull(parse).sumHeard()
        }
    }

    /** The file's lines (none when there is no file yet); null, set aside, when it cannot be read. */
    private fun readLines(): List<String>? = try {
        if (file.isFile) file.readLines() else emptyList()
    } catch (e: IOException) {
        AudoraLog.w(TAG, "Listening stats unreadable, set aside: ${e.javaClass.simpleName}")
        setAside()
        null
    }

    /** Replaces the file with [slots]; false (logged) if it could not be written. */
    fun save(slots: Map<ListeningSlot, Heard>): Boolean {
        if (keepFile) {
            AudoraLog.w(TAG, "Listening stats not saved: an unreadable file is in the way")
            return false
        }
        return try {
            val lines = slots.filterValues { !it.isEmpty }.entries
                .sortedWith(compareBy({ it.key.day }, { it.key.hour ?: -1 }))
                .map { (slot, heard) -> lineOf(slot, heard) }
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

    private fun lineOf(slot: ListeningSlot, heard: Heard): String = listOf(
        slot.day.toString(),
        slot.hour?.toString() ?: NONE,
        heard.ms.toString(),
        heard.words.toString(),
        slot.lang?.bcp47Tag ?: NONE,
        slot.book ?: NONE,
    ).joinToString(SEPARATOR.toString())

    // A line this build cannot read is dropped, like a quiz card whose highlight is gone.
    private fun slotOf(line: String): Pair<ListeningSlot, Heard>? {
        val parts = line.split(SEPARATOR).takeIf { it.size == FIELDS } ?: return null
        val day = runCatching { LocalDate.parse(parts[AT_DAY]) }.getOrNull()
        val hour = if (parts[AT_HOUR] == NONE) NO_HOUR else parts[AT_HOUR].toIntOrNull()?.takeIf { it in HOURS }
        val ms = parts[AT_MS].toLongOrNull()?.takeIf { it >= 0 }
        val words = parts[AT_WORDS].toLongOrNull()?.takeIf { it >= 0 }
        val heard = if (ms != null && words != null) Heard(ms, words).takeUnless { it.isEmpty } else null
        val lang = Lang.entries.find { it.bcp47Tag == parts[AT_LANG] }
        val book = parts[AT_BOOK].takeUnless { it == NONE || it.isEmpty() }
        return if (day != null && hour != null && heard != null) {
            ListeningSlot(day, hour.takeIf { it != NO_HOUR }, book, lang) to heard
        } else {
            null
        }
    }

    private fun dayOf(line: String): Pair<ListeningSlot, Heard>? {
        val parts = line.split(SEPARATOR).takeIf { it.size == FIELDS_V1 } ?: return null
        val day = runCatching { LocalDate.parse(parts[AT_DAY]) }.getOrNull()
        val ms = parts[AT_V1_MS].toLongOrNull()?.takeIf { it > 0 }
        return if (day != null && ms != null) ListeningSlot(day) to Heard(ms) else null
    }

    companion object {
        /** Where the app keeps it, under `filesDir`; the backup lists it by this name. */
        const val FILE_NAME = "listening-days.txt"
        private const val TAG = "Audora.ListeningStats"
        private const val HEADER = "audora-listening-days 2"
        private const val HEADER_V1 = "audora-listening-days 1"
        private const val SEPARATOR = '\t'
        private const val NONE = "-"

        // Where each field sits on a line; FIELDS counts them.
        private const val AT_DAY = 0
        private const val AT_HOUR = 1
        private const val AT_MS = 2
        private const val AT_WORDS = 3
        private const val AT_LANG = 4
        private const val AT_BOOK = 5
        private const val FIELDS = 6
        private const val AT_V1_MS = 1
        private const val FIELDS_V1 = 2
        private const val NO_HOUR = -1
        private val HOURS = 0..23
    }
}
