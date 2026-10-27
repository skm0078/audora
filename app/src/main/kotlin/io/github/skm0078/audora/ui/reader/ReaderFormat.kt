// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/ReaderFormat.kt
// Purpose: Pure text helpers for the reader's controls (speed, time left, typed page), unit-tested on the JVM.

package io.github.skm0078.audora.ui.reader

import java.math.BigDecimal
import java.util.Locale

/** 1f → "1", 1.25f → "1.25", 0.5f → "0.5". */
fun speedLabel(speed: Float): String = BigDecimal(speed.toString()).stripTrailingZeros().toPlainString()

/** Time left on a sleep timer, rounded up so it never shows 0:00 while still running: "14:59", "1:00:00". */
fun remainingLabel(remainingMs: Long): String {
    val totalSeconds = (remainingMs.coerceAtLeast(0) + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND
    val hours = totalSeconds / SECONDS_PER_HOUR
    val minutes = totalSeconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}

/** A time left as whole hours and minutes, the minutes rounded up so "under a minute" only shows at the very end. */
data class HoursMinutes(val hours: Long, val minutes: Long)

fun hoursMinutes(ms: Long): HoursMinutes {
    val totalMinutes = (ms.coerceAtLeast(0) + MILLIS_PER_MINUTE - 1) / MILLIS_PER_MINUTE
    return HoursMinutes(totalMinutes / MINUTES_PER_HOUR, totalMinutes % MINUTES_PER_HOUR)
}

/** Whole minutes left, rounded up, so TalkBack says "1 minute left" until the very end, never "0 minutes". */
fun minutesLeft(ms: Long): Int = maxOf(1L, (ms.coerceAtLeast(0) + MILLIS_PER_MINUTE - 1) / MILLIS_PER_MINUTE).toInt()

/** The typed 1-based page if it exists in the book, else null. */
fun parsePageNumber(text: String, pageCount: Int): Int? = text.toIntOrNull()?.takeIf { it in 1..pageCount }

private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3_600L
private const val MILLIS_PER_MINUTE = 60_000L
private const val MINUTES_PER_HOUR = 60L
