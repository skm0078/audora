// File: app/src/main/kotlin/io/github/skm0078/audora/ui/web/CatalogueDialog.kt
// Purpose: Book catalogues (CF-15): the list of catalogues, one page of a catalogue at a time, search, More, and
//          Download on each book ListenLock can read. Back goes up a page before it closes.

package io.github.skm0078.audora.ui.web

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.result.Outcome
import io.github.skm0078.audora.playback.web.CuratedCatalogue
import io.github.skm0078.audora.playback.web.OpdsCatalogue
import io.github.skm0078.audora.playback.web.OpdsDownload
import io.github.skm0078.audora.playback.web.OpdsEntry
import io.github.skm0078.audora.playback.web.OpdsFeed
import io.github.skm0078.audora.playback.web.WebAddress
import io.github.skm0078.audora.playback.web.WebBooks
import io.github.skm0078.audora.playback.web.WebError
import io.github.skm0078.audora.ui.theme.Dimens
import kotlinx.coroutines.launch

/** A catalogue page as it loads: loading, shown (with More loading below it), or failed with why. */
private sealed interface PageState {
    data object Loading : PageState

    data class Shown(val feed: OpdsFeed, val loadingMore: Boolean = false) : PageState

    data class Failed(val error: WebError) : PageState
}

/**
 * Full screen, so a long list of books reads like a page. The pages opened are kept as a stack of addresses, saved
 * across rotation (each page is fetched again then: nothing is cached). [onDownloadStarted] gets false when another
 * download is already running.
 */
@Composable
internal fun CatalogueDialog(books: WebBooks, onDownloadStarted: (Boolean) -> Unit, onClose: () -> Unit) {
    // Addresses never hold a line break, so the stack saves as one string.
    var stack by rememberSaveable { mutableStateOf("") }
    val pages = stack.split(STACK_SEPARATOR).filter(String::isNotEmpty)
    val current = pages.lastOrNull()
    val open = { address: String -> stack = (pages + address).joinToString(STACK_SEPARATOR) }
    val back = {
        if (current == null) {
            onClose()
        } else {
            stack = pages.dropLast(1).joinToString(STACK_SEPARATOR)
        }
    }
    Dialog(onDismissRequest = back, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = back) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigate_back))
                    }
                    Text(
                        stringResource(R.string.catalogues_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    IconButton(onClick = onClose) {
                        Icon(painterResource(R.drawable.ic_close), stringResource(R.string.catalogues_close))
                    }
                }
                if (current == null) {
                    CatalogueList(books, open)
                } else {
                    FeedPage(books, current, open) { title, download ->
                        onDownloadStarted(books.downloadBook(title, download))
                    }
                }
            }
        }
    }
}

/**
 * The curated catalogues, each with what it offers, then any the listener added before the list was curated (with
 * Remove). There is no field to add one by its address (ADR 2026-10-28-curated-catalogues).
 */
@Composable
private fun CatalogueList(books: WebBooks, onOpen: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var catalogues by remember { mutableStateOf<List<OpdsCatalogue>?>(null) }
    LaunchedEffect(Unit) { catalogues = books.browser.all() }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = Dimens.ScreenPadding)) {
        item {
            Text(
                stringResource(R.string.catalogues_explain),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = Dimens.SpaceS),
            )
        }
        items(catalogues.orEmpty(), key = { it.address }) { catalogue ->
            CatalogueRow(catalogue, onOpen = { onOpen(catalogue.address) }) {
                scope.launch {
                    books.browser.remove(catalogue.address)
                    catalogues = books.browser.all()
                }
            }
        }
    }
}

@Composable
private fun CatalogueRow(catalogue: OpdsCatalogue, onOpen: () -> Unit, onRemove: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = Dimens.SpaceM),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
            Text(catalogue.title, style = MaterialTheme.typography.titleMedium)
            catalogue.curated?.let { Text(stringResource(aboutOf(it)), style = MaterialTheme.typography.bodyMedium) }
            Text(
                WebAddress.hostOf(catalogue.address),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!catalogue.builtIn) TextButton(onClick = onRemove) { Text(stringResource(R.string.catalogues_remove)) }
    }
    HorizontalDivider()
}

/** What a curated catalogue offers, in the listener's language. */
@StringRes
internal fun aboutOf(curated: CuratedCatalogue): Int = when (curated) {
    CuratedCatalogue.GUTENBERG -> R.string.catalogue_about_gutenberg
    CuratedCatalogue.WIKISOURCE_EN -> R.string.catalogue_about_wikisource_en
    CuratedCatalogue.FEEDBOOKS -> R.string.catalogue_about_feedbooks
    CuratedCatalogue.UNGLUE_IT -> R.string.catalogue_about_unglue_it
}

/** One catalogue page: its search, its sections and books, and More when it has another page. */
@Composable
private fun FeedPage(
    books: WebBooks,
    address: String,
    onOpen: (String) -> Unit,
    onDownload: (String, OpdsDownload) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember(address) { mutableStateOf<PageState>(PageState.Loading) }
    var attempt by remember(address) { mutableIntStateOf(0) }
    val tell = { error: WebError -> Toast.makeText(context, webErrorText(context, error), Toast.LENGTH_LONG).show() }
    LaunchedEffect(address, attempt) {
        state = PageState.Loading
        state = when (val read = books.browser.feed(address)) {
            is Outcome.Success -> PageState.Shown(read.value)
            is Outcome.Failure -> PageState.Failed(read.error)
        }
    }
    when (val shown = state) {
        PageState.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(Dimens.ScreenPadding))

        is PageState.Failed -> Column(Modifier.padding(Dimens.ScreenPadding)) {
            Text(webErrorText(context, shown.error), style = MaterialTheme.typography.bodyLarge)
            TextButton(onClick = { attempt++ }) { Text(stringResource(R.string.catalogues_try_again)) }
        }

        is PageState.Shown -> FeedList(
            shown = shown,
            onOpen = onOpen,
            onDownload = onDownload,
            onSearch = { query ->
                val search = shown.feed.search
                if (search != null) {
                    scope.launch {
                        when (val found = books.browser.searchAddress(search, query)) {
                            is Outcome.Success -> onOpen(found.value)
                            is Outcome.Failure -> tell(found.error)
                        }
                    }
                }
            },
            onMore = {
                val next = shown.feed.next
                if (next != null && !shown.loadingMore) {
                    state = shown.copy(loadingMore = true)
                    scope.launch { state = withMore(shown, books.browser.feed(next), tell) }
                }
            },
        )
    }
}

/** [shown] with the next page's books added below, or as it was (and the error told) when that page failed. */
private fun withMore(shown: PageState.Shown, read: Outcome<OpdsFeed, WebError>, tell: (WebError) -> Unit): PageState =
    when (read) {
        is Outcome.Success ->
            PageState.Shown(shown.feed.copy(entries = shown.feed.entries + read.value.entries, next = read.value.next))

        is Outcome.Failure -> {
            tell(read.error)
            shown
        }
    }

@Composable
private fun FeedList(
    shown: PageState.Shown,
    onOpen: (String) -> Unit,
    onDownload: (String, OpdsDownload) -> Unit,
    onSearch: (String) -> Unit,
    onMore: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = Dimens.ScreenPadding)) {
        item {
            Text(
                shown.feed.title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = Dimens.SpaceS).semantics { heading() },
            )
        }
        if (shown.feed.search != null) item { SearchField(onSearch) }
        if (shown.feed.entries.isEmpty()) item { Text(stringResource(R.string.catalogues_empty)) }
        items(shown.feed.entries) { entry -> EntryRow(entry, onOpen) { download -> onDownload(entry.title, download) } }
        if (shown.feed.next != null) {
            item {
                if (shown.loadingMore) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = Dimens.SpaceL))
                } else {
                    TextButton(onClick = onMore) { Text(stringResource(R.string.catalogues_more)) }
                }
            }
        }
    }
}

@Composable
private fun SearchField(onSearch: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val search = { if (query.isNotBlank()) onSearch(query) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.catalogues_search_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { search() }),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { search() }, enabled = query.isNotBlank()) {
            Icon(painterResource(R.drawable.ic_search), stringResource(R.string.catalogues_search))
        }
    }
}

/** A section opens its page; a book has Download (in the best format ListenLock reads). */
@Composable
private fun EntryRow(entry: OpdsEntry, onOpen: (String) -> Unit, onDownload: (OpdsDownload) -> Unit) {
    val section = entry.section
    val rowModifier = if (section != null) Modifier.clickable { onOpen(section) } else Modifier
    Row(
        rowModifier.fillMaxWidth().padding(vertical = Dimens.SpaceM),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
    ) {
        Column(Modifier.weight(1f)) {
            Text(entry.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            entry.author?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            entry.summary?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = SUMMARY_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        entry.download?.let { download ->
            Button(onClick = { onDownload(download) }) { Text(stringResource(R.string.catalogues_download)) }
        }
    }
    HorizontalDivider()
}

private const val STACK_SEPARATOR = "\n"
private const val SUMMARY_LINES = 3
