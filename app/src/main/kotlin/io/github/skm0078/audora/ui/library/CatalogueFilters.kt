// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/CatalogueFilters.kt
// Purpose: The library's catalogue chips (ADR-069), after the shelves in the one chip row: Filters with how many
//          are set, one chip per collection, and New or Edit collections; and the filter sheet (author, file type,
//          language, collection), which applies each choice at once.

package io.github.skm0078.audora.ui.library

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.library.BookCollection
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.LibraryFacets
import io.github.skm0078.audora.core.library.LibraryNarrow
import io.github.skm0078.audora.core.library.LibraryQuery
import io.github.skm0078.audora.ui.theme.AppSheet
import io.github.skm0078.audora.ui.theme.Dimens

/** How many books each collection holds, from every book's collections. */
internal fun collectionCounts(members: Map<BookId, Set<String>>): Map<String, Int> =
    members.values.flatten().groupingBy { it }.eachCount()

/**
 * After the shelves: a collection chip narrows the library to it (tap again to leave it), and search, sort and the
 * shelf still apply inside it. The sheets open from here, so the library screen holds none of their state.
 */
@Composable
internal fun CatalogueChips(list: LibraryListState.Loaded, onQuery: (LibraryQuery) -> Unit) {
    var filtering by rememberSaveable { mutableStateOf(false) }
    var managing by rememberSaveable { mutableStateOf(false) }
    val counts = remember(list.members) { collectionCounts(list.members) }
    val narrow = list.query.narrow
    val setNarrow = { next: LibraryNarrow -> onQuery(list.query.copy(narrow = next)) }
    FilterChip(
        selected = !narrow.isEmpty,
        onClick = { filtering = true },
        label = {
            Text(
                if (narrow.isEmpty) {
                    stringResource(R.string.catalogue_filters)
                } else {
                    stringResource(R.string.catalogue_filters_count, narrow.count)
                },
            )
        },
    )
    list.collections.forEach { collection ->
        val selected = narrow.collection == collection.id
        FilterChip(
            selected = selected,
            onClick = { setNarrow(narrow.copy(collection = collection.id.takeUnless { selected })) },
            label = {
                Text(
                    stringResource(R.string.library_shelf, collection.name, counts[collection.id] ?: 0),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = CHIP_MAX_WIDTH),
                )
            },
        )
    }
    val manage = if (list.collections.isEmpty()) R.string.collections_new else R.string.collections_edit
    AssistChip(onClick = { managing = true }, label = { Text(stringResource(manage)) })
    if (filtering) {
        FilterSheet(list.facets, list.collections, counts, narrow, setNarrow, onDismiss = { filtering = false })
    }
    if (managing) CollectionsRoute(list.collections, counts) { managing = false }
}

/** Each list shows only what the library holds, with its number of books; "Any" leaves that one open. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FilterSheet(
    facets: LibraryFacets,
    collections: List<BookCollection>,
    counts: Map<String, Int>,
    narrow: LibraryNarrow,
    onNarrow: (LibraryNarrow) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val formatNames = facets.formats.associate { (format, _) -> format to stringResource(formatName(format)) }
    val languageNames = facets.languages.associate { (tag, _) -> tag to languageName(tag) }
    val collectionNames = collections.associate { it.id to it.name }
    AppSheet(onDismissRequest = onDismiss, modifier = modifier) {
        LazyColumn(Modifier.navigationBarsPadding().padding(horizontal = Dimens.ScreenPadding)) {
            item {
                Text(
                    stringResource(R.string.catalogue_filters_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
            }
            choices(R.string.catalogue_author, facets.authors, narrow.author, { it }) {
                onNarrow(narrow.copy(author = it))
            }
            choices(R.string.catalogue_format, facets.formats, narrow.format, formatNames::getValue) {
                onNarrow(narrow.copy(format = it))
            }
            choices(
                R.string.catalogue_language,
                facets.languages,
                narrow.language,
                languageNames::getValue,
            ) {
                onNarrow(narrow.copy(language = it))
            }
            val owned = collections.map { it.id to (counts[it.id] ?: 0) }
            choices(R.string.catalogue_collection, owned, narrow.collection, collectionNames::getValue) {
                onNarrow(narrow.copy(collection = it))
            }
            item { SheetButtons(narrow, onClear = { onNarrow(LibraryNarrow()) }, onDone = onDismiss) }
        }
    }
}

/** One radio list; left out when the library has nothing to tell apart (no book has an author, say). */
private fun <T : Any> LazyListScope.choices(
    @StringRes title: Int,
    options: List<Pair<T, Int>>,
    selected: T?,
    name: (T) -> String,
    onPick: (T?) -> Unit,
) {
    if (options.isEmpty()) return
    item(key = "$title:header") {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = Dimens.SpaceL).semantics { heading() },
        )
    }
    item(key = "$title:any") { ChoiceRow(stringResource(R.string.catalogue_any), selected == null) { onPick(null) } }
    items(options, key = { "$title:${it.first}" }) { (value, books) ->
        ChoiceRow(stringResource(R.string.library_shelf, name(value), books), selected == value) { onPick(value) }
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.TouchTarget)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = Dimens.SpaceXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SheetButtons(narrow: LibraryNarrow, onClear: () -> Unit, onDone: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = Dimens.SpaceL),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS, Alignment.End),
    ) {
        if (!narrow.isEmpty) {
            OutlinedButton(onClick = onClear) { Text(stringResource(R.string.catalogue_clear)) }
        }
        Button(onClick = onDone) { Text(stringResource(R.string.catalogue_done)) }
    }
}

/** A long collection name is cut, so one chip never fills the row. */
private val CHIP_MAX_WIDTH = 200.dp
