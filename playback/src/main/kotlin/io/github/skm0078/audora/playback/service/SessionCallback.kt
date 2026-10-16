// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/service/SessionCallback.kt
// Purpose: Accepts controllers and runs the contract's custom commands (play from, voice, sleep timer).

package io.github.skm0078.audora.playback.service

import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.playback.contract.PlaybackContract
import io.github.skm0078.audora.playback.contract.PlaybackErrorCode
import io.github.skm0078.audora.playback.contract.SleepTimerMode
import io.github.skm0078.audora.playback.session.BookSession
import io.github.skm0078.audora.playback.voice.SpeechVoices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Only controllers from Audora itself get the custom commands; the system UI, Bluetooth and other apps get
 * the standard transport controls (play, pause, skip), which is all a lock screen or headset needs.
 */
@OptIn(UnstableApi::class)
class SessionCallback(
    private val ownPackage: String,
    private val scope: CoroutineScope,
    private val voices: SpeechVoices,
    private val bookSession: () -> BookSession,
    private val resumption: () -> PlaybackResumption?,
) : MediaSession.Callback {
    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): MediaSession.ConnectionResult {
        val trusted = controller.packageName == ownPackage
        AudoraLog.i(TAG, "Controller connected: ${controller.packageName} trusted=$trusted")
        val builder = MediaSession.ConnectionResult.AcceptedResultBuilder(session)
        if (trusted) {
            builder.setAvailableSessionCommands(
                MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                    .add(PlaybackContract.PLAY_FROM)
                    .add(PlaybackContract.SET_VOICE)
                    .add(PlaybackContract.SET_SLEEP_TIMER)
                    .build(),
            )
        }
        return builder.build()
    }

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle,
    ): ListenableFuture<SessionResult> = when (customCommand.customAction) {
        PlaybackContract.ACTION_PLAY_FROM -> playFrom(args)
        PlaybackContract.ACTION_SET_VOICE -> Futures.immediateFuture(setVoice(args))
        PlaybackContract.ACTION_SET_SLEEP_TIMER -> Futures.immediateFuture(setSleepTimer(args))
        else -> Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
    }

    override fun onPlaybackResumption(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        isForPlayback: Boolean,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        AudoraLog.i(TAG, "Playback resumption from ${controller.packageName} forPlayback=$isForPlayback")
        return resumption()?.resume(isForPlayback) ?: PlaybackResumption.unsupported()
    }

    private fun playFrom(args: Bundle): ListenableFuture<SessionResult> {
        val bookId = args.getString(PlaybackContract.ARG_BOOK_ID)?.takeIf { it.isNotBlank() }
        val page = args.getInt(PlaybackContract.ARG_PAGE, -1)
        val charStart = args.getInt(PlaybackContract.ARG_CHAR_START, 0)
        if (bookId == null || page < 0 || charStart < 0) {
            AudoraLog.w(TAG, "PLAY_FROM rejected: book set=${bookId != null} page=$page char=$charStart")
            return Futures.immediateFuture(failure(PlaybackErrorCode.BAD_REQUEST))
        }
        val password = args.getString(PlaybackContract.ARG_PASSWORD)
        val result = SettableFuture.create<SessionResult>()
        scope.launch {
            val outcome = bookSession().playFrom(BookId(bookId), page, charStart, password)
            result.set(
                when (outcome) {
                    is Outcome.Success -> SessionResult(SessionResult.RESULT_SUCCESS)
                    is Outcome.Failure -> failure(outcome.error.code)
                },
            )
        }
        return result
    }

    private fun setVoice(args: Bundle): SessionResult {
        val (voice, lang) = PlaybackContract.voiceFrom(args) ?: return failure(PlaybackErrorCode.BAD_REQUEST)
        voices.select(lang, voice)
        bookSession().restartRendering()
        return SessionResult(SessionResult.RESULT_SUCCESS)
    }

    private fun setSleepTimer(args: Bundle): SessionResult {
        val mode = PlaybackContract.sleepModeFrom(args)
        val minutes = args.getInt(PlaybackContract.ARG_SLEEP_MINUTES, 0)
        if (mode == null || (mode == SleepTimerMode.MINUTES && minutes !in 1..MAX_SLEEP_MINUTES)) {
            return failure(PlaybackErrorCode.BAD_REQUEST)
        }
        // End of chapter needs a book with chapters; the reader only offers it then (SESSION_HAS_CHAPTERS).
        val accepted = bookSession().setSleepTimer(mode, minutes)
        return if (accepted) SessionResult(SessionResult.RESULT_SUCCESS) else failure(PlaybackErrorCode.BAD_REQUEST)
    }

    private fun failure(code: PlaybackErrorCode): SessionResult {
        val resultCode = if (code == PlaybackErrorCode.BAD_REQUEST) {
            SessionResult.RESULT_ERROR_BAD_VALUE
        } else {
            SessionResult.RESULT_ERROR_UNKNOWN
        }
        return SessionResult(resultCode, Bundle().apply { putString(PlaybackContract.RESULT_ERROR, code.name) })
    }

    private companion object {
        const val TAG = "Playback.Session"

        /** A day; anything longer is a bug in the caller, not a bedtime. */
        const val MAX_SLEEP_MINUTES = 24 * 60
    }
}
