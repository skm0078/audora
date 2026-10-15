// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/session/QueueRegistry.kt
// Purpose: Hands the player's loader threads the render queue of the current epoch, and only that one.

package io.github.skm0078.audora.playback.session

import io.github.skm0078.audora.playback.queue.RenderAheadQueue

/**
 * Written on the main thread when the playlist is rebuilt, read on ExoPlayer's loader threads.
 * The previous epoch's queue stays reachable until its playlist is gone (the player cancels those loads),
 * then the session closes it.
 */
class QueueRegistry {
    @Volatile
    private var current: Pair<Int, RenderAheadQueue>? = null

    fun publish(epoch: Int, queue: RenderAheadQueue) {
        current = epoch to queue
    }

    fun clear() {
        current = null
    }

    /** The queue for [epoch], or null when that epoch is no longer current. */
    fun queueFor(epoch: Int): RenderAheadQueue? = current?.takeIf { it.first == epoch }?.second
}
