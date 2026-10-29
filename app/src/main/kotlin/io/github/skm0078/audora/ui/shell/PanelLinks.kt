// File: app/src/main/kotlin/io/github/skm0078/audora/ui/shell/PanelLinks.kt
// Purpose: The rows the tablet sidebar and the phone drawer share under the places and collections, so the big
//          things to look for sit in one place in both (ADR 2026-10-28-phone-drawer).

package io.github.skm0078.audora.ui.shell

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.skm0078.audora.R
import io.github.skm0078.audora.ui.theme.Dimens

/**
 * What the side panel opens besides the places: the library's own sheets and the listening stats (each also in the
 * ⋮ menu or on a chip, so neither way hides them), then the tour and Settings.
 */
@Immutable
class PanelLinks(
    val onMakeItYours: () -> Unit,
    val onEditCollections: () -> Unit,
    val onListeningStats: () -> Unit,
    val onTour: () -> Unit,
    val onSettings: () -> Unit,
)

/**
 * The shared rows: Your library, then the tour and Settings. [then] wraps each action, so the drawer can close
 * before it runs; [hasCollections] names the collections row as the library's chip does.
 */
@Composable
internal fun PanelLinkRows(links: PanelLinks, hasCollections: Boolean, then: (() -> Unit) -> () -> Unit = { it }) {
    PanelDivider()
    PanelHeading(R.string.drawer_your_library)
    PanelRow(R.string.library_make_it_yours, R.drawable.ic_palette, then(links.onMakeItYours))
    val collections = if (hasCollections) R.string.collections_edit else R.string.collections_new
    PanelRow(collections, R.drawable.ic_edit, then(links.onEditCollections))
    PanelRow(R.string.library_listening_stats, R.drawable.ic_bar_chart, then(links.onListeningStats))
    PanelDivider()
    PanelRow(R.string.settings_tour_title, R.drawable.ic_explore, then(links.onTour))
    PanelRow(R.string.settings_title, R.drawable.ic_settings, then(links.onSettings))
}

@Composable
internal fun PanelDivider() {
    HorizontalDivider(Modifier.padding(horizontal = Dimens.SpaceM, vertical = Dimens.SpaceM))
}

@Composable
internal fun PanelHeading(@StringRes text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Dimens.SpaceL, vertical = Dimens.SpaceS).semantics { heading() },
    )
}

@Composable
internal fun PanelRow(@StringRes label: Int, @DrawableRes icon: Int, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(stringResource(label)) },
        icon = { Icon(painterResource(icon), contentDescription = null) },
        selected = false,
        onClick = onClick,
    )
}
