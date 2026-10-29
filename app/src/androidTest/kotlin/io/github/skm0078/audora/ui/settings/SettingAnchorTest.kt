// File: app/src/androidTest/kotlin/io/github/skm0078/audora/ui/settings/SettingAnchorTest.kt
// Purpose: A setting anchor holding several rows stacks them: voice commands' model row and its switch were drawn on
//          top of each other, their texts unreadable, before and after the model is installed. The pack's button says
//          which pack it acts on.

package io.github.skm0078.audora.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.skm0078.audora.R
import io.github.skm0078.audora.speech.ocr.OcrModelsState
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

    private fun text(id: Int, vararg args: Any) = context.getString(id, *args)

    @Test
    fun voiceCommandsRowsStackAndTheirDownloadIsNamed() {
        val settings = VoiceCommandsSettings(
            model = OcrModelsState.NotDownloaded(missingBytes = MODEL_BYTES),
            mode = VoiceCommandsMode.OFF,
            onModel = {},
            onChange = {},
        )
        rule.setContent { AudoraTheme { SettingAnchor(SettingKey.VOICE_COMMANDS) { VoiceCommandsItems(settings) } } }

        val download = text(
            R.string.a11y_named_button,
            text(R.string.ocr_models_download_short),
            text(R.string.commands_model_title),
        )
        val model = rule.onNodeWithContentDescription(download).fetchSemanticsNode().boundsInRoot
        val switchRow = rule.onNodeWithText(text(R.string.commands_switch_title), substring = true)
        val switch = switchRow.fetchSemanticsNode().boundsInRoot
        assertTrue("switch at ${switch.top} is above Download's end, ${model.bottom}", switch.top >= model.bottom)
    }

    /** As on the test phone: the model installed, its Delete, then the switch (on) and the screen-off row. */
    @Test
    fun installedModelsDeleteSwitchAndScreenOffRowStack() {
        val settings = VoiceCommandsSettings(
            model = OcrModelsState.Ready(bytes = MODEL_BYTES),
            mode = VoiceCommandsMode.WHILE_VISIBLE,
            onModel = {},
            onChange = {},
        )
        rule.setContent { AudoraTheme { SettingAnchor(SettingKey.VOICE_COMMANDS) { VoiceCommandsItems(settings) } } }

        val delete = text(
            R.string.a11y_named_button,
            text(R.string.ocr_models_delete),
            text(R.string.commands_model_title),
        )
        val rows = listOf(
            rule.onNodeWithContentDescription(delete),
            rule.onNodeWithText(text(R.string.commands_switch_title), substring = true),
            rule.onNodeWithText(text(R.string.commands_screen_off_title), substring = true),
        ).map { it.fetchSemanticsNode().boundsInRoot }
        rows.zipWithNext().forEach { (above, below) ->
            assertTrue("row at ${below.top} overlaps the one ending at ${above.bottom}", below.top >= above.bottom)
        }
    }

    private companion object {
        const val MODEL_BYTES = 40_000_000L
    }
}
