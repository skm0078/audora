// File: app/src/androidTest/kotlin/io/github/skm0078/audora/ui/settings/SettingsHomeTest.kt
// Purpose: Settings asked for by name (the phone drawer's row) opens Settings' own page, even when Advanced was the
//          page left open (ADR 2026-10-28-phone-drawer).

package io.github.skm0078.audora.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.skm0078.audora.R
import io.github.skm0078.audora.ui.diagnostics.ShareLogsState
import io.github.skm0078.audora.ui.theme.AudoraTheme
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

    @Test
    fun settingsByNameOpensItsOwnPageNotTheOneLeftOpen() {
        // A found Advanced setting leaves Advanced open, as a search would.
        var key by mutableStateOf<SettingKey?>(SettingKey.PAUSES_IN_SCANS)
        var home by mutableStateOf(false)
        rule.setContent {
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
                    focus = SettingFocus(key, home = home) {
                        key = null
                        home = false
                    },
                )
            }
        }
        rule.waitUntil(TIMEOUT_MS) { key == null }
        rule.onNode(heading(R.string.settings_advanced_title)).assertExists()

        rule.runOnIdle { home = true }
        rule.waitUntil(TIMEOUT_MS) { !home }
        rule.onNode(heading(R.string.settings_title)).assertExists()
        assertTrue(rule.onAllNodes(heading(R.string.settings_advanced_title)).fetchSemanticsNodes().isEmpty())
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
