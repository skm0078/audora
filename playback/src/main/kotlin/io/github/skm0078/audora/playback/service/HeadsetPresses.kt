// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/service/HeadsetPresses.kt
// Purpose: Counts quick presses of a headset's one button: one plays or pauses, two bookmark, three go back (item 20).

package io.github.skm0078.audora.playback.service

/** What a burst of presses of the headset's play/pause button asks for. */
enum class HeadsetGesture { PLAY_PAUSE, BOOKMARK, BACK_ONE_SENTENCE }

/**
 * Each press waits [windowMs] for another; when none comes, the burst ends and [onGesture] gets its count (three or
 * more presses go back one sentence). The cost is that play and pause answer [windowMs] late, as in Media3's own
 * double-tap handling, which this replaces (Media3's double tap skips ahead; the next button already does that).
 *
 * Main thread only: [schedule] runs its action on the same thread and returns a way to cancel it.
 */
class HeadsetPresses(
    private val windowMs: Long,
    private val schedule: (delayMs: Long, action: () -> Unit) -> () -> Unit,
    private val onGesture: (HeadsetGesture) -> Unit,
) {
    private var presses = 0
    private var cancelPending: (() -> Unit)? = null

    fun onPress() {
        cancelPending?.invoke()
        presses++
        cancelPending = schedule(windowMs) { finish() }
    }

    /** Ends a burst at once, e.g. another media key arrived: keys are then applied in the order pressed. */
    fun flush() {
        if (presses == 0) return
        cancelPending?.invoke()
        finish()
    }

    private fun finish() {
        val count = presses
        presses = 0
        cancelPending = null
        onGesture(
            when (count) {
                1 -> HeadsetGesture.PLAY_PAUSE
                2 -> HeadsetGesture.BOOKMARK
                else -> HeadsetGesture.BACK_ONE_SENTENCE
            },
        )
    }
}
