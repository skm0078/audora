// File: app/src/androidTest/kotlin/io/github/skm0078/audora/release/NoticesJourney.kt
// Purpose: Release journey step 61 (ADR 2026-10-30-open-source-notices): on the installed app, Voices & licences
//          opens every licence text the APK carries, the notices index first and ListenLock's own GPL in reach.

package io.github.skm0078.audora.release

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import io.github.skm0078.audora.R
import io.github.skm0078.audora.release.JourneyDevice.app
import io.github.skm0078.audora.release.JourneyDevice.shows
import io.github.skm0078.audora.ui.licences.AppNotices
import io.github.skm0078.audora.ui.licences.LICENCE_TEXTS_TAG
import org.junit.Assert.assertEquals

/** [backToTabs] leaves any screen for the main tabs, as the journey does. Nothing here touches the network. */
internal class NoticesJourney(private val rule: ComposeTestRule, private val backToTabs: () -> Unit) {
    private fun text(id: Int): String = app.getString(id)

    private fun waitFor(matcher: SemanticsMatcher) {
        rule.waitUntil(UI_TIMEOUT_MS) { rule.shows(matcher) }
    }

    private fun tap(matcher: SemanticsMatcher) {
        waitFor(matcher)
        rule.onAllNodes(matcher).onFirst().performClick()
    }

    /**
     * The APK's licence folder starts with the notices index and ListenLock's GPL. You → Voices & licences → All
     * licence texts → Read shows the index's title, and the list scrolls on to the GPL's own heading and to the
     * statement the IJG licence asks for.
     */
    fun everyLicenceTextOpens() {
        val names = AppNotices.order(app.assets.list(AppNotices.ASSET_DIR).orEmpty().toList())
        assertEquals(listOf(AppNotices.INDEX, AppNotices.APP_LICENCE), names.take(2))

        backToTabs()
        tap(hasText(text(R.string.tab_you)) and hasClickAction())
        // You keeps the page it was last on; its back arrow returns to the main page, where the licences row is.
        val licences = hasText(text(R.string.licences_title)) and hasClickAction()
        val pageBack = hasContentDescription(text(R.string.navigate_back))
        rule.waitUntil(UI_TIMEOUT_MS) { rule.shows(licences) || rule.shows(pageBack) }
        if (!rule.shows(licences)) tap(pageBack)
        waitFor(licences)
        rule.onAllNodes(licences).onFirst().performScrollTo().performClick()

        tap(hasText(text(R.string.notices_read)) and hasClickAction())
        waitFor(hasText(INDEX_TITLE, substring = true))
        val list = rule.onNode(hasTestTag(LICENCE_TEXTS_TAG))
        list.performScrollToNode(hasText(IJG_STATEMENT, substring = true))
        list.performScrollToNode(hasText(AppNotices.APP_LICENCE))
        list.performScrollToNode(hasText(GPL_HEADING, substring = true))

        tap(hasText(text(R.string.licences_close)) and hasClickAction())
        rule.waitUntil(UI_TIMEOUT_MS) { !rule.shows(hasTestTag(LICENCE_TEXTS_TAG)) }
        backToTabs()
    }

    private companion object {
        const val UI_TIMEOUT_MS = 15_000L
        const val INDEX_TITLE = "ListenLock: open-source notices"
        const val IJG_STATEMENT = "based in part on the work of the Independent JPEG Group"
        const val GPL_HEADING = "GNU GENERAL PUBLIC LICENSE"
    }
}
