// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/controls/ReconnectResumeTest.kt
// Purpose: Headphones coming back resume reading (CF-4) only when asked for, only after Android's own disconnect
//          pause, only once, only in time, and only when Android lets a paused service start again.

package io.github.skm0078.audora.playback.controls

import io.github.skm0078.audora.playback.controls.ReconnectResume.Decision
import org.junit.Assert.assertEquals
import org.junit.Test

class ReconnectResumeTest {
    private var now = 1_000L
    private var on = true
    private var unrestricted = false
    private val resume = ReconnectResume(
        windowMs = WINDOW,
        foregroundGraceMs = GRACE,
        enabled = { on },
        unrestricted = { unrestricted },
        clock = { now },
    )

    private fun disconnectThenReconnectAfter(ms: Long): Decision {
        resume.onPaused(byDisconnect = true)
        now += ms
        return resume.onOutputConnected()
    }

    @Test
    fun `headphones back soon after leaving resume reading, once`() {
        assertEquals(Decision.RESUME, disconnectThenReconnectAfter(5_000))
        assertEquals(
            "a second output (Bluetooth adds two) does not resume again",
            Decision.NOT_PAUSED_BY_DISCONNECT,
            resume.onOutputConnected(),
        )
    }

    @Test
    fun `nothing resumes when the listener has not turned it on`() {
        on = false
        assertEquals(Decision.SWITCHED_OFF, disconnectThenReconnectAfter(5_000))
    }

    @Test
    fun `the listener's own pause is never undone by headphones`() {
        resume.onPaused(byDisconnect = false)
        assertEquals(Decision.NOT_PAUSED_BY_DISCONNECT, resume.onOutputConnected())
    }

    @Test
    fun `playing again, then pausing by hand, cancels the reconnect`() {
        resume.onPaused(byDisconnect = true)
        resume.onPlaying()
        assertEquals(Decision.NOT_PAUSED_BY_DISCONNECT, resume.onOutputConnected())
        resume.onPaused(byDisconnect = true)
        resume.onPaused(byDisconnect = false)
        assertEquals(Decision.NOT_PAUSED_BY_DISCONNECT, resume.onOutputConnected())
    }

    @Test
    fun `after the window reading stays paused`() {
        unrestricted = true
        assertEquals(Decision.RESUME, disconnectThenReconnectAfter(WINDOW))
        assertEquals(Decision.TOO_LATE, disconnectThenReconnectAfter(WINDOW + 1))
    }

    @Test
    fun `past the foreground grace it needs unrestricted battery use`() {
        assertEquals(Decision.RESUME, disconnectThenReconnectAfter(GRACE))
        assertEquals(Decision.NOT_ALLOWED_IN_BACKGROUND, disconnectThenReconnectAfter(GRACE + 1))
        unrestricted = true
        assertEquals(Decision.RESUME, disconnectThenReconnectAfter(GRACE + 1))
    }

    @Test
    fun `outputs present with nothing paused are ignored`() {
        assertEquals(Decision.NOT_PAUSED_BY_DISCONNECT, resume.onOutputConnected())
    }

    private companion object {
        const val WINDOW = 30 * 60_000L
        const val GRACE = 10 * 60_000L
    }
}
