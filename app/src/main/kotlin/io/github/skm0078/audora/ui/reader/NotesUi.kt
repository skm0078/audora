// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/NotesUi.kt
// Purpose: The sentence menu, bookmarks sheet, bookmark note and pronunciation fix dialogs (plan items 29, 1).

package io.github.skm0078.audora.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.notes.Bookmark
import io.github.skm0078.audora.core.notes.PronunciationRule

/** Top bar: opens the bookmarks sheet; filled while this book has bookmarks. */
@Composable
fun BookmarksButton(notes: NotesState, actions: BookNotesActions) {
    IconButton(onClick = actions::showList) {
        val icon = if (notes.bookmarks.isEmpty()) R.drawable.ic_bookmark_border else R.drawable.ic_bookmark
        Icon(painterResource(icon), stringResource(R.string.bookmarks_title))
    }
}

/** Everything the notes put over the reader, each shown while its state is set. */
@Composable
fun NotesOverlays(notes: NotesState, actions: BookNotesActions, reader: ReaderActions) {
    notes.menuFor?.let { unit ->
        SentenceMenuDialog(
            onBookmark = { actions.bookmark(unit) },
            onFix = { actions.fix(unit) },
            onReport = {
                actions.dismissMenu()
                reader.onReportSentence(unit)
            },
            onDismiss = actions::dismissMenu,
        )
    }
    if (notes.listOpen) BookmarksSheet(notes.bookmarks, actions, reader)
    notes.editing?.let { BookmarkNoteDialog(it, actions::saveNote, actions::dismissNote) }
    notes.fixing?.let { PronunciationDialog(it, notes.rules, actions) }
    if (notes.failed) {
        AlertDialog(
            onDismissRequest = actions::clearFailure,
            text = { Text(stringResource(R.string.notes_save_failed)) },
            confirmButton = { TextButton(onClick = actions::clearFailure) { Text(stringResource(R.string.notes_ok)) } },
        )
    }
}

@Composable
private fun SentenceMenuDialog(onBookmark: () -> Unit, onFix: () -> Unit, onReport: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sentence_menu_title)) },
        text = {
            Column {
                MenuRow(stringResource(R.string.sentence_menu_bookmark), onBookmark)
                MenuRow(stringResource(R.string.sentence_menu_fix), onFix)
                MenuRow(stringResource(R.string.report_title), onReport)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.password_cancel)) } },
    )
}

@Composable
private fun MenuRow(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.fillMaxWidth())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookmarksSheet(bookmarks: List<Bookmark>, actions: BookNotesActions, reader: ReaderActions) {
    ModalBottomSheet(onDismissRequest = actions::hideList) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.bookmarks_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = reader::onBookmarkHere) { Text(stringResource(R.string.bookmarks_add_here)) }
            }
            if (bookmarks.isEmpty()) {
                Text(
                    stringResource(R.string.bookmarks_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                )
            }
            LazyColumn {
                items(bookmarks, key = { it.id }) { bookmark ->
                    BookmarkRow(
                        bookmark,
                        onOpen = {
                            actions.hideList()
                            reader.onBookmark(bookmark)
                        },
                        onEdit = { actions.editNote(bookmark) },
                        onDelete = { actions.delete(bookmark) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun BookmarkRow(bookmark: Bookmark, onOpen: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(
            onClick = onOpen,
        ).padding(start = 24.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.bookmarks_page, bookmark.anchor.page + 1),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (bookmark.note.isNotBlank()) {
                Text(
                    bookmark.note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        TextButton(onClick = onEdit) { Text(stringResource(R.string.bookmarks_note)) }
        TextButton(onClick = onDelete) { Text(stringResource(R.string.notes_delete)) }
    }
}

@Composable
private fun BookmarkNoteDialog(bookmark: Bookmark, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var note by rememberSaveable(bookmark.id) { mutableStateOf(bookmark.note) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.bookmarks_note_title, bookmark.anchor.page + 1)) },
        text = {
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                placeholder = { Text(stringResource(R.string.bookmarks_note_hint)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(note) }) { Text(stringResource(R.string.sleep_options_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.password_cancel)) } },
    )
}

/** Adds a fix for a word of [unit]'s language and lists the fixes in use, each removable. */
@Composable
private fun PronunciationDialog(unit: ReadingUnit, rules: List<PronunciationRule>, actions: BookNotesActions) {
    var written by rememberSaveable(unit.page, unit.charStart) { mutableStateOf("") }
    var spoken by rememberSaveable(unit.page, unit.charStart) { mutableStateOf("") }
    var wholeWord by remember { mutableStateOf(true) }
    var thisBookOnly by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = actions::dismissFix,
        title = { Text(stringResource(R.string.fix_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    unit.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                OutlinedTextField(written, { written = it }, label = { Text(stringResource(R.string.fix_written)) })
                OutlinedTextField(spoken, { spoken = it }, label = { Text(stringResource(R.string.fix_spoken)) })
                FixSwitch(stringResource(R.string.fix_whole_word), wholeWord) { wholeWord = it }
                FixSwitch(stringResource(R.string.fix_this_book), thisBookOnly) { thisBookOnly = it }
                TextButton(
                    onClick = {
                        actions.addRule(written, spoken, wholeWord, thisBookOnly)
                        written = ""
                        spoken = ""
                    },
                    enabled = written.isNotBlank() && spoken.isNotBlank(),
                ) { Text(stringResource(R.string.fix_add)) }
                rules.forEach { rule -> RuleRow(rule) { actions.deleteRule(rule) } }
            }
        },
        confirmButton = { TextButton(onClick = actions::dismissFix) { Text(stringResource(R.string.notes_done)) } },
    )
}

@Composable
private fun FixSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun RuleRow(rule: PronunciationRule, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val scope = if (rule.bookId == null) R.string.fix_every_book else R.string.fix_this_book_short
        Text(
            stringResource(R.string.fix_rule, rule.written, rule.spoken, stringResource(scope)),
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(onClick = onDelete) { Text(stringResource(R.string.notes_delete)) }
    }
}
