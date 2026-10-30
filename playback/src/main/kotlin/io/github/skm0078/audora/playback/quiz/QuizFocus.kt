// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/quiz/QuizFocus.kt
// Purpose: Which audio focus changes stop a quiz, word or voice-sample line: a call or another app taking the sound.

package io.github.skm0078.audora.playback.quiz

import android.media.AudioManager

internal object QuizFocus {
    /**
     * A loss, lasting or for a while (a call, a voice message, another player), stops the line, which is short and
     * is not resumed. A duck (a notification sound) does not: Android lowers the line itself.
     */
    fun stopsOn(change: Int): Boolean =
        change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
}
