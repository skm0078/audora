// File: app/src/androidTest/kotlin/io/github/skm0078/audora/ui/reader/PreviouslyOnCardTest.kt
// Purpose: The "Previously, N days ago" card shows the days and the sentences, and its buttons wait for the player.

package io.github.skm0078.audora.ui.reader

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.ui.theme.AudoraTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreviouslyOnCardTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val taps = mutableListOf<String>()
    private val lines = listOf("Call me Ishmael.", "Some years ago.")
    private val recap = PreviouslyOn(9, lines, ReadingPosition(BookId("b"), 3, 0))

    private fun text(id: Int): String = rule.activity.getString(id)

    private fun show(ready: Boolean) = rule.setContent {
        AudoraTheme {
            PreviouslyOnCard(recap, ready, { taps += "again" }, { taps += "on" }, { taps += "closed" })
        }
    }

    @Test
    fun theDaysAwayAndTheSentencesAreShown() {
        show(ready = true)
        val title = rule.activity.resources.getQuantityString(R.plurals.reader_previously_days, 9, 9)

        rule.onNode(isHeading()).assertExists()
        rule.onNodeWithText(title).assertExists()
        rule.onNodeWithText("Call me Ishmael. Some years ago.", substring = true).assertExists()
    }

    @Test
    fun theButtonsSayWhichWayToGo() {
        show(ready = true)

        rule.onNodeWithText(text(R.string.reader_previously_hear_again)).assertIsEnabled().performClick()
        rule.onNodeWithText(text(R.string.reader_previously_go_on)).performClick()

        assertEquals(listOf("again", "on"), taps)
    }

    @Test
    fun theButtonsWaitForThePlayer() {
        show(ready = false)

        rule.onNodeWithText(text(R.string.reader_previously_hear_again)).assertIsNotEnabled()
        rule.onNodeWithText(text(R.string.reader_previously_go_on)).assertIsNotEnabled()
    }
}
