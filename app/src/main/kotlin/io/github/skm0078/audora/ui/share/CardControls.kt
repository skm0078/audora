// File: app/src/main/kotlin/io/github/skm0078/audora/ui/share/CardControls.kt
// Purpose: The card screen's choices: picture size chips, background swatches and the dedication line.

package io.github.skm0078.audora.ui.share

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.config.ShareCardConfig
import io.github.skm0078.audora.core.text.TextCuts
import io.github.skm0078.audora.ui.theme.Dimens

/** Square, story, post: one row that scrolls sideways when large text makes it wider than the screen. */
@Composable
internal fun ShapeChips(selected: CardShape, onShape: (CardShape) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
        Text(stringResource(R.string.share_card_shape), style = MaterialTheme.typography.titleSmall)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
        ) {
            CardShape.entries.forEach { shape ->
                FilterChip(
                    selected = shape == selected,
                    onClick = { onShape(shape) },
                    label = { Text(stringResource(shape.label)) },
                )
            }
        }
    }
}

/**
 * One round swatch per background, the selected one ringed. The cover's shows only when the book has a cover;
 * "Your own picture" opens the photo picker (no storage permission) and says to use a picture one may share.
 */
@Composable
internal fun LookSwatches(
    selected: CardLook,
    hasCover: Boolean,
    onLook: (CardLook) -> Unit,
    onPickPicture: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
        Text(stringResource(R.string.share_card_look), style = MaterialTheme.typography.titleSmall)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
        ) {
            CardLook.entries.filter { it != CardLook.COVER || hasCover }.forEach { look ->
                Swatch(look, look == selected) {
                    if (look == CardLook.PICTURE) onPickPicture() else onLook(look)
                }
            }
        }
        if (selected == CardLook.PICTURE) {
            Text(
                stringResource(R.string.share_card_picture_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.share_card_picture_drag),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Swatch(look: CardLook, selected: Boolean, onClick: () -> Unit) {
    val label = stringResource(look.label)
    val ring = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Box(
        Modifier
            .size(Dimens.TouchTarget)
            .border(if (selected) RING_SELECTED else RING, ring, CircleShape)
            .padding(RING_GAP)
            .clip(CircleShape)
            .background(look.brush)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        val icon = when (look) {
            CardLook.PICTURE -> R.drawable.ic_add
            CardLook.COVER -> R.drawable.ic_library
            else -> null
        }
        icon?.let { Icon(painterResource(it), contentDescription = null, tint = look.ink) }
    }
}

/** "For Ma": printed small under the credit, never saved anywhere but on the picture. */
@Composable
internal fun DedicationField(value: String, onValue: (String) -> Unit) {
    val max = ShareCardConfig().maxDedicationChars
    OutlinedTextField(
        value = value,
        onValueChange = { onValue(TextCuts.take(it, max)) },
        label = { Text(stringResource(R.string.share_card_dedication)) },
        placeholder = { Text(stringResource(R.string.share_card_dedication_hint)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

private val RING = 1.dp
private val RING_SELECTED = 3.dp
private val RING_GAP = 4.dp
