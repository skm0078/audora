// File: app/src/androidTest/kotlin/io/github/skm0078/audora/ui/shell/AppShellTest.kt
// Purpose: The four places take the form the window width asks for: a bottom bar on a phone, a rail from 600 dp, a
//          sidebar with the collections from 840 dp, none in kids mode; with screenshots of the phone and tablet forms.

package io.github.skm0078.audora.ui.shell

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.skm0078.audora.R
import io.github.skm0078.audora.ui.AppTab
import io.github.skm0078.audora.ui.Screens
import io.github.skm0078.audora.ui.theme.AudoraTheme
import io.github.skm0078.audora.ui.theme.WindowWidths
import org.junit.Assert.assertEquals
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

    @Test
    fun aBigTabletHasTheSidebarWithTheFourPlacesAndTheCollections() {
        val selected = mutableListOf<AppTab>()
        val opened = mutableListOf<String?>()
        var shelf by mutableStateOf<String?>(null)
        rule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(BIG_TABLET)) {
                AudoraTheme {
                    AppShell(
                        selected = AppTab.LIBRARY,
                        onSelect = { selected += it },
                        shelves = SidebarShelves(SHELVES, shelf) { opened += it },
                        windowWidth = BIG_TABLET.width,
                    ) {}
                }
            }
        }
        rule.onNodeWithTag(SIDEBAR_TAG).assertExists()
        rule.onNodeWithTag(TAB_RAIL_TAG).assertDoesNotExist()
        rule.onNodeWithTag(TAB_BAR_TAG).assertDoesNotExist()
        PLACES.forEach { rule.onNodeWithText(text(it)).assertExists() }
        Screens.save("places-tablet-sidebar", rule.onRoot().captureToImage().asAndroidBitmap())

        rule.onNodeWithText("For school").performClick()
        rule.runOnIdle { assertEquals(listOf<String?>("c1"), opened) }
        // Library with a collection open shows the whole library again.
        rule.runOnIdle { shelf = "c1" }
        rule.onNodeWithText(text(R.string.tab_library)).performClick()
        rule.runOnIdle {
            assertEquals(listOf("c1", null), opened)
            assertEquals(listOf(AppTab.LIBRARY), selected)
        }
    }

    @Test
    fun theSidebarHasTheDrawersOwnRowsUnderTheCollections() {
        val opened = mutableListOf<String>()
        val links = PanelLinks(
            onLibraryAction = { opened += it.name },
            onTour = { opened += "tour" },
            onSettings = { opened += "settings" },
        )
        val drawer = PhoneDrawer(emptyList(), SidebarShelves(SHELVES), onMade = {}, links = links)
        rule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(BIG_TABLET)) {
                AudoraTheme {
                    AppShell(
                        selected = AppTab.LIBRARY,
                        onSelect = {},
                        shelves = drawer.collections,
                        drawer = drawer,
                        windowWidth = BIG_TABLET.width,
                    ) {}
                }
            }
        }
        for (row in listOf(R.string.library_make_it_yours, R.string.collections_edit, R.string.settings_title)) {
            rule.onNodeWithText(text(row)).performScrollTo().performClick()
        }
        rule.runOnIdle { assertEquals(listOf("MAKE_IT_YOURS", "EDIT_COLLECTIONS", "settings"), opened) }
    }

    @Test
    fun everyFormNamesTheSameFourPlacesInTheSameOrder() {
        showShell()
        for (form in listOf(PHONE, TABLET)) {
            rule.runOnIdle { width = form }
            val positions = PLACES.map { rule.onNodeWithText(text(it)).fetchSemanticsNode().positionInRoot }
            // Left to right in the bar, top to bottom in the rail.
            val along = positions.map { if (form == PHONE) it.x else it.y }
            assertEquals("order at $form", along.sorted(), along)
        }
        rule.runOnIdle { width = PHONE }
        Screens.save("places-phone-bar", rule.onRoot().captureToImage().asAndroidBitmap())
    }

    private fun text(id: Int): String = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private companion object {
        val PHONE: Dp = 360.dp
        val TABLET: Dp = 700.dp
        val BIG_TABLET = DpSize(1280.dp, 800.dp)
        val PLACES = listOf(R.string.tab_library, R.string.tab_notes, R.string.tab_made, R.string.tab_you)
        val SHELVES = listOf(Shelf("c1", "For school", 9), Shelf("c2", "Bedtime", 3))
    }
}
