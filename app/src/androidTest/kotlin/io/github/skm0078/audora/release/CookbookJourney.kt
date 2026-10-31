// File: app/src/androidTest/kotlin/io/github/skm0078/audora/release/CookbookJourney.kt
// Purpose: The release journey's cookbook step on the installed app (ADR 2026-10-29-in-app-cookbook), kept apart from
//          ReleaseJourneyTest so that file stays under detekt's class size.

package io.github.skm0078.audora.release

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import io.github.skm0078.audora.COOKBOOK_ASSET_DIR
import io.github.skm0078.audora.R
import io.github.skm0078.audora.playback.text.MarkdownParser
import io.github.skm0078.audora.release.JourneyDevice.app
import io.github.skm0078.audora.release.JourneyDevice.shows
import io.github.skm0078.audora.ui.shell.DRAWER_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/** [backToTabs] leaves any screen for the main tabs, as the journey does. Nothing here touches the network. */
internal class CookbookJourney(private val rule: ComposeTestRule, private val backToTabs: () -> Unit) {
    private fun text(id: Int): String = app.getString(id)

    private fun waitForNode(matcher: SemanticsMatcher, timeoutMs: Long = UI_TIMEOUT_MS) {
        rule.waitUntil(timeoutMs) { rule.shows(matcher) }
    }

    private fun tap(matcher: SemanticsMatcher) {
        waitForNode(matcher)
        rule.onAllNodes(matcher).onFirst().performClick()
    }

    /**
     * The APK carries the cookbook in English, Hindi and Bengali with the same recipes, none with the site's markup
     * left in. The library's home labels it "From ListenLock" and its card opens it in the reader, ready to play; the
     * side panel's row opens the same book.
     */
    fun opensFromTheLibrary() {
        val chapters = LANGS.associateWith { lang ->
            val markdown = app.assets.open("$COOKBOOK_ASSET_DIR/$lang.md").bufferedReader().use { it.readText() }
            assertFalse("$lang: site markup left in", markdown.contains("{{") || markdown.contains("{%"))
            assertFalse("$lang: front matter left in", markdown.lineSequence().any { it.trim() == "---" })
            MarkdownParser.parse(markdown).chapters.size
        }
        assertTrue("recipes in every book: $chapters", chapters.values.all { it > 0 })
        assertEquals("the same recipes in every language: $chapters", 1, chapters.values.toSet().size)

        backToTabs()
        tap(hasText(text(R.string.tab_library)) and hasClickAction())
        val fromListenLock = hasText(text(R.string.home_section_from_listenlock))
        waitForNode(hasScrollToNodeAction())
        rule.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(fromListenLock)
        tap(fromListenLock and hasClickAction())
        opensTheCookbook()

        tap(hasText(text(R.string.tab_library)) and hasClickAction())
        tap(hasContentDescription(text(R.string.drawer_open)))
        val inPanel =
            hasText(text(R.string.cookbook_open)) and hasClickAction() and hasAnyAncestor(hasTestTag(DRAWER_TAG))
        waitForNode(inPanel)
        rule.onNode(inPanel).performScrollTo().performClick()
        opensTheCookbook()
    }

    private fun opensTheCookbook() {
        waitForNode(hasContentDescription(text(R.string.reader_play)), OPEN_TIMEOUT_MS)
        assertTrue("the cookbook's title shows", rule.shows(hasText(text(R.string.cookbook_title), substring = true)))
        backToTabs()
    }

    private companion object {
        const val UI_TIMEOUT_MS = 15_000L
        const val OPEN_TIMEOUT_MS = 60_000L
        val LANGS = listOf("en", "hi", "bn")
    }
}
