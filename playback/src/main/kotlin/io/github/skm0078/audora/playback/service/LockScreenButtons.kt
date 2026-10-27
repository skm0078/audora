// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/service/LockScreenButtons.kt
// Purpose: The notification's and lock screen's buttons (CF-1): previous sentence, play/pause, next sentence and
//          bookmark, with the bookmark drawn filled while the sentence just bookmarked plays.

package io.github.skm0078.audora.playback.service

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import com.google.common.collect.ImmutableList
import io.github.skm0078.audora.playback.contract.LockScreenCommands
import io.github.skm0078.audora.playback.queue.UnitKey

/**
 * Media3 draws these as the media notification's and the system media controls' buttons (the lock screen, quick
 * settings). Previous and next take the back and forward places, so they replace the player's own skip buttons;
 * play/pause stays Media3's. The bookmark goes in the overflow, which the expanded notification and the lock screen
 * show beside them.
 */
@OptIn(UnstableApi::class)
class LockScreenButtons(private val labels: Labels) {
    /** What TalkBack says for each button, in the app's language. */
    data class Labels(val previous: String, val next: String, val bookmark: String, val bookmarked: String)

    fun buttons(bookmarked: Boolean): ImmutableList<CommandButton> = ImmutableList.of(
        CommandButton.Builder(CommandButton.ICON_PREVIOUS)
            .setSessionCommand(LockScreenCommands.PREVIOUS_SENTENCE)
            .setDisplayName(labels.previous)
            .setSlots(CommandButton.SLOT_BACK)
            .build(),
        CommandButton.Builder(CommandButton.ICON_NEXT)
            .setSessionCommand(LockScreenCommands.NEXT_SENTENCE)
            .setDisplayName(labels.next)
            .setSlots(CommandButton.SLOT_FORWARD)
            .build(),
        CommandButton.Builder(
            if (bookmarked) CommandButton.ICON_BOOKMARK_FILLED else CommandButton.ICON_BOOKMARK_UNFILLED,
        )
            .setSessionCommand(LockScreenCommands.BOOKMARK)
            .setDisplayName(if (bookmarked) labels.bookmarked else labels.bookmark)
            .setSlots(CommandButton.SLOT_OVERFLOW)
            .build(),
    )
}

/**
 * Whether the lock screen shows the bookmark filled: from the moment the sentence playing is bookmarked until another
 * sentence plays. A bookmark that lands after its sentence ended (the save is a database write) shows nothing. Each
 * call says whether what is shown changed, so the buttons are sent again only then. Main thread.
 */
class BookmarkedSentence {
    private var playing: UnitKey? = null
    private var marked: UnitKey? = null

    val shown: Boolean get() = marked != null && marked == playing

    fun onPlaying(key: UnitKey?): Boolean = changes {
        playing = key
        if (marked != key) marked = null
    }

    fun onBookmarked(key: UnitKey): Boolean = changes { marked = key }

    private inline fun changes(update: () -> Unit): Boolean {
        val before = shown
        update()
        return shown != before
    }
}
