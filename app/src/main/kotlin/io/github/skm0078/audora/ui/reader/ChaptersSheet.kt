// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/ChaptersSheet.kt
// Purpose: The chapter list (plan item 11): its top-bar button and sheet, the current chapter marked; tap to go.

package io.github.skm0078.audora.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.R
import io.github.skm0078.audora.pdf.outline.ChapterEntry

@Composable
internal fun ChaptersButton(state: ReaderState, actions: ReaderActions) {
    if (state.chapters.isEmpty()) return
    var open by rememberSaveable { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(painterResource(R.drawable.ic_toc), stringResource(R.string.chapters_title))
    }
    if (open) {
        ChaptersSheet(
            chapters = state.chapters,
            current = state.currentChapter,
            onChapter = { chapter ->
                open = false
                actions.onChapter(chapter)
            },
            onDismiss = { open = false },
        )
    }
}

/** Opens scrolled to [current] (the chapter being read), so a long table of contents needs no hunting. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChaptersSheet(
    chapters: List<ChapterEntry>,
    current: Int?,
    onChapter: (ChapterEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    val first = ((current ?: 0) - CONTEXT_ROWS).coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = first)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            stringResource(R.string.chapters_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).semantics { heading() },
        )
        LazyColumn(state = listState) {
            itemsIndexed(chapters) { index, chapter ->
                ChapterRow(chapter, isCurrent = index == current) { onChapter(chapter) }
            }
        }
    }
}

@Composable
private fun ChapterRow(chapter: ChapterEntry, isCurrent: Boolean, onClick: () -> Unit) {
    val color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    val now = stringResource(R.string.chapters_current)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // The current chapter is shown by colour and weight; TalkBack says it in words.
            .semantics { if (isCurrent) stateDescription = now }
            .clickable(onClickLabel = stringResource(R.string.a11y_read_from_chapter), onClick = onClick)
            .padding(start = 24.dp + INDENT * chapter.depth, end = 24.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            chapter.title.ifBlank { stringResource(R.string.chapters_untitled, chapter.page + 1) },
            color = color,
            fontWeight = if (isCurrent) FontWeight.Bold else null,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            (chapter.page + 1).toString(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}

/** Nesting step per outline level. */
private val INDENT = 16.dp

/** Rows shown above the current chapter when the sheet opens. */
private const val CONTEXT_ROWS = 2
