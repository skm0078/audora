// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/LibraryTopBar.kt
// Purpose: The library's top bar: title, search field, sort and filter menu (plan item 22).

package io.github.skm0078.audora.ui.library

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.library.LibraryFilter
import io.github.skm0078.audora.core.library.LibraryQuery
import io.github.skm0078.audora.core.library.LibrarySort

@Composable
internal fun LibraryTopBar(listState: LibraryListState, actions: LibraryActions) {
    val loaded = listState as? LibraryListState.Loaded
    LibraryTopBar(
        query = loaded?.query ?: LibraryQuery(),
        hasBooks = loaded?.all?.isNotEmpty() == true,
        onQuery = actions::onQuery,
    )
}

/**
 * Search and the sort menu show once the library has a book. Closing the search clears its text but keeps the sort
 * and filter, which stay until changed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryTopBar(query: LibraryQuery, hasBooks: Boolean, onQuery: (LibraryQuery) -> Unit) {
    var searching by rememberSaveable { mutableStateOf(query.text.isNotEmpty()) }
    val closeSearch = {
        searching = false
        onQuery(query.copy(text = ""))
    }
    TopAppBar(
        title = {
            if (searching) {
                SearchField(query.text) { onQuery(query.copy(text = it)) }
            } else {
                Text(
                    stringResource(R.string.library_title),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() },
                )
            }
        },
        navigationIcon = {
            if (searching) {
                IconButton(onClick = closeSearch) {
                    Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.library_search_close))
                }
            }
        },
        actions = {
            if (hasBooks && !searching) {
                IconButton(onClick = { searching = true }) {
                    Icon(painterResource(R.drawable.ic_search), stringResource(R.string.library_search))
                }
            }
            if (hasBooks) SortMenu(query, onQuery)
        },
    )
}

@Composable
private fun SearchField(text: String, onChange: (String) -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    TextField(
        value = text,
        onValueChange = onChange,
        placeholder = { Text(stringResource(R.string.library_search_hint)) },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
        ),
        modifier = Modifier.focusRequester(focus),
    )
}

@Composable
private fun SortMenu(query: LibraryQuery, onQuery: (LibraryQuery) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(painterResource(R.drawable.ic_sort), stringResource(R.string.library_sort_filter))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            MenuHeader(R.string.library_sort_by)
            LibrarySort.entries.forEach { sort ->
                Choice(sortLabel(sort), selected = query.sort == sort) { onQuery(query.copy(sort = sort)) }
            }
            HorizontalDivider()
            MenuHeader(R.string.library_show)
            LibraryFilter.entries.forEach { filter ->
                Choice(filterLabel(filter), selected = query.filter == filter) {
                    onQuery(query.copy(filter = filter))
                }
            }
        }
    }
}

@Composable
private fun MenuHeader(@StringRes label: Int) {
    DropdownMenuItem(
        text = {
            Text(
                stringResource(label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        },
        onClick = {},
        enabled = false,
    )
}

/** The menu stays open, so sort and filter can both be picked in one go. */
@Composable
private fun Choice(@StringRes label: Int, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(label)) },
        onClick = onClick,
        leadingIcon = { RadioButton(selected = selected, onClick = null) },
    )
}

@StringRes
private fun sortLabel(sort: LibrarySort): Int = when (sort) {
    LibrarySort.RECENT -> R.string.library_sort_recent
    LibrarySort.TITLE -> R.string.library_sort_title
    LibrarySort.ADDED -> R.string.library_sort_added
    LibrarySort.PROGRESS -> R.string.library_sort_progress
}

@StringRes
private fun filterLabel(filter: LibraryFilter): Int = when (filter) {
    LibraryFilter.ALL -> R.string.library_filter_all
    LibraryFilter.IN_PROGRESS -> R.string.library_filter_in_progress
    LibraryFilter.NOT_STARTED -> R.string.library_filter_not_started
    LibraryFilter.FINISHED -> R.string.library_filter_finished
}
