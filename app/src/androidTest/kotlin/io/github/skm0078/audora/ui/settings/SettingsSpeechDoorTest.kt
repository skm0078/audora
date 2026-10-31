// File: app/src/androidTest/kotlin/io/github/skm0078/audora/ui/settings/SettingsSpeechDoorTest.kt
// Purpose: Settings' Listening group has a door to the player's Voice and speed sheet: it opens the book read last,
//          and with no book yet it says to open one instead of doing nothing (ADR 2026-10-29-find-player-options).

package io.github.skm0078.audora.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.skm0078.audora.R
import io.github.skm0078.audora.ui.diagnostics.ShareLogsState
import io.github.skm0078.audora.ui.theme.AudoraTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsSpeechDoorTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun text(id: Int) = rule.activity.getString(id)

    private fun showSettings(onOpenSpeech: (() -> Unit)?) = rule.setContent {
        AudoraTheme {
            SettingsScreen(
                shareState = ShareLogsState.Idle,
                batteryExempt = true,
                versionName = "test",
                showDebugTools = false,
                onOpenBatterySetup = {},
                onOpenVoices = {},
                onOpenLicences = {},
                onShareLogs = {},
                onSimulateReadingSession = {},
                onOpenSpeech = onOpenSpeech,
            )
        }
    }

    @Test
    fun theRowOpensTheBookReadLast() {
        var opened = 0
        showSettings { opened++ }
        rule.onNodeWithText(text(R.string.settings_speech_body)).assertExists()
        rule.onNode(hasText(text(R.string.speech_sheet_title)) and hasClickAction())
            .performScrollTo()
            .performClick()
        rule.runOnIdle { assertEquals(1, opened) }
    }

    @Test
    fun withNoBookTheRowSaysToOpenOne() {
        showSettings(null)
        rule.onNodeWithText(text(R.string.settings_speech_no_book)).performScrollTo().assertExists()
        rule.onNodeWithText(text(R.string.speech_sheet_title), useUnmergedTree = true).assertHasNoClickAction()
    }
}
