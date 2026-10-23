// File: app/src/main/kotlin/io/github/skm0078/audora/ui/settings/KidsModeItem.kt
// Purpose: Settings' Kids mode row, which asks before turning it on, since Kids mode hides Settings at once.

package io.github.skm0078.audora.ui.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import io.github.skm0078.audora.R
import io.github.skm0078.audora.ui.theme.LinkOption

/** Kids mode hides Settings at once, so it asks first and says how to leave (hold the banner on the library). */
@Composable
internal fun KidsModeItem(onKidsMode: () -> Unit) {
    var asking by remember { mutableStateOf(false) }
    LinkOption(
        stringResource(R.string.settings_kids_title),
        onClick = { asking = true },
        subtitle = stringResource(R.string.settings_kids_short),
        help = stringResource(R.string.settings_kids_body),
    )
    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text(stringResource(R.string.kids_confirm_title)) },
            text = { Text(stringResource(R.string.kids_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    asking = false
                    onKidsMode()
                }) { Text(stringResource(R.string.kids_confirm_turn_on)) }
            },
            dismissButton = {
                TextButton(onClick = { asking = false }) { Text(stringResource(R.string.kids_confirm_cancel)) }
            },
        )
    }
}
