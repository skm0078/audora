// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/SearchUi.kt
// Purpose: The search-in-book screen (plan item 16): a query box, how much of the book is searched, matching sentences.

package io.github.skm0078.audora.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.skm0078.audora.R
import io.github.skm0078.audora.playback.search.SearchHit

/** Full screen over the reader; a tapped sentence's page opens in the reader and the screen closes. */
@Composable
fun SearchScreen(state: SearchState, onQuery: (String) -> Unit, onHit: (SearchHit) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                SearchBox(state.query, onQuery, onDismiss)
                SearchProgress(state)
                LazyColumn(Modifier.fillMaxSize()) {
                    items(state.hits, key = { "${it.page}:${it.charStart}" }) { hit ->
                        HitRow(hit) { onHit(hit) }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchBox(query: String, onQuery: (String) -> Unit, onDismiss: () -> Unit) {
    val focus = remember { FocusRequester() }
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onDismiss) {
            Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigate_back))
        }
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.weight(1f).focusRequester(focus),
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
            singleLine = true,
        )
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}

@Composable
private fun SearchProgress(state: SearchState) {
    val progress = state.progress
    val line = when {
        state.failed -> stringResource(R.string.search_failed)

        progress == null -> stringResource(R.string.search_opening)

        state.building -> stringResource(R.string.search_building, progress.pagesDone, progress.pageCount)

        progress.missing.isNotEmpty() ->
            pluralStringResource(R.plurals.search_missing, progress.missing.size, progress.missing.size)

        state.query.isNotBlank() && state.hits.isEmpty() -> stringResource(R.string.search_no_results)

        else -> null
    }
    if (state.building && progress != null && progress.pageCount > 0) {
        LinearProgressIndicator(
            progress = { progress.pagesDone.toFloat() / progress.pageCount },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        )
    }
    line?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp)) }
}

@Composable
private fun HitRow(hit: SearchHit, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(R.string.a11y_go_to_hit), onClick = onClick)
            .padding(vertical = 12.dp),
    ) {
        Text(
            stringResource(R.string.search_page, hit.page + 1),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(hit.text, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}
