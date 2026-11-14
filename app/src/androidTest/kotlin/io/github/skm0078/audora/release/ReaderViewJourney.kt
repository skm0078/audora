// File: app/src/androidTest/kotlin/io/github/skm0078/audora/release/ReaderViewJourney.kt
// Purpose: The release journey's full screen and text size step on the installed app: Full screen hides the phone's
//          bars and the reader's own, Back brings them back without leaving the book, and ⋮ › Text size makes the
//          text larger. Kept apart from ReleaseJourneyTest so that file stays under detekt's class size.

package io.github.skm0078.audora.release

import android.util.Log
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.github.skm0078.audora.R
import io.github.skm0078.audora.release.JourneyDevice.app
import io.github.skm0078.audora.release.JourneyDevice.awaitTrue
import io.github.skm0078.audora.release.JourneyDevice.hides
import io.github.skm0078.audora.release.JourneyDevice.shows
import io.github.skm0078.audora.ui.reader.ReadingLookPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** [book] is a PDF in assets/pdfs; [backToTabs] leaves any screen for the main tabs, as the journey does. */
internal class ReaderViewJourney(
    private val rule: ComposeTestRule,
    private val book: String,
    private val backToTabs: () -> Unit,
) {
    private fun described(id: Int): SemanticsMatcher = hasContentDescription(app.getString(id))

    private fun tap(matcher: SemanticsMatcher) {
        awaitTrue("${matcher.description} on screen") { rule.shows(matcher) }
        rule.onAllNodes(matcher).onFirst().performClick()
    }

    /**
     * Full screen hides the status bar and the reader's back button, keeps a way out in sight, and Back turns it off
     * with the book still open. Then ⋮ › Text size's Larger text makes the saved size larger; the look is put back.
     */
    fun fullScreenAndTextSize() {
        val looks = ReadingLookPreferences(app)
        val before = runBlocking { looks.look.first() }
        try {
            openTheBook()
            val back = described(R.string.navigate_back)
            // CI's emulator image has no system UI, so there is no status bar to hide; the bars are checked where
            // there is one.
            val hasStatusBar = statusBarShown()
            Log.i(LOG_TAG, "Status bar on screen before full screen: $hasStatusBar")
            tap(described(R.string.reader_full_screen))
            awaitTrue("the reader's top bar gone") { rule.hides(back) }
            awaitTrue("Exit full screen in sight") { rule.shows(described(R.string.reader_full_screen_exit)) }
            if (hasStatusBar) awaitTrue("the status bar hidden") { !statusBarShown() }

            JourneyDevice.pressBackKey()
            awaitTrue("the top bar back after Back") { rule.shows(back) }
            if (hasStatusBar) awaitTrue("the status bar back after Back") { statusBarShown() }
            awaitTrue("still in the book") { rule.shows(described(R.string.reader_full_screen)) }

            tap(described(R.string.reader_more))
            tap(hasText(app.getString(R.string.text_size_title)))
            tap(described(R.string.text_size_larger))
            awaitTrue("a larger text size saved") { runBlocking { looks.look.first() }.textScale > before.textScale }
            JourneyDevice.pressBackKey()
        } finally {
            runBlocking { looks.save(before) }
        }
        backToTabs()
    }

    private fun openTheBook() {
        backToTabs()
        val current = requireNotNull(JourneyDevice.resumedActivity()) { "No app on screen" }
        JourneyDevice.onMain { current.startActivity(JourneyDevice.openWith(JourneyDevice.downloadedPdf(book))) }
        awaitTrue("the book open with its Full screen button", OPEN_TIMEOUT_MS) {
            rule.shows(described(R.string.reader_full_screen))
        }
    }

    private fun statusBarShown(): Boolean {
        val activity = JourneyDevice.resumedActivity() ?: return false
        var shown = false
        JourneyDevice.onMain {
            val insets = ViewCompat.getRootWindowInsets(activity.window.decorView)
            shown = insets?.isVisible(WindowInsetsCompat.Type.statusBars()) == true
        }
        return shown
    }

    private companion object {
        const val OPEN_TIMEOUT_MS = 60_000L
        const val LOG_TAG = "ReleaseJourney"
    }
}
