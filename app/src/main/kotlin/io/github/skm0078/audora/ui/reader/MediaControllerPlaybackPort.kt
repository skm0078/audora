// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/MediaControllerPlaybackPort.kt
// Purpose: PlaybackPort over a Media3 MediaController connected to Audora's own playback session service.

package io.github.skm0078.audora.ui.reader

import android.content.Context
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.playback.contract.PlaybackAddress
import io.github.skm0078.audora.playback.contract.PlaybackContract
import io.github.skm0078.audora.playback.contract.ReaderSessionCommands
import io.github.skm0078.audora.playback.contract.SleepTimerSetting
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Connect in onStart and [release] in onStop: a controller left connected keeps the service bound, and the
 * service can only stop once no controller is left (MediaSessionService docs). Main thread only.
 *
 * The session service is found by package rather than by class, so the reader does not depend on the
 * playback service's class (the contract is `playback.contract` plus the standard Player API).
 */
@Suppress("TooManyFunctions") // One per PlaybackPort operation, plus connection handling.
class MediaControllerPlaybackPort(private val context: Context) : PlaybackPort {
    private val mutableState = MutableStateFlow(PlaybackSnapshot.Connecting)
    override val state: StateFlow<PlaybackSnapshot> = mutableState.asStateFlow()

    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh()
    }

    fun connect() {
        if (future != null) return
        val token = sessionToken()
        if (token == null) {
            AudoraLog.e(TAG, "No playback session service in this build")
            mutableState.value = PlaybackSnapshot(PlaybackSnapshot.Connection.UNAVAILABLE)
            return
        }
        mutableState.value = PlaybackSnapshot.Connecting
        val pending = MediaController.Builder(context, token)
            .setListener(ControllerListener())
            .buildAsync()
        future = pending
        pending.addListener({ onConnected(pending) }, ContextCompat.getMainExecutor(context))
    }

    fun release() {
        controller?.removeListener(listener)
        controller = null
        future?.let(MediaController::releaseFuture)
        future = null
    }

    override fun start(address: PlaybackAddress, password: String?) = withPlayer("start") { player ->
        val extras = Bundle().apply { password?.let { putString(PlaybackContract.EXTRA_PASSWORD, it) } }
        val request = MediaItem.Builder()
            .setMediaId(address.toMediaId())
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setExtras(extras).build())
            .build()
        AudoraLog.i(TAG, "Start book=${address.bookId.logLabel} page=${address.page} char=${address.charStart}")
        player.setMediaItem(request)
        player.prepare()
        player.play()
    }

    override fun play() = withPlayer("play") { player ->
        // A player stopped by an error must be prepared again before it plays.
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
        player.play()
    }

    override fun pause() = withPlayer("pause") { it.pause() }

    override fun skipToNextSentence() = withPlayer("skip forward") { it.seekToNextMediaItem() }

    override fun skipToPreviousSentence() = withPlayer("skip back") { it.seekToPreviousMediaItem() }

    override fun setSpeed(speed: Float) = withPlayer("speed") { it.setPlaybackSpeed(speed) }

    override fun setSleepTimer(setting: SleepTimerSetting) = withPlayer("sleep timer") { player ->
        val result = player.sendCustomCommand(
            ReaderSessionCommands.SET_SLEEP_TIMER,
            ReaderSessionCommands.sleepTimerArgs(setting),
        )
        result.addListener(
            {
                val code = runCatching { result.get().resultCode }.getOrNull()
                if (code != SessionResult.RESULT_SUCCESS) AudoraLog.w(TAG, "Sleep timer $setting refused, result=$code")
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    private fun onConnected(pending: ListenableFuture<MediaController>) {
        if (future !== pending) return // released meanwhile
        val connected = runCatching { pending.get() }
            .onFailure { AudoraLog.e(TAG, "Could not connect to the playback service", it) }
            .getOrNull()
        if (connected == null) {
            mutableState.value = PlaybackSnapshot(PlaybackSnapshot.Connection.UNAVAILABLE)
            return
        }
        controller = connected
        connected.addListener(listener)
        refresh()
    }

    private fun refresh() {
        val player = controller ?: return
        mutableState.value = PlaybackSnapshot(
            connection = PlaybackSnapshot.Connection.CONNECTED,
            position = PlaybackAddress.parse(player.currentMediaItem?.mediaId),
            wantsToPlay = player.playWhenReady,
            buffering = player.playbackState == Player.STATE_BUFFERING,
            speed = player.playbackParameters.speed,
            canSkipBack = player.currentMediaItem != null && player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS),
            canSkipForward = player.hasNextMediaItem(),
            sleepTimer = ReaderSessionCommands.sleepTimerStateFrom(player.sessionExtras),
            errorCode = player.playerError?.errorCodeName,
        )
    }

    private fun sessionToken(): SessionToken? = SessionToken.getAllServiceTokens(context)
        .firstOrNull { it.packageName == context.packageName && it.type == SessionToken.TYPE_SESSION_SERVICE }

    private inline fun withPlayer(action: String, block: (MediaController) -> Unit) {
        val player = controller
        if (player == null) {
            AudoraLog.w(TAG, "Ignored $action: not connected to the playback service")
        } else {
            block(player)
        }
    }

    private inner class ControllerListener : MediaController.Listener {
        override fun onExtrasChanged(controller: MediaController, extras: Bundle) = refresh()

        override fun onDisconnected(controller: MediaController) {
            AudoraLog.w(TAG, "Playback service disconnected")
            this@MediaControllerPlaybackPort.controller = null
            future = null
            mutableState.value = PlaybackSnapshot(PlaybackSnapshot.Connection.UNAVAILABLE)
        }
    }

    private companion object {
        const val TAG = "Audora.ReaderPlayer"
    }
}
