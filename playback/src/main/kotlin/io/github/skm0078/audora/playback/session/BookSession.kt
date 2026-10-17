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
import io.github.skm0078.audora.core.model.SleepOptions
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
import io.github.skm0078.audora.playback.queue.UnitKey
import io.github.skm0078.audora.playback.reading.ReadingSource
import io.github.skm0078.audora.playback.recovery.PlaybackRecovery
import io.github.skm0078.audora.playback.recovery.PlayerFault
import io.github.skm0078.audora.playback.recovery.RecoveryAction
import java.time.LocalTime
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** What the session reads, how it speaks, and how far ahead it renders (P4-02: from each voice's measured speed). */
@Suppress("LongParameterList") // All but the first three optional, with the defaults tests need.
class SessionSources(
    val renderer: SentenceRenderer,
    val books: BookSource,
    val texts: ItemTexts,
    val renderAhead: RenderAheadPolicy = RenderAheadPolicy.FIXED,
    val reading: ReadingSource = ReadingSource(),
    val sleepOptions: () -> SleepOptions = { SleepOptions() },
    val onBook: suspend (BookId) -> Unit = {},
    val keepGoingToNextChapter: () -> Boolean = { true },
    val onBookFinished: (BookId) -> Unit = {},
)

/**
 * Where the session runs. [scope] is main-thread; [extraction] is one thread (PDFium, P1-02);
 * [elapsedRealtime] keeps counting in deep sleep (sleep timer); [wallClock] is for what the UI shows;
 * [minuteOfDay] is the local time (0 = midnight) for bedtime auto-on; [shakes] feeds "shake to extend".
 */
class SessionEnvironment(
    val scope: CoroutineScope,
    val extraction: CoroutineDispatcher,
    val elapsedRealtime: () -> Long,
    val wallClock: () -> Long,
    val minuteOfDay: () -> Int = { LocalTime.now().let { it.hour * MINUTES_PER_HOUR + it.minute } },
    val shakes: ShakeSource = ShakeSource.NONE,
)

private const val MINUTES_PER_HOUR = 60

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
    private val loader = PageLoader(env.scope, env.extraction, LoaderEvents(), sources.reading.quotes::page)
    private val faults = FaultHandler()
    private val sleep = SleepControl()

    /** "Keep going to the next chapter" off (plan item 25): an end-of-chapter rule that never runs out. */
    private val chapterStop = SleepTimer(env.elapsedRealtime)
    private val rewind = ResumeRewind(config.resumeRewind, env.elapsedRealtime)
    private val playerListener = PlayerListener()
    private val jumps = JumpHistory(config.jumps)

    /** The item a seek the session makes itself goes to (resume rewind, replay): not the listener's jump. */
    private var ownSeekTarget: Int? = null

    /** "What did it say?": one sentence replayed slower, nothing saved. */
    private val replay = ReplaySpeed()

    /** Study modes (plan item 31): repeats, pause after each sentence, A–B loops. */
    private val study = StudyLoop()

    /** The sleep timer or "Keep going" wants this sentence's end-of-item pause: study modes leave it alone. */
    private var stopForSleep = false

    private var book: OpenedBook? = null

    // The book the player's playlist was built for; differs from [book] between a book switch and its rebuild.
    private var playlistBook: BookId? = null
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
            // Only a jump within the book: after a book switch the player still holds the old book's playlist.
            if (playlistBook == id) {
                currentSpot()?.let { from -> recordJump(from, Spot(page, charStart), JumpOrigin.START) }
            }
            rebuild(page, charStart, play = true)
            sleep.onStarted()
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

    /**
     * "Undo jump" (plan item 78): back to where reading was before the last jump (a burst of skips counts as one),
     * playing or paused as now. False when there is nothing to undo.
     */
    fun undoJump(): Boolean {
        val pageCount = book?.text?.pageCount ?: 0
        // Never outside the open book, whatever the history holds.
        val spot = jumps.undo()?.takeIf { it.page in 0 until pageCount } ?: return false
        AudoraLog.i(TAG, "Undo jump to page=${spot.page} char=${spot.charStart}, book=${bookId?.logLabel}")
        rebuild(spot.page, spot.charStart, play = player.playWhenReady)
        return true
    }

    /** The sentence playing, or the last one heard while between sentences; null with no book. */
    fun currentSentence(): ReadingUnit? = (playlist.current() as? PlaybackItem.Sentence)?.unit ?: lastSentence

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
        replay.forget()
        player.setPlaybackSpeed(playerSpeed)
        val (page, char) = resumePoint() ?: return publishState()
        AudoraLog.i(TAG, "Speech style changed, restart book=${bookId?.logLabel} page=$page char=$char")
        rebuild(page, char, play = player.playWhenReady)
    }

    /**
     * "What did it say?": plays the sentence before the current one again, [factor] times the listener's speed (a
     * little slower), then goes on at their speed from the next sentence. Only the player's speed changes, so nothing
     * is saved: leaving the reader, rotating or locking the phone mid-replay cannot keep the slower speed. The sentence
     * is one the reading modes play (the playlist holds no other), from the recent-audio cache when it is still there.
     * False when no sentence was played before this one.
     */
    fun replayPrevious(factor: Float): Boolean {
        val current = player.currentMediaItemIndex
        val target = (current - 1 downTo 0).firstOrNull { playlist.itemAt(it) is PlaybackItem.Sentence }
        val unit = target?.let { (playlist.itemAt(it) as? PlaybackItem.Sentence)?.unit } ?: return false
        player.setPlaybackSpeed(replay.start(UnitKey.of(unit), player.playbackParameters.speed, factor))
        AudoraLog.i(TAG, "Replay page=${unit.page} unit=${unit.index} factor=$factor, book=${bookId?.logLabel}")
        ownSeekTarget = target
        player.seekTo(target, 0L)
        player.play()
        return true
    }

    /**
     * Sets A or B of a study loop at the sentence playing (plan item 31); [loop] false reads the range once and stops.
     * False with no sentence playing.
     */
    fun markStudy(mark: StudyMark, loop: Boolean): Boolean = study.mark(mark, currentSentence(), loop).also { marked ->
        if (marked) onStudyChanged("Study mark $mark loop=$loop")
    }

    /** Loop on or off for the A–B range. */
    fun setStudyLoop(loop: Boolean) {
        study.setLoop(loop)
        onStudyChanged("Study loop=$loop")
    }

    fun clearStudy() {
        study.clear()
        onStudyChanged("Study range cleared")
    }

    /**
     * "Read a selection only": plays [range] of [id] from its start, once or looping. A user action (C7), like
     * [playFrom].
     */
    suspend fun playRange(id: BookId, range: StudyRange, password: String?): Outcome<Unit, OpenFailure> =
        playFrom(id, range.start.page, range.start.charStart, password).also { played ->
            if (played is Outcome.Success) {
                study.set(range)
                onStudyChanged("Study range set loop=${range.loop}")
            }
        }

    private fun onStudyChanged(what: String) {
        AudoraLog.i(TAG, "$what, book=${bookId?.logLabel}")
        updateWindow()
        publishState()
    }

    /**
     * False when [mode] cannot apply to the open book: end of chapter in a book without chapters. [chapters] is how
     * many chapter ends an end-of-chapter timer waits for.
     */
    fun setSleepTimer(mode: SleepTimerMode, minutes: Int, chapters: Int = 1): Boolean =
        sleep.set(mode, minutes, chapters)

    /** The sleep options changed: shake listening follows at once; bedtime and fade apply from the next timer. */
    fun onSleepOptionsChanged() {
        sleep.updateShakes()
        publishState()
    }

    fun release() {
        player.removeListener(playerListener)
        loader.cancel()
        sleep.cancel()
        env.shakes.stop()
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
        jumps.clear()
        // The new book's speed is set next (onBook); a replay of the old book must not restore over it.
        replay.forget()
        study.clear()
        // On the extraction thread, after any page read still running there.
        val chapters = withContext(env.extraction) {
            old?.text?.close()
            opened.text.chapterStarts()
        }
        sleep.onBookChanged(chapters)
        chapterStop.chapterStarts = chapters
        sources.reading.chapterPages = chapters.toSet()
        sources.reading.scanned = opened.text::isScanned
        sources.reading.quotes.clear()
        // The book's own speed, voices and pronunciation fixes, before its first sentence is rendered.
        sources.onBook(opened.book.id)
        AudoraLog.i(TAG, "Opened book=${opened.book.id.logLabel} chapters=${chapters.size}")
    }

    private fun rebuild(page: Int, charStart: Int, play: Boolean) {
        val opened = book ?: return
        playlistBook = opened.book.id
        replay.finish()?.let(player::setPlaybackSpeed)
        loader.cancel()
        // A new position: a pause before it says nothing about where the listener lost the thread.
        rewind.forget()
        epoch++
        val newQueue = RenderAheadQueue(sources.renderer, config, QueueLog(opened.book.id), sources.renderAhead)
        newQueue.start("audora-render-$epoch")
        val oldQueue = queue
        queue = newQueue
        registry.publish(epoch, newQueue)
        val items = PlaylistItems(opened.book.id.value, epoch, opened.book.title, opened.text.pageCount, sources.texts)
        val newFeeder = PlaylistFeeder(PlayerPlaylist(player, items), config, sources.reading.current())
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
        val current = playlist.current()
        val next = playlist.next()
        stopForSleep = sleep.pauseAfter(current, next) || stopsAtChapterEnd(current, next)
        player.pauseAtEndOfMediaItems = stopForSleep || study.pausesAfter(current, next, sources.reading.modes())
    }

    /** With "Keep going to the next chapter" off, reading pauses where a chapter ends (the book's end included). */
    private fun stopsAtChapterEnd(current: PlaybackItem?, next: PlaybackItem?): Boolean {
        if (sources.keepGoingToNextChapter() || chapterStop.chapterStarts.isEmpty()) return false
        if (chapterStop.mode != SleepTimerMode.END_OF_CHAPTER) chapterStop.set(SleepTimerMode.END_OF_CHAPTER, 0)
        return chapterStop.pauseAfter(current, next)
    }

    /** Where a rebuild restarts: the current sentence, or just after the last one while waiting for a page. */
    private fun resumePoint(): Pair<Int, Int>? = when (val item = playlist.current()) {
        is PlaybackItem.Sentence -> item.unit.page to item.unit.charStart
        is PlaybackItem.More -> lastSentence?.let { it.page to it.charEnd } ?: cursor?.let { it.startPage to 0 }
        null -> null
    }

    /** Resuming after a pause steps back by whole sentences (ADR-033); placeholders are never rewound. */
    private fun rewindOnResume() {
        val step = rewind.onResumed()
        val index = player.currentMediaItemIndex
        if (step == RewindStep.NONE || playlist.itemAt(index) !is PlaybackItem.Sentence) return
        val previous = index - 1
        val target =
            if (step == RewindStep.PREVIOUS_SENTENCE && playlist.itemAt(previous) is PlaybackItem.Sentence) {
                previous
            } else {
                index
            }
        AudoraLog.i(TAG, "Resume rewind $step to item $target, book=${bookId?.logLabel}")
        ownSeekTarget = target
        player.seekTo(target, 0L)
    }

    /** The sentence playing, or the one last played while waiting for a page. */
    private fun currentSpot(): Spot? =
        ((playlist.current() as? PlaybackItem.Sentence)?.unit ?: lastSentence)?.let { Spot(it.page, it.charStart) }

    /** A seek to another sentence (skip buttons, headset, notification, car) is a jump, unless it is the rewind. */
    private fun noteSeek(reason: Int, now: ReadingUnit?) {
        val rewound = ownSeekTarget != null && ownSeekTarget == player.currentMediaItemIndex
        ownSeekTarget = null
        if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_SEEK || rewound) return
        val from = lastSentence
        if (from != null && now != null) {
            recordJump(Spot(from.page, from.charStart), Spot(now.page, now.charStart), JumpOrigin.SKIP)
        }
    }

    private fun recordJump(from: Spot, to: Spot, origin: JumpOrigin) {
        val before = jumps.last
        jumps.record(from, to, origin, env.elapsedRealtime())
        if (jumps.last == before) return
        AudoraLog.i(
            TAG,
            "Jump origin=$origin from page=${from.page} char=${from.charStart} to page=${to.page} " +
                "char=${to.charStart}, book=${bookId?.logLabel}",
        )
        publishState()
    }

    /** The player paused at a sentence end for a study mode: true when playback went on (repeat, next, loop). */
    private fun onStudySentenceEnd(): Boolean {
        val unit = (playlist.current() as? PlaybackItem.Sentence)?.unit ?: return false
        val step = study.onSentenceEnd(unit, playlist.next(), sources.reading.modes())
        studyStep(step)
        return step != StudyStep.Stop
    }

    private fun studyStep(step: StudyStep) {
        when (step) {
            StudyStep.Repeat -> {
                player.seekTo(player.currentMediaItemIndex, 0L)
                player.play()
            }

            StudyStep.Continue -> player.play()

            StudyStep.Stop -> {
                if (player.playWhenReady) player.pause()
                AudoraLog.i(TAG, "Study stop, book=${bookId?.logLabel}")
                publishState()
            }

            is StudyStep.LoopTo -> loopTo(step.start)
        }
    }

    /** Back to the range's first sentence: a seek when it is still in the playlist, else a rebuild from it. */
    private fun loopTo(start: Spot) {
        val index = (0 until player.mediaItemCount).firstOrNull { i ->
            (playlist.itemAt(i) as? PlaybackItem.Sentence)?.unit?.let(study::startsRange) == true
        }
        AudoraLog.i(TAG, "Study loop to page=${start.page} char=${start.charStart} seek=${index != null}")
        if (index == null) {
            rebuild(start.page, start.charStart, play = true)
            return
        }
        ownSeekTarget = index
        player.seekTo(index, 0L)
        player.play()
    }

    private fun publishState() {
        events.onStateChanged(
            SessionState(
                bookId?.value,
                error,
                sleep.mode,
                sleep.endsAtWall,
                sleep.hasChapters,
                jumps.last?.from,
                sleep.chapters,
                study.marks(),
            ),
        )
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

    /**
     * Sleep timer: minutes on a coroutine, end of page or chapter through the player's pause-at-end-of-item. Extras
     * (plan item 37): the minutes fade out, a shake starts the timer again, and starting to read at bedtime sets one.
     */
    private inner class SleepControl {
        private val timer = SleepTimer(env.elapsedRealtime)
        private var job: Job? = null

        /** The minutes ran out: reading stops at the end of the sentence playing, not mid-word. */
        private var finishingSentence = false

        /** The last timer set, which a shake starts again. */
        private var lastSet: Triple<SleepTimerMode, Int, Int>? = null

        val mode: SleepTimerMode get() = timer.mode
        var endsAtWall: Long? = null
            private set

        /** Chapter ends an end-of-chapter timer waits for (1 for any other mode). */
        var chapters: Int = 1
            private set

        val hasChapters: Boolean get() = timer.chapterStarts.isNotEmpty()

        fun set(mode: SleepTimerMode, minutes: Int, chapters: Int = 1): Boolean {
            if (mode == SleepTimerMode.END_OF_CHAPTER && !hasChapters) {
                AudoraLog.w(TAG, "Sleep timer $mode refused: no chapters, book=${bookId?.logLabel}")
                return false
            }
            cancel()
            val page = (playlist.current() as? PlaybackItem.Sentence)?.unit?.page ?: lastSentence?.page
            timer.set(mode, minutes, chapters, page)
            this.chapters = if (mode == SleepTimerMode.END_OF_CHAPTER) chapters else 1
            lastSet = Triple(mode, minutes, chapters).takeIf { mode != SleepTimerMode.OFF }
            endsAtWall = timer.remainingMs()?.let { env.wallClock() + it }
            if (mode == SleepTimerMode.MINUTES) job = env.scope.launch { runMinutes() }
            AudoraLog.i(TAG, "Sleep timer $mode minutes=$minutes chapters=$chapters book=${bookId?.logLabel}")
            updateShakes()
            updateWindow()
            publishState()
            return true
        }

        /** Reading started: at bedtime with no timer set, the bedtime timer starts (plan item 37). */
        fun onStarted() {
            val options = sources.sleepOptions()
            if (!options.bedtime || timer.mode != SleepTimerMode.OFF || !options.inBedtime(env.minuteOfDay())) return
            AudoraLog.i(TAG, "Bedtime: sleep timer on for ${options.bedtimeMinutes} min, book=${bookId?.logLabel}")
            set(SleepTimerMode.MINUTES, options.bedtimeMinutes)
        }

        /** Listens for shakes while a timer runs with "shake to extend" on. */
        fun updateShakes() {
            if (timer.mode != SleepTimerMode.OFF && sources.sleepOptions().shakeToExtend) {
                env.shakes.start(::onShake)
            } else {
                env.shakes.stop()
            }
        }

        private fun onShake() {
            val (mode, minutes, chapterCount) = lastSet ?: return
            if (timer.mode == SleepTimerMode.OFF) return
            AudoraLog.i(TAG, "Shake: sleep timer $mode started again, book=${bookId?.logLabel}")
            set(mode, minutes, chapterCount)
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
            player.volume = FULL_VOLUME
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
            val fadeMs = if (sources.sleepOptions().fadeOut) config.sleep.fadeOutMs else 0L
            var remaining = timer.remainingMs() ?: return
            while (remaining > fadeMs) {
                delay(remaining - fadeMs)
                remaining = timer.remainingMs() ?: return
            }
            if (fadeMs > 0 && player.isPlaying) {
                fadeOut(fadeMs)
            } else if (player.isPlaying && playlist.current() is PlaybackItem.Sentence) {
                AudoraLog.i(TAG, "Sleep timer ended, finishing the sentence, book=${bookId?.logLabel}")
                finishingSentence = true
                updateWindow()
            } else {
                AudoraLog.i(TAG, "Sleep timer ended, pausing book=${bookId?.logLabel}")
                player.pause()
                clearAndPublish()
            }
        }

        /** Fades the voice to silence over the timer's last [fadeMs], then pauses and restores the volume. */
        private suspend fun fadeOut(fadeMs: Long) {
            AudoraLog.i(TAG, "Sleep timer fading out, book=${bookId?.logLabel}")
            try {
                var remaining = timer.remainingMs() ?: 0L
                while (remaining > 0) {
                    player.volume = (remaining.toFloat() / fadeMs).coerceIn(0f, FULL_VOLUME)
                    delay(config.sleep.fadeStepMs.coerceAtMost(remaining))
                    remaining = timer.remainingMs() ?: 0L
                }
                AudoraLog.i(TAG, "Sleep timer ended after the fade, pausing book=${bookId?.logLabel}")
                player.pause()
                clearAndPublish()
            } finally {
                player.volume = FULL_VOLUME
            }
        }

        private fun clearAndPublish() {
            timer.clear()
            endsAtWall = null
            chapters = 1
            updateShakes()
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
            val started = (PlaylistItems.itemOf(mediaItem) as? PlaybackItem.Sentence)?.unit
            val previous = lastSentence
            noteSeek(reason, started)
            study.onNewSentence()
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                study.onOvershoot(previous, started)?.let(::studyStep)
            }
            replay.onSentence(started?.let(UnitKey::of))?.let(player::setPlaybackSpeed)
            (PlaylistItems.itemOf(mediaItem) as? PlaybackItem.Sentence)?.let { sentence ->
                lastSentence = sentence.unit
                bookId?.let { events.onSentenceStarted(it, sentence.unit) }
            }
            feeder?.trimBehind()
            updateWindow()
            pump()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (playWhenReady) {
                rewindOnResume()
                sleep.onStarted()
                return
            }
            val atItemEnd = reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM
            // A study mode's stop at a sentence end goes on by itself unless it is a real stop.
            if (atItemEnd && !stopForSleep && onStudySentenceEnd()) return
            // Only the timer's own stop ends an end-of-page or end-of-chapter timer, not a study pause.
            sleep.onPaused(atItemEnd = atItemEnd && stopForSleep)
            rewind.onPaused(atSentenceEnd = atItemEnd)
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            // The listener chose a new place while paused (skip, seek bar): start there, not before it.
            if (reason == Player.DISCONTINUITY_REASON_SEEK && !player.playWhenReady) rewind.forget()
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
            // Only a book read to its end while playing: an ENDED reached while paused (a seek past the last item,
            // a replaced playlist) is not the listener finishing it, so Up next must not move on.
            val ended = player.playbackState == Player.STATE_ENDED && player.playWhenReady
            if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) && ended) {
                AudoraLog.i(TAG, "Book finished, book=${bookId?.logLabel}")
                bookId?.let(sources.onBookFinished)
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
        const val FULL_VOLUME = 1f
    }
}
