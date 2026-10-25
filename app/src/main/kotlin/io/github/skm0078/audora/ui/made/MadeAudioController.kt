// File: app/src/main/kotlin/io/github/skm0078/audora/ui/made/MadeAudioController.kt
// Purpose: The in-app player screen's connection to the saved-audio player (ADR-068): loads a set's chapters, goes
//          on from where the listener stopped, and reports what is playing.

package io.github.skm0078.audora.ui.made

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import io.github.skm0078.audora.core.library.OutputSet
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.playback.outputs.MadeAudioService
import io.github.skm0078.audora.playback.outputs.MadePositions
import java.util.concurrent.ExecutionException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the player screen shows; [failed] when a chapter could not be played (deleted or moved since). */
data class MadePlayerState(
    val connected: Boolean = false,
    val playing: Boolean = false,
    val index: Int = 0,
    val count: Int = 0,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val speed: Float = 1f,
    val failed: Boolean = false,
)

/**
 * Connect when the screen shows and [release] when it goes: the playing goes on in the service, with its own
 * notification, like any media app. Opening the set that is already loaded keeps playing where it is; another set
 * replaces it, starting where that one was left. Main thread only.
 */
@Suppress("TooManyFunctions") // One per player control, plus connection handling.
class MadeAudioController(private val context: Context) {
    private val mutableState = MutableStateFlow(MadePlayerState())
    val state: StateFlow<MadePlayerState> = mutableState.asStateFlow()

    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh()

        override fun onPlayerError(error: PlaybackException) {
            AudoraLog.w(TAG, "Made audio failed: ${error.errorCodeName}")
            mutableState.value = mutableState.value.copy(failed = true)
        }
    }

    /**
     * Connects and loads [set] unless the player already holds it. [play] starts it at once: true only when the
     * listener just opened the player, never when Android restores the screen after the app was closed (C7: playback
     * starts only from a user action).
     */
    fun connect(set: OutputSet, bookTitle: String, play: Boolean) {
        if (future != null) return
        val token = SessionToken(context, ComponentName(context, MadeAudioService::class.java))
        val pending = MediaController.Builder(context, token).buildAsync()
        future = pending
        pending.addListener({ onConnected(pending, set, bookTitle, play) }, ContextCompat.getMainExecutor(context))
    }

    fun release() {
        controller?.removeListener(listener)
        controller = null
        future?.let(MediaController::releaseFuture)
        future = null
    }

    /** Called by the screen twice a second for the position; a controller does not report it by itself. */
    fun refresh() {
        val player = controller ?: return
        mutableState.value = mutableState.value.copy(
            connected = true,
            playing = player.isPlaying,
            index = player.currentMediaItemIndex,
            count = player.mediaItemCount,
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = player.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0) ?: 0,
            speed = player.playbackParameters.speed,
        )
    }

    fun playPause() = withPlayer { if (it.isPlaying) it.pause() else it.play() }

    fun seekTo(positionMs: Long) = withPlayer { it.seekTo(positionMs) }

    fun skip(byMs: Long) = withPlayer { player ->
        val end = player.duration.takeIf { it != C.TIME_UNSET } ?: Long.MAX_VALUE
        player.seekTo((player.currentPosition + byMs).coerceIn(0, end))
    }

    fun chapter(index: Int) = withPlayer { if (index in 0 until it.mediaItemCount) it.seekTo(index, 0) }

    fun previousChapter() = withPlayer { it.seekToPreviousMediaItem() }

    fun nextChapter() = withPlayer { it.seekToNextMediaItem() }

    /** The next speed of [SPEEDS], round to the first. */
    fun nextSpeed() = withPlayer { player ->
        val now = player.playbackParameters.speed
        player.setPlaybackSpeed(SPEEDS.firstOrNull { it > now + SPEED_EPSILON } ?: SPEEDS.first())
    }

    private fun withPlayer(action: (MediaController) -> Unit) {
        controller?.let {
            action(it)
            refresh()
        }
    }

    private fun onConnected(
        pending: ListenableFuture<MediaController>,
        set: OutputSet,
        bookTitle: String,
        play: Boolean,
    ) {
        if (pending != future) return
        val connected = try {
            pending.get()
        } catch (e: ExecutionException) {
            AudoraLog.w(TAG, "Made audio player not reached: ${e.cause?.javaClass?.simpleName}")
            mutableState.value = MadePlayerState(failed = true)
            return
        }
        controller = connected
        connected.addListener(listener)
        val loaded = connected.currentMediaItem?.mediaId?.let(MadePositions::setOf)
        if (loaded != set.key) load(connected, set, bookTitle, play)
        refresh()
    }

    private fun load(player: MediaController, set: OutputSet, bookTitle: String, play: Boolean) {
        val items = set.files.map { file ->
            MediaItem.Builder()
                .setMediaId(MadePositions.mediaId(set.key, file.part))
                .setUri(Uri.parse(file.location))
                .setMimeType(file.mimeType)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(file.name).setArtist(bookTitle).build())
                .build()
        }
        val start = MadePositions(context).get(set.key)?.takeIf { it.index < items.size }
        AudoraLog.i(TAG, "Made audio loaded, files=${items.size} resumed=${start != null} play=$play")
        player.setMediaItems(items, start?.index ?: 0, start?.positionMs ?: 0)
        player.prepare()
        if (play) player.play()
    }

    companion object {
        private const val TAG = "Library.MadePlayer"
        private const val SPEED_EPSILON = 0.01f
        val SPEEDS = listOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
    }
}
