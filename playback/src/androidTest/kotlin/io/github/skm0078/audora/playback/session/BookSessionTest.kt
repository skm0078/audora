// File: playback/src/androidTest/kotlin/io/github/skm0078/audora/playback/session/BookSessionTest.kt
// Purpose: The book session on a real ExoPlayer: reads in order, seeks, rebuilds, skips failures, sleeps at chapters,
// studies (ranges at page, chapter and book ends; loops never revive a timer), replays and finishes books, and
// starts, skips and undoes onto the unit asked for when two share an offset.

package io.github.skm0078.audora.playback.session

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.NoSampleRenderer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.RenderersFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.skm0078.audora.core.config.QueueConfig
import io.github.skm0078.audora.core.config.ReadingUnitConfig
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.logging.LogSink
import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.model.SleepOptions
import io.github.skm0078.audora.core.model.SpeechStyle
import io.github.skm0078.audora.core.model.TableRowInfo
import io.github.skm0078.audora.core.model.UnitKind
import io.github.skm0078.audora.core.model.VoiceId
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.pdf.document.PdfPageError
import io.github.skm0078.audora.playback.book.BookSource
import io.github.skm0078.audora.playback.book.BookText
import io.github.skm0078.audora.playback.book.OpenedBook
import io.github.skm0078.audora.playback.contract.PlaybackErrorCode
import io.github.skm0078.audora.playback.contract.SleepTimerMode
import io.github.skm0078.audora.playback.playlist.PlaybackItem
import io.github.skm0078.audora.playback.queue.SentenceRenderer
import io.github.skm0078.audora.playback.queue.UnitKey
import io.github.skm0078.audora.playback.voice.ReplayRender
import io.github.skm0078.audora.speech.SpeechError
import io.github.skm0078.audora.speech.audio.PcmAudio
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class BookSessionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val registry = QueueRegistry()
    private val played: MutableList<UnitKey> = Collections.synchronizedList(mutableListOf())

    // [Recorder] pauses the player when this many sentences have started; null = never.
    @Volatile private var pauseWhenPlayed: Int? = null

    // The player's speed as each sentence started (same index as [played]).
    private val speeds: MutableList<Float> = Collections.synchronizedList(mutableListOf())
    private val finished: MutableList<BookId> = Collections.synchronizedList(mutableListOf())
    private val ended = CountDownLatch(1)
    private val endings = AtomicInteger()
    private val pausedAtItemEnd = CountDownLatch(1)
    private val states: MutableList<SessionState> = Collections.synchronizedList(mutableListOf())
    private var failOn: UnitKey? = null
    private var sleepOptions = SleepOptions()
    private var keepGoing = true

    // Smart speed's replay (ADR-049): when on, "What did it say?" renders the sentence again in [SLOWER].
    private val replayRender = ReplayRender()
    private var smartSpeed = false

    // Each render: the sentence and the replay style it was rendered with (null: the listener's own).
    private val renders: MutableList<Pair<UnitKey, SpeechStyle?>> = Collections.synchronizedList(mutableListOf())
    private lateinit var player: ExoPlayer
    private lateinit var session: BookSession

    @Before
    fun setUp() {
        // The session's own log lines, in logcat under one tag (CI prints it), to see why a run stalled.
        AudoraLog.install(LogSink { Log.i(LOG_TAG, "${it.tag}: ${it.message}", it.throwable) })
        onMain {
            // A renderer that plays nothing: the player's position follows the system clock, because the CI
            // emulator runs without sound and its audio output never advances. Loading, playlist moves and
            // ENDED are all real.
            player = SpeechPlayer.build(
                instrumentation.targetContext,
                registry,
                CONFIG,
                handleAudioFocus = false,
                renderers = RenderersFactory { _, _, _, _, _ -> arrayOf<Renderer>(SilentRenderer()) },
            )
            player.addListener(Recorder())
            session = BookSession(
                player,
                registry,
                SessionSources(
                    renderer(),
                    BookSource { id, _ ->
                        val book = listOf(BOOK, SHORT_BOOK, TABLE_BOOK).first { it.id == id }
                        Outcome.Success(
                            OpenedBook(
                                book,
                                FakeText(
                                    CHAPTER_STARTS,
                                    checkNotNull(book.pageCount),
                                    book == TABLE_BOOK,
                                ),
                            ),
                        )
                    },
                    Texts,
                    sleepOptions = { sleepOptions },
                    keepGoingToNextChapter = { keepGoing },
                    onBookFinished = { finished += it },
                    replayRender = replayRender,
                    replayStyle = { if (smartSpeed) SLOWER else null },
                ),
                CONFIG,
                Events(),
                SessionEnvironment(
                    scope,
                    Dispatchers.IO.limitedParallelism(1),
                    System::nanoTime,
                    System::currentTimeMillis,
                    minuteOfDay = { TEST_MINUTE_OF_DAY },
                ),
            )
        }
    }

    @After
    fun tearDown() {
        onMain {
            session.release()
            player.release()
        }
        scope.cancel()
        AudoraLog.install()
    }

    @Test
    fun readsEverySentenceInOrderAndEndsOnlyAfterTheLast() {
        play(page = 0, charStart = 0)

        awaitEnd()
        assertEquals(allKeys(), played.toList())
    }

    @Test
    fun startsAtTheSentenceContainingTheCharOffset() {
        // Unit 1 of page 1 spans chars 10..19, so offset 15 starts there.
        play(page = 1, charStart = 15)

        awaitEnd()
        assertEquals(UnitKey(1, 1), played.first())
        assertEquals(allKeys().dropWhile { it != UnitKey(1, 1) }, played.toList())
    }

    @Test
    fun restartRenderingOpensANewEpochAndStillFinishes() {
        play(page = 0, charStart = 0)
        onMain { session.restartRendering() }

        assertTrue(onMain { player.currentMediaItem?.mediaId.orEmpty() }.contains("/e/2/"))
        awaitEnd()
        assertEquals(UnitKey(PAGES - 1, UNITS_PER_PAGE - 1), played.last())
    }

    @Test
    fun aSentenceThatFailsToRenderIsSkipped() {
        failOn = UnitKey(0, 1)
        play(page = 0, charStart = 0)

        awaitEnd()
        // The player may move onto the failing sentence before its load error surfaces; it is then removed and
        // never heard. What matters: every other sentence plays, in order, and the book still ends.
        assertEquals(allKeys() - UnitKey(0, 1), played.filter { it != UnitKey(0, 1) })
        assertTrue(states.any { it.error?.code == PlaybackErrorCode.PLAYBACK_FAILED })
    }

    @Test
    fun endOfChapterTimerPausesAfterTheChapterLastSentence() {
        play(page = 0, charStart = 0)
        assertTrue(onMain { session.setSleepTimer(SleepTimerMode.END_OF_CHAPTER, 0) })

        if (!pausedAtItemEnd.await(TIMEOUT_S, TimeUnit.SECONDS)) fail("did not pause: ${describePlayer()}")
        // Chapter 2 starts on page 2, so chapter 1 is pages 0 and 1. The player pauses at the end of the
        // chapter's last sentence, before moving on.
        val chapterOne = allKeys().takeWhile { it.page < CHAPTER_STARTS.last() }
        assertEquals(chapterOne, played.toList())
        assertFalse(onMain { player.playWhenReady })
        assertEquals(SleepTimerMode.OFF, onMain { states.last().sleepMode })
        assertTrue(states.last().hasChapters)
    }

    @Test
    fun aRangeReadOncePausesAfterItsLastSentence() {
        // Chars 10..15 of the range's ends: from unit 1 of page 0 through unit 1 of page 1 (chars 10..19).
        playRange(StudyRange(Spot(0, CHARS), Spot(1, CHARS + CHARS / 2), loop = false))

        awaitCondition("range paused") { played.contains(UnitKey(1, 1)) && !player.playWhenReady }
        val range = listOf(UnitKey(0, 1), UnitKey(0, 2), UnitKey(1, 0), UnitKey(1, 1))
        assertEquals(range, played.take(range.size))
        assertTrue("read past the range: ${played.toList()}", played.none { it.page == PAGES - 1 })
        assertEquals(null, onMain { states.last().study })
    }

    @Test
    fun aLoopingRangeGoesBackToItsStart() {
        playRange(StudyRange(Spot(0, 0), Spot(0, CHARS + CHARS / 2), loop = true))

        awaitCondition("looped twice") { played.size >= LOOP_ROUNDS * 2 }
        onMain { player.pause() }
        val round = listOf(UnitKey(0, 0), UnitKey(0, 1))
        assertEquals((1..LOOP_ROUNDS).flatMap { round }, played.take(LOOP_ROUNDS * 2))
    }

    @Test
    fun aRangeEndingOnAPageEndPausesThereWhileTheNextPageLoads() {
        // The range ends on page 1's last sentence; the next page takes longer to load than a sentence plays, so the
        // item after it is "more is coming", not a sentence.
        playRange(StudyRange(Spot(0, CHARS), Spot(0, 2 * CHARS + CHARS / 2), loop = false))

        awaitCondition("range paused") { played.contains(UnitKey(0, 2)) && !player.playWhenReady }
        Thread.sleep(SETTLE_MS)
        assertEquals(listOf(UnitKey(0, 1), UnitKey(0, 2)), played.toList())
    }

    @Test
    fun aLoopingRangeEndingOnAPageEndLoops() {
        playRange(StudyRange(Spot(0, CHARS), Spot(0, 2 * CHARS + CHARS / 2), loop = true))

        awaitCondition("looped twice") { played.size >= LOOP_ROUNDS * 2 }
        onMain { player.pause() }
        val round = listOf(UnitKey(0, 1), UnitKey(0, 2))
        assertEquals((1..LOOP_ROUNDS).flatMap { round }, played.take(LOOP_ROUNDS * 2))
    }

    @Test
    fun aRangeEndingOnTheBookLastSentenceDoesNotFinishTheBook() {
        val last = PAGES - 1
        playRange(StudyRange(Spot(last, 2 * CHARS), Spot(last, 2 * CHARS + CHARS / 2), loop = false))

        awaitCondition("range paused") { played.contains(UnitKey(last, 2)) && !player.playWhenReady }
        Thread.sleep(SETTLE_MS)
        assertEquals(emptyList<BookId>(), onMain { finished.toList() })
    }

    @Test
    fun aLoopAtAChapterEndLoopsWithKeepGoingOff() {
        keepGoing = false
        // Chapter 2 starts on page 3, so page 2's last sentence ends chapter 1.
        playRange(StudyRange(Spot(1, CHARS), Spot(1, 2 * CHARS + CHARS / 2), loop = true))

        awaitCondition("looped twice") { played.size >= LOOP_ROUNDS * 2 }
        onMain { player.pause() }
        val round = listOf(UnitKey(1, 1), UnitKey(1, 2))
        assertEquals((1..LOOP_ROUNDS).flatMap { round }, played.take(LOOP_ROUNDS * 2))
    }

    @Test
    fun studyLoopsNeverTurnATimerTheListenerTurnedOffBackOn() {
        // Bedtime all day long: the start turns the bedtime timer on, as it should.
        sleepOptions = SleepOptions(bedtime = true, bedtimeFromMinute = 0, bedtimeToMinute = LAST_MINUTE_OF_DAY)
        playRange(StudyRange(Spot(0, 0), Spot(0, CHARS + CHARS / 2), loop = true))
        awaitCondition("bedtime timer on") { states.lastOrNull()?.sleepMode == SleepTimerMode.MINUTES }

        assertTrue(onMain { session.setSleepTimer(SleepTimerMode.OFF, 0) })
        val before = onMain { played.size }
        // Each loop pauses at the range's end and plays again: none of those resumes is the listener starting.
        awaitCondition("looped twice more") { played.size >= before + LOOP_ROUNDS * 2 }
        onMain { player.pause() }
        assertEquals(SleepTimerMode.OFF, onMain { states.last().sleepMode })
    }

    @Test
    fun replayPlaysThePreviousSentenceSlowerThenGoesOnAtTheListenersSpeed() {
        play(page = 0, charStart = 0)
        awaitCondition("reached page 2") { played.contains(UnitKey(1, 0)) }

        val before = onMain {
            assertTrue(session.replayPrevious(REPLAY_FACTOR))
            played.size
        }

        awaitEnd()
        assertEquals(played[before - 2], played[before])
        assertEquals(REPLAY_FACTOR, speeds[before], SPEED_DELTA)
        assertEquals(1f, onMain { player.playbackParameters.speed }, SPEED_DELTA)
        assertEquals(UnitKey(PAGES - 1, UNITS_PER_PAGE - 1), played.last())
    }

    @Test
    fun aSmartSpeedReplayRendersTheSentenceSlowerOnceThenInTheListenersStyle() {
        smartSpeed = true
        play(page = 0, charStart = 0)
        awaitCondition("reached page 2") { played.contains(UnitKey(1, 0)) }

        val before = onMain {
            assertTrue(session.replayPrevious(REPLAY_FACTOR))
            // The replay is played[before]; the player pauses as the sentence after it starts (see [Recorder]).
            pauseWhenPlayed = played.size + 2
            played.size
        }
        val replayed = played[before - 2]
        awaitCondition("the sentence after the replay started and paused") {
            played.size >= before + 2 && !player.playWhenReady
        }
        assertEquals(replayed, played[before])
        assertTrue(renders.contains(replayed to SLOWER))

        // Back to the replayed sentence: its slower audio was dropped, so it renders again in the listener's style.
        val ownRenders = renders.count { it == replayed to null }
        val index = onMain { itemIndexOf(replayed) }
        assertTrue("$replayed is not in the playlist: ${describePlayer()}", index >= 0)
        onMain { player.seekTo(index, 0) }
        awaitCondition("the replayed sentence rendered again in the listener's style") {
            renders.count { it == replayed to null } > ownRenders
        }
        assertEquals(1, renders.count { it == replayed to SLOWER })
    }

    @Test
    fun undoJumpNeverCrossesIntoAnotherBook() {
        play(page = 0, charStart = 0)
        awaitCondition("first sentence") { played.isNotEmpty() }
        play(page = PAGES - 1, charStart = 0)
        // Within the book the jump is undone.
        assertTrue(onMain { session.undoJump() })
        awaitCondition("back on page 2") { session.currentSentence()?.page == 1 }

        // Page 2 is also in the short book: only the book-switch guard keeps the old book's spot out of its history.
        play(page = 0, charStart = 0, book = SHORT_BOOK)
        assertFalse(onMain { session.undoJump() })
    }

    @Test
    fun onlyABookReadToItsEndWhilePlayingCountsAsFinished() {
        play(page = 0, charStart = 0)
        awaitCondition("second sentence") { played.size >= 2 }
        // Paused, then the playlist emptied: ENDED, but the listener did not finish the book.
        onMain {
            player.pause()
            player.clearMediaItems()
        }
        awaitCondition("ended while paused") { endings.get() >= 1 }
        assertFalse(onMain { player.playWhenReady })
        assertEquals(emptyList<BookId>(), onMain { finished.toList() })

        // Read to the end while playing: finished once. On the main thread, since the session hears ENDED in the
        // same dispatch as the counter, after it.
        play(page = 0, charStart = 0)
        awaitCondition("ended while playing") { endings.get() >= 2 }
        assertEquals(listOf(BOOK.id), onMain { finished.toList() })
    }

    @Test
    fun aStartWithAUnitIndexStartsAtThatUnitNotTheOneSharingItsOffset() {
        playPaused(page = 1, charStart = 0, book = TABLE_BOOK)
        assertEquals(UnitKey(1, 0), awaitSentenceOn(page = 1))

        playPaused(page = 1, charStart = 0, book = TABLE_BOOK, unitIndex = 1)
        assertEquals(UnitKey(1, 1), awaitSentenceOn(page = 1))
    }

    @Test
    fun aParagraphSkipLandsOnTheUnitItNamesWhenTwoShareAnOffset() {
        // Unit 0 (the zero-length summary) ends a paragraph, so "previous paragraph" from unit 2 is unit 1, which
        // starts where the summary does. Unit 1 is not in the playlist, so reading rebuilds there.
        playPaused(page = 1, charStart = 2 * CHARS, book = TABLE_BOOK)
        assertEquals(UnitKey(1, 2), awaitSentenceOn(page = 1))

        val skipped = runBlocking { withContext(Dispatchers.Main) { session.skipParagraph(forward = false) } }
        assertEquals(ParagraphSkipResult.MOVED, skipped)
        assertEquals(UnitKey(1, 1), awaitSentenceOn(page = 1))
    }

    @Test
    fun aParagraphSkipWithNowhereToGoSaysSoAndStaysPut() {
        playPaused(page = 0, charStart = 0, book = BOOK)
        assertEquals(UnitKey(0, 0), awaitSentenceOn(page = 0))

        val skipped = runBlocking { withContext(Dispatchers.Main) { session.skipParagraph(forward = false) } }
        assertEquals(ParagraphSkipResult.NONE_THAT_WAY, skipped)
        assertEquals(UnitKey(0, 0), onMain { UnitKey.of(checkNotNull(session.currentSentence())) })
    }

    @Test
    fun undoJumpGoesBackToTheUnitItLeftNotTheOneSharingItsOffset() {
        playPaused(page = 1, charStart = 0, book = TABLE_BOOK, unitIndex = 1)
        assertEquals(UnitKey(1, 1), awaitSentenceOn(page = 1))
        playPaused(page = PAGES - 1, charStart = 0, book = TABLE_BOOK)
        awaitSentenceOn(page = PAGES - 1)

        assertTrue(onMain { session.undoJump() })
        assertEquals(UnitKey(1, 1), awaitSentenceOn(page = 1))
    }

    /** Starts reading and pauses at once, so the first sentence stays current while it is checked. */
    private fun playPaused(page: Int, charStart: Int, book: Book, unitIndex: Int? = null) {
        val result = runBlocking {
            withContext(Dispatchers.Main) {
                session.playFrom(book.id, page, charStart, null, unitIndex).also { player.pause() }
            }
        }
        assertTrue("playFrom failed: $result", result is Outcome.Success)
    }

    /** Waits for a sentence on [page] to be current, and names it. */
    private fun awaitSentenceOn(page: Int): UnitKey {
        // The playlist's own item, not the last sentence heard, which a rebuild leaves in place until its page loads.
        awaitCondition("a sentence on page ${page + 1}") {
            (PlaylistItems.itemOf(player.currentMediaItem) as? PlaybackItem.Sentence)?.unit?.page == page
        }
        return onMain { UnitKey.of(checkNotNull(session.currentSentence())) }
    }

    private fun awaitCondition(what: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_S)
        while (!onMain(condition)) {
            if (System.nanoTime() > deadline) fail("timed out waiting for $what: ${describePlayer()}")
            Thread.sleep(POLL_MS)
        }
    }

    private fun playRange(range: StudyRange) {
        val result = runBlocking { withContext(Dispatchers.Main) { session.playRange(BOOK.id, range, null) } }
        assertTrue("playRange failed: $result", result is Outcome.Success)
    }

    private fun awaitEnd() {
        if (!ended.await(TIMEOUT_S, TimeUnit.SECONDS)) fail("book did not end: ${describePlayer()}")
    }

    /** The playlist index of [key]'s sentence, or -1; main thread. */
    private fun itemIndexOf(key: UnitKey): Int = (0 until player.mediaItemCount).indexOfFirst { i ->
        (PlaylistItems.itemOf(player.getMediaItemAt(i)) as? PlaybackItem.Sentence)?.let { UnitKey.of(it.unit) } == key
    }

    private fun describePlayer(): String = onMain {
        "state=${player.playbackState} playWhenReady=${player.playWhenReady} playing=${player.isPlaying} " +
            "item=${player.currentMediaItem?.mediaId} index=${player.currentMediaItemIndex}/${player.mediaItemCount} " +
            "positionMs=${player.currentPosition} error=${player.playerError?.errorCodeName} " +
            "played=${played.toList()} lastState=${states.lastOrNull()}"
    }

    private fun play(page: Int, charStart: Int, book: Book = BOOK) {
        val result = runBlocking { withContext(Dispatchers.Main) { session.playFrom(book.id, page, charStart, null) } }
        assertTrue("playFrom failed: $result", result is Outcome.Success)
    }

    private fun renderer() = SentenceRenderer { unit ->
        val key = UnitKey.of(unit)
        renders += key to replayRender.styleFor(key)
        if (key == failOn) {
            Outcome.Failure(SpeechError.SynthesisFailed(VOICE, "test failure"))
        } else {
            Thread.sleep(RENDER_DELAY_MS)
            Outcome.Success(PcmAudio(ShortArray(SAMPLES_PER_SENTENCE), SAMPLE_RATE))
        }
    }

    private fun allKeys(): List<UnitKey> = (0 until PAGES).flatMap { p ->
        (0 until UNITS_PER_PAGE).map { UnitKey(p, it) }
    }

    private fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        return checkNotNull(result).getOrThrow()
    }

    private inner class Recorder : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            (PlaylistItems.itemOf(mediaItem) as? PlaybackItem.Sentence)?.let {
                // Recorded before the session's own listener runs, so a replay's speed is still the one it set.
                speeds += player.playbackParameters.speed
                played += UnitKey.of(it.unit)
                if (played.size == pauseWhenPlayed) player.pause()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                endings.incrementAndGet()
                ended.countDown()
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
                pausedAtItemEnd.countDown()
            }
        }
    }

    private inner class Events : SessionEvents {
        override fun onReading(bookId: String, page: Int) = Unit

        override fun onNotReading() = Unit

        override fun onStateChanged(state: SessionState) {
            states += state
        }
    }

    /**
     * Pages load slowly, so playback catches up with extraction and waits on the "more is coming" item. With
     * [tableStart], each page opens with a zero-length table summary ending a paragraph, and unit 1 starts at its
     * offset too (as a continued table's first row does).
     */
    private class FakeText(
        private val chapters: List<Int>,
        override val pageCount: Int,
        private val tableStart: Boolean = false,
    ) : BookText {
        override fun chapterStarts() = chapters

        override fun units(page: Int): Outcome<List<ReadingUnit>, PdfPageError> {
            Thread.sleep(PAGE_DELAY_MS)
            val plain = (0 until UNITS_PER_PAGE).map { i ->
                ReadingUnit(page, i, i * CHARS, i * CHARS + CHARS - 1, "s", Lang.EN, 0, null)
            }
            if (!tableStart) return Outcome.Success(plain)
            val summary = ReadingUnit(
                page, 0, 0, 0, "", Lang.EN, ReadingUnitConfig().paragraphPauseMs, null,
                kind = UnitKind.TABLE_SUMMARY,
                table = TableRowInfo(0, null, 1, 1, emptyList(), emptyList(), continued = true),
            )
            return Outcome.Success(listOf(summary, plain[1].copy(charStart = 0)) + plain.drop(2))
        }

        override fun close() = Unit
    }

    /** Consumes nothing and is always ready, so ExoPlayer runs on its own clock (Media3's [NoSampleRenderer]). */
    private class SilentRenderer : NoSampleRenderer() {
        override fun getName() = "SilentRenderer"

        override fun render(positionUs: Long, elapsedRealtimeUs: Long) = Unit
    }

    private object Texts : ItemTexts {
        override fun pageOf(page: Int, pageCount: Int) = "Page ${page + 1} of $pageCount"

        override fun loadingNextPage() = "Loading"
    }

    private companion object {
        const val PAGES = 3
        const val UNITS_PER_PAGE = 3
        const val SHORT_PAGES = 2
        const val CHARS = 10
        const val SAMPLE_RATE = 16_000
        const val SAMPLES_PER_SENTENCE = 1_600 // 100 ms
        const val RENDER_DELAY_MS = 30L
        const val PAGE_DELAY_MS = 400L
        const val TIMEOUT_S = 30L
        const val LOG_TAG = "AudoraPlaybackTest"
        const val POLL_MS = 20L
        const val LOOP_ROUNDS = 3
        const val REPLAY_FACTOR = 0.5f
        val SLOWER = SpeechStyle(rate = REPLAY_FACTOR)
        const val SPEED_DELTA = 0.001f

        /** Long enough for a sentence past a range's end to start, were it going to. */
        const val SETTLE_MS = 600L
        const val TEST_MINUTE_OF_DAY = 600
        const val LAST_MINUTE_OF_DAY = 1_439
        val CHAPTER_STARTS = listOf(0, 2)
        val CONFIG = QueueConfig(minUnitsAhead = 2, keepBehindUnits = 2)
        val VOICE = VoiceId(EngineId.SHERPA, "test")
        val BOOK = Book(BookId("book-1"), "content://test/book-1", "Test book", 1L, "fp", PAGES, 0L, null)
        val SHORT_BOOK = Book(BookId("book-2"), "content://test/book-2", "Short book", 1L, "fp2", SHORT_PAGES, 0L, null)
        val TABLE_BOOK = Book(BookId("book-3"), "content://test/book-3", "Table book", 1L, "fp3", PAGES, 0L, null)
    }
}
