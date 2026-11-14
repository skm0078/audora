// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/TextSize.kt
// Purpose: The text view's size control, one block shared by the reader's Text size sheet, Page and text, and
//          Settings: smaller and larger buttons either side of a slider, the size in percent, and a sample line.

package io.github.skm0078.audora.ui.reader

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.R
import io.github.skm0078.audora.ui.theme.OptionBlock
import io.github.skm0078.audora.ui.theme.OptionGroup
import io.github.skm0078.audora.ui.theme.OptionSheet
import io.github.skm0078.audora.ui.theme.namedSlider
import kotlin.math.roundToInt

/**
 * The size as a slider with a button each side, one [TEXT_SIZE_BUTTON_STEP] at a time: a slider alone is hard to set
 * exactly with a shaky hand, and TalkBack users get two plainly named buttons as well as the slider. [sample] shows a
 * line in [look] under it, so each change shows at once; Page and text leaves it out, as it has its own sample under
 * every type option.
 */
@Composable
internal fun TextSizeBlock(
    look: ReadingLook,
    onLookChange: (ReadingLook) -> Unit,
    @StringRes title: Int = R.string.text_size_title,
    sample: Boolean = true,
    subtitle: String? = null,
    help: String? = null,
) {
    val percent = stringResource(R.string.text_look_size_value, (look.textScale * PERCENT).roundToInt())
    val name = stringResource(title)
    // Disabled where a press would change nothing: at the smallest or largest size.
    val smaller = stepTextScale(look.textScale, larger = false)
    val larger = stepTextScale(look.textScale, larger = true)
    OptionBlock(name, value = percent, subtitle = subtitle, help = help) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                onLookChange(look.copy(textScale = smaller))
            }, enabled = smaller != look.textScale) {
                Icon(
                    painterResource(R.drawable.ic_format_size),
                    stringResource(R.string.text_size_smaller),
                    Modifier.size(SMALLER_ICON),
                )
            }
            Slider(
                value = look.textScale,
                onValueChange = { onLookChange(look.copy(textScale = snapScale(it))) },
                valueRange = ReadingLook.TEXT_SCALES,
                modifier = Modifier.weight(1f).namedSlider(name, percent),
            )
            IconButton(onClick = { onLookChange(look.copy(textScale = larger)) }, enabled = larger != look.textScale) {
                Icon(
                    painterResource(R.drawable.ic_format_size),
                    stringResource(R.string.text_size_larger),
                    Modifier.size(LARGER_ICON),
                )
            }
        }
        if (sample) {
            Text(
                stringResource(R.string.text_look_sample),
                style = ReadingTextStyles.forLook(look),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * The reader's quick Text size sheet (from ⋮), so the size is one tap from the book instead of inside Page and text.
 * The size is the text view's, so the reader opens it there.
 */
@Composable
internal fun TextSizeSheet(look: ReadingLook, onLookChange: (ReadingLook) -> Unit, onDismiss: () -> Unit) {
    OptionSheet(stringResource(R.string.text_size_title), onDismiss, help = stringResource(R.string.text_size_body)) {
        OptionGroup(title = null) { TextSizeBlock(look, onLookChange) }
    }
}

/** One button press smaller or larger, kept on the slider's grid and within [ReadingLook.TEXT_SCALES]. */
internal fun stepTextScale(scale: Float, larger: Boolean): Float =
    snapScale(if (larger) scale + TEXT_SIZE_BUTTON_STEP else scale - TEXT_SIZE_BUTTON_STEP)

/** Two of the slider's 5 % steps: a press shows a change at a glance. */
internal const val TEXT_SIZE_BUTTON_STEP = 0.1f

private const val PERCENT = 100

// The same "size" icon drawn small and large, so the pair reads as smaller and larger at a glance.
private val SMALLER_ICON = 16.dp
private val LARGER_ICON = 28.dp
