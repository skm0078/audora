// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/MediaControllerPlaybackPort.kt
// Purpose: PlaybackPort over a Media3 MediaController connected to Audora's playback service (P1-04 contract).

package io.github.skm0078.audora.ui.reader

import android.content.Context
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.ListenableFuture
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.Pace
import io.github.skm0078.audora.core.model.ReadingModes
import io.github.skm0078.audora.core.model.SpeechStyle
import io.github.skm0078.audora.playback.contract.PlaybackContract
import io.github.skm0078.audora.playback.contract.SleepTimerMode
import io.github.skm0078.audora.playback.report.SoundedWrongReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Connect in onStart and [release] in onStop: a controller left connected keeps the service bound, and the
 * service can only stop once no controller is left (MediaSessionService docs). Main thread only.
 *
 * Everything goes through [PlaybackContract]: standard player commands, plus custom commands for starting a
 * book and the sleep timer, and the per-sentence metadata and session extras for what is being read.
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
        mutableState.value = PlaybackSnapshot.Connecting
        val pending = MediaController.Builder(context, PlaybackContract.sessionToken(context))
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

    override fun start(position: ReadingPosition, password: String?) = withPlayer("start") { player ->
        AudoraLog.i(TAG, "Start book=${position.bookId.logLabel} page=${position.page} char=${position.charStart}")
        val args = PlaybackContract.playFromArgs(position.bookId.value, position.page, position.charStart, password)
        send(player, PlaybackContract.PLAY_FROM, args, "start")
    }

    override fun play() = withPlayer("play") { player ->
        // A player stopped by an error must be prepared again before it plays.
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
        player.play()
    }

    override fun pause() = withPlayer("pause") { it.pause() }

    override fun skipToNextSentence() = withPlayer("skip forward") { it.seekToNextMediaItem() }

    override fun skipToPreviousSentence() = withPlayer("skip back") { it.seekToPreviousMediaItem() }

    override fun setSpeed(speed: Float) = setStyle(currentStyle().copy(rate = speed))

    override fun setPace(pace: Pace) = setStyle(currentStyle().copy(pace = pace))

    override fun setClausePauses(on: Boolean) = setStyle(currentStyle().copy(clausePauses = on))

    override fun setReadingModes(modes: ReadingModes) = setStyle(currentStyle().copy(modes = modes))

    private fun currentStyle(): SpeechStyle =
        mutableState.value.let { SpeechStyle(it.speed, it.pace, it.clausePauses, it.readingModes) }

    private fun setStyle(style: SpeechStyle) = withPlayer("speech style") { player ->
        AudoraLog.i(
            TAG,
            "Speech style rate=${style.rate} pace=${style.pace} clauses=${style.clausePauses} ${style.modes}",
        )
        send(player, PlaybackContract.SET_SPEECH_STYLE, PlaybackContract.speechStyleArgs(style), "speech style")
    }

    override fun setSleepTimer(setting: SleepTimerSetting) = withPlayer("sleep timer") { player ->
        val args = when (setting) {
            SleepTimerSetting.Off -> PlaybackContract.sleepTimerArgs(SleepTimerMode.OFF)

            is SleepTimerSetting.AfterMinutes -> PlaybackContract.sleepTimerArgs(
                SleepTimerMode.MINUTES,
                setting.minutes,
            )

            SleepTimerSetting.EndOfPage -> PlaybackContract.sleepTimerArgs(SleepTimerMode.END_OF_PAGE)

            SleepTimerSetting.EndOfChapter -> PlaybackContract.sleepTimerArgs(SleepTimerMode.END_OF_CHAPTER)
        }
        send(player, PlaybackContract.SET_SLEEP_TIMER, args, "sleep timer $setting")
    }

    override fun undoJump() = withPlayer("undo jump") { player ->
        send(player, PlaybackContract.UNDO_JUMP, Bundle.EMPTY, "undo jump")
    }

    override fun reportSoundedWrong(report: SoundedWrongReport) = withPlayer("report") { player ->
        send(player, PlaybackContract.REPORT_SOUNDED_WRONG, PlaybackContract.reportArgs(report), "report")
    }

    private fun send(player: MediaController, command: SessionCommand, args: Bundle, what: String) {
        val result = player.sendCustomCommand(command, args)
        result.addListener(
            {
                val outcome = runCatching { result.get() }.getOrNull()
                if (outcome?.resultCode != SessionResult.RESULT_SUCCESS) {
                    val error = outcome?.extras?.getString(PlaybackContract.RESULT_ERROR)
                    AudoraLog.w(TAG, "$what refused, result=${outcome?.resultCode} error=$error")
                }
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
        val extras = player.sessionExtras
        mutableState.value = PlaybackSnapshot(
            connection = PlaybackSnapshot.Connection.CONNECTED,
            position = positionOf(player.currentMediaItem?.mediaMetadata?.extras),
            wantsToPlay = player.playWhenReady,
            buffering = player.playbackState == Player.STATE_BUFFERING,
            speed = extras.getFloat(PlaybackContract.SESSION_SPEECH_RATE, SpeechStyle.NORMAL_RATE),
            pace = Pace.entries.firstOrNull { it.name == extras.getString(PlaybackContract.SESSION_PACE) }
                ?: Pace.NORMAL,
            clausePauses = extras.getBoolean(PlaybackContract.SESSION_CLAUSE_PAUSES, true),
            readingModes = ReadingModes.decode(extras.getString(PlaybackContract.SESSION_READING_MODES)),
            canSkipBack = player.currentMediaItem != null && player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS),
            canSkipForward = player.hasNextMediaItem(),
            sleepTimer = sleepTimerOf(extras),
            hasChapters = extras.getBoolean(PlaybackContract.SESSION_HAS_CHAPTERS, false),
            undoJumpTo = undoOf(extras),
            // A skipped sentence is reported in the extras while reading goes on; only a stop is an error.
            errorCode = player.playerError?.errorCodeName
                ?: extras.getString(PlaybackContract.SESSION_ERROR)?.takeUnless { player.playWhenReady },
        )
    }

    private fun positionOf(extras: Bundle?): ReadingPosition? {
        val bookId = extras?.getString(PlaybackContract.ITEM_BOOK_ID)?.takeIf { it.isNotBlank() }
        return if (bookId == null || !extras.containsKey(PlaybackContract.ITEM_PAGE)) {
            null
        } else {
            ReadingPosition(
                BookId(bookId),
                extras.getInt(PlaybackContract.ITEM_PAGE),
                extras.getInt(PlaybackContract.ITEM_CHAR_START),
            )
        }
    }

    private fun undoOf(extras: Bundle): ReadingPosition? {
        val bookId = extras.getString(PlaybackContract.SESSION_BOOK_ID)?.takeIf { it.isNotBlank() }
        return if (bookId == null || !extras.containsKey(PlaybackContract.SESSION_UNDO_PAGE)) {
            null
        } else {
            ReadingPosition(
                BookId(bookId),
                extras.getInt(PlaybackContract.SESSION_UNDO_PAGE),
                extras.getInt(PlaybackContract.SESSION_UNDO_CHAR),
            )
        }
    }

    private fun sleepTimerOf(extras: Bundle): SleepTimerState =
        when (extras.getString(PlaybackContract.SESSION_SLEEP_MODE)) {
            SleepTimerMode.MINUTES.name ->
                SleepTimerState.Counting(extras.getLong(PlaybackContract.SESSION_SLEEP_ENDS_AT))

            SleepTimerMode.END_OF_PAGE.name -> SleepTimerState.EndOfPage

            SleepTimerMode.END_OF_CHAPTER.name -> SleepTimerState.EndOfChapter

            else -> SleepTimerState.Off
        }

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
