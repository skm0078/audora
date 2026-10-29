// File: app/src/androidTest/kotlin/io/github/skm0078/audora/ui/reader/OpeningLineCardTest.kt
// Purpose: The opening line floats over the page without taking its touches, is heard as one phrase, then goes.

package io.github.skm0078.audora.ui.reader

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.ui.theme.AudoraTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OpeningLineCardTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val line = ReadingUnit(0, 0, 0, 16, "Call me Ishmael.", Lang.EN, 0, null)
    private var pageTaps = 0
    private var shown = 0

    private fun show() = rule.setContent {
        AudoraTheme {
            WithOpeningLine(line, onShown = { shown++ }) {
                Button(onClick = { pageTaps++ }, Modifier.fillMaxSize()) { Text("page") }
            }
        }
    }

    private fun label() = rule.activity.getString(R.string.reader_opening_line)

    @Test
    fun theLineIsHeardAsOnePhraseNotAsTheSentence() {
        show()

        rule.onNode(hasContentDescription("${label()}: Call me Ishmael.")).assertExists()
        rule.onNodeWithText("Call me Ishmael.").assertDoesNotExist()
    }

    @Test
    fun aTapOnTheCardReachesThePageUnderIt() {
        show()

        rule.onNode(hasContentDescription(label(), substring = true)).performClick()

        assertEquals(1, pageTaps)
    }

    @Test
    fun itGoesByItselfAndSaysSo() {
        rule.mainClock.autoAdvance = false
        show()
        rule.mainClock.advanceTimeBy(SHOWN_FOR_A_WHILE)
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()

        rule.onNode(hasContentDescription(label(), substring = true)).assertDoesNotExist()
        assertEquals(1, shown)
    }

    private companion object {
        /** Past the card's time, even with Android's longer times for reading asked for. */
        const val SHOWN_FOR_A_WHILE = 60_000L
    }
}
