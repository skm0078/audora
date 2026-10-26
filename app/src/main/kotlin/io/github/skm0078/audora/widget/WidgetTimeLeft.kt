// File: app/src/main/kotlin/io/github/skm0078/audora/widget/WidgetTimeLeft.kt
// Purpose: The home-screen widget's time left (CF-7): the reader's last estimate for a book, counted down by the time
//          the playback service then reports listened, so it stays right with the screen off.

package io.github.skm0078.audora.widget

import android.content.Context
import androidx.core.content.edit
import io.github.skm0078.audora.R
import io.github.skm0078.audora.playback.controls.ListenedSpan
import io.github.skm0078.audora.ui.reader.hoursMinutes

/**
 * [ms] left of [bookId] at the listener's speed, as the reader estimated it at [atElapsedMs] on the device's
 * elapsed-time clock. Listening uses it up one for one: the estimate is already in heard time.
 */
data class WidgetTimeLeft(val bookId: String, val ms: Long, val atElapsedMs: Long) {
    /**
     * What is left after [span] of [listened] reading, reported at [nowElapsedMs]. Only the part of the span after the
     * estimate counts (the reader's estimate already knew the rest), and the estimate moves to the span's end, so a
     * span reported twice is subtracted once. An estimate from before a reboot (its time is ahead of the clock now)
     * counts from the span's start. Another book's span changes nothing.
     */
    fun afterListening(listened: String, span: ListenedSpan, nowElapsedMs: Long): WidgetTimeLeft {
        if (listened != bookId) return this
        val since = if (atElapsedMs > nowElapsedMs) span.fromElapsedMs else atElapsedMs
        val heard = (span.toElapsedMs - maxOf(span.fromElapsedMs, since)).coerceAtLeast(0)
        return copy(ms = (ms - heard).coerceAtLeast(0), atElapsedMs = maxOf(since, span.toElapsedMs))
    }
}

/**
 * The widget's copy of [WidgetTimeLeft], in a preferences file of its own (read synchronously in a broadcast, like
 * [ResumeWidgetStore]). The reader writes an estimate; the service's listening reports count it down.
 */
class WidgetTimeLeftStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun read(): WidgetTimeLeft? {
        val id = prefs.getString(KEY_BOOK, null)?.takeIf { it.isNotBlank() }
        val ms = prefs.getLong(KEY_MS, -1)
        return if (id != null && ms >= 0) WidgetTimeLeft(id, ms, prefs.getLong(KEY_AT, 0)) else null
    }

    /** Saves [timeLeft]; true when what the widget shows may have changed. */
    fun write(timeLeft: WidgetTimeLeft): Boolean {
        val before = read()
        if (before == timeLeft) return false
        prefs.edit {
            putString(KEY_BOOK, timeLeft.bookId)
            putLong(KEY_MS, timeLeft.ms)
            putLong(KEY_AT, timeLeft.atElapsedMs)
        }
        return true
    }

    private companion object {
        const val FILE = "widget_time_left"
        const val KEY_BOOK = "book_id"
        const val KEY_MS = "ms"
        const val KEY_AT = "at_elapsed_ms"
    }
}

/**
 * While reading, the estimate moves with every sentence; the widget needs it only now and then, since the service's
 * listening reports count it down in between. Pure; [now] is the device's elapsed time.
 */
class WidgetEstimateSchedule(private val intervalMs: Long) {
    private var lastBook: String? = null
    private var lastAt = 0L

    /** True when the estimate of [bookId] at [now] goes to the widget. */
    fun due(bookId: String, reading: Boolean, now: Long): Boolean {
        val due = bookId != lastBook || !reading || now - lastAt >= intervalMs
        if (due) {
            lastBook = bookId
            lastAt = now
        }
        return due
    }
}

/** "2 h 10 min", "12 min" or "under a minute", as the reader's time-left line words it, outside Compose. */
internal fun Context.durationLabel(ms: Long): String {
    val (hours, minutes) = hoursMinutes(ms)
    return when {
        hours > 0 -> getString(R.string.duration_hours_minutes, hours, minutes)
        minutes > 0 -> getString(R.string.duration_minutes, minutes)
        else -> getString(R.string.duration_under_minute)
    }
}
