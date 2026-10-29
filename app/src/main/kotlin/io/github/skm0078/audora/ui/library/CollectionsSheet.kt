// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/CollectionsSheet.kt
// Purpose: The listener's collections (ADR-069): making, renaming and deleting them, and putting a book in them from
//          its details sheet. Writes go to the collection store; one that fails says so in a toast.

package io.github.skm0078.audora.ui.library

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.library.BookCollection
import io.github.skm0078.audora.core.library.CollectionNames
import io.github.skm0078.audora.core.library.ShelfLook
import io.github.skm0078.audora.ui.theme.AppSheet
import io.github.skm0078.audora.ui.theme.Dimens
import io.github.skm0078.audora.ui.theme.TitleAndTrailing

/** Opened from the library's collection chips and the side panel; [counts] is how many books each collection holds. */
@Composable
internal fun CollectionsRoute(collections: List<BookCollection>, counts: Map<String, Int>, onDismiss: () -> Unit) {
    val writer = rememberCollectionWriter()
    CollectionsSheet(
        collections,
        counts,
        rememberShelfLooks(),
        onCreate = { name, look -> writer.create(name, look = look) },
        onEdit = writer::edit,
        onDelete = writer::delete,
        onDismiss = onDismiss,
    )
}

/**
 * Every collection with its badge, number of books, Edit and Delete, and New collection; deleting asks first. Edit
 * and New swap the list for the edit page in the same sheet, and Back returns to the list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CollectionsSheet(
    collections: List<BookCollection>,
    counts: Map<String, Int>,
    looks: Map<String, ShelfLook>,
    onCreate: (String, ShelfLook) -> Unit,
    onEdit: (id: String, name: String, nameBefore: String, look: ShelfLook) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by rememberSaveable { mutableStateOf<String?>(null) } // a collection's id, or NEW_COLLECTION
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    AppSheet(onDismissRequest = onDismiss, modifier = modifier) {
        val edited = collections.firstOrNull { it.id == editing }
        when {
            editing == NEW_COLLECTION -> {
                BackHandler { editing = null }
                ShelfEditor(R.string.collections_new, NEW_COLLECTION, "", ShelfLook.DEFAULT, { name, look ->
                    onCreate(name, look)
                    editing = null
                }) { editing = null }
            }

            edited != null -> {
                BackHandler { editing = null }
                val look = looks[edited.id] ?: ShelfLook.DEFAULT
                ShelfEditor(R.string.shelf_edit_title, edited.id, edited.name, look, { name, chosen ->
                    onEdit(edited.id, name, edited.name, chosen)
                    editing = null
                }) { editing = null }
            }

            else -> CollectionList(collections, counts, looks, { editing = it }, { deleting = it }) {
                editing = NEW_COLLECTION
            }
        }
    }
    collections.firstOrNull { it.id == deleting }?.let { collection ->
        DeleteCollectionDialog(collection, onConfirm = { onDelete(collection.id) }) { deleting = null }
    }
}

@Composable
private fun CollectionList(
    collections: List<BookCollection>,
    counts: Map<String, Int>,
    looks: Map<String, ShelfLook>,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    onNew: () -> Unit,
) {
    LazyColumn(Modifier.navigationBarsPadding().padding(horizontal = Dimens.ScreenPadding)) {
        item {
            Text(
                stringResource(R.string.collections_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
        }
        if (collections.isEmpty()) {
            item { Text(stringResource(R.string.collections_empty), Modifier.padding(vertical = Dimens.SpaceS)) }
        }
        items(collections, key = { it.id }) { collection ->
            val look = looks[collection.id] ?: ShelfLook.DEFAULT
            CollectionLine(collection, look, counts[collection.id] ?: 0, { onEdit(collection.id) }) {
                onDelete(collection.id)
            }
        }
        item {
            Button(onClick = onNew, Modifier.fillMaxWidth().padding(vertical = Dimens.SpaceS)) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = null)
                Text(stringResource(R.string.collections_new))
            }
        }
    }
}

@Composable
private fun CollectionLine(
    collection: BookCollection,
    look: ShelfLook,
    books: Int,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val editLabel = stringResource(R.string.shelf_edit_named, collection.name)
    val deleteLabel = stringResource(R.string.collections_delete_named, collection.name)
    // Edit and Delete go under the name when both buttons and the name do not fit on one line (large text).
    ListItem(
        leadingContent = { ShelfBadge(collection.id, look) },
        headlineContent = {
            TitleAndTrailing(
                title = {
                    Column {
                        Text(collection.name)
                        Text(
                            pluralStringResource(R.plurals.collections_book_count, books, books),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                trailing = {
                    Row {
                        TextButton(onClick = onEdit, Modifier.semantics { contentDescription = editLabel }) {
                            Text(stringResource(R.string.shelf_edit))
                        }
                        TextButton(onClick = onDelete, Modifier.semantics { contentDescription = deleteLabel }) {
                            Text(stringResource(R.string.collections_delete), color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
            )
        },
    )
}

/** Save stays off until the name has a letter; a name is cut at [CollectionNames.MAX_LENGTH] as it is typed. */
@Composable
internal fun NameDialog(title: String, initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    val cleaned = CollectionNames.clean(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(CollectionNames.MAX_LENGTH) },
                label = { Text(stringResource(R.string.collections_name)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    cleaned?.let(onSave)
                    onDismiss()
                },
                enabled = cleaned != null,
            ) { Text(stringResource(R.string.collections_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.collections_cancel)) } },
    )
}

@Composable
private fun DeleteCollectionDialog(collection: BookCollection, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.collections_delete_title, collection.name)) },
        text = { Text(stringResource(R.string.collections_delete_body)) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) { Text(stringResource(R.string.collections_delete), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.collections_cancel)) } },
    )
}

/**
 * A book's collections in its details sheet: tap a chip to put the book in it or take it out, at once; New
 * collection makes one with this book already in it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BookCollectionChips(
    collections: List<BookCollection>,
    memberOf: Set<String>,
    onToggle: (String, Boolean) -> Unit,
    onCreate: (String) -> Unit,
) {
    var creating by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
        Text(
            stringResource(R.string.collections_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        if (collections.isNotEmpty()) {
            Text(
                stringResource(R.string.collections_in_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
            collections.forEach { collection ->
                val member = collection.id in memberOf
                FilterChip(
                    selected = member,
                    onClick = { onToggle(collection.id, !member) },
                    label = { Text(collection.name, maxLines = 1) },
                )
            }
            AssistChip(
                onClick = { creating = true },
                label = { Text(stringResource(R.string.collections_new)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
            )
        }
    }
    if (creating) {
        NameDialog(stringResource(R.string.collections_new), "", onSave = onCreate) { creating = false }
    }
}

/** The edit page's key for a collection not made yet; no real id is blank. */
private const val NEW_COLLECTION = ""
