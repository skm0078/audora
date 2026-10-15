// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/PagePickerDialog.kt
// Purpose: "Go to page": type a page number (1-based on screen, zero-based to the model).

package io.github.skm0078.audora.ui.reader

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import io.github.skm0078.audora.R

@Composable
fun PagePickerDialog(pageCount: Int, onGo: (page: Int) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val page = parsePageNumber(text, pageCount)
    val go = { page?.let { onGo(it - 1) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reader_pick_page)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { typed -> text = typed.filter(Char::isDigit).take(MAX_DIGITS) },
                label = { Text(stringResource(R.string.reader_pick_page_label, pageCount)) },
                singleLine = true,
                isError = text.isNotEmpty() && page == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { go() }),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                go()
            }, enabled = page != null) { Text(stringResource(R.string.reader_pick_page_go)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.password_cancel)) } },
    )
}

private const val MAX_DIGITS = 6
