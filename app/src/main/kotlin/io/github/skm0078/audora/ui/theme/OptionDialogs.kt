// File: app/src/main/kotlin/io/github/skm0078/audora/ui/theme/OptionDialogs.kt
// Purpose: The option rows' pop-ups (ADR-062): the info button's help, the radio choice behind a value row, and the
//          bottom sheet that holds groups of options.

package io.github.skm0078.audora.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.skm0078.audora.R

/** An info button that opens [body] under [title]; the long help that used to sit under each option. */
@Composable
fun HelpButton(title: String, body: String) {
    var open by rememberSaveable { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(
            painterResource(R.drawable.ic_info),
            contentDescription = stringResource(R.string.option_help_about, title),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            // Some help (the privacy statement) is longer than a small phone's dialog: it scrolls.
            text = { Text(body, Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = {
                TextButton(onClick = { open = false }) { Text(stringResource(R.string.option_help_ok)) }
            },
        )
    }
}

/**
 * A bottom sheet of option groups with its [title] on top, and [help] for the sheet as a whole beside it. The sheet
 * takes the screen's surface colour so its group cards stand out, as they do in Settings. It opens at its full
 * height (up to the screen's), not half-way: half-way showed only the first control at large font sizes (UI audit).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionSheet(
    title: String,
    onDismiss: () -> Unit,
    help: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.ScreenPadding)
                .padding(bottom = Dimens.SpaceXl),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f).padding(start = Dimens.SpaceXs).semantics { heading() },
                )
                help?.let { HelpButton(title, it) }
            }
            content()
        }
    }
}

@Composable
internal fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onDismiss: () -> Unit,
    onPick: (T) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.selectableGroup().verticalScroll(rememberScrollState())) {
                options.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = Dimens.TouchTarget)
                            .selectable(option == selected, role = Role.RadioButton) { onPick(option) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Text(label(option), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.option_choice_cancel)) } },
    )
}
