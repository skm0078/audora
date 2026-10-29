// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/ShelfEditor.kt
// Purpose: The Collections sheet's edit page ("Make it yours", touch 9): a collection's name, one of six colours and
//          an emoji, shown in the same sheet so nothing stacks on it; and the badge that shows them everywhere.

package io.github.skm0078.audora.ui.library

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.library.CollectionNames
import io.github.skm0078.audora.core.library.ShelfColour
import io.github.skm0078.audora.core.library.ShelfEmoji
import io.github.skm0078.audora.core.library.ShelfLook
import io.github.skm0078.audora.ui.theme.Dimens
import io.github.skm0078.audora.ui.theme.ShelfColours
import io.github.skm0078.audora.ui.theme.shelfDotColour

/**
 * Name, colour and emoji, with a live badge at the top. Save stays off until the name has a letter. A chosen colour
 * tapped again goes back to the collection's own one; None clears the emoji.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ShelfEditor(
    @StringRes title: Int,
    id: String,
    initialName: String,
    initialLook: ShelfLook,
    onSave: (String, ShelfLook) -> Unit,
    onCancel: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var colour by rememberSaveable { mutableStateOf(initialLook.colour) }
    var emoji by rememberSaveable { mutableStateOf(initialLook.emoji) }
    val cleaned = CollectionNames.clean(name)
    Column(
        Modifier.navigationBarsPadding().padding(horizontal = Dimens.ScreenPadding).padding(bottom = Dimens.SpaceL),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
        ) {
            ShelfBadge(id, ShelfLook(colour, emoji))
            Text(
                stringResource(title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
        }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(CollectionNames.MAX_LENGTH) },
            label = { Text(stringResource(R.string.collections_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Label(R.string.shelf_colour)
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
            ShelfColour.entries.forEach { choice ->
                ColourSwatch(choice, selected = choice == colour) { colour = choice.takeUnless { it == colour } }
            }
        }
        Label(R.string.shelf_emoji)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
            FilterChip(
                selected = emoji == null,
                onClick = { emoji = null },
                label = { Text(stringResource(R.string.shelf_emoji_none)) },
            )
            ShelfEmoji.entries.forEach { choice ->
                val spoken = stringResource(emojiName(choice))
                FilterChip(
                    selected = choice == emoji,
                    onClick = { emoji = choice },
                    label = { Text(choice.glyph) },
                    modifier = Modifier.semantics { contentDescription = spoken },
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS, Alignment.End)) {
            OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.collections_cancel)) }
            Button(onClick = { cleaned?.let { onSave(it, ShelfLook(colour, emoji)) } }, enabled = cleaned != null) {
                Text(stringResource(R.string.collections_save))
            }
        }
    }
}

@Composable
private fun Label(@StringRes text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** A 48 dp target round a filled circle; the chosen one gets a ring and a tick, so colour is never the only sign. */
@Composable
private fun ColourSwatch(colour: ShelfColour, selected: Boolean, onClick: () -> Unit) {
    val spoken = stringResource(colourName(colour))
    val ring = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surface
    Box(
        Modifier
            .size(Dimens.TouchTarget)
            .clip(CircleShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = spoken },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(SWATCH_SIZE)
                .border(RING_WIDTH, ring, CircleShape)
                .padding(RING_WIDTH + RING_GAP)
                .clip(CircleShape)
                .background(ShelfColours.getValue(colour)),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = SWATCH_TICK)
            }
        }
    }
}

/**
 * A collection's dot, in its colour, with its emoji beside it when it has one. Decoration only: the name next to it
 * is what TalkBack reads.
 */
@Composable
internal fun ShelfBadge(id: String, look: ShelfLook, modifier: Modifier = Modifier) {
    Row(
        modifier.clearAndSetSemantics {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
    ) {
        Box(Modifier.size(DOT_SIZE).clip(CircleShape).background(shelfDotColour(id, look.colour)))
        look.emoji?.let { Text(it.glyph, style = MaterialTheme.typography.bodyMedium) }
    }
}

@StringRes
internal fun colourName(colour: ShelfColour): Int = when (colour) {
    ShelfColour.CLAY -> R.string.shelf_colour_clay
    ShelfColour.SAFFRON -> R.string.shelf_colour_saffron
    ShelfColour.FOREST -> R.string.shelf_colour_forest
    ShelfColour.TEAL -> R.string.shelf_colour_teal
    ShelfColour.INDIGO -> R.string.shelf_colour_indigo
    ShelfColour.PLUM -> R.string.shelf_colour_plum
}

@StringRes
internal fun emojiName(emoji: ShelfEmoji): Int = when (emoji) {
    ShelfEmoji.BOOKS -> R.string.shelf_emoji_books
    ShelfEmoji.MOON -> R.string.shelf_emoji_moon
    ShelfEmoji.LAMP -> R.string.shelf_emoji_lamp
    ShelfEmoji.HEART -> R.string.shelf_emoji_heart
    ShelfEmoji.SCHOOL -> R.string.shelf_emoji_school
}

private val SWATCH_SIZE = 36.dp
private val RING_WIDTH = 2.dp
private val RING_GAP = 2.dp
private val DOT_SIZE = 12.dp

// White reads on all six mid tones (each at least 3:1 against it, as an icon needs).
private val SWATCH_TICK = Color.White
