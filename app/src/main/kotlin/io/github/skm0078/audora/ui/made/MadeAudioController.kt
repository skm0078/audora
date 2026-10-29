// File: app/src/main/kotlin/io/github/skm0078/audora/ui/made/MadeAudioController.kt
// Purpose: The in-app player screen's connection to the saved-audio player (ADR-068): loads a set's chapters, goes
//          on from where the listener stopped, sets the sleep timer and reports what is playing.

package io.github.skm0078.audora.ui.made

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import io.github.skm0078.audora.core.library.OutputSet
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.playback.outputs.MadeAudioService
import io.github.skm0078.audora.playback.outputs.MadePositions
import io.github.skm0078.audora.playback.outputs.MadeSleepContract
import io.github.skm0078.audora.ui.reader.SleepTimerSetting
import io.github.skm0078.audora.ui.reader.SleepTimerState
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
    val sleepTimer: SleepTimerState = SleepTimerState.Off,
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
            sleepTimer = sleepTimerOf(player.sessionExtras),
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

    fun setSpeed(speed: Float) = withPlayer { it.setPlaybackSpeed(speed) }

    /** The timer runs in the service, so it pauses with the screen off; End of page has no meaning here. */
    fun sleep(setting: SleepTimerSetting) = withPlayer { player -> sendSleep(player, setting) }

    private fun sendSleep(player: MediaController, setting: SleepTimerSetting) {
        val args = Bundle().apply {
            when (setting) {
                is SleepTimerSetting.AfterMinutes -> putInt(MadeSleepContract.ARG_MINUTES, setting.minutes)
                SleepTimerSetting.EndOfChapter -> putInt(MadeSleepContract.ARG_CHAPTERS, 1)
                is SleepTimerSetting.AfterChapters -> putInt(MadeSleepContract.ARG_CHAPTERS, setting.count)
                SleepTimerSetting.Off, SleepTimerSetting.EndOfPage -> Unit
            }
        }
        val result = player.sendCustomCommand(MadeSleepContract.SLEEP, args)
        result.addListener(
            {
                val code = runCatching { result.get().resultCode }.getOrNull()
                if (code != SessionResult.RESULT_SUCCESS) AudoraLog.w(TAG, "Sleep timer refused, result=$code")
            },
            ContextCompat.getMainExecutor(context),
        )
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
        if (loaded != set.key) {
            // A timer set for the set that was playing does not carry over to this one.
            if (loaded != null) sendSleep(connected, SleepTimerSetting.Off)
            load(connected, set, bookTitle, play)
        }
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

        /** What the service publishes, in the book player's own words, so both players show a timer alike. */
        fun sleepTimerOf(extras: Bundle): SleepTimerState {
            val chapters = MadeSleepContract.chaptersIn(extras)
            return when {
                MadeSleepContract.endsAtIn(extras) != null ->
                    SleepTimerState.Counting(requireNotNull(MadeSleepContract.endsAtIn(extras)))

                chapters == 1 -> SleepTimerState.EndOfChapter

                chapters > 1 -> SleepTimerState.AfterChapters(chapters)

                else -> SleepTimerState.Off
            }
        }
    }
}
