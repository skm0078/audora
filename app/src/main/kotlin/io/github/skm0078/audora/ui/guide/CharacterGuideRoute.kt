// File: app/src/main/kotlin/io/github/skm0078/audora/ui/guide/CharacterGuideRoute.kt
// Purpose: The character guide dialog: who is who so far, with add, edit and delete (plan item 80).

package io.github.skm0078.audora.ui.guide

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.config.CharacterGuideConfig
import io.github.skm0078.audora.core.guide.CharacterGuideStore
import io.github.skm0078.audora.core.guide.GuideEntry
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.resume.ResumeAnchorStore
import io.github.skm0078.audora.core.text.TextCuts
import io.github.skm0078.audora.ui.library.titleOrUntitled
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
    // The id being edited (NEW_ENTRY for a new one), saved so a rotation keeps the editor and what was typed.
    var editingId by rememberSaveable(book.id) { mutableStateOf<Long?>(null) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.guide_title, titleOrUntitled(book.title))) },
        text = { GuideList(state, model) { editingId = it.id } },
        confirmButton = {
            TextButton(onClick = { editingId = NEW_ENTRY }, enabled = state != null) {
                Text(stringResource(R.string.guide_add))
            }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.guide_close)) } },
    )
    state?.let { current ->
        GuideEditor(current, editingId, model) { editingId = null }
    }
}

/** The editor for [editingId] (NEW_ENTRY for a new one), while one is open. */
@Composable
private fun GuideEditor(current: GuideState, editingId: Long?, model: CharacterGuideModel, onDone: () -> Unit) {
    val editing = editingId?.let { id ->
        if (id == NEW_ENTRY) GuideEntry(NEW_ENTRY, "", "", current.page) else model.entry(id)
    }
    if (editing == null) return
    var full by rememberSaveable(editing.id) { mutableStateOf(false) }
    EntryEditor(
        entry = editing,
        full = full,
        onSave = { if (model.save(it)) onDone() else full = true },
        onDelete = {
            model.delete(editing.id)
            onDone()
        },
        onDismiss = onDone,
    )
}

@Composable
private fun GuideList(state: GuideState?, model: CharacterGuideModel, onEdit: (GuideEntry) -> Unit) {
    Column(
        Modifier.verticalScroll(rememberScrollState()).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state == null) {
            CircularProgressIndicator()
            return@Column
        }
        state.undoable?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.guide_deleted), Modifier.weight(1f))
                TextButton(onClick = model::undoDelete) { Text(stringResource(R.string.guide_undo)) }
            }
        }
        if (state.view.shown.isEmpty()) Text(stringResource(R.string.guide_empty))
        state.view.shown.forEach { entry -> EntryRow(entry) { onEdit(entry) } }
        if (state.view.later > 0) {
            Text(
                pluralStringResource(R.plurals.guide_later, state.view.later, state.view.later),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun EntryRow(entry: GuideEntry, onEdit: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MIN_TOUCH)
            .clickable(onClickLabel = stringResource(R.string.guide_edit), onClick = onEdit)
            .padding(vertical = 4.dp),
    ) {
        Text(entry.name, style = MaterialTheme.typography.titleSmall)
        if (entry.note.isNotBlank()) Text(entry.note, style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.guide_met_on, entry.page + 1), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun EntryEditor(
    entry: GuideEntry,
    full: Boolean,
    onSave: (GuideEntry) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val limits = remember { CharacterGuideConfig() }
    var name by rememberSaveable(entry.id) { mutableStateOf(entry.name) }
    var note by rememberSaveable(entry.id) { mutableStateOf(entry.note) }
    var page by rememberSaveable(entry.id) { mutableStateOf((entry.page + 1).toString()) }
    val pageNumber = page.toIntOrNull()?.takeIf { it >= 1 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (entry.id == NEW_ENTRY) R.string.guide_add else R.string.guide_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    name,
                    { name = TextCuts.take(it, limits.maxNameChars) },
                    label = { Text(stringResource(R.string.guide_name)) },
                )
                OutlinedTextField(
                    note,
                    { note = TextCuts.take(it, limits.maxNoteChars) },
                    label = { Text(stringResource(R.string.guide_note)) },
                )
                OutlinedTextField(
                    page,
                    { page = it.filter(Char::isDigit).take(MAX_PAGE_DIGITS) },
                    label = { Text(stringResource(R.string.guide_page)) },
                    isError = pageNumber == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                if (full) {
                    Text(
                        stringResource(R.string.guide_full, limits.maxEntries),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                if (entry.id != NEW_ENTRY) {
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

/** The id an entry has until it is saved (the guide gives it a real one). */
private const val NEW_ENTRY = 0L

/** A page number has at most this many digits (no book has a billion pages; keeps toInt from overflowing). */
private const val MAX_PAGE_DIGITS = 6

private val MIN_TOUCH = 48.dp
