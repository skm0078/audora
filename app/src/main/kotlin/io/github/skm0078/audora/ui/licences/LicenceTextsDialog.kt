// File: app/src/main/kotlin/io/github/skm0078/audora/ui/licences/LicenceTextsDialog.kt
// Purpose: The dialog over "Voices & licences" that shows licence files: one voice's, or every text the app carries.

package io.github.skm0078.audora.ui.licences

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.R
import io.github.skm0078.audora.speech.voices.LicenceText
import io.github.skm0078.audora.ui.voices.HEADER_PADDING

/** The licence-text list in its dialog, for tests. */
const val LICENCE_TEXTS_TAG = "licence-texts"

private val PARAGRAPH_GAP = 8.dp

@Composable
internal fun LicenceTextsDialog(open: OpenLicence, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(open.title) },
        text = {
            if (open.texts.isEmpty()) {
                Text(stringResource(R.string.licences_no_files))
            } else {
                LicenceTextList(open.texts)
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.licences_close)) } },
    )
}

/** Each file's name as a heading, then its text a paragraph at a time, so even the longest file scrolls smoothly. */
@Composable
private fun LicenceTextList(texts: List<LicenceText>) {
    LazyColumn(modifier = Modifier.testTag(LICENCE_TEXTS_TAG)) {
        texts.forEachIndexed { index, file ->
            item(key = "name:$index") {
                Text(
                    file.name,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = HEADER_PADDING).semantics { heading() },
                )
            }
            itemsIndexed(AppNotices.paragraphs(file.text), key = { at, _ -> "text:$index:$at" }) { _, text ->
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = PARAGRAPH_GAP),
                )
            }
        }
    }
}
