// File: app/src/androidTest/kotlin/io/github/skm0078/audora/ui/settings/SettingAnchorTest.kt
// Purpose: A setting anchor holding several rows stacks them: voice commands' rows were once drawn on top of each
//          other, their texts unreadable. A phone that cannot listen says so instead of offering a switch that does
//          nothing (ADR 2026-11-02-voice-commands-system-recogniser).

package io.github.skm0078.audora.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.skm0078.audora.R
import io.github.skm0078.audora.ui.reader.VoiceCommandsMode
import io.github.skm0078.audora.ui.theme.AudoraTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingAnchorTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun text(id: Int) = context.getString(id)

    /** No on-device recogniser: the switch is off, can't be turned on, and says why, even with "on" saved. */
    @Test
    fun aPhoneThatCannotListenSaysSo() {
        val settings = VoiceCommandsSettings(available = false, mode = VoiceCommandsMode.WHILE_VISIBLE, onChange = {})
        rule.setContent { AudoraTheme { SettingAnchor(SettingKey.VOICE_COMMANDS) { VoiceCommandsItems(settings) } } }

        rule.onNodeWithText(text(R.string.commands_not_available), substring = true).assertExists()
        rule.onNodeWithText(text(R.string.commands_switch_title), substring = true).assertIsNotEnabled()
        rule.onNodeWithText(text(R.string.commands_screen_off_title), substring = true).assertDoesNotExist()
    }

    /** As on the test phone: the switch (on), then the screen-off row, one under the other. */
    @Test
    fun switchAndScreenOffRowStack() {
        val settings = VoiceCommandsSettings(available = true, mode = VoiceCommandsMode.WHILE_VISIBLE, onChange = {})
        rule.setContent { AudoraTheme { SettingAnchor(SettingKey.VOICE_COMMANDS) { VoiceCommandsItems(settings) } } }

        val rows = listOf(
            rule.onNodeWithText(text(R.string.commands_switch_title), substring = true),
            rule.onNodeWithText(text(R.string.commands_screen_off_title), substring = true),
        ).map { it.fetchSemanticsNode().boundsInRoot }
        rows.zipWithNext().forEach { (above, below) ->
            assertTrue("row at ${below.top} overlaps the one ending at ${above.bottom}", below.top >= above.bottom)
        }
    }
}
