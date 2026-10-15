// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/ReadingPosition.kt
// Purpose: A sentence's place in a book as the reader sees it, and the sleep-timer choices it offers.

package io.github.skm0078.audora.ui.reader

import io.github.skm0078.audora.core.library.BookId

/**
 * A book, a zero-based page and a character offset into that page's text. `charStart`, not the sentence
 * index, is the key (as for resume anchors): the service starts at the sentence that contains it.
 */
data class ReadingPosition(val bookId: BookId, val page: Int, val charStart: Int) {
    init {
        require(page >= 0) { "page must be >= 0, was $page" }
        require(charStart >= 0) { "charStart must be >= 0, was $charStart" }
    }

    companion object {
        fun startOfPage(bookId: BookId, page: Int) = ReadingPosition(bookId, page, 0)
    }
}

/** What the listener picks (Build Spec §2). End of chapter is offered only for books that have chapters. */
sealed interface SleepTimerSetting {
    data object Off : SleepTimerSetting

    data class AfterMinutes(val minutes: Int) : SleepTimerSetting {
        init {
            require(minutes > 0) { "minutes must be > 0, was $minutes" }
        }
    }

    data object EndOfPage : SleepTimerSetting

    data object EndOfChapter : SleepTimerSetting
}

/** The timer as the playback service reports it; it runs there so it works with the screen off. */
sealed interface SleepTimerState {
    data object Off : SleepTimerState

    /** @property endsAtMillis wall-clock time (`System.currentTimeMillis()`) when reading stops. */
    data class Counting(val endsAtMillis: Long) : SleepTimerState

    data object EndOfPage : SleepTimerState

    data object EndOfChapter : SleepTimerState
}
