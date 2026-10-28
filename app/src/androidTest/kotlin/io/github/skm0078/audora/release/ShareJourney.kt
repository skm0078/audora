// File: app/src/androidTest/kotlin/io/github/skm0078/audora/release/ShareJourney.kt
// Purpose: The release journey's share and text-page steps on the installed app (a quote video, a page snippet,
//          Turn pages and its slide), kept apart from ReleaseJourneyTest so neither file grows past detekt's class
//          size.

package io.github.skm0078.audora.release

import android.Manifest
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import io.github.skm0078.audora.MainActivity
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.library.DocumentFormat
import io.github.skm0078.audora.di.LibraryEntryPoint
import io.github.skm0078.audora.playback.export.video.QuoteVideos
import io.github.skm0078.audora.release.JourneyDevice.app
import io.github.skm0078.audora.release.JourneyDevice.awaitTrue
import io.github.skm0078.audora.release.JourneyDevice.shows
import io.github.skm0078.audora.ui.reader.ReadingLookPreferences
import io.github.skm0078.audora.ui.share.ShareCardActivity
import io.github.skm0078.audora.ui.share.ShareCardFiles
import io.github.skm0078.audora.ui.share.ShareEntry
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/** [activity] is the app on screen now; [backToTabs] leaves any screen for the main tabs, as the journey does. */
internal class ShareJourney(
    private val rule: ComposeTestRule,
    private val activity: () -> MainActivity,
    private val backToTabs: () -> Unit,
) {
    private fun text(id: Int, vararg args: Any): String = app.getString(id, *args)

    private fun waitForNode(matcher: SemanticsMatcher, timeoutMs: Long = UI_TIMEOUT_MS) {
        rule.waitUntil(timeoutMs) { rule.shows(matcher) }
    }

    private fun node(matcher: SemanticsMatcher): SemanticsNodeInteraction {
        waitForNode(matcher)
        return rule.onAllNodes(matcher).onFirst()
    }

    private fun tap(matcher: SemanticsMatcher) {
        node(matcher).performClick()
    }

    /** The share sheet (another app's window) and the card screen closed with Back, the reader on screen again. */
    private fun backToTheReader() {
        repeat(MAX_BACKS) {
            if (JourneyDevice.resumedActivity() is MainActivity) return@repeat
            JourneyDevice.pressBackKey()
            Thread.sleep(POLL_MS)
        }
        awaitTrue("the reader back on screen") { JourneyDevice.resumedActivity() is MainActivity }
    }

    /**
     * "Share as a video" on the installed app (ADR 2026-10-28-quote-video): the card screen opens on Video, Make the
     * video captures the card without its words, and the service reads the quote in the book's own voice into a
     * 720 x 1280 MP4 with both tracks in the video share folder; then Back returns to the app. The permission is
     * granted first so the progress notification is part of the run (step 04 already covered refusing it).
     */
    fun aQuoteVideoIsShared() {
        val entry = LibraryEntryPoint.of(app)
        val book = runBlocking { entry.library().books().first() }.first { it.format == DocumentFormat.PDF }
        val folder = File(app.cacheDir, QuoteVideos.FOLDER).apply { deleteRecursively() }
        JourneyDevice.shell("pm grant ${app.packageName} ${Manifest.permission.POST_NOTIFICATIONS}")
        val reader = activity()
        JourneyDevice.onMain { ShareEntry.video(reader, book.id, VIDEO_QUOTE, page = 0) }
        waitForNode(hasText(text(R.string.share_card_video_title)), OPEN_TIMEOUT_MS)
        node(hasText(text(R.string.share_card_make_video)) and hasClickAction()).performScrollTo().performClick()
        val video = { folder.listFiles()?.firstOrNull { it.name.endsWith(".mp4") } }
        awaitTrue("the video in the share folder", VIDEO_TIMEOUT_MS, { "Files: ${folder.list()?.toList()}" }) {
            video() != null && !QuoteVideos.busy
        }
        MediaMetadataRetriever().use { made ->
            made.setDataSource(requireNotNull(video()).path)
            assertEquals("720", made.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH))
            assertEquals("1280", made.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT))
            assertEquals("yes", made.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))
        }
        backToTheReader()
    }

    /**
     * "Share part of the page" on the installed app (ADR 2026-10-28-page-snippet): the journey's [pdf] opened again
     * (Open with, as the step before may end anywhere), the sentence menu's Share part of the page opens the
     * snippet's box on a drawn page, Share cuts the page as drawn and opens the card screen on it, and the card's
     * Share writes a 1080 x 1920 picture into the share folder.
     */
    fun aPageSnippetIsShared(pdf: String) {
        val folder = File(app.cacheDir, ShareCardFiles.FOLDER).apply { deleteRecursively() }
        val uri = JourneyDevice.downloadedPdf(pdf)
        val current = activity()
        JourneyDevice.onMain { current.startActivity(JourneyDevice.openWith(uri)) }
        // Any page's picture: its description after the page number is the same on every page.
        val pageImage = hasContentDescription(text(R.string.reader_page_image, 1).substringAfter("1"), substring = true)
        waitForNode(pageImage, OPEN_TIMEOUT_MS)
        val menu = hasText(text(R.string.sentence_menu_share))
        // A long-press on a line opens its menu; the test page's text starts below its heading, so a few are tried.
        val opened = SNIPPET_PRESS_POINTS.any { (u, v) ->
            node(pageImage).performTouchInput { longClick(percentOffset(u, v)) }
            runCatching { waitForNode(menu, MENU_TIMEOUT_MS) }.isSuccess
        }
        assertTrue("no sentence menu from a long-press on the page", opened)
        tap(menu)
        node(hasText(text(R.string.share_page_part))).performScrollTo().performClick()
        waitForNode(hasText(text(R.string.reader_snippet_hint)))
        tap(hasText(text(R.string.share_card_send)) and hasClickAction())
        waitForNode(hasText(text(R.string.share_card_snippet_title)), OPEN_TIMEOUT_MS)
        node(hasText(text(R.string.share_card_send)) and hasClickAction()).performScrollTo().performClick()
        val card = { folder.listFiles()?.firstOrNull { it.extension == "png" || it.extension == "jpg" } }
        awaitTrue("the snippet card in the share folder", state = { "Files: ${folder.list()?.toList()}" }) {
            card() != null
        }
        val size = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(requireNotNull(card()).path, size)
        assertEquals(CARD_WIDTH, size.outWidth)
        assertEquals(CARD_HEIGHT, size.outHeight)
        awaitTrue("the share sheet") { JourneyDevice.resumedActivity() !is ShareCardActivity }
        backToTheReader()
    }

    /**
     * "Turn pages" on the installed app (ADR 2026-10-28-text-pages): with it on, a text book opens a screenful at a
     * time with the arrows and where it is under the text, and the arrow turns without leaving the reader.
     */
    fun aTextBookTurnsPages(textBook: Triple<String, String, String>) = withTurnPages(textBook) {
        tap(hasContentDescription(text(R.string.text_pages_next)))
        waitForNode(hasContentDescription(text(R.string.text_pages_previous)))
        awaitTrue("the reader still on screen") { JourneyDevice.resumedActivity() is MainActivity }
    }

    /**
     * A text turn's slide on the installed app (ADR 2026-10-28-text-pages): a turn on and back slides the first
     * screen in again and it settles exactly where it was, so the slide never leaves the text off to one side.
     */
    fun aTextTurnSlidesAndSettles(textBook: Triple<String, String, String>) = withTurnPages(textBook) {
        val sentence = hasText(textBook.third, substring = true)
        val left = { node(sentence).fetchSemanticsNode().boundsInRoot.left }
        val before = left()
        tap(hasContentDescription(text(R.string.text_pages_next)))
        tap(hasContentDescription(text(R.string.text_pages_previous)))
        rule.waitForIdle()
        assertEquals("the screen settled where it was", before, left(), SETTLED_PX)
    }

    /**
     * [textBook] (an asset, its type and a sentence in it) opened with Turn pages on, then [check]. The look is put
     * back as it was, so later runs start from the default.
     */
    private fun withTurnPages(textBook: Triple<String, String, String>, check: () -> Unit) {
        val looks = ReadingLookPreferences(app)
        val before = runBlocking { looks.look.first() }
        runBlocking { looks.save(before.copy(turnPages = true)) }
        try {
            val (asset, type, sentence) = textBook
            backToTabs()
            val uri = JourneyDevice.downloadedBook(asset)
            val current = activity()
            JourneyDevice.onMain { current.startActivity(JourneyDevice.openWith(uri, type)) }
            waitForNode(hasText(sentence, substring = true), OPEN_TIMEOUT_MS)
            check()
        } finally {
            runBlocking { looks.save(before) }
        }
    }

    private companion object {
        const val UI_TIMEOUT_MS = 15_000L
        const val OPEN_TIMEOUT_MS = 60_000L
        const val MAX_BACKS = 4
        const val POLL_MS = 200L
        const val CARD_WIDTH = 1080
        const val CARD_HEIGHT = 1920
        const val VIDEO_QUOTE = "Every book, read aloud."

        // The voice renders the quote, then the frames are encoded; an emulator without a GPU is slow at both.
        const val VIDEO_TIMEOUT_MS = 300_000L

        // Where a page snippet's long-press is tried, as fractions of the page across and down.
        val SNIPPET_PRESS_POINTS = listOf(0.3f to 0.3f, 0.3f to 0.45f, 0.5f to 0.6f, 0.3f to 0.75f)
        const val MENU_TIMEOUT_MS = 3_000L
        const val SETTLED_PX = 1f
    }
}
