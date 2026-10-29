// File: app/src/androidTest/kotlin/io/github/skm0078/audora/release/CurlJourney.kt
// Purpose: The release journey's page-curl step on the installed app (ADR 2026-10-29-page-curl), kept apart from
//          ReleaseJourneyTest so that file stays under detekt's class size.

package io.github.skm0078.audora.release

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import io.github.skm0078.audora.MainActivity
import io.github.skm0078.audora.R
import io.github.skm0078.audora.release.JourneyDevice.app
import io.github.skm0078.audora.release.JourneyDevice.awaitTrue
import io.github.skm0078.audora.release.JourneyDevice.shows
import io.github.skm0078.audora.ui.reader.ReadingLookPreferences
import io.github.skm0078.audora.ui.reader.flip.TurnStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals

/** [activity] is the app on screen now. */
internal class CurlJourney(private val rule: ComposeTestRule, private val activity: () -> MainActivity) {
    private fun text(id: Int, vararg args: Any): String = app.getString(id, *args)

    private fun waitForNode(matcher: SemanticsMatcher, timeoutMs: Long = UI_TIMEOUT_MS) {
        rule.waitUntil(timeoutMs) { rule.shows(matcher) }
    }

    private fun node(matcher: SemanticsMatcher): SemanticsNodeInteraction {
        waitForNode(matcher)
        return rule.onAllNodes(matcher).onFirst()
    }

    /**
     * A fresh install curls its pages: a swipe on [pdf]'s page turns it with the curl. Then Page and text offers the
     * other styles, and Slide picked there is kept. The look is put back as it was for later steps.
     */
    fun aPdfPageCurlsAndSlideCanBePicked(pdf: String) {
        val looks = ReadingLookPreferences(app)
        val before = runBlocking { looks.look.first() }
        assertEquals("pages curl unless the reader picks otherwise", TurnStyle.CURL, before.turn.style)
        try {
            val uri = JourneyDevice.downloadedPdf(pdf)
            val current = activity()
            JourneyDevice.onMain { current.startActivity(JourneyDevice.openWith(uri)) }
            val pageImage = { page: Int -> hasContentDescription(text(R.string.reader_page_image, page)) }
            var shown: Int? = null
            awaitTrue("a drawn page on screen") {
                shown = (1..MAX_SCANNED_PAGES).firstOrNull { rule.shows(pageImage(it)) }
                shown != null
            }
            val from = requireNotNull(shown)
            val to = if (from > 1) from - 1 else from + 1
            node(pageImage(from)).performTouchInput { if (to < from) swipeRight() else swipeLeft() }
            waitForNode(pageImage(to), OPEN_TIMEOUT_MS)

            node(hasContentDescription(text(R.string.reader_more))).performClick()
            node(hasText(text(R.string.page_text_title))).performClick()
            node(hasText(text(R.string.page_turn_style))).performClick()
            node(hasText(text(R.string.page_turn_slide))).performClick()
            awaitTrue("Slide kept") { runBlocking { looks.look.first() }.turn.style == TurnStyle.SLIDE }
            JourneyDevice.pressBackKey()
        } finally {
            runBlocking { looks.save(before) }
        }
    }

    private companion object {
        const val UI_TIMEOUT_MS = 15_000L
        const val OPEN_TIMEOUT_MS = 60_000L
        const val MAX_SCANNED_PAGES = 40
    }
}
