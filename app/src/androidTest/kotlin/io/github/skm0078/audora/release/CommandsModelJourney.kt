// File: app/src/androidTest/kotlin/io/github/skm0078/audora/release/CommandsModelJourney.kt
// Purpose: Release journey step 62 (ADR 2026-10-30-voice-commands-librispeech): the installed app's voice commands
//          download is the LibriSpeech model, and Voices & licences credits LibriSpeech for it.

package io.github.skm0078.audora.release

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import io.github.skm0078.audora.R
import io.github.skm0078.audora.release.JourneyDevice.app
import io.github.skm0078.audora.release.JourneyDevice.shows
import io.github.skm0078.audora.speech.kws.KeywordModel
import io.github.skm0078.audora.speech.kws.VoiceCommandModels
import io.github.skm0078.audora.speech.ocr.OcrModelsState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/** [backToTabs] leaves any screen for the main tabs, as the journey does. Nothing here touches the network. */
internal class CommandsModelJourney(private val rule: ComposeTestRule, private val backToTabs: () -> Unit) {
    private fun text(id: Int): String = app.getString(id)

    private fun waitFor(matcher: SemanticsMatcher) {
        rule.waitUntil(UI_TIMEOUT_MS) { rule.shows(matcher) }
    }

    private fun tap(matcher: SemanticsMatcher) {
        waitFor(matcher)
        rule.onAllNodes(matcher).onFirst().performClick()
    }

    /**
     * The download Settings offers is the LibriSpeech model at its pinned size, whatever an older build left on the
     * phone (an old pin is a different folder, so it never counts as installed), and the licences list credits it.
     */
    fun isLibriSpeech() {
        val model = KeywordModel.ENGLISH
        assertTrue(model.id, model.id.contains(LIBRISPEECH, ignoreCase = true))
        val state = runBlocking { VoiceCommandModels.get(app).state().first { it != OcrModelsState.Queued } }
        assertEquals(model.sizeBytes, sizeOf(state))

        backToTabs()
        tap(hasText(text(R.string.tab_you)) and hasClickAction())
        // You keeps the page it was last on; its back arrow returns to the main page, where the licences row is.
        val licences = hasText(text(R.string.licences_title)) and hasClickAction()
        val pageBack = hasContentDescription(text(R.string.navigate_back))
        rule.waitUntil(UI_TIMEOUT_MS) { rule.shows(licences) || rule.shows(pageBack) }
        if (!rule.shows(licences)) tap(pageBack)
        waitFor(licences)
        rule.onAllNodes(licences).onFirst().performScrollTo().performClick()

        val credit = hasText(VOICE_COMMANDS_CREDIT, substring = true)
        waitFor(hasScrollAction())
        rule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(credit)
        assertTrue(rule.shows(credit and hasText(LIBRISPEECH, substring = true)))
        backToTabs()
    }

    /** The size a state reports: what a download fetches, or what is installed. */
    private fun sizeOf(state: OcrModelsState): Long? = when (state) {
        is OcrModelsState.NotDownloaded -> state.missingBytes
        is OcrModelsState.Downloading -> state.total
        is OcrModelsState.Ready -> state.bytes
        else -> null
    }

    private companion object {
        const val UI_TIMEOUT_MS = 15_000L
        const val LIBRISPEECH = "LibriSpeech"
        const val VOICE_COMMANDS_CREDIT = "voice commands, if downloaded"
    }
}
