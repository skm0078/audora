// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/TextLookSheet.kt
// Purpose: The text view's type settings (plan item 15): font, size, line spacing and running paragraphs, with a
//          sample line that shows each change at once.

package io.github.skm0078.audora.ui.reader

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import io.github.skm0078.audora.R
import io.github.skm0078.audora.ui.theme.Dimens
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TextLookSheet(look: ReadingLook, onLookChange: (ReadingLook) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.SpaceXl)
                .padding(bottom = Dimens.SpaceXl),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceL),
        ) {
            Text(
                stringResource(R.string.text_look_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(R.string.text_look_sample), style = ReadingTextStyles.forLook(look))
            ChoiceRow(R.string.text_look_font, ReadingFont.entries, look.font, ReadingFont::label) {
                onLookChange(look.copy(font = it))
            }
            SizeSlider(look, onLookChange)
            ChoiceRow(R.string.text_look_spacing, LineSpacing.entries, look.spacing, LineSpacing::label) {
                onLookChange(look.copy(spacing = it))
            }
            ReflowRow(look.reflow) { onLookChange(look.copy(reflow = it)) }
        }
    }
}

@Composable
private fun <T> ChoiceRow(
    @StringRes title: Int,
    choices: List<T>,
    selected: T,
    label: (T) -> Int,
    onSelect: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
        Text(stringResource(title), style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
            choices.forEach { choice ->
                FilterChip(
                    selected = choice == selected,
                    onClick = { onSelect(choice) },
                    label = { Text(stringResource(label(choice))) },
                )
            }
        }
    }
}

@Composable
private fun SizeSlider(look: ReadingLook, onLookChange: (ReadingLook) -> Unit) {
    val range = ReadingLook.TEXT_SCALES
    val percent = stringResource(R.string.text_look_size_value, (look.textScale * PERCENT).roundToInt())
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
        Text(stringResource(R.string.text_look_size), style = MaterialTheme.typography.labelLarge)
        Slider(
            value = look.textScale,
            onValueChange = { onLookChange(look.copy(textScale = snapScale(it))) },
            valueRange = range,
            modifier = Modifier.semantics { stateDescription = percent },
        )
    }
}

@Composable
private fun ReflowRow(on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = on, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = Dimens.SpaceS),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.text_look_reflow), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.text_look_reflow_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = on, onCheckedChange = null)
    }
}

/** The text size on a [SCALE_STEP] grid, within [ReadingLook.TEXT_SCALES], so saved values stay tidy. */
internal fun snapScale(scale: Float): Float {
    val range = ReadingLook.TEXT_SCALES
    val stepped = (scale / SCALE_STEP).roundToInt() * SCALE_STEP
    return stepped.coerceIn(range.start, range.endInclusive)
}

private val ReadingFont.label: Int
    get() = when (this) {
        ReadingFont.SERIF -> R.string.text_look_font_serif
        ReadingFont.SANS -> R.string.text_look_font_sans
    }

private val LineSpacing.label: Int
    get() = when (this) {
        LineSpacing.COMPACT -> R.string.text_look_spacing_compact
        LineSpacing.NORMAL -> R.string.text_look_spacing_normal
        LineSpacing.RELAXED -> R.string.text_look_spacing_relaxed
    }

private const val PERCENT = 100
private const val SCALE_STEP = 0.05f
