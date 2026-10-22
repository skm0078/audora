// File: app/src/main/kotlin/io/github/skm0078/audora/ui/settings/SettingsComponents.kt
// Purpose: Settings building blocks: a titled group card, its rows and dividers, so every row looks the same.

package io.github.skm0078.audora.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.skm0078.audora.ui.theme.Dimens

/** A heading over a card of rows. Rows inside use [settingsRowColors] so the card shows through them. */
@Composable
internal fun SettingsGroup(title: String, modifier: Modifier = Modifier, rows: @Composable ColumnScope.() -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = Dimens.SpaceXs).semantics { heading() },
        )
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large) {
            Column(content = rows)
        }
    }
}

/** Transparent rows, so a group card's colour shows through every row. */
@Composable
internal fun settingsRowColors(): ListItemColors = ListItemDefaults.colors(containerColor = Color.Transparent)

/** A row that opens something, or shows a value when [onClick] is null. */
@Composable
internal fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    summary: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = summary,
        trailingContent = trailing,
        colors = settingsRowColors(),
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
    )
}

/** A thin line between rows of one group, inset so it does not touch the card's rounded edges. */
@Composable
internal fun SettingsDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(horizontal = Dimens.SpaceL),
    )
}
