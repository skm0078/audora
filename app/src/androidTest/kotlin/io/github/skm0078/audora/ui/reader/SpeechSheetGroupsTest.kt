// File: app/src/androidTest/kotlin/io/github/skm0078/audora/ui/reader/SpeechSheetGroupsTest.kt
// Purpose: The Voice and speed sheet stays one level deep (ADR 2026-10-29-voice-and-speed-one-level): a group opens
//          in place under its header, one at a time, never as a second sheet, and a shortcut opens its group.

package io.github.skm0078.audora.ui.reader

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.config.ReaderConfig
import io.github.skm0078.audora.core.model.FocusSound
import io.github.skm0078.audora.core.model.FocusSoundOptions
import io.github.skm0078.audora.core.model.Pace
import io.github.skm0078.audora.ui.theme.AudoraTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpeechSheetGroupsTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun text(id: Int) = rule.activity.getString(id)

    private fun assertShown(line: String) =
        assertTrue("\"$line\" is not shown", rule.onAllNodesWithText(line).fetchSemanticsNodes().isNotEmpty())

    private fun showSheet(opensAt: SpeechSpot = SpeechSpot.TOP) = rule.setContent {
        AudoraTheme {
            SpeechSheet(
                speed = 1f,
                pace = Pace.NORMAL,
                config = ReaderConfig(),
                onSpeed = {},
                onPace = {},
                onDismiss = {},
                focusSound = FocusSoundOptions(sound = FocusSound.RAIN),
                opensAt = opensAt,
            )
        }
    }

    @Test
    fun aGroupOpensInPlaceAndClosesWhenAnotherOpens() {
        showSheet()
        val soundLine = text(R.string.row_line_even_loudness)
        rule.onAllNodesWithText(soundLine).assertCountEquals(0)

        rule.onNodeWithText(text(R.string.speech_sound)).performScrollTo().performClick()
        assertShown(soundLine)
        // Still the one sheet: no second sheet with its own title opened over it.
        rule.onAllNodesWithText(text(R.string.speech_sheet_title)).assertCountEquals(1)

        rule.onNodeWithText(text(R.string.speech_cast)).performScrollTo().performClick()
        rule.onAllNodesWithText(soundLine).assertCountEquals(0)
    }

    @Test
    fun theSameHeaderClosesItsGroup() {
        showSheet()
        val header = rule.onNodeWithText(text(R.string.speech_sound))
        header.performScrollTo().performClick()
        header.performClick()
        rule.onAllNodesWithText(text(R.string.row_line_even_loudness)).assertCountEquals(0)
    }

    @Test
    fun theFocusShortcutOpensFocusSoundAlone() {
        showSheet(opensAt = SpeechSpot.FOCUS)
        assertShown(text(R.string.focus_level))
        rule.onAllNodesWithText(text(R.string.row_line_even_loudness)).assertCountEquals(0)
    }
}
