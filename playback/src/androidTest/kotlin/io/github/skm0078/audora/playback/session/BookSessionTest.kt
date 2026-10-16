// File: playback/src/androidTest/kotlin/io/github/skm0078/audora/playback/session/BookSessionTest.kt
// Purpose: The book session on a real ExoPlayer: reads in order, seeks, rebuilds, skips failures, sleeps at chapters.

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
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.logging.LogSink
import io.github.skm0078.audora.core.model.EngineId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
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
import io.github.skm0078.audora.speech.SpeechError
import io.github.skm0078.audora.speech.audio.PcmAudio
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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
    private val ended = CountDownLatch(1)
    private val pausedAtItemEnd = CountDownLatch(1)
    private val states: MutableList<SessionState> = Collections.synchronizedList(mutableListOf())
    private var failOn: UnitKey? = null
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
                    BookSource { _, _ ->
                        Outcome.Success(OpenedBook(BOOK, FakeText(CHAPTER_STARTS)))
                    },
                    Texts,
                ),
                CONFIG,
                Events(),
                SessionEnvironment(
                    scope,
                    Dispatchers.IO.limitedParallelism(1),
                    System::nanoTime,
                    System::currentTimeMillis,
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

    private fun awaitEnd() {
        if (!ended.await(TIMEOUT_S, TimeUnit.SECONDS)) fail("book did not end: ${describePlayer()}")
    }

    private fun describePlayer(): String = onMain {
        "state=${player.playbackState} playWhenReady=${player.playWhenReady} playing=${player.isPlaying} " +
            "item=${player.currentMediaItem?.mediaId} index=${player.currentMediaItemIndex}/${player.mediaItemCount} " +
            "positionMs=${player.currentPosition} error=${player.playerError?.errorCodeName} " +
            "played=${played.toList()} lastState=${states.lastOrNull()}"
    }

    private fun play(page: Int, charStart: Int) {
        val result = runBlocking { withContext(Dispatchers.Main) { session.playFrom(BOOK.id, page, charStart, null) } }
        assertTrue("playFrom failed: $result", result is Outcome.Success)
    }

    private fun renderer() = SentenceRenderer { unit ->
        val key = UnitKey.of(unit)
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
            (PlaylistItems.itemOf(mediaItem) as? PlaybackItem.Sentence)?.let { played += UnitKey.of(it.unit) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) ended.countDown()
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

    /** Pages load slowly, so playback catches up with extraction and waits on the "more is coming" item. */
    private class FakeText(private val chapters: List<Int>) : BookText {
        override val pageCount = PAGES

        override fun chapterStarts() = chapters

        override fun units(page: Int): Outcome<List<ReadingUnit>, PdfPageError> {
            Thread.sleep(PAGE_DELAY_MS)
            return Outcome.Success(
                (0 until UNITS_PER_PAGE).map { i ->
                    ReadingUnit(page, i, i * CHARS, i * CHARS + CHARS - 1, "s", Lang.EN, 0, null)
                },
            )
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
        const val CHARS = 10
        const val SAMPLE_RATE = 16_000
        const val SAMPLES_PER_SENTENCE = 1_600 // 100 ms
        const val RENDER_DELAY_MS = 30L
        const val PAGE_DELAY_MS = 400L
        const val TIMEOUT_S = 30L
        const val LOG_TAG = "AudoraPlaybackTest"
        val CHAPTER_STARTS = listOf(0, 2)
        val CONFIG = QueueConfig(minUnitsAhead = 2, keepBehindUnits = 2)
        val VOICE = VoiceId(EngineId.SHERPA, "test")
        val BOOK = Book(BookId("book-1"), "content://test/book-1", "Test book", 1L, "fp", PAGES, 0L, null)
    }
}
