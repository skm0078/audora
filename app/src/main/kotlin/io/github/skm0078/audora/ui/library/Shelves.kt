// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/Shelves.kt
// Purpose: Library shelves (plan item 23): All, Reading, Not started and Finished as one-tap chips with counts,
//          computed from reading progress over the library's own filter (no stored shelves); and the one chip row
//          they share with the catalogue's chips (ADR 2026-10-28-home-and-tour).

package io.github.skm0078.audora.ui.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.library.LibraryFilter
import io.github.skm0078.audora.core.library.LibraryQuery
import io.github.skm0078.audora.ui.theme.Dimens

/** How many books each shelf holds, by the same rule as the library's filter. */
fun shelfCounts(all: List<LibraryItem>): Map<LibraryFilter, Int> = LibraryFilter.entries.associateWith { filter ->
    LibraryQuery(filter = filter).apply(all, book = { it.book }, progress = { it.progress }).size
}

/**
 * The library's one chip row over the books: the shelves, a thin rule, then Filters and the collections. One row,
 * not two, so the first books sit higher on a phone; it scrolls sideways when the chips do not fit.
 */
@Composable
internal fun LibraryChipRow(list: LibraryListState.Loaded, onQuery: (LibraryQuery) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.SpaceXs),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShelfChips(list, onQuery)
        VerticalDivider(Modifier.height(CHIP_RULE_HEIGHT))
        CatalogueChips(list, onQuery)
    }
}

/**
 * The shelf chips: picking one sets the library's filter, so search and sort still apply within it. Shown whatever
 * the shelf holds, so an empty shelf can always be left.
 */
@Composable
private fun ShelfChips(list: LibraryListState.Loaded, onQuery: (LibraryQuery) -> Unit) {
    val counts = remember(list.all) { shelfCounts(list.all) }
    LibraryFilter.entries.forEach { shelf ->
        FilterChip(
            selected = list.query.filter == shelf,
            onClick = { onQuery(list.query.copy(filter = shelf)) },
            label = {
                Text(stringResource(R.string.library_shelf, stringResource(shelf.label), counts[shelf] ?: 0))
            },
        )
    }
}

private val CHIP_RULE_HEIGHT = 24.dp

private val LibraryFilter.label: Int
    get() = when (this) {
        LibraryFilter.ALL -> R.string.library_filter_all
        LibraryFilter.IN_PROGRESS -> R.string.library_shelf_reading
        LibraryFilter.NOT_STARTED -> R.string.library_filter_not_started
        LibraryFilter.FINISHED -> R.string.library_filter_finished
    }
