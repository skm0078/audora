// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/LibrarySortSheet.kt
// Purpose: The library's sort sheet: sort order, which books show, grid or list, and how many books that leaves
//          (plan item 22, ADR 2026-10-28-book-menu-sort-sheet).

package io.github.skm0078.audora.ui.library

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.library.LibraryFilter
import io.github.skm0078.audora.core.library.LibraryQuery
import io.github.skm0078.audora.core.library.LibrarySort
import io.github.skm0078.audora.ui.theme.AppSheet
import io.github.skm0078.audora.ui.theme.Dimens
import io.github.skm0078.audora.ui.theme.SectionTitle

/**
 * Everything here applies the moment it is picked, so the books behind the sheet already show the result; the button
 * at the bottom only says how many that leaves and closes the sheet. Reset puts back the library's own order, all
 * books and the automatic layout; the search text and the Filters chip's choices stay, since they are set elsewhere.
 */
@Composable
internal fun LibrarySortSheet(
    query: LibraryQuery,
    shown: Int,
    view: LibraryLayoutState,
    onQuery: (LibraryQuery) -> Unit,
    onDismiss: () -> Unit,
) {
    val layout = view.layout ?: LibraryLayout.AUTO
    val changed = query.sort != LibrarySort.RECENT || query.filter != LibraryFilter.ALL || layout != LibraryLayout.AUTO
    AppSheet(onDismissRequest = onDismiss, skipPartiallyExpanded = true) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.ScreenPadding)
                .padding(bottom = Dimens.SpaceXl),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.library_sort_filter),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                TextButton(enabled = changed, onClick = {
                    onQuery(query.copy(sort = LibrarySort.RECENT, filter = LibraryFilter.ALL))
                    view.onLayout(LibraryLayout.AUTO)
                }) { Text(stringResource(R.string.library_sort_reset)) }
            }
            SectionTitle(stringResource(R.string.library_sort_by), Modifier.padding(top = Dimens.SpaceL))
            SortChoices(query.sort) { onQuery(query.copy(sort = it)) }
            SectionTitle(stringResource(R.string.library_show), Modifier.padding(top = Dimens.SpaceL))
            ShowChoices(query.filter) { onQuery(query.copy(filter = it)) }
            SectionTitle(stringResource(R.string.library_layout), Modifier.padding(top = Dimens.SpaceL))
            LayoutChoice(layout.showsList(LocalDensity.current.fontScale), view.onLayout)
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().padding(top = Dimens.SpaceL).heightIn(min = Dimens.PlayButtonSmall),
            ) {
                Text(pluralStringResource(R.plurals.library_sort_show, shown, shown), textAlign = TextAlign.Center)
            }
        }
    }
}

/** One line per order, as radio buttons: five orders do not split evenly into rows of chips. */
@Composable
private fun SortChoices(selected: LibrarySort, onSort: (LibrarySort) -> Unit) {
    Column(Modifier.selectableGroup()) {
        LibrarySort.entries.forEach { sort ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimens.TouchTarget)
                    .selectable(selected = sort == selected, role = Role.RadioButton) { onSort(sort) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
            ) {
                RadioButton(selected = sort == selected, onClick = null)
                Text(stringResource(sortLabel(sort)), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/** The four shelves, two to a row, every chip the same width. */
@Composable
private fun ShowChoices(selected: LibraryFilter, onFilter: (LibraryFilter) -> Unit) {
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
        LibraryFilter.entries.chunked(CHIPS_PER_ROW).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
                row.forEach { filter ->
                    FilterChip(
                        selected = filter == selected,
                        onClick = { onFilter(filter) },
                        label = {
                            Text(
                                stringResource(filterLabel(filter)),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                        modifier = Modifier.weight(1f).heightIn(min = Dimens.TouchTarget),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LayoutChoice(showsList: Boolean, onLayout: (LibraryLayout) -> Unit) {
    val choices = listOf(
        Triple(LibraryLayout.GRID, R.string.library_layout_grid, R.drawable.ic_grid_view),
        Triple(LibraryLayout.LIST, R.string.library_layout_list, R.drawable.ic_view_list),
    )
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        choices.forEachIndexed { index, (layout, label, icon) ->
            SegmentedButton(
                selected = showsList == (layout == LibraryLayout.LIST),
                onClick = { onLayout(layout) },
                shape = SegmentedButtonDefaults.itemShape(index, choices.size),
                icon = { Icon(painterResource(icon), contentDescription = null) },
            ) { Text(stringResource(label)) }
        }
    }
}

@StringRes
internal fun sortLabel(sort: LibrarySort): Int = when (sort) {
    LibrarySort.RECENT -> R.string.library_sort_recent
    LibrarySort.TITLE -> R.string.library_sort_title
    LibrarySort.AUTHOR -> R.string.library_sort_author
    LibrarySort.ADDED -> R.string.library_sort_added
    LibrarySort.PROGRESS -> R.string.library_sort_progress
}

@StringRes
internal fun filterLabel(filter: LibraryFilter): Int = when (filter) {
    LibraryFilter.ALL -> R.string.library_filter_all
    LibraryFilter.IN_PROGRESS -> R.string.library_filter_in_progress
    LibraryFilter.NOT_STARTED -> R.string.library_filter_not_started
    LibraryFilter.FINISHED -> R.string.library_filter_finished
}

private const val CHIPS_PER_ROW = 2
