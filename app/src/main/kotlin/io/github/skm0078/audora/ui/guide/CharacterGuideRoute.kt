// File: app/src/main/kotlin/io/github/skm0078/audora/ui/guide/CharacterGuideRoute.kt
// Purpose: The character guide dialog: who is who so far, with add, edit and delete (plan item 80).

package io.github.skm0078.audora.ui.guide

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.guide.CharacterGuideStore
import io.github.skm0078.audora.core.guide.GuideEntry
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.resume.ResumeAnchorStore
import java.io.File

/** While [book] is set, shows its character guide up to the book's resume point. */
@Composable
fun CharacterGuideRoute(book: Book?, anchors: ResumeAnchorStore, onClose: () -> Unit) {
    if (book == null) return
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val model = remember(book.id) {
        val store = CharacterGuideStore(File(context.filesDir, CharacterGuideStore.FOLDER_NAME))
        CharacterGuideModel(book.id, store, anchors, scope).apply { start() }
    }
    val state by model.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<GuideEntry?>(null) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.guide_title, book.title)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val current = state
                if (current == null) {
                    CircularProgressIndicator()
                } else {
                    if (current.view.shown.isEmpty()) Text(stringResource(R.string.guide_empty))
                    current.view.shown.forEach { entry -> EntryRow(entry) { editing = entry } }
                    if (current.view.later > 0) {
                        Text(
                            pluralStringResource(R.plurals.guide_later, current.view.later, current.view.later),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { editing = GuideEntry(0, "", "", state?.page ?: 0) },
                enabled = state != null,
            ) { Text(stringResource(R.string.guide_add)) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.guide_close)) } },
    )
    editing?.let { entry ->
        EntryEditor(
            entry = entry,
            onSave = { if (model.save(it)) editing = null },
            onDelete = {
                model.delete(entry.id)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun EntryRow(entry: GuideEntry, onEdit: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onEdit).padding(vertical = 4.dp)) {
        Text(entry.name, style = MaterialTheme.typography.titleSmall)
        if (entry.note.isNotBlank()) Text(entry.note, style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.guide_met_on, entry.page + 1), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun EntryEditor(entry: GuideEntry, onSave: (GuideEntry) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable(entry.id) { mutableStateOf(entry.name) }
    var note by rememberSaveable(entry.id) { mutableStateOf(entry.note) }
    var page by rememberSaveable(entry.id) { mutableStateOf((entry.page + 1).toString()) }
    val pageNumber = page.toIntOrNull()?.takeIf { it >= 1 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (entry.id == 0L) R.string.guide_add else R.string.guide_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.guide_name)) })
                OutlinedTextField(note, { note = it }, label = { Text(stringResource(R.string.guide_note)) })
                OutlinedTextField(
                    page,
                    { page = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.guide_page)) },
                    isError = pageNumber == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                if (entry.id != 0L) {
                    TextButton(onClick = onDelete) { Text(stringResource(R.string.guide_delete)) }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { pageNumber?.let { onSave(entry.copy(name = name, note = note, page = it - 1)) } },
                enabled = name.isNotBlank() && pageNumber != null,
            ) { Text(stringResource(R.string.guide_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.guide_cancel)) } },
    )
}
