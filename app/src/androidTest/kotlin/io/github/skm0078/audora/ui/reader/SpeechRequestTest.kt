// File: app/src/androidTest/kotlin/io/github/skm0078/audora/ui/reader/SpeechRequestTest.kt
// Purpose: A place in the Voice and speed sheet asked for from outside the player (a search result, Settings) opens
//          the sheet there once and is handed back, so closing the sheet never brings it back by itself
//          (ADR 2026-10-29-find-player-options).

package io.github.skm0078.audora.ui.reader

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.config.ReaderConfig
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.release.JourneyDevice
import io.github.skm0078.audora.ui.theme.AudoraTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpeechRequestTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun text(id: Int) = rule.activity.getString(id)

    private fun shows(id: Int) = rule.onAllNodes(hasText(text(id))).fetchSemanticsNodes().isNotEmpty()

    private var asked by mutableStateOf<SpeechSpot?>(null)
    private var taken = 0

    private fun showControls() = rule.setContent {
        AudoraTheme {
            ReaderControls(
                state = openState(),
                config = ReaderConfig(),
                actions = NoReaderActions,
                speech = SpeechRequest(asked) {
                    taken++
                    asked = null
                },
            )
        }
    }

    @Test
    fun aRequestOpensItsGroupOnceAndIsHandedBack() {
        asked = SpeechSpot.READING
        showControls()
        rule.waitUntil(TIMEOUT_MS) { shows(R.string.reading_skim_short) }
        rule.runOnIdle { assertEquals("the request is handed back once", 1, taken) }

        // The phone's Back key, as a person closes the sheet.
        JourneyDevice.pressBackKey()
        rule.waitUntil(TIMEOUT_MS) { !shows(R.string.speech_sound) }
        rule.waitForIdle()
        assertFalse("the sheet came back after it was closed", shows(R.string.speech_sound))
        assertEquals(1, taken)
    }

    @Test
    fun theTopOpensWithEveryGroupClosed() {
        asked = SpeechSpot.TOP
        showControls()
        rule.waitUntil(TIMEOUT_MS) { shows(R.string.speech_sound) }
        assertTrue(shows(R.string.speech_sheet_title))
        assertFalse("a group opened from the top", shows(R.string.reading_skim_short))
    }

    @Test
    fun noRequestOpensNothing() {
        showControls()
        rule.waitForIdle()
        assertFalse(shows(R.string.speech_sound))
        assertEquals(0, taken)
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
        const val SENTENCE = "It was the best of times."
        val BOOK = BookId("speech-request-book")
        val UNIT = ReadingUnit(0, 0, 0, SENTENCE.length, SENTENCE, Lang.EN, 0, null)

        fun openState() = ReaderState(
            bookId = BOOK,
            title = "A Tale of Two Cities",
            document = DocumentState.Open(pageCount = 1),
            content = PageContent.Ready(listOf(UNIT)),
            view = ReaderView.TEXT,
        )
    }
}
