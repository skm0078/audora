// File: core/src/main/kotlin/io/github/skm0078/audora/core/home/BookGlances.kt
// Purpose: The last line heard in each book and the time left at the listener's speed, for the Continue card
//          (personal touches 4 and 13, ADR 2026-10-28-make-it-yours).

package io.github.skm0078.audora.core.home

import io.github.skm0078.audora.core.library.BookId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the Continue card says about a book besides its page. [line] is the sentence that started last, cut to
 * [LINE_MAX]; [msLeft] the reader's estimate of the rest of the book at the speed and pace then, when [pagesLeft]
 * pages were left. Both are the book's own words and figures: kept on the phone, never logged.
 */
data class BookGlance(
    val line: String? = null,
    val msLeft: Long? = null,
    val pagesLeft: Int? = null,
    val savedAtMillis: Long = 0,
) {
    /**
     * The time left now that [pagesLeftNow] pages are left: the saved estimate scaled by the pages read since, so a
     * book played on from the library without the reader open still counts down. Null without an estimate.
     */
    fun msLeftNow(pagesLeftNow: Int): Long? {
        val then = pagesLeft?.takeIf { it > 0 }
        return msLeft?.let { ms -> if (then == null) ms else ms * pagesLeftNow.coerceIn(0, then) / then }
    }

    companion object {
        /** About two lines on the card; the rest of a long sentence is cut at a word. */
        const val LINE_MAX = 160
    }
}

/**
 * The process's one set of glances, shared by the playback service (each sentence started) and the reader (each new
 * estimate). Changes stay in memory; the file is written at most every [saveIntervalMs] and on [flush] (pause, stop,
 * the service going away), so reading a book costs a few small writes a minute at most. Blocking file I/O: call off
 * the main thread.
 */
class BookGlances(
    private val store: BookGlanceStore,
    private val clock: () -> Long,
    private val saveIntervalMs: Long = SAVE_INTERVAL_MS,
) {
    private val lock = Any()
    private var loaded = false
    private var dirty = false
    private var lastSaveAt = Long.MIN_VALUE / 2
    private val state = MutableStateFlow<Map<BookId, BookGlance>>(emptyMap())

    val glances: StateFlow<Map<BookId, BookGlance>> = state.asStateFlow()

    fun load() = synchronized(lock) { ensureLoaded() }

    /** A sentence of [bookId] started; [text] is what it says. */
    fun heard(bookId: BookId, text: String) =
        change(bookId) { it.copy(line = LineOfTheDay.clip(text, BookGlance.LINE_MAX)) }

    /** The reader's new estimate for [bookId]: [msLeft] for the rest of the book, [pagesLeft] pages from here. */
    fun timeLeft(bookId: BookId, msLeft: Long, pagesLeft: Int) =
        change(bookId) { it.copy(msLeft = msLeft.coerceAtLeast(0), pagesLeft = pagesLeft.coerceAtLeast(0)) }

    /** Writes what changed since the last save. */
    fun flush() = synchronized(lock) { if (dirty) save() }

    /** Forgets the books not in [kept] (removed from the library); saves only if one went. */
    fun prune(kept: Set<BookId>) = synchronized(lock) {
        ensureLoaded()
        val next = state.value.filterKeys { it in kept }
        if (next.size == state.value.size) return@synchronized
        state.value = next
        save()
    }

    private fun change(bookId: BookId, edit: (BookGlance) -> BookGlance) = synchronized(lock) {
        ensureLoaded()
        val now = clock()
        val before = state.value[bookId] ?: BookGlance()
        val after = edit(before).copy(savedAtMillis = now)
        if (after.copy(savedAtMillis = before.savedAtMillis) == before) return@synchronized
        state.value = keepNewest(state.value + (bookId to after))
        dirty = true
        if (now - lastSaveAt >= saveIntervalMs) save()
    }

    private fun save() {
        store.save(state.value)
        dirty = false
        lastSaveAt = clock()
    }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        state.value = keepNewest(store.load())
    }

    private fun keepNewest(all: Map<BookId, BookGlance>): Map<BookId, BookGlance> = if (all.size <= BOOKS_KEPT) {
        all
    } else {
        all.entries.sortedByDescending { it.value.savedAtMillis }.take(BOOKS_KEPT).associate { it.key to it.value }
    }

    companion object {
        /** A kill loses at most this much of the last line; the estimate is redone when the reader opens anyway. */
        const val SAVE_INTERVAL_MS = 30_000L

        /** Only the Continue card reads these, so the books read longest ago can go: the file stays a few KB. */
        const val BOOKS_KEPT = 60
    }
}
