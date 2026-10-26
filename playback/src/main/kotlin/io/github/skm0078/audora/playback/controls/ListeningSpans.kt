// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/controls/ListeningSpans.kt
// Purpose: Cuts reading time into spans for the home-screen widget's time left (CF-7): one at each pause, and one
//          every so often while reading goes on.

package io.github.skm0078.audora.playback.controls

/** [fromElapsedMs] to [toElapsedMs] of continuous reading, on the device's elapsed-time clock (survives no reboot). */
data class ListenedSpan(val fromElapsedMs: Long, val toElapsedMs: Long) {
    init {
        require(toElapsedMs >= fromElapsedMs) { "A span ends after it starts: $fromElapsedMs..$toElapsedMs" }
    }
}

/**
 * The widget subtracts each span from the time left the reader last estimated, so it counts down with the screen off,
 * where the reader cannot estimate. Pure and main-thread only.
 */
class ListeningSpans(private val intervalMs: Long, private val clock: () -> Long) {
    private var from: Long? = null

    val reading: Boolean get() = from != null

    fun onReading() {
        if (from == null) from = clock()
    }

    /** Reading stopped: the span since it started or was last reported; null when it was not reading. */
    fun onStopped(): ListenedSpan? {
        val start = from ?: return null
        from = null
        return ListenedSpan(start, clock())
    }

    /** While reading: the span since the last one, once [intervalMs] has passed; null before that. */
    fun onTick(): ListenedSpan? {
        val start = from ?: return null
        val now = clock()
        return if (now - start < intervalMs) {
            null
        } else {
            from = now
            ListenedSpan(start, now)
        }
    }
}
