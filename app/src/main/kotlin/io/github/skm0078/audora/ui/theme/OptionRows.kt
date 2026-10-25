// File: app/src/main/kotlin/io/github/skm0078/audora/ui/theme/OptionRows.kt
// Purpose: One look for every option in Settings and the reader's sheets (ADR-062): titled cards of rows, each row a
//          real control (a switch, a value that opens a choice, a slider with its value) with at most one short line
//          under it. Longer help waits behind an info button, so the screen shows controls, not paragraphs.

package io.github.skm0078.audora.ui.theme

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import io.github.skm0078.audora.R

/**
 * A heading (none when [title] is null) over a card of rows. The card is a step lighter than the screen or sheet
 * behind it, so a group reads as one block of controls. Rows inside use [optionRowColors] so the card shows through.
 */
@Composable
fun OptionGroup(title: String?, modifier: Modifier = Modifier, rows: @Composable ColumnScope.() -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
        title?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = Dimens.SpaceXs, top = Dimens.SpaceXs).semantics { heading() },
            )
        }
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large) {
            Column(content = rows)
        }
    }
}

/** A thin line between rows of one group, inset so it does not touch the card's rounded edges. */
@Composable
fun OptionDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(horizontal = Dimens.SpaceL),
    )
}

/** Transparent rows, so a group card's colour shows through every row. */
@Composable
fun optionRowColors(): ListItemColors = ListItemDefaults.colors(containerColor = Color.Transparent)

/**
 * A switch row. The whole row toggles; the switch only shows the state (no onCheckedChange), so it is not a second,
 * smaller target, and TalkBack reads the title, the subtitle and "on/off" together. [help] opens behind its own
 * button, which stays a separate target.
 */
@Composable
fun SwitchOption(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    help: String? = null,
    enabled: Boolean = true,
    subtitleIsError: Boolean = false,
) {
    ListItem(
        headlineContent = { OptionTitle(title, enabled) },
        supportingContent = subtitle?.let { { OptionSubtitle(it, subtitleIsError) } },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                help?.let { HelpButton(title, it) }
                Switch(checked = checked, onCheckedChange = null, enabled = enabled)
            }
        },
        colors = optionRowColors(),
        modifier = modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onChange,
        ),
    )
}

/**
 * A row that opens something (a screen, a dialog), or only shows [value] when [onClick] is null. The current value
 * sits at the end, so the row says what is set without being opened.
 */
@Composable
fun LinkOption(
    title: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    help: String? = null,
    @DrawableRes leadingIcon: Int? = null,
    enabled: Boolean = true,
    subtitleIsError: Boolean = false,
) {
    val hasTrailing = value != null || help != null || onClick != null
    ListItem(
        // The value shares the title's line only when both fit whole; a long value never squeezes the title.
        headlineContent = {
            TitleAndTrailing(
                title = { OptionTitle(title, enabled) },
                trailing = if (hasTrailing) {
                    { OptionTrailing(value, help, title, showChevron = onClick != null) }
                } else {
                    null
                },
            )
        },
        supportingContent = subtitle?.let { { OptionSubtitle(it, subtitleIsError) } },
        leadingContent = leadingIcon?.let { { Icon(painterResource(it), contentDescription = null) } },
        colors = optionRowColors(),
        modifier = if (onClick != null) {
            modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        } else {
            modifier
        },
    )
}

/**
 * One of a few choices, shown as a row with the current one at its end; a tap opens the choices as radio buttons, and
 * picking one closes them. Picking the current one again changes nothing.
 */
@Composable
fun <T> ChoiceOption(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onPick: (T) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    help: String? = null,
    enabled: Boolean = true,
) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    LinkOption(
        title,
        onClick = { choosing = true },
        modifier = modifier,
        subtitle = subtitle,
        value = label(selected),
        help = help,
        enabled = enabled,
    )
    if (choosing) {
        ChoiceDialog(title, options, selected, label, onDismiss = { choosing = false }) { option ->
            choosing = false
            if (option != selected) onPick(option)
        }
    }
}

/**
 * A row whose control sits under its title: a slider, a segmented button or chips. [value] is what is set now ("1.25×",
 * "40 percent"), at the title's end, so the control's state reads without counting ticks.
 */
@Composable
fun OptionBlock(
    title: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    subtitle: String? = null,
    help: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.SpaceL, vertical = Dimens.SpaceS),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
    ) {
        TitleAndTrailing(
            title = {
                Column {
                    Text(title, style = MaterialTheme.typography.bodyLarge)
                    subtitle?.let { OptionSubtitle(it) }
                }
            },
            trailing = if (value != null || help != null) {
                { OptionTrailing(value, help, title, showChevron = false) }
            } else {
                null
            },
            modifier = Modifier.heightIn(min = Dimens.TouchTarget),
        )
        content()
    }
}

@Composable
private fun OptionTitle(title: String, enabled: Boolean) {
    Text(
        title,
        color = if (enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_ALPHA),
    )
}

/** One short line under a title. Two lines at most, so a longer translation wraps once instead of being cut. */
@Composable
private fun OptionSubtitle(text: String, isError: Boolean = false) {
    Text(
        text,
        maxLines = SUBTITLE_LINES,
        overflow = TextOverflow.Ellipsis,
        color = if (isError) MaterialTheme.colorScheme.error else Color.Unspecified,
    )
}

@Composable
private fun OptionTrailing(value: String?, help: String?, title: String, showChevron: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        value?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = VALUE_LINES,
                overflow = TextOverflow.Ellipsis,
                // Shrinks before the buttons after it, so help and the chevron always show.
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        help?.let { HelpButton(title, it) }
        if (showChevron) {
            Icon(
                painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Material's opacity for disabled text. */
private const val DISABLED_ALPHA = 0.38f

private const val SUBTITLE_LINES = 2

/** A value on its own line under the title may wrap once ("English (US), Kokoro Michael, male (premium)"). */
private const val VALUE_LINES = 2
