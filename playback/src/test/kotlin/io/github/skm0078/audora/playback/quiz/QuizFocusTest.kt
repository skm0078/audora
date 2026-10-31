// File: playback/src/test/kotlin/io/github/skm0078/audora/playback/quiz/QuizFocusTest.kt
// Purpose: A call or another app taking the sound stops a quiz line; a notification's duck and a gain do not.

package io.github.skm0078.audora.playback.quiz

import android.media.AudioManager
import org.junit.Assert.assertEquals
import org.junit.Test

class QuizFocusTest {
    @Test
    fun `losing the sound stops the line and a duck or a gain does not`() {
        val cases = mapOf(
            AudioManager.AUDIOFOCUS_LOSS to true,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT to true,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK to false,
            AudioManager.AUDIOFOCUS_GAIN to false,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT to false,
        )

        cases.forEach { (change, stops) -> assertEquals("focus change $change", stops, QuizFocus.stopsOn(change)) }
    }
}
