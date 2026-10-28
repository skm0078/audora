// File: app/src/main/kotlin/io/github/skm0078/audora/ui/theme/SwitchRows.kt
// Purpose: One way to make a row with a switch: the whole row is the touch target and TalkBack reads it as one switch.
//          [SwitchLine] is that row for sheets and dialogs; settings cards use SwitchOption.

package io.github.skm0078.audora.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role

/**
 * The row toggles; its Switch takes `onCheckedChange = null` so it only shows the state and is not a second, smaller
 * target. TalkBack then reads the title, the detail and "on/off" together, as Settings' rows already do.
 */
fun Modifier.switchRow(checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit): Modifier = this
    .fillMaxWidth()
    .heightIn(min = Dimens.TouchTarget)
    .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)

/**
 * A switch row for sheets and dialogs, where a settings card's padding would not line up: [title], an optional
 * [detail] under it, and the switch at the end.
 */
@Composable
fun SwitchLine(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
) {
    Row(
        modifier.switchRow(checked, onChange = onChange).padding(vertical = Dimens.SpaceS),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            detail?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
