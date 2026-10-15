// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/session/PlaylistView.kt
// Purpose: Read-only questions about the player's playlist, answered as PlaybackItems.

package io.github.skm0078.audora.playback.session

import androidx.media3.common.Player
import io.github.skm0078.audora.playback.playlist.PlaybackItem
import io.github.skm0078.audora.playback.queue.UnitKey

/** Main thread only. */
class PlaylistView(private val player: Player) {
    fun itemAt(index: Int): PlaybackItem? =
        if (index in 0 until player.mediaItemCount) PlaylistItems.itemOf(player.getMediaItemAt(index)) else null

    fun current(): PlaybackItem? = itemAt(player.currentMediaItemIndex)

    fun next(): PlaybackItem? = itemAt(player.currentMediaItemIndex + 1)

    fun currentKey(): UnitKey? = (current() as? PlaybackItem.Sentence)?.unit?.let(UnitKey::of)

    fun indexOf(key: UnitKey): Int? = (0 until player.mediaItemCount).firstOrNull { index ->
        (itemAt(index) as? PlaybackItem.Sentence)?.unit?.let(UnitKey::of) == key
    }

    /** For logs: the sentence key, "more" for the placeholder. Never the text. */
    fun describeCurrent(): String = when (val item = current()) {
        is PlaybackItem.Sentence -> UnitKey.of(item.unit).toString()
        is PlaybackItem.More -> "more"
        null -> "none"
    }
}
