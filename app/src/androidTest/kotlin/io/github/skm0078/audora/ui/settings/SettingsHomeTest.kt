// File: app/src/androidTest/kotlin/io/github/skm0078/audora/ui/settings/SettingsHomeTest.kt
// Purpose: Settings asked for by name (the phone drawer's row) opens Settings' own page, even when Advanced was the
//          page left open (ADR 2026-10-28-phone-drawer); a found setting left mid-flash is not shown again.

package io.github.skm0078.audora.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.skm0078.audora.R
import io.github.skm0078.audora.ui.diagnostics.ShareLogsState
import io.github.skm0078.audora.ui.theme.AudoraTheme
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsHomeTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun heading(id: Int) = hasText(rule.activity.getString(id)) and
        SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)

    private var key by mutableStateOf<SettingKey?>(SettingKey.SHARE_LOGS)
    private var home by mutableStateOf(false)
    private var showing by mutableStateOf(true)

    private fun showSettings() = rule.setContent {
        AudoraTheme {
            if (showing) {
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
                    focus = SettingFocus(key, home = home) {
                        key = null
                        home = false
                    },
                )
            }
        }
    }

    @Test
    fun settingsByNameOpensItsOwnPageNotTheOneLeftOpen() {
        // A found Advanced setting leaves Advanced open, as a search would.
        showSettings()
        rule.waitUntil(TIMEOUT_MS) { key == null }
        rule.onNode(heading(R.string.settings_advanced_title)).assertExists()

        rule.runOnIdle { home = true }
        rule.waitUntil(TIMEOUT_MS) { !home }
        rule.onNode(heading(R.string.settings_title)).assertExists()
        assertTrue(rule.onAllNodes(heading(R.string.settings_advanced_title)).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun aFoundSettingLeftMidFlashIsNotShownAgain() {
        rule.mainClock.autoAdvance = false
        showSettings()
        rule.mainClock.advanceTimeBy(MID_FLASH_MS)
        rule.onNodeWithTag(FOCUSED_TAG).assertExists()
        // Back to the search before the flash ends, as the release journey does.
        showing = false
        rule.mainClock.advanceTimeBy(MID_FLASH_MS)
        rule.runOnIdle { assertNull(key) }
    }

    private companion object {
        const val MID_FLASH_MS = 400L
        const val TIMEOUT_MS = 5_000L
    }
}
