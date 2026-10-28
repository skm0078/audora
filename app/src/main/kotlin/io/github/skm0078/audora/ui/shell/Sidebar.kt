// File: app/src/main/kotlin/io/github/skm0078/audora/ui/shell/Sidebar.kt
// Purpose: The big-tablet form of the four places: a permanent sidebar with the app's name, the places and the
//          listener's collections under them (ADR 2026-10-28-four-places).

package io.github.skm0078.audora.ui.shell

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.R
import io.github.skm0078.audora.ui.AppTab
import io.github.skm0078.audora.ui.firstrun.tourTarget
import io.github.skm0078.audora.ui.theme.Dimens
import io.github.skm0078.audora.ui.theme.ShelfDotColours

/** One of the listener's collections, as the sidebar lists it. */
@Immutable
data class Shelf(val id: String, val name: String, val books: Int)

/**
 * The collections the sidebar lists under the places, which one the library is narrowed to, and how to open one;
 * `null` opens the whole library again.
 */
@Immutable
data class SidebarShelves(
    val shelves: List<Shelf> = emptyList(),
    val selected: String? = null,
    val onOpen: (String?) -> Unit = {},
)

/**
 * The look the listener liked in the drawer mockup, kept open beside the content: nothing to swipe or find. Tapping
 * Library while a collection is open shows the whole library again; tapping a collection opens the library on it.
 */
@Composable
internal fun Sidebar(tabs: List<AppTab>, selected: AppTab, onSelect: (AppTab) -> Unit, shelves: SidebarShelves) {
    PermanentDrawerSheet(
        Modifier.width(Dimens.SidebarWidth).testTag(SIDEBAR_TAG),
        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        windowInsets = RAIL_INSETS,
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(Dimens.SpaceM),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
        ) {
            AppName()
            tabs.forEach { tab ->
                NavigationDrawerItem(
                    label = { Text(stringResource(tabLabel(tab))) },
                    icon = { Icon(painterResource(tabIcon(tab)), contentDescription = null) },
                    selected = tab == selected,
                    onClick = {
                        if (tab == AppTab.LIBRARY && shelves.selected != null) shelves.onOpen(null)
                        onSelect(tab)
                    },
                    modifier = Modifier.tourTarget(tab),
                )
            }
            if (shelves.shelves.isNotEmpty()) Shelves(shelves, libraryShowing = selected == AppTab.LIBRARY)
        }
    }
}

@Composable
internal fun AppName() {
    Row(
        Modifier.padding(horizontal = Dimens.SpaceM, vertical = Dimens.SpaceL),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
    ) {
        Image(
            painterResource(R.drawable.ic_launcher_logo),
            contentDescription = null,
            modifier = Modifier.size(LOGO_SIZE).clip(RoundedCornerShape(Dimens.SpaceS)),
        )
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
    }
}

@Composable
internal fun Shelves(shelves: SidebarShelves, libraryShowing: Boolean) {
    HorizontalDivider(Modifier.padding(horizontal = Dimens.SpaceM, vertical = Dimens.SpaceM))
    Text(
        stringResource(R.string.sidebar_collections),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Dimens.SpaceL, vertical = Dimens.SpaceS).semantics { heading() },
    )
    shelves.shelves.forEach { shelf ->
        NavigationDrawerItem(
            label = { Text(shelf.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            icon = { ShelfDot(shelf.id) },
            badge = { Text(shelf.books.toString(), style = MaterialTheme.typography.labelMedium) },
            selected = libraryShowing && shelf.id == shelves.selected,
            onClick = { shelves.onOpen(shelf.id) },
        )
    }
}

/** A small colour per collection, the same every time, so a shelf is recognised before its name is read. */
@Composable
private fun ShelfDot(id: String) {
    Box(
        Modifier
            .size(SHELF_DOT_SIZE)
            .clip(CircleShape)
            .background(ShelfDotColours[Math.floorMod(id.hashCode(), ShelfDotColours.size)]),
    )
}

private val LOGO_SIZE = 32.dp
private val SHELF_DOT_SIZE = 10.dp
