// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/contract/LockScreenCommands.kt
// Purpose: The custom commands behind the notification's and lock screen's buttons: previous sentence, next sentence
//          and bookmark (CF-1). Only Audora's own controllers get them; the system's media controls act through
//          Audora's notification controller.

package io.github.skm0078.audora.playback.contract

import android.os.Bundle
import androidx.media3.session.SessionCommand

object LockScreenCommands {
    private const val PREFIX = "io.github.skm0078.audora.playback.lockscreen"

    const val ACTION_PREVIOUS_SENTENCE = "$PREFIX.PREVIOUS_SENTENCE"
    const val ACTION_NEXT_SENTENCE = "$PREFIX.NEXT_SENTENCE"
    const val ACTION_BOOKMARK = "$PREFIX.BOOKMARK"

    /**
     * Back one whole sentence. Not the player's own "previous", which restarts the sentence playing once it is a few
     * seconds in: on a lock screen that reads as a button that did nothing.
     */
    val PREVIOUS_SENTENCE = SessionCommand(ACTION_PREVIOUS_SENTENCE, Bundle.EMPTY)

    /** On to the next sentence. */
    val NEXT_SENTENCE = SessionCommand(ACTION_NEXT_SENTENCE, Bundle.EMPTY)

    /** Bookmarks the sentence playing, like a headset's double press; refused when none plays. */
    val BOOKMARK = SessionCommand(ACTION_BOOKMARK, Bundle.EMPTY)

    val ALL: List<SessionCommand> = listOf(PREVIOUS_SENTENCE, NEXT_SENTENCE, BOOKMARK)

    val ACTIONS: Set<String> = ALL.mapTo(mutableSetOf()) { it.customAction }
}
