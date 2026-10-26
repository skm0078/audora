// File: app/src/main/kotlin/io/github/skm0078/audora/tile/PlaybackTileService.kt
// Purpose: The Quick Settings tile (plan item 36): play or pause Audora from the notification shade, or pick the
//          last book up when nothing is loaded. Talks to the player through the media session only.

package io.github.skm0078.audora.tile

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.github.skm0078.audora.MainActivity
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.playback.contract.PendingIntentCodes
import io.github.skm0078.audora.ui.reader.MediaControllerPlaybackPort
import io.github.skm0078.audora.ui.reader.PlaybackSnapshot
import io.github.skm0078.audora.widget.ResumeWidgetProvider
import io.github.skm0078.audora.widget.ResumeWidgetStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Connected to the media session only while the shade shows the tile (a connected controller keeps the playback
 * service bound). A tap is the listener's own action, so starting to read from it keeps the "playback starts only
 * from a user action" invariant; with nothing loaded it opens Audora on the book read last, which reads on.
 */
class PlaybackTileService : TileService() {
    private var scope: CoroutineScope? = null
    private var port: MediaControllerPlaybackPort? = null
    private var watch: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        val connected = MediaControllerPlaybackPort(applicationContext).also { it.connect() }
        val main = MainScope()
        port = connected
        scope = main
        watch = main.launch { connected.state.collect(::draw) }
    }

    override fun onStopListening() {
        watch?.cancel()
        scope?.cancel()
        port?.release()
        watch = null
        scope = null
        port = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val player = port
        when (tileTap(player?.state?.value)) {
            TileTap.PAUSE -> player?.pause()

            TileTap.PLAY -> player?.play()

            TileTap.OPEN_APP -> openAudora()

            // Tapped as the shade opened: the tap waits for the player instead of opening the app.
            TileTap.WAIT -> scope?.launch { act(player) }
        }
    }

    /** The tap once connected, or after [CONNECT_WAIT_MS]; a player still not there opens the app. */
    private suspend fun act(player: MediaControllerPlaybackPort?) {
        val settled = player?.let {
            withTimeoutOrNull(CONNECT_WAIT_MS) {
                it.state.first { snapshot -> snapshot.connection != PlaybackSnapshot.Connection.CONNECTING }
            }
        }
        when (tileTap(settled)) {
            TileTap.PAUSE -> player?.pause()
            TileTap.PLAY -> player?.play()
            TileTap.OPEN_APP, TileTap.WAIT -> openAudora()
        }
    }

    private fun draw(snapshot: PlaybackSnapshot) {
        val tile = qsTile ?: return
        val loaded = snapshot.position != null
        tile.state = when {
            snapshot.connection == PlaybackSnapshot.Connection.UNAVAILABLE -> Tile.STATE_UNAVAILABLE
            loaded && snapshot.wantsToPlay -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.label = getString(R.string.app_name)
        tile.subtitle = getString(
            when {
                !loaded -> R.string.tile_resume
                snapshot.wantsToPlay -> R.string.tile_playing
                else -> R.string.tile_paused
            },
        )
        tile.updateTile()
    }

    /** Opens Audora on the book read last (it reads on from there), or the library when none was read yet. */
    private fun openAudora() {
        val book = ResumeWidgetStore(applicationContext).read()
        AudoraLog.i(TAG, "Tile opens Audora, book=${book?.bookId?.take(LOG_ID_LENGTH)}")
        val intent = book?.let { ResumeWidgetProvider.continueIntent(applicationContext, it.bookId) }
            ?: Intent(applicationContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startAndCollapse(intent)
    }

    /**
     * The PendingIntent form on Android 14 and later, the Intent form before (where the other does not exist). The
     * version check lives here with the suppression, so no caller can reach the old form on a newer Android.
     */
    @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
    private fun startAndCollapse(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this,
                    PendingIntentCodes.OPEN_FROM_TILE,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
        } else {
            startActivityAndCollapse(intent)
        }
    }

    private companion object {
        const val TAG = "Audora.Tile"
        const val LOG_ID_LENGTH = 8

        // Long enough for a cold start of the playback service on a slow phone, short enough to feel like a tap.
        const val CONNECT_WAIT_MS = 3_000L
    }
}

/** What a tap on the tile does. */
internal enum class TileTap { PAUSE, PLAY, OPEN_APP, WAIT }

/**
 * Pause or play the book loaded; open the app when none is (it reads on from the book read last); wait while the
 * tile is still connecting, since the player's state is not known yet and opening the app would be wrong if a book
 * is playing.
 */
internal fun tileTap(snapshot: PlaybackSnapshot?): TileTap = when {
    snapshot == null -> TileTap.OPEN_APP
    snapshot.connection == PlaybackSnapshot.Connection.CONNECTING -> TileTap.WAIT
    snapshot.position != null && snapshot.wantsToPlay -> TileTap.PAUSE
    snapshot.position != null -> TileTap.PLAY
    else -> TileTap.OPEN_APP
}
