// File: app/src/androidTest/kotlin/io/github/skm0078/audora/ui/reader/SleepOptionsDialogTest.kt
// Purpose: The sleep options dialog keeps choices not saved yet across a rotation (saved instance state), so turning
//          the phone does not undo what the listener just switched.

package io.github.skm0078.audora.ui.reader

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.config.ReaderConfig
import io.github.skm0078.audora.core.model.SleepOptions
import io.github.skm0078.audora.ui.theme.AudoraTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SleepOptionsDialogTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun aChoiceNotSavedYetSurvivesARotation() {
        val restoration = StateRestorationTester(rule)
        restoration.setContent {
            AudoraTheme {
                SleepOptionsDialog(SleepOptions(fadeOut = true), ReaderConfig(), onSave = {}, onDismiss = {})
            }
        }
        val fade = hasText(rule.activity.getString(R.string.sleep_options_fade)) and isToggleable()
        rule.onNode(fade).assertIsOn().performClick()
        rule.onNode(fade).assertIsOff()

        restoration.emulateSavedInstanceStateRestore()

        rule.onNode(fade).assertIsOff()
    }
}
