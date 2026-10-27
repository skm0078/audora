// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/service/LockScreenActions.kt
// Purpose: Runs the notification's and lock screen's buttons (CF-1) on the service's player: a whole sentence back or
//          on, and a bookmark on the sentence playing.

package io.github.skm0078.audora.playback.service

import androidx.media3.common.Player
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.playback.contract.LockScreenCommands
import io.github.skm0078.audora.playback.session.BookSession

/**
 * Each sentence is one media item, so a sentence back is the previous item, never a restart of this one. Main thread.
 * [bookmarks] confirms a bookmark with a buzz and the filled bookmark button.
 */
class LockScreenActions(
    private val player: () -> Player,
    private val bookSession: () -> BookSession,
    private val bookmarks: HeadsetBookmarks,
) {
    /** False when [action] is not a lock-screen button's, or there is nothing to do (the first sentence, no book). */
    fun run(action: String): Boolean {
        AudoraLog.i(TAG, "Lock screen ${action.substringAfterLast('.').lowercase()}")
        return when (action) {
            LockScreenCommands.ACTION_PREVIOUS_SENTENCE -> skip(forward = false)
            LockScreenCommands.ACTION_NEXT_SENTENCE -> skip(forward = true)
            LockScreenCommands.ACTION_BOOKMARK -> bookmarks.addCurrent(bookSession())
            else -> false
        }
    }

    private fun skip(forward: Boolean): Boolean {
        val player = player()
        val can = if (forward) player.hasNextMediaItem() else player.hasPreviousMediaItem()
        if (!can) {
            AudoraLog.i(TAG, "No sentence ${if (forward) "after" else "before"} this one in the player")
            return false
        }
        if (forward) player.seekToNextMediaItem() else player.seekToPreviousMediaItem()
        return true
    }

    private companion object {
        const val TAG = "Playback.LockScreen"
    }
}
