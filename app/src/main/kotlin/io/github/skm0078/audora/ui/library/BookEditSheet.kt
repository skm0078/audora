// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/BookEditSheet.kt
// Purpose: Edit details (ADR-068): a book's title, author, language and cover, set by the listener and kept over what
//          the file says, with a way back to the file's own.

package io.github.skm0078.audora.ui.library

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.library.Book
import io.github.skm0078.audora.core.library.edit
import io.github.skm0078.audora.core.library.resetDetails
import io.github.skm0078.audora.core.logging.AudoraLog
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.di.LibraryEntryPoint
import io.github.skm0078.audora.ui.theme.AppSheet
import io.github.skm0078.audora.ui.theme.Dimens
import kotlinx.coroutines.launch

/** Saves through the library, so the list, its covers and the reader see the change at once. */
@Composable
internal fun BookEditRoute(book: Book, onDismiss: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val entry = remember { LibraryEntryPoint.of(context) }
    val said = { text: Int -> Toast.makeText(context, text, Toast.LENGTH_SHORT).show() }
    BookEditSheet(
        book = book,
        onSave = { draft ->
            onDismiss()
            scope.launch {
                val picture = draft.picture
                val kept = picture == null || entry.covers().keep(book.id) {
                    context.contentResolver.openInputStream(Uri.parse(picture))
                }
                if (!kept) return@launch said(R.string.book_edit_picture_failed)
                val saved = entry.library().edit(book.id, draft.toEdit(book))
                AudoraLog.i(TAG, "Book details edited, book=${book.id.logLabel} saved=${saved is Outcome.Success}")
                said(if (saved is Outcome.Success) R.string.book_edit_saved else R.string.book_edit_not_saved)
            }
        },
        onFillFromFile = {
            onDismiss()
            scope.launch { entry.library().resetDetails(book.id) }
        },
        onDismiss = onDismiss,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BookEditSheet(
    book: Book,
    onSave: (BookEditDraft) -> Unit,
    onFillFromFile: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by rememberSaveable(book.id.value, stateSaver = DraftSaver) { mutableStateOf(BookEditDraft.of(book)) }
    val pick = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        uri?.let { draft = draft.copy(cover = CoverOption.GALLERY, picture = it.toString()) }
    }
    AppSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.SpaceS),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
        ) {
            Text(
                stringResource(R.string.book_edit),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            OutlinedTextField(
                value = draft.title,
                onValueChange = { draft = draft.copy(title = it) },
                label = { Text(stringResource(R.string.book_edit_title_field)) },
                isError = draft.title.isBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = draft.author,
                onValueChange = { draft = draft.copy(author = it) },
                label = { Text(stringResource(R.string.book_edit_author_field)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            LanguageChoice(draft.language) { draft = draft.copy(language = it) }
            CoverChoices(book, draft, onDraft = { draft = it }) {
                pick.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.book_edit_cancel)) }
                Button(onClick = { onSave(draft) }, enabled = draft.savable(book)) {
                    Text(stringResource(R.string.book_edit_save))
                }
            }
            TextButton(onClick = onFillFromFile, Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.book_edit_fill))
            }
            Text(
                stringResource(R.string.book_edit_fill_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LanguageChoice(selected: String, onSelect: (String) -> Unit) {
    SectionTitle(R.string.book_edit_language)
    Column(Modifier.selectableGroup()) {
        Choice(stringResource(R.string.book_edit_language_auto), selected.isEmpty()) { onSelect("") }
        BookEditDraft.LANGUAGES.forEach { tag -> Choice(languageName(tag), selected == tag) { onSelect(tag) } }
    }
}

@Composable
private fun CoverChoices(book: Book, draft: BookEditDraft, onDraft: (BookEditDraft) -> Unit, onPick: () -> Unit) {
    SectionTitle(R.string.book_edit_cover)
    Column(Modifier.selectableGroup()) {
        BookEditDraft.coverOptions(book).forEach { option ->
            val label = stringResource(coverLabel(option))
            Choice(label, draft.cover == option) {
                if (option == CoverOption.GALLERY) onPick() else onDraft(draft.copy(cover = option))
            }
            if (option == CoverOption.PAGE && draft.cover == CoverOption.PAGE) {
                val pages = book.pageCount
                OutlinedTextField(
                    value = draft.page,
                    onValueChange = { onDraft(draft.copy(page = it.filter(Char::isDigit))) },
                    label = {
                        Text(
                            pages?.let { stringResource(R.string.book_edit_cover_page_field, it) }
                                ?: stringResource(R.string.book_edit_cover_page_field_unknown),
                        )
                    },
                    isError = draft.pageIndex(pages) == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(start = Dimens.SpaceXxl),
                )
            }
            if (option == CoverOption.GALLERY && draft.picture != null) {
                Text(
                    stringResource(R.string.book_edit_cover_picked),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = Dimens.SpaceXxl),
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(label: Int) {
    Text(
        stringResource(label),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.semantics { heading() },
    )
}

/** One radio row; the whole row is the target, at least a touch target tall. */
@Composable
private fun Choice(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = Dimens.SpaceXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, Modifier.padding(start = Dimens.SpaceS))
    }
}

private fun coverLabel(option: CoverOption): Int = when (option) {
    CoverOption.FIRST_PAGE -> R.string.book_edit_cover_first
    CoverOption.PAGE -> R.string.book_edit_cover_page
    CoverOption.GALLERY -> R.string.book_edit_cover_gallery
    CoverOption.FROM_BOOK -> R.string.book_edit_cover_book
}

/** A draft survives rotation as a few strings; nothing in it is large. */
private val DraftSaver = mapSaver(
    save = {
        mapOf(
            "title" to it.title,
            "author" to it.author,
            "language" to it.language,
            "cover" to it.cover.name,
            "page" to it.page,
            "picture" to it.picture,
        )
    },
    restore = { saved ->
        BookEditDraft(
            title = saved["title"] as? String ?: "",
            author = saved["author"] as? String ?: "",
            language = saved["language"] as? String ?: "",
            cover = CoverOption.valueOf(saved["cover"] as? String ?: CoverOption.FIRST_PAGE.name),
            page = saved["page"] as? String ?: "",
            picture = saved["picture"] as? String,
        )
    },
)

private const val TAG = "Library.Edit"
