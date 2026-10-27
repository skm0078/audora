// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/service/QuickControls.kt
// Purpose: The controls a listener reaches without opening the app, wired to the service's player: the notification's
//          and lock screen's buttons (CF-1), resuming when headphones come back (CF-4) and the time listened that the
//          home-screen widget counts down (CF-7).

package io.github.skm0078.audora.playback.service

import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import io.github.skm0078.audora.core.config.PlayerControlsConfig
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.notes.BookmarkStore
import io.github.skm0078.audora.playback.R
import io.github.skm0078.audora.playback.controls.ListeningReports
import io.github.skm0078.audora.playback.controls.ListeningSpans
import io.github.skm0078.audora.playback.controls.ReconnectResume
import io.github.skm0078.audora.playback.controls.ReconnectWatcher
import io.github.skm0078.audora.playback.controls.ResumeOnReconnect
import io.github.skm0078.audora.playback.playlist.PlaybackItem
import io.github.skm0078.audora.playback.queue.UnitKey
import io.github.skm0078.audora.playback.session.BookSession
import io.github.skm0078.audora.playback.session.PlaylistItems
import kotlinx.coroutines.CoroutineScope

/**
 * Main thread. Built in the service's onCreate before its session (which takes [initialButtons], [lockScreen] and
 * [headsetBookmarks]); [attach] once the session exists, [release] in onDestroy. A bookmark from the lock screen or a
 * headset buzzes and fills the lock screen's bookmark while its sentence plays, since the screen may be off.
 */
@OptIn(UnstableApi::class)
@Suppress("LongParameterList") // The service's parts these controls reach; the config has a default.
class QuickControls(
    private val context: Context,
    private val player: Player,
    private val bookSession: () -> BookSession,
    private val bookmarkStore: BookmarkStore,
    private val scope: CoroutineScope,
    private val pipelineVersion: Int,
    private val buzzMs: Long,
    config: PlayerControlsConfig = PlayerControlsConfig(),
) {
    private val buttons = LockScreenButtons(
        LockScreenButtons.Labels(
            previous = context.getString(R.string.lockscreen_previous),
            next = context.getString(R.string.lockscreen_next),
            bookmark = context.getString(R.string.lockscreen_bookmark),
            bookmarked = context.getString(R.string.lockscreen_bookmarked),
        ),
    )
    private val marked = BookmarkedSentence()
    private var session: MediaSession? = null

    /** A headset's double press (item 20). */
    val headsetBookmarks = bookmarks(source = "Headset")

    /** The lock screen's and notification's buttons. */
    val lockScreen = LockScreenActions({ player }, bookSession, bookmarks(source = "Lock screen"))

    val initialButtons: List<CommandButton> get() = buttons.buttons(bookmarked = false)

    private val resumeChoice = ResumeOnReconnect(context)

    private val reconnect = ReconnectWatcher(
        context,
        player,
        ReconnectResume(
            windowMs = config.reconnectWindowMs,
            // Media3 keeps a paused service in the foreground this long; after it, Android 12+ refuses a background
            // start unless the app may run unrestricted (Settings → Battery).
            foregroundGraceMs = MediaSessionService.DEFAULT_FOREGROUND_SERVICE_TIMEOUT_MS,
            enabled = { resumeChoice.on },
            unrestricted = ::unrestricted,
            clock = SystemClock::elapsedRealtime,
        ),
    ) { bookSession().bookId?.logLabel }

    private val listening = ListeningReports(
        context,
        player,
        ListeningSpans(config.widgetRefreshMs, SystemClock::elapsedRealtime),
        config.widgetRefreshMs,
    ) { bookSession().bookId }

    private val sentences = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val key = (PlaylistItems.itemOf(mediaItem) as? PlaybackItem.Sentence)?.unit?.let(UnitKey::of)
            if (marked.onPlaying(key)) publish()
        }
    }

    fun attach(session: MediaSession) {
        this.session = session
        player.addListener(sentences)
        reconnect.start()
        listening.start()
    }

    fun release() {
        player.removeListener(sentences)
        reconnect.release()
        listening.release()
        session = null
    }

    private fun bookmarks(source: String) = HeadsetBookmarks(
        store = bookmarkStore,
        scope = scope,
        pipelineVersion = pipelineVersion,
        clock = System::currentTimeMillis,
        onSaved = ::onBookmarkSaved,
        source = source,
    )

    private fun onBookmarkSaved(unit: ReadingUnit) {
        context.buzz(buzzMs)
        if (marked.onBookmarked(UnitKey.of(unit))) publish()
    }

    private fun publish() {
        session?.setMediaButtonPreferences(buttons.buttons(marked.shown))
    }

    private fun unrestricted(): Boolean =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true
}
