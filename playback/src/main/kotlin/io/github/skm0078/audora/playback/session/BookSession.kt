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
import io.github.skm0078.audora.core.resume.ResumeLocator
import io.github.skm0078.audora.pdf.document.PdfPageError
import io.github.skm0078.audora.playback.book.BookSource
import io.github.skm0078.audora.playback.book.OpenFailure
import io.github.skm0078.audora.playback.book.OpenedBook
import io.github.skm0078.audora.playback.book.PageCursor
import io.github.skm0078.audora.playback.contract.PlaybackErrorCode
import io.github.skm0078.audora.playback.contract.SleepTimerMode
import io.github.skm0078.audora.playback.playlist.PlaybackItem
import io.github.skm0078.audora.playback.playlist.PlaylistFeeder
import io.github.skm0078.audora.playback.queue.RenderAheadPolicy
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

/** What the session reads, how it speaks, and how far ahead it renders (P4-02: from each voice's measured speed). */
class SessionSources(
    val renderer: SentenceRenderer,
    val books: BookSource,
    val texts: ItemTexts,
    val renderAhead: RenderAheadPolicy = RenderAheadPolicy.FIXED,
)

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
// The session's entry points (play from, resume, voice, sleep timer, release) plus the playlist steps they share.
@Suppress("TooManyFunctions")
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
     * when it is the same one (a seek), unless the reading text settings changed since it was opened. Plays at
     * once: callers send this only from a user action (C7).
     */
    suspend fun playFrom(id: BookId, page: Int, charStart: Int, password: String?): Outcome<Unit, OpenFailure> =
        opening.withLock {
            fun fail(failure: OpenFailure): Outcome<Unit, OpenFailure> {
                error = SessionError(failure.code, failure.message)
                publishState()
                return Outcome.Failure(failure)
            }
            val same = book?.takeIf { it.book.id == id && (password == null || password == bookPassword) }
            // A same book opened again (settings changed) keeps the password it was opened with.
            val openPassword = password ?: same?.let { bookPassword }
            val opened = same?.takeIf { current(it) }
                ?: when (val result = sources.books.open(id, openPassword)) {
                    is Outcome.Success -> result.value.also { replaceBook(it, openPassword) }
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

    /**
     * Playback resumption (P1-06): reopens [id] without a password and plays from its saved anchor, re-anchored
     * on the current pipeline ([ResumeLocator]); from page 1 if it has none. An encrypted book fails with
     * PASSWORD_REQUIRED, because its password is never stored.
     */
    suspend fun resume(id: BookId, locator: ResumeLocator): Outcome<Unit, OpenFailure> {
        // The open book if it is [id]; else open [id] without a password and make it the session's book.
        val opened = opening.withLock {
            val same = book?.takeIf { it.book.id == id }
            val password = same?.let { bookPassword }
            same?.takeIf { current(it) }?.let { Outcome.Success(it) }
                ?: sources.books.open(id, password).also { if (it is Outcome.Success) replaceBook(it.value, password) }
        }
        val text = when (opened) {
            is Outcome.Success -> opened.value.text
            is Outcome.Failure -> return Outcome.Failure(opened.error)
        }
        val point = withContext(env.extraction) {
            locator.locate(id, text.pageCount) { page -> (text.units(page) as? Outcome.Success)?.value }
        }
        return playFrom(id, point?.page ?: 0, point?.charStart ?: 0, password = bookPassword)
    }

    /** The voice changed: the current sentence restarts in the new voice (Build Spec §6). */
    fun restartRendering() {
        val (page, char) = resumePoint() ?: return
        AudoraLog.i(TAG, "Restart rendering book=${bookId?.logLabel} page=$page char=$char")
        rebuild(page, char, play = player.playWhenReady)
    }

    /**
     * The speaking rate or pace changed (ADR-033): the player takes its share of the speed ([playerSpeed], above the
     * voice model's cap) and, like a voice change, the current sentence restarts, since the sentences rendered ahead
     * were spoken at the old rate. With nothing loaded, only the session extras change.
     */
    fun onSpeechStyleChanged(playerSpeed: Float) {
        player.setPlaybackSpeed(playerSpeed)
        val (page, char) = resumePoint() ?: return publishState()
        AudoraLog.i(TAG, "Speech style changed, restart book=${bookId?.logLabel} page=$page char=$char")
        rebuild(page, char, play = player.playWhenReady)
    }

    /** False when [mode] cannot apply to the open book: end of chapter in a book without chapters. */
    fun setSleepTimer(mode: SleepTimerMode, minutes: Int): Boolean = sleep.set(mode, minutes)

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

    /** False (logged) when [opened] must be opened again: the reading text settings changed since (P2-03). */
    private suspend fun current(opened: OpenedBook): Boolean = sources.books.isCurrent(opened).also { fresh ->
        if (!fresh) AudoraLog.i(TAG, "Reading text settings changed, reopening book=${opened.book.id.logLabel}")
    }

    private suspend fun replaceBook(opened: OpenedBook, password: String?) {
        loader.cancel()
        val old = book
        book = opened
        bookPassword = password
        lastSentence = null
        // On the extraction thread, after any page read still running there.
        val chapters = withContext(env.extraction) {
            old?.text?.close()
            opened.text.chapterStarts()
        }
        sleep.onBookChanged(chapters)
        AudoraLog.i(TAG, "Opened book=${opened.book.id.logLabel} chapters=${chapters.size}")
    }

    private fun rebuild(page: Int, charStart: Int, play: Boolean) {
        val opened = book ?: return
        loader.cancel()
        epoch++
        val newQueue = RenderAheadQueue(sources.renderer, config, QueueLog(opened.book.id), sources.renderAhead)
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
        val candidates = currentFeeder.window(1 + config.renderAhead.maxAheadUnits)
        queue?.setWindow(sources.renderAhead.window(candidates, config))
        player.pauseAtEndOfMediaItems = sleep.pauseAfter(playlist.current(), playlist.next())
    }

    /** Where a rebuild restarts: the current sentence, or just after the last one while waiting for a page. */
    private fun resumePoint(): Pair<Int, Int>? = when (val item = playlist.current()) {
        is PlaybackItem.Sentence -> item.unit.page to item.unit.charStart
        is PlaybackItem.More -> lastSentence?.let { it.page to it.charEnd } ?: cursor?.let { it.startPage to 0 }
        null -> null
    }

    private fun publishState() {
        events.onStateChanged(SessionState(bookId?.value, error, sleep.mode, sleep.endsAtWall, sleep.hasChapters))
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

    /** Sleep timer: minutes on a coroutine, end of page or chapter through the player's pause-at-end-of-item. */
    private inner class SleepControl {
        private val timer = SleepTimer(env.elapsedRealtime)
        private var job: Job? = null

        /** The minutes ran out: reading stops at the end of the sentence playing, not mid-word. */
        private var finishingSentence = false

        val mode: SleepTimerMode get() = timer.mode
        var endsAtWall: Long? = null
            private set

        val hasChapters: Boolean get() = timer.chapterStarts.isNotEmpty()

        fun set(mode: SleepTimerMode, minutes: Int): Boolean {
            if (mode == SleepTimerMode.END_OF_CHAPTER && !hasChapters) {
                AudoraLog.w(TAG, "Sleep timer $mode refused: no chapters, book=${bookId?.logLabel}")
                return false
            }
            cancel()
            timer.set(mode, minutes)
            endsAtWall = timer.remainingMs()?.let { env.wallClock() + it }
            if (mode == SleepTimerMode.MINUTES) job = env.scope.launch { runMinutes() }
            AudoraLog.i(TAG, "Sleep timer $mode minutes=$minutes book=${bookId?.logLabel}")
            updateWindow()
            publishState()
            return true
        }

        /** A new book: its chapters replace the old ones, and an end-of-chapter timer without any is dropped. */
        fun onBookChanged(chapters: List<Int>) {
            timer.chapterStarts = chapters
            if (timer.mode == SleepTimerMode.END_OF_CHAPTER && chapters.isEmpty()) {
                AudoraLog.i(TAG, "Sleep timer cleared: the new book has no chapters, book=${bookId?.logLabel}")
                clearAndPublish()
            }
        }

        fun cancel() {
            job?.cancel()
            job = null
            finishingSentence = false
        }

        fun pauseAfter(current: PlaybackItem?, next: PlaybackItem?): Boolean =
            (finishingSentence && current is PlaybackItem.Sentence) || timer.pauseAfter(current, next)

        /**
         * The player paused. At the end of a sentence, an end-of-page, end-of-chapter or ran-out timer is done;
         * any other pause while the ran-out timer finishes its sentence ends that timer too, so resuming reads
         * on normally.
         */
        fun onPaused(atItemEnd: Boolean) {
            val done = finishingSentence || (atItemEnd && timer.mode in ITEM_END_MODES)
            if (!done) return
            AudoraLog.i(TAG, "Sleep timer done, paused book=${bookId?.logLabel}")
            finishingSentence = false
            clearAndPublish()
            player.pauseAtEndOfMediaItems = false
        }

        private suspend fun runMinutes() {
            var remaining = timer.remainingMs() ?: return
            while (remaining > 0) {
                delay(remaining)
                remaining = timer.remainingMs() ?: return
            }
            if (player.isPlaying && playlist.current() is PlaybackItem.Sentence) {
                AudoraLog.i(TAG, "Sleep timer ended, finishing the sentence, book=${bookId?.logLabel}")
                finishingSentence = true
                updateWindow()
            } else {
                AudoraLog.i(TAG, "Sleep timer ended, pausing book=${bookId?.logLabel}")
                player.pause()
                clearAndPublish()
            }
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
            (PlaylistItems.itemOf(mediaItem) as? PlaybackItem.Sentence)?.let { sentence ->
                lastSentence = sentence.unit
                bookId?.let { events.onSentenceStarted(it, sentence.unit) }
            }
            feeder?.trimBehind()
            updateWindow()
            pump()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady) {
                sleep.onPaused(
                    atItemEnd =
                        reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM,
                )
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
            // A faster speed drains the queue sooner (ADR-027): resize the window now, not at the next sentence.
            if (events.contains(Player.EVENT_PLAYBACK_PARAMETERS_CHANGED)) {
                updateWindow()
                pump()
            }
        }

        override fun onPlayerError(error: PlaybackException) = faults.handle(error)
    }

    private companion object {
        const val TAG = "Playback.Session"

        /** Timer modes that end by pausing at the end of a sentence (pause-at-end-of-item). */
        val ITEM_END_MODES = setOf(SleepTimerMode.END_OF_PAGE, SleepTimerMode.END_OF_CHAPTER)
    }
}
