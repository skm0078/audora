// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/controls/ReconnectResume.kt
// Purpose: Decides whether headphones or a speaker coming back resume reading (CF-4, opt-in, off by default).

package io.github.skm0078.audora.playback.controls

/**
 * Reading resumes only when all hold: the listener turned it on ([enabled]); the last pause was Android's own, because
 * the headphones or speaker left (not the listener's pause, nor a call or another app); nothing played since; and the
 * output came back within [windowMs]. After [foregroundGraceMs] Android lets the paused service start playing again
 * from the background only when the app may run unrestricted ([unrestricted]), so it waits for a tap instead.
 *
 * Pure and main-thread only: [clock] is the device's elapsed time. Each disconnect resumes at most once.
 */
class ReconnectResume(
    private val windowMs: Long,
    private val foregroundGraceMs: Long,
    private val enabled: () -> Boolean,
    private val unrestricted: () -> Boolean,
    private val clock: () -> Long,
) {
    private var leftAt: Long? = null

    /** Reading paused; [byDisconnect] when Android paused it because the headphones or speaker left. */
    fun onPaused(byDisconnect: Boolean) {
        leftAt = if (byDisconnect) clock() else null
    }

    /** Reading started again some other way: a later reconnect must not restart what the listener then paused. */
    fun onPlaying() {
        leftAt = null
    }

    /** Headphones or a speaker connected: the answer, once per disconnect, to whether reading resumes now. */
    fun onOutputConnected(): Decision {
        val left = leftAt ?: return Decision.NOT_PAUSED_BY_DISCONNECT
        leftAt = null
        val away = clock() - left
        return when {
            !enabled() -> Decision.SWITCHED_OFF
            away > windowMs -> Decision.TOO_LATE
            away > foregroundGraceMs && !unrestricted() -> Decision.NOT_ALLOWED_IN_BACKGROUND
            else -> Decision.RESUME
        }
    }

    /** Every answer but [RESUME] leaves reading paused; the reason goes to the log. */
    enum class Decision { RESUME, NOT_PAUSED_BY_DISCONNECT, SWITCHED_OFF, TOO_LATE, NOT_ALLOWED_IN_BACKGROUND }
}
