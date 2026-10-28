// File: app/src/main/kotlin/io/github/skm0078/audora/ui/search/FoundCard.kt
// Purpose: The library search's "Actions and settings" rows above the books, found on the phone from a small list
//          made once per language (ADR 2026-10-28-action-search).

package io.github.skm0078.audora.ui.search

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.search.ActionSearch
import io.github.skm0078.audora.ui.kids.LocalKidsMode
import io.github.skm0078.audora.ui.library.LibraryListState
import io.github.skm0078.audora.ui.settings.SettingKey
import io.github.skm0078.audora.ui.theme.Dimens
import io.github.skm0078.audora.ui.theme.LinkOption
import io.github.skm0078.audora.ui.theme.OptionDivider
import io.github.skm0078.audora.ui.theme.OptionGroup
import java.util.Locale

/**
 * What the search text in [list] finds besides books; none in kids mode, where the book menu and You are hidden too.
 * The list is made again only when the language, the book it names or a shown value changes, and each keystroke
 * compares a few dozen short entries.
 */
@Composable
internal fun rememberFoundRows(list: LibraryListState.Loaded, values: Map<SettingKey, String>): List<FoundRow> {
    val kids = LocalKidsMode.current
    val query = list.query.text
    val texts = rememberFoundTexts()
    val book = remember(query, list.all, list.latest) { FoundIndex.bookFor(query, list) }
    val entries = remember(texts, book?.item, book?.reading, values) { FoundIndex.entries(texts, book, values) }
    return remember(kids, query, entries) { if (kids) emptyList() else ActionSearch.find(query, entries) }
}

/** The rows under their heading, then the heading of the books below them. */
@Composable
internal fun FoundCard(rows: List<FoundRow>, onPick: (FoundTarget) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.testTag(FOUND_TAG), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
        OptionGroup(stringResource(R.string.found_heading)) {
            rows.forEachIndexed { index, row ->
                if (index > 0) OptionDivider()
                LinkOption(
                    row.title,
                    onClick = { onPick(row.target) },
                    subtitle = row.where,
                    leadingIcon = row.icon,
                )
            }
        }
        Text(
            stringResource(R.string.found_books_heading),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = Dimens.SpaceXs, top = Dimens.SpaceS).semantics { heading() },
        )
    }
}

/** No book matches [query] but something else does: the rows, then a line that says no book title matched. */
@Composable
internal fun FoundOnly(rows: List<FoundRow>, query: String, onPick: (FoundTarget) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.SpaceS),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
    ) {
        FoundCard(rows, onPick)
        Text(
            stringResource(R.string.found_no_book, query.trim()),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = Dimens.SpaceXs),
        )
    }
}

@Composable
private fun rememberFoundTexts(): FoundTexts {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(context, configuration) { ResourceTexts(context.resources, englishResources(context)) }
}

private fun englishResources(context: Context): Resources {
    val english = Configuration(context.resources.configuration).apply { setLocale(Locale.ENGLISH) }
    return context.createConfigurationContext(english).resources
}

private class ResourceTexts(private val local: Resources, private val englishOnes: Resources) : FoundTexts {
    override fun text(id: Int): String = local.getString(id)

    override fun english(id: Int): String = englishOnes.getString(id)

    override fun format(id: Int, vararg args: String): String = local.getString(id, *args)
}

internal const val FOUND_TAG = "found_actions"
