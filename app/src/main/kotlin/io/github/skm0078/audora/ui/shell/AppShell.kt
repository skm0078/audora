// File: app/src/main/kotlin/io/github/skm0078/audora/ui/shell/AppShell.kt
// Purpose: The tabs' frame: Library, Notes and Settings in a bottom bar, with the mini player above it (ADR-036).

package io.github.skm0078.audora.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import io.github.skm0078.audora.R
import io.github.skm0078.audora.ui.AppTab

/**
 * The bar and the mini player stay put while the tab content changes, so the listener can always reach play and
 * pause with a thumb. The content gets no system insets of its own at the bottom: the bar takes them.
 */
@Composable
fun AppShell(
    selected: AppTab,
    onSelect: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
    miniPlayer: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            Column {
                miniPlayer()
                NavigationBar {
                    AppTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = tab == selected,
                            onClick = { onSelect(tab) },
                            icon = { Icon(painterResource(tabIcon(tab)), contentDescription = null) },
                            label = { Text(stringResource(tabLabel(tab))) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding).fillMaxSize()) { content() }
    }
}

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
