// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/service/SessionCallback.kt
// Purpose: Accepts controllers and runs the contract's custom commands (play from, voice, sleep timer, reports).

package io.github.skm0078.audora.playback.service

import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.SleepOptions
import io.github.skm0078.audora.core.model.SpeechStyle
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.playback.async.launchedValue
import io.github.skm0078.audora.playback.browse.LibraryBrowser
import io.github.skm0078.audora.playback.contract.PlaybackContract
import io.github.skm0078.audora.playback.contract.PlaybackErrorCode
import io.github.skm0078.audora.playback.contract.SleepTimerMode
import io.github.skm0078.audora.playback.contract.StudyAction
import io.github.skm0078.audora.playback.notes.BookContext
import io.github.skm0078.audora.playback.session.BookSession
import io.github.skm0078.audora.playback.session.SleepOptionsStore
import io.github.skm0078.audora.playback.session.StudyMark
import io.github.skm0078.audora.playback.voice.SpeechStyleStore
import io.github.skm0078.audora.playback.voice.SpeechVoices
import kotlinx.coroutines.CoroutineScope

/**
 * Only controllers from Audora itself get the custom commands; the system UI, Bluetooth and other apps get
 * the standard transport controls (play, pause, skip), which is all a lock screen or headset needs. The lock screen's
 * slider is locked (item 79), and a headset's play/pause button gets [buttons]' presses (item 20). Media browsers
 * (Android Auto, item 34) browse and search the library through [library] and play what the driver picks.
 */
@OptIn(UnstableApi::class)
@Suppress(
    "TooManyFunctions", // One handler per custom command, plus the Media3 callbacks.
    "LongParameterList", // The service parts the commands reach; the buttons and library are optional for tests.
)
class SessionCallback(
    private val ownPackage: String,
    private val scope: CoroutineScope,
    private val voices: SpeechVoices,
    private val stores: SessionStores,
    private val bookSession: () -> BookSession,
    private val resumption: () -> PlaybackResumption?,
    private val buttons: MediaButtons? = null,
    private val library: LibraryBrowser? = null,
) : MediaLibrarySession.Callback {
    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): MediaSession.ConnectionResult {
        val trusted = controller.packageName == ownPackage
        val browses = mayBrowse(session, controller)
        AudoraLog.i(TAG, "Controller connected: ${controller.packageName} trusted=$trusted browses=$browses")
        val builder = MediaSession.ConnectionResult.AcceptedResultBuilder(session)
        if (!browses) {
            // Any other app: play and pause what is playing, but never list the library (titles are often personal
            // documents) or put a book in the player.
            builder.setAvailableSessionCommands(SessionCommands.EMPTY)
            builder.setAvailablePlayerCommands(OTHER_APPS)
        }
        if (session.isMediaNotificationController(controller)) builder.setAvailablePlayerCommands(LOCKED_SLIDER)
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
                    .add(PlaybackContract.REPLAY_PREVIOUS)
                    .add(PlaybackContract.STUDY)
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
        PlaybackContract.ACTION_REPLAY_PREVIOUS -> Futures.immediateFuture(replayPrevious(args))
        PlaybackContract.ACTION_STUDY -> study(args)
        else -> Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
    }

    override fun onMediaButtonEvent(
        session: MediaSession,
        controllerInfo: MediaSession.ControllerInfo,
        intent: Intent,
    ): Boolean = buttons?.handle(controllerInfo, intent) == true

    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<MediaItem>> {
        AudoraLog.i(TAG, "Library root asked by ${browser.packageName}")
        if (!mayBrowse(session, browser)) return denied()
        return library?.root(params) ?: super.onGetLibraryRoot(session, browser, params)
    }

    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = when {
        !mayBrowse(session, browser) -> denied()

        else -> library?.children(parentId, page, pageSize, params)
            ?: super.onGetChildren(session, browser, parentId, page, pageSize, params)
    }

    override fun onGetItem(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String,
    ): ListenableFuture<LibraryResult<MediaItem>> = when {
        !mayBrowse(session, browser) -> denied()
        else -> library?.item(mediaId) ?: super.onGetItem(session, browser, mediaId)
    }

    override fun onSearch(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<Void>> = when {
        !mayBrowse(session, browser) -> denied()
        else -> library?.search(session, browser, query, params) ?: super.onSearch(session, browser, query, params)
    }

    override fun onGetSearchResult(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = when {
        !mayBrowse(session, browser) -> denied()

        else -> library?.searchResult(query, page, pageSize, params)
            ?: super.onGetSearchResult(session, browser, query, page, pageSize, params)
    }

    /** A browsed row or a spoken search (item 34); anything else keeps Media3's handling. */
    override fun onSetMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        AudoraLog.i(TAG, "Play request from ${controller.packageName}, items=${mediaItems.size}")
        val browsed = if (mayBrowse(mediaSession, controller)) library?.play(mediaItems) else null
        return browsed ?: super.onSetMediaItems(mediaSession, controller, mediaItems, startIndex, startPositionMs)
    }

    override fun onPlaybackResumption(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        isForPlayback: Boolean,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        AudoraLog.i(TAG, "Playback resumption from ${controller.packageName} forPlayback=$isForPlayback")
        return resumption()?.resume(isForPlayback) ?: PlaybackResumption.unsupported()
    }

    /**
     * Who may list the library and play from it (plan item 34): this app, Android Auto and cars, and controllers the
     * system trusts (System UI's media controls, Assistant), never any other installed app.
     */
    private fun mayBrowse(session: MediaSession, controller: MediaSession.ControllerInfo): Boolean =
        controller.packageName == ownPackage || controller.isTrusted ||
            session.isAutoCompanionController(controller) || session.isAutomotiveController(controller)

    private fun <V : Any> denied(): ListenableFuture<LibraryResult<V>> =
        Futures.immediateFuture(LibraryResult.ofError<V>(SessionError.ERROR_PERMISSION_DENIED))

    private fun playFrom(args: Bundle): ListenableFuture<SessionResult> {
        val bookId = args.getString(PlaybackContract.ARG_BOOK_ID)?.takeIf { it.isNotBlank() }
        val page = args.getInt(PlaybackContract.ARG_PAGE, -1)
        val charStart = args.getInt(PlaybackContract.ARG_CHAR_START, 0)
        if (bookId == null || page < 0 || charStart < 0) {
            AudoraLog.w(TAG, "PLAY_FROM rejected: book set=${bookId != null} page=$page char=$charStart")
            return Futures.immediateFuture(failure(PlaybackErrorCode.BAD_REQUEST))
        }
        val password = args.getString(PlaybackContract.ARG_PASSWORD)
        return later {
            when (val outcome = bookSession().playFrom(BookId(bookId), page, charStart, password)) {
                is Outcome.Success -> SessionResult(SessionResult.RESULT_SUCCESS)
                is Outcome.Failure -> failure(outcome.error.code)
            }
        }
    }

    private suspend fun setVoice(args: Bundle): SessionResult =
        PlaybackContract.voiceFrom(args)?.let { (voice, lang) -> setVoice(voice, lang) } ?: badRequest()

    /** On top of what is heard now: the open book's own speed and pace when it has them, else the global style. */
    private suspend fun setSpeechStyle(args: Bundle): SessionResult = PlaybackContract.speechStyleChangeFrom(args)
        ?.let { change -> setSpeechStyle(change.over(stores.book.style(stores.style.current))) }
        ?: badRequest()

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
    private fun later(block: suspend () -> SessionResult): ListenableFuture<SessionResult> =
        scope.launchedValue(TAG, "Custom command", block)

    /** Logs a "This sounded wrong" report (RI-8b) with the voice reading its language; never the sentence's text. */
    private fun report(args: Bundle): SessionResult {
        val report = PlaybackContract.reportFrom(args) ?: return failure(PlaybackErrorCode.BAD_REQUEST)
        AudoraLog.i(TAG, report.logLine(voices.knownVoice(report.lang), stores.style.current.modes))
        return SessionResult(SessionResult.RESULT_SUCCESS)
    }

    private fun replayPrevious(args: Bundle): SessionResult {
        val factor = PlaybackContract.replayFactorFrom(args) ?: return failure(PlaybackErrorCode.BAD_REQUEST)
        val replayed = bookSession().replayPrevious(factor)
        return if (replayed) SessionResult(SessionResult.RESULT_SUCCESS) else failure(PlaybackErrorCode.BAD_REQUEST)
    }

    /** The A–B study range (plan item 31); a range of a book plays it, like PLAY_FROM. */
    private fun study(args: Bundle): ListenableFuture<SessionResult> {
        val loop = args.getBoolean(PlaybackContract.ARG_STUDY_LOOP, true)
        val session = bookSession()
        val done = when (PlaybackContract.studyActionFrom(args)) {
            StudyAction.MARK_START -> session.markStudy(StudyMark.START, loop)
            StudyAction.MARK_END -> session.markStudy(StudyMark.END, loop)
            StudyAction.SET_LOOP -> true.also { session.setStudyLoop(loop) }
            StudyAction.CLEAR -> true.also { session.clearStudy() }
            StudyAction.RANGE -> return playRange(args)
            null -> false
        }
        return Futures.immediateFuture(if (done) SessionResult(SessionResult.RESULT_SUCCESS) else badRequest())
    }

    private fun playRange(args: Bundle): ListenableFuture<SessionResult> {
        val bookId = args.getString(PlaybackContract.ARG_BOOK_ID)?.takeIf { it.isNotBlank() }
        val range = PlaybackContract.studyRangeFrom(args)
        if (bookId == null || range == null) return Futures.immediateFuture(badRequest())
        val password = args.getString(PlaybackContract.ARG_PASSWORD)
        return later {
            when (val outcome = bookSession().playRange(BookId(bookId), range, password)) {
                is Outcome.Success -> SessionResult(SessionResult.RESULT_SUCCESS)
                is Outcome.Failure -> failure(outcome.error.code)
            }
        }
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

        /**
         * The notification's and lock screen's commands (they drive the system's media controls): everything but
         * seeking inside the item, which is one sentence, so a stray touch on the slider cannot lose the place
         * (plan item 79). Skips, play and pause stay.
         */
        val LOCKED_SLIDER: Player.Commands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
            .remove(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
            .build()

        /**
         * Commands for any other app that connects (plan item 34): it may see and drive what is playing, but never
         * swap the book, so a stray app cannot load media into the reader's session.
         */
        val OTHER_APPS: Player.Commands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
            .remove(Player.COMMAND_SET_MEDIA_ITEM)
            .remove(Player.COMMAND_CHANGE_MEDIA_ITEMS)
            .build()

        /** A day; anything longer is a bug in the caller, not a bedtime. */
        const val MAX_SLEEP_MINUTES = 24 * 60
    }
}

/** The listener's saved settings the custom commands change: style, sleep timer extras, the open book's own. */
class SessionStores(val style: SpeechStyleStore, val sleepOptions: SleepOptionsStore, val book: BookContext)
