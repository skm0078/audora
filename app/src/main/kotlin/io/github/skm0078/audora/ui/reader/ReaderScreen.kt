// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/ReaderScreen.kt
// Purpose: The reader: a page drawn or as sentences (tap to read, long-press to report), page picker, controls.

package io.github.skm0078.audora.ui.reader

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.skm0078.audora.R
import io.github.skm0078.audora.core.config.ReaderConfig
import io.github.skm0078.audora.core.library.BookId
import io.github.skm0078.audora.core.model.Lang
import io.github.skm0078.audora.core.model.ReadingUnit
import io.github.skm0078.audora.pdf.render.ViewPoint
import io.github.skm0078.audora.speech.ocr.OcrModelsState
import io.github.skm0078.audora.ui.theme.AudoraTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    state: ReaderState,
    config: ReaderConfig,
    actions: ReaderActions,
    modifier: Modifier = Modifier,
    ocrModels: OcrModelsState? = null,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(state.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = actions::onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigate_back))
                    }
                },
                actions = { if (state.document is DocumentState.Open) ViewToggle(state.view, actions) },
            )
        },
        bottomBar = { if (state.document is DocumentState.Open) ReaderControls(state, config, actions) },
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding).fillMaxSize()) {
            when (val document = state.document) {
                DocumentState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                DocumentState.NotInLibrary -> Message(stringResource(R.string.book_not_in_library))

                is DocumentState.NeedsPassword -> PasswordPromptDialog(
                    wrongPassword = document.wrongPassword,
                    onSubmit = actions::onPasswordSubmitted,
                    onDismiss = actions::onPasswordDismissed,
                )

                DocumentState.Locked -> Message(stringResource(R.string.reader_locked)) {
                    Button(onClick = actions::onAskForPassword) { Text(stringResource(R.string.reader_enter_password)) }
                }

                is DocumentState.Failed -> Message(stringResource(failureText(document.reason))) {
                    Button(onClick = actions::onRetry) { Text(stringResource(R.string.reader_retry)) }
                }

                is DocumentState.Open -> OpenBook(state, document.pageCount, config, actions, ocrModels)
            }
        }
    }
}

@Composable
private fun OpenBook(
    state: ReaderState,
    pageCount: Int,
    config: ReaderConfig,
    actions: ReaderActions,
    ocrModels: OcrModelsState?,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        PageBar(state.page, pageCount, actions, onPick = { picking = true })
        state.readingPosition?.takeIf { state.readingElsewhere }?.let { position ->
            TextButton(
                onClick = actions::onShowReadingPosition,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(stringResource(R.string.reader_back_to_reading, position.page + 1))
            }
        }
        PlayerStatus(state.playback)
        when (val content = state.content) {
            PageContent.Loading -> Box(Modifier.fillMaxSize()) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }

            PageContent.Failed -> Message(stringResource(R.string.reader_page_failed))

            is PageContent.Ready -> {
                val badge = ocrBadgeText(content.origin, content.ocrConfidence, config)
                PageOriginNotice(content.origin, ocrModels, badge, actions::onDownloadOcrModels)
                when {
                    state.view == ReaderView.PAGE -> PageView(state, content.units, actions)
                    content.units.isNotEmpty() -> Sentences(state, content.units, actions)
                    content.origin == PageOrigin.TEXT -> Message(stringResource(R.string.reader_page_no_text))
                }
            }
        }
    }
    if (state.reporting != null) SoundedWrongDialog(actions::onReport, actions::onReportDismissed)
    if (state.unreadableTapped != null) UnreadableLineDialog(actions::onReadUnreadable, actions::onUnreadableDismissed)
    if (picking) {
        PagePickerDialog(
            pageCount = pageCount,
            onGo = { page ->
                picking = false
                actions.onGoToPage(page)
            },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun PageBar(page: Int, pageCount: Int, actions: ReaderActions, onPick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = actions::onPreviousPage, enabled = page > 0) {
            Icon(painterResource(R.drawable.ic_chevron_left), stringResource(R.string.reader_previous_page))
        }
        TextButton(onClick = onPick) { Text(stringResource(R.string.reader_page_of, page + 1, pageCount)) }
        IconButton(onClick = actions::onNextPage, enabled = page < pageCount - 1) {
            Icon(painterResource(R.drawable.ic_chevron_right), stringResource(R.string.reader_next_page))
        }
    }
}

@Composable
private fun PlayerStatus(playback: PlaybackSnapshot) {
    val text = when {
        playback.connection == PlaybackSnapshot.Connection.UNAVAILABLE -> R.string.reader_player_unavailable
        playback.errorCode != null -> R.string.reader_player_error
        else -> return
    }
    Text(
        stringResource(text),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
    )
}

@Composable
private fun Sentences(state: ReaderState, units: List<ReadingUnit>, actions: ReaderActions) {
    // A new list state per page, so every page opens at its top.
    val listState = remember(state.page) { LazyListState() }
    val spoken = state.spokenUnitIndex
    LaunchedEffect(spoken, state.page) {
        if (spoken != null && state.following && !listState.isFullyVisible(spoken + HEADER_ITEMS)) {
            listState.animateScrollToItem(spoken + HEADER_ITEMS)
        }
    }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item {
            Text(
                stringResource(R.string.reader_tap_hint),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        itemsIndexed(units, key = { _, unit -> unit.charStart }) { index, unit ->
            Sentence(
                unit,
                SentenceLook(spoken = index == spoken, unreadable = state.isUnreadable(unit)),
                enabled = state.playerReady,
                onTap = actions::onSentenceTapped,
                onLongPress = actions::onSentenceLongPressed,
            )
        }
    }
}

/** [unreadable]: playback skips it as junk (RI-2b), so it is greyed (RI-9). */
private data class SentenceLook(val spoken: Boolean, val unreadable: Boolean)

/** Tap reads from the sentence (once the player is ready); a long-press reports it (RI-8b). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Sentence(
    unit: ReadingUnit,
    look: SentenceLook,
    enabled: Boolean,
    onTap: (ReadingUnit) -> Unit,
    onLongPress: (ReadingUnit) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val background = if (look.spoken) colors.primaryContainer else Color.Transparent
    val color = when {
        look.spoken -> colors.onPrimaryContainer
        look.unreadable -> colors.onSurface.copy(alpha = UNREADABLE_ALPHA)
        else -> colors.onSurface
    }
    Text(
        text = unit.text,
        style = MaterialTheme.typography.bodyLarge,
        color = color,
        fontStyle = if (look.unreadable) FontStyle.Italic else null,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .combinedClickable(
                onClick = { if (enabled) onTap(unit) },
                onLongClick = { onLongPress(unit) },
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

@Composable
private fun Message(text: String, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        action?.invoke()
    }
}

private fun failureText(reason: ReaderFailure): Int = when (reason) {
    ReaderFailure.DAMAGED -> R.string.reader_damaged
    ReaderFailure.UNREADABLE -> R.string.reader_unreadable
    ReaderFailure.ENGINE_UNAVAILABLE -> R.string.reader_engine
}

private fun LazyListState.isFullyVisible(index: Int): Boolean {
    val info = layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return false
    return item.offset >= info.viewportStartOffset && item.offset + item.size <= info.viewportEndOffset
}

/** The hint above the sentences. */
private const val HEADER_ITEMS = 1

/** A greyed (unreadable) sentence's text: Material's disabled-content opacity. */
private const val UNREADABLE_ALPHA = 0.38f

@Preview(showBackground = true)
@Composable
private fun ReaderScreenPreview() {
    val book = BookId("preview")
    val sentences = listOf("Call me Ishmael.", "Some years ago, never mind how long precisely, I thought I would sail.")
    var start = 0
    val units = sentences.mapIndexed { i, text ->
        ReadingUnit(0, i, start, start + text.length, text, Lang.EN, 0, null).also { start += text.length + 1 }
    }
    val state = ReaderState(
        bookId = book,
        title = "Moby Dick",
        document = DocumentState.Open(574),
        content = PageContent.Ready(units),
        view = ReaderView.TEXT,
        playback = PlaybackSnapshot(
            connection = PlaybackSnapshot.Connection.CONNECTED,
            position = ReadingPosition(book, 0, units[1].charStart),
            wantsToPlay = true,
            speed = 1.25f,
            canSkipBack = true,
            canSkipForward = true,
        ),
    )
    AudoraTheme { ReaderScreen(state, ReaderConfig(), PreviewActions) }
}

@Suppress("TooManyFunctions")
private object PreviewActions : ReaderActions {
    override fun onBack() = Unit
    override fun onPreviousPage() = Unit
    override fun onNextPage() = Unit
    override fun onGoToPage(page: Int) = Unit
    override fun onShowReadingPosition() = Unit
    override fun onSentenceTapped(unit: ReadingUnit) = Unit
    override fun onPageTapped(point: ViewPoint) = Unit
    override fun onViewChanged(view: ReaderView) = Unit
    override fun onPageWidth(widthPx: Int) = Unit
    override fun onPlayPause() = Unit
    override fun onSkipBack() = Unit
    override fun onSkipForward() = Unit
    override fun onSpeed(speed: Float) = Unit
    override fun onSleepTimer(setting: SleepTimerSetting) = Unit
    override fun onPasswordSubmitted(password: String) = Unit
    override fun onPasswordDismissed() = Unit
    override fun onAskForPassword() = Unit
    override fun onRetry() = Unit
}
