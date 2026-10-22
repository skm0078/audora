// File: app/src/main/kotlin/io/github/skm0078/audora/ui/reader/ReaderScreen.kt
// Purpose: The reader: a page drawn or as sentences (tap to read, long-press for the sentence menu), controls;
//          Focus view and bold word starts in the text view (plan item 77).

package io.github.skm0078.audora.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import io.github.skm0078.audora.speech.voices.VoiceRow
import io.github.skm0078.audora.ui.theme.AudoraTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    state: ReaderState,
    config: ReaderConfig,
    actions: ReaderActions,
    modifier: Modifier = Modifier,
    ocrModels: OcrModelsState? = null,
    notes: NotesState = NotesState(),
    notesActions: BookNotesActions = BookNotesActions.None,
    voices: List<VoiceRow> = emptyList(),
    look: ReadingLook = ReadingLook(),
    onLookChange: (ReadingLook) -> Unit = {},
    onOpenUpNext: (() -> Unit)? = null,
    spokenWord: () -> IntRange? = { null },
    thumbnails: PageThumbnails? = null,
) {
    var textLookOpen by rememberSaveable { mutableStateOf(false) }
    if (textLookOpen) TextLookSheet(look, onLookChange) { textLookOpen = false }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { ReaderTitle(state.title.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = actions::onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigate_back))
                    }
                },
                actions = {
                    if (state.document is DocumentState.Open) {
                        BookmarksButton(notes, notesActions)
                        ChaptersButton(state, actions)
                        ViewToggle(state.view, actions)
                        MoreMenu(state.view == ReaderView.TEXT, look, onLookChange, onOpenUpNext) {
                            textLookOpen = true
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (state.document is DocumentState.Open) ReaderControls(state, config, actions, voices = voices)
        },
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

                is DocumentState.Open -> {
                    OpenBook(state, document.pageCount, config, actions, ocrModels, look, spokenWord, thumbnails)
                    NotesOverlays(notes, notesActions, actions)
                }
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
    look: ReadingLook,
    spokenWord: () -> IntRange?,
    thumbnails: PageThumbnails?,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        PageBar(state.page, pageCount, actions, onPick = { picking = true })
        PageScrubber(state.page, pageCount, thumbnails, actions::onGoToPage)
        state.timeLeft?.let { TimeLeftLine(it, state.playback.speed) }
        Row(Modifier.align(Alignment.CenterHorizontally)) {
            state.readingPosition?.takeIf { state.readingElsewhere }?.let { position ->
                TextButton(onClick = actions::onShowReadingPosition) {
                    Text(stringResource(R.string.reader_back_to_reading, position.page + 1))
                }
            }
            state.undoJumpTo?.let { target ->
                TextButton(onClick = actions::onUndoJump) {
                    Icon(painterResource(R.drawable.ic_undo), contentDescription = null)
                    Text(stringResource(R.string.reader_undo_jump, target.page + 1))
                }
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
                    state.view == ReaderView.PAGE -> PageView(state, content.units, actions, look.tone)
                    content.units.isNotEmpty() -> Sentences(state, content.units, actions, look, spokenWord)
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

/** The book's title in the top bar, one line; TalkBack's heading, so a swipe by headings lands on it. */
@Composable
private fun ReaderTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.semantics { heading() },
    )
}
