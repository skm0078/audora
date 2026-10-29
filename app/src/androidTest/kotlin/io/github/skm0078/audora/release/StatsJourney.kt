// File: app/src/androidTest/kotlin/io/github/skm0078/audora/release/StatsJourney.kt
// Purpose: The release journey's listening stats step on the installed app (ADR 2026-10-28-listening-stats-screen),
//          kept apart from ReleaseJourneyTest so it stays under detekt's class size.

package io.github.skm0078.audora.release

import android.graphics.BitmapFactory
import android.provider.MediaStore
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.github.skm0078.audora.MainActivity
import io.github.skm0078.audora.R
import io.github.skm0078.audora.release.JourneyDevice.app
import io.github.skm0078.audora.release.JourneyDevice.awaitTrue
import io.github.skm0078.audora.release.JourneyDevice.shows
import io.github.skm0078.audora.ui.search.FOUND_TAG
import io.github.skm0078.audora.ui.share.ShareCardFiles
import java.io.File
import org.junit.Assert.assertEquals

/** [backToTabs] leaves any screen for the main tabs, as the journey does. */
internal class StatsJourney(private val rule: ComposeTestRule, private val backToTabs: () -> Unit) {
    private fun text(id: Int): String = app.getString(id)

    private fun tap(matcher: SemanticsMatcher) {
        rule.waitUntil(UI_TIMEOUT_MS) { rule.shows(matcher) }
        rule.onAllNodes(matcher).onFirst().performClick()
    }

    /**
     * After the journey's listening, the stats screen opens from the library's search like its menu item, shows the
     * week and the month, and its card is saved to the phone's pictures and shared as a story-sized picture. The
     * earlier steps played books, so there is something to share.
     */
    fun cardIsSavedAndShared() {
        backToTabs()
        tap(hasContentDescription(text(R.string.library_search)))
        rule.onAllNodes(hasSetTextAction()).onFirst().performTextInput(text(R.string.library_listening_stats))
        tap(hasText(text(R.string.library_listening_stats)) and hasAnyAncestor(hasTestTag(FOUND_TAG)))
        rule.waitUntil(OPEN_TIMEOUT_MS) { rule.shows(hasText(text(R.string.stats_title))) }
        tap(hasText(text(R.string.stats_period_month)) and hasClickAction())
        tap(hasText(text(R.string.stats_share)) and hasClickAction())
        rule.waitUntil(UI_TIMEOUT_MS) { rule.shows(hasText(text(R.string.stats_share_title))) }

        val saved = picturesSaved()
        tap(hasText(text(R.string.stats_save)) and hasClickAction())
        awaitTrue("the stats picture in the phone's pictures") { picturesSaved() > saved }

        val folder = File(app.cacheDir, ShareCardFiles.FOLDER).apply { deleteRecursively() }
        val share = hasText(text(R.string.stats_share)) and hasClickAction()
        rule.waitUntil(UI_TIMEOUT_MS) { rule.shows(share) }
        rule.onAllNodes(share).onFirst().performClick()
        val card = { folder.listFiles()?.firstOrNull { it.extension == "png" } }
        awaitTrue("the stats card in the share folder", state = { "Files: ${folder.list()?.toList()}" }) {
            card() != null
        }
        val size = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(requireNotNull(card()).path, size)
        assertEquals(CARD_WIDTH, size.outWidth)
        assertEquals(CARD_HEIGHT, size.outHeight)
        awaitTrue("the share sheet over the app") { JourneyDevice.resumedActivity() !is MainActivity }
        repeat(MAX_BACKS) {
            if (JourneyDevice.resumedActivity() is MainActivity) return@repeat
            JourneyDevice.pressBackKey()
            Thread.sleep(POLL_MS)
        }
        backToTabs()
        // The search this step opened would hide the library's ⋮ menu from the steps after it.
        tap(hasContentDescription(text(R.string.library_search_close)))
    }

    /** The stats pictures this app has saved, counted from the phone's own pictures. */
    private fun picturesSaved(): Int {
        val where = "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
        return app.contentResolver.query(
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            arrayOf(MediaStore.Images.Media._ID),
            where,
            arrayOf("listenlock-stats-%"),
            null,
        )?.use { it.count } ?: 0
    }

    private companion object {
        const val UI_TIMEOUT_MS = 15_000L
        const val OPEN_TIMEOUT_MS = 60_000L
        const val MAX_BACKS = 4
        const val POLL_MS = 200L
        const val CARD_WIDTH = 1080
        const val CARD_HEIGHT = 1920
    }
}
