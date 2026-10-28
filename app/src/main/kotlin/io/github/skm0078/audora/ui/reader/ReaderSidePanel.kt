// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/ReaderSidePanel.kt
// Purpose: The reader's side panel on a wide screen: the book's contents, notes and people beside the page, so none
//          of them covers it as a sheet (ADR 2026-10-28-tablet-panes).

package io.github.skm0078.audora.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.R
import io.github.skm0078.audora.ui.theme.Dimens
import io.github.skm0078.audora.ui.theme.WindowWidths

/** Whether the reader is wide enough for the panel and a page of comfortable lines beside it. */
internal fun showsSidePanel(readerWidth: Dp): Boolean = readerWidth >= WindowWidths.EXPANDED

/**
 * The same lists the chapters and bookmarks sheets show, in tabs; opening a chapter or a note leaves the panel up, so
 * the listener can jump again. [people]: the character guide, given the space it fills.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderSidePanel(
    state: ReaderState,
    actions: ReaderActions,
    notes: NotesState,
    notesActions: BookNotesActions,
    voiceNotes: VoiceNotesBinding,
    people: (@Composable (Modifier) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val tabs = panelTabs(state.chapters.isNotEmpty(), people != null)
    var picked by rememberSaveable { mutableStateOf(tabs.first()) }
    // A tab can go: the guide switched off, or a book with no chapters opened in the same reader.
    val shown = picked.takeIf { it in tabs } ?: tabs.first()
    Column(modifier.width(SIDE_PANEL_WIDTH).fillMaxHeight().padding(top = Dimens.SpaceL).testTag(SIDE_PANEL_TAG)) {
        if (tabs.size > 1) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = Dimens.SpaceL)) {
                tabs.forEachIndexed { index, tab ->
                    SegmentedButton(
                        selected = tab == shown,
                        onClick = { picked = tab },
                        shape = SegmentedButtonDefaults.itemShape(index, tabs.size),
                        // No tick: three short names fit 320 dp at large text only without it.
                        icon = {},
                    ) { Text(stringResource(tabLabel(tab)), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
            Spacer(Modifier.height(Dimens.SpaceS))
        }
        val rest = Modifier.weight(1f)
        when (shown) {
            PanelTab.CONTENTS -> ChapterList(state.chapters, state.currentChapter, actions::onChapter, rest)

            PanelTab.NOTES -> BookmarksList(
                notes.bookmarks,
                notesActions,
                actions,
                voiceNotes,
                onOpened = {},
                modifier = rest,
                titled = false,
            )

            PanelTab.PEOPLE -> people?.invoke(rest)
        }
    }
}

/** The page, with the side panel at its start when there is one (a wide screen). */
@Composable
internal fun BesidePanel(panel: (@Composable () -> Unit)?, page: @Composable () -> Unit) {
    if (panel == null) {
        page()
        return
    }
    Row(Modifier.fillMaxSize()) {
        panel()
        VerticalDivider()
        Box(Modifier.weight(1f).fillMaxHeight()) { page() }
    }
}

/** The window's width, so a split screen or a folded foldable keeps the sheets. */
@Composable
internal fun currentWindowWidth(): Dp = with(LocalDensity.current) {
    LocalWindowInfo.current.containerSize.width.toDp()
}

private fun tabLabel(tab: PanelTab): Int = when (tab) {
    PanelTab.CONTENTS -> R.string.reader_panel_contents
    PanelTab.NOTES -> R.string.reader_panel_notes
    PanelTab.PEOPLE -> R.string.reader_panel_people
}

/** The mockup's 320 dp: chapter names on one line at 100 % text, and the page keeps 520 dp at 840 dp. */
internal val SIDE_PANEL_WIDTH = 320.dp

internal const val SIDE_PANEL_TAG = "reader_side_panel"
