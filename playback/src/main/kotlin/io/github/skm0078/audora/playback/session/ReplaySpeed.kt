// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/session/ReplaySpeed.kt
// Purpose: The one-shot slower speed of "What did it say?": one sentence replayed slower, then the listener's speed.

package io.github.skm0078.audora.playback.session

import io.github.skm0078.audora.playback.queue.UnitKey

/**
 * Holds the player speed to go back to while one sentence replays slower. Nothing is saved: the slower speed lives
 * only on the player, so a rotated screen, a locked phone or a closed reader can never leave the listener on it.
 * Main thread only.
 */
class ReplaySpeed {
    private var replaying: UnitKey? = null
    private var restoreTo: Float? = null

    val active: Boolean get() = replaying != null

    /** Starts a replay of [key] at [factor] times [current]; returns the player speed to set. */
    fun start(key: UnitKey, current: Float, factor: Float): Float {
        require(factor > 0f && factor.isFinite()) { "factor must be a positive number, was $factor" }
        // A second replay before the first ends keeps the listener's speed, not the slowed one.
        val base = restoreTo ?: current
        replaying = key
        restoreTo = base
        return base * factor
    }

    /** A sentence started playing: the speed to restore when it is not the one replayed, else null. */
    fun onSentence(key: UnitKey?): Float? = if (replaying == null || key == replaying) null else finish()

    /** Ends the replay now (a rebuild, a new book); the speed to restore, or null when none was running. */
    fun finish(): Float? = restoreTo.also {
        replaying = null
        restoreTo = null
    }

    /** The listener changed the speed meanwhile: theirs wins, so nothing is restored. */
    fun forget() {
        replaying = null
        restoreTo = null
    }
}
