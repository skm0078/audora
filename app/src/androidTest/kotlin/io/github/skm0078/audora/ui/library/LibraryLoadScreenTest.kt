// File: app/src/androidTest/kotlin/io/github/skm0078/audora/ui/library/LibraryLoadScreenTest.kt
// Purpose: The library screen with 5,000 books on CI's emulator (ADR 2026-10-28-performance-budget): the first screen
//          draws, flings run without a frozen frame, and the last book is reached, inside LibraryLoadBudget, as covers
//          and as a list. The platform's frame metrics (no library) time every frame; the numbers go to logcat
//          (tag AudoraLoadTest), which CI prints.

package io.github.skm0078.audora.ui.library

import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.FrameMetrics
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.skm0078.audora.core.config.LibraryLoadBudget
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.BookAvailability
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.BookProgress
import io.github.skm0078.audora.core.library.SyntheticLibrary
import io.github.skm0078.audora.ui.theme.AudoraTheme
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryLoadScreenTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val budget = LibraryLoadBudget()

    @Test
    fun fiveThousandCoversDrawScrollAndReachTheLastBook() = checkLayout(LibraryLayout.GRID)

    @Test
    fun fiveThousandRowsDrawScrollAndReachTheLastBook() = checkLayout(LibraryLayout.LIST)

    private fun checkLayout(layout: LibraryLayout) {
        val items = itemsOf(budget.largeLibraryBooks)
        val shown = SystemClock.elapsedRealtime()
        rule.setContent {
            AudoraTheme {
                LibraryScreen(
                    listState = LibraryListState.Loaded(items),
                    busy = false,
                    message = null,
                    pendingRemoval = null,
                    actions = NoActions,
                    view = LibraryLayoutState(layout) {},
                )
            }
        }
        rule.onAllNodes(book(items.first())).onFirst().assertExists()
        val firstFrameMs = SystemClock.elapsedRealtime() - shown
        val list = sameNode(rule.onNode(hasScrollToIndexAction() and hasAnyDescendant(book(items.first()))))

        val frames = FrameTimes().apply { attach(rule.activity.window) }
        var slowestFlingMs = 0L
        repeat(FLINGS) {
            val flung = SystemClock.elapsedRealtime()
            rule.onNode(list).performTouchInput { swipeUp() }
            rule.waitForIdle()
            slowestFlingMs = maxOf(slowestFlingMs, SystemClock.elapsedRealtime() - flung)
        }
        // Frame metrics arrive after the frame, on their own thread: the last flings' frames land within a moment.
        SystemClock.sleep(FRAME_REPORT_MS)
        frames.detach(rule.activity.window)

        val scrolled = SystemClock.elapsedRealtime()
        rule.onNode(list).performScrollToIndex(items.lastIndex)
        rule.onAllNodes(book(items.last())).onFirst().assertExists()
        val scrollMs = SystemClock.elapsedRealtime() - scrolled

        val frozen = frames.longerThan(budget.frozenFrameMs)
        Log.i(
            TAG,
            "$layout books=${items.size} firstScreenMs=$firstFrameMs scrollToLastMs=$scrollMs " +
                "slowestFlingMs=$slowestFlingMs frames=${frames.count} janky=${frames.janky} " +
                "slowestMs=${frames.slowestMs} frozen=$frozen",
        )
        // Some emulator images report no frame metrics at all; then "0 frozen" would mean nothing, so say so.
        if (frames.count == 0) Log.w(TAG, "$layout: no frame metrics were reported, frozen frames were not measured")
        assertTrue(
            "First screen took $firstFrameMs ms (budget ${budget.firstFrameMs} ms)",
            firstFrameMs <= budget.firstFrameMs,
        )
        assertTrue(
            "Scrolling to the last book took $scrollMs ms (budget ${budget.scrollToEndMs} ms)",
            scrollMs <= budget.scrollToEndMs,
        )
        assertTrue("$frozen frozen frames (over ${budget.frozenFrameMs} ms) while flinging", frozen == 0)
    }

    /** The node [interaction] finds now, matched by its id later, so scrolling the first book away keeps it found. */
    private fun sameNode(interaction: SemanticsNodeInteraction): SemanticsMatcher {
        val id = interaction.fetchSemanticsNode().id
        return SemanticsMatcher("node $id") { node: SemanticsNode -> node.id == id }
    }

    /**
     * A book's tile (its description) or row (its title's text), and its menu button: named with the title, unique
     * thanks to the 4-digit number.
     */
    private fun book(item: LibraryItem): SemanticsMatcher {
        val title = item.book.title
        return (hasContentDescription(title, substring = true) or hasText(title, substring = true)) and hasClickAction()
    }

    /**
     * Every frame drawn while attached, from the platform's own frame metrics, timed on a thread of its own. Janky:
     * over the frame's deadline (one refresh). On an emulator with a software GPU many frames miss it, so jank is
     * logged and only frozen frames fail.
     */
    private class FrameTimes : Window.OnFrameMetricsAvailableListener {
        private val thread = HandlerThread("frame-times").apply { start() }

        /** Each frame's total time and its deadline, in nanoseconds. */
        private val frames = CopyOnWriteArrayList<Pair<Long, Long>>()

        val count: Int get() = frames.size
        val janky: Int get() = frames.count { (total, deadline) -> total > deadline }
        val slowestMs: Long get() = (frames.maxOfOrNull { it.first } ?: 0L) / NANOS_PER_MS

        fun longerThan(ms: Long): Int = frames.count { it.first / NANOS_PER_MS > ms }

        fun attach(window: Window) {
            window.addOnFrameMetricsAvailableListener(this, Handler(thread.looper))
        }

        fun detach(window: Window) {
            window.removeOnFrameMetricsAvailableListener(this)
            thread.quitSafely()
        }

        override fun onFrameMetricsAvailable(window: Window, metrics: FrameMetrics, dropCount: Int) {
            frames += metrics.getMetric(FrameMetrics.TOTAL_DURATION) to metrics.getMetric(FrameMetrics.DEADLINE)
        }
    }

    private object NoActions : LibraryActions {
        override fun onAddPdf(documentUri: Uri) = Unit

        override fun onLocate(id: BookId, documentUri: Uri) = Unit

        override fun onOpenBook(book: Book) = Unit

        override fun onContinue(book: Book) = Unit

        override fun onRequestRemoval(book: Book) = Unit

        override fun onExportAudiobook(book: Book) = Unit

        override fun onConfirmRemoval() = Unit

        override fun onCancelRemoval() = Unit

        override fun onMessageShown() = Unit

        override fun onScreenResumed() = Unit
    }

    private companion object {
        const val TAG = "AudoraLoadTest"
        const val FLINGS = 10
        const val FRAME_REPORT_MS = 500L
        const val NANOS_PER_MS = 1_000_000L

        /** The JVM load test's books, each shown as the library shows it: on hand, with its reading progress. */
        fun itemsOf(count: Int): List<LibraryItem> {
            val books = SyntheticLibrary.books(count)
            val anchors = SyntheticLibrary.anchors(books).associateBy { it.bookId }
            return books.map { LibraryItem(it, BookAvailability.AVAILABLE, BookProgress.of(it, anchors[it.id])) }
        }
    }
}
