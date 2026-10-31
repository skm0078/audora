// File: app/src/androidTest/kotlin/io/github/skm0078/audora/release/BetaLanguageJourney.kt
// Purpose: Release journey step 65 (2026-10-31): on the installed app, every Bengali voice is marked beta, from
//          speech/models.json through the APK's catalog to the Voices screen, and no other voice is.

package io.github.skm0078.audora.release

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.release.JourneyDevice.app
import io.github.skm0078.audora.release.JourneyDevice.shows
import io.github.skm0078.audora.speech.voices.VoiceKind
import io.github.skm0078.audora.speech.voices.VoiceLibrary
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/** [backToTabs] leaves any screen for the main tabs, as the journey does. Nothing here touches the network. */
internal class BetaLanguageJourney(private val rule: ComposeTestRule, private val backToTabs: () -> Unit) {
    private fun text(id: Int): String = app.getString(id)

    private fun waitFor(matcher: SemanticsMatcher) {
        rule.waitUntil(UI_TIMEOUT_MS) { rule.shows(matcher) }
    }

    private fun tap(matcher: SemanticsMatcher) {
        waitFor(matcher)
        rule.onAllNodes(matcher).onFirst().performClick()
    }

    /**
     * The catalog in the APK marks exactly the Bengali downloads beta (the build read speech/models.json `beta`), and
     * You → Voices shows the Beta line on the Bengali download.
     */
    fun bengaliVoicesSayBeta() {
        val catalog = runBlocking { VoiceLibrary.get(app).rows().first { it.isNotEmpty() } }
            .filter { it.kind == VoiceKind.CATALOG }
        val bengali = catalog.filter { it.lang == Lang.BN }
        assertTrue("no Bengali voice in the catalog", bengali.isNotEmpty())
        val beta = catalog.filter { it.beta }.map { it.id }.toSet()
        assertEquals("Only Bengali voices are beta", bengali.map { it.id }.toSet(), beta)

        backToTabs()
        tap(hasText(text(R.string.tab_you)) and hasClickAction())
        // You keeps the page it was last on; its back arrow returns to the main page, where the Voices row is.
        val voices = hasText(text(R.string.settings_voices_title)) and hasClickAction()
        val pageBack = hasContentDescription(text(R.string.navigate_back))
        rule.waitUntil(UI_TIMEOUT_MS) { rule.shows(voices) || rule.shows(pageBack) }
        if (!rule.shows(voices)) tap(pageBack)
        waitFor(voices)
        rule.onAllNodes(voices).onFirst().performScrollTo().performClick()

        // The Bengali one-tap download, first on the screen, carries it; "All voices" stays closed.
        val betaLine = hasText(text(R.string.voices_beta))
        waitFor(betaLine)
        rule.onAllNodes(betaLine).onFirst().performScrollTo()
        backToTabs()
    }

    private companion object {
        const val UI_TIMEOUT_MS = 15_000L
    }
}
