// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/session/SleepTimer.kt
// Purpose: Sleep-timer rules: pause after N minutes, at the end of the page, or after one or more chapters.

package io.github.skm0078.audora.playback.session

import io.github.skm0078.audora.playback.contract.SleepTimerMode
import io.github.skm0078.audora.playback.playlist.PlaybackItem

/**
 * Pure state; the session schedules the checks. [clock] is a monotonic millisecond clock that keeps counting
 * in deep sleep (`SystemClock.elapsedRealtime`), so a timer set before the screen turned off stays accurate.
 */
class SleepTimer(private val clock: () -> Long) {
    var mode: SleepTimerMode = SleepTimerMode.OFF
        private set

    /**
     * First pages of the open book's chapters, ascending (see `BookText.chapterStarts`). Chapters are
     * page-granular: a chapter that starts mid-page is taken to start with that page.
     */
    var chapterStarts: List<Int> = emptyList()

    /**
     * For "stop after N chapters": the chapter start page reading stops before; null for one chapter (any chapter
     * start ends it) or when the book has fewer chapters left (the end of the book ends it).
     */
    var chapterTarget: Int? = null
        private set

    /** [clock] time when a [SleepTimerMode.MINUTES] timer is due; null otherwise. */
    var dueAt: Long? = null
        private set

    /**
     * [chapters] (end-of-chapter mode) counts chapter ends from [fromPage], the page being read: 2 stops before the
     * second chapter start after it.
     */
    fun set(mode: SleepTimerMode, minutes: Int, chapters: Int = 1, fromPage: Int? = null) {
        require(mode != SleepTimerMode.MINUTES || minutes > 0) { "A minutes timer needs minutes > 0, was $minutes" }
        require(chapters > 0) { "chapters must be > 0, was $chapters" }
        this.mode = mode
        dueAt = if (mode == SleepTimerMode.MINUTES) clock() + minutes * MS_PER_MINUTE else null
        chapterTarget = if (mode == SleepTimerMode.END_OF_CHAPTER && chapters > 1 && fromPage != null) {
            chapterStarts.filter { it > fromPage }.getOrNull(chapters - 1) ?: Int.MAX_VALUE
        } else {
            null
        }
    }

    fun clear() = set(SleepTimerMode.OFF, 0)

    /** Milliseconds until a minutes timer is due (0 when overdue); null when no minutes timer is set. */
    fun remainingMs(): Long? = dueAt?.let { (it - clock()).coerceAtLeast(0) }

    /**
     * End-of-page and end-of-chapter modes: pause after [current] when [next] starts a new page, or a page
     * at or past the next chapter start. A null [next] is the end of the book, which ends both.
     */
    fun pauseAfter(current: PlaybackItem?, next: PlaybackItem?): Boolean {
        val page = (current as? PlaybackItem.Sentence)?.unit?.page ?: return false
        val nextPage = when (next) {
            is PlaybackItem.Sentence -> next.unit.page

            // The next page is still being extracted; pages are appended whole, so it is a later page.
            is PlaybackItem.More -> page + 1

            null -> null
        }
        return when (mode) {
            SleepTimerMode.END_OF_PAGE -> nextPage != page
            SleepTimerMode.END_OF_CHAPTER -> nextPage == null || endsChapterRun(page, nextPage)
            SleepTimerMode.OFF, SleepTimerMode.MINUTES -> false
        }
    }

    /** Crossing into [nextPage] ends the timer's chapters: the target chapter start, or any start without one. */
    private fun endsChapterRun(page: Int, nextPage: Int): Boolean {
        val target = chapterTarget?.takeIf { it > page }
        return if (target == null) chapterStarts.any { it in page + 1..nextPage } else nextPage >= target
    }

    private companion object {
        const val MS_PER_MINUTE = 60_000L
    }
}
