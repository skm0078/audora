// File: core/src/main/kotlin/io/github/skm0078/audora/core/quiz/QuizProgressStore.kt
// Purpose: Keeps each quiz card's schedule in one small file, replaced whole on every save (plan item 70).

package io.github.skm0078.audora.core.quiz

import io.github.skm0078.audora.core.logging.AudoraLog
import java.io.File
import java.io.IOException

/**
 * One line per card: the highlight id, the streak and when it is due; never the highlight's words. A card whose
 * highlight was deleted is dropped at the next save. Written to a temp file and renamed, so a crash never leaves
 * half a file; an unreadable or foreign file reads as no progress (every card asks again) and is logged.
 */
class QuizProgressStore(private val file: File) {
    fun load(): Map<Long, CardProgress> {
        val lines = try {
            if (file.isFile) file.readLines() else emptyList()
        } catch (e: IOException) {
            AudoraLog.w(TAG, "Quiz progress unreadable: ${e.javaClass.simpleName}")
            emptyList()
        }
        return when {
            lines.isEmpty() -> emptyMap()

            lines.first() != HEADER -> {
                AudoraLog.w(TAG, "Quiz progress from another format ignored")
                emptyMap()
            }

            else -> lines.drop(1).mapNotNull(::entryOf).toMap()
        }
    }

    /** Saves [progress] for the cards in [known] only; false (logged) if it could not be written. */
    fun save(progress: Map<Long, CardProgress>, known: Set<Long>): Boolean {
        val temp = File(file.path + TEMP)
        return try {
            file.parentFile?.mkdirs()
            val lines = progress.filterKeys { it in known }.toSortedMap()
                .map { (id, state) -> "$id$SEPARATOR${state.streak}$SEPARATOR${state.dueAtMs}" }
            temp.writeText((listOf(HEADER) + lines).joinToString("\n", postfix = "\n"))
            if (!temp.renameTo(file)) throw IOException("rename failed")
            true
        } catch (e: IOException) {
            AudoraLog.w(TAG, "Quiz progress not saved: ${e.javaClass.simpleName}")
            temp.delete()
            false
        }
    }

    private fun entryOf(line: String): Pair<Long, CardProgress>? {
        val parts = line.split(SEPARATOR).takeIf { it.size == FIELDS } ?: return null
        val id = parts[0].toLongOrNull()
        val streak = parts[1].toIntOrNull()?.takeIf { it >= 0 }
        val due = parts[2].toLongOrNull()
        return if (id != null && streak != null && due != null) id to CardProgress(streak, due) else null
    }

    companion object {
        /** Where the app keeps it, under `filesDir`; the backup lists it by this name. */
        const val FILE_NAME = "quiz-progress.txt"
        private const val TAG = "Quiz.Progress"
        private const val HEADER = "audora-quiz 1"
        private const val SEPARATOR = "\t"
        private const val FIELDS = 3
        private const val TEMP = ".tmp"
    }
}
