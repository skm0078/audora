// File: app/src/main/kotlin/io/github/skm0078/audora/ui/shell/AppShell.kt
// Purpose: The tabs' frame: Library, Notes and Settings in a bottom bar (a side rail on wide windows), with the mini
//          player above the bar (ADR-036).

package io.github.skm0078.audora.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.R
import io.github.skm0078.audora.ui.AppTab
import io.github.skm0078.audora.ui.theme.WindowWidths

/**
 * The bar and the mini player stay put while the tab content changes, so the listener can always reach play and
 * pause with a thumb. The content gets no system insets of its own at the bottom: the bar takes them. From a medium
 * width up (a phone in landscape, a foldable open, a tablet) the tabs move to a rail at the side, as Material's
 * window size classes ask, and the mini player stays at the bottom of the content.
 */
@Composable
fun AppShell(
    selected: AppTab,
    onSelect: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
    miniPlayer: @Composable () -> Unit = {},
    tabs: List<AppTab> = AppTab.entries,
    // The window's own width, not this box's: the root's cutout padding must not push a 600-640 dp landscape phone
    // back to the bottom bar. Not Configuration.screenWidthDp, which leaves out a side cutout and a side three-button
    // navigation bar before Android 15.
    windowWidth: Dp = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() },
    content: @Composable () -> Unit,
) {
    // Kids mode (plan item 71) has the library only, so no bar or rail at all.
    val showTabs = tabs.size > 1
    Box(modifier.fillMaxSize()) {
        val rail = showTabs && windowWidth >= WindowWidths.MEDIUM
        Row(Modifier.fillMaxSize()) {
            if (rail) TabRail(tabs, selected, onSelect)
            Scaffold(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                contentWindowInsets = WindowInsets(0),
                bottomBar = {
                    val barBelow = showTabs && !rail
                    // Without the bar under it, the mini player keeps clear of the navigation bar itself.
                    Column(if (barBelow) Modifier else Modifier.windowInsetsPadding(miniPlayerInsets(rail))) {
                        miniPlayer()
                        if (barBelow) TabBar(tabs, selected, onSelect)
                    }
                },
            ) { innerPadding ->
                // The rail already keeps clear of the system bars on its side, so the content does not pad again.
                val start = if (rail) Modifier.consumeWindowInsets(START) else Modifier
                Box(Modifier.padding(innerPadding).then(start).fillMaxSize()) { content() }
            }
        }
    }
}

@Composable
private fun TabBar(tabs: List<AppTab>, selected: AppTab, onSelect: (AppTab) -> Unit) {
    NavigationBar(Modifier.testTag(TAB_BAR_TAG)) {
        tabs.forEach { tab ->
            NavigationBarItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                icon = { Icon(painterResource(tabIcon(tab)), contentDescription = null) },
                label = { Text(stringResource(tabLabel(tab))) },
            )
        }
    }
}

/** The rail takes the system bars' top, bottom and start insets itself; the root already pads for a cutout. */
@Composable
private fun TabRail(tabs: List<AppTab>, selected: AppTab, onSelect: (AppTab) -> Unit) {
    NavigationRail(Modifier.testTag(TAB_RAIL_TAG), windowInsets = RAIL_INSETS) {
        Spacer(Modifier.weight(1f))
        tabs.forEach { tab ->
            NavigationRailItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                icon = { Icon(painterResource(tabIcon(tab)), contentDescription = null) },
                label = { Text(stringResource(tabLabel(tab))) },
            )
        }
        Spacer(Modifier.weight(1f))
    }
}

/** For UI tests: which form the tabs took. */
internal const val TAB_BAR_TAG = "tab_bar"
internal const val TAB_RAIL_TAG = "tab_rail"

/** System bars only: the keyboard (in safeDrawing) must not shift the rail's items when search opens. */
private val RAIL_INSETS: WindowInsets
    @Composable get() = WindowInsets.systemBars.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start)

/** Beside the rail only the end side is still open; with no tabs at all (kids mode) both sides are. No keyboard. */
@Composable
private fun miniPlayerInsets(rail: Boolean): WindowInsets = WindowInsets.systemBars.only(
    WindowInsetsSides.Bottom + if (rail) WindowInsetsSides.End else WindowInsetsSides.Horizontal,
)

private val START: WindowInsets
    @Composable get() = WindowInsets.systemBars.only(WindowInsetsSides.Start)

private fun tabIcon(tab: AppTab): Int = when (tab) {
    AppTab.LIBRARY -> R.drawable.ic_library
    AppTab.NOTES -> R.drawable.ic_bookmark
    AppTab.SETTINGS -> R.drawable.ic_settings
}

private fun tabLabel(tab: AppTab): Int = when (tab) {
    AppTab.LIBRARY -> R.string.tab_library
    AppTab.NOTES -> R.string.tab_notes
    AppTab.SETTINGS -> R.string.tab_settings
}
