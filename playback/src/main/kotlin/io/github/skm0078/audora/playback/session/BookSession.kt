// File: playback/src/main/kotlin/io/github/skm0078/audora/playback/session/BookSession.kt
// Purpose: Plays one book on the service's player: feeds pages, drives the render queue, recovers from errors.

package io.github.skm0078.audora.playback.session

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import io.github.skm0078.audora.core.config.QueueConfig
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.pdf.document.PdfPageError
import io.github.skm0078.audora.playback.book.BookSource
import io.github.skm0078.audora.playback.book.OpenFailure
import io.github.skm0078.audora.playback.book.OpenedBook
import io.github.skm0078.audora.playback.book.PageCursor
import io.github.skm0078.audora.playback.contract.PlaybackErrorCode
import io.github.skm0078.audora.playback.contract.SleepTimerMode
import io.github.skm0078.audora.playback.playlist.PlaybackItem
import io.github.skm0078.audora.playback.playlist.PlaylistFeeder
import io.github.skm0078.audora.playback.queue.RenderAheadQueue
import io.github.skm0078.audora.playback.queue.SentenceRenderer
import io.github.skm0078.audora.playback.recovery.PlaybackRecovery
import io.github.skm0078.audora.playback.recovery.PlayerFault
import io.github.skm0078.audora.playback.recovery.RecoveryAction
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** What the session reads and how it speaks. */
class SessionSources(val renderer: SentenceRenderer, val books: BookSource, val texts: ItemTexts)

/**
 * Where the session runs. [scope] is main-thread; [extraction] is one thread (PDFium, P1-02);
 * [elapsedRealtime] keeps counting in deep sleep (sleep timer); [wallClock] is for what the UI shows.
 */
class SessionEnvironment(
    val scope: CoroutineScope,
    val extraction: CoroutineDispatcher,
    val elapsedRealtime: () -> Long,
    val wallClock: () -> Long,
)

/**
 * Owns the playback of one book at a time on the service's [player] (main thread only).
 *
 * Every (re)start, from a new position or with a new voice, opens a new render epoch: a fresh render queue,
 * a playlist of just the "more is coming" placeholder, and new media ids; pages are then appended ahead of
 * the playhead as they are extracted ([PageLoader], [PlaylistFeeder]). Player errors go through
 * [PlaybackRecovery] (S3 finding 3: Media3's 10-minute stuck timeout must lead to recovery, not a dead player).
 */
@OptIn(UnstableApi::class)
class BookSession(
    private val player: ExoPlayer,
    private val registry: QueueRegistry,
    private val sources: SessionSources,
    private val config: QueueConfig,
    private val events: SessionEvents,
    private val env: SessionEnvironment,
) {
    private val opening = Mutex()
    private val playlist = PlaylistView(player)
    private val reporter = ReadingReporter(events)
    private val loader = PageLoader(env.scope, env.extraction, LoaderEvents())
    private val faults = FaultHandler()
    private val sleep = SleepControl()
    private val playerListener = PlayerListener()

    private var book: OpenedBook? = null
    private var bookPassword: String? = null
    private var epoch = 0
    private var queue: RenderAheadQueue? = null
    private var feeder: PlaylistFeeder? = null
    private var cursor: PageCursor? = null
    private var lastSentence: ReadingUnit? = null
    private var error: SessionError? = null

    init {
        player.addListener(playerListener)
    }

    /** The book being read, for logs and the session extras. */
    val bookId: BookId? get() = book?.book?.id

    /**
     * Starts reading [id] at the first sentence of [page] that ends after [charStart]. Reuses the open book
     * when it is the same one (a seek). Plays at once: callers send this only from a user action (C7).
     */
    suspend fun playFrom(id: BookId, page: Int, charStart: Int, password: String?): Outcome<Unit, OpenFailure> =
        opening.withLock {
            fun fail(failure: OpenFailure): Outcome<Unit, OpenFailure> {
                error = SessionError(failure.code, failure.message)
                publishState()
                return Outcome.Failure(failure)
            }
            val opened = book?.takeIf { it.book.id == id && (password == null || password == bookPassword) }
                ?: when (val result = sources.books.open(id, password)) {
                    is Outcome.Success -> result.value.also { replaceBook(it, password) }
                    is Outcome.Failure -> return@withLock fail(result.error)
                }
            val pageCount = opened.text.pageCount
            if (page !in 0 until pageCount || charStart < 0) {
                return@withLock fail(OpenFailure(PlaybackErrorCode.BAD_REQUEST, "Page ${page + 1} is not in this book"))
            }
            error = null
            AudoraLog.i(TAG, "Play book=${id.logLabel} page=$page char=$charStart pages=$pageCount")
            rebuild(page, charStart, play = true)
            Outcome.Success(Unit)
        }

    /** The voice changed: the current sentence restarts in the new voice (Build Spec §6). */
    fun restartRendering() {
        val (page, char) = resumePoint() ?: return
        AudoraLog.i(TAG, "Restart rendering book=${bookId?.logLabel} page=$page char=$char")
        rebuild(page, char, play = player.playWhenReady)
    }

    fun setSleepTimer(mode: SleepTimerMode, minutes: Int) = sleep.set(mode, minutes)

    fun release() {
        player.removeListener(playerListener)
        loader.cancel()
        sleep.cancel()
        queue?.close()
        queue = null
        registry.clear()
        book?.text?.close()
        book = null
        reporter.notReading()
    }

    private suspend fun replaceBook(opened: OpenedBook, password: String?) {
        loader.cancel()
        val old = book
        book = opened
        bookPassword = password
        lastSentence = null
        // On the extraction thread, after any page read still running there.
        if (old != null) withContext(env.extraction) { old.text.close() }
    }

    private fun rebuild(page: Int, charStart: Int, play: Boolean) {
        val opened = book ?: return
        loader.cancel()
        epoch++
        val newQueue = RenderAheadQueue(sources.renderer, config, QueueLog(opened.book.id))
        newQueue.start("audora-render-$epoch")
        val oldQueue = queue
        queue = newQueue
        registry.publish(epoch, newQueue)
        val items = PlaylistItems(opened.book.id.value, epoch, opened.book.title, opened.text.pageCount, sources.texts)
        val newFeeder = PlaylistFeeder(PlayerPlaylist(player, items), config)
        feeder = newFeeder
        cursor = PageCursor(opened.text.pageCount, page, charStart)
        player.setMediaItems(newFeeder.initialItems().map(items::toMediaItem), 0, 0L)
        player.prepare()
        if (play) player.play()
        // After setMediaItems: the player has dropped the old items, so their cancelled loads never surface.
        oldQueue?.close()
        updateWindow()
        pump()
        publishState()
    }

    private fun pump() {
        val opened = book
        val currentFeeder = feeder
        val currentCursor = cursor
        if (opened != null && currentFeeder != null && currentCursor != null) {
            loader.pump(opened.text, currentCursor, currentFeeder, "book=${opened.book.id.logLabel}")
        }
    }

    /** Tells the render queue what to render next and arms the end-of-page sleep timer. */
    private fun updateWindow() {
        val currentFeeder = feeder ?: return
        queue?.setWindow(currentFeeder.window())
        player.pauseAtEndOfMediaItems = sleep.pauseAfter(playlist.current(), playlist.next())
    }

    /** Where a rebuild restarts: the current sentence, or just after the last one while waiting for a page. */
    private fun resumePoint(): Pair<Int, Int>? = when (val item = playlist.current()) {
        is PlaybackItem.Sentence -> item.unit.page to item.unit.charStart
        is PlaybackItem.More -> lastSentence?.let { it.page to it.charEnd } ?: cursor?.let { it.startPage to 0 }
        null -> null
    }

    private fun publishState() {
        events.onStateChanged(SessionState(bookId?.value, error, sleep.mode, sleep.endsAtWall))
    }

    /** Errors: classify, ask [PlaybackRecovery], act. */
    private inner class FaultHandler {
        private val recovery = PlaybackRecovery(config)

        fun onSentenceCompleted() = recovery.onSentenceCompleted()

        fun handle(playbackError: PlaybackException) {
            val fault = PlayerFaults.classify(playbackError, playlist.currentKey())
            val action = recovery.decide(fault)
            AudoraLog.w(
                TAG,
                "Player error ${playbackError.errorCodeName}, book=${bookId?.logLabel} " +
                    "item=${playlist.describeCurrent()} fault=$fault action=$action",
                playbackError,
            )
            when (action) {
                RecoveryAction.Retry -> player.prepare()

                RecoveryAction.RestartRendering -> resumePoint()?.let { (page, char) ->
                    rebuild(page, char, play = true)
                }

                RecoveryAction.SkipSentence -> skip(fault)

                is RecoveryAction.Stop -> stop(SessionError(action.code, action.message))
            }
        }

        /** Removes the failing sentence; playback goes on with the next one (or the current one, if it was ahead). */
        private fun skip(fault: PlayerFault) {
            val key = PlayerFaults.keyOf(fault)
            val index = key?.let(playlist::indexOf)
            if (key == null || index == null) {
                stop(SessionError(PlaybackErrorCode.BOOK_UNREADABLE, "The next page could not be prepared"))
                return
            }
            val reason = (fault as? PlayerFault.RenderFailed)?.error?.message ?: "the voice stopped responding"
            error =
                SessionError(PlaybackErrorCode.PLAYBACK_FAILED, "Skipped a sentence on page ${key.page + 1}: $reason")
            AudoraLog.w(TAG, "Skipping sentence $key, book=${bookId?.logLabel}")
            player.removeMediaItem(index)
            player.prepare()
            publishState()
        }

        fun stop(stopError: SessionError) {
            AudoraLog.e(TAG, "Reading stopped, book=${bookId?.logLabel}: ${stopError.code} ${stopError.message}")
            error = stopError
            player.pause()
            reporter.notReading()
            publishState()
        }
    }

    /** Sleep timer: minutes on a coroutine, end of page through the player's pause-at-end-of-item. */
    private inner class SleepControl {
        private val timer = SleepTimer(env.elapsedRealtime)
        private var job: Job? = null

        val mode: SleepTimerMode get() = timer.mode
        var endsAtWall: Long? = null
            private set

        fun set(mode: SleepTimerMode, minutes: Int) {
            cancel()
            timer.set(mode, minutes)
            endsAtWall = timer.remainingMs()?.let { env.wallClock() + it }
            if (mode == SleepTimerMode.MINUTES) job = env.scope.launch { runMinutes() }
            AudoraLog.i(TAG, "Sleep timer $mode minutes=$minutes book=${bookId?.logLabel}")
            updateWindow()
            publishState()
        }

        fun cancel() {
            job?.cancel()
            job = null
        }

        fun pauseAfter(current: PlaybackItem?, next: PlaybackItem?): Boolean = timer.pauseAfter(current, next)

        /** The player paused at the end of a sentence: if that was the end-of-page timer, it is done. */
        fun onPausedAtItemEnd() {
            if (timer.mode != SleepTimerMode.END_OF_PAGE) return
            AudoraLog.i(TAG, "Sleep timer: end of page, paused book=${bookId?.logLabel}")
            clearAndPublish()
            player.pauseAtEndOfMediaItems = false
        }

        private suspend fun runMinutes() {
            var remaining = timer.remainingMs() ?: return
            while (remaining > 0) {
                delay(remaining)
                remaining = timer.remainingMs() ?: return
            }
            AudoraLog.i(TAG, "Sleep timer ended, pausing book=${bookId?.logLabel}")
            player.pause()
            clearAndPublish()
        }

        private fun clearAndPublish() {
            timer.clear()
            endsAtWall = null
            publishState()
        }
    }

    private inner class LoaderEvents : PageLoader.Listener {
        override fun onPageAppended() = updateWindow()

        override fun onBookUnreadable(page: Int, error: PdfPageError) {
            this@BookSession.error =
                SessionError(PlaybackErrorCode.BOOK_UNREADABLE, "The PDF was moved or deleted while reading")
            publishState()
        }
    }

    private inner class PlayerListener : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) faults.onSentenceCompleted()
            (PlaylistItems.itemOf(mediaItem) as? PlaybackItem.Sentence)?.let { lastSentence = it.unit }
            feeder?.trimBehind()
            updateWindow()
            pump()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady &&
                reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM
            ) {
                sleep.onPausedAtItemEnd()
            }
        }

        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(
                    Player.EVENT_PLAYBACK_STATE_CHANGED,
                    Player.EVENT_PLAY_WHEN_READY_CHANGED,
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                )
            ) {
                val page =
                    (playlist.current() as? PlaybackItem.Sentence)?.unit?.page ?: lastSentence?.page
                        ?: cursor?.startPage
                reporter.update(bookId?.value, player, page)
            }
            if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) && player.playbackState == Player.STATE_ENDED) {
                AudoraLog.i(TAG, "Book finished, book=${bookId?.logLabel}")
            }
        }

        override fun onPlayerError(error: PlaybackException) = faults.handle(error)
    }

    private companion object {
        const val TAG = "Playback.Session"
    }
}
