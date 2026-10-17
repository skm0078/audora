// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/service/SessionCallback.kt
// Purpose: Accepts controllers and runs the contract's custom commands (play from, voice, sleep timer, reports).

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
import io.github.skm0078.audora.core.model.SleepOptions
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.playback.contract.PlaybackContract
import io.github.skm0078.audora.playback.contract.PlaybackErrorCode
import io.github.skm0078.audora.playback.contract.SleepTimerMode
import io.github.skm0078.audora.playback.session.BookSession
import io.github.skm0078.audora.playback.session.SleepOptionsStore
import io.github.skm0078.audora.playback.voice.SpeechStyleStore
import io.github.skm0078.audora.playback.voice.SpeechVoices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Only controllers from Audora itself get the custom commands; the system UI, Bluetooth and other apps get
 * the standard transport controls (play, pause, skip), which is all a lock screen or headset needs.
 */
@OptIn(UnstableApi::class)
@Suppress("TooManyFunctions") // One handler per custom command, plus the Media3 callbacks.
class SessionCallback(
    private val ownPackage: String,
    private val scope: CoroutineScope,
    private val voices: SpeechVoices,
    private val stores: SessionStores,
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
                    .add(PlaybackContract.SET_SPEECH_STYLE)
                    .add(PlaybackContract.REPORT_SOUNDED_WRONG)
                    .add(PlaybackContract.UNDO_JUMP)
                    .add(PlaybackContract.SET_SLEEP_OPTIONS)
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
        PlaybackContract.ACTION_SET_SPEECH_STYLE -> Futures.immediateFuture(setSpeechStyle(args))
        PlaybackContract.ACTION_REPORT_SOUNDED_WRONG -> Futures.immediateFuture(report(args))
        PlaybackContract.ACTION_UNDO_JUMP -> Futures.immediateFuture(undoJump())
        PlaybackContract.ACTION_SET_SLEEP_OPTIONS -> Futures.immediateFuture(setSleepOptions(args))
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

    private fun setSpeechStyle(args: Bundle): SessionResult {
        val requested = PlaybackContract.speechStyleFrom(args) ?: return failure(PlaybackErrorCode.BAD_REQUEST)
        val style = stores.style
        val before = style.current
        val applied = style.set(requested)
        if (applied != before) bookSession().onSpeechStyleChanged(style.config.playerSpeed(applied.rate))
        return SessionResult(SessionResult.RESULT_SUCCESS)
    }

    /** Logs a "This sounded wrong" report (RI-8b) with the voice reading its language; never the sentence's text. */
    private fun report(args: Bundle): SessionResult {
        val report = PlaybackContract.reportFrom(args) ?: return failure(PlaybackErrorCode.BAD_REQUEST)
        AudoraLog.i(TAG, report.logLine(voices.knownVoice(report.lang), stores.style.current.modes))
        return SessionResult(SessionResult.RESULT_SUCCESS)
    }

    private fun undoJump(): SessionResult = if (bookSession().undoJump()) {
        SessionResult(SessionResult.RESULT_SUCCESS)
    } else {
        failure(PlaybackErrorCode.BAD_REQUEST)
    }

    private fun setSleepTimer(args: Bundle): SessionResult {
        val mode = PlaybackContract.sleepModeFrom(args)
        val minutes = args.getInt(PlaybackContract.ARG_SLEEP_MINUTES, 0)
        val chapters = args.getInt(PlaybackContract.ARG_SLEEP_CHAPTERS, 1)
        val valid = mode != null && chapters >= 1 && (mode != SleepTimerMode.MINUTES || minutes in 1..MAX_SLEEP_MINUTES)
        if (!valid) return failure(PlaybackErrorCode.BAD_REQUEST)
        // End of chapter needs a book with chapters; the reader only offers it then (SESSION_HAS_CHAPTERS).
        val accepted = bookSession().setSleepTimer(mode, minutes, chapters)
        return if (accepted) SessionResult(SessionResult.RESULT_SUCCESS) else failure(PlaybackErrorCode.BAD_REQUEST)
    }

    private fun setSleepOptions(args: Bundle): SessionResult {
        val line = args.getString(PlaybackContract.ARG_SLEEP_OPTIONS) ?: return failure(PlaybackErrorCode.BAD_REQUEST)
        stores.sleepOptions.set(SleepOptions.decode(line))
        bookSession().onSleepOptionsChanged()
        return SessionResult(SessionResult.RESULT_SUCCESS)
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

/** The listener's saved settings the custom commands change: speech style and the sleep timer extras. */
class SessionStores(val style: SpeechStyleStore, val sleepOptions: SleepOptionsStore)
