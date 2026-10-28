// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/BookMenuParts.kt
// Purpose: The book menu's two richer parts: the big buttons under the cover and the Save as card whose tiles open
//          in place (ADR 2026-10-28-book-menu-sort-sheet).

package io.github.skm0078.audora.ui.library

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.R
import io.github.skm0078.audora.ui.theme.Dimens
import io.github.skm0078.audora.ui.theme.HelpButton

/**
 * Play, Up next, any [MenuPlace.QUICK] action and Finished, all the same width. Play is filled, as the one thing most
 * people open the menu for; Finished reads as a switch, on once the book is finished.
 */
@Composable
internal fun QuickButtons(choices: List<QuickChoice>, modifier: Modifier = Modifier, onChoose: (QuickChoice) -> Unit) {
    Row(
        modifier.fillMaxWidth().padding(bottom = Dimens.SpaceL),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
    ) {
        choices.forEach { choice -> QuickButton(choice, Modifier.weight(1f)) { onChoose(choice) } }
    }
}

@Composable
private fun QuickButton(choice: QuickChoice, modifier: Modifier, onClick: () -> Unit) {
    val colours = MaterialTheme.colorScheme
    val on = choice is QuickChoice.Finished && choice.finished
    val (container, content) = when {
        choice == QuickChoice.Play -> colours.primary to colours.onPrimary
        on -> colours.primaryContainer to colours.onPrimaryContainer
        else -> colours.secondaryContainer to colours.onSecondaryContainer
    }
    val shape = RoundedCornerShape(QUICK_CORNER)
    val pressable = if (choice is QuickChoice.Finished) {
        Modifier.toggleable(value = on, role = Role.Switch) { onClick() }
    } else {
        Modifier.clickable(role = Role.Button, onClick = onClick)
    }
    Column(
        modifier.clip(shape).then(pressable),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
    ) {
        Surface(
            shape = shape,
            color = container,
            contentColor = content,
            modifier = Modifier.fillMaxWidth().height(QUICK_HEIGHT),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(painterResource(quickIcon(choice)), contentDescription = null)
            }
        }
        Text(
            stringResource(quickLabel(choice)),
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.padding(bottom = Dimens.SpaceXs),
        )
    }
}

@DrawableRes
private fun quickIcon(choice: QuickChoice): Int = when (choice) {
    QuickChoice.Play -> R.drawable.ic_play
    QuickChoice.UpNext -> R.drawable.ic_queue
    is QuickChoice.Action -> actionIcon(choice.action)
    is QuickChoice.Finished -> R.drawable.ic_check
}

// The big buttons are a quarter of the sheet wide: Share, not Share this book (the hint keeps the long name).
@StringRes
private fun quickActionLabel(action: BookAction): Int =
    if (action == BookAction.SHARE_CARD) R.string.menu_quick_share else action.label

private fun quickLabel(choice: QuickChoice): Int = when (choice) {
    QuickChoice.Play -> R.string.menu_play
    QuickChoice.UpNext -> R.string.up_next_title
    is QuickChoice.Action -> quickActionLabel(choice.action)
    is QuickChoice.Finished -> R.string.library_status_finished
}

/**
 * Save as, closed until tapped so the menu stays short; open, its tiles sit two to a row, the same size, with a line
 * each on what comes out. The info button holds the long text for all of them: where each file goes, what it costs.
 */
@Composable
internal fun SaveAsCard(choices: List<SaveChoice>, modifier: Modifier = Modifier, onChoose: (SaveChoice) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val colours = MaterialTheme.colorScheme
    Surface(
        modifier.fillMaxWidth().padding(vertical = Dimens.SpaceXs),
        shape = RoundedCornerShape(CARD_CORNER),
        color = colours.surfaceContainerLow,
        border = BorderStroke(BORDER, colours.outlineVariant),
    ) {
        Column(Modifier.animateContentSize()) {
            SaveAsHeader(choices, open) { open = !open }
            if (open) SaveTiles(choices, onChoose)
        }
    }
}

@Composable
private fun SaveAsHeader(choices: List<SaveChoice>, open: Boolean, onToggle: () -> Unit) {
    val title = stringResource(R.string.menu_save_as)
    val state = stringResource(if (open) R.string.more_options_expanded else R.string.more_options_collapsed)
    val help = choices.map { choice ->
        when (choice) {
            SaveChoice.Audiobook ->
                stringResource(R.string.library_export_audiobook) to stringResource(R.string.menu_hint_audiobook)

            is SaveChoice.Action -> stringResource(choice.action.label) to stringResource(choice.action.hint)
        }
    }.joinToString(HELP_GAP) { (label, hint) -> "$label: $hint" }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = HEADER_HEIGHT)
            .clickable(role = Role.Button, onClick = onToggle)
            .semantics { stateDescription = state }
            .padding(start = Dimens.SpaceS, end = Dimens.SpaceXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceL),
    ) {
        IconSquare(R.drawable.ic_download, MaterialTheme.colorScheme.onSurface)
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        HelpButton(title, help)
        Box(Modifier.size(Dimens.TouchTarget), contentAlignment = Alignment.Center) {
            Icon(
                painterResource(R.drawable.ic_expand_more),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(if (open) HALF_TURN else 0f),
            )
        }
    }
}

@Composable
private fun SaveTiles(choices: List<SaveChoice>, onChoose: (SaveChoice) -> Unit) {
    Column(
        Modifier.padding(start = Dimens.SpaceS, end = Dimens.SpaceS, bottom = Dimens.SpaceS),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
    ) {
        choices.chunked(TILES_PER_ROW).forEach { row ->
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
            ) {
                row.forEach { choice -> SaveTile(choice, Modifier.weight(1f).fillMaxHeight()) { onChoose(choice) } }
                // An odd last tile keeps its width, so the edges stay in line with the row above.
                repeat(TILES_PER_ROW - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun SaveTile(choice: SaveChoice, modifier: Modifier, onClick: () -> Unit) {
    val colours = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = TILE_MIN_HEIGHT),
        shape = RoundedCornerShape(TILE_CORNER),
        color = colours.surface,
        border = BorderStroke(BORDER, colours.outlineVariant),
    ) {
        Column(Modifier.padding(Dimens.SpaceM), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
            val icon = if (choice is SaveChoice.Action) actionIcon(choice.action) else R.drawable.ic_headphones
            Icon(painterResource(icon), contentDescription = null, tint = colours.primary)
            Text(stringResource(saveLabel(choice)), style = MaterialTheme.typography.titleSmall)
            saveLine(choice)?.let { line ->
                Text(stringResource(line), style = MaterialTheme.typography.bodySmall, color = colours.onSurfaceVariant)
            }
        }
    }
}

private const val TILES_PER_ROW = 2
private const val HALF_TURN = 180f
private const val HELP_GAP = "\n\n"
private val QUICK_HEIGHT = 56.dp
private val QUICK_CORNER = 16.dp
private val HEADER_HEIGHT = 56.dp
private val CARD_CORNER = 20.dp
private val TILE_CORNER = 16.dp
private val TILE_MIN_HEIGHT = 92.dp
private val BORDER = 1.dp
