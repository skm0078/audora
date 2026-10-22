// File: app/src/main/kotlin/io/github/skm0078/audora/ui/theme/SwitchRows.kt
// Purpose: One way to make a row with a switch: the whole row is the touch target and TalkBack reads it as one switch.

package io.github.skm0078.audora.ui.theme

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
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
