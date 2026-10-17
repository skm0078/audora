// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/export/ExportProgress.kt
// Purpose: Turns finished pages into a percentage, reporting only real steps so the notification isn't spammed.

package io.github.skm0078.audora.playback.export

/**
 * Not thread-safe: fed from the exporter's thread. [onPageDone] returns the new percentage when it moved on by
 * at least [stepPercent] since the last report (and always at 100), null otherwise.
 */
class ExportProgress(chapters: List<AudioChapter>, private val stepPercent: Int) {
    private val totalPages = chapters.sumOf { it.lastPage - it.firstPage + 1 }
    private var pagesDone = 0
    private var reported = 0

    val percent: Int get() = if (totalPages == 0) PERCENT else pagesDone * PERCENT / totalPages

    /** Counts [pages] an earlier run finished (a resumed export) as done, without reporting. */
    fun skip(pages: Int) {
        pagesDone += pages
        reported = percent
    }

    fun onPageDone(): Int? {
        pagesDone++
        val now = percent
        if (now - reported < stepPercent && now < PERCENT) return null
        reported = now
        return now
    }

    private companion object {
        const val PERCENT = 100
    }
}
