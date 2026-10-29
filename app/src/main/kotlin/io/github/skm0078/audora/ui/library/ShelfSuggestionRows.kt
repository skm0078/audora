// File: app/src/main/kotlin/io/github/skm0078/audora/ui/library/ShelfSuggestionRows.kt
// Purpose: "Shelves you might like" (personal touch 17) under the collections: each suggested shelf with why it is
//          offered and Make shelf; nothing is made until it is tapped.

package io.github.skm0078.audora.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.library.BookCollection
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.library.ShelfSuggestion
import io.github.skm0078.audora.core.library.ShelfSuggestions
import io.github.skm0078.audora.core.library.SuggestionKind
import io.github.skm0078.audora.ui.theme.Dimens
import java.util.Locale

/** The shelves the library's books suggest, worked out again only when the books or collections change. */
@Composable
internal fun rememberShelfSuggestions(
    books: List<LibraryItem>,
    collections: List<BookCollection>,
    members: Map<BookId, Set<String>>,
): List<ShelfSuggestion> {
    val context = LocalContext.current
    return remember(books, collections, members) {
        ShelfSuggestions.of(books.map { it.book }, collections, members) { tag ->
            val language = Locale.forLanguageTag(tag).let { it.getDisplayLanguage(it) }.ifEmpty { tag }
            context.getString(R.string.smart_shelf_language, language)
        }
    }
}

/** The heading, the line on where they come from, and one row per suggestion; nothing when there is none. */
internal fun LazyListScope.shelfSuggestions(suggestions: List<ShelfSuggestion>, onMake: (ShelfSuggestion) -> Unit) {
    if (suggestions.isEmpty()) return
    item {
        Column(Modifier.padding(top = Dimens.SpaceL, bottom = Dimens.SpaceXs)) {
            Text(
                stringResource(R.string.smart_shelf_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(R.string.smart_shelf_where),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    items(suggestions, key = { "suggest-${it.kind}-${it.name}" }) { suggestion ->
        SuggestionRow(suggestion) { onMake(suggestion) }
    }
}

@Composable
private fun SuggestionRow(suggestion: ShelfSuggestion, onMake: () -> Unit) {
    val count = suggestion.books.size
    val why = when (suggestion.kind) {
        SuggestionKind.SERIES -> pluralStringResource(R.plurals.smart_shelf_series, count, count)
        SuggestionKind.AUTHOR -> pluralStringResource(R.plurals.smart_shelf_author, count, count)
        SuggestionKind.LANGUAGE -> pluralStringResource(R.plurals.smart_shelf_language_line, count, count)
    }
    val makeLabel = stringResource(R.string.smart_shelf_make_named, suggestion.name)
    ListItem(
        headlineContent = { Text(suggestion.name) },
        supportingContent = { Text(why) },
        trailingContent = {
            FilledTonalButton(onClick = onMake, Modifier.semantics { contentDescription = makeLabel }) {
                Text(stringResource(R.string.smart_shelf_make))
            }
        },
    )
}
