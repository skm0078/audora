// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/service/MediaButtons.kt
// Purpose: A headset's play/pause button: one press plays or pauses, two bookmark, three go back a sentence (item 20).

package io.github.skm0078.audora.playback.service

import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.ViewConfiguration
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.session.MediaSession
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.playback.session.BookSession

/**
 * Only the play/pause and hook keys from outside the app (a wired or Bluetooth headset, a car's single button) are
 * taken over; next and previous already move by sentence (each sentence is one media item), and every other key, a
 * press with no book open (Media3 then resumes the last book) and a long press keep Media3's handling.
 */
@OptIn(UnstableApi::class)
class MediaButtons(
    private val player: () -> Player,
    private val bookSession: () -> BookSession,
    private val bookmarks: HeadsetBookmarks,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val presses = HeadsetPresses(
        windowMs = ViewConfiguration.getDoubleTapTimeout().toLong(),
        schedule = { delayMs, action ->
            val task = Runnable(action)
            handler.postDelayed(task, delayMs)
            ({ handler.removeCallbacks(task) })
        },
        onGesture = ::apply,
    )

    /** True when the event was handled here. */
    fun handle(controller: MediaSession.ControllerInfo, intent: Intent): Boolean {
        val event = keyEventOf(intent)
        val ours = event != null && event.keyCode in PLAY_PAUSE_KEYS && event.repeatCount == 0 &&
            controller.controllerVersion == MediaSession.ControllerInfo.LEGACY_CONTROLLER_VERSION &&
            player().mediaItemCount > 0
        if (!ours) {
            presses.flush()
        } else if (event?.action == KeyEvent.ACTION_DOWN) {
            presses.onPress()
        }
        return ours
    }

    private fun apply(gesture: HeadsetGesture) {
        val player = player()
        AudoraLog.i(TAG, "Headset ${gesture.name.lowercase()}")
        when (gesture) {
            HeadsetGesture.PLAY_PAUSE -> Util.handlePlayPauseButtonAction(player)
            HeadsetGesture.BACK_ONE_SENTENCE -> player.seekToPreviousMediaItem()
            HeadsetGesture.BOOKMARK -> bookmark()
        }
    }

    private fun bookmark() {
        val session = bookSession()
        val bookId = session.bookId
        val unit = session.currentSentence()
        if (bookId == null || unit == null) {
            AudoraLog.w(TAG, "Headset bookmark ignored: no sentence playing")
        } else {
            bookmarks.add(bookId, unit)
        }
    }

    private fun keyEventOf(intent: Intent): KeyEvent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
    } else {
        @Suppress("DEPRECATION") // The typed overload is API 33; minSdk is 31.
        intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
    }

    private companion object {
        const val TAG = "Playback.Buttons"
        val PLAY_PAUSE_KEYS = setOf(KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
    }
}
