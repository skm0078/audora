// File: app/src/main/kotlin/io/github/skm0078/audora/ui/shell/PhoneDrawer.kt
// Purpose: The phone's side panel (ADR 2026-10-28-phone-drawer): the library's shelves and collections, what the
//          listener made, then the sidebar's own rows (Your library, the tour, Settings), opened from the library's
//          menu button over the bottom bar.

package io.github.skm0078.audora.ui.shell

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import io.github.skm0078.audora.R
import io.github.skm0078.audora.ui.theme.Dimens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** One of the library's shelves (All books, Reading, Not started, Finished) as the drawer lists it. */
@Immutable
data class DrawerShelf(
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
    val books: Int,
    val selected: Boolean,
    val onOpen: () -> Unit,
)

/**
 * What the drawer lists and where each row goes. [shelves] and [collections] narrow the library; [onMade] and
 * [links] open a place or a sheet. The drawer closes before any of them runs, so a sheet never opens on top of it.
 */
@Immutable
class PhoneDrawer(
    val shelves: List<DrawerShelf>,
    val collections: SidebarShelves,
    val onMade: () -> Unit,
    val links: PanelLinks,
)

/** Opens the drawer, for the library's menu button; null where there is none (tablets, kids mode, previews). */
val LocalOpenDrawer = staticCompositionLocalOf<(() -> Unit)?> { null }

/**
 * The drawer over [content], the bottom bar and the mini player. It opens only from the menu button: a swipe from
 * the edge would fight the sideways rows and Android's back gesture, so swiping only closes it. Back closes it too;
 * the sheet takes the predictive back gesture itself. With no [drawer] (a sidebar, kids mode) the frame stays
 * but holds nothing and has no menu button, so [content] keeps its place in the tree, and its state, either way.
 */
@Composable
internal fun PhoneDrawerFrame(drawer: PhoneDrawer?, content: @Composable () -> Unit) {
    val state = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val open = remember(state, scope) {
        {
            scope.launch { state.open() }
            Unit
        }
    }
    // A drawer left open on a phone that then widens has nothing left to show; close it rather than keep a scrim.
    if (drawer == null &&
        state.targetValue == DrawerValue.Open
    ) {
        LaunchedEffect(state) { state.snapTo(DrawerValue.Closed) }
    }
    ModalNavigationDrawer(
        drawerState = state,
        gesturesEnabled = drawer != null && state.isOpen,
        drawerContent = { drawer?.let { DrawerSheet(it, state, scope) } },
    ) {
        CompositionLocalProvider(LocalOpenDrawer provides open.takeIf { drawer != null }) { content() }
    }
}

@Composable
private fun DrawerSheet(drawer: PhoneDrawer, state: DrawerState, scope: CoroutineScope) {
    val then: (() -> Unit) -> () -> Unit = { action ->
        {
            scope.launch {
                state.close()
                action()
            }
        }
    }
    // A closed drawer stays composed just off screen; its rows leave the semantics tree, so neither TalkBack nor a
    // test finds a Settings or Show me around that cannot be seen.
    val showing = state.currentValue == DrawerValue.Open || state.targetValue == DrawerValue.Open
    val reachable = if (showing) Modifier.testTag(DRAWER_TAG) else Modifier.clearAndSetSemantics {}
    ModalDrawerSheet(drawerState = state) {
        Column(
            reachable.verticalScroll(rememberScrollState()).padding(Dimens.SpaceM),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
        ) {
            AppName()
            PanelHeading(R.string.drawer_shelves)
            drawer.shelves.forEach { shelf ->
                NavigationDrawerItem(
                    label = { Text(stringResource(shelf.label)) },
                    icon = { Icon(painterResource(shelf.icon), contentDescription = null) },
                    badge = { Text(shelf.books.toString(), style = MaterialTheme.typography.labelMedium) },
                    selected = shelf.selected,
                    onClick = then(shelf.onOpen),
                )
            }
            val collections = drawer.collections
            if (collections.shelves.isNotEmpty()) {
                Shelves(collections.copy(onOpen = { id -> then { collections.onOpen(id) }() }), libraryShowing = true)
            }
            PanelDivider()
            PanelHeading(R.string.drawer_made)
            PanelRow(R.string.drawer_made_audio, R.drawable.ic_made, then(drawer.onMade))
            PanelLinkRows(drawer.links, hasCollections = collections.shelves.isNotEmpty(), then)
        }
    }
}

/** For UI tests: the drawer's sheet. */
internal const val DRAWER_TAG = "phone_drawer"
