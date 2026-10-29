// File: app/src/main/kotlin/io/github/skm0078/audora/ui/theme/ConfirmedButton.kt
// Purpose: A text button for a step that loses something (clear a list, delete a download): it asks first.

package io.github.skm0078.audora.ui.theme

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.skm0078.audora.R

/**
 * [label] opens a question ([question]); only its [label] button runs [onConfirm], Keep and outside taps do not.
 * [modifier] is the first button's, as for naming it after what it deletes.
 */
@Composable
fun ConfirmedTextButton(label: String, question: String, modifier: Modifier = Modifier, onConfirm: () -> Unit) {
    var asking by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { asking = true }, modifier = modifier) { Text(label) }
    if (!asking) return
    AlertDialog(
        onDismissRequest = { asking = false },
        text = { Text(question) },
        confirmButton = {
            TextButton(
                onClick = {
                    asking = false
                    onConfirm()
                },
            ) { Text(label) }
        },
        dismissButton = { TextButton(onClick = { asking = false }) { Text(stringResource(R.string.confirm_keep)) } },
    )
}
