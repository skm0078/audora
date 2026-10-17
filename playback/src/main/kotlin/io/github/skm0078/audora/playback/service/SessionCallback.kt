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
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.SleepOptions
import io.github.skm0078.audora.core.model.SpeechStyle
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.playback.contract.PlaybackContract
import io.github.skm0078.audora.playback.contract.PlaybackErrorCode
import io.github.skm0078.audora.playback.contract.SleepTimerMode
import io.github.skm0078.audora.playback.notes.BookContext
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
                    .add(PlaybackContract.SET_OWN_SETTINGS)
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
        PlaybackContract.ACTION_SET_VOICE -> later { setVoice(args) }
        PlaybackContract.ACTION_SET_SLEEP_TIMER -> Futures.immediateFuture(setSleepTimer(args))
        PlaybackContract.ACTION_SET_SPEECH_STYLE -> later { setSpeechStyle(args) }
        PlaybackContract.ACTION_REPORT_SOUNDED_WRONG -> Futures.immediateFuture(report(args))
        PlaybackContract.ACTION_UNDO_JUMP -> Futures.immediateFuture(undoJump())
        PlaybackContract.ACTION_SET_SLEEP_OPTIONS -> Futures.immediateFuture(setSleepOptions(args))
        PlaybackContract.ACTION_SET_OWN_SETTINGS -> later { setOwnSettings(args) }
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

    private suspend fun setVoice(args: Bundle): SessionResult =
        PlaybackContract.voiceFrom(args)?.let { (voice, lang) -> setVoice(voice, lang) } ?: badRequest()

    private suspend fun setSpeechStyle(args: Bundle): SessionResult =
        PlaybackContract.speechStyleFrom(args)?.let { setSpeechStyle(it) } ?: badRequest()

    private suspend fun setOwnSettings(args: Bundle): SessionResult =
        if (args.containsKey(PlaybackContract.ARG_OWN_SETTINGS)) {
            setOwnSettings(args.getBoolean(PlaybackContract.ARG_OWN_SETTINGS))
        } else {
            badRequest()
        }

    /** A book with its own settings keeps the voice for itself (plan item 4); otherwise it is the global choice. */
    private suspend fun setVoice(voice: VoiceId, lang: Lang): SessionResult =
        when (val own = stores.book.updateIfOwn { it.copy(voices = it.voices + (lang to voice)) }) {
            is Outcome.Failure -> failure(PlaybackErrorCode.BAD_REQUEST)

            is Outcome.Success -> {
                if (!own.value) voices.select(lang, voice)
                bookSession().restartRendering()
                SessionResult(SessionResult.RESULT_SUCCESS)
            }
        }

    /**
     * Speed and pace go to the open book when it has its own settings (plan item 4), everything else (clause pauses,
     * reading modes, sound) to the global style. The sentence restarts only when what is heard changed.
     */
    private suspend fun setSpeechStyle(requested: SpeechStyle): SessionResult {
        val style = stores.style
        val book = stores.book
        val before = book.style(style.current)
        val rate = style.config.clampRate(requested.rate)
        val own = book.updateIfOwn { it.copy(rate = rate, pace = requested.pace) }
        if (own is Outcome.Failure) return failure(PlaybackErrorCode.BAD_REQUEST)
        val global = style.current
        val bookOwnsSpeed = own == Outcome.Success(true)
        style.set(if (bookOwnsSpeed) requested.copy(rate = global.rate, pace = global.pace) else requested)
        val after = book.style(style.current)
        if (after != before) bookSession().onSpeechStyleChanged(style.config.playerSpeed(after.rate))
        return SessionResult(SessionResult.RESULT_SUCCESS)
    }

    /** On: the open book keeps the current speed, pace and voices as its own; off: it follows the global ones. */
    private suspend fun setOwnSettings(on: Boolean): SessionResult {
        val style = stores.style
        val book = stores.book
        val before = book.style(style.current)
        val saved = book.setOwnSettings(on, before)
        if (saved !is Outcome.Success || !saved.value) return failure(PlaybackErrorCode.BAD_REQUEST)
        val after = book.style(style.current)
        // Turning it off may change the speed or the voices back; either way the sentences ahead are redone.
        if (after != before) {
            bookSession().onSpeechStyleChanged(style.config.playerSpeed(after.rate))
        } else {
            bookSession().restartRendering()
        }
        return SessionResult(SessionResult.RESULT_SUCCESS)
    }

    /** Runs [block] on the service's main-thread scope, for commands that read or write the database. */
    private fun later(block: suspend () -> SessionResult): ListenableFuture<SessionResult> {
        val result = SettableFuture.create<SessionResult>()
        scope.launch { result.set(block()) }
        return result
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

    private fun badRequest(): SessionResult = failure(PlaybackErrorCode.BAD_REQUEST)

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

/** The listener's saved settings the custom commands change: style, sleep timer extras, the open book's own. */
class SessionStores(val style: SpeechStyleStore, val sleepOptions: SleepOptionsStore, val book: BookContext)
