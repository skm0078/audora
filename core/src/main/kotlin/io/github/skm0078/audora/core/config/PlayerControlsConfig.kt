// File: core/src/main/kotlin/io/github/skm0078/audora/core/config/PlayerControlsConfig.kt
// Purpose: Tunables for the player's quick controls: the one-tap bookmark's notice, resuming when headphones come
//          back, and how often the home-screen widget's time left is refreshed (CF-2, CF-4, CF-7).

package io.github.skm0078.audora.core.config

/**
 * @property bookmarkNoticeMs how long the player's "Bookmarked" notice (with Add note and Undo) stays up; Android
 *   lengthens it for a listener who asked for more time to act (Accessibility timeout).
 * @property reconnectWindowMs headphones or a speaker that come back within this long after leaving resume reading,
 *   when the listener turned that on; later than that the listener has moved on, and reading stays paused.
 * @property widgetRefreshMs while reading, the widget's time left is brought up to date at most this often (and on
 *   every pause), so a long listen never redraws it once a sentence.
 */
data class PlayerControlsConfig(
    val bookmarkNoticeMs: Long = DEFAULT_BOOKMARK_NOTICE_MS,
    val reconnectWindowMs: Long = DEFAULT_RECONNECT_WINDOW_MS,
    val widgetRefreshMs: Long = DEFAULT_WIDGET_REFRESH_MS,
) {
    init {
        require(bookmarkNoticeMs > 0) { "bookmarkNoticeMs must be > 0, was $bookmarkNoticeMs" }
        require(reconnectWindowMs > 0) { "reconnectWindowMs must be > 0, was $reconnectWindowMs" }
        require(widgetRefreshMs > 0) { "widgetRefreshMs must be > 0, was $widgetRefreshMs" }
    }

    private companion object {
        const val MS_PER_MINUTE = 60_000L
        const val DEFAULT_BOOKMARK_NOTICE_MS = 6_000L
        const val DEFAULT_RECONNECT_WINDOW_MS = 30 * MS_PER_MINUTE
        const val DEFAULT_WIDGET_REFRESH_MS = 5 * MS_PER_MINUTE
    }
}
