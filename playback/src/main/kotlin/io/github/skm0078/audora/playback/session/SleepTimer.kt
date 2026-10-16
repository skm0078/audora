// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/session/SleepTimer.kt
// Purpose: Sleep-timer rules: pause after N minutes, or at the end of the current page or chapter.

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

    /** [clock] time when a [SleepTimerMode.MINUTES] timer is due; null otherwise. */
    var dueAt: Long? = null
        private set

    fun set(mode: SleepTimerMode, minutes: Int) {
        require(mode != SleepTimerMode.MINUTES || minutes > 0) { "A minutes timer needs minutes > 0, was $minutes" }
        this.mode = mode
        dueAt = if (mode == SleepTimerMode.MINUTES) clock() + minutes * MS_PER_MINUTE else null
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
            SleepTimerMode.END_OF_CHAPTER -> nextPage == null || chapterStarts.any { it in page + 1..nextPage }
            SleepTimerMode.OFF, SleepTimerMode.MINUTES -> false
        }
    }

    private companion object {
        const val MS_PER_MINUTE = 60_000L
    }
}
