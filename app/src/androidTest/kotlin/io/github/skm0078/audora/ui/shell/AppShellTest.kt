// File: app/src/androidTest/kotlin/io/github/skm0078/audora/ui/shell/AppShellTest.kt
// Purpose: The tabs take the form the window width asks for: a bottom bar on a phone, a rail from 600 dp, neither
//          in kids mode.

package io.github.skm0078.audora.ui.shell

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.skm0078.audora.ui.AppTab
import io.github.skm0078.audora.ui.theme.AudoraTheme
import io.github.skm0078.audora.ui.theme.WindowWidths
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppShellTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private var width by mutableStateOf(PHONE)
    private var tabs by mutableStateOf<List<AppTab>>(AppTab.entries)

    private fun showShell() = rule.setContent {
        AudoraTheme {
            Box(Modifier.requiredWidth(width)) {
                AppShell(selected = AppTab.LIBRARY, onSelect = {}, tabs = tabs, windowWidth = width) {}
            }
        }
    }

    @Test
    fun aPhoneHasTheBottomBar() {
        showShell()
        rule.onNodeWithTag(TAB_BAR_TAG).assertExists()
        rule.onNodeWithTag(TAB_RAIL_TAG).assertDoesNotExist()
    }

    @Test
    fun aWideWindowHasTheRail() {
        showShell()
        rule.runOnIdle { width = TABLET }
        rule.onNodeWithTag(TAB_RAIL_TAG).assertExists()
        rule.onNodeWithTag(TAB_BAR_TAG).assertDoesNotExist()
    }

    @Test
    fun theRailStartsAtExactlyTheMediumWidth() {
        showShell()
        rule.runOnIdle { width = WindowWidths.MEDIUM - 1.dp }
        rule.onNodeWithTag(TAB_BAR_TAG).assertExists()
        rule.runOnIdle { width = WindowWidths.MEDIUM }
        rule.onNodeWithTag(TAB_RAIL_TAG).assertExists()
        rule.onNodeWithTag(TAB_BAR_TAG).assertDoesNotExist()
    }

    @Test
    fun byDefaultTheWindowDecidesNotTheBox() {
        var wideWindow = false
        rule.setContent {
            AudoraTheme {
                val window = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
                wideWindow = window >= WindowWidths.MEDIUM
                // A box of the opposite width: only the window's width can give the expected form.
                Box(Modifier.requiredWidth(if (wideWindow) PHONE else TABLET)) {
                    AppShell(selected = AppTab.LIBRARY, onSelect = {}) {}
                }
            }
        }
        rule.onNodeWithTag(if (wideWindow) TAB_RAIL_TAG else TAB_BAR_TAG).assertExists()
    }

    @Test
    fun kidsModeHasNeither() {
        showShell()
        rule.runOnIdle {
            tabs = listOf(AppTab.LIBRARY)
            width = TABLET
        }
        rule.onNodeWithTag(TAB_RAIL_TAG).assertDoesNotExist()
        rule.onNodeWithTag(TAB_BAR_TAG).assertDoesNotExist()
    }

    private companion object {
        val PHONE: Dp = 360.dp
        val TABLET: Dp = 700.dp
    }
}
