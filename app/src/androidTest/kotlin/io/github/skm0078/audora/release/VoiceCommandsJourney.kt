// File: app/src/androidTest/kotlin/io/github/skm0078/audora/release/VoiceCommandsJourney.kt
// Purpose: Release journey step 62 (ADR 2026-11-02-voice-commands-system-recogniser): on the installed app, voice
//          commands are one switch with nothing to download, and the phone's own recogniser decides whether it works.

package io.github.skm0078.audora.release

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
import io.github.skm0078.audora.R
import io.github.skm0078.audora.release.JourneyDevice.app
import io.github.skm0078.audora.release.JourneyDevice.hides
import io.github.skm0078.audora.release.JourneyDevice.shows
import io.github.skm0078.audora.speech.kws.CommandListener
import io.github.skm0078.audora.ui.search.FOUND_TAG
import io.github.skm0078.audora.ui.settings.FOCUSED_TAG
import org.junit.Assert.assertTrue

/** [back] presses Back in the app on screen and [backToTabs] leaves any screen, as the journey does. Offline. */
internal class VoiceCommandsJourney(
    private val rule: ComposeTestRule,
    private val back: () -> Unit,
    private val backToTabs: () -> Unit,
) {
    private fun text(id: Int): String = app.getString(id)

    private fun waitFor(matcher: SemanticsMatcher) {
        rule.waitUntil(UI_TIMEOUT_MS) { rule.shows(matcher) }
    }

    private fun first(matcher: SemanticsMatcher) = rule.onAllNodes(matcher).onFirst()

    /**
     * Search finds voice commands; their row on Advanced is the switch alone (no model row, no download), says it
     * cannot listen exactly where the phone has no on-device recogniser.
     */
    fun needNoDownload() {
        backToTabs()
        first(hasText(text(R.string.tab_library)) and hasClickAction()).performClick()
        waitFor(hasContentDescription(text(R.string.library_search)))
        first(hasContentDescription(text(R.string.library_search))).performClick()
        waitFor(hasSetTextAction())
        first(hasSetTextAction()).performTextInput(SEARCHED)
        val row = hasText(text(R.string.commands_switch_title)) and hasAnyAncestor(hasTestTag(FOUND_TAG))
        waitFor(row)
        first(row).performClick()
        waitFor(hasTestTag(FOCUSED_TAG))
        waitFor(hasText(text(R.string.commands_switch_title), substring = true))
        assertTrue("a voice-command model is still offered", rule.hides(hasText(OLD_MODEL_ROW, substring = true)))
        val cannotListen = rule.shows(hasText(text(R.string.commands_not_available), substring = true))
        assertTrue("the switch says it cannot listen on a phone that can", cannotListen != available())
        back()
        waitFor(hasTestTag(FOUND_TAG))
        back()
        rule.waitUntil(UI_TIMEOUT_MS) { rule.hides(hasTestTag(FOUND_TAG)) }
    }

    private fun available(): Boolean {
        var available = false
        JourneyDevice.onMain { available = CommandListener.isAvailable(app) }
        return available
    }

    private companion object {
        const val UI_TIMEOUT_MS = 15_000L
        const val SEARCHED = "voice command"

        /** The row older builds showed: "Voice commands model (English)". */
        const val OLD_MODEL_ROW = "Voice commands model"
    }
}
