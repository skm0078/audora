// File: app/src/androidTest/kotlin/io/github/skm0078/audora/ui/shell/PhoneDrawerTest.kt
// Purpose: On a phone, and beside a rail, the menu button opens the side drawer with the shelves, collections, Made,
//          Your library, the tour and Settings, and a row closes the drawer before it acts; a sidebar and kids mode
//          have none (ADR 2026-10-28-phone-drawer).

package io.github.skm0078.audora.ui.shell

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.skm0078.audora.R
import io.github.skm0078.audora.ui.AppTab
import io.github.skm0078.audora.ui.theme.AudoraTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhoneDrawerTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val opened = mutableListOf<String>()

    private fun text(id: Int): String = rule.activity.getString(id)

    private val drawer = PhoneDrawer(
        shelves = listOf(
            DrawerShelf(R.string.library_filter_all, R.drawable.ic_library, 6, selected = true) { opened += "all" },
            DrawerShelf(R.string.library_filter_finished, R.drawable.ic_check, 1, selected = false) {
                opened += "finished"
            },
        ),
        collections = SidebarShelves(listOf(Shelf("c1", "For Ma", 3))) { opened += "collection $it" },
        onMade = { opened += "made" },
        links = PanelLinks(
            onMakeItYours = { opened += "make it yours" },
            onEditCollections = { opened += "collections" },
            onListeningStats = { opened += "stats" },
            onTour = { opened += "tour" },
            onSettings = { opened += "settings" },
        ),
    )

    private fun show(width: Dp, tabs: List<AppTab> = AppTab.entries) = rule.setContent {
        AudoraTheme {
            Box(Modifier.requiredWidth(width)) {
                AppShell(selected = AppTab.LIBRARY, onSelect = {}, tabs = tabs, drawer = drawer, windowWidth = width) {
                    // Stands in for the library's top bar, which shows the button only where a drawer exists.
                    LocalOpenDrawer.current?.let { open -> IconButton(onClick = open) { Text(MENU) } }
                }
            }
        }
    }

    private fun openDrawer() {
        rule.onNodeWithText(MENU).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(DRAWER_TAG).assertExists()
    }

    @Test
    fun aClosedDrawerHidesItsRows() {
        show(PHONE)
        rule.onNodeWithTag(DRAWER_TAG).assertDoesNotExist()
        rule.onNode(hasText(text(R.string.settings_title))).assertDoesNotExist()
    }

    @Test
    fun theMenuButtonOpensEveryRow() {
        show(PHONE)
        openDrawer()
        listOf(
            R.string.drawer_shelves,
            R.string.library_filter_all,
            R.string.sidebar_collections,
            R.string.drawer_made_audio,
            R.string.drawer_your_library,
            R.string.library_make_it_yours,
            R.string.collections_edit,
            R.string.library_listening_stats,
            R.string.settings_tour_title,
            R.string.settings_title,
        ).forEach { rule.onNode(hasText(text(it))).performScrollTo().assertExists() }
        rule.onNode(hasText("For Ma")).performScrollTo().assertExists()
    }

    @Test
    fun aRowClosesTheDrawerAndThenActs() {
        show(PHONE)
        openDrawer()
        rule.onNode(hasText(text(R.string.settings_title))).performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(DRAWER_TAG).assertDoesNotExist()
        assertEquals(listOf("settings"), opened)
    }

    @Test
    fun theLibrarysOwnSheetsOpenFromTheDrawer() {
        show(PHONE)
        for (row in listOf(R.string.library_make_it_yours, R.string.collections_edit)) {
            openDrawer()
            rule.onNode(hasText(text(row))).performScrollTo().performClick()
            rule.waitForIdle()
            rule.onNodeWithTag(DRAWER_TAG).assertDoesNotExist()
        }
        assertEquals(listOf("make it yours", "collections"), opened)
    }

    @Test
    fun aCollectionOpensFromTheDrawer() {
        show(PHONE)
        openDrawer()
        rule.onNode(hasText("For Ma")).performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(listOf("collection c1"), opened)
    }

    @Test
    fun aRailStillHasTheDrawer() {
        show(TABLET)
        openDrawer()
        rule.onNode(hasText(text(R.string.library_make_it_yours))).performScrollTo().assertExists()
    }

    @Test
    fun aSidebarHasNoDrawer() {
        show(BIG_TABLET)
        rule.onNodeWithText(MENU).assertDoesNotExist()
        assertTrue(opened.isEmpty())
    }

    @Test
    fun turningThePhoneSidewaysKeepsThePlaceAsItWas() {
        var width by mutableStateOf(PHONE)
        rule.setContent {
            AudoraTheme {
                Box(Modifier.requiredWidth(width)) {
                    AppShell(selected = AppTab.LIBRARY, onSelect = {}, drawer = drawer, windowWidth = width) {
                        var taps by remember { mutableIntStateOf(0) }
                        Button(onClick = { taps++ }) { Text("taps $taps") }
                    }
                }
            }
        }
        rule.onNodeWithText("taps 0").performClick()
        rule.runOnIdle { width = BIG_TABLET }
        rule.onNodeWithText("taps 1").assertExists()
        rule.runOnIdle { width = PHONE }
        rule.onNodeWithText("taps 1").assertExists()
    }

    @Test
    fun kidsModeHasNoDrawer() {
        show(PHONE, tabs = listOf(AppTab.LIBRARY))
        rule.onNodeWithText(MENU).assertDoesNotExist()
    }

    private companion object {
        const val MENU = "menu"
        val PHONE = 360.dp
        val TABLET = 700.dp
        val BIG_TABLET = 900.dp
    }
}
