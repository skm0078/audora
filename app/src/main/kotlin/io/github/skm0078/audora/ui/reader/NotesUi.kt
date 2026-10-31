// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/NotesUi.kt
// Purpose: The notes over the reader: bookmarks sheet, bookmark note and pronunciation fix dialogs, and the sentence
//          menu (SentenceMenuUi.kt) (items 29, 1, 30, 32, 33).

package io.github.skm0078.audora.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.core.notes.Bookmark
import io.github.skm0078.audora.core.notes.PronunciationRule
import io.github.skm0078.audora.ui.pronunciation.PronunciationFixDialog
import io.github.skm0078.audora.ui.theme.AppSheet
import io.github.skm0078.audora.ui.theme.Dimens
import io.github.skm0078.audora.ui.theme.SwitchLine

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
fun NotesOverlays(
    notes: NotesState,
    actions: BookNotesActions,
    reader: ReaderActions,
    voice: VoiceNotesBinding = VoiceNotesBinding.None,
    lookUp: LookUpBinding = LookUpBinding.None,
) {
    notes.menuFor?.let { unit -> SentenceMenu(unit, notes, actions, reader, voice, lookUp) }
    PassageOverlays(notes, actions)
    notes.sharing?.let { SharedPassageMenu(it, actions, reader) }
    if (notes.listOpen) BookmarksSheet(notes.bookmarks, actions, reader, voice)
    VoiceNotesOverlays(voice)
    LookUpOverlay(lookUp)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookmarksSheet(
    bookmarks: List<Bookmark>,
    actions: BookNotesActions,
    reader: ReaderActions,
    voice: VoiceNotesBinding,
) {
    AppSheet(onDismissRequest = actions::hideList) {
        BookmarksList(
            bookmarks,
            actions,
            reader,
            voice,
            onOpened = actions::hideList,
            modifier = Modifier.navigationBarsPadding().padding(bottom = Dimens.SpaceL),
        )
    }
}

/**
 * The book's bookmarks and voice notes, with "Bookmark here": in the bookmarks sheet, or in the reader's side panel on
 * a wide screen. [onOpened] runs after one is opened (the sheet closes; the panel stays).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BookmarksList(
    bookmarks: List<Bookmark>,
    actions: BookNotesActions,
    reader: ReaderActions,
    voice: VoiceNotesBinding,
    onOpened: () -> Unit,
    modifier: Modifier = Modifier,
    titled: Boolean = true,
) {
    Column(modifier) {
        // Wraps "Bookmark here" under the title when both do not fit, rather than squeezing the title.
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = Dimens.SpaceXl),
            horizontalArrangement = if (titled) Arrangement.SpaceBetween else Arrangement.End,
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            if (titled) {
                Text(
                    stringResource(R.string.bookmarks_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
            }
            TextButton(onClick = reader::onBookmarkHere) { Text(stringResource(R.string.bookmarks_add_here)) }
        }
        if (bookmarks.isEmpty()) {
            Text(
                stringResource(R.string.bookmarks_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Dimens.SpaceXl, vertical = Dimens.SpaceL),
            )
        }
        LazyColumn {
            items(bookmarks, key = { it.id }) { bookmark ->
                BookmarkRow(
                    bookmark,
                    onOpen = {
                        onOpened()
                        reader.onBookmark(bookmark)
                    },
                    onEdit = { actions.editNote(bookmark) },
                    onDelete = { actions.delete(bookmark) },
                )
                HorizontalDivider()
            }
            // A voice note opens where it was recorded, found again the way a bookmark is.
            voiceNoteItems(voice) { note ->
                onOpened()
                reader.onBookmark(Bookmark(0, note.anchor))
            }
        }
    }
}

@Composable
private fun BookmarkRow(bookmark: Bookmark, onOpen: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(
            onClick = onOpen,
        ).padding(start = Dimens.SpaceXl, end = Dimens.SpaceS, top = Dimens.SpaceS, bottom = Dimens.SpaceS),
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
    val sentence = unit.page to unit.charStart
    var thisBookOnly by rememberSaveable(sentence) { mutableStateOf(true) }
    PronunciationFixDialog(
        title = stringResource(R.string.fix_title),
        rules = rules,
        where = { rule ->
            stringResource(if (rule.bookId == null) R.string.fix_every_book else R.string.fix_this_book_short)
        },
        onAdd = { actions.addRule(it.written, it.spoken, it.wholeWord, thisBookOnly) },
        onDelete = actions::deleteRule,
        onDismiss = actions::dismissFix,
        draftKey = sentence,
        above = {
            Text(
                unit.text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        },
        options = { SwitchLine(stringResource(R.string.fix_this_book), thisBookOnly, { thisBookOnly = it }) },
    )
}
